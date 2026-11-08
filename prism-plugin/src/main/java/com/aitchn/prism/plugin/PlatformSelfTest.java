package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.block.PrismCapabilities;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Optional startup check of the block platform, enabled with {@code -Dprism.selftest.platform=<block key>}; the smoke
 * test passes {@code examplemod:ruby_counter}. It proves on a real server that placement runs place behaviors and
 * keeps the facing, per-block data is written, ticking runs, neighbours are told, capabilities resolve for custom and
 * vanilla blocks, variants switch the look, removal deletes the data, the dropped item carries block data back into a
 * new placement, and the global timer runs. The block's mod must count ticks and neighbour changes in
 * {@code examplemod:ticks} / {@code examplemod:neighbors}, offer {@code examplemod:count}, and keep the tick count
 * through its drop.
 */
final class PlatformSelfTest implements Listener {

    private final org.bukkit.plugin.Plugin prism;
    private final PrismKey key;
    private final PrismKey neighbor;
    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;
    private final Logger log;
    private final AtomicInteger globalTicks = new AtomicInteger();

    PlatformSelfTest(org.bukkit.plugin.Plugin prism, PrismKey key, PrismKey neighbor, PrismBlockService blocks, PlatformScheduler scheduler, Logger log) {
        this.prism = prism;
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
        Location at = Bukkit.getWorlds().get(0).getSpawnLocation().add(0, 0, 6); // clear of the block (0) and model (+3 x) self tests
        scheduler.runAt(at, () -> start(at.getBlock()));
        // Counted for the 50 ticks the block test waits; checked in finish().
        ScheduledTask[] timer = new ScheduledTask[1];
        timer[0] = scheduler.runGlobalTimer(() -> {
            if (globalTicks.incrementAndGet() >= 5) {
                timer[0].cancel();
            }
        }, 1, 5);
    }

    private void start(Block block) {
        try {
            // No player is online, so the chunk would unload (and stop ticking) during the wait.
            block.getChunk().addPluginChunkTicket(prism);
            NamespacedKey placedKey = NamespacedKey.fromString("examplemod:placed");
            NamespacedKey neighborsKey = NamespacedKey.fromString("examplemod:neighbors");
            boolean placed = blocks.place(key, block.getLocation(), null, BlockFace.EAST);
            Optional<CustomBlock> custom = blocks.get(block);
            boolean facing = custom.map(c -> c.facing() == BlockFace.EAST).orElse(false);
            boolean placeRan = custom.map(c -> c.data().has(placedKey, PersistentDataType.LONG)).orElse(false);
            // Variants: placed with the first, switched at runtime, unknown names refused, the look follows. The world
            // keeps the inert carrier (ADR-0022), so the look is the state the packet rewrite sends to clients.
            int before = blocks.expectedLookId(block);
            boolean variant = custom.map(c -> c.variant().equals("idle")).orElse(false)
                    && blocks.setVariant(block, "active")
                    && blocks.get(block).map(c -> c.variant().equals("active")).orElse(false)
                    && !blocks.setVariant(block, "missing")
                    && before != -1 && before != blocks.expectedLookId(block);

            Block chest = block.getRelative(BlockFace.NORTH);
            chest.setType(Material.CHEST, false);
            boolean vanillaCapability = blocks.capability(chest, BlockFace.SOUTH, PrismCapabilities.INVENTORY).isPresent();
            // Slotless item transfer into and out of the chest: simulate changes nothing, then a real move.
            boolean itemHandler = blocks.capability(chest, BlockFace.SOUTH, PrismCapabilities.ITEM_HANDLER)
                    .map(handler -> {
                        ItemStack diamonds = new ItemStack(Material.DIAMOND, 10);
                        boolean simulated = handler.insert(diamonds, true).isEmpty() && handler.contents().isEmpty();
                        boolean inserted = handler.insert(diamonds, false).isEmpty() && diamonds.getAmount() == 10;
                        ItemStack taken = handler.extract(s -> s.getType() == Material.DIAMOND, 4, false);
                        int left = handler.contents().stream().mapToInt(ItemStack::getAmount).sum();
                        boolean none = handler.extract(s -> s.getType() == Material.EMERALD, 1, false).isEmpty();
                        return simulated && inserted && taken.getAmount() == 4 && left == 6 && none;
                    }).orElse(false);

            Block side = block.getRelative(BlockFace.WEST);
            boolean neighborPlaced = blocks.place(neighbor, side.getLocation());
            int neighbors = custom.map(c -> c.data().getOrDefault(neighborsKey, PersistentDataType.INTEGER, 0)).orElse(0);
            boolean listed = blocks.blocksIn(block.getChunk()).size() >= (side.getChunk().equals(block.getChunk()) ? 2 : 1);

            BlockCapability<IntSupplier> count = BlockCapability.of(new PrismKey("examplemod", "count"), IntSupplier.class);
            String early = "placed=" + placed + " facing=" + facing + " placeBehavior=" + placeRan + " variant=" + variant
                    + " vanillaCapability=" + vanillaCapability + " itemHandler=" + itemHandler + " neighborPlaced=" + neighborPlaced
                    + " neighbors=" + neighbors + " listed=" + listed;
            scheduler.runAtLater(block.getLocation(), () -> finish(block, side, chest, count, early, placed && facing
                    && placeRan && variant && vanillaCapability && itemHandler && neighborPlaced && neighbors >= 1 && listed), 50);
        } catch (RuntimeException e) {
            log.severe("PRISM_PLATFORM_SELFTEST_FAIL " + e);
        }
    }

