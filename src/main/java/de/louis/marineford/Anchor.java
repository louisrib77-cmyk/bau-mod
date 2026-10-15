package de.louis.marineford;

/** Ankerpunkt: Weltkoordinate, auf die alle relativen Plan-Koordinaten bezogen werden. */
public final class Anchor {
    public String world;
    public int x, y, z;

    public Anchor() {}

    public Anchor(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
