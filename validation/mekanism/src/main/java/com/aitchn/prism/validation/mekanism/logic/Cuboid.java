package com.aitchn.prism.validation.mekanism.logic;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Validates a Mekanism-style hollow cuboid (dynamic tank, boiler, evaporation tower): a box of "casing" blocks with
 * an empty interior. The seed is any casing block of the structure.
 */
public final class Cuboid {

    public record Result(boolean valid, String problem, Pos min, Pos max, int interiorVolume) {
        static Result fail(String problem) {
            return new Result(false, problem, null, null, 0);
        }
    }

    private Cuboid() {}

    public static Result validate(Pos seed, Predicate<Pos> isCasing, Predicate<Pos> isInteriorAllowed, int minSize,
            int maxSize) {
        if (!isCasing.test(seed)) {
            return Result.fail("seed is not a casing block");
        }
        Set<Pos> casing = new HashSet<>();
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        queue.add(seed);
        casing.add(seed);
        int budget = maxSize * maxSize * 6 + 16;
        while (!queue.isEmpty()) {
            Pos current = queue.poll();
            for (Pos n : current.neighbors()) {
                if (isCasing.test(n) && casing.add(n)) {
                    if (casing.size() > budget) {
                        return Result.fail("structure is too large");
                    }
                    queue.add(n);
                }
            }
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Pos p : casing) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
        }
        int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
        if (Math.min(sx, Math.min(sy, sz)) < minSize || Math.max(sx, Math.max(sy, sz)) > maxSize) {
            return Result.fail("size " + sx + "x" + sy + "x" + sz + " outside " + minSize + ".." + maxSize);
        }
        String world = seed.world();
        int interior = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Pos p = new Pos(world, x, y, z);
                    boolean shell = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                    if (shell && !casing.contains(p)) {
                        return Result.fail("hole in the casing at " + p);
                    }
                    if (!shell) {
                        if (casing.contains(p) || !isInteriorAllowed.test(p)) {
                            return Result.fail("interior blocked at " + p);
                        }
                        interior++;
                    }
                }
            }
        }
        return new Result(true, null, new Pos(world, minX, minY, minZ), new Pos(world, maxX, maxY, maxZ), interior);
    }
}
