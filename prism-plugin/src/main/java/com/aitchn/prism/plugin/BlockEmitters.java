package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.effect.Emitter;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.core.effect.EmitterSet;
import com.aitchn.prism.core.effect.ParticleBudget;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Block particle emitters ({@code prism:particles}, ADR-0027). One shared clock runs every
 * {@code effects.particles.interval} ticks, and only while some loaded chunk has an emitting block:
 *
 * <ol>
 *   <li>Chunks whose blocks changed (placed, removed, variant switched, chunk loaded, content changed) are marked dirty
 *       by their own thread; the clock rebuilds each dirty chunk's {@link EmitterSet} once, however many changes it had.
 *   <li>For each player, the chunks within the largest emitter range around them are looked up. A player near no
 *       emitter costs nothing more; for the others one task runs on the player's thread, which keeps the chunks the
 *       client has, selects the due emitters in range nearest first within the player's budget
 *       ({@link EmitterSet#select}) and sends them with {@link org.bukkit.entity.Player#spawnParticle}.
 * </ol>
 *
 * Nothing is scheduled per block, and nothing at all runs when no chunk with emitters is loaded.
 */
final class BlockEmitters implements Listener {

    private final Registry<BlockDefinition> definitions;
    private final BlockIndex index;
    private final PlatformScheduler scheduler;
    private final EffectViewers viewers;
    private final EffectParticles particles;
    private final int interval;
    private final double defaultRange;
    private final int budgetPerCycle;
    /** World to chunk key ({@link Chunk#getChunkKey}) to the chunk's emitters; only chunks that have some. */
    private final Map<UUID, Map<Long, EmitterSet>> sets = new ConcurrentHashMap<>();
    /** World to the chunks to rebuild at the next cycle. */
    private final Map<UUID, Set<Long>> dirty = new ConcurrentHashMap<>();
    private final AtomicLong cycle = new AtomicLong();
    private final AtomicLong tasks = new AtomicLong();
    private volatile boolean anyEmitting;
    private ScheduledTask clock;

    /**
     * @param interval        ticks between cycles ({@code effects.particles.interval})
     * @param defaultRange    the range of an emitter that sets none ({@code effects.particles.range})
     * @param perPlayerSecond particles one player may get per second from block emitters
     *                        ({@code effects.particles.max-per-player})
     */
    BlockEmitters(Registry<BlockDefinition> definitions, BlockIndex index, PlatformScheduler scheduler,
            EffectViewers viewers, EffectParticles particles, int interval, double defaultRange, int perPlayerSecond) {
        this.definitions = definitions;
        this.index = index;
        this.scheduler = scheduler;
        this.viewers = viewers;
        this.particles = particles;
        this.interval = Math.max(1, interval);
        this.defaultRange = defaultRange;
        this.budgetPerCycle = ParticleBudget.perCycle(perPlayerSecond, this.interval);
        contentChanged();
    }

    int interval() {
        return interval;
    }

    /** Definitions came or went: whether any block declares emitters at all decides whether chunks are watched. */
    void contentChanged() {
        boolean any = false;
        for (PrismKey key : definitions.keys()) {
            any |= definitions.get(key).map(d -> d.components().has(PrismComponents.PARTICLES)).orElse(false);
        }
        anyEmitting = any;
    }

    // ---------------------------------------------------------------- what changes the emitters

    /** A custom block was placed, removed or changed; any thread. */
    void changed(Block block) {
        markDirty(block.getWorld().getUID(), Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4));
    }

    /** A chunk's blocks were read (chunk load) or its looks recomputed (content change); any thread. */
    void chunkChanged(BlockIndex.Loaded mirror) {
        markDirty(mirror.world.getUID(), Chunk.getChunkKey(mirror.chunkX, mirror.chunkZ));
    }

    private void markDirty(UUID world, long chunkKey) {
        if (!anyEmitting && !sets.containsKey(world)) {
            return;
        }
        dirty.computeIfAbsent(world, w -> ConcurrentHashMap.newKeySet()).add(chunkKey);
        ensureClock();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        Map<Long, EmitterSet> world = sets.get(chunk.getWorld().getUID());
        if (world != null) {
            world.remove(Chunk.getChunkKey(chunk.getX(), chunk.getZ()));
        }
    }

    /** Rebuilds a chunk's emitters from the index mirror (a concurrent map of immutable entries); any thread. */
    private void rebuild(UUID worldId, long chunkKey) {
        World world = Bukkit.getWorld(worldId);
        int chunkX = (int) chunkKey;
        int chunkZ = (int) (chunkKey >> 32);
        BlockIndex.Loaded mirror = world == null ? null : index.loaded(world, chunkX, chunkZ);
        EmitterSet.Builder builder = new EmitterSet.Builder(interval, defaultRange);
        if (mirror != null) {
            mirror.blocks.forEach((packed, entry) -> {
                Optional<PrismComponents.Particles> declared = definitions.get(entry.key())
                        .flatMap(d -> d.components().get(PrismComponents.PARTICLES));
                if (declared.isEmpty()) {
                    return;
                }
                List<Emitter> emitters = declared.get().emitters();
                int y = (int) (packed >> 8);
                int x = (chunkX << 4) + (int) ((packed >> 4) & 15);
                int z = (chunkZ << 4) + (int) (packed & 15);
                builder.add(x, y, z, entry.facing().name(), emitters, entry.variant());
            });
        }
        EmitterSet built = builder.build();
        Map<Long, EmitterSet> perWorld = sets.computeIfAbsent(worldId, w -> new ConcurrentHashMap<>());
        if (built.isEmpty()) {
            perWorld.remove(chunkKey);
        } else {
            perWorld.put(chunkKey, built);
        }
    }

    // ---------------------------------------------------------------- the clock

    private synchronized void ensureClock() {
        if (clock == null) {
            clock = scheduler.runGlobalTimer(this::cycle, interval, interval);
        }
    }

    private synchronized boolean stopIfIdle() {
        boolean idle = dirty.values().stream().allMatch(Set::isEmpty)
                && sets.values().stream().allMatch(Map::isEmpty);
        if (idle && clock != null) {
            clock.cancel();
            clock = null;
        }
        return idle;
    }

    /** One cycle, on the global thread. */
    private void cycle() {
        long now = cycle.incrementAndGet();
        dirty.forEach((world, chunks) -> {
            for (var it = chunks.iterator(); it.hasNext();) {
                long key = it.next();
                it.remove();
                rebuild(world, key);
            }
        });
        if (stopIfIdle() || viewers.isEmpty()) {
            return;
        }
        for (EffectViewers.Viewer viewer : viewers.all()) {
            UUID worldId = viewer.world;
            Map<Long, EmitterSet> world = worldId == null ? null : sets.get(worldId);
            if (world == null || world.isEmpty()) {
                continue;
            }
            List<Long> near = near(world, viewer.x, viewer.z);
            if (near.isEmpty()) {
                continue; // no emitter can reach this player: no task
            }
            tasks.incrementAndGet();
            Player player = viewer.player;
            scheduler.runOn(player, () -> emit(player, worldId, near, now), null);
        }
    }

    /** The chunk keys around {@code (x, z)} (with their emitters) that could hold an emitter in range. */
    private static List<Long> near(Map<Long, EmitterSet> world, double x, double z) {
        double reach = 0;
        for (EmitterSet set : world.values()) {
            reach = Math.max(reach, set.maxRange());
        }
        int radius = (int) Math.ceil(reach / 16.0);
        int centerX = (int) Math.floor(x) >> 4;
        int centerZ = (int) Math.floor(z) >> 4;
        List<Long> found = new ArrayList<>();
        if ((2L * radius + 1) * (2L * radius + 1) > world.size()) {
            for (Long key : world.keySet()) {
                int chunkX = (int) (long) key;
                int chunkZ = (int) (key >> 32);
                if (Math.abs(chunkX - centerX) <= radius && Math.abs(chunkZ - centerZ) <= radius) {
                    found.add(key);
                }
            }
            return found;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long key = Chunk.getChunkKey(centerX + dx, centerZ + dz);
                if (world.containsKey(key)) {
                    found.add(key);
                }
            }
        }
        return found;
    }

    /** On the player's thread: the emitters this player gets this cycle. */
    private void emit(Player player, UUID worldId, List<Long> near, long now) {
        if (!player.isOnline() || !player.getWorld().getUID().equals(worldId)) {
            return;
        }
        Map<Long, EmitterSet> world = sets.get(worldId);
        if (world == null) {
            return;
        }
        List<EmitterSet> visible = new ArrayList<>();
        for (long key : near) {
            EmitterSet set = world.get(key);
            if (set != null && player.isChunkSent(key)) {
                visible.add(set);
            }
        }
        org.bukkit.Location eye = player.getEyeLocation();
        for (EmitterSet.Pick pick : EmitterSet.select(now, eye.getX(), eye.getY(), eye.getZ(), visible,
                budgetPerCycle)) {
            EmitterSet set = pick.set();
            int i = pick.index();
            particles.resolve(set.particle(i)).ifPresent(resolved -> EffectParticles.send(player, resolved,
                    set.x(i), set.y(i), set.z(i), pick.emissions()));
        }
    }

    // ---------------------------------------------------------------- self test

    /** The emitters of the chunk at {@code block} as last rebuilt (empty if none). */
    EmitterSet at(Block block) {
        Map<Long, EmitterSet> world = sets.get(block.getWorld().getUID());
        EmitterSet set = world == null ? null : world.get(Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4));
        return set == null ? EmitterSet.EMPTY : set;
    }

    /**
     * What a viewer at {@code (x, y, z)} that has the chunk of {@code block} would be sent over {@code cycles} cycles,
     * in emissions, computed with the same selection as the clock and without a player.
     */
    int simulate(Block block, double x, double y, double z, int cycles) {
        EmitterSet set = at(block);
        int total = 0;
        for (long c = 0; c < cycles; c++) {
            for (EmitterSet.Pick pick : EmitterSet.select(c, x, y, z, List.of(set), budgetPerCycle)) {
                total += pick.emissions();
            }
        }
        return total;
    }

    /** Player tasks the clock has scheduled since start. */
    long tasksScheduled() {
        return tasks.get();
    }

    /** Whether the clock runs. */
    synchronized boolean running() {
        return clock != null;
    }
}
