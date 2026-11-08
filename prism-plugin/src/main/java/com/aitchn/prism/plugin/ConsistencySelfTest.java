package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.client.ClientStates;
import com.aitchn.prism.core.block.BlockLook;
import com.aitchn.prism.core.block.ChunkLooks;
import com.aitchn.prism.core.block.SectionStates;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Piston;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Optional startup check of the block carrier consistency, enabled with
 * {@code -Dprism.selftest.consistency=<solid block>,<passable block>,<block with an "active" variant>}; the smoke test
 * passes {@code examplemod:ruby_block,examplemod:ruby_flower,examplemod:ruby_counter}.
 *
 * <p>No client joins the smoke test server, so this stands in for one: after every disturbance it asserts that the
 * index, the carrier in the world and the state the packet rewriter would send (what {@link PrismBlockService#lookOf}
 * says, rewritten through the same {@link ChunkLooks} code the network threads use) still agree. Disturbances: vanilla
 * blocks (stone, a real note block, a redstone block) placed and removed on all six sides, a piston pushing at a
 * block, an explosion, a variant change, other code replacing the carrier with {@code setBlockData(..., false)} and
 * {@code setType}, a report from the packet layer, carriers of earlier versions (note block, tripwire) and carriers
 * replaced while nobody looked (the index is read again as a chunk load does), a real chunk unload and reload, and
 * hundreds of blocks in one chunk. Runs far from spawn, in the air, on the owning thread.
 */
final class ConsistencySelfTest implements Listener {

    private static final BlockFace[] SIX = PrismBlockService.SIDES;

    private final Plugin prism;
    private final PrismKey solid;
    private final PrismKey passable;
    private final PrismKey variant;
    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;
    private final Logger log;
    private final List<String> failed = new ArrayList<>();
    private int checks;
    private World world;
    private int chunkX;
    private int chunkZ;
    private Location center;

    ConsistencySelfTest(Plugin prism, String spec, PrismBlockService blocks, PlatformScheduler scheduler, Logger log) {
        String[] parts = spec.split(",");
        this.prism = prism;
        this.solid = PrismKey.parse(parts[0]);
        this.passable = PrismKey.parse(parts.length > 1 ? parts[1] : parts[0]);
        this.variant = PrismKey.parse(parts.length > 2 ? parts[2] : parts[0]);
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            log.severe("PRISM_CONSISTENCY_SELFTEST_FAIL no world");
            return;
        }
        chunkX = (world.getSpawnLocation().getBlockX() >> 4) + 150;
        chunkZ = world.getSpawnLocation().getBlockZ() >> 4;
        center = new Location(world, (chunkX << 4) + 8, 100, (chunkZ << 4) + 8);
        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, error) -> {
            if (error != null || chunk == null) {
                log.severe("PRISM_CONSISTENCY_SELFTEST_FAIL " + (error == null ? "no chunk" : error.toString()));
                return;
            }
            scheduler.runAt(center, () -> {
                world.addPluginChunkTicket(chunkX, chunkZ, prism);
                scheduler.runAtLater(center, this::immediate, 40);
            });
        });
    }

    private Block at(int dx, int dy, int dz) {
        return world.getBlockAt(center.getBlockX() + dx, center.getBlockY() + dy, center.getBlockZ() + dz);
    }

    private void check(String name, String problem) {
        checks++;
        if (problem != null) {
            failed.add(name + ": " + problem);
        }
    }

    /**
     * Everything that must hold for a custom block: indexed, its carrier in the world, a published look equal to the
     * one recomputed from the index, and the packet rewrite of the carrier equal to that look. Null when it holds.
     */
    private String problem(Block block, PrismKey key, Carrier carrier) {
        if (!blocks.indexed(block)) {
            return "not indexed";
        }
        if (blocks.identify(block).filter(key::equals).isEmpty()) {
            return "identify says " + blocks.identify(block);
        }
        if (block.getType() != carrier.material()) {
            return "world holds " + block.getType();
        }
        BlockLook look = blocks.lookOf(block);
        if (look == null) {
            return "no look published";
        }
        int expected = blocks.expectedLookId(block);
        int carrierId = ClientStates.id(block.getBlockData());
        if (look.look() != expected || look.carrier() != carrierId) {
            return "published " + look + " but expected look=" + expected + " carrier=" + carrierId;
        }
        boolean[] foreign = {false};
        int sent = blocks.looks(world.getUID(), block.getX() >> 4, block.getZ() >> 4).rewrite(block.getX() & 15,
                block.getY(), block.getZ() & 15, carrierId, ClientStates::isLegacyCarrier, (x, y, z) -> foreign[0] = true);
        return sent != expected || foreign[0] ? "rewrite sends " + sent + ", expected " + expected : null;
    }

    /** Whether the position is plainly not a custom block any more: no entry, no look; the world is left as it is. */
    private String gone(Block block, Material expectedWorld) {
        if (blocks.indexed(block)) {
            return "still indexed";
        }
        if (blocks.lookOf(block) != null) {
            return "look still published";
        }
        return block.getType() == expectedWorld ? null : "world holds " + block.getType() + ", expected " + expectedWorld;
    }

    private boolean place(PrismKey key, Block block) {
        return blocks.place(key, block.getLocation());
    }

    // ---------------------------------------------------------------- phase 1: everything that needs no waiting

    private void immediate() {
        try {
            neighbours();
            variantChange();
            externalChanges();
            explosion();
            manyBlocks();
            startPiston();
        } catch (RuntimeException e) {
            failed.add("exception in phase 1: " + e);
        }
        scheduler.runAtLater(center, this::afterTicks, 20);
    }

    /** Vanilla blocks placed and removed on every side, as a player's or a plugin's placements would. */
    private void neighbours() {
        Block s = at(0, 0, 0);
        Block p = at(4, 0, 0);
        check("place solid", place(solid, s) ? null : "place failed");
        check("place passable", place(passable, p) ? null : "place failed");
        Material[] vanilla = {Material.STONE, Material.NOTE_BLOCK, Material.REDSTONE_BLOCK, Material.OAK_FENCE};
        for (BlockFace face : SIX) {
            for (Material material : vanilla) {
                for (Block target : List.of(s, p)) {
                    Block neighbor = target.getRelative(face);
                    neighbor.setType(material, true);
                    blocks.notifyNeighbors(neighbor);
                    String name = "neighbour " + material + " " + face + " of " + (target == s ? "solid" : "passable");
                    check(name, target == s ? problem(s, solid, Carrier.SOLID) : problem(p, passable, Carrier.PASSABLE));
                    neighbor.setType(Material.AIR, true);
                    blocks.notifyNeighbors(neighbor);
                    check(name + " removed",
                            target == s ? problem(s, solid, Carrier.SOLID) : problem(p, passable, Carrier.PASSABLE));
                }
            }
        }
        // A block that was never placed by Prism but is the carrier material is nobody's look.
        Block plain = at(8, 0, 0);
        plain.setType(Carrier.SOLID.material(), false);
        check("unindexed carrier material", gone(plain, Carrier.SOLID.material()));
        plain.setType(Material.AIR, false);
        blocks.remove(s, false);
        blocks.remove(p, false);
        check("removed solid", gone(s, Material.AIR));
        check("removed passable", gone(p, Material.AIR));
    }

    private void variantChange() {
        Block v = at(0, 0, 3);
        if (!place(variant, v)) {
            check("place variant block", "place failed");
            return;
        }
        BlockLook shown = blocks.lookOf(v);
        int before = shown == null ? -1 : shown.look();
        int beforeDisplay = shown == null ? -1 : shown.display();
        boolean changed = blocks.setVariant(v, "active");
        check("variant change", !changed ? "setVariant refused" : problem(v, variant, Carrier.SOLID));
        BlockLook after = blocks.lookOf(v);
        check("variant look differs", after == null || after.look() == before && after.display() == beforeDisplay
                ? "look did not change" : null);
        blocks.remove(v, false);
    }

    /** Other code replaces or rewrites the carrier behind Prism's back: the world wins, the index follows. */
    private void externalChanges() {
        Block same = at(0, 0, 6);
        place(solid, same);
        same.setBlockData(same.getBlockData(), false);
        same.setType(Carrier.SOLID.material(), false);
        check("same carrier written again", problem(same, solid, Carrier.SOLID));
        blocks.remove(same, false);

        Block replaced = at(2, 0, 6);
        place(solid, replaced);
        replaced.setBlockData(Material.STONE.createBlockData(), false);
        check("setBlockData stone, lazy check", blocks.identify(replaced).isEmpty() ? null : "still identified");
        check("setBlockData stone, entry dropped", gone(replaced, Material.STONE));
        replaced.setType(Material.AIR, false);

        Block typed = at(4, 0, 6);
        place(passable, typed);
        typed.setType(Material.GLASS, false);
        check("setType glass, lazy check", blocks.get(typed).isEmpty() ? null : "still a custom block");
        check("setType glass, entry dropped", gone(typed, Material.GLASS));
        typed.setType(Material.AIR, false);

        // The packet layer's report (from a network thread) is checked on the region thread a little later.
        Block reported = at(6, 0, 6);
        place(solid, reported);
        reported.setType(Material.STONE, false);
        blocks.foreignChange(world.getUID(), reported.getX(), reported.getY(), reported.getZ());
        pendingReported = reported;
    }

    private Block pendingReported;

    private void explosion() {
        Block s = at(1, 0, -6);
        Block p = at(-1, 0, -6);
        place(solid, s);
        place(passable, p);
        world.createExplosion(at(0, 0, -6).getLocation().add(0.5, 0.5, 0.5), 4f, false, true);
        check("explosion solid", gone(s, Material.AIR));
        check("explosion passable", gone(p, Material.AIR));
    }

    /** Hundreds of blocks in one chunk: every one consistent, and a simulated chunk packet rewrites exactly them. */
    private void manyBlocks() {
        int placed = 0;
        List<Block> all = new ArrayList<>();
        for (int x = 1; x <= 14; x++) {
            for (int z = 1; z <= 14; z++) {
                for (int layer = 0; layer < 2; layer++) {
                    Block block = world.getBlockAt((chunkX << 4) + x, 120 + layer * 10, (chunkZ << 4) + z);
                    if (place(layer == 0 ? solid : passable, block)) {
                        placed++;
                        all.add(block);
                    }
                }
            }
        }
        check("many placed", placed == 14 * 14 * 2 ? null : "placed " + placed);
        String worst = null;
        for (Block block : all) {
            boolean isSolid = block.getY() == 120;
            String problem = problem(block, isSolid ? solid : passable, isSolid ? Carrier.SOLID : Carrier.PASSABLE);
            if (problem != null) {
                worst = block.getX() + "," + block.getY() + "," + block.getZ() + " " + problem;
                break;
            }
        }
        check("many consistent", worst);
        check("many snapshot size", chunkSnapshotSize() >= placed ? null : "snapshot " + chunkSnapshotSize());
        check("many chunk packet", simulatedChunkPacket(all));
        int removed = 0;
        for (int i = 0; i < all.size(); i += 2) {
            if (blocks.remove(all.get(i), false)) {
                removed++;
            }
        }
        String after = null;
        for (int i = 1; i < all.size() && after == null; i += 2) {
            Block block = all.get(i);
            boolean isSolid = block.getY() == 120;
            after = problem(block, isSolid ? solid : passable, isSolid ? Carrier.SOLID : Carrier.PASSABLE);
        }
        check("many after removing half", after);
        check("many removed gone", all.size() / 2 == removed ? null : "removed " + removed);
        for (int i = 0; i < all.size(); i += 2) {
            if (blocks.lookOf(all.get(i)) != null) {
                check("many removed look cleared", "look of a removed block still published");
                break;
            }
        }
        for (int i = 1; i < all.size(); i += 2) {
            blocks.remove(all.get(i), false);
        }
    }

    private int chunkSnapshotSize() {
        return blocks.looks(world.getUID(), chunkX, chunkZ).size();
    }

    /**
     * Builds the sections of the arena chunk from the real world, as the server would encode them, rewrites them with
     * {@link ChunkLooks#rewriteSection} like the packet listener does, and compares position by position.
     */
    private String simulatedChunkPacket(List<Block> custom) {
        ChunkLooks looks = blocks.looks(world.getUID(), chunkX, chunkZ);
        java.util.Set<Long> expected = new java.util.HashSet<>();
        for (Block block : custom) {
            expected.add(((long) block.getY() << 8) | ((block.getX() & 15) << 4) | (block.getZ() & 15));
        }
        int checked = 0;
        for (int sectionY : new int[] {Math.floorDiv(120, 16), Math.floorDiv(130, 16)}) {
            int[] ids = new int[4096];
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        ids[(y << 8) | (z << 4) | x] = ClientStates.id(
                                world.getBlockAt((chunkX << 4) + x, sectionY * 16 + y, (chunkZ << 4) + z).getBlockData());
                    }
                }
            }
            int[] untouched = ids.clone();
            SectionStates states = new SectionStates() {
                @Override
                public int get(int x, int y, int z) {
                    return ids[(y << 8) | (z << 4) | x];
                }

                @Override
                public void set(int x, int y, int z, int state) {
                    ids[(y << 8) | (z << 4) | x] = state;
                }
            };
            int[] foreign = {0};
            looks.rewriteSection(sectionY, states, ClientStates::isLegacyCarrier, (x, y, z) -> foreign[0]++);
            if (foreign[0] != 0) {
                return foreign[0] + " foreign positions in section " + sectionY;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int i = (y << 8) | (z << 4) | x;
                        boolean isCustom = expected.contains(((long) (sectionY * 16 + y) << 8) | (x << 4) | z);
                        Block block = world.getBlockAt((chunkX << 4) + x, sectionY * 16 + y, (chunkZ << 4) + z);
                        if (isCustom ? ids[i] != blocks.expectedLookId(block) : ids[i] != untouched[i]) {
                            return "section " + sectionY + " position " + x + "," + y + "," + z + " is " + ids[i];
                        }
                        checked++;
                    }
                }
            }
        }
        return checked == 2 * 4096 ? null : "checked " + checked;
    }

    private Block pistonTarget;

    /** A piston extending at a custom block: Prism holds custom blocks still, the block must stay put and indexed. */
    private void startPiston() {
        pistonTarget = at(0, 0, 12);
        Block base = at(-1, 0, 12);
        place(solid, pistonTarget);
        Piston piston = (Piston) Material.PISTON.createBlockData();
        piston.setFacing(BlockFace.EAST);
        base.setBlockData(piston, true);
        at(-2, 0, 12).setType(Material.REDSTONE_BLOCK, true); // powers the piston, which tries to push the block
    }

    // ---------------------------------------------------------------- phase 2: after a few ticks

    private void afterTicks() {
        try {
            check("piston next to the block", problem(pistonTarget, solid, Carrier.SOLID));
            check("packet layer report removed the block", gone(pendingReported, Material.STONE));
            blocks.remove(pistonTarget, false);
            at(-1, 0, 12).setType(Material.AIR, false);
            at(-2, 0, 12).setType(Material.AIR, false);
        } catch (RuntimeException e) {
            failed.add("exception in phase 2: " + e);
        }
        reloadIndex();
    }

    // ---------------------------------------------------------------- phase 3: chunk loads

    private Block[] reloaded;

    /**
     * Reads the chunk's index again as a chunk load does, with blocks in the states an earlier version or an editor
     * could have left them in: old carriers (migrated), a replaced carrier (dropped, the world wins), and plain ones.
     */
    private void reloadIndex() {
        try {
            reloaded = new Block[8];
            for (int i = 0; i < reloaded.length; i++) {
                reloaded[i] = at(i, 3, 0);
                place(i % 2 == 0 ? solid : passable, reloaded[i]);
            }
            blocks.forgetCarrier(reloaded[0]);
            reloaded[0].setType(Material.NOTE_BLOCK, false); // an earlier version's solid block
            blocks.forgetCarrier(reloaded[1]);
            reloaded[1].setType(Material.TRIPWIRE, false); // an earlier version's passable block
            reloaded[2].setType(Material.STONE, false); // replaced while nobody looked
            reloaded[3].setType(Material.AIR, false); // removed while nobody looked
            blocks.reloadIndex(reloaded[0].getChunk());
            check("reload migrates note block", problem(reloaded[0], solid, Carrier.SOLID));
            check("reload migrates tripwire", problem(reloaded[1], passable, Carrier.PASSABLE));
            check("reload drops replaced carrier", gone(reloaded[2], Material.STONE));
            check("reload drops removed carrier", gone(reloaded[3], Material.AIR));
            check("reload keeps plain carriers", problem(reloaded[4], solid, Carrier.SOLID));
            check("reload keeps plain passable", problem(reloaded[5], passable, Carrier.PASSABLE));
            check("reload snapshot", blocks.looks(world.getUID(), chunkX, chunkZ).size() >= 4 ? null : "snapshot too small");
        } catch (RuntimeException e) {
            failed.add("exception in reload: " + e);
        }
        unloadChunk();
    }

    private boolean unloaded;

    /** A real unload and load of the chunk: the persisted index must come back with its looks. */
    private void unloadChunk() {
        world.removePluginChunkTicket(chunkX, chunkZ, prism);
        boolean accepted = world.unloadChunk(chunkX, chunkZ, true);
        scheduler.runAtLater(center, () -> {
            unloaded = accepted && !world.isChunkLoaded(chunkX, chunkZ);
            world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, error) ->
                    scheduler.runAt(center, () -> {
                        world.addPluginChunkTicket(chunkX, chunkZ, prism);
                        scheduler.runAtLater(center, this::finish, 20);
                    }));
        }, 40);
    }

    private void finish() {
        try {
            if (unloaded) {
                check("unload reload keeps plain carriers", problem(reloaded[4], solid, Carrier.SOLID));
                check("unload reload keeps plain passable", problem(reloaded[5], passable, Carrier.PASSABLE));
                check("unload reload keeps migrated", problem(reloaded[0], solid, Carrier.SOLID));
            }
            for (Block block : reloaded) {
                blocks.remove(block, false);
            }
            world.removePluginChunkTicket(chunkX, chunkZ, prism);
            boolean ok = failed.isEmpty();
            log.info((ok ? "PRISM_CONSISTENCY_SELFTEST_OK " : "PRISM_CONSISTENCY_SELFTEST_FAIL ") + solid + "," + passable
                    + " checks=" + checks + " failed=" + failed.size() + " realUnload=" + unloaded
                    + (ok ? "" : " " + failed.subList(0, Math.min(failed.size(), 8))));
        } catch (RuntimeException e) {
            log.severe("PRISM_CONSISTENCY_SELFTEST_FAIL " + e);
        }
    }
}