    private void finish(Block block, Block side, Block chest, BlockCapability<IntSupplier> count, String early,
            boolean earlyOk) {
        try {
            int ticks = blocks.capability(block, BlockFace.UP, count).map(IntSupplier::getAsInt).orElse(-1);
            // Drop and placement with data: the drop carries the count, placing it continues from there.
            ItemStack[] dropped = new ItemStack[1];
            boolean removed = blocks.remove(block, true, stack -> dropped[0] = stack) && blocks.remove(side, false);
            NamespacedKey ticksKey = NamespacedKey.fromString("examplemod:ticks");
            int carried = dropped[0] == null ? -1
                    : dropped[0].getPersistentDataContainer().getOrDefault(ticksKey, PersistentDataType.INTEGER, -1);
            boolean restored = carried > 0 && blocks.place(key, block.getLocation(), null, BlockFace.NORTH, dropped[0])
                    && blocks.get(block).map(c -> c.data().getOrDefault(ticksKey, PersistentDataType.INTEGER, -1)
                            == carried).orElse(false)
                    && blocks.remove(block, false);
            chest.setType(Material.AIR, false);
            boolean gone = blocks.get(block).isEmpty() && blocks.identify(side).isEmpty();
            boolean replacedClean = blocks.place(key, block.getLocation())
                    && blocks.get(block)
                            .map(c -> !c.data().has(NamespacedKey.fromString("examplemod:ticks"))).orElse(false);
            blocks.remove(block, false);
            block.getChunk().removePluginChunkTicket(prism);
            boolean ok = earlyOk && ticks >= 2 && removed && gone && replacedClean && restored && globalTicks.get() >= 2;
            log.info((ok ? "PRISM_PLATFORM_SELFTEST_OK " : "PRISM_PLATFORM_SELFTEST_FAIL ") + key + " " + early
                    + " ticks=" + ticks + " dropCarried=" + carried + " restored=" + restored
                    + " globalTimer=" + globalTicks.get() + " removed=" + removed + " gone=" + gone + " dataCleared=" + replacedClean
                    + " tickingChunks=" + blocks.tickingChunks());
        } catch (RuntimeException e) {
            log.severe("PRISM_PLATFORM_SELFTEST_FAIL " + e);
        }
    }
}
