package com.aitchn.prism.core.structure;

import com.aitchn.prism.api.content.PrismComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/**
 * A compiled {@code prism:structure}: every cell of the pattern as an offset from the controller, per rotation.
 * Layer index is y (bottom up), row is z (north to south), column is x (west to east). Rotation 0 is the pattern as
 * written (facing north); 1, 2 and 3 turn it clockwise to face east, south and west.
 */
public final class StructurePattern implements StructureShape {

    /** One cell to check: an offset from the controller and what must be there. {@code expect} is a block key or "air". */
    public record Cell(int dx, int dy, int dz, @NotNull String expect) {}

    public static final List<String> FACINGS = List.of("NORTH", "EAST", "SOUTH", "WEST");

    private final List<List<Cell>> rotations;
    private final int reach;

    private StructurePattern(List<List<Cell>> rotations, int reach) {
        this.rotations = rotations;
        this.reach = reach;
    }

    public static @NotNull StructurePattern compile(@NotNull PrismComponents.Structure structure) {
        int cx = 0;
        int cy = 0;
        int cz = 0;
        List<String[]> layers = new ArrayList<>();
        for (int y = 0; y < structure.layers().size(); y++) {
            String[] rows = structure.layers().get(y).split("/", -1);
            layers.add(rows);
            for (int z = 0; z < rows.length; z++) {
                int x = rows[z].indexOf('@');
                if (x >= 0) {
                    cx = x;
                    cy = y;
                    cz = z;
                }
            }
        }
        List<Cell> base = new ArrayList<>();
        for (int y = 0; y < layers.size(); y++) {
            String[] rows = layers.get(y);
            for (int z = 0; z < rows.length; z++) {
                for (int x = 0; x < rows[z].length(); x++) {
                    char c = rows[z].charAt(x);
                    if (c == ' ' || c == '@') {
                        continue;
                    }
                    String expect = c == '.' ? "air" : structure.key().get(c);
                    base.add(new Cell(x - cx, y - cy, z - cz, expect));
                }
            }
        }
        List<List<Cell>> rotations = new ArrayList<>();
        int turns = structure.rotate() ? 4 : 1;
        int reach = 0;
        for (int turn = 0; turn < turns; turn++) {
            List<Cell> cells = new ArrayList<>();
            for (Cell cell : base) {
                int dx = cell.dx();
                int dz = cell.dz();
                for (int i = 0; i < turn; i++) {
                    int t = dx;
                    dx = -dz; // clockwise seen from above: north (-z) becomes east (+x)
                    dz = t;
                }
                cells.add(new Cell(dx, cell.dy(), dz, cell.expect()));
                reach = Math.max(reach, Math.max(Math.abs(dx), Math.max(Math.abs(cell.dy()), Math.abs(dz))));
            }
            rotations.add(List.copyOf(cells));
        }
        return new StructurePattern(List.copyOf(rotations), reach);
    }

    /** The cells of rotation {@code turn}. */
    public @NotNull List<Cell> cells(int turn) {
        return rotations.get(turn);
    }

    public int rotations() {
        return rotations.size();
    }

    @Override
    public int reach() {
        return reach;
    }

    /**
     * The first rotation that stands. Uncertain when none does and some cell of some rotation could not be looked at.
     */
    @Override
    public @NotNull Result find(@NotNull Probe probe) {
        boolean uncertain = false;
        for (int turn = 0; turn < rotations.size(); turn++) {
            boolean stands = true;
            for (Cell cell : rotations.get(turn)) {
                Boolean is = probe.is(cell.dx(), cell.dy(), cell.dz(), cell.expect());
                if (is == null) {
                    uncertain = true;
                }
                if (!Boolean.TRUE.equals(is)) {
                    stands = false;
                }
            }
            if (stands) {
                List<Offset> members = new ArrayList<>();
                List<Offset> watched = new ArrayList<>();
                for (Cell cell : rotations.get(turn)) {
                    Offset offset = new Offset(cell.dx(), cell.dy(), cell.dz());
                    watched.add(offset);
                    if (!cell.expect().equals("air")) {
                        members.add(offset);
                    }
                }
                String facing = FACINGS.get(turn);
                return new Result(new Match(facing, facing, List.copyOf(members), List.copyOf(watched)), false);
            }
        }
        return uncertain ? Result.UNKNOWN : Result.NONE;
    }

    @Override
    public @NotNull Result verify(@NotNull Probe probe, @NotNull String signature) {
        int turn = FACINGS.indexOf(signature);
        if (turn < 0 || turn >= rotations.size()) {
            return Result.NONE;
        }
        boolean uncertain = false;
        for (Cell cell : rotations.get(turn)) {
            Boolean is = probe.is(cell.dx(), cell.dy(), cell.dz(), cell.expect());
            if (Boolean.FALSE.equals(is)) {
                return Result.NONE;
            }
            uncertain |= is == null;
        }
        return uncertain ? Result.UNKNOWN : find(probe);
    }

    /** The first rotation whose every cell passes {@code matches}, or -1. */
    public int match(@NotNull Predicate<Cell> matches) {
        for (int turn = 0; turn < rotations.size(); turn++) {
            if (rotations.get(turn).stream().allMatch(matches)) {
                return turn;
            }
        }
        return -1;
    }
}
