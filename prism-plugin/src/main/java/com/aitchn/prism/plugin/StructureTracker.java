package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.block.FormedStructure;
import com.aitchn.prism.api.block.StructureBehavior;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.core.mod.BehaviorDispatcher;
import com.aitchn.prism.core.structure.StructureShape;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Multiblock structures ({@code prism:structure} patterns and {@code prism:cuboid} boxes): looks for them around
 * controllers when blocks change, keeps the formed ones in memory (member to controller) and their signature in the
 * chunk index, and calls {@link StructureBehavior}. All work for one controller runs on the thread that owns it; a
 * structure reaching into an unloaded chunk or another Folia region does not form.
 */
final class StructureTracker {

    /** {@code watched} is every checked cell, members plus the cells that must stay air. */
    private record Formed(BlockFace facing, String signature, List<Location> members, List<Location> watched,
            Location min, Location max) {}

    private final PrismBlockService blocks;
    private final Registry<BlockDefinition> definitions;
    private final BlockIndex index;
    private final BehaviorDispatcher dispatcher;
    private final PlatformScheduler scheduler;
    private final Map<PrismKey, StructureShape> shapes = new ConcurrentHashMap<>();
    private final Map<Location, Formed> formed = new ConcurrentHashMap<>();
    /** Cell to the formed controllers that watch it: several controllers (valves of one box) can share cells. */
    private final Map<Location, Set<Location>> controllersOf = new ConcurrentHashMap<>();
    private volatile int reach = -1;

    StructureTracker(PrismBlockService blocks, Registry<BlockDefinition> definitions, BlockIndex index,
            BehaviorDispatcher dispatcher, PlatformScheduler scheduler) {
        this.blocks = blocks;
        this.definitions = definitions;
        this.index = index;
        this.dispatcher = dispatcher;
        this.scheduler = scheduler;
    }

    /**
     * Content changed (a mod enabled, disabled or reloaded): drop the compiled shapes, then re-check every loaded
     * custom block on its own thread, so structures whose pattern changed or went are released and controllers that
     * gained one can form.
     */
    void contentChanged() {
        shapes.clear();
        reach = -1;
        for (BlockIndex.Loaded mirror : index.allLoaded()) {
            Location center = new Location(mirror.world, (mirror.chunkX << 4) + 8, 64, (mirror.chunkZ << 4) + 8);
            scheduler.runAt(center, () -> {
                if (!mirror.world.isChunkLoaded(mirror.chunkX, mirror.chunkZ)) {
                    return;
                }
                mirror.blocks.keySet().forEach(packed -> recheck(mirror.block(packed)));
            });
        }
    }

    Optional<FormedStructure> structure(Block controller) {
        Formed found = formed.get(controller.getLocation());
        return found == null ? Optional.empty() : Optional.of(new FormedStructure(controller, found.facing(),
                found.members().stream().map(Location::getBlock).toList(), found.min().getBlock(),
                found.max().getBlock()));
    }

    Optional<Block> controllerOf(Block member) {
        for (Location controller : controllersOf.getOrDefault(member.getLocation(), Set.of())) {
            Formed found = formed.get(controller);
            if (found != null && found.members().contains(member.getLocation())) {
                return Optional.of(controller.getBlock());
            }
        }
        return Optional.empty();
    }

    /** {@code changed} was placed or removed: re-check structures it belongs to or could complete. */
    void changed(Block changed) {
        if (maxReach() < 0) {
            return; // no mod defines a structure
        }
        Location at = changed.getLocation();
        for (Location owner : List.copyOf(controllersOf.getOrDefault(at, Set.of()))) {
            recheck(owner.getBlock());
        }
        if (!formed.containsKey(at)) {
            blocks.get(changed).ifPresent(this::check);
        }
        // Controllers near enough for their pattern to reach the change, not yet formed.
        recheckNear(changed.getWorld(), changed.getX(), changed.getY(), changed.getZ(), changed.getX(),
                changed.getY(), changed.getZ(), changed);
    }

