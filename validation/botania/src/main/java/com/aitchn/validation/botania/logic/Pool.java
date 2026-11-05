package com.aitchn.validation.botania.logic;

import java.util.List;

/** Mana pool: stores mana and turns dropped items into mana items (mana infusion). */
public final class Pool implements ManaCollector {

    public static final int CAPACITY = 1_000_000;
    private static final int INFUSION_INTERVAL = 10;
    private static final double ITEM_RADIUS = 1.0;

    private final Pos pos;
    private int mana;
    private int tick;

    public Pool(Pos pos, int mana) {
        this.pos = pos;
        this.mana = Math.clamp(mana, 0, CAPACITY);
    }

    @Override
    public Pos pos() {
        return pos;
    }

    @Override
    public synchronized int mana() {
        return mana;
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    @Override
    public synchronized int receive(int amount) {
        int accepted = Math.max(0, Math.min(amount, CAPACITY - mana));
        mana += accepted;
        return accepted;
    }

    @Override
    public synchronized void tick(WorldView world, ManaNetwork network) {
        if (++tick % INFUSION_INTERVAL != 0) {
            return;
        }
        List<String> items = world.droppedItems(pos, ITEM_RADIUS);
        for (String item : items) {
            Recipes.Infusion recipe = Recipes.infusion(item);
            if (recipe != null && mana >= recipe.cost() && world.consumeDroppedItem(pos, ITEM_RADIUS, item)) {
                mana -= recipe.cost();
                world.dropItem(pos.offset(0, 1, 0), recipe.result(), 1);
                return;
            }
        }
    }

    @Override
    public synchronized String serialize() {
        return "pool;" + mana;
    }

    static Pool fromParts(Pos pos, String[] parts) {
        return new Pool(pos, Integer.parseInt(parts[1]));
    }
}
