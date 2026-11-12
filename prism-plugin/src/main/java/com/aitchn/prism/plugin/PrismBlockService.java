package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.block.BlockCapabilityProvider;
import com.aitchn.prism.api.block.BlockConnectionBehavior;
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
import com.aitchn.prism.client.LookSender;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.block.BlockLook;
import com.aitchn.prism.core.block.ChunkLooks;
import com.aitchn.prism.core.block.TickSchedule;
import com.aitchn.prism.core.mod.BehaviorDispatcher;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * ticking, neighbour notifications and capability lookup. Appearance is delegated to {@link DisplayEntityBackend}, or,
 * for blocks drawn as a client-side look, to an inert {@link Carrier} in the world plus the look this service publishes
 * to {@link BlockIndex} for the packet rewriter ({@code BlockPacketRewriter}). The index is the source of truth: if the
 * world no longer holds a block's carrier, the block is gone (see ADR-0022).
 */
final class PrismBlockService implements BlockService, Listener {

    static final BlockFace[] SIDES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final Registry<BlockDefinition> definitions;
    private final ItemService items;
    private final BlockIndex index;
    private final DisplayEntityBackend display;
    private final CarrierAppearance noteBlocks;
    private final CarrierAppearance tripwires;
    private final CarrierAppearance clearShells;
    private final java.util.function.Supplier<ClientLooks> clientLooks;
    private final BehaviorDispatcher dispatcher;
    private final PlatformScheduler scheduler;
    private final Logger log;
    private final OreGenerator ores;
    private final StructureTracker structures;
    private final BlockLights lights;
    /** Told after a block is placed, removed or changes variant, so per-player looks can follow; set at startup. */
    private final java.util.List<java.util.function.Consumer<BlockIndex.Loaded>> chunkRelooked =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<java.util.function.Consumer<Block>> lookChanged =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<java.util.function.Consumer<BlockIndex.Loaded>> chunkActivated =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    PrismBlockService(Registry<BlockDefinition> definitions, ItemService items, BlockIndex index,
            DisplayEntityBackend display, java.util.function.Supplier<ClientLooks> clientLooks,
            BehaviorDispatcher dispatcher, PlatformScheduler scheduler, Logger log) {
        this.clientLooks = clientLooks;
        this.noteBlocks = new CarrierAppearance(Carrier.SOLID, clientLooks, log);
        this.tripwires = new CarrierAppearance(Carrier.PASSABLE, clientLooks, log);
        this.clearShells = new CarrierAppearance(Carrier.DISPLAY, clientLooks, log);
        this.definitions = definitions;
        this.items = items;
        this.index = index;
        this.display = display;
        this.dispatcher = dispatcher;
        this.scheduler = scheduler;
        this.log = log;
        this.ores = new OreGenerator(definitions, log);
        this.structures = new StructureTracker(this, definitions, index, dispatcher, scheduler);
        this.lights = new BlockLights(index, scheduler);
        index.onLoaded(this::onMirrorLoaded);
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
        if (found.isEmpty() || !(block.isEmpty() || block.isReplaceable()) || entry(block).isPresent()) {
            return false;
        }
        BlockDefinition definition = found.get();
        boolean connected = definition.components().has(PrismComponents.CONNECTIONS);
        BlockFace actual = !connected && definition.components().get(PrismComponents.ROTATION)
                .filter(rotation -> rotation != PrismComponents.Rotation.NONE)
                .isPresent() ? facing : BlockFace.NORTH;
        String variant = Appearances.variants(definition).getFirst();
        int mask = connected ? connections(new Handle(block, key, definition, actual, variant, index)) : -1;
        String drawn = Appearances.drawnVariant(variant, mask);
        Carrier carrier = carrierFor(definition, new Appearances.Look(key, drawn, actual.name()).id());
        if (!appearance(carrier).spawn(block, definition, actual, drawn)) {
            return false;
        }
        index.put(block, new BlockIndex.Entry(key, actual, variant, mask, carrier));
        publishLook(block);
        Handle handle = new Handle(block, key, definition, actual, variant, index);
        dispatcher.dispatchBlock(key, block.getLocation(), BlockPlaceBehavior.class,
                d -> BlockEventContext.of(handle, placer).placedFrom(from), (b, c) -> b.onPlace(c));
        // Again now the block is indexed and its place behaviors set up its data, which a connection behavior may read.
        refreshConnections(block);
        startTicking(handle);
        lights.update(handle);
        notifyNeighbors(block);
        lookChanged.forEach(listener -> listener.accept(block));
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
        // Neighbours are not looked at while the chunk generates: a connected block starts unconnected.
        int mask = definition.components().has(PrismComponents.CONNECTIONS) ? 0 : -1;
        String drawn = Appearances.drawnVariant(variant, mask);
        Carrier carrier = carrierFor(definition,
                new Appearances.Look(definition.key(), drawn, BlockFace.NORTH.name()).id());
        if (!appearance(carrier).spawn(block, definition, BlockFace.NORTH, drawn)) {
            return false;
        }
        index.put(block, new BlockIndex.Entry(definition.key(), BlockFace.NORTH, variant, mask, carrier));
        publishLook(block);
        return true;
    }