    /**
     * {@code chunk} loaded (after {@link #load} ran for its own blocks): controllers in other chunks whose pattern
     * reaches into it could only be checked partly before, so check them again.
     */
    void chunkLoaded(Chunk chunk) {
        if (maxReach() < 0) {
            return;
        }
        World world = chunk.getWorld();
        int x = chunk.getX() << 4;
        int z = chunk.getZ() << 4;
        recheckNear(world, x, world.getMinHeight(), z, x + 15, world.getMaxHeight() - 1, z + 15, null);
    }

    /** Checks the unformed controllers (other than {@code skip}) whose pattern reaches the given box. */
    private void recheckNear(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Block skip) {
        int r = maxReach();
        for (int cx = (minX - r) >> 4; cx <= (maxX + r) >> 4; cx++) {
            for (int cz = (minZ - r) >> 4; cz <= (maxZ + r) >> 4; cz++) {
                BlockIndex.Loaded mirror = index.loaded(world, cx, cz);
                if (mirror == null) {
                    continue;
                }
                mirror.blocks.forEach((packed, entry) -> {
                    StructureShape shape = shape(entry.key());
                    if (shape == null) {
                        return;
                    }
                    Block controller = mirror.block(packed);
                    if (controller.equals(skip) || formed.containsKey(controller.getLocation())
                            || outside(controller.getX(), minX, maxX, shape.reach())
                            || outside(controller.getY(), minY, maxY, shape.reach())
                            || outside(controller.getZ(), minZ, maxZ, shape.reach())) {
                        return;
                    }
                    Location location = controller.getLocation();
                    if (scheduler.isOwnedByCurrentThread(location)) {
                        blocks.get(controller).ifPresent(this::check);
                    } else {
                        scheduler.runAt(location, () -> blocks.get(controller).ifPresent(this::check));
                    }
                });
            }
        }
    }

    private static boolean outside(int value, int min, int max, int reach) {
        return value < min - reach || value > max + reach;
    }

    /** The chunk of {@code custom} loaded: restore a formed structure, or report one that broke meanwhile. */
    void load(CustomBlock custom) {
        check(custom);
    }

    /** The controller's chunk unloads: forget its structure without calling behaviors. */
    void unload(Chunk chunk) {
        formed.keySet().removeIf(location -> {
            if (location.getWorld() != chunk.getWorld() || location.getBlockX() >> 4 != chunk.getX()
                    || location.getBlockZ() >> 4 != chunk.getZ()) {
                return false;
            }
            controllersOf.values().forEach(set -> set.remove(location));
            return true;
        });
    }

    /** The controller is being removed: its structure breaks first, while the block and its data still exist. */
    void controllerRemoved(CustomBlock custom) {
        if (formed.containsKey(custom.block().getLocation())) {
            forget(custom.block().getLocation());
            index.setStructure(custom.block(), null);
            broken(custom);
        }
    }

    private void recheck(Block controller) {
        Optional<CustomBlock> custom = blocks.get(controller);
        if (custom.isEmpty()) {
            forget(controller.getLocation());
            return;
        }
        check(custom.get());
    }

    /**
     * Looks for the structure around {@code custom} and reports the difference to what was formed, in memory or saved
     * in the chunk. A structure that no longer stands because part of it is in an unloaded chunk (or another region)
     * stays formed; {@link #chunkLoaded} checks it again once that part is there.
     */
    private void check(CustomBlock custom) {
        Block block = custom.block();
        Location at = block.getLocation();
        Formed before = formed.get(at);
        Optional<String> saved = index.structure(block);
        boolean was = before != null || saved.isPresent();
        StructureShape shape = shape(custom.key());
        if (shape == null) {
            if (was) { // the definition lost its structure
                forget(at);
                index.setStructure(block, null);
                broken(custom);
            }
            return;
        }
        StructureShape.Probe probe = (dx, dy, dz, expect) -> is(block.getRelative(dx, dy, dz), expect);
        StructureShape.Result result = shape.find(probe);
        StructureShape.Match match = result.match();
        if (match != null) {
            boolean same = before != null ? before.signature().equals(match.signature())
                    : saved.filter(match.signature()::equals).isPresent();
            if (was && !same) {
                forget(at);
                broken(custom);
            }
            register(custom, match, !same);
        } else if (was && !(result.uncertain()
                && shape.verify(probe, before != null ? before.signature() : saved.orElseThrow()).uncertain())) {
            // Gone, unless the part of the earlier structure that cannot be seen now might still hold it up.
            forget(at);
            index.setStructure(block, null);
            broken(custom);
        }
    }

