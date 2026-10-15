package de.louis.marineford;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimaler eingebauter Webserver (JDK-intern, keine Fremd-Bibliothek noetig). */
public final class WebServer {

    private final MarinefordPlugin plugin;
    private final String token;
    private final HttpServer server;
    private final ExecutorService executor;

    public WebServer(MarinefordPlugin plugin, String bind, int port, String token) throws IOException {
        this.plugin = plugin;
        this.token = token == null ? "" : token;
        this.server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "MarinefordBuilder-Web");
            t.setDaemon(true);
            return t;
        });
        this.server.setExecutor(executor);
        this.server.createContext("/api/status", this::handleStatus);
        this.server.createContext("/", this::handleStatic);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private boolean authorized(HttpExchange ex) {
        if (token.isEmpty()) return true;
        String header = ex.getRequestHeaders().getFirst("X-Token");
        if (token.equals(header)) return true;
        String query = ex.getRequestURI().getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int i = pair.indexOf('=');
                if (i > 0 && pair.substring(0, i).equals("token")
                        && token.equals(URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            send(ex, 401, "application/json", "{\"error\":\"unauthorized\"}");
            return;
        }
        String json = "{\"plugin\":\"MarinefordBuilder\""
                + ",\"version\":\"" + plugin.getPluginMeta().getVersion() + "\""
                + ",\"players\":" + Bukkit.getOnlinePlayers().size()
                + ",\"bluemap\":" + plugin.isBlueMapReady() + "}";
        send(ex, 200, "application/json", json);
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            send(ex, 401, "text/plain; charset=utf-8", "Kein Zugriff. Token fehlt oder ist falsch.");
            return;
        }
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
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, data.length);
        try (var out = ex.getResponseBody()) {
            out.write(data);
        }
    }
}
