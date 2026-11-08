package com.aitchn.prism.validation.mekanism.logic;

/**
 * The scan state of a digital miner: a square of columns around the miner, walked layer by layer from just below the
 * miner down to a depth limit. Bukkit-free; the adapter turns offsets into blocks. A mined block costs
 * {@link #COST_PER_BLOCK} joules, looking at one costs {@link #COST_PER_LOOK}.
 */
public final class Miner {

    public static final int MAX_RADIUS = 16;
    public static final int[] RADII = {4, 8, 12, 16};
    public static final long COST_PER_BLOCK = 1_000;
    public static final long COST_PER_LOOK = 2;
    /** Positions looked at per tick at most. */
    public static final int LOOKS_PER_TICK = 32;

    private int radius = 8;
    private int index;
    private int pending;

    public int radius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(1, Math.min(MAX_RADIUS, radius));
        this.index = 0;
    }

    /** The next bigger radius of {@link #RADII}, wrapping around; the scan starts again. */
    public int nextRadius() {
        for (int r : RADII) {
            if (r > radius) {
                setRadius(r);
                return radius;
            }
        }
        setRadius(RADII[0]);
        return radius;
    }

    public int index() {
        return index;
    }

    public void reset() {
        index = 0;
    }

    /** Looks at {@code count} positions were handed out and not yet reported back. */
    public int pending() {
        return pending;
    }

    public void begin(int count) {
        pending += count;
    }

    public void finish() {
        pending = Math.max(0, pending - 1);
    }

    public int columns() {
        int side = 2 * radius + 1;
        return side * side;
    }

    /** Whether every position down to {@code depth} blocks below the miner has been looked at. */
    public boolean done(int depth) {
        return index >= (long) columns() * depth;
    }

    /** Offset {dx, dy, dz} (dy negative) of the next position, or null when done. Does not advance. */
    public int[] next(int depth) {
        if (done(depth)) {
            return null;
        }
        int area = columns();
        int side = 2 * radius + 1;
        int layer = index / area;
        int in = index % area;
        return new int[] {in % side - radius, -(layer + 1), in / side - radius};
    }

    /** A position that could not be mined yet (no power, no signal): look again from there. Never moves forward. */
    public void rewind(int toIndex) {
        index = Math.max(0, Math.min(index, toIndex));
    }

    public void advance() {
        index++;
    }

    /** True when {@code blockId} is something the miner takes: ores. */
    public static boolean accepts(String blockId) {
        return blockId.endsWith("_ore") || blockId.equals("minecraft:ancient_debris");
    }

    public String save() {
        return "r" + radius + ":" + index;
    }

    public void load(String text) {
        try {
            String[] parts = text.substring(1).split(":");
            radius = Math.max(1, Math.min(MAX_RADIUS, Integer.parseInt(parts[0])));
            index = Math.max(0, Integer.parseInt(parts[1]));
        } catch (RuntimeException e) {
            // damaged: keep the defaults
        }
    }
}