    private boolean available(Block block) {
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                && scheduler.isOwnedByCurrentThread(block.getLocation());
    }

    /** Whether {@code block} is {@code expect} (a block key or "air"); null if it cannot be looked at here. */
    private Boolean is(Block block, String expect) {
        if (!available(block)) {
            return null;
        }
        Optional<PrismKey> key = index.get(block).map(BlockIndex.Entry::key);
        if (expect.equals("air")) {
            return key.isEmpty() && block.getType().isAir();
        }
        if (key.isPresent()) {
            return key.get().toString().equals(expect);
        }
        return VanillaItems.blockMaterial(PrismKey.parse(expect)).filter(block.getType()::equals).isPresent();
    }

    private void register(CustomBlock custom, StructureShape.Match match, boolean announce) {
        Block origin = custom.block();
        List<Location> members = new ArrayList<>();
        List<Location> watched = new ArrayList<>();
        match.members().forEach(o -> members.add(origin.getRelative(o.dx(), o.dy(), o.dz()).getLocation()));
        int[] min = {0, 0, 0};
        int[] max = {0, 0, 0};
        for (StructureShape.Offset o : match.watched()) {
            watched.add(origin.getRelative(o.dx(), o.dy(), o.dz()).getLocation());
            min[0] = Math.min(min[0], o.dx());
            min[1] = Math.min(min[1], o.dy());
            min[2] = Math.min(min[2], o.dz());
            max[0] = Math.max(max[0], o.dx());
            max[1] = Math.max(max[1], o.dy());
            max[2] = Math.max(max[2], o.dz());
        }
        Location at = origin.getLocation();
        forget(at);
        formed.put(at, new Formed(BlockFace.valueOf(match.facing()), match.signature(), List.copyOf(members),
                List.copyOf(watched), origin.getRelative(min[0], min[1], min[2]).getLocation(),
                origin.getRelative(max[0], max[1], max[2]).getLocation()));
        watched.forEach(cell -> controllersOf.computeIfAbsent(cell, c -> ConcurrentHashMap.newKeySet()).add(at));
        index.setStructure(origin, match.signature());
        if (announce) {
            FormedStructure structure = structure(origin).orElseThrow();
            dispatcher.dispatchBlock(custom.key(), at, StructureBehavior.class, d -> BlockEventContext.of(custom, null),
                    (b, c) -> b.onFormed(c, structure));
        }
    }

    private void broken(CustomBlock custom) {
        dispatcher.dispatchBlock(custom.key(), custom.block().getLocation(), StructureBehavior.class,
                d -> BlockEventContext.of(custom, null), (b, c) -> b.onBroken(c));
    }

    private void forget(Location controller) {
        Formed old = formed.remove(controller);
        if (old != null) {
            old.watched().forEach(cell -> controllersOf.computeIfPresent(cell, (c, set) -> {
                set.remove(controller);
                return set.isEmpty() ? null : set;
            }));
        }
    }

    private StructureShape shape(PrismKey key) {
        StructureShape cached = shapes.get(key);
        if (cached != null) {
            return cached;
        }
        StructureShape compiled = definitions.get(key).map(StructureShape::of).orElse(null);
        if (compiled != null) {
            shapes.put(key, compiled);
        }
        return compiled;
    }

    private int maxReach() {
        int known = reach;
        if (known == -1) {
            known = -2;
            for (PrismKey key : definitions.keys()) {
                StructureShape shape = shape(key);
                if (shape != null) {
                    known = Math.max(known, shape.reach());
                }
            }
            reach = known;
        }
        return known < 0 ? -1 : known;
    }
}
