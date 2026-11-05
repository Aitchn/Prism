package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.block.BlockCapabilityProvider;
import com.aitchn.prism.api.block.BlockLoadBehavior;
import com.aitchn.prism.api.block.BlockNeighborBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.block.BlockService;
import com.aitchn.prism.api.block.BlockTickBehavior;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.block.PrismCapabilities;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.core.block.TickSchedule;
import com.aitchn.prism.core.mod.BehaviorDispatcher;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The block platform: placement and removal with lifecycle behaviors, the chunk index, per-block data, batched
 * ticking, neighbour notifications and capability lookup. Appearance is delegated to {@link DisplayEntityBackend}.
 */
final class PrismBlockService implements BlockService, Listener {

    static final BlockFace[] SIDES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final Registry<BlockDefinition> definitions;
    private final ItemService items;
    private final BlockIndex index;
    private final DisplayEntityBackend display;
    private final BehaviorDispatcher dispatcher;
    private final PlatformScheduler scheduler;
    private final Logger log;

    PrismBlockService(Registry<BlockDefinition> definitions, ItemService items, BlockIndex index,
            DisplayEntityBackend display, BehaviorDispatcher dispatcher, PlatformScheduler scheduler, Logger log) {
        this.definitions = definitions;
        this.items = items;
        this.index = index;
        this.display = display;
        this.dispatcher = dispatcher;
        this.scheduler = scheduler;
        this.log = log;
    }

    /** A custom block handle. Cheap; created per lookup. */
    private record Handle(Block block, PrismKey key, BlockDefinition definition, BlockFace facing, BlockIndex index)
            implements CustomBlock {
        @Override
        public @NotNull PersistentDataContainer data() {
            return index.data(block);
        }
    }

    // ---------------------------------------------------------------- BlockService

    @Override
    public boolean place(@NotNull PrismKey key, @NotNull Location location, @Nullable Player placer,
            @NotNull BlockFace facing) {
        Optional<BlockDefinition> found = definitions.get(key);
        Block block = location.getBlock();
        if (found.isEmpty() || !(block.isEmpty() || block.isReplaceable()) || index.get(block).isPresent()) {
            return false;
        }
        BlockDefinition definition = found.get();
        BlockFace actual = definition.components().get(PrismComponents.ROTATION)
                .filter(rotation -> rotation != PrismComponents.Rotation.NONE)
                .isPresent() ? facing : BlockFace.NORTH;
        if (!display.spawn(block, definition, actual)) {
            return false;
        }
        index.put(block, new BlockIndex.Entry(key, actual));
        Handle handle = new Handle(block, key, definition, actual, index);
        dispatcher.dispatchBlock(key, block.getLocation(), BlockPlaceBehavior.class,
                d -> BlockEventContext.of(handle, placer), (b, c) -> b.onPlace(c));
        startTicking(handle);
        notifyNeighbors(block);
        return true;
    }

    @Override
    public @NotNull Optional<CustomBlock> get(@NotNull Block block) {
        return index.get(block).flatMap(entry -> definitions.get(entry.key())
                .map(definition -> new Handle(block, entry.key(), definition, entry.facing(), index)));
    }

    @Override
    public @NotNull Optional<PrismKey> identify(@NotNull Block block) {
        // Answers even for blocks whose mod is not loaded, so they are still protected from vanilla changes.
        return index.get(block).map(BlockIndex.Entry::key);
    }

    @Override
    public @NotNull List<CustomBlock> blocksIn(@NotNull Chunk chunk) {
        BlockIndex.Loaded mirror = index.load(chunk);
        List<CustomBlock> result = new ArrayList<>();
        mirror.blocks.forEach((packed, entry) -> definitions.get(entry.key()).ifPresent(definition ->
                result.add(new Handle(mirror.block(packed), entry.key(), definition, entry.facing(), index))));
        return result;
    }

