package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.block.BlockCapabilityProvider;
import com.aitchn.prism.api.block.BlockDropBehavior;
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
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.block.TickSchedule;
import com.aitchn.prism.core.mod.BehaviorDispatcher;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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
import org.bukkit.inventory.ItemStack;
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
    private final NoteBlockAppearance noteBlocks;
    private final String backend;
    private final BehaviorDispatcher dispatcher;
    private final PlatformScheduler scheduler;
    private final Logger log;
    private final OreGenerator ores;
    /** Told after a block is placed, removed or changes variant, so per-player looks can follow; set once at startup. */
    private volatile java.util.function.Consumer<Block> lookChanged = block -> {};

    /**
     * @param backend {@code auto} (note block states for every block that has no BetterModel model, so Bedrock sees
     *                them), {@code display} (always display entities) or {@code native} (note block states only)
     */
    PrismBlockService(Registry<BlockDefinition> definitions, ItemService items, BlockIndex index,
            DisplayEntityBackend display, NoteBlockAppearance noteBlocks, String backend, BehaviorDispatcher dispatcher,
            PlatformScheduler scheduler, Logger log) {
        this.noteBlocks = noteBlocks;
        this.backend = backend;
        this.definitions = definitions;
        this.items = items;
        this.index = index;
        this.display = display;
        this.dispatcher = dispatcher;
        this.scheduler = scheduler;
        this.log = log;
        this.ores = new OreGenerator(definitions, log);
    }

    /** A custom block handle. Cheap; created per lookup. */
    private record Handle(Block block, PrismKey key, BlockDefinition definition, BlockFace facing, String variant,
            BlockIndex index) implements CustomBlock {
        @Override
        public @NotNull PersistentDataContainer data() {
            return index.data(block);
        }
    }

    // ---------------------------------------------------------------- BlockService

    @Override
    public boolean place(@NotNull PrismKey key, @NotNull Location location, @Nullable Player placer,
            @NotNull BlockFace facing, @Nullable ItemStack from) {
        Optional<BlockDefinition> found = definitions.get(key);
        Block block = location.getBlock();
        if (found.isEmpty() || !(block.isEmpty() || block.isReplaceable()) || index.get(block).isPresent()) {
            return false;
        }
        BlockDefinition definition = found.get();
        BlockFace actual = definition.components().get(PrismComponents.ROTATION)
                .filter(rotation -> rotation != PrismComponents.Rotation.NONE)
                .isPresent() ? facing : BlockFace.NORTH;
        String variant = Appearances.variants(definition).getFirst();
        if (!appearanceFor(definition).spawn(block, definition, actual, variant)) {
            return false;
        }
        index.put(block, new BlockIndex.Entry(key, actual, variant));
        Handle handle = new Handle(block, key, definition, actual, variant, index);
        dispatcher.dispatchBlock(key, block.getLocation(), BlockPlaceBehavior.class,
                d -> BlockEventContext.of(handle, placer).placedFrom(from), (b, c) -> b.onPlace(c));
        startTicking(handle);
        notifyNeighbors(block);
        lookChanged.accept(block);
        return true;
    }

    /** The resolved {@code prism:ore} specs, for the self test. */
    List<OreGenerator.Target> oreTargets() {
        return ores.targets();
    }

    /** New chunks generated into and ore blocks placed so far, for the self test. */
    String oreStats() {
        return "newChunks=" + ores.chunks.get() + " orePlaced=" + ores.placedBlocks.get();
    }

    /**
     * World generation: draws and indexes the block without place behaviors or neighbour notifications (the chunk is
     * still loading). Load behaviors and ticking start with the rest of the chunk one tick later.
     */
    private boolean placeGenerated(Block block, BlockDefinition definition) {
        if (index.get(block).isPresent()) {
            return false;
        }
        String variant = Appearances.variants(definition).getFirst();
        if (!appearanceFor(definition).spawn(block, definition, BlockFace.NORTH, variant)) {
            return false;
        }
        index.put(block, new BlockIndex.Entry(definition.key(), BlockFace.NORTH, variant));
        return true;
    }

    @Override
    public @NotNull Optional<CustomBlock> get(@NotNull Block block) {
        return index.get(block).flatMap(entry -> definitions.get(entry.key())
                .map(definition -> new Handle(block, entry.key(), definition, entry.facing(), entry.variant(), index)));
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
                result.add(new Handle(mirror.block(packed), entry.key(), definition, entry.facing(), entry.variant(),
                        index))));
        return result;
    }

    @Override
    public boolean remove(@NotNull Block block, boolean dropItem) {
        return remove(block, dropItem,
                stack -> block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), stack));
    }

    /** Removes like {@code remove(block, true)} but hands the filled drop to {@code sink} instead of the world. */
    boolean remove(Block block, boolean dropItem, Consumer<ItemStack> sink) {
        Optional<BlockIndex.Entry> entry = index.get(block);
        if (entry.isEmpty()) {
            return false;
        }
        PrismKey key = entry.get().key();
        Optional<BlockDefinition> definition = definitions.get(key);
        Optional<ItemStack> drop = dropItem
                ? definition.flatMap(BlockDefinition::drop).flatMap(item -> items.create(item, 1))
                : Optional.empty();
        definition.ifPresent(d -> {
            Handle handle = new Handle(block, key, d, entry.get().facing(), entry.get().variant(), index);
            // Drop behaviors first: they read the data that remove behaviors may clear.
            drop.ifPresent(stack -> dispatcher.dispatchBlock(key, block.getLocation(), BlockDropBehavior.class,
                    x -> BlockEventContext.of(handle, null), (b, c) -> b.onDrop(c, stack)));
            dispatcher.dispatchBlock(key, block.getLocation(), BlockRemoveBehavior.class,
                    x -> BlockEventContext.of(handle, null), (b, c) -> b.onRemove(c));
        });
        stopTicking(block);
        appearanceOf(block).despawn(block);
        index.remove(block);
        drop.ifPresent(sink);
        notifyNeighbors(block);
        lookChanged.accept(block);
        return true;
    }

    @Override
    public boolean setVariant(@NotNull Block block, @NotNull String variant) {
        Optional<CustomBlock> custom = get(block);
        if (custom.isEmpty() || !Appearances.variants(custom.get().definition()).contains(variant)) {
            return false;
        }
        if (!custom.get().variant().equals(variant)) {
            appearanceOf(block).update(block, custom.get().definition(), custom.get().facing(), variant);
            index.setVariant(block, variant);
            lookChanged.accept(block);
        }
        return true;
    }

    void onLookChanged(java.util.function.Consumer<Block> listener) {
        this.lookChanged = listener;
    }

    /**
     * Whether blocks of {@code definition} drawn by a BetterModel model also get a note block look for Bedrock players
     * (sent to them per player; Java players keep the model).
     */
    static boolean bedrockLookForModel(String backend, BlockDefinition definition) {
        return !"display".equals(backend) && ModelAnchors.modelOf(definition).isPresent();
    }

    /** The appearance new blocks of {@code definition} get. */
    private BlockAppearance appearanceFor(BlockDefinition definition) {
        return usesNoteBlocks(backend, definition) ? noteBlocks : display;
    }

    /** Whether new blocks of {@code definition} are drawn as note block states under the {@code blocks.backend} setting. */
    static boolean usesNoteBlocks(String backend, BlockDefinition definition) {
        // BetterModel models need an entity to animate, so they always use a display entity.
        return !"display".equals(backend) && ModelAnchors.modelOf(definition).isEmpty();
    }

    /** The appearance an existing block was placed with (blocks keep theirs when the setting changes). */
    BlockAppearance appearanceOf(Block block) {
        return noteBlocks.draws(block) ? noteBlocks : display;
    }

    /** Whether the client breaks this block itself (its carrier is breakable), so Prism must not run the dig. */
    boolean clientBreaks(Block block) {
        return noteBlocks.draws(block);
    }

    @Override
    public @NotNull Optional<BlockDefinition> definition(@NotNull PrismKey key) {
        return definitions.get(key);
    }

    @Override
    public <T> @NotNull Optional<T> capability(
            @NotNull Block block, @Nullable BlockFace side, @NotNull BlockCapability<T> capability) {
        Optional<CustomBlock> custom = get(block);
        boolean itemHandler = capability.key().equals(PrismCapabilities.ITEM_HANDLER.key());
        if (custom.isPresent()) {
            Optional<T> found = provided(custom.get(), side, capability);
            if (found.isEmpty() && itemHandler) {
                // A block that offers an inventory can be piped into like a chest.
                found = provided(custom.get(), side, PrismCapabilities.INVENTORY)
                        .map(inventory -> capability.type().cast(InventoryItemHandler.allSlots(inventory)));
            }
            return found;
        }
        if (capability.key().equals(PrismCapabilities.INVENTORY.key()) || itemHandler) {
            BlockState state = block.getState(false);
            if (state instanceof Container container) {
                Object answer = itemHandler
                        ? InventoryItemHandler.forContainer(container.getInventory(), side)
                        : container.getInventory();
                return Optional.of(capability.type().cast(answer));
            }
        }
        return Optional.empty();
    }

    private <T> Optional<T> provided(CustomBlock custom, @Nullable BlockFace side, BlockCapability<T> capability) {
        for (BlockCapabilityProvider provider : custom.definition().behaviors(BlockCapabilityProvider.class)) {
            try {
                T found = provider.capability(custom, side, capability);
                if (found != null && capability.type().isInstance(found)) {
                    return Optional.of(found);
                }
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, "Capability provider of " + custom.key() + " failed", e);
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
        if (event.isNewChunk()) {
            ores.generate(chunk, this::placeGenerated);
        }
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
        ores.contentChanged();
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
            index.put(block, new BlockIndex.Entry(key, BlockFace.NORTH, ""));
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
        Optional<PrismComponents.AmbientSound> ambient = custom.definition().components()
                .get(PrismComponents.AMBIENT_SOUND);
        if (tickers.isEmpty() && ambient.isEmpty()) {
            return;
        }
        BlockIndex.Loaded mirror = index.load(custom.block().getChunk());
        TickSchedule<Runnable> schedule = schedule(mirror);
        long base = BlockIndex.packed(custom.block()) << 4;
        Block block = custom.block();
        // With an ambient sound, slot 15 of the block's 16 schedule slots plays it; behaviors use the others.
        ambient.ifPresent(sound -> schedule.add(base | AMBIENT_SLOT, () -> get(block).ifPresent(current -> {
            if (sound.playsIn(current.variant())) {
                block.getWorld().playSound(block.getLocation().toCenterLocation(), sound.sound().toString(),
                        org.bukkit.SoundCategory.BLOCKS, sound.volume(), sound.pitch());
            }
        }), sound.interval()));
        int slots = ambient.isPresent() ? AMBIENT_SLOT : 16;
        for (int i = 0; i < tickers.size() && i < slots; i++) {
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

    private static final int AMBIENT_SLOT = 15;

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
