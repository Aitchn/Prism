package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.block.BlockLook;
import com.aitchn.prism.core.block.BlockPositions;
import com.aitchn.prism.core.block.ChunkLooks;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Where the custom blocks are. The source of truth is the chunk's persistent data (saved with the chunk by the
 * server): one entry {@code prism:b/<x>/<y>/<z>} per block holding its key, facing and per-block data. Loaded chunks
 * are mirrored in memory so lookups are constant time. All writes to one chunk happen on the thread that owns it.
 *
 * <p>Network threads must not use the mirror's mutable maps. They read {@link #looks}: an immutable
 * {@link ChunkLooks} snapshot per chunk, rebuilt after a change, that names the custom block positions and the look a
 * viewer is shown for each (see ADR-0022).
 */
final class BlockIndex {

    /**
     * {@code connections} is the connection mask of a connected block, or -1 for any other block. {@code carrier} is the
     * inert block the server stores for a block that viewers are shown a client-side look for, or null for a block drawn
     * by a display entity.
     */
    record Entry(PrismKey key, BlockFace facing, String variant, int connections, @Nullable Carrier carrier) {
        Entry(PrismKey key, BlockFace facing, String variant, int connections) {
            this(key, facing, variant, connections, null);
        }

        Entry(PrismKey key, BlockFace facing, String variant) {
            this(key, facing, variant, -1, null);
        }

        Entry withVariant(String variant) {
            return new Entry(key, facing, variant, connections, carrier);
        }

        Entry withConnections(int connections) {
            return new Entry(key, facing, variant, connections, carrier);
        }

        Entry withCarrier(@Nullable Carrier carrier) {
            return new Entry(key, facing, variant, connections, carrier);
        }
    }

    record ChunkId(UUID world, long chunk) {
        static ChunkId of(Chunk chunk) {
            return new ChunkId(chunk.getWorld().getUID(), BlockPositions.chunkKey(chunk.getX(), chunk.getZ()));
        }

        static ChunkId of(Block block) {
            return new ChunkId(block.getWorld().getUID(), BlockPositions.chunkKey(block.getX() >> 4, block.getZ() >> 4));
        }
    }

    /** The in-memory mirror of one loaded chunk. */
    static final class Loaded {
        final World world;
        final int chunkX;
        final int chunkZ;
        final Map<Long, Entry> blocks = new ConcurrentHashMap<>();
        final Map<Long, PersistentDataContainer> data = new ConcurrentHashMap<>();
        /** What viewers are shown at the positions drawn as a client-side look; only written on the owning thread. */
        final Map<Long, BlockLook> looks = new ConcurrentHashMap<>();
        Object ticker;
        private final AtomicInteger worldLookChunks;
        private volatile ChunkLooks snapshot = ChunkLooks.EMPTY;
        private volatile boolean dirty;
        private boolean disposed;

        Loaded(World world, int chunkX, int chunkZ, AtomicInteger worldLookChunks) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.worldLookChunks = worldLookChunks;
        }

        /** Records the look viewers see at {@code packed}. */
        synchronized void setLook(long packed, BlockLook look) {
            if (disposed) {
                return;
            }
            boolean wasEmpty = looks.isEmpty();
            BlockLook old = looks.put(packed, look);
            if (!look.equals(old)) {
                dirty = true;
            }
            if (wasEmpty) {
                worldLookChunks.incrementAndGet();
            }
        }

        /** Forgets the look at {@code packed}: the position is not drawn as a client-side look any more. */
        synchronized void clearLook(long packed) {
            if (disposed) {
                return;
            }
            if (looks.remove(packed) != null) {
                dirty = true;
                if (looks.isEmpty()) {
                    worldLookChunks.decrementAndGet();
                }
            }
        }

        /** The immutable view for network threads; rebuilt only after a change. Safe from any thread. */
        ChunkLooks snapshot() {
            if (dirty) {
                dirty = false;
                snapshot = ChunkLooks.of(looks);
            }
            return snapshot;
        }

