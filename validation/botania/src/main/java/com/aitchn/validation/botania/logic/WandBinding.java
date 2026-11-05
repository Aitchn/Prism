package com.aitchn.validation.botania.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Wand of the Forest: remembers a selected flower per player, then binds it to the next collector clicked. */
public final class WandBinding {

    public enum Result {
        SELECTED,
        BOUND,
        OUT_OF_RANGE,
        NOTHING
    }

    private final Map<String, Pos> selected = new HashMap<>();

    public synchronized Optional<Pos> selected(String player) {
        return Optional.ofNullable(selected.get(player));
    }

    public synchronized Result click(String player, Pos clicked, ManaNetwork network) {
        var clickedEntity = network.get(clicked);
        if (clickedEntity.isEmpty()) {
            return Result.NOTHING;
        }
        if (clickedEntity.get() instanceof Flower) {
            selected.put(player, clicked);
            return Result.SELECTED;
        }
        Pos from = selected.get(player);
        if (from != null && clickedEntity.get() instanceof ManaCollector
                && network.get(from).orElse(null) instanceof Flower flower) {
            if (!flower.bind(clicked)) {
                return Result.OUT_OF_RANGE;
            }
            selected.remove(player);
            return Result.BOUND;
        }
        return Result.NOTHING;
    }

    public synchronized void forget(String player) {
        selected.remove(player);
    }
}
