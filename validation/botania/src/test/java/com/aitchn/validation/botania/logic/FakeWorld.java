package com.aitchn.validation.botania.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory world for the rule tests. */
final class FakeWorld implements WorldView {

    final Set<Pos> solid = new HashSet<>();
    final Map<Pos, String> blocks = new HashMap<>();
    final List<String> dropped = new ArrayList<>();
    final List<String> spawned = new ArrayList<>();
    final RecipeBook recipes = RecipeBook.defaults();
    boolean daylight = true;
    int ownedMinX = Integer.MIN_VALUE;
    int ownedMaxX = Integer.MAX_VALUE;

    @Override
    public boolean isOwned(Pos pos) {
        return pos.x() >= ownedMinX && pos.x() <= ownedMaxX;
    }

    @Override
    public boolean isSolid(Pos pos) {
        return solid.contains(pos);
    }

    @Override
    public boolean isDaylight(Pos pos) {
        return daylight;
    }

    @Override
    public String blockId(Pos pos) {
        return blocks.getOrDefault(pos, "minecraft:air");
    }

    @Override
    public boolean convert(Pos pos, String blockId) {
        blocks.put(pos, blockId);
        return true;
    }

    @Override
    public List<String> droppedItems(Pos pos, double radius) {
        return List.copyOf(dropped);
    }

    @Override
    public boolean consumeDroppedItem(Pos pos, double radius, String itemId) {
        return dropped.remove(itemId);
    }

    @Override
    public RecipeBook recipes() {
        return recipes;
    }

    @Override
    public void dropItem(Pos pos, String itemId, int amount) {
        spawned.add(itemId);
    }
}