        synchronized void dispose() {
            if (!disposed && !looks.isEmpty()) {
                worldLookChunks.decrementAndGet();
            }
            disposed = true;
        }

        Block block(long packed) {
            int y = (int) (packed >> 8);
            int x = (int) ((packed >> 4) & 15);
            int z = (int) (packed & 15);
            return world.getBlockAt((chunkX << 4) + x, y, (chunkZ << 4) + z);
        }
    }

    private final Plugin prism;
    private final String namespace;
    private final NamespacedKey keyKey;
    private final NamespacedKey facingKey;
    private final NamespacedKey dataKey;
    private final NamespacedKey variantKey;
    private final NamespacedKey connectionsKey;
    private final NamespacedKey structureKey;
    private final NamespacedKey lightKey;
    private final NamespacedKey carrierKey;
    private final Map<ChunkId, Loaded> loaded = new ConcurrentHashMap<>();
    private volatile java.util.function.Consumer<Loaded> onLoaded = mirror -> {};
    /** Per world, the number of loaded chunks that have at least one look; lets network threads skip other worlds. */
    private final Map<UUID, AtomicInteger> lookChunks = new ConcurrentHashMap<>();

    BlockIndex(Plugin prism) {
        this.prism = prism;
        this.namespace = new NamespacedKey(prism, "x").getNamespace();
        this.keyKey = new NamespacedKey(prism, "key");
        this.facingKey = new NamespacedKey(prism, "facing");
        this.dataKey = new NamespacedKey(prism, "data");
        this.variantKey = new NamespacedKey(prism, "variant");
        this.connectionsKey = new NamespacedKey(prism, "connections");
        this.structureKey = new NamespacedKey(prism, "structure");
        this.lightKey = new NamespacedKey(prism, "light");
        this.carrierKey = new NamespacedKey(prism, "carrier");
    }

    /** Reads the chunk's entries into memory (idempotent) and returns the mirror. */
    Loaded load(Chunk chunk) {
        ChunkId id = ChunkId.of(chunk);
        Loaded mirror = loaded.get(id);
        if (mirror != null) {
            return mirror;
        }
        Loaded fresh = read(chunk);
        Loaded raced = loaded.putIfAbsent(id, fresh);
        if (raced != null) {
            return raced;
        }
        onLoaded.accept(fresh); // outside the map update: the hook may look at other chunks
        return fresh;
    }

    /** Called once for every chunk mirror right after it was read, on the thread that read it. */
    void onLoaded(java.util.function.Consumer<Loaded> hook) {
        this.onLoaded = hook;
    }

    @Nullable Loaded loaded(Chunk chunk) {
        return loaded.get(ChunkId.of(chunk));
    }

    /** The mirror of a chunk by position, without touching the chunk itself (it may belong to another thread). */
    @Nullable Loaded loaded(World world, int chunkX, int chunkZ) {
        return loaded.get(new ChunkId(world.getUID(), BlockPositions.chunkKey(chunkX, chunkZ)));
    }

    @Nullable Loaded unload(Chunk chunk) {
        Loaded mirror = loaded.remove(ChunkId.of(chunk));
        if (mirror != null) {
            mirror.dispose();
        }
        return mirror;
    }

    /** The look snapshot of a chunk, for network threads: touches no world or chunk state. */
    ChunkLooks looks(UUID world, int chunkX, int chunkZ) {
        Loaded mirror = loaded.get(new ChunkId(world, BlockPositions.chunkKey(chunkX, chunkZ)));
        return mirror == null ? ChunkLooks.EMPTY : mirror.snapshot();
    }

    /** Whether any loaded chunk of {@code world} has a look; for network threads. */
    boolean anyLooks(UUID world) {
        AtomicInteger count = lookChunks.get(world);
        return count != null && count.get() > 0;
    }

    List<Loaded> allLoaded() {
        return new ArrayList<>(loaded.values());
    }

    private Loaded read(Chunk chunk) {
        Loaded mirror = new Loaded(chunk.getWorld(), chunk.getX(), chunk.getZ(),
                lookChunks.computeIfAbsent(chunk.getWorld().getUID(), id -> new AtomicInteger()));
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            if (!key.getNamespace().equals(namespace)) {
                continue;
            }
            int[] pos = BlockPositions.parse(key.getKey());
            PersistentDataContainer entry = pos == null ? null : pdc.get(key, PersistentDataType.TAG_CONTAINER);
            if (entry == null) {
                continue;
            }
            String raw = entry.get(keyKey, PersistentDataType.STRING);
            String facing = entry.getOrDefault(facingKey, PersistentDataType.STRING, "NORTH");
            String variant = entry.getOrDefault(variantKey, PersistentDataType.STRING, "");
            int connections = entry.getOrDefault(connectionsKey, PersistentDataType.INTEGER, -1);
            String carrier = entry.get(carrierKey, PersistentDataType.STRING);
            try {
                mirror.blocks.put(BlockPositions.pack(pos[0], pos[1], pos[2]), new Entry(
                        PrismKey.parse(raw == null ? "" : raw), BlockFace.valueOf(facing), variant, connections,
                        Carrier.parse(carrier)));
            } catch (IllegalArgumentException e) {
                // Unreadable entry (for example a hand-edited chunk): ignore it rather than failing the chunk.
            }
        }
        return mirror;
    }

    Optional<Entry> get(Block block) {
        Loaded mirror = loaded.get(ChunkId.of(block));
        if (mirror == null) {
            // Chunk not mirrored yet (load event still pending): read it now, on the owning thread.
            mirror = load(block.getChunk());
        }
        return Optional.ofNullable(mirror.blocks.get(packed(block)));
    }

    void put(Block block, Entry entry) {
        Chunk chunk = block.getChunk();
        Loaded mirror = load(chunk);
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        PersistentDataContainer stored = pdc.getAdapterContext().newPersistentDataContainer();
        stored.set(keyKey, PersistentDataType.STRING, entry.key().toString());
        stored.set(facingKey, PersistentDataType.STRING, entry.facing().name());
        stored.set(variantKey, PersistentDataType.STRING, entry.variant());
        if (entry.connections() >= 0) {
            stored.set(connectionsKey, PersistentDataType.INTEGER, entry.connections());
        }
        if (entry.carrier() != null) {
            stored.set(carrierKey, PersistentDataType.STRING, entry.carrier().name());
        }
        stored.set(dataKey, PersistentDataType.TAG_CONTAINER, pdc.getAdapterContext().newPersistentDataContainer());
        pdc.set(entryKey(block), PersistentDataType.TAG_CONTAINER, stored);
        mirror.blocks.put(packed(block), entry);
        mirror.data.remove(packed(block));
    }

    /** Changes the variant of an existing entry; its data is kept. */
    void setVariant(Block block, String variant) {
        update(block, old -> old.withVariant(variant), stored -> stored.set(variantKey, PersistentDataType.STRING,
                variant));
    }

    /** Changes the connection mask of an existing entry; its data is kept. */
    void setConnections(Block block, int connections) {
        update(block, old -> old.withConnections(connections), stored -> stored.set(connectionsKey,
                PersistentDataType.INTEGER, connections));
    }

    /** Records which inert carrier the server stores for an existing entry (a migrated block), or none (null). */
    void setCarrier(Block block, @Nullable Carrier carrier) {
        update(block, old -> old.withCarrier(carrier), stored -> {
            if (carrier == null) {
                stored.remove(carrierKey);
            } else {
                stored.set(carrierKey, PersistentDataType.STRING, carrier.name());
            }
        });
    }

    private void update(Block block, java.util.function.UnaryOperator<Entry> change,
            java.util.function.Consumer<PersistentDataContainer> write) {
        Chunk chunk = block.getChunk();
        Loaded mirror = load(chunk);
        Entry old = mirror.blocks.get(packed(block));
        if (old == null) {
            return;
        }
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        PersistentDataContainer stored = pdc.get(entryKey(block), PersistentDataType.TAG_CONTAINER);
        if (stored != null) {
            write.accept(stored);
            pdc.set(entryKey(block), PersistentDataType.TAG_CONTAINER, stored);
        }
        mirror.blocks.put(packed(block), change.apply(old));
    }

    /** The facing a structure controller was last formed with, or empty if it was not formed. */
    Optional<String> structure(Block block) {
        PersistentDataContainer stored = block.getChunk().getPersistentDataContainer()
                .get(entryKey(block), PersistentDataType.TAG_CONTAINER);
        return stored == null ? Optional.empty()
                : Optional.ofNullable(stored.get(structureKey, PersistentDataType.STRING));
    }

    /** Records that a structure controller is formed facing {@code facing}, or no longer formed (null). */
    void setStructure(Block block, @Nullable String facing) {
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        PersistentDataContainer stored = pdc.get(entryKey(block), PersistentDataType.TAG_CONTAINER);
        if (stored == null) {
            return;
        }
        if (facing == null) {
            stored.remove(structureKey);
        } else {
            stored.set(structureKey, PersistentDataType.STRING, facing);
        }
        pdc.set(entryKey(block), PersistentDataType.TAG_CONTAINER, stored);
    }

    /** The side of a lit block ({@code prism:light}) where its light block was put, or empty. */
    Optional<String> light(Block block) {
        PersistentDataContainer stored = block.getChunk().getPersistentDataContainer()
                .get(entryKey(block), PersistentDataType.TAG_CONTAINER);
        return stored == null ? Optional.empty()
                : Optional.ofNullable(stored.get(lightKey, PersistentDataType.STRING));
    }

    /** Records the side of {@code block}'s light block, or that it has none (null). */
    void setLight(Block block, @Nullable String side) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        PersistentDataContainer stored = pdc.get(entryKey(block), PersistentDataType.TAG_CONTAINER);
        if (stored == null) {
            return;
        }
        if (side == null) {
            stored.remove(lightKey);
        } else {
            stored.set(lightKey, PersistentDataType.STRING, side);
        }
        pdc.set(entryKey(block), PersistentDataType.TAG_CONTAINER, stored);
    }

    void remove(Block block) {
        Chunk chunk = block.getChunk();
        Loaded mirror = load(chunk);
        chunk.getPersistentDataContainer().remove(entryKey(block));
        mirror.blocks.remove(packed(block));
        mirror.data.remove(packed(block));
        mirror.clearLook(packed(block));
    }

    /** The live per-block data container; every change is written back to the chunk immediately. */
    PersistentDataContainer data(Block block) {
        Loaded mirror = load(block.getChunk());
        return mirror.data.computeIfAbsent(packed(block), p -> liveData(block));
    }

    private PersistentDataContainer liveData(Block block) {
        NamespacedKey entryKey = entryKey(block);
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        PersistentDataContainer stored = pdc.get(entryKey, PersistentDataType.TAG_CONTAINER);
        PersistentDataContainer data = stored == null ? null : stored.get(dataKey, PersistentDataType.TAG_CONTAINER);
        PersistentDataContainer delegate = data != null ? data : pdc.getAdapterContext().newPersistentDataContainer();
        return (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class}, (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    String name = method.getName();
                    if (name.startsWith("set") || name.startsWith("remove") || name.startsWith("read")
                            || name.startsWith("clear")) {
                        PersistentDataContainer current = chunk.getPersistentDataContainer();
                        PersistentDataContainer entry = current.get(entryKey, PersistentDataType.TAG_CONTAINER);
                        if (entry != null) {
                            entry.set(dataKey, PersistentDataType.TAG_CONTAINER, delegate);
                            current.set(entryKey, PersistentDataType.TAG_CONTAINER, entry);
                        }
                    }
                    return result;
                });
    }

    private NamespacedKey entryKey(Block block) {
        return new NamespacedKey(prism, BlockPositions.key(block.getX() & 15, block.getY(), block.getZ() & 15));
    }

    static long packed(Block block) {
        return BlockPositions.pack(block.getX() & 15, block.getY(), block.getZ() & 15);
    }
}
