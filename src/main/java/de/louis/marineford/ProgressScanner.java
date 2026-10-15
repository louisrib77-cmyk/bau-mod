package de.louis.marineford;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vergleicht die echte Welt mit dem Plan. Laedt bereits generierte Chunks einzeln (ein Chunk pro Tick,
 * ohne neue Chunks zu generieren) und zaehlt passende Bloecke pro Bauteil und Layer.
 */
public final class ProgressScanner {

    private final MarinefordPlugin plugin;
    private final PlanStore store;
    private volatile boolean scanning;
    private volatile boolean cancelled;
    private volatile long lastScan;
    private volatile int chunksTotal;
    private volatile int chunksDone;

    public ProgressScanner(MarinefordPlugin plugin, PlanStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public boolean isScanning() { return scanning; }
    public long lastScan() { return lastScan; }
    public int chunksTotal() { return chunksTotal; }
    public int chunksDone() { return chunksDone; }

    public void cancel() { cancelled = true; }

    /** Nur auf dem Hauptthread aufrufen. */
    public void start() {
        if (scanning) return;
        Anchor a = store.anchor();
        if (a == null) return;
        World world = Bukkit.getWorld(a.world);
        if (world == null) {
            plugin.getLogger().warning("Scan: Welt '" + a.world + "' nicht gefunden.");
            return;
        }
        Plan plan = store.plan();
        for (Part p : plan.parts) p.resetProgress();

        Map<Long, List<Part>> work = new LinkedHashMap<>();
        for (Part p : plan.parts) {
            int minCx = Math.floorDiv(a.x + p.x, 16), maxCx = Math.floorDiv(a.x + p.x + p.sx - 1, 16);
            int minCz = Math.floorDiv(a.z + p.z, 16), maxCz = Math.floorDiv(a.z + p.z + p.sz - 1, 16);
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    work.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(p);
                }
            }
        }
        ArrayDeque<Long> queue = new ArrayDeque<>(work.keySet());
        cancelled = false;
        scanning = true;
        chunksTotal = queue.size();
        chunksDone = 0;
        next(world, a, work, queue);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    private void next(World world, Anchor a, Map<Long, List<Part>> work, ArrayDeque<Long> queue) {
        if (cancelled) {
            scanning = false;
            return;
        }
        Long key = queue.poll();
        if (key == null) {
            finish();
            return;
        }
        final int cx = (int) (key >> 32);
        final int cz = (int) (long) key;
        world.getChunkAtAsync(cx, cz, false).whenComplete((chunk, err) -> {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (chunk != null && err == null) {
                            process(world, a, chunk, cx, cz, work.get(key));
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Scan-Fehler bei Chunk " + cx + "," + cz + ": " + t);
                    }
                    chunksDone++;
                    next(world, a, work, queue);
                });
            } catch (Throwable t) {
                // Plugin wurde waehrend des Scans deaktiviert
                scanning = false;
            }
        });
    }

    private void process(World world, Anchor a, Chunk chunk, int cx, int cz, List<Part> parts) {
        ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int cMinX = cx * 16, cMinZ = cz * 16;
        for (Part p : parts) {
            int bx = a.x + p.x, bz = a.z + p.z, by = a.y + p.y;
            int x0 = Math.max(cMinX, bx), x1 = Math.min(cMinX + 15, bx + p.sx - 1);
            int z0 = Math.max(cMinZ, bz), z1 = Math.min(cMinZ + 15, bz + p.sz - 1);
            for (int ly = 0; ly < p.sy; ly++) {
                int wy = by + ly;
                if (wy < minY || wy >= maxY) continue;
                for (int wz = z0; wz <= z1; wz++) {
                    for (int wx = x0; wx <= x1; wx++) {
                        if (!p.contains(wx - bx, ly, wz - bz)) continue;
                        p.checked++;
                        if (snap.getBlockType(wx - cMinX, wy, wz - cMinZ) == p.mat) {
                            p.done++;
                            p.layerDone[ly]++;
                        }
                    }
                }
            }
        }
    }

    private void finish() {
        scanning = false;
        lastScan = System.currentTimeMillis();
        plugin.refreshMarkers();
    }
}