    @Override
    public boolean remove(@NotNull Block block, boolean dropItem) {
        Optional<BlockIndex.Entry> entry = index.get(block);
        if (entry.isEmpty()) {
            return false;
        }
        PrismKey key = entry.get().key();
        Optional<BlockDefinition> definition = definitions.get(key);
        definition.ifPresent(d -> {
            Handle handle = new Handle(block, key, d, entry.get().facing(), index);
            dispatcher.dispatchBlock(key, block.getLocation(), BlockRemoveBehavior.class,
                    x -> BlockEventContext.of(handle, null), (b, c) -> b.onRemove(c));
        });
        stopTicking(block);
        display.despawn(block);
        index.remove(block);
        if (dropItem) {
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            definition.flatMap(BlockDefinition::drop)
                    .flatMap(drop -> items.create(drop, 1))
                    .ifPresent(stack -> block.getWorld().dropItemNaturally(center, stack));
        }
        notifyNeighbors(block);
        return true;
    }

    @Override
    public @NotNull Optional<BlockDefinition> definition(@NotNull PrismKey key) {
        return definitions.get(key);
    }

    @Override
    public <T> @NotNull Optional<T> capability(
            @NotNull Block block, @Nullable BlockFace side, @NotNull BlockCapability<T> capability) {
        Optional<CustomBlock> custom = get(block);
        if (custom.isPresent()) {
            for (BlockCapabilityProvider provider : custom.get().definition().behaviors(BlockCapabilityProvider.class)) {
                try {
                    T found = provider.capability(custom.get(), side, capability);
                    if (found != null && capability.type().isInstance(found)) {
                        return Optional.of(found);
                    }
                } catch (RuntimeException e) {
                    log.log(Level.SEVERE, "Capability provider of " + custom.get().key() + " failed", e);
                }
            }
            return Optional.empty();
        }
        if (capability.key().equals(PrismCapabilities.INVENTORY.key())) {
            BlockState state = block.getState(false);
            if (state instanceof Container container) {
                return Optional.of(capability.type().cast(container.getInventory()));
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- neighbours

    /** Tells the custom blocks next to {@code changed} that it changed. */
    void notifyNeighbors(Block changed) {
        for (BlockFace face : SIDES) {
            Block neighbor = changed.getRelative(face);
            Runnable notify = () -> get(neighbor).ifPresent(custom -> dispatcher.dispatchBlock(custom.key(),
                    neighbor.getLocation(), BlockNeighborBehavior.class, d -> BlockEventContext.of(custom, null),
                    (b, c) -> b.onNeighborChanged(c, face.getOppositeFace())));
            Location at = neighbor.getLocation();
            if (!neighbor.getWorld().isChunkLoaded(neighbor.getX() >> 4, neighbor.getZ() >> 4)) {
                continue;
            }
            if (scheduler.isOwnedByCurrentThread(at)) {
                notify.run();
            } else {
                scheduler.runAt(at, notify);
            }
        }
    }

    // ---------------------------------------------------------------- chunk lifecycle

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        index.load(chunk);
        // Behaviors run one tick later, outside the chunk load, so they may look at neighbours safely.
        scheduler.runAt(center(chunk), () -> activate(chunk));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        BlockIndex.Loaded mirror = index.loaded(chunk);
        if (mirror == null) {
            return;
        }
        stopTicker(mirror);
        for (CustomBlock custom : blocksIn(chunk)) {
            dispatcher.dispatchBlock(custom.key(), custom.block().getLocation(), BlockLoadBehavior.class,
                    d -> BlockEventContext.of(custom, null), (b, c) -> b.onUnload(c));
        }
        index.unload(chunk);
    }

    /** Migrates display markers of worlds created before the chunk index existed. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            display.forMarker(entity, this::adopt);
        }
    }

    /** Chunks loaded before Prism (spawn chunks) never fired a load event for us. */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int x = chunk.getX();
                int z = chunk.getZ();
                scheduler.runAt(center(chunk), () -> {
                    if (world.isChunkLoaded(x, z)) {
                        Chunk loaded = world.getChunkAt(x, z);
                        index.load(loaded);
                        display.forEachMarker(loaded, this::adopt);
                        activate(loaded);
                    }
                });
            }
        }
    }

    /**
     * Content changed (a mod enabled, disabled or reloaded): restart ticking everywhere so new definitions tick and
     * removed ones stop.
     */
    void contentChanged() {
        for (BlockIndex.Loaded mirror : index.allLoaded()) {
            scheduler.runAt(new Location(mirror.world, (mirror.chunkX << 4) + 8, 64, (mirror.chunkZ << 4) + 8), () -> {
                if (mirror.world.isChunkLoaded(mirror.chunkX, mirror.chunkZ)) {
                    stopTicker(mirror);
                    for (CustomBlock custom : blocksIn(mirror.world.getChunkAt(mirror.chunkX, mirror.chunkZ))) {
                        startTicking(custom);
                    }
                }
            });
        }
    }

    private void adopt(Block block, PrismKey key) {
        if (index.get(block).isEmpty()) {
            index.put(block, new BlockIndex.Entry(key, BlockFace.NORTH));
        }
    }

    private void activate(Chunk chunk) {
        if (!chunk.isLoaded()) {
            return;
        }
        for (CustomBlock custom : blocksIn(chunk)) {
            dispatcher.dispatchBlock(custom.key(), custom.block().getLocation(), BlockLoadBehavior.class,
                    d -> BlockEventContext.of(custom, null), (b, c) -> b.onLoad(c));
            startTicking(custom);
        }
    }

    // ---------------------------------------------------------------- ticking

    private void startTicking(CustomBlock custom) {
        List<BlockTickBehavior> tickers = custom.definition().behaviors(BlockTickBehavior.class);
        if (tickers.isEmpty()) {
            return;
        }
        BlockIndex.Loaded mirror = index.load(custom.block().getChunk());
        TickSchedule<Runnable> schedule = schedule(mirror);
        long base = BlockIndex.packed(custom.block()) << 4;
        Block block = custom.block();
        for (int i = 0; i < tickers.size() && i < 16; i++) {
            BlockTickBehavior ticker = tickers.get(i);
            schedule.add(base | i, () -> get(block).ifPresent(current -> {
                BlockEventContext context = BlockEventContext.of(current, null);
                try {
                    ticker.onTick(context);
                } catch (RuntimeException e) {
                    log.log(Level.SEVERE, "Tick of " + current.key() + " failed", e);
                }
            }), safeInterval(ticker));
        }
    }

    private static int safeInterval(BlockTickBehavior ticker) {
        try {
            return Math.max(1, ticker.tickInterval());
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private void stopTicking(Block block) {
        BlockIndex.Loaded mirror = index.loaded(block.getChunk());
        if (mirror == null || !(mirror.ticker instanceof Ticker ticker)) {
            return;
        }
        long base = BlockIndex.packed(block) << 4;
        for (int i = 0; i < 16; i++) {
            ticker.schedule.remove(base | i);
        }
    }

    private static final class Ticker {
        final TickSchedule<Runnable> schedule = new TickSchedule<>();
        ScheduledTask task;
    }

    private TickSchedule<Runnable> schedule(BlockIndex.Loaded mirror) {
        if (mirror.ticker instanceof Ticker ticker) {
            return ticker.schedule;
        }
        Ticker ticker = new Ticker();
        mirror.ticker = ticker;
        ticker.task = scheduler.runAtTimer(
                new Location(mirror.world, (mirror.chunkX << 4) + 8, 64, (mirror.chunkZ << 4) + 8),
                () -> ticker.schedule.tick(Runnable::run), 1, 1);
        return ticker.schedule;
    }

    private static void stopTicker(BlockIndex.Loaded mirror) {
        if (mirror.ticker instanceof Ticker ticker) {
            ticker.task.cancel();
            mirror.ticker = null;
        }
    }

    int tickingChunks() {
        int count = 0;
        for (BlockIndex.Loaded mirror : index.allLoaded()) {
            if (mirror.ticker instanceof Ticker ticker && !ticker.schedule.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static Location center(Chunk chunk) {
        return new Location(chunk.getWorld(), (chunk.getX() << 4) + 8, 64, (chunk.getZ() << 4) + 8);
    }
}
