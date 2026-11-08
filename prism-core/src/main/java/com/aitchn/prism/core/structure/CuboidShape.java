package com.aitchn.prism.core.structure;

import com.aitchn.prism.api.content.PrismComponents;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A compiled {@code prism:cuboid}: a hollow box of variable size whose shell is made of wall blocks and whose inside is
 * air (or allowed inside blocks). The controller sits on a face of the shell, not on an edge or corner.
 *
 * <p>Finding it: for each side of the controller that is open (air or an allowed inside block), walk from that cell
 * along each axis until a block that is neither. That gives the inside box, and one more layer around it is the
 * shell. Then every shell cell must be a wall, every inside cell air or an allowed block, and each outer side must
 * measure {@code min} to {@code max}.
 */
public final class CuboidShape implements StructureShape {

    /** The six directions, in this order, as names and offsets. */
    static final List<String> SIDES = List.of("NORTH", "EAST", "SOUTH", "WEST", "UP", "DOWN");
    private static final int[][] STEP = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private final PrismComponents.Cuboid spec;

    public CuboidShape(@NotNull PrismComponents.Cuboid spec) {
        this.spec = spec;
    }

    @Override
    public int reach() {
        return spec.max() - 1;
    }

    @Override
    public @NotNull Result find(@NotNull Probe probe) {
        boolean uncertain = false;
        for (int side = 0; side < SIDES.size(); side++) {
            int[] s = STEP[side];
            Boolean open = open(probe, s[0], s[1], s[2]);
            if (open == null) {
                uncertain = true;
                continue;
            }
            if (!open) {
                continue;
            }
            int[] lo = {s[0], s[1], s[2]};
            int[] hi = {s[0], s[1], s[2]};
            boolean walked = true;
            for (int axis = 0; axis < 3 && walked; axis++) {
                for (int sign = -1; sign <= 1 && walked; sign += 2) {
                    int[] at = {s[0], s[1], s[2]};
                    while (true) {
                        at[axis] += sign;
                        if (at[0] == 0 && at[1] == 0 && at[2] == 0) {
                            break; // back at the controller: the shell
                        }
                        Boolean air = open(probe, at[0], at[1], at[2]);
                        if (air == null) {
                            uncertain = true;
                            walked = false;
                            break;
                        }
                        if (!air) {
                            break;
                        }
                        if (Math.abs(at[axis] - s[axis]) > spec.max() - 2) {
                            walked = false; // the inside is already wider than allowed
                            break;
                        }
                    }
                    if (sign < 0) {
                        lo[axis] = at[axis] + 1;
                    } else {
                        hi[axis] = at[axis] - 1;
                    }
                }
            }
            if (!walked) {
                continue;
            }
            Result found = check(probe, side, lo, hi);
            if (found.match() != null) {
                return found;
            }
            uncertain |= found.uncertain();
        }
        return uncertain ? Result.UNKNOWN : Result.NONE;
    }

    @Override
    public @NotNull Result verify(@NotNull Probe probe, @NotNull String signature) {
        String[] parts = signature.split(" ");
        int side = SIDES.indexOf(parts[0]);
        if (side < 0 || parts.length != 3) {
            return Result.NONE;
        }
        try {
            String[] low = parts[1].split(",");
            String[] high = parts[2].split(",");
            int[] lo = new int[3];
            int[] hi = new int[3];
            for (int axis = 0; axis < 3; axis++) {
                lo[axis] = Integer.parseInt(low[axis]) + 1;
                hi[axis] = Integer.parseInt(high[axis]) - 1;
            }
            return check(probe, side, lo, hi);
        } catch (RuntimeException malformed) {
            return Result.NONE;
        }
    }

    /** Checks the shell around the inside box {@code lo..hi} (offsets from the controller). */
    private Result check(Probe probe, int side, int[] lo, int[] hi) {
        for (int axis = 0; axis < 3; axis++) {
            int outer = hi[axis] - lo[axis] + 3;
            if (outer < spec.min() || outer > spec.max()) {
                return Result.NONE;
            }
        }
        List<Offset> members = new ArrayList<>();
        List<Offset> watched = new ArrayList<>();
        boolean uncertain = false;
        // Shell first: a wrong wall is the usual reason to fail, and it is cheaper than the inside.
        for (int x = lo[0] - 1; x <= hi[0] + 1; x++) {
            for (int y = lo[1] - 1; y <= hi[1] + 1; y++) {
                for (int z = lo[2] - 1; z <= hi[2] + 1; z++) {
                    boolean shell = x < lo[0] || x > hi[0] || y < lo[1] || y > hi[1] || z < lo[2] || z > hi[2];
                    if (!shell || (x == 0 && y == 0 && z == 0)) {
                        continue;
                    }
                    Boolean wall = any(probe, x, y, z, spec.walls());
                    if (wall == null) {
                        uncertain = true;
                    } else if (!wall) {
                        return Result.NONE;
                    }
                    Offset offset = new Offset(x, y, z);
                    members.add(offset);
                    watched.add(offset);
                }
            }
        }
        if (uncertain) {
            return Result.UNKNOWN;
        }
        for (int x = lo[0]; x <= hi[0]; x++) {
            for (int y = lo[1]; y <= hi[1]; y++) {
                for (int z = lo[2]; z <= hi[2]; z++) {
                    Boolean air = probe.is(x, y, z, "air");
                    if (air == null) {
                        return Result.UNKNOWN;
                    }
                    Offset offset = new Offset(x, y, z);
                    watched.add(offset);
                    if (air) {
                        continue;
                    }
                    Boolean allowed = any(probe, x, y, z, spec.inside());
                    if (allowed == null) {
                        return Result.UNKNOWN;
                    }
                    if (!allowed) {
                        return Result.NONE;
                    }
                    members.add(offset);
                }
            }
        }
        String facing = SIDES.get(side);
        String signature = facing + " " + (lo[0] - 1) + "," + (lo[1] - 1) + "," + (lo[2] - 1) + " " + (hi[0] + 1)
                + "," + (hi[1] + 1) + "," + (hi[2] + 1);
        return new Result(new Match(facing, signature, List.copyOf(members), List.copyOf(watched)), false);
    }

    /** Air or an allowed inside block: what the walk that measures the inside passes through. */
    private Boolean open(Probe probe, int x, int y, int z) {
        Boolean air = probe.is(x, y, z, "air");
        return Boolean.FALSE.equals(air) ? any(probe, x, y, z, spec.inside()) : air;
    }

    private static Boolean any(Probe probe, int x, int y, int z, List<String> keys) {
        boolean unknown = false;
        for (String key : keys) {
            Boolean is = probe.is(x, y, z, key);
            if (Boolean.TRUE.equals(is)) {
                return true;
            }
            unknown |= is == null;
        }
        return unknown ? null : false;
    }
}
