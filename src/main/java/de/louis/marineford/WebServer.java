package de.louis.marineford;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Eingebauter Webserver (JDK-intern): statische Seite + kleine JSON-API. */
public final class WebServer {

    private interface Route {
        void handle(HttpExchange ex) throws Exception;
    }

    private static final String JSON = "application/json; charset=utf-8";

    private final MarinefordPlugin plugin;
    private final String token;
    private final HttpServer server;
    private final ExecutorService executor;
    private final Gson gson = new Gson();

    public WebServer(MarinefordPlugin plugin, String bind, int port, String token) throws IOException {
        this.plugin = plugin;
        this.token = token == null ? "" : token;
        this.server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "MarinefordBuilder-Web");
            t.setDaemon(true);
            return t;
        });
        this.server.setExecutor(executor);

        route("/api/status", "GET", this::status);
        route("/api/config", "GET", this::config);
        route("/api/players", "GET", this::players);
        route("/api/plan", "GET", this::plan);
        route("/api/layer", "GET", this::layer);
        route("/api/anchor", "POST", this::anchor);
        route("/api/scan", "POST", this::scan);
        route("/api/todos", null, this::todos);
        route("/api/todos/toggle", "POST", this::todoToggle);
        route("/api/todos/delete", "POST", this::todoDelete);
        route("/", "GET", this::staticFile);
    }

    public void start() { server.start(); }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ Infrastruktur

    private void route(String path, String method, Route r) {
        server.createContext(path, ex -> {
            try {
                if (!authorized(ex)) {
                    send(ex, 401, JSON, "{\"error\":\"unauthorized\"}");
                    return;
                }
                if (method != null && !ex.getRequestMethod().equalsIgnoreCase(method)) {
                    send(ex, 405, JSON, "{\"error\":\"method not allowed\"}");
                    return;
                }
                r.handle(ex);
            } catch (Exception e) {
                plugin.getLogger().warning("Webfehler bei " + ex.getRequestURI() + ": " + e);
                try {
                    send(ex, 500, JSON, "{\"error\":\"internal\"}");
                } catch (IOException ignored) {
                    // Antwort war evtl. schon gesendet
                }
            } finally {
                ex.close();
            }
        });
    }

    private boolean authorized(HttpExchange ex) {
        if (token.isEmpty()) return true;
        if (token.equals(ex.getRequestHeaders().getFirst("X-Token"))) return true;
        return token.equals(query(ex).get("token"));
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return map;
        for (String pair : q.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                map.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static JsonObject body(HttpExchange ex) throws IOException {
        String s = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonElement e = JsonParser.parseString(s.isBlank() ? "{}" : s);
        return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    private void json(HttpExchange ex, int code, JsonElement el) throws IOException {
        send(ex, code, JSON, gson.toJson(el));
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, data.length);
        try (var out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private static JsonObject error(String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("error", msg);
        return o;
    }

    // ------------------------------------------------------------------ Handler

    private void status(HttpExchange ex) throws IOException {
        ProgressScanner sc = plugin.scanner();
        JsonObject o = new JsonObject();
        o.addProperty("plugin", "MarinefordBuilder");
        o.addProperty("version", plugin.getPluginMeta().getVersion());
        o.addProperty("players", plugin.players().size());
        o.addProperty("bluemap", plugin.isBlueMapReady());
        o.addProperty("anchorSet", plugin.store().anchor() != null);
        o.addProperty("scanning", sc.isScanning());
        o.addProperty("scanChunksTotal", sc.chunksTotal());
        o.addProperty("scanChunksDone", sc.chunksDone());
        o.addProperty("lastScan", sc.lastScan());
        json(ex, 200, o);
    }

    private void config(HttpExchange ex) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("bluemapPort", plugin.getConfig().getInt("web.bluemap-port", 8100));
        Anchor a = plugin.store().anchor();
        o.add("anchor", a == null ? null : gson.toJsonTree(a));
        o.add("stages", gson.toJsonTree(plugin.store().plan().stages));
        json(ex, 200, o);
    }

    private void players(HttpExchange ex) throws IOException {
        JsonArray arr = new JsonArray();
        for (MarinefordPlugin.PlayerInfo p : plugin.players()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.name());
            o.addProperty("uuid", p.uuid());
            o.addProperty("world", p.world());
            o.addProperty("x", p.x());
            o.addProperty("y", p.y());
            o.addProperty("z", p.z());
            o.addProperty("yaw", p.yaw());
            arr.add(o);
        }
        json(ex, 200, arr);
    }

    private void plan(HttpExchange ex) throws IOException {
        Plan plan = plugin.store().plan();
        JsonObject o = new JsonObject();
        o.add("stages", gson.toJsonTree(plan.stages));
        JsonArray parts = new JsonArray();
        for (Part p : plan.parts) {
            JsonObject j = new JsonObject();
            j.addProperty("id", p.id);
            j.addProperty("name", p.name);
            j.addProperty("stage", p.stage);
            j.addProperty("shape", p.shape);
            j.addProperty("material", p.material);
            j.addProperty("x", p.x);
            j.addProperty("y", p.y);
            j.addProperty("z", p.z);
            j.addProperty("sx", p.sx);
            j.addProperty("sy", p.sy);
            j.addProperty("sz", p.sz);
            j.addProperty("hollow", p.hollow);
            j.addProperty("total", p.total);
            j.addProperty("done", p.done);
            j.addProperty("checked", p.checked);
            j.addProperty("nextLayer", p.nextLayer());
            parts.add(j);
        }
        o.add("parts", parts);
        json(ex, 200, o);
    }

    private void layer(HttpExchange ex) throws IOException {
        Map<String, String> q = query(ex);
        Part part = null;
        for (Part p : plugin.store().plan().parts) {
            if (p.id.equals(q.get("part"))) part = p;
        }
        int ly;
        try {
            ly = Integer.parseInt(q.getOrDefault("y", "0"));
        } catch (NumberFormatException e) {
            json(ex, 400, error("y ungueltig"));
            return;
        }
        if (part == null || ly < 0 || ly >= part.sy) {
            json(ex, 404, error("Bauteil oder Layer nicht gefunden"));
            return;
        }
        Anchor a = plugin.store().anchor();
        JsonArray rows = new JsonArray();
        for (int lz = 0; lz < part.sz; lz++) {
            StringBuilder sb = new StringBuilder(part.sx);
            for (int lx = 0; lx < part.sx; lx++) sb.append(part.contains(lx, ly, lz) ? '#' : '.');
            rows.add(sb.toString());
        }
        JsonObject o = new JsonObject();
        o.addProperty("part", part.id);
        o.addProperty("y", ly);
        o.addProperty("width", part.sx);
        o.addProperty("depth", part.sz);
        o.addProperty("count", part.layerTotal[ly]);
        o.addProperty("done", part.layerDone != null && ly < part.layerDone.length ? part.layerDone[ly] : 0);
        o.addProperty("material", part.material);
        o.addProperty("anchorSet", a != null);
        o.addProperty("minX", (a == null ? 0 : a.x) + part.x);
        o.addProperty("minZ", (a == null ? 0 : a.z) + part.z);
        o.addProperty("absY", (a == null ? 0 : a.y) + part.y + ly);
        o.add("rows", rows);
        json(ex, 200, o);
    }

    private void anchor(HttpExchange ex) throws IOException {
        JsonObject b = body(ex);
        try {
            String world = b.get("world").getAsString().trim();
            if (world.isEmpty()) throw new IllegalArgumentException("world leer");
            Anchor a = new Anchor(world, b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt());
            plugin.setAnchor(a);
            json(ex, 200, ok());
        } catch (RuntimeException e) {
            json(ex, 400, error("Ungueltige Anker-Daten"));
        }
    }

    private void scan(HttpExchange ex) throws IOException {
        if (plugin.store().anchor() == null) {
            json(ex, 400, error("Kein Anker gesetzt"));
            return;
        }
        plugin.requestScan();
        json(ex, 200, ok());
    }

    private void todos(HttpExchange ex) throws IOException {
        PlanStore store = plugin.store();
        if (ex.getRequestMethod().equalsIgnoreCase("GET")) {
            json(ex, 200, gson.toJsonTree(store.todos()));
        } else if (ex.getRequestMethod().equalsIgnoreCase("POST")) {
            JsonObject b = body(ex);
            String text = b.has("text") ? b.get("text").getAsString().trim() : "";
            if (text.isEmpty()) {
                json(ex, 400, error("Text fehlt"));
                return;
            }
            String assignee = b.has("assignee") ? b.get("assignee").getAsString().trim() : "";
            int stage = b.has("stage") ? b.get("stage").getAsInt() : 0;
            json(ex, 200, gson.toJsonTree(store.addTodo(text, assignee, stage)));
        } else {
            json(ex, 405, error("method not allowed"));
        }
    }

    private void todoToggle(HttpExchange ex) throws IOException {
        long id = parseId(ex);
        json(ex, plugin.store().toggleTodo(id) ? 200 : 404, ok());
    }

    private void todoDelete(HttpExchange ex) throws IOException {
        long id = parseId(ex);
        json(ex, plugin.store().deleteTodo(id) ? 200 : 404, ok());
    }

    private static long parseId(HttpExchange ex) {
        try {
            return Long.parseLong(query(ex).getOrDefault("id", "-1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void staticFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) path = "/index.html";
        if (path.contains("..")) {
            send(ex, 400, "text/plain", "Bad request");
            return;
        }
        try (InputStream in = plugin.getResource("web" + path)) {
            if (in == null) {
                send(ex, 404, "text/plain; charset=utf-8", "Nicht gefunden");
                return;
            }
            byte[] data = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", contentType(path));
            ex.sendResponseHeaders(200, data.length);
            try (var out = ex.getResponseBody()) {
                out.write(data);
            }
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".json")) return JSON;
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}
