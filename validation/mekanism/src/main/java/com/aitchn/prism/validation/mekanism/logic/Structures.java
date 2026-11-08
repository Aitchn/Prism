package com.aitchn.prism.validation.mekanism.logic;

import java.util.HashMap;
import java.util.Map;

/**
 * Dynamic tanks: hollow boxes that hold one chemical in total, 16,000 mB per interior block. Prism finds the box
 * ({@code prism:cuboid} on the valves); this class only keeps the tanks. Every valve of a box reports the same box
 * corners, so they share one tank. The content stays with the minimum corner while the box is broken, so rebuilding
 * it keeps what was inside. Bukkit-free.
 */
public final class Structures {

    public static final long PER_BLOCK = 16_000;

    /** The outer corners of a box. */
    public record Box(Pos min, Pos max) {
        public int interior() {
            return Math.max(0, max.x() - min.x() - 1) * Math.max(0, max.y() - min.y() - 1)
                    * Math.max(0, max.z() - min.z() - 1);
        }

        public String size() {
            return (max.x() - min.x() + 1) + "x" + (max.y() - min.y() + 1) + "x" + (max.z() - min.z() + 1);
        }
    }

    public record Formed(Box box, ChemicalTank tank) {
    }

    private final Map<Pos, Box> attached = new HashMap<>();
    private final Map<Box, Formed> formed = new HashMap<>();
    private final Map<Pos, String> restored = new HashMap<>();
    private final Map<Pos, String> kept = new HashMap<>();
    private final Map<Pos, Box> lastBox = new HashMap<>();

    /** The tank of the box the valve at {@code valve} belongs to, or null while Prism reports none. */
    public Formed at(Pos valve) {
        Box box = attached.get(valve);
        return box == null ? null : formed.get(box);
    }

    /** The tank a pipe at the valve {@code pos} reaches, or null. */
    public ChemicalTank valveTank(Pos pos) {
        Formed f = at(pos);
        return f == null ? null : f.tank();
    }

    /** Content found in a valve's block data when it loaded: applied when the box that valve belongs to forms. */
    public void restore(Pos valve, String text) {
        restored.put(valve, text);
    }

    /** The valve stands in {@code box} (as Prism reports it). Moves it if it was in another one. */
    public void attach(Pos valve, Box box) {
        Box before = attached.get(valve);
        if (box.equals(before)) {
            return;
        }
        if (before != null) {
            detach(valve, false);
        }
        String own = restored.remove(valve); // used once: later content comes from the tank itself
        attached.put(valve, box);
        lastBox.put(valve, box);
        if (formed.containsKey(box)) {
            return;
        }
        ChemicalTank tank = new ChemicalTank((long) box.interior() * PER_BLOCK).asBuffer();
        String text = kept.remove(box.min());
        apply(tank, text == null ? own : text);
        formed.put(box, new Formed(box, tank));
    }

    /**
     * The valve is no longer part of a box: it was broken, or its chunk unloads. While the box is broken its content
     * is kept for the minimum corner, so rebuilding it brings the content back and the valves can still save it.
     * With {@code unloading} the valve's own block data holds the content from now on.
     */
    public void detach(Pos valve, boolean unloading) {
        Box box = attached.remove(valve);
        if (box != null && !attached.containsValue(box)) {
            Formed f = formed.remove(box);
            if (f != null && f.tank().type() != null) {
                kept.put(box.min(), text(f.tank()));
            }
        }
        if (unloading) {
            Box last = lastBox.remove(valve);
            restored.remove(valve);
            if (last != null && !lastBox.containsValue(last) && !formed.containsKey(last)) {
                kept.remove(last.min());
            }
        }
    }

    /** The content to save in the valve's data: what its box holds, or what was kept while the box is broken. */
    public String contentOf(Pos valve) {
        Formed f = at(valve);
        if (f != null) {
            return f.tank().type() == null ? null : text(f.tank());
        }
        Box last = lastBox.get(valve);
        if (last != null) {
            return kept.get(last.min());
        }
        return restored.get(valve);
    }

    private static String text(ChemicalTank tank) {
        return tank.type().id() + "," + tank.amount();
    }

    private static void apply(ChemicalTank tank, String text) {
        if (text == null) {
            return;
        }
        int comma = text.lastIndexOf(',');
        try {
            long amount = Math.min(tank.capacity(), Long.parseLong(text.substring(comma + 1)));
            tank.set(new Chemical(text.substring(0, comma)), amount);
        } catch (RuntimeException e) {
            // damaged: start empty
        }
    }
}
