package com.aitchn.prism.validation.mekanism.logic;

import java.util.Comparator;
import java.util.List;

/**
 * Teleporter frequencies and costs. A teleporter keeps "f:<name>:<counter>" as its attachment: the frequency it is
 * tuned to and how many trips it has started (the counter picks the next target in turn). Teleporters with the same
 * name are linked; the empty name is the public frequency.
 */
public final class Teleport {

    public static final long BASE_COST = 2_000;
    public static final long PER_BLOCK = 20;
    public static final long OTHER_WORLD = 50_000;

    private Teleport() {}

    public record Tuning(String frequency, int counter) {

        public String save() {
            return "f:" + frequency + ":" + counter;
        }

        public Tuning next() {
            return new Tuning(frequency, counter + 1);
        }

        public Tuning tuned(String name) {
            return new Tuning(name, counter);
        }

        /** Reads an attachment; anything else is the public frequency. */
        public static Tuning parse(String attachment) {
            if (attachment == null || !attachment.startsWith("f:")) {
                return new Tuning("", 0);
            }
            int last = attachment.lastIndexOf(':');
            if (last <= 1) {
                return new Tuning("", 0);
            }
            try {
                return new Tuning(attachment.substring(2, last), Integer.parseInt(attachment.substring(last + 1)));
            } catch (NumberFormatException e) {
                return new Tuning(attachment.substring(2, last), 0);
            }
        }
    }

    /** Joules for one trip. */
    public static long cost(Pos from, Pos to) {
        if (!from.world().equals(to.world())) {
            return OTHER_WORLD;
        }
        double dx = from.x() - to.x();
        double dy = from.y() - to.y();
        double dz = from.z() - to.z();
        return BASE_COST + PER_BLOCK * Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /**
     * The target of the next trip among the other teleporters of the frequency, taken in a fixed order (world, x, y, z)
     * so the same teleporter visits all of them in turn; null when it is alone.
     */
    public static Pos pick(Pos from, List<Pos> sameFrequency, int counter) {
        List<Pos> others = sameFrequency.stream().filter(p -> !p.equals(from))
                .sorted(Comparator.comparing(Pos::world).thenComparingInt(Pos::x).thenComparingInt(Pos::y)
                        .thenComparingInt(Pos::z)).toList();
        return others.isEmpty() ? null : others.get(Math.floorMod(counter, others.size()));
    }
}
