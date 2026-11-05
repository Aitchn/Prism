package com.aitchn.validation.botania.logic;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Registry of the loaded block entities, keyed by position. */
public final class ManaNetwork {

    private final Map<Pos, BlockEntity> entities = new ConcurrentHashMap<>();

    public void put(BlockEntity entity) {
        entities.put(entity.pos(), entity);
    }

    public Optional<BlockEntity> remove(Pos pos) {
        return Optional.ofNullable(entities.remove(pos));
    }

    public Optional<BlockEntity> get(Pos pos) {
        return Optional.ofNullable(entities.get(pos));
    }

    public Optional<ManaCollector> collector(Pos pos) {
        return get(pos).filter(ManaCollector.class::isInstance).map(ManaCollector.class::cast);
    }

    public int size() {
        return entities.size();
    }
}
