package com.aitchn.prism.plugin;

import com.aitchn.prism.client.DisplayEntities;
import com.aitchn.prism.core.block.BlockLook;
import com.aitchn.prism.core.look.Dir;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
 * The display layer (ADR-0023): draws the custom blocks whose look is not a borrowed state with item display entities
 * that exist only in the packets sent to each viewer. The server holds the invisible shell of the block; each viewer
 * that has the chunk (and is within the view-distance cap) gets, per block, the face entities {@link DisplayParts}
 * computes: one per face whose neighbour does not hide it, placed in that neighbour's cell so the client lights it from
 * there. A block is re-evaluated, for every viewer, when it or one of its six neighbours changes.
 *
 * <p>Occlusion of a neighbour is read from the world only where the current thread owns it; elsewhere (a region
 * boundary on Folia) the neighbour is taken as not hiding the face, which draws a face that stays behind the neighbour's
 * own surface and costs nothing visible. Bedrock viewers get no entities.
 */
final class DisplayLayer implements Listener {

    private final PrismBlockService blocks;
    private final Supplier<ClientLooks> looks;
    private final PlatformScheduler scheduler;
    private final Predicate<UUID> bedrock;
    private final int capChunks;
    private final float viewRange;
    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();

    /** An entity of one block as one viewer has it. */
    private record Held(DisplayLook.Part part, int entityId) {}

    private static final class Viewer {
        final Player player;
        volatile UUID world;
        /** Chunks the client has (chunk key). */
        final Set<Long> sent = ConcurrentHashMap.newKeySet();
        /** Chunk key to block key to the entities of that block by slot; guarded by {@code this}. */
        final Map<Long, Map<Long, Held[]>> shown = new HashMap<>();
        volatile int centerX;
        volatile int centerZ;

        Viewer(Player player, UUID world) {
            this.player = player;
            this.world = world;
            this.centerX = player.getLocation().getBlockX() >> 4;
            this.centerZ = player.getLocation().getBlockZ() >> 4;
        }
    }

    /**
     * @param capChunks the farthest chunk (Chebyshev distance from the viewer's chunk) that gets entities; 0 means every
     *                  chunk the client has
     * @param viewRange the entities' view range multiplier
     */
    DisplayLayer(PrismBlockService blocks, Supplier<ClientLooks> looks, PlatformScheduler scheduler,
            Predicate<UUID> bedrock, int capChunks, float viewRange) {
        this.blocks = blocks;
        this.looks = looks;
        this.scheduler = scheduler;
        this.bedrock = bedrock;
        this.capChunks = capChunks;
        this.viewRange = viewRange;
    }

    // ---------------------------------------------------------------- viewers

    private Viewer viewer(Player player) {
        UUID world = player.getWorld().getUID();
        Viewer viewer = viewers.computeIfAbsent(player.getUniqueId(), id -> new Viewer(player, world));
        if (!viewer.world.equals(world)) {
            // The client dropped its entities with the world: forget them without sending anything.
            synchronized (viewer) {
                viewer.shown.clear();
            }
            viewer.sent.clear();
            viewer.world = world;
        }
        return viewer;
    }

