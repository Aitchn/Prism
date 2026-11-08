package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.block.FormedStructure;
import com.aitchn.prism.api.block.StructureBehavior;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.core.mod.BehaviorDispatcher;
import com.aitchn.prism.core.structure.StructurePattern;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Multiblock structures ({@code prism:structure}): matches patterns around controllers when blocks change, keeps the
 * formed ones in memory (member to controller) and their facing in the chunk index, and calls
 * {@link StructureBehavior}. All work for one controller runs on the thread that owns it; a pattern reaching into an
 * unloaded chunk or another Folia region does not match.
 */
final class StructureTracker {

    /** {@code watched} is every checked cell, members plus the cells that must stay air. */
    private record Formed(BlockFace facing, List<Location> members, List<Location> watched) {}

    private final PrismBlockService blocks;
    private final Registry<BlockDefinition> definitions;
    private final BlockIndex index;
    private final BehaviorDispatcher dispatcher;
    private final PlatformScheduler scheduler;
    private final Map<PrismKey, StructurePattern> patterns = new ConcurrentHashMap<>();
    private final Map<Location, Formed> formed = new ConcurrentHashMap<>();
    private final Map<Location, Location> controllerOf = new ConcurrentHashMap<>();
    private volatile int reach = -1;

    StructureTracker(PrismBlockService blocks, Registry<BlockDefinition> definitions, BlockIndex index,
            BehaviorDispatcher dispatcher, PlatformScheduler scheduler) {
        this.blocks = blocks;
        this.definitions = definitions;
        this.index = index;
        this.dispatcher = dispatcher;
        this.scheduler = scheduler;
    }

    void contentChanged() {
        patterns.clear();
        reach = -1;
    }

    Optional<FormedStructure> structure(Block controller) {
        Formed found = formed.get(controller.getLocation());
        return found == null ? Optional.empty() : Optional.of(new FormedStructure(controller, found.facing(),
                found.members().stream().map(Location::getBlock).toList()));
    }

    Optional<Block> controllerOf(Block member) {
        Location controller = controllerOf.get(member.getLocation());
        Formed found = controller == null ? null : formed.get(controller);
        return found != null && found.members().contains(member.getLocation())
                ? Optional.of(controller.getBlock()) : Optional.empty();
    }

    /** {@code changed} was placed or removed: re-check structures it belongs to or could complete. */
    void changed(Block changed) {
        if (maxReach() < 0) {
            return; // no mod defines a structure
        }
        Location at = changed.getLocation();
        Location owner = controllerOf.get(at);
        if (owner != null) {
            recheck(owner.getBlock());
        }
        if (!formed.containsKey(at)) {
            blocks.get(changed).ifPresent(this::check);
        }
        // Controllers near enough for their pattern to reach the change, not yet formed.
        int r = maxReach();
        World world = changed.getWorld();
        for (int cx = (changed.getX() - r) >> 4; cx <= (changed.getX() + r) >> 4; cx++) {
            for (int cz = (changed.getZ() - r) >> 4; cz <= (changed.getZ() + r) >> 4; cz++) {
                BlockIndex.Loaded mirror = index.loaded(world, cx, cz);
                if (mirror == null) {
                    continue;
                }
                mirror.blocks.forEach((packed, entry) -> {
                    StructurePattern pattern = pattern(entry.key());
                    if (pattern == null) {
                        return;
                    }
                    Block controller = mirror.block(packed);
                    if (controller.equals(changed) || formed.containsKey(controller.getLocation())
                            || Math.abs(controller.getX() - changed.getX()) > pattern.reach()
                            || Math.abs(controller.getY() - changed.getY()) > pattern.reach()
                            || Math.abs(controller.getZ() - changed.getZ()) > pattern.reach()) {
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

    /** The chunk of {@code custom} loaded: restore a formed structure, or report one that broke meanwhile. */
    void load(CustomBlock custom) {
        if (pattern(custom.key()) == null) {
            return;
        }
        Optional<String> was = index.structure(custom.block());
        int turn = match(custom);
        if (turn >= 0) {
            register(custom, turn, was.isEmpty());
        } else if (was.isPresent()) {
            index.setStructure(custom.block(), null);
            broken(custom);
        }
    }

    /** The controller's chunk unloads: forget its structure without calling behaviors. */
    void unload(Chunk chunk) {
        formed.keySet().removeIf(location -> {
            if (location.getWorld() != chunk.getWorld() || location.getBlockX() >> 4 != chunk.getX()
                    || location.getBlockZ() >> 4 != chunk.getZ()) {
                return false;
            }
            controllerOf.values().removeIf(location::equals);
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

    private void check(CustomBlock custom) {
        if (pattern(custom.key()) == null) {
            return;
        }
        Location at = custom.block().getLocation();
        Formed before = formed.get(at);
        int turn = match(custom);
        if (turn >= 0) {
            boolean fresh = before == null || !before.facing().name().equals(StructurePattern.FACINGS.get(turn));
            if (fresh && before != null) {
                forget(at);
                broken(custom);
            }
            register(custom, turn, fresh);
        } else if (before != null) {
            forget(at);
            index.setStructure(custom.block(), null);
            broken(custom);
        }
    }

    private int match(CustomBlock custom) {
        StructurePattern pattern = pattern(custom.key());
        Block origin = custom.block();
        return pattern.match(cell -> {
            Block block = origin.getRelative(cell.dx(), cell.dy(), cell.dz());
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                    || !scheduler.isOwnedByCurrentThread(block.getLocation())) {
                return false;
            }
            Optional<PrismKey> key = index.get(block).map(BlockIndex.Entry::key);
            if (cell.expect().equals("air")) {
                return key.isEmpty() && block.getType().isAir();
            }
            if (key.isPresent()) {
                return key.get().toString().equals(cell.expect());
            }
            return VanillaItems.blockMaterial(PrismKey.parse(cell.expect())).filter(block.getType()::equals)
                    .isPresent();
        });
    }

    private void register(CustomBlock custom, int turn, boolean announce) {
        StructurePattern pattern = pattern(custom.key());
        Block origin = custom.block();
        List<Location> members = new ArrayList<>();
        List<Location> watched = new ArrayList<>();
        for (StructurePattern.Cell cell : pattern.cells(turn)) {
            Location location = origin.getRelative(cell.dx(), cell.dy(), cell.dz()).getLocation();
            watched.add(location);
            if (!cell.expect().equals("air")) {
                members.add(location);
            }
        }
        BlockFace facing = BlockFace.valueOf(StructurePattern.FACINGS.get(turn));
        Location at = origin.getLocation();
        forget(at);
        formed.put(at, new Formed(facing, List.copyOf(members), List.copyOf(watched)));
        watched.forEach(cell -> controllerOf.put(cell, at));
        index.setStructure(origin, facing.name());
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
            old.watched().forEach(cell -> controllerOf.remove(cell, controller));
        }
    }

    private StructurePattern pattern(PrismKey key) {
        StructurePattern cached = patterns.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<PrismComponents.Structure> spec = definitions.get(key)
                .flatMap(definition -> definition.components().get(PrismComponents.STRUCTURE));
        if (spec.isEmpty()) {
            return null;
        }
        StructurePattern compiled = StructurePattern.compile(spec.get());
        patterns.put(key, compiled);
        return compiled;
    }

    private int maxReach() {
        int known = reach;
        if (known == -1) {
            known = -2;
            for (PrismKey key : definitions.keys()) {
                StructurePattern pattern = pattern(key);
                if (pattern != null) {
                    known = Math.max(known, pattern.reach());
                }
            }
            reach = known;
        }
        return known < 0 ? -1 : known;
    }
}
