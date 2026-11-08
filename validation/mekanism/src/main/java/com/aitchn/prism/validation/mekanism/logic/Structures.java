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
        Map<Pos, String> contents = new HashMap<>(kept);
        for (Formed f : formed) {
            if (f.tank().type() != null) {
                contents.put(f.anchor(), f.tank().type().id() + "," + f.tank().amount());
            }
        }
        List<Formed> next = new java.util.ArrayList<>();
        byPos.clear();
        for (Set<Pos> component : Networks.components(members)) {
            Pos seed = component.iterator().next();
            Cuboid.Result result = Cuboid.validate(seed, members::contains, p -> !members.contains(p), MIN, MAX);
            if (!result.valid() || component.size() != shellSize(result)) {
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
