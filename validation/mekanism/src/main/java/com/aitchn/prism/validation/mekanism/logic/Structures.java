package com.aitchn.prism.validation.mekanism.logic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dynamic tanks: hollow boxes of casing blocks (tank blocks, valves, structural glass) that hold one chemical in total,
 * 16,000 mB per interior block. Valves are the doors pipes use. The structure is found again whenever a member appears
 * or goes; its content stays with the minimum corner (the anchor) so it survives a rebuild. Bukkit-free.
 */
public final class Structures {

    public static final long PER_BLOCK = 16_000;
    public static final int MIN = 3;
    public static final int MAX = 18;

    public record Formed(Pos anchor, Pos min, Pos max, int interior, Set<Pos> members, ChemicalTank tank) {
        public String size() {
            return (max.x() - min.x() + 1) + "x" + (max.y() - min.y() + 1) + "x" + (max.z() - min.z() + 1);
        }
    }

    private final Set<Pos> members = new HashSet<>();
    private final Set<Pos> valves = new HashSet<>();
    private final Map<Pos, String> kept = new HashMap<>();
    private final Map<Pos, Formed> byPos = new HashMap<>();
    private List<Formed> formed = List.of();
    private final Map<Pos, Set<Pos>> foreign = new HashMap<>();
    private Map<Pos, Cuboid.Result> boxes = Map.of();

    public Set<Pos> members() {
        return members;
    }

    public Set<Pos> valves() {
        return valves;
    }

    public List<Formed> formed() {
        return formed;
    }

    /** The structure {@code pos} belongs to, or null while it is not part of a complete one. */
    public Formed at(Pos pos) {
        return byPos.get(pos);
    }

    /** The tank a pipe at a valve {@code pos} reaches, or null. */
    public ChemicalTank valveTank(Pos pos) {
        Formed f = valves.contains(pos) ? byPos.get(pos) : null;
        return f == null ? null : f.tank();
    }

    /** Content found in an anchor's block data when it loaded: applied the next time a structure forms there. */
    public void restore(Pos anchor, String text) {
        kept.put(anchor, text);
    }

    /** The content to save for the block at {@code anchor}: what the structure holds, or what was kept for it. */
    public String contentOf(Pos anchor) {
        Formed f = byPos.get(anchor);
        if (f != null && f.anchor().equals(anchor)) {
            return f.tank().type() == null ? null : f.tank().type().id() + "," + f.tank().amount();
        }
        return kept.get(anchor);
    }

    public void forget(Pos anchor) {
        kept.remove(anchor);
    }

    /** Finds the structures again. Content of structures that stopped being whole is kept by their anchor. */
    public void rebuild() {
        rebuild(p -> false);
    }

    /**
     * Whole boxes of casing, formed or not, by minimum corner. The adapter looks inside them in the world, because
     * stone or a vanilla block in the middle is not hollow, and reports it with {@link #setInteriorBlocked}.
     */
    public Map<Pos, Cuboid.Result> boxes() {
        return boxes;
    }

    /** Records which interior positions of the box at {@code anchor} hold a non-air block. True when that changed. */
    public boolean setInteriorBlocked(Pos anchor, Set<Pos> blocked) {
        Set<Pos> before = foreign.get(anchor);
        if (blocked.isEmpty()) {
            return foreign.remove(anchor) != null;
        }
        if (blocked.equals(before)) {
            return false;
        }
        foreign.put(anchor, Set.copyOf(blocked));
        return true;
    }

    /** As {@link #rebuild()}; {@code occupied} says which other positions hold a known block, which spoils a hollow. */
    public void rebuild(java.util.function.Predicate<Pos> occupied) {
        Map<Pos, String> contents = new HashMap<>(kept);
        for (Formed f : formed) {
            if (f.tank().type() != null) {
                contents.put(f.anchor(), f.tank().type().id() + "," + f.tank().amount());
            }
        }
        List<Formed> next = new java.util.ArrayList<>();
        Map<Pos, Cuboid.Result> seen = new HashMap<>();
        byPos.clear();
        for (Set<Pos> component : Networks.components(members)) {
            Pos seed = component.iterator().next();
            Cuboid.Result result = Cuboid.validate(seed, members::contains,
                    p -> !members.contains(p) && !occupied.test(p), MIN, MAX);
            if (!result.valid() || component.size() != shellSize(result)) {
                continue;
            }
            seen.put(result.min(), result);
            boolean blocked = false;
            Set<Pos> stuck = foreign.getOrDefault(result.min(), Set.of());
            for (Pos p : stuck) {
                blocked |= p.x() > result.min().x() && p.x() < result.max().x() && p.y() > result.min().y()
                        && p.y() < result.max().y() && p.z() > result.min().z() && p.z() < result.max().z();
            }
            if (blocked) {
                continue;
            }
            ChemicalTank tank = new ChemicalTank((long) result.interiorVolume() * PER_BLOCK).asBuffer();
            String saved = contents.remove(result.min());
            if (saved != null) {
                int comma = saved.lastIndexOf(',');
                try {
                    tank.set(new Chemical(saved.substring(0, comma)), Long.parseLong(saved.substring(comma + 1)));
                } catch (RuntimeException e) {
                    // damaged: start empty
                }
            }
            Formed f = new Formed(result.min(), result.min(), result.max(), result.interiorVolume(), component, tank);
            next.add(f);
            for (Pos p : component) {
                byPos.put(p, f);
            }
        }
        boxes = Map.copyOf(seen);
        foreign.keySet().retainAll(seen.keySet());
        kept.clear();
        kept.putAll(contents);
        formed = List.copyOf(next);
    }

    /** Blocks on the shell of a box: the structure must be exactly that, no extra casing stuck on. */
    private static int shellSize(Cuboid.Result r) {
        int sx = r.max().x() - r.min().x() + 1;
        int sy = r.max().y() - r.min().y() + 1;
        int sz = r.max().z() - r.min().z() + 1;
        return sx * sy * sz - (sx - 2) * (sy - 2) * (sz - 2);
    }
}
