package de.louis.marineford;

import org.bukkit.Material;

/** Ein Bauteil des Plans: eine Grundform aus genau einem Material, relativ zum Ankerpunkt. */
public final class Part {
    public String id;
    public String name;
    public int stage;
    /** box | cylinder | dome */
    public String shape = "box";
    public String material = "STONE";
    /** Minimale Ecke relativ zum Anker. */
    public int x, y, z;
    /** Groesse der Bounding-Box. */
    public int sx = 1, sy = 1, sz = 1;
    /** Nur Wand (offene Huelle) statt Vollkoerper. */
    public boolean hollow;
    public int wall = 2;

    public transient Material mat;
    public transient long total;
    public transient int[] layerTotal;
    public transient int[] layerDone;
    public transient volatile long done;
    public transient volatile long checked;

    /** Berechnet Blockzahlen (gesamt und pro Layer). */
    public void init() {
        mat = Material.matchMaterial(material);
        if (mat == null) mat = Material.STONE;
        layerTotal = new int[sy];
        layerDone = new int[sy];
        long t = 0;
        for (int ly = 0; ly < sy; ly++) {
            int c = 0;
            for (int lz = 0; lz < sz; lz++) {
                for (int lx = 0; lx < sx; lx++) {
                    if (contains(lx, ly, lz)) c++;
                }
            }
            layerTotal[ly] = c;
            t += c;
        }
        total = t;
        done = 0;
        checked = 0;
    }

    public void resetProgress() {
        done = 0;
        checked = 0;
        layerDone = new int[sy];
    }

    /** Niedrigster Layer, der noch nicht komplett ist; -1 = fertig. */
    public int nextLayer() {
        int[] lt = layerTotal, ld = layerDone;
        if (lt == null || ld == null) return 0;
        for (int ly = 0; ly < sy && ly < ld.length; ly++) {
            if (lt[ly] > 0 && ld[ly] < lt[ly]) return ly;
        }
        return -1;
    }

    /** Lokale Koordinaten (0..s-1). */
    public boolean contains(int lx, int ly, int lz) {
        if (lx < 0 || ly < 0 || lz < 0 || lx >= sx || ly >= sy || lz >= sz) return false;
        switch (shape) {
            case "cylinder":
                if (!inEllipse(lx, lz, 0)) return false;
                return !hollow || !inEllipse(lx, lz, wall);
            case "dome":
                if (!inEllipsoid(lx, ly, lz, 0)) return false;
                return !hollow || !inEllipsoid(lx, ly, lz, wall);
            default:
                if (!hollow) return true;
                return lx < wall || lx >= sx - wall || lz < wall || lz >= sz - wall;
        }
    }

    private boolean inEllipse(int lx, int lz, int inset) {
        double rx = sx / 2.0 - inset, rz = sz / 2.0 - inset;
        if (rx <= 0 || rz <= 0) return false;
        double dx = (lx + 0.5 - sx / 2.0) / rx, dz = (lz + 0.5 - sz / 2.0) / rz;
        return dx * dx + dz * dz <= 1.0;
    }

    private boolean inEllipsoid(int lx, int ly, int lz, int inset) {
        double rx = sx / 2.0 - inset, rz = sz / 2.0 - inset, ry = sy - inset;
        if (rx <= 0 || rz <= 0 || ry <= 0) return false;
        double dx = (lx + 0.5 - sx / 2.0) / rx, dz = (lz + 0.5 - sz / 2.0) / rz, dy = (ly + 0.5) / ry;
        return dx * dx + dy * dy + dz * dz <= 1.0;
    }
}
