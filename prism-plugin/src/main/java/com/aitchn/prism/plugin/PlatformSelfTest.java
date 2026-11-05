package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.block.PrismCapabilities;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Optional startup check of the block platform, enabled with {@code -Dprism.selftest.platform=<block key>}; the smoke
 * test passes {@code examplemod:ruby_counter}. It proves on a real server that placement runs place behaviors and
 * keeps the facing, per-block data is written, ticking runs, neighbours are told, capabilities resolve for custom and
 * vanilla blocks, and removal deletes the data. The block's mod must count ticks and neighbour changes in
 * {@code examplemod:ticks} / {@code examplemod:neighbors} and offer {@code examplemod:count}.
 */
final class PlatformSelfTest implements Listener {

    private final PrismKey key;
    private final PrismKey neighbor;
    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;
    private final Logger log;

    PlatformSelfTest(PrismKey key, PrismKey neighbor, PrismBlockService blocks, PlatformScheduler scheduler, Logger log) {
        this.key = key;
        this.neighbor = neighbor;
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (Bukkit.getWorlds().isEmpty()) {
            log.severe("PRISM_PLATFORM_SELFTEST_FAIL no world");
            return;
        }
        Location at = Bukkit.getWorlds().get(0).getSpawnLocation().add(3, 0, 0);
        scheduler.runAt(at, () -> start(at.getBlock()));
    }

    private void start(Block block) {
        try {
            NamespacedKey placedKey = NamespacedKey.fromString("examplemod:placed");
            NamespacedKey neighborsKey = NamespacedKey.fromString("examplemod:neighbors");
            boolean placed = blocks.place(key, block.getLocation(), null, BlockFace.EAST);
            Optional<CustomBlock> custom = blocks.get(block);
            boolean facing = custom.map(c -> c.facing() == BlockFace.EAST).orElse(false);
            boolean placeRan = custom.map(c -> c.data().has(placedKey, PersistentDataType.LONG)).orElse(false);

            Block chest = block.getRelative(BlockFace.NORTH);
            chest.setType(Material.CHEST, false);
            boolean vanillaCapability = blocks.capability(chest, BlockFace.SOUTH, PrismCapabilities.INVENTORY).isPresent();

            Block side = block.getRelative(BlockFace.WEST);
            boolean neighborPlaced = blocks.place(neighbor, side.getLocation());
            int neighbors = custom.map(c -> c.data().getOrDefault(neighborsKey, PersistentDataType.INTEGER, 0)).orElse(0);
            boolean listed = blocks.blocksIn(block.getChunk()).size() >= (side.getChunk().equals(block.getChunk()) ? 2 : 1);

            BlockCapability<IntSupplier> count = BlockCapability.of(new PrismKey("examplemod", "count"), IntSupplier.class);
            String early = "placed=" + placed + " facing=" + facing + " placeBehavior=" + placeRan
                    + " vanillaCapability=" + vanillaCapability + " neighborPlaced=" + neighborPlaced
                    + " neighbors=" + neighbors + " listed=" + listed;
            scheduler.runAtLater(block.getLocation(), () -> finish(block, side, chest, count, early, placed && facing
                    && placeRan && vanillaCapability && neighborPlaced && neighbors >= 1 && listed), 50);
        } catch (RuntimeException e) {
            log.severe("PRISM_PLATFORM_SELFTEST_FAIL " + e);
        }
    }

    private void finish(Block block, Block side, Block chest, BlockCapability<IntSupplier> count, String early,
            boolean earlyOk) {
        try {
            int ticks = blocks.capability(block, BlockFace.UP, count).map(IntSupplier::getAsInt).orElse(-1);
            boolean removed = blocks.remove(block, false) && blocks.remove(side, false);
            chest.setType(Material.AIR, false);
            boolean gone = blocks.get(block).isEmpty() && blocks.identify(side).isEmpty();
            boolean replacedClean = blocks.place(key, block.getLocation())
                    && blocks.get(block)
                            .map(c -> !c.data().has(NamespacedKey.fromString("examplemod:ticks"))).orElse(false);
            blocks.remove(block, false);
            boolean ok = earlyOk && ticks >= 2 && removed && gone && replacedClean;
            log.info((ok ? "PRISM_PLATFORM_SELFTEST_OK " : "PRISM_PLATFORM_SELFTEST_FAIL ") + key + " " + early
                    + " ticks=" + ticks + " removed=" + removed + " gone=" + gone + " dataCleared=" + replacedClean
                    + " tickingChunks=" + blocks.tickingChunks());
        } catch (RuntimeException e) {
            log.severe("PRISM_PLATFORM_SELFTEST_FAIL " + e);
        }
    }
}