    @Override
    public @NotNull Optional<CustomBlock> get(@NotNull Block block) {
        return entry(block).flatMap(entry -> definitions.get(entry.key())
                .map(definition -> new Handle(block, entry.key(), definition, entry.facing(), entry.variant(), index)));
    }

    @Override
    public @NotNull Optional<PrismKey> identify(@NotNull Block block) {
        // Answers even for blocks whose mod is not loaded, so they are still protected from vanilla changes.
        return entry(block).map(BlockIndex.Entry::key);
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
        return remove(block, dropItem, sink, false);
    }

    /**
     * @param orphan the world no longer holds the block's carrier (something else replaced it), so the block that is
     *               there now must be left alone: only the entry, its behaviors and its drawing are removed
     */
    private boolean remove(Block block, boolean dropItem, Consumer<ItemStack> sink, boolean orphan) {
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
            structures.controllerRemoved(handle);
            lights.removed(handle);
            // Drop behaviors first: they read the data that remove behaviors may clear.
            drop.ifPresent(stack -> dispatcher.dispatchBlock(key, block.getLocation(), BlockDropBehavior.class,
                    x -> BlockEventContext.of(handle, null), (b, c) -> b.onDrop(c, stack)));
            dispatcher.dispatchBlock(key, block.getLocation(), BlockRemoveBehavior.class,
                    x -> BlockEventContext.of(handle, null), (b, c) -> b.onRemove(c));
        });
        stopTicking(block);
        if (orphan) {
            appearanceOf(block).orphaned(block);
        } else {
            appearanceOf(block).despawn(block);
        }
        index.remove(block);
        drop.ifPresent(sink);
        notifyNeighbors(block);
        lookChanged.forEach(listener -> listener.accept(block));
        return true;
    }

    @Override
    public boolean setVariant(@NotNull Block block, @NotNull String variant) {
        Optional<CustomBlock> custom = get(block);
        if (custom.isEmpty() || !Appearances.variants(custom.get().definition()).contains(variant)) {
            return false;
        }
        if (!custom.get().variant().equals(variant)) {
            int mask = index.get(block).map(BlockIndex.Entry::connections).orElse(-1);
            appearanceOf(block).update(block, custom.get().definition(), custom.get().facing(),
                    Appearances.drawnVariant(variant, mask));
            index.setVariant(block, variant);
            publishLook(block);
            broadcastLook(block);
            get(block).ifPresent(lights::update);
            lookChanged.forEach(listener -> listener.accept(block));
        }
        return true;
    }

    @Override
    public @NotNull Optional<com.aitchn.prism.api.block.FormedStructure> structure(@NotNull Block controller) {
        return structures.structure(controller);
    }

    @Override
    public @NotNull Optional<Block> controllerOf(@NotNull Block member) {
        return structures.controllerOf(member);
    }

    @Override
    public void refreshConnections(@NotNull Block block) {
        Optional<BlockIndex.Entry> entry = index.get(block);
        Optional<CustomBlock> custom = get(block);
        if (entry.isEmpty() || custom.isEmpty() || entry.get().connections() < 0
                || !custom.get().definition().components().has(PrismComponents.CONNECTIONS)) {
            return;
        }
        int mask = connections(custom.get());
        if (mask != entry.get().connections()) {
            index.setConnections(block, mask);
            appearanceOf(block).update(block, custom.get().definition(), custom.get().facing(),
                    Appearances.drawnVariant(custom.get().variant(), mask));
            publishLook(block);
            broadcastLook(block);
            lookChanged.forEach(listener -> listener.accept(block));
        }
    }

    /** The name the block's current look is drawn under: its variant, plus its connections if it is connected. */
    String drawnVariant(CustomBlock custom) {
        int mask = index.get(custom.block()).map(BlockIndex.Entry::connections).orElse(-1);
        return Appearances.drawnVariant(custom.variant(), mask);
    }

    /** The sides {@code custom} connects to, as a {@link PrismComponents.Connections#bit} mask. */
    private int connections(CustomBlock custom) {
        PrismComponents.Connections spec = custom.definition().components().get(PrismComponents.CONNECTIONS)
                .orElseThrow();
        List<BlockConnectionBehavior> behaviors = custom.definition().behaviors(BlockConnectionBehavior.class);
        Block block = custom.block();
        int mask = 0;
        for (String face : spec.faces()) {
            BlockFace side = BlockFace.valueOf(face);
            Block neighbor = block.getRelative(side);
            // A neighbour in an unloaded chunk or another region's thread counts as not connected.
            if (!neighbor.getWorld().isChunkLoaded(neighbor.getX() >> 4, neighbor.getZ() >> 4)
                    || !scheduler.isOwnedByCurrentThread(neighbor.getLocation())) {
                continue;
            }
            boolean connects;
            if (!behaviors.isEmpty()) {
                connects = false;
                for (BlockConnectionBehavior behavior : behaviors) {
                    try {
                        connects |= behavior.connects(custom, side, neighbor);
                    } catch (RuntimeException e) {
                        log.log(Level.SEVERE, "Connection check of " + custom.key() + " failed", e);
                    }
                }
            } else {
                connects = matches(spec.connectsTo(), custom.key(), side, neighbor);
            }
            if (connects) {
                mask |= PrismComponents.Connections.bit(face);
            }
        }
        return mask;
    }

    private boolean matches(List<String> rules, PrismKey self, BlockFace side, Block neighbor) {
        Optional<PrismKey> custom = index.get(neighbor).map(BlockIndex.Entry::key);
        for (String rule : rules) {
            if (rule.equals("self")) {
                if (custom.isPresent() && custom.get().equals(self)) {
                    return true;
                }
            } else if (rule.startsWith("capability:")) {
                BlockCapability<?> capability = BUILT_IN_CAPABILITIES.get(rule.substring("capability:".length()));
                if (capability != null && capability(neighbor, side.getOppositeFace(), capability).isPresent()) {
                    return true;
                }
            } else if (custom.isPresent() ? custom.get().toString().equals(rule)
                    : VanillaItems.blockMaterial(PrismKey.parse(rule)).filter(neighbor.getType()::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** Capabilities {@code connects_to} can name; a mod's own capabilities go through a BlockConnectionBehavior. */
    private static final java.util.Map<String, BlockCapability<?>> BUILT_IN_CAPABILITIES = java.util.Map.of(
            PrismCapabilities.INVENTORY.key().toString(), PrismCapabilities.INVENTORY,
            PrismCapabilities.ITEM_HANDLER.key().toString(), PrismCapabilities.ITEM_HANDLER);

    /** Told, on the chunk's thread, after the looks of a whole chunk were recomputed (the content changed). */
    void onChunkRelooked(java.util.function.Consumer<BlockIndex.Loaded> listener) {
        this.chunkRelooked.add(listener);
    }

    void onLookChanged(java.util.function.Consumer<Block> listener) {
        this.lookChanged.add(listener);
    }

    /** Told, on the chunk's thread, after a loaded chunk's blocks were activated (load behaviors ran, ticking started). */
    void onChunkActivated(java.util.function.Consumer<BlockIndex.Loaded> listener) {
        this.chunkActivated.add(listener);
    }

    /**
     * Whether blocks of {@code definition} drawn by a server-side display entity (a BetterModel model or a connected
     * shape) also get note block looks for Bedrock players (sent to them per player; Java players keep the entity).
     */
    static boolean bedrockOnlyLooks(BlockDefinition definition) {
        return !usesCarrier(definition);
    }

    /**
     * Whether blocks of {@code definition} are stored as an inert {@link Carrier} and drawn as a client-side look (a
     * borrowed state, or a shell plus the display layer). BetterModel models need a server entity to animate, and a
     * connected shape is drawn by one too, so neither uses a carrier unless the block is passable.
     */
    static boolean usesCarrier(BlockDefinition definition) {
        return Appearances.passable(definition) || ModelAnchors.modelOf(definition).isEmpty()
                && !definition.components().has(PrismComponents.CONNECTIONS);
    }

    /**
     * The carrier a new block of {@code definition} drawn as {@code lookId} gets in this boot, or null if it is drawn by
     * a server-side display entity. Follows the look's tier: one display entity in a clear shell is stored as
     * {@link Carrier#DISPLAY}, everything else solid as {@link Carrier#SOLID} (ADR-0025).
     */
    private @Nullable Carrier carrierFor(BlockDefinition definition, String lookId) {
        if (!usesCarrier(definition)) {
            return null;
        }
        Carrier carrier = clientLooks.get().carrierFor(lookId, Appearances.passable(definition));
        return carrier != null ? carrier : Carrier.SOLID;
    }

    /** The carrier the custom block at {@code block} would get if it were placed now, or null; for the self tests. */
    @Nullable Carrier wantedCarrier(Block block) {
        Optional<BlockIndex.Entry> entry = index.get(block);
        Optional<BlockDefinition> definition = entry.flatMap(e -> definitions.get(e.key()));
        if (entry.isEmpty() || definition.isEmpty()) {
            return null;
        }
        return carrierFor(definition.get(), lookIdOf(entry.get()));
    }

    private BlockAppearance appearance(@Nullable Carrier carrier) {
        if (carrier == null) {
            return display;
        }
        return switch (carrier) {
            case SOLID -> noteBlocks;
            case PASSABLE -> tripwires;
            case DISPLAY -> clearShells;
        };
    }

    /** The carrier the block at {@code block} is stored with (an earlier version's counts), or null. */
    private @Nullable Carrier carrierOf(Block block) {
        Carrier carrier = index.get(block).map(BlockIndex.Entry::carrier).orElse(null);
        return carrier != null ? carrier : Carrier.legacy(block.getType()); // an earlier version's, not migrated yet
    }

    /** The appearance an existing block was placed with. */
    BlockAppearance appearanceOf(Block block) {
        return appearance(carrierOf(block));
    }

    /** The look id of the custom block at {@code block} (what {@link ClientLooks} is keyed by), or null. */
    @Nullable String lookIdOf(Block block) {
        return entry(block).map(entry -> new Appearances.Look(entry.key(),
                Appearances.drawnVariant(entry.variant(), entry.connections()), entry.facing().name()).id()).orElse(null);
    }

    private static String lookIdOf(BlockIndex.Entry entry) {
        return new Appearances.Look(entry.key(), Appearances.drawnVariant(entry.variant(), entry.connections()),
                entry.facing().name()).id();
    }

    /**
     * Whether the client breaks this block itself (what it is shown is breakable), so Prism must not run the dig. Not
     * for a server-side display entity's carrier or the barrier shown for {@link Carrier#DISPLAY}.
     */
    boolean clientBreaks(Block block) {
        Carrier carrier = carrierOf(block);
        return carrier == Carrier.SOLID || carrier == Carrier.PASSABLE;
    }

    /** Whether {@code block} is drawn as a client-side look (the server stores an inert carrier). */
    boolean drawnAsLook(Block block) {
        return index.get(block).map(BlockIndex.Entry::carrier).isPresent();
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

    /** Like {@link #notifyNeighbors} one tick later, for a change that has not happened yet. */
    void notifyNeighborsLater(Block changed) {
        scheduler.runAt(changed.getLocation(), () -> notifyNeighbors(changed));
    }

    /** Tells the custom blocks next to {@code changed} that it changed. */
    void notifyNeighbors(Block changed) {
        structures.changed(changed);
        for (BlockFace face : SIDES) {
            Block neighbor = changed.getRelative(face);
            Runnable notify = () -> {
                refreshConnections(neighbor);
                // A lit block whose light cell was built into moves its light (update is a no-op otherwise).
                get(neighbor).filter(custom -> custom.definition().components().has(PrismComponents.LIGHT))
                        .ifPresent(lights::update);
                get(neighbor).ifPresent(custom -> dispatcher.dispatchBlock(custom.key(),
                        neighbor.getLocation(), BlockNeighborBehavior.class, d -> BlockEventContext.of(custom, null),
                        (b, c) -> b.onNeighborChanged(c, face.getOppositeFace())));
            };
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

    // ---------------------------------------------------------------- carriers and looks

    /** The entry at {@code block}; first checks, on the owning thread, that the world still holds its carrier. */
    private Optional<BlockIndex.Entry> entry(Block block) {
        Optional<BlockIndex.Entry> found = index.get(block);
        if (found.isPresent() && !consistent(block, found.get())) {
            return Optional.empty();
        }
        return found;
    }

    /**
     * Whether the world still agrees with the index about {@code block}. The common case is one material comparison.
     * Only checked on the thread that owns the block; elsewhere the index is trusted until the next check.
     */
    private boolean consistent(Block block, BlockIndex.Entry entry) {
        Carrier carrier = entry.carrier();
        if (carrier == null || !scheduler.isOwnedByCurrentThread(block.getLocation())) {
            return true; // drawn by a display entity (nothing to compare), or not ours to read
        }
        return block.getType() == carrier.material() || reconcile(block, entry);
    }

    /**
     * Slow path of {@link #consistent}: the block holds a carrier of an earlier version (it is converted; the index is
     * the truth) or something else (it was replaced outside Prism: the world wins, the entry is removed).
     *
     * @return whether the entry is still valid
     */
    private boolean reconcile(Block block, BlockIndex.Entry entry) {
        Material type = block.getType();
        Carrier old = Carrier.legacy(type);
        Carrier wanted = entry.carrier() != null ? entry.carrier() : old;
        if (wanted == null) {
            return true;
        }
        if (old == wanted) {
            block.setType(wanted.material(), false);
            if (entry.carrier() == null) {
                index.setCarrier(block, wanted);
            }
            publishLook(block);
            return true;
        }
        Carrier found = Carrier.of(type);
        if (convertible(found, wanted)) {
            // The world holds the other solid carrier (a conversion that was cut short): adopt it, then convert again.
            index.setCarrier(block, found);
            publishLook(block);
            return true;
        }
        log.info("PRISM_CARRIER_LOST " + entry.key() + " at " + block.getX() + "," + block.getY() + "," + block.getZ()
                + " now " + type + "; the world wins, removing the block");
        remove(block, false, stack -> {}, true);
        return false;
    }

    /** First access to a chunk: migrate old carriers, drop blocks whose carrier is gone, publish the looks. */
    private void onMirrorLoaded(BlockIndex.Loaded mirror) {
        Location center = center(mirror.world, mirror.chunkX, mirror.chunkZ);
        if (!scheduler.isOwnedByCurrentThread(center)) {
            return; // checked lazily by the first get or packet that notices
        }
        mirror.blocks.forEach((packed, entry) -> {
            Block block = mirror.block(packed);
            boolean valid = entry.carrier() == null
                    ? Carrier.legacy(block.getType()) == null || reconcile(block, entry) // old note block or tripwire
                    : consistent(block, entry);
            if (valid) {
                publishLook(block);
            }
        });
    }

    /** Computes what viewers see at {@code block} and publishes it for the packet rewriter. Owning thread only. */
    void publishLook(Block block) {
        BlockIndex.Loaded mirror = index.load(block.getChunk());
        long packed = BlockIndex.packed(block);
        BlockIndex.Entry entry = mirror.blocks.get(packed);
        Carrier carrier = entry == null ? null : entry.carrier();
        if (carrier != null && scheduler.isOwnedByCurrentThread(block.getLocation())) {
            // Only once this boot's looks know the look: before that (or for a look that got nothing) keep what is there.
            Carrier wanted = definitions.get(entry.key()).filter(PrismBlockService::usesCarrier)
                    .map(definition -> clientLooks.get().carrierFor(lookIdOf(entry), Appearances.passable(definition)))
                    .orElse(null);
            if (wanted != null && convertible(carrier, wanted) && block.getType() == carrier.material()) {
                // The look's tier changed since the block was stored (settings or content): follow it. ADR-0025.
                block.setType(wanted.material(), false);
                index.setCarrier(block, wanted);
                carrier = wanted;
            }
        }
        BlockLook look = carrier == null ? null : lookFor(carrier, entry);
        if (look == null) {
            mirror.clearLook(packed);
            return;
        }
        mirror.setLook(packed, look);
    }

    /** Whether a block stored as {@code from} is converted to {@code to}: only between the two solid carriers. */
    private static boolean convertible(@Nullable Carrier from, @Nullable Carrier to) {
        return from != to && (from == Carrier.SOLID || from == Carrier.DISPLAY)
                && (to == Carrier.SOLID || to == Carrier.DISPLAY);
    }

    /** What viewers see for {@code entry}, or null if its look got no client representation in this boot. */
    private BlockLook lookFor(Carrier carrier, BlockIndex.Entry entry) {
        String drawn = Appearances.drawnVariant(entry.variant(), entry.connections());
        return clientLooks.get().lookFor(carrier, new Appearances.Look(entry.key(), drawn, entry.facing().name()).id());
    }

    /**
     * Shows every player who has the chunk the current look of {@code block}. The world does not change when only the
     * look does, so the server sends nothing by itself.
     */
    void broadcastLook(Block block) {
        BlockIndex.Loaded mirror = index.loaded(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
        BlockLook look = mirror == null ? null : mirror.looks.get(BlockIndex.packed(block));
        if (look == null) {
            return;
        }
        World world = block.getWorld();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        long chunkKey = Chunk.getChunkKey(x >> 4, z >> 4);
        for (Player player : world.getPlayers()) {
            scheduler.runOn(player, () -> {
                if (player.getWorld() == world && player.isChunkSent(chunkKey)) {
                    LookSender.send(player, x, y, z, look.look());
                }
            }, null);
        }
    }

    /**
     * Sends {@code player} the look of every custom block among the six neighbours of {@code at} and {@code at}
     * itself, undoing what the client predicted locally. Reads only the index, so it is safe on any thread.
     */
    void resendLooks(Player player, Block at) {
        World world = at.getWorld();
        for (int i = 0; i < 7; i++) {
            int x = at.getX() + (i < 6 ? SIDES[i].getModX() : 0);
            int y = at.getY() + (i < 6 ? SIDES[i].getModY() : 0);
            int z = at.getZ() + (i < 6 ? SIDES[i].getModZ() : 0);
            BlockLook look = index.looks(world.getUID(), x >> 4, z >> 4).at(x & 15, y, z & 15);
            if (look != null) {
                LookSender.send(player, x, y, z, look.look());
            }
        }
    }

    /** What the packet rewriter would send at {@code block}, or null; for tests and diagnostics. */
    @Nullable BlockLook lookOf(Block block) {
        return index.looks(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4)
                .at(block.getX() & 15, block.getY(), block.getZ() & 15);
    }

    /** The look snapshot of a chunk for the packet rewriter (network threads). */
    ChunkLooks looks(UUID world, int chunkX, int chunkZ) {
        return index.looks(world, chunkX, chunkZ);
    }

    /** Whether the world has any look to rewrite (network threads). */
    boolean anyLooks(UUID world) {
        return index.anyLooks(world);
    }

    // Hooks for ConsistencySelfTest: they read or simulate what the world and the packet layer would show.

    /** Whether the index has an entry at {@code block}, without the carrier check that {@code get} does. */
    boolean indexed(Block block) {
        return index.get(block).isPresent();
    }

    /** The look id recomputed from the index entry alone, or -1: what {@link #lookOf} must agree with. */
    int expectedLookId(Block block) {
        BlockLook look = expectedLook(block);
        return look == null ? -1 : look.look();
    }

    /**
     * The whole look recomputed from the index entry alone, or null. Two display looks share the shell state and differ
     * only in {@link BlockLook#display()}, so a check that the look changed must compare this, not the state id.
     */
    BlockLook expectedLook(Block block) {
        Optional<BlockIndex.Entry> entry = index.get(block);
        if (entry.isEmpty() || entry.get().carrier() == null) {
            return null;
        }
        return lookFor(entry.get().carrier(), entry.get());
    }

    /** Makes the entry look like one an earlier version wrote (no carrier recorded). */
    void forgetCarrier(Block block) {
        index.setCarrier(block, null);
        index.load(block.getChunk()).clearLook(BlockIndex.packed(block));
    }

    /** Drops the chunk's in-memory mirror and reads it from the chunk again, as a chunk load does. */
    void reloadIndex(Chunk chunk) {
        index.unload(chunk);
        index.load(chunk);
    }

    private final Set<String> pendingChecks = ConcurrentHashMap.newKeySet();

    /**
     * Called from a network thread: an outgoing packet put something that is not the carrier at a custom block's
     * position. Schedules a check on the owning region thread; world state is never touched from here.
     */
    void foreignChange(UUID worldId, int x, int y, int z) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            return;
        }
        String pending = worldId + "/" + x + "/" + y + "/" + z;
        if (!pendingChecks.add(pending)) {
            return;
        }
        scheduler.runAt(new Location(world, x, y, z), () -> {
            pendingChecks.remove(pending);
            if (world.isChunkLoaded(x >> 4, z >> 4)) {
                Block block = world.getBlockAt(x, y, z);
                index.get(block).ifPresent(entry -> consistent(block, entry));
            }
        });
    }

    // ---------------------------------------------------------------- chunk lifecycle

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        index.load(chunk); // migrates and publishes the chunk's looks (see onMirrorLoaded)
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
        structures.unload(chunk);
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
        structures.contentChanged();
        for (BlockIndex.Loaded mirror : index.allLoaded()) {
            scheduler.runAt(new Location(mirror.world, (mirror.chunkX << 4) + 8, 64, (mirror.chunkZ << 4) + 8), () -> {
                if (mirror.world.isChunkLoaded(mirror.chunkX, mirror.chunkZ)) {
                    stopTicker(mirror);
                    for (CustomBlock custom : blocksIn(mirror.world.getChunkAt(mirror.chunkX, mirror.chunkZ))) {
                        startTicking(custom);
                        lights.update(custom); // a level or the lit variants may have changed
                    }
                    // Definitions came or went: the looks of this chunk's blocks may have changed with them.
                    mirror.blocks.forEach((packed, entry) -> publishLook(mirror.block(packed)));
                    mirror.looks.forEach((packed, look) -> broadcastLook(mirror.block(packed)));
                    chunkRelooked.forEach(listener -> listener.accept(mirror));
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
            // Neighbours in chunks that were unloaded when the mask was saved can be seen now.
            refreshConnections(custom.block());
            structures.load(custom);
            lights.update(custom); // the light may have changed or gone while the chunk was unloaded
        }
        refreshBorders(chunk);
        structures.chunkLoaded(chunk);
        BlockIndex.Loaded mirror = index.loaded(chunk);
        if (mirror != null) {
            chunkActivated.forEach(listener -> listener.accept(mirror));
        }
    }

    /** Connected blocks on the edges of the loaded chunks around {@code chunk} may now reach into it. */
    private void refreshBorders(Chunk chunk) {
        int[][] around = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] step : around) {
            int x = chunk.getX() + step[0];
            int z = chunk.getZ() + step[1];
            BlockIndex.Loaded mirror = index.loaded(chunk.getWorld(), x, z);
            if (mirror == null) {
                continue;
            }
            mirror.blocks.forEach((packed, entry) -> {
                if (entry.connections() < 0) {
                    return;
                }
                Block block = mirror.block(packed);
                int localX = block.getX() & 15;
                int localZ = block.getZ() & 15;
                // The edge that faces the newly loaded chunk.
                boolean edge = step[0] == 1 ? localX == 0 : step[0] == -1 ? localX == 15
                        : step[1] == 1 ? localZ == 0 : localZ == 15;
                if (edge) {
                    scheduler.runAt(block.getLocation(), () -> refreshConnections(block));
                }
            });
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
        return center(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    private static Location center(World world, int chunkX, int chunkZ) {
        return new Location(world, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8);
    }
}
