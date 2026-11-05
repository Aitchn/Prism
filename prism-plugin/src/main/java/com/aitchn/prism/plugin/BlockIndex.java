package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.block.BlockPositions;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * are mirrored in memory so lookups are constant time. All access to one chunk happens on the thread that owns it.
 */
final class BlockIndex {

    record Entry(PrismKey key, BlockFace facing, String variant) {}

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
        Object ticker;

        Loaded(World world, int chunkX, int chunkZ) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
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
    private final Map<ChunkId, Loaded> loaded = new ConcurrentHashMap<>();

    BlockIndex(Plugin prism) {
        this.prism = prism;
        this.namespace = new NamespacedKey(prism, "x").getNamespace();
        this.keyKey = new NamespacedKey(prism, "key");
        this.facingKey = new NamespacedKey(prism, "facing");
        this.dataKey = new NamespacedKey(prism, "data");
        this.variantKey = new NamespacedKey(prism, "variant");
    }

    /** Reads the chunk's entries into memory (idempotent) and returns the mirror. */
    Loaded load(Chunk chunk) {
        return loaded.computeIfAbsent(ChunkId.of(chunk), id -> read(chunk));
    }

    @Nullable Loaded loaded(Chunk chunk) {
        return loaded.get(ChunkId.of(chunk));
    }

    @Nullable Loaded unload(Chunk chunk) {
        return loaded.remove(ChunkId.of(chunk));
    }

    List<Loaded> allLoaded() {
        return new ArrayList<>(loaded.values());
    }

    private Loaded read(Chunk chunk) {
        Loaded mirror = new Loaded(chunk.getWorld(), chunk.getX(), chunk.getZ());
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
            try {
                mirror.blocks.put(BlockPositions.pack(pos[0], pos[1], pos[2]),
                        new Entry(PrismKey.parse(raw == null ? "" : raw), BlockFace.valueOf(facing), variant));
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
        stored.set(dataKey, PersistentDataType.TAG_CONTAINER, pdc.getAdapterContext().newPersistentDataContainer());
        pdc.set(entryKey(block), PersistentDataType.TAG_CONTAINER, stored);
        mirror.blocks.put(packed(block), entry);
        mirror.data.remove(packed(block));
    }

    /** Changes the variant of an existing entry; its data is kept. */
    void setVariant(Block block, String variant) {
        Chunk chunk = block.getChunk();
        Loaded mirror = load(chunk);
        Entry old = mirror.blocks.get(packed(block));
        if (old == null) {
            return;
        }
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        PersistentDataContainer stored = pdc.get(entryKey(block), PersistentDataType.TAG_CONTAINER);
        if (stored != null) {
            stored.set(variantKey, PersistentDataType.STRING, variant);
            pdc.set(entryKey(block), PersistentDataType.TAG_CONTAINER, stored);
        }
        mirror.blocks.put(packed(block), new Entry(old.key(), old.facing(), variant));
    }

    void remove(Block block) {
        Chunk chunk = block.getChunk();
        Loaded mirror = load(chunk);
        chunk.getPersistentDataContainer().remove(entryKey(block));
        mirror.blocks.remove(packed(block));
        mirror.data.remove(packed(block));
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
