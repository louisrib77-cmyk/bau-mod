package de.louis.marineford;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.function.Consumer;

public final class MarinefordPlugin extends JavaPlugin {

    public static final String MARKER_SET_ID = "marineford-plan";

    private WebServer webServer;
    private final Consumer<BlueMapAPI> blueMapEnable = this::hookBlueMap;
    private final Consumer<BlueMapAPI> blueMapDisable =
            api -> getLogger().info("BlueMap wurde deaktiviert, Karten-Ebene entfernt.");

    @Override
    public void onEnable() {
        saveDefaultConfig();

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

        BlueMapAPI.onEnable(blueMapEnable);
        BlueMapAPI.onDisable(blueMapDisable);
    }

    @Override
    public void onDisable() {
        BlueMapAPI.unregisterListener(blueMapEnable);
        BlueMapAPI.unregisterListener(blueMapDisable);
        if (webServer != null) {
            webServer.stop();
        }
    }

    /** Wird aufgerufen, sobald BlueMap bereit ist (auch nach /bluemap reload). */
    private void hookBlueMap(BlueMapAPI api) {
        api.getMaps().forEach(map -> {
            MarkerSet set = MarkerSet.builder()
                    .label("Marineford Bauplan")
                    .defaultHidden(false)
                    .build();
            map.getMarkerSets().put(MARKER_SET_ID, set);
        });
        getLogger().info("BlueMap-Ebene 'Marineford Bauplan' auf " + api.getMaps().size() + " Karte(n) registriert.");
    }

    public boolean isBlueMapReady() {
        return BlueMapAPI.getInstance().isPresent();
    }
}
