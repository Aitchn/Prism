package com.aitchn.prism.plugin;

import com.aitchn.prism.client.DisplayEntities;
import com.aitchn.prism.core.block.BlockLook;
import com.aitchn.prism.core.block.ChunkLooks;
import com.aitchn.prism.core.look.Dir;
import com.aitchn.prism.core.look.DisplayBudget;
import com.aitchn.prism.core.look.DisplayHeld;
import com.aitchn.prism.core.look.DisplayLighting;
import com.aitchn.prism.core.look.DisplayLook;
import com.aitchn.prism.core.look.DisplayParts;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * The display layer (ADR-0023, ADR-0025): draws the custom blocks whose look is not a borrowed state with item display
 * entities that exist only in the packets sent to each viewer. Nothing is spawned, ticked or saved on the server.
 *
 * <p>What a viewer gets:
 * <ul>
 *   <li>Per block, the entities {@link DisplayParts} computes: with {@link DisplayLighting#BLOCK} (the default) one
 *       entity at the block's centre, none if all six neighbours hide the block; with {@link DisplayLighting#FACES} one
 *       per visible face, in the neighbouring cell.
 *   <li>Only for chunks the client has, within {@code blocks.display.view-distance} chunks, and at most
 *       {@code blocks.display.max-per-player} entities, nearest first ({@link DisplayBudget}): whole chunks in order
 *       of distance, the boundary chunk by block distance, the farthest dropped. The plan is made again when the viewer
 *       crosses a chunk border, when a chunk arrives or leaves, and when what a chunk needs changes; at most once a
 *       tick per viewer, and never per block.
 *   <li>A chunk's entities are sent together, in bundles. A look change of a block sends one metadata packet.
 * </ul>
 *
 * <p>Per viewer the state is a {@link DisplayHeld} per chunk (primitive arrays, no object per block). A block is
 * re-evaluated, for every viewer, when it or one of its six neighbours changes. Occlusion of a neighbour is read from
 * the world only where the current thread owns it; elsewhere (a region boundary on Folia) the neighbour is taken as
 * not hiding anything, which draws something that stays behind the neighbour's own surface. Bedrock viewers get no
 * entities: they see the block's synthetic Bedrock block (ADR-0024).
 */
final class DisplayLayer implements Listener {

    private final PrismBlockService blocks;
    private final Supplier<ClientLooks> looks;
    private final PlatformScheduler scheduler;
    private final Predicate<UUID> bedrock;
    private final int capChunks;
    private final float viewRange;
    private final int budget;
    private final DisplayLighting lighting;
    private final int width;
    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();

    private static final class Viewer {
        final Player player;
        volatile UUID world;
        /** Chunks the client has (chunk key). */
        final Set<Long> sent = ConcurrentHashMap.newKeySet();
        /** Chunk key to the entities and the allowance of that chunk; only admitted chunks. Guarded by {@code this}. */
        final Map<Long, DisplayHeld> shown = new HashMap<>();
        /** Admitted chunks whose need changed beyond their allowance; guarded by {@code this}. */
        final Set<Long> dirty = new HashSet<>();
        /** Chunks with a full evaluation scheduled. */
        final Set<Long> pending = ConcurrentHashMap.newKeySet();
        final AtomicBoolean replanQueued = new AtomicBoolean();
        volatile int x;
        volatile int y;
        volatile int z;

        Viewer(Player player, UUID world) {
            this.player = player;
            this.world = world;
            Location at = player.getLocation();
            this.x = at.getBlockX();
            this.y = at.getBlockY();
            this.z = at.getBlockZ();
        }

        int chunkX() {
            return x >> 4;
        }

        int chunkZ() {
            return z >> 4;
        }
    }

    /**
     * @param capChunks the farthest chunk (Chebyshev distance from the viewer's chunk) that gets entities; 0 means every
     *                  chunk the client has
     * @param viewRange the entities' view range multiplier
     * @param budget    the most entities one viewer is sent
     * @param lighting  one entity per block, or one per face
     */
    DisplayLayer(PrismBlockService blocks, Supplier<ClientLooks> looks, PlatformScheduler scheduler,
            Predicate<UUID> bedrock, int capChunks, float viewRange, int budget, DisplayLighting lighting) {
        this.blocks = blocks;
        this.looks = looks;
        this.scheduler = scheduler;
        this.bedrock = bedrock;
        this.capChunks = capChunks;
        this.viewRange = viewRange;
        this.budget = budget;
        this.lighting = lighting;
        this.width = DisplayHeld.width(lighting);
    }

    DisplayLighting lighting() {
        return lighting;
    }

    int budget() {
        return budget;
    }

    /** How many display entities {@code player} has right now (for {@code /prism bench}). */
    int entityCount(Player player) {
        Viewer viewer = viewers.get(player.getUniqueId());
        if (viewer == null) {
            return 0;
        }
        synchronized (viewer) {
            int count = 0;
            for (DisplayHeld held : viewer.shown.values()) {
                count += held.entities();
            }
            return count;
        }
    }

    // ---------------------------------------------------------------- viewers

    private Viewer viewer(Player player) {
        UUID world = player.getWorld().getUID();
        Viewer viewer = viewers.computeIfAbsent(player.getUniqueId(), id -> new Viewer(player, world));
        if (!viewer.world.equals(world)) {
            // The client dropped its entities with the world: forget them without sending anything.
            synchronized (viewer) {
                viewer.shown.clear();
                viewer.dirty.clear();
            }
            viewer.sent.clear();
            viewer.world = world;
        }
        return viewer;
    }

    private boolean inRange(Viewer viewer, int chunkX, int chunkZ) {
        return capChunks <= 0
                || Math.max(Math.abs(chunkX - viewer.chunkX()), Math.abs(chunkZ - viewer.chunkZ())) <= capChunks;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        if (bedrock.test(player.getUniqueId())) {
            return;
        }
        Viewer viewer = viewer(player);
        Chunk chunk = event.getChunk();
        viewer.sent.add(Chunk.getChunkKey(chunk.getX(), chunk.getZ()));
        if (blocks.looks(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()).hasDisplays()) {
            requestReplan(viewer);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(PlayerChunkUnloadEvent event) {
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer == null) {
            return;
        }
        Chunk chunk = event.getChunk();
        long key = Chunk.getChunkKey(chunk.getX(), chunk.getZ());
        viewer.sent.remove(key);
        if (hideChunk(viewer, key)) {
            requestReplan(viewer); // budget came free for other chunks
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer == null) {
            return;
        }
        Location to = event.getTo();
        int oldChunkX = viewer.chunkX();
        int oldChunkZ = viewer.chunkZ();
        viewer.x = to.getBlockX();
        viewer.y = to.getBlockY();
        viewer.z = to.getBlockZ();
        if (viewer.chunkX() != oldChunkX || viewer.chunkZ() != oldChunkZ) {
            requestReplan(viewer);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Viewer viewer = viewers.remove(event.getPlayer().getUniqueId());
        if (viewer != null) {
            // A respawn in the same world may keep the client's level: take the entities down rather than leak them.
            DisplayEntities.Batch batch = new DisplayEntities.Batch();
            synchronized (viewer) {
                viewer.shown.values().forEach(held -> batch.destroy(held.allIds()));
                viewer.shown.clear();
            }
            batch.send(viewer.player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- what changes the picture

    /** A custom block was placed, removed or changed look; call on the block's thread. */
    void changed(Block block) {
        refreshAround(block);
    }

    /** A chunk's looks were recomputed (content changed): evaluate it again for everyone who has it; its thread. */
    void relooked(BlockIndex.Loaded mirror) {
        long key = Chunk.getChunkKey(mirror.chunkX, mirror.chunkZ);
        for (Viewer viewer : viewers.values()) {
            if (viewer.world.equals(mirror.world.getUID()) && viewer.sent.contains(key)) {
                synchronized (viewer) {
                    viewer.dirty.add(key);
                }
                requestReplan(viewer);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        refreshAround(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        refreshLater(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::refreshLater);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::refreshLater);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        for (Block moved : event.getBlocks()) {
            refreshLater(moved);
            refreshLater(moved.getRelative(event.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent event) {
        for (Block moved : event.getBlocks()) {
            refreshLater(moved);
            refreshLater(moved.getRelative(event.getDirection()));
        }
    }

    /** The world changes after break and explosion events, so look again one tick later. */
    private void refreshLater(Block block) {
        scheduler.runAtLater(block.getLocation(), () -> refreshAround(block), 1);
    }

    /** Re-evaluates the block at {@code block} and its six neighbours for every viewer. */
    private void refreshAround(Block block) {
        if (viewers.isEmpty()) {
            return;
        }
        World world = block.getWorld();
        for (int i = 0; i < 7; i++) {
            int[] step = i < 6 ? Dir.offset(i) : new int[] {0, 0, 0};
            int x = block.getX() + step[0];
            int y = block.getY() + step[1];
            int z = block.getZ() + step[2];
            long chunkKey = Chunk.getChunkKey(x >> 4, z >> 4);
            for (Viewer viewer : viewers.values()) {
                if (viewer.world.equals(world.getUID()) && viewer.sent.contains(chunkKey)
                        && inRange(viewer, x >> 4, z >> 4)) {
                    sync(viewer, world, x, y, z);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the plan: which chunks get how many

    /** Makes the plan again on the viewer's thread, at most once until it has run. */
    private void requestReplan(Viewer viewer) {
        if (viewer.replanQueued.compareAndSet(false, true)) {
            scheduler.runOn(viewer.player, () -> {
                viewer.replanQueued.set(false);
                replan(viewer);
            }, null);
        }
    }

    /**
     * Shares the budget among the chunks the viewer has: O(chunks log chunks), no block is looked at. Chunks that lose
     * their place are taken down now; chunks whose shown blocks change are evaluated on their own thread.
     */
    private void replan(Viewer viewer) {
        World world = Bukkit.getWorld(viewer.world);
        if (world == null || !viewers.containsKey(viewer.player.getUniqueId())) {
            return;
        }
        List<Long> candidates = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (long key : viewer.sent) {
            int chunkX = (int) key;
            int chunkZ = (int) (key >> 32);
            if (!inRange(viewer, chunkX, chunkZ)) {
                continue;
            }
            int count = blocks.looks(world.getUID(), chunkX, chunkZ).displayCount();
            if (count > 0) {
                candidates.add(key);
                counts.add(count);
            }
        }
        int n = candidates.size();
        long[] keys = new long[n];
        long[] distances = new long[n];
        int[] needs = new int[n];
        int viewerX = viewer.x;
        int viewerZ = viewer.z;
        List<Long> toSync = new ArrayList<>();
        DisplayEntities.Batch hidden = new DisplayEntities.Batch();
        synchronized (viewer) {
            for (int i = 0; i < n; i++) {
                keys[i] = candidates.get(i);
                distances[i] = DisplayBudget.chunkDistanceSq(viewerX, viewerZ, (int) keys[i], (int) (keys[i] >> 32));
                DisplayHeld held = viewer.shown.get(keys[i]);
                // A chunk not evaluated yet is assumed to need the most it could (an upper bound keeps the budget).
                needs[i] = held != null && held.need() >= 0 ? held.need() : counts.get(i) * lighting.maxPerBlock();
            }
            int[] allowance = DisplayBudget.allowances(budget, keys, distances, needs);
            Set<Long> admitted = new HashSet<>();
            for (int i = 0; i < n; i++) {
                long key = keys[i];
                int now = allowance[i];
                DisplayHeld held = viewer.shown.get(key);
                if (now == DisplayBudget.NONE) {
                    continue;
                }
                admitted.add(key);
                if (held == null) {
                    held = new DisplayHeld(width);
                    held.allowance(now);
                    viewer.shown.put(key, held);
                    toSync.add(key);
                    continue;
                }
                int before = held.allowance();
                held.allowance(now);
                int need = held.need();
                if (viewer.dirty.remove(key) || need < 0 || (before != now && need > Math.min(before, now))) {
                    toSync.add(key);
                }
            }
            for (var it = viewer.shown.entrySet().iterator(); it.hasNext();) {
                var entry = it.next();
                if (!admitted.contains(entry.getKey())) {
                    hidden.destroy(entry.getValue().allIds());
                    viewer.dirty.remove(entry.getKey());
                    it.remove();
                }
            }
        }
        hidden.send(viewer.player);
        for (long key : toSync) {
            scheduleEvaluation(viewer, world, (int) key, (int) (key >> 32));
        }
    }

    private void scheduleEvaluation(Viewer viewer, World world, int chunkX, int chunkZ) {
        long key = Chunk.getChunkKey(chunkX, chunkZ);
        if (!viewer.pending.add(key)) {
            return;
        }
        scheduler.runAt(new Location(world, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8), () -> {
            viewer.pending.remove(key);
            if (viewer.sent.contains(key) && viewer.world.equals(world.getUID())
                    && viewers.get(viewer.player.getUniqueId()) == viewer) {
                evaluate(viewer, world, chunkX, chunkZ);
            }
        });
    }

    /** Takes down everything the viewer has in a chunk; returns whether it had anything there. */
    private boolean hideChunk(Viewer viewer, long chunkKey) {
        int[] ids;
        synchronized (viewer) {
            viewer.dirty.remove(chunkKey);
            DisplayHeld held = viewer.shown.remove(chunkKey);
            if (held == null) {
                return false;
            }
            ids = held.allIds();
        }
        DisplayEntities.destroy(viewer.player, ids);
        return true;
    }

    // ---------------------------------------------------------------- one chunk, one block

    /** One chunk's display blocks and the entities each needs now. */
    private record Needs(int[] keys, List<List<DisplayLook.Part>> parts, int[] weights, int total) {}

    /** Computes what every display block of a chunk needs; on the chunk's thread. */
    private Needs needs(World world, int chunkX, int chunkZ) {
        ChunkLooks chunk = blocks.looks(world.getUID(), chunkX, chunkZ);
        ClientLooks current = looks.get();
        List<Integer> keys = new ArrayList<>();
        List<List<DisplayLook.Part>> parts = new ArrayList<>();
        chunk.forEachDisplay((localX, y, localZ, id) -> {
            DisplayLook display = current.displays().get(id);
            keys.add(DisplayHeld.key(localX, y, localZ));
            parts.add(display == null ? List.of()
                    : parts(current, world, display, (chunkX << 4) + localX, y, (chunkZ << 4) + localZ));
        });
        int[] keyArray = new int[keys.size()];
        int[] weights = new int[keys.size()];
        int total = 0;
        for (int i = 0; i < keyArray.length; i++) {
            keyArray[i] = keys.get(i);
            weights[i] = parts.get(i).size();
            total += weights[i];
        }
        return new Needs(keyArray, parts, weights, total);
    }

    /** Brings the viewer's entities of one chunk in line with its blocks, neighbours and allowance. */
    private void evaluate(Viewer viewer, World world, int chunkX, int chunkZ) {
        long chunkKey = Chunk.getChunkKey(chunkX, chunkZ);
        Needs needs = needs(world, chunkX, chunkZ);
        int viewerX = viewer.x;
        int viewerY = viewer.y;
        int viewerZ = viewer.z;
        DisplayEntities.Batch batch = new DisplayEntities.Batch();
        boolean replan;
        synchronized (viewer) {
            DisplayHeld held = viewer.shown.get(chunkKey);
            if (held == null) {
                return; // no longer admitted
            }
            int allowance = held.allowance();
            boolean[] chosen;
            if (needs.total() <= allowance) {
                chosen = new boolean[needs.keys().length];
                Arrays.fill(chosen, true);
            } else {
                long[] distances = new long[needs.keys().length];
                for (int i = 0; i < distances.length; i++) {
                    int key = needs.keys()[i];
                    distances[i] = DisplayBudget.blockDistanceSq(viewerX, viewerY, viewerZ,
                            (chunkX << 4) + DisplayHeld.keyX(key), DisplayHeld.keyY(key),
                            (chunkZ << 4) + DisplayHeld.keyZ(key));
                }
                chosen = DisplayBudget.nearest(allowance, distances, needs.weights());
            }
            int[] keep = new int[needs.keys().length];
            int kept = 0;
            for (int i = 0; i < chosen.length; i++) {
                int key = needs.keys()[i];
                if (chosen[i]) {
                    apply(held, key, needs.parts().get(i), batch, chunkX, chunkZ);
                    keep[kept++] = key;
                }
            }
            Arrays.sort(keep, 0, kept);
            for (int key : held.keys()) {
                if (Arrays.binarySearch(keep, 0, kept, key) < 0) {
                    batch.destroy(held.clearAll(key));
                }
            }
            replan = held.need() != needs.total();
            held.need(needs.total());
        }
        batch.send(viewer.player);
        if (replan) {
            requestReplan(viewer); // the plan used an estimate or an older count for this chunk
        }
    }

    /** Re-evaluates one block for one viewer after it or a neighbour changed. */
    private void sync(Viewer viewer, World world, int x, int y, int z) {
        long chunkKey = Chunk.getChunkKey(x >> 4, z >> 4);
        BlockLook look = blocks.looks(world.getUID(), x >> 4, z >> 4).at(x & 15, y, z & 15);
        ClientLooks current = looks.get();
        DisplayLook display = look == null ? null : current.displays().get(look.display());
        int key = DisplayHeld.key(x & 15, y, z & 15);
        boolean admitted;
        synchronized (viewer) {
            DisplayHeld held = viewer.shown.get(chunkKey);
            admitted = held != null;
            if (admitted && display == null && held.count(key) == 0) {
                return; // not a display block, and nothing shown there
            }
        }
        if (!admitted) {
            if (display != null) {
                requestReplan(viewer); // a new display block in a chunk without a place may change the plan
            }
            return;
        }
        List<DisplayLook.Part> parts = display == null ? List.of() : parts(current, world, display, x, y, z);
        DisplayEntities.Batch batch = new DisplayEntities.Batch();
        boolean replan = false;
        synchronized (viewer) {
            DisplayHeld held = viewer.shown.get(chunkKey);
            if (held == null) {
                return;
            }
            int need = held.need();
            if (need < 0) {
                return; // a full evaluation is on its way
            }
            int before = held.count(key);
            if (need > held.allowance()) {
                // The chunk is cut by the budget: which blocks it shows is decided by distance over the whole chunk.
                viewer.dirty.add(chunkKey);
                replan = true;
            } else {
                int changed = need - before + parts.size();
                held.need(changed);
                if (changed <= held.allowance()) {
                    apply(held, key, parts, batch, x >> 4, z >> 4);
                } else {
                    viewer.dirty.add(chunkKey);
                }
                replan = changed != need;
            }
        }
        batch.send(viewer.player);
        if (replan) {
            requestReplan(viewer);
        }
    }

    /** Makes the entities of block {@code key} the ones {@code parts} describe; under the viewer's lock. */
    private void apply(DisplayHeld held, int key, List<DisplayLook.Part> parts, DisplayEntities.Batch batch,
            int chunkX, int chunkZ) {
        int x = (chunkX << 4) + DisplayHeld.keyX(key);
        int y = DisplayHeld.keyY(key);
        int z = (chunkZ << 4) + DisplayHeld.keyZ(key);
        boolean[] wanted = new boolean[width];
        for (DisplayLook.Part part : parts) {
            int slot = DisplayHeld.slotOf(part.slot(), width);
            int cell = DisplayHeld.cell(part.dx(), part.dy(), part.dz());
            int hash = hash(part);
            int id = held.id(key, slot);
            wanted[slot] = true;
            if (id != 0 && held.cellOf(key, slot) == cell) {
                if (held.hashOf(key, slot) != hash) {
                    batch.update(id, part.model(), part.translation(), part.rotation());
                    held.set(key, slot, id, cell, hash);
                }
                continue;
            }
            if (id != 0) {
                batch.destroy(id);
            }
            int created = DisplayEntities.nextId();
            batch.spawn(created, x + 0.5 + part.dx(), y + 0.5 + part.dy(), z + 0.5 + part.dz(), part.model(),
                    part.translation(), part.rotation(), viewRange);
            held.set(key, slot, created, cell, hash);
        }
        for (int slot = 0; slot < width; slot++) {
            if (!wanted[slot]) {
                int old = held.clear(key, slot);
                if (old != 0) {
                    batch.destroy(old);
                }
            }
        }
    }

    private static int hash(DisplayLook.Part part) {
        return 31 * (31 * part.model().hashCode() + Arrays.hashCode(part.translation()))
                + Arrays.hashCode(part.rotation());
    }

    private List<DisplayLook.Part> parts(ClientLooks current, World world, DisplayLook display, int x, int y, int z) {
        boolean[] occluded = new boolean[6];
        int[] light = new int[6];
        Arrays.fill(light, 15);
        for (int dir = 0; dir < 6; dir++) {
            int[] step = Dir.offset(dir);
            int nx = x + step[0];
            int ny = y + step[1];
            int nz = z + step[2];
            BlockLook neighbour = blocks.looks(world.getUID(), nx >> 4, nz >> 4).at(nx & 15, ny, nz & 15);
            if (neighbour != null) {
                occluded[dir] = current.occludes(neighbour);
                continue;
            }
            Location at = new Location(world, nx, ny, nz);
            if (ny >= world.getMinHeight() && ny < world.getMaxHeight() && scheduler.isOwnedByCurrentThread(at)
                    && world.isChunkLoaded(nx >> 4, nz >> 4)) {
                Block block = world.getBlockAt(nx, ny, nz);
                occluded[dir] = block.getType().isOccluding();
                if (lighting == DisplayLighting.FACES) {
                    light[dir] = block.getLightLevel();
                }
            }
        }
        return DisplayParts.compute(display, occluded, light, lighting);
    }

    // ---------------------------------------------------------------- self test

    /**
     * What a viewer standing at {@code (x, y, z)} with {@code budget} would be sent from the given chunks once the plan
     * has settled, computed with the same code the layer uses, without a player and without sending anything. Call on
     * the thread that owns the chunks.
     *
     * @return per chunk, the block keys, the entities each block needs, and whether it is sent
     */
    Simulation simulate(World world, int x, int y, int z, int budget, int[] chunkX, int[] chunkZ) {
        int[][] keys = new int[chunkX.length][];
        int[][] weights = new int[chunkX.length][];
        for (int c = 0; c < chunkX.length; c++) {
            Needs needs = needs(world, chunkX[c], chunkZ[c]);
            keys[c] = needs.keys();
            weights[c] = needs.weights();
        }
        return new Simulation(keys, weights, DisplayBudget.select(budget, x, y, z, chunkX, chunkZ, keys, weights));
    }

    record Simulation(int[][] keys, int[][] weights, boolean[][] sent) {
        int entities() {
            int total = 0;
            for (int c = 0; c < keys.length; c++) {
                for (int b = 0; b < keys[c].length; b++) {
                    total += sent[c][b] ? weights[c][b] : 0;
                }
            }
            return total;
        }
    }
}
