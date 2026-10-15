package de.louis.marineford;

/**
 * Start-Bauplan fuer Marineford (vereinfachte Grundformen, Mittelpunkt der Plaza = Anker).
 * Wird beim ersten Start als plugins/MarinefordBuilder/plan.json gespeichert und kann dort frei
 * bearbeitet werden (Teile aendern, loeschen, neue hinzufuegen), danach /marineford reload.
 */
public final class DefaultPlan {

    private DefaultPlan() {}

    public static Plan create() {
        Plan plan = new Plan();
        plan.stages.add("Gelände & Plattform");
        plan.stages.add("Mauern & Hafen");
        plan.stages.add("Stadt");
        plan.stages.add("Hauptgebäude");
        plan.stages.add("Details");

        // ---- Etappe 1: Gelaende & Plattform ----
        add(plan, "base", "Inselplattform", 1, "cylinder", "STONE", -220, -10, -200, 440, 10, 400, false, 0);
        add(plan, "bay", "Bucht (Wasser)", 1, "cylinder", "WATER", -200, -10, 200, 400, 10, 200, false, 0);
        add(plan, "plaza", "Plaza", 1, "cylinder", "STONE_BRICKS", -120, 0, -100, 240, 1, 200, false, 0);

        // ---- Etappe 2: Mauern & Hafen ----
        add(plan, "wall", "Ringmauer", 2, "cylinder", "STONE_BRICKS", -200, 0, -180, 400, 30, 360, true, 6);
        for (int i = 0; i < 4; i++) {
            add(plan, "pier" + (i + 1), "Steg " + (i + 1), 2, "box", "SPRUCE_PLANKS", -155 + i * 100, 0, 180, 10, 1, 110, false, 0);
        }
        add(plan, "lighthouse_l", "Leuchtturm links", 2, "cylinder", "WHITE_CONCRETE", -185, 0, 165, 14, 40, 14, true, 2);
        add(plan, "lighthouse_r", "Leuchtturm rechts", 2, "cylinder", "WHITE_CONCRETE", 171, 0, 165, 14, 40, 14, true, 2);

        // ---- Etappe 3: Stadt (Haeuser im Ring zwischen Plaza und Mauer, ohne Nordseite) ----
        int n = 1;
        for (int deg = 0; deg < 360; deg += 12) {
            double t = Math.toRadians(deg);
            if (Math.sin(t) < -0.5) continue;
            int hx = (int) Math.round(0.78 * 194 * Math.cos(t)) - 9;
            int hz = (int) Math.round(0.78 * 174 * Math.sin(t)) - 9;
            add(plan, "house" + n, "Haus " + n, 3, "box", "BRICKS", hx, 1, hz, 18, 14, 18, true, 2);
            n++;
        }

        // ---- Etappe 4: Hauptgebaeude (Nordseite) ----
        add(plan, "hq_center", "Hauptgebäude Mitte", 4, "box", "WHITE_CONCRETE", -45, 1, -155, 90, 60, 55, true, 3);
        add(plan, "hq_center_roof", "Dach Mitte", 4, "box", "BLUE_CONCRETE", -45, 61, -155, 90, 2, 55, false, 0);
        add(plan, "hq_left", "Flügel links", 4, "box", "WHITE_CONCRETE", -110, 1, -145, 60, 42, 45, true, 3);
        add(plan, "hq_left_roof", "Dach links", 4, "box", "BLUE_CONCRETE", -110, 43, -145, 60, 2, 45, false, 0);
        add(plan, "hq_right", "Flügel rechts", 4, "box", "WHITE_CONCRETE", 50, 1, -145, 60, 42, 45, true, 3);
        add(plan, "hq_right_roof", "Dach rechts", 4, "box", "BLUE_CONCRETE", 50, 43, -145, 60, 2, 45, false, 0);
        add(plan, "tower", "Uhrturm", 4, "cylinder", "WHITE_CONCRETE", -12, 63, -138, 24, 38, 24, true, 2);
        add(plan, "dome", "Kuppel", 4, "dome", "LIGHT_BLUE_CONCRETE", -12, 101, -138, 24, 14, 24, false, 0);

        // ---- Etappe 5: Details ----
        add(plan, "execution", "Hinrichtungsplattform", 5, "box", "STONE_BRICKS", -25, 1, -20, 50, 12, 40, false, 0);
        for (int k = 0; k < 8; k++) {
            double t = Math.toRadians(k * 45 + 22.5);
            int px = (int) Math.round(0.92 * 120 * Math.cos(t)) - 3;
            int pz = (int) Math.round(0.92 * 100 * Math.sin(t)) - 3;
            add(plan, "pillar" + (k + 1), "Säule " + (k + 1), 5, "cylinder", "SMOOTH_STONE", px, 1, pz, 6, 20, 6, false, 0);
        }
        return plan;
    }

    private static void add(Plan plan, String id, String name, int stage, String shape, String material,
                            int x, int y, int z, int sx, int sy, int sz, boolean hollow, int wall) {
        Part p = new Part();
        p.id = id;
        p.name = name;
        p.stage = stage;
        p.shape = shape;
        p.material = material;
        p.x = x;
        p.y = y;
        p.z = z;
        p.sx = sx;
        p.sy = sy;
        p.sz = sz;
        p.hollow = hollow;
        p.wall = wall == 0 ? 2 : wall;
        plan.parts.add(p);
    }
}
