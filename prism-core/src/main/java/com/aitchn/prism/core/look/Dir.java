package com.aitchn.prism.core.look;

import org.jetbrains.annotations.NotNull;

/**
 * The six block directions as indices, in the order of {@code Appearances.ALL}: north (-z), east (+x), south (+z),
 * west (-x), up (+y), down (-y). Also the blockstate rotation of a model ({@code x} then {@code y}, clockwise seen from
 * the positive axis, as in a blockstate file).
 */
public final class Dir {

    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;
    public static final int UP = 4;
    public static final int DOWN = 5;

    private static final int[][] OFFSET = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
    private static final String[] NAME = {"north", "east", "south", "west", "up", "down"};

    private Dir() {}

    /** The unit step {@code {dx, dy, dz}} of direction {@code dir}. */
    public static int @NotNull [] offset(int dir) {
        return OFFSET[dir].clone();
    }

    public static @NotNull String name(int dir) {
        return NAME[dir];
    }

    /** The index of the direction called {@code name} (lower case), or -1. */
    public static int of(@NotNull String name) {
        for (int i = 0; i < NAME.length; i++) {
            if (NAME[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    public static int opposite(int dir) {
        return switch (dir) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            default -> UP;
        };
    }

    /**
     * Where the face of a model that looks toward {@code dir} ends up after the blockstate rotation {@code x} then
     * {@code y} (degrees, multiples of 90): {@code x=90} turns north to down, {@code y=90} turns north to east.
     */
    public static int rotate(int dir, int x, int y) {
        int result = dir;
        for (int i = 0; i < Math.floorMod(x, 360) / 90; i++) {
            result = switch (result) { // x=90: north -> down -> south -> up -> north
                case NORTH -> DOWN;
                case DOWN -> SOUTH;
                case SOUTH -> UP;
                case UP -> NORTH;
                default -> result;
            };
        }
        for (int i = 0; i < Math.floorMod(y, 360) / 90; i++) {
            result = switch (result) { // y=90: north -> east -> south -> west -> north
                case NORTH -> EAST;
                case EAST -> SOUTH;
                case SOUTH -> WEST;
                case WEST -> NORTH;
                default -> result;
            };
        }
        return result;
    }

    /**
     * The rotation as a unit quaternion {@code (x, y, z, w)} that turns model-space vectors the way the blockstate
     * rotation does, for a display entity's {@code left_rotation}. A blockstate angle {@code a} is {@code -a} about the
     * positive axis for a right-handed quaternion; the rotation about x is applied first.
     */
    public static float @NotNull [] quaternion(int x, int y) {
        float[] qx = axisAngle(1, 0, 0, -x);
        float[] qy = axisAngle(0, 1, 0, -y);
        return multiply(qy, qx);
    }

    private static float[] axisAngle(float ax, float ay, float az, int degrees) {
        double half = Math.toRadians(degrees) / 2;
        float s = (float) Math.sin(half);
        return new float[] {ax * s, ay * s, az * s, (float) Math.cos(half)};
    }

    /** Hamilton product {@code a * b}: applying {@code b} first, then {@code a}. */
    static float[] multiply(float[] a, float[] b) {
        return new float[] {
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    /** Rotates {@code v} by the unit quaternion {@code q}. */
    public static float @NotNull [] apply(float @NotNull [] q, float @NotNull [] v) {
        float[] p = {v[0], v[1], v[2], 0};
        float[] conj = {-q[0], -q[1], -q[2], q[3]};
        float[] r = multiply(multiply(q, p), conj);
        return new float[] {r[0], r[1], r[2]};
    }
}
