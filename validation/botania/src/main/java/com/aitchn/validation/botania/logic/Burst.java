package com.aitchn.validation.botania.logic;

/**
 * A packet of mana flying in a straight line. It is a state machine instead of an entity so it can be advanced a
 * few blocks at a time on whichever Folia region owns its current position.
 */
public final class Burst {

    public enum Outcome {
        /** Reached a collector; {@link Result#delivered()} is what it accepted. */
        HIT,
        /** Stopped by a solid block. */
        ABSORBED,
        /** Ran out of range. */
        EXPIRED,
        /** The next block belongs to another region; call {@code advance} again from there. */
        NEED_REGION
    }

    public record Result(Outcome outcome, Pos at, int delivered) {}

    private static final double STEP = 0.25;

    private final Pos origin;
    private final double dx;
    private final double dy;
    private final double dz;
    private final int mana;
    private final double range;
    private double x;
    private double y;
    private double z;
    private double travelled;

    public Burst(Pos origin, double dx, double dy, double dz, int mana, double range) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0) {
            throw new IllegalArgumentException("direction must not be zero");
        }
        this.origin = origin;
        this.dx = dx / length;
        this.dy = dy / length;
        this.dz = dz / length;
        this.mana = mana;
        this.range = range;
        this.x = origin.x() + 0.5;
        this.y = origin.y() + 0.5;
        this.z = origin.z() + 0.5;
    }

    public int mana() {
        return mana;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public Result advance(WorldView world, ManaNetwork network) {
        Pos current = blockAt();
        while (travelled < range) {
            double nx = x + dx * STEP;
            double ny = y + dy * STEP;
            double nz = z + dz * STEP;
            Pos next = new Pos(origin.world(), (int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz));
            if (!next.equals(current)) {
                if (!world.isOwned(next)) {
                    return new Result(Outcome.NEED_REGION, next, 0);
                }
                var collector = network.collector(next);
                if (collector.isPresent() && !next.equals(origin)) {
                    int delivered = collector.get().receive(mana);
                    return new Result(Outcome.HIT, next, delivered);
                }
                if (world.isSolid(next)) {
                    return new Result(Outcome.ABSORBED, next, 0);
                }
                current = next;
            }
            x = nx;
            y = ny;
            z = nz;
            travelled += STEP;
        }
        return new Result(Outcome.EXPIRED, current, 0);
    }

    private Pos blockAt() {
        return new Pos(origin.world(), (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
