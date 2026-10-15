package de.louis.marineford;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MarinefordPlugin extends JavaPlugin {

    public static final String MARKER_SET_ID = "marineford-plan";

    public record PlayerInfo(String name, String uuid, String world, double x, double y, double z, float yaw) {}

    private PlanStore store;
    private ProgressScanner scanner;
    private WebServer webServer;
    private volatile List<PlayerInfo> players = List.of();

    private final Consumer<BlueMapAPI> blueMapEnable = api -> {
        refreshMarkers();
        getLogger().info("BlueMap-Ebene 'Marineford Bauplan' registriert.");
    };
    private final Consumer<BlueMapAPI> blueMapDisable =
            api -> getLogger().info("BlueMap wurde deaktiviert.");

    @Override
    public void onEnable() {
        saveDefaultConfig();

        store = new PlanStore(this);
        try {
            store.load();
        } catch (IOException | RuntimeException e) {
            getLogger().severe("plan.json/state.json konnte nicht geladen werden: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        scanner = new ProgressScanner(this, store);

        int port = getConfig().getInt("web.port", 8200);
        String bind = getConfig().getString("web.bind", "0.0.0.0");
        String token = getConfig().getString("web.token", "");
        try {
            webServer = new WebServer(this, bind, port, token);
            webServer.start();
            getLogger().info("Webseite laeuft auf " + bind + ":" + port);
        } catch (IOException e) {
            getLogger().severe("Webserver konnte nicht auf Port " + port + " starten: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Live-Spielerliste (Schnappschuss alle Sekunde, damit der Webserver nie Bukkit-Objekte anfasst)
        getServer().getScheduler().runTaskTimer(this, () -> {
            List<PlayerInfo> list = new ArrayList<>();
            for (Player p : getServer().getOnlinePlayers()) {
                Location l = p.getLocation();
                list.add(new PlayerInfo(p.getName(), p.getUniqueId().toString(), l.getWorld().getName(),
                        l.getX(), l.getY(), l.getZ(), l.getYaw()));
            }
            players = List.copyOf(list);
        }, 20L, 20L);

        // Regelmaessiger Fortschritts-Scan
        int minutes = getConfig().getInt("scan-interval-minutes", 10);
        if (minutes > 0) {
            long ticks = 20L * 60L * minutes;
            getServer().getScheduler().runTaskTimer(this, () -> scanner.start(), 20L * 30L, ticks);
        }

        BlueMapAPI.onEnable(blueMapEnable);
        BlueMapAPI.onDisable(blueMapDisable);
    }

    @Override
    public void onDisable() {
        BlueMapAPI.unregisterListener(blueMapEnable);
        BlueMapAPI.unregisterListener(blueMapDisable);
        if (scanner != null) scanner.cancel();
        if (webServer != null) webServer.stop();
    }

    // ------------------------------------------------------------------ API fuer den Webserver

    public PlanStore store() { return store; }
    public ProgressScanner scanner() { return scanner; }
    public List<PlayerInfo> players() { return players; }
    public boolean isBlueMapReady() { return BlueMapAPI.getInstance().isPresent(); }

    /** Thread-sicher: fuehrt die Aktion auf dem Hauptthread aus. */
    private void sync(Runnable r) {
        if (getServer().isPrimaryThread()) r.run();
        else getServer().getScheduler().runTask(this, r);
    }

    public void setAnchor(Anchor a) {
        store.setAnchor(a);
        sync(() -> {
            refreshMarkers();
            scanner.start();
        });
    }

    public void requestScan() {
        sync(() -> scanner.start());
    }

    public void reloadPlan() throws IOException {
        store.load();
        sync(this::refreshMarkers);
    }

    // ------------------------------------------------------------------ BlueMap-Ebene

    public void refreshMarkers() {
        BlueMapAPI.getInstance().ifPresent(api -> {
            Anchor a = store.anchor();
            for (BlueMapMap map : api.getMaps()) {
                MarkerSet set = MarkerSet.builder()
                        .label("Marineford Bauplan")
                        .defaultHidden(false)
                        .build();
                if (a != null) {
                    for (Part p : store.plan().parts) {
                        set.put(p.id, marker(p, a));
                    }
                }
                map.getMarkerSets().put(MARKER_SET_ID, set);
            }
        });
    }

    private Marker marker(Part p, Anchor a) {
        float y = a.y + p.y + p.sy;
        double x1 = a.x + p.x, z1 = a.z + p.z;
        double x2 = x1 + p.sx, z2 = z1 + p.sz;
        Shape shape = (p.shape.equals("cylinder") || p.shape.equals("dome"))
                ? Shape.createEllipse((x1 + x2) / 2, (z1 + z2) / 2, p.sx / 2.0, p.sz / 2.0, 32)
                : Shape.createRect(x1, z1, x2, z2);
        double pct = p.total == 0 ? 0 : p.done * 100.0 / p.total;
        boolean full = p.total > 0 && p.done == p.total;
        Color line = full ? new Color(63, 185, 80, 1f) : new Color(88, 166, 255, 1f);
        Color fill = full ? new Color(63, 185, 80, 0.18f) : new Color(88, 166, 255, 0.10f);
        return ShapeMarker.builder()
                .label(String.format("%s – %.0f%%", p.name, pct))
                .shape(shape, y)
                .lineColor(line)
                .fillColor(fill)
                .depthTestEnabled(false)
                .build();
    }

    // ------------------------------------------------------------------ Befehl /marineford

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (sub) {
            case "anchor" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Nur Spieler können den Anker setzen.");
                    return true;
                }
                Location l = p.getLocation();
                setAnchor(new Anchor(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()));
                msg(sender, "Anker gesetzt auf " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                        + " (" + l.getWorld().getName() + "). Scan startet.");
            }
            case "scan" -> {
                if (store.anchor() == null) {
                    msg(sender, "Noch kein Anker gesetzt (/marineford anchor).");
                } else {
                    requestScan();
                    msg(sender, "Scan gestartet.");
                }
            }
            case "reload" -> {
                try {
                    reloadPlan();
                    msg(sender, "plan.json neu geladen (" + store.plan().parts.size() + " Bauteile).");
                } catch (IOException | RuntimeException e) {
                    msg(sender, "Fehler beim Laden: " + e.getMessage());
                }
            }
            default -> {
                Anchor a = store.anchor();
                long total = 0, done = 0;
                for (Part p : store.plan().parts) {
                    total += p.total;
                    done += p.done;
                }
                msg(sender, "Marineford: " + store.plan().parts.size() + " Bauteile, "
                        + (total == 0 ? 0 : done * 100 / total) + "% fertig"
                        + (scanner.isScanning() ? " (Scan läuft)" : "") + ".");
                msg(sender, a == null ? "Anker: nicht gesetzt" : "Anker: " + a.x + " " + a.y + " " + a.z + " (" + a.world + ")");
                msg(sender, "Befehle: /marineford anchor | scan | reload");
            }
        }
        return true;
    }

    private static void msg(CommandSender s, String text) {
        s.sendMessage(Component.text(text));
    }
}
