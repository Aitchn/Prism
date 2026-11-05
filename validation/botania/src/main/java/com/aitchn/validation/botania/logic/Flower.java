package com.aitchn.validation.botania.logic;

import java.util.List;
import java.util.Optional;

/** A mana flower. Generating flowers fill an internal buffer and push it to the collector they are bound to. */
public final class Flower implements BlockEntity {

    public static final double BIND_RANGE = 6.0;
    private static final int DAYBLOOM_CAPACITY = 500;
    private static final int ENDOFLAME_CAPACITY = 300;
    private static final int ENDOFLAME_PER_TICK = 3;
    private static final double FUEL_RADIUS = 3.0;
    static final int DAISY_TICKS = 150;

    private final Pos pos;
    private final FlowerType type;
    private int buffer;
    private int burnTicks;
    private int daisyProgress;
    private Pos bound;

    public Flower(Pos pos, FlowerType type, int buffer, Pos bound) {
        this.pos = pos;
        this.type = type;
        this.buffer = buffer;
        this.bound = bound;
    }

    @Override
    public Pos pos() {
        return pos;
    }

    public FlowerType type() {
        return type;
    }

    public synchronized int buffer() {
        return buffer;
    }

    public synchronized Optional<Pos> bound() {
        return Optional.ofNullable(bound);
    }

    /** Binds to a collector in range. Returns false (and changes nothing) when it is too far or in another world. */
    public synchronized boolean bind(Pos target) {
        if (!target.world().equals(pos.world()) || pos.distanceSquared(target) > BIND_RANGE * BIND_RANGE) {
            return false;
        }
        bound = target;
        return true;
    }

    @Override
    public synchronized void tick(WorldView world, ManaNetwork network) {
        switch (type) {
            case DAYBLOOM -> {
                if (world.isDaylight(pos) && buffer < DAYBLOOM_CAPACITY) {
                    buffer++;
                }
            }
            case ENDOFLAME -> tickEndoflame(world);
            case PURE_DAISY -> tickDaisy(world);
        }
        push(world, network);
    }

    private void tickEndoflame(WorldView world) {
        if (burnTicks <= 0) {
            List<String> items = world.droppedItems(pos, FUEL_RADIUS);
            for (String item : items) {
                int burn = Recipes.burnTime(item);
                if (burn > 0 && world.consumeDroppedItem(pos, FUEL_RADIUS, item)) {
                    burnTicks = burn;
                    break;
                }
            }
        }
        if (burnTicks > 0 && buffer + ENDOFLAME_PER_TICK <= ENDOFLAME_CAPACITY) {
            buffer += ENDOFLAME_PER_TICK;
            burnTicks--;
        }
    }

    private void tickDaisy(WorldView world) {
        daisyProgress++;
        if (daisyProgress < DAISY_TICKS) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Pos target = pos.offset(dx, 0, dz);
                if (!world.isOwned(target)) {
                    continue;
                }
                String result = world.recipes().conversion(world.blockId(target));
                if (result != null && world.convert(target, result)) {
                    daisyProgress = 0;
                    return;
                }
            }
        }
        daisyProgress = DAISY_TICKS; // nothing to convert: stay ready, check again next tick
    }

    private void push(WorldView world, ManaNetwork network) {
        if (buffer <= 0 || bound == null) {
            return;
        }
        var collector = world.collectorAt(bound, network);
        if (collector.isPresent()) {
            buffer -= collector.get().receive(buffer);
        }
    }

    @Override
    public synchronized String serialize() {
        return "flower;" + type.id() + ";" + buffer + ";" + (bound == null ? "-" : bound.coords()) + ";" + burnTicks;
    }

    static Flower fromParts(Pos pos, String[] parts) {
        Pos bound = parts[3].equals("-") ? null : Pos.parseCoords(pos.world(), parts[3]);
        Flower flower = new Flower(pos, FlowerType.byId(parts[1]), Integer.parseInt(parts[2]), bound);
        flower.burnTicks = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        return flower;
    }
}