    private boolean inRange(Viewer viewer, int chunkX, int chunkZ) {
        return capChunks <= 0
                || Math.max(Math.abs(chunkX - viewer.centerX), Math.abs(chunkZ - viewer.centerZ)) <= capChunks;
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
        if (inRange(viewer, chunk.getX(), chunk.getZ())) {
            showChunk(viewer, chunk.getWorld(), chunk.getX(), chunk.getZ());
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
        hideChunk(viewer, key);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer == null || capChunks <= 0) {
            return;
        }
        int chunkX = to.getBlockX() >> 4;
        int chunkZ = to.getBlockZ() >> 4;
        if (chunkX == viewer.centerX && chunkZ == viewer.centerZ) {
            return;
        }
        viewer.centerX = chunkX;
        viewer.centerZ = chunkZ;
        World world = event.getPlayer().getWorld();
        for (long key : List.copyOf(viewer.sent)) {
            int cx = (int) key;
            int cz = (int) (key >> 32);
            boolean shown;
            synchronized (viewer) {
                shown = viewer.shown.containsKey(key);
            }
            if (inRange(viewer, cx, cz)) {
                if (!shown) {
                    showChunk(viewer, world, cx, cz);
                }
            } else if (shown) {
                hideChunk(viewer, key);
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
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

    /** A chunk's looks were recomputed (content changed): redraw it for everyone who has it; call on its thread. */
    void relooked(BlockIndex.Loaded mirror) {
        long key = Chunk.getChunkKey(mirror.chunkX, mirror.chunkZ);
        for (Viewer viewer : viewers.values()) {
            if (viewer.world.equals(mirror.world.getUID()) && viewer.sent.contains(key)
                    && inRange(viewer, mirror.chunkX, mirror.chunkZ)) {
                resync(viewer, mirror.world, mirror.chunkX, mirror.chunkZ);
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

    // ---------------------------------------------------------------- showing and hiding

    private void showChunk(Viewer viewer, World world, int chunkX, int chunkZ) {
        if (!blocks.looks(world.getUID(), chunkX, chunkZ).hasDisplays()) {
            return;
        }
        scheduler.runAt(new Location(world, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8), () -> {
            if (viewer.sent.contains(Chunk.getChunkKey(chunkX, chunkZ)) && inRange(viewer, chunkX, chunkZ)
                    && viewer.world.equals(world.getUID())) {
                resync(viewer, world, chunkX, chunkZ);
            }
        });
    }

    /** Brings the viewer's entities of one chunk in line with the looks and neighbours as they are now. */
    private void resync(Viewer viewer, World world, int chunkX, int chunkZ) {
        Set<Long> positions = new HashSet<>();
        blocks.looks(world.getUID(), chunkX, chunkZ).forEachDisplay((x, y, z, id) ->
                positions.add(pack((chunkX << 4) + x, y, (chunkZ << 4) + z)));
        synchronized (viewer) {
            Map<Long, Held[]> held = viewer.shown.get(Chunk.getChunkKey(chunkX, chunkZ));
            if (held != null) {
                positions.addAll(held.keySet());
            }
        }
        for (long position : positions) {
            sync(viewer, world, x(position), y(position), z(position));
        }
    }

    private void hideChunk(Viewer viewer, long chunkKey) {
        int[] ids;
        synchronized (viewer) {
            Map<Long, Held[]> held = viewer.shown.remove(chunkKey);
            if (held == null) {
                return;
            }
            ids = held.values().stream().flatMap(Arrays::stream).filter(Objects::nonNull)
                    .mapToInt(Held::entityId).toArray();
        }
        DisplayEntities.destroy(viewer.player, ids);
    }

    /** Makes the viewer's entities of the block at (x, y, z) the ones its look and neighbours call for. */
    private void sync(Viewer viewer, World world, int x, int y, int z) {
        long chunkKey = Chunk.getChunkKey(x >> 4, z >> 4);
        long position = pack(x, y, z);
        BlockLook look = blocks.looks(world.getUID(), x >> 4, z >> 4).at(x & 15, y, z & 15);
        ClientLooks current = looks.get();
        DisplayLook display = look == null ? null : current.displays().get(look.display());
        boolean holding;
        synchronized (viewer) {
            Map<Long, Held[]> chunk = viewer.shown.get(chunkKey);
            holding = chunk != null && chunk.containsKey(position);
        }
        if (display == null && !holding) {
            return;
        }
        List<DisplayLook.Part> parts = display == null ? List.of() : parts(current, world, display, x, y, z);
        List<Integer> destroyed = new ArrayList<>();
        synchronized (viewer) {
            Map<Long, Held[]> chunk = viewer.shown.computeIfAbsent(chunkKey, k -> new HashMap<>());
            Held[] old = chunk.get(position);
            Held[] next = new Held[7];
            for (DisplayLook.Part part : parts) {
                Held kept = old == null ? null : old[part.slot()];
                if (kept != null && same(kept.part(), part)) {
                    next[part.slot()] = kept;
                    continue;
                }
                if (kept != null) {
                    destroyed.add(kept.entityId());
                }
                int id = DisplayEntities.nextId();
                DisplayEntities.spawn(viewer.player, id, x + 0.5 + part.dx(), y + 0.5 + part.dy(), z + 0.5 + part.dz(),
                        part.model(), part.translation(), part.rotation(), viewRange);
                next[part.slot()] = new Held(part, id);
            }
            if (old != null) {
                for (int slot = 0; slot < 7; slot++) {
                    if (old[slot] != null && next[slot] == null) {
                        destroyed.add(old[slot].entityId());
                    }
                }
            }
            if (Arrays.stream(next).anyMatch(Objects::nonNull)) {
                chunk.put(position, next);
            } else {
                chunk.remove(position);
                if (chunk.isEmpty()) {
                    viewer.shown.remove(chunkKey);
                }
            }
        }
        DisplayEntities.destroy(viewer.player, destroyed.stream().mapToInt(Integer::intValue).toArray());
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
                light[dir] = block.getLightLevel();
            }
        }
        return DisplayParts.compute(display, occluded, light);
    }

    private static boolean same(DisplayLook.Part a, DisplayLook.Part b) {
        return a.model().equals(b.model()) && a.dx() == b.dx() && a.dy() == b.dy() && a.dz() == b.dz()
                && Arrays.equals(a.translation(), b.translation()) && Arrays.equals(a.rotation(), b.rotation());
    }

    // World block key: 26 bits x, 26 bits z, 12 bits y (offset so negative heights fit).

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFF);
    }

    private static int x(long packed) {
        return (int) (packed >> 38);
    }

    private static int z(long packed) {
        return (int) ((packed << 26) >> 38);
    }

    private static int y(long packed) {
        return (int) (packed & 0xFFF) - 2048;
    }
}
