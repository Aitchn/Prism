package com.aitchn.prism.plugin;

import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.core.block.DigMath;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Survival breaking of custom blocks. Their carrier (usually {@code barrier}) cannot be broken by a survival client,
 * so Prism runs the break itself: it starts when the player starts digging, advances every tick while the player
 * keeps looking at the block, shows the crack animation and a progress bar, and ends with a real
 * {@link BlockBreakEvent} so protection plugins and {@code BlockBreakBehavior}s see it like any other break.
 */
final class DigListener implements Listener {

    private static final int REACH = 6;
    private static final double VIEW_DISTANCE_SQUARED = 32 * 32;

    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    DigListener(PrismBlockService blocks, PlatformScheduler scheduler) {
        this.blocks = blocks;
        this.scheduler = scheduler;
    }

    private final class Session implements Runnable {
        final Player player;
        final Block block;
        final int total;
        final int sourceId;
        int elapsed;
        int shownStage = -1;
        ScheduledTask task;

        Session(Player player, Block block, int total) {
            this.player = player;
            this.block = block;
            this.total = total;
            this.sourceId = player.getEntityId() ^ 0x5052; // distinct from the player's own vanilla dig id
        }

        @Override
        public void run() {
            if (!stillDigging()) {
                stop(this);
                return;
            }
            elapsed++;
            double progress = (double) elapsed / total;
            if (progress >= 1) {
                stop(this);
                finish(player, block);
                return;
            }
            int stage = DigMath.stage(progress);
            if (stage != shownStage) {
                shownStage = stage;
                showCrack(this, (float) progress);
            }
            if (elapsed % 4 == 0) {
                block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_STONE_HIT,
                        SoundCategory.BLOCKS, 0.25f, 0.5f);
            }
        }

        private boolean stillDigging() {
            return player.isOnline() && scheduler.isOwnedByCurrentThread(player)
                    && player.getGameMode() == GameMode.SURVIVAL
                    && blocks.identify(block).isPresent()
                    && block.equals(player.getTargetBlockExact(REACH));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStartDigging(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || block == null
                || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        Optional<CustomBlock> custom = blocks.get(block);
        if (custom.isEmpty()) {
            return;
        }
        Session previous = sessions.get(player.getUniqueId());
        if (previous != null) {
            if (previous.block.equals(block)) {
                return;
            }
            stop(previous);
        }
        PrismComponents.Mining mining = custom.get().definition().components().get(PrismComponents.MINING)
                .orElse(PrismComponents.Mining.DEFAULT);
        ItemStack held = player.getInventory().getItemInMainHand();
        int ticks = DigMath.ticksToBreak(mining, held.getType().name(),
                held.getEnchantmentLevel(Enchantment.EFFICIENCY), level(player, PotionEffectType.HASTE),
                level(player, PotionEffectType.MINING_FATIGUE), true,
                player.getEyeLocation().getBlock().getType() == Material.WATER);
        if (ticks == DigMath.UNBREAKABLE) {
            return;
        }
        if (ticks == 0) {
            finish(player, block);
            return;
        }
        Session session = new Session(player, block, ticks);
        sessions.put(player.getUniqueId(), session);
        session.task = scheduler.runAtTimer(block.getLocation(), session, 1, 1);
    }

    @EventHandler
    public void onAbort(BlockDamageAbortEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.block.equals(event.getBlock())) {
            stop(session);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            session.task.cancel();
            sessions.remove(event.getPlayer().getUniqueId(), session);
        }
    }

    private static int level(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    private void stop(Session session) {
        if (session.task != null) {
            session.task.cancel();
        }
        sessions.remove(session.player.getUniqueId(), session);
        showCrack(session, 0f);
        if (session.player.isOnline()) {
            session.player.sendActionBar(Component.empty());
        }
    }

    private void showCrack(Session session, float progress) {
        Location at = session.block.getLocation();
        for (Player viewer : at.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(at) <= VIEW_DISTANCE_SQUARED) {
                viewer.sendBlockDamage(at, progress, session.sourceId);
            }
        }
        if (progress > 0 && session.player.isOnline()) {
            // The crack is drawn on the carrier, which is invisible for display-entity blocks: show progress too.
            int filled = Math.round(progress * 10);
            session.player.sendActionBar(Component.text("▮".repeat(filled), NamedTextColor.GOLD)
                    .append(Component.text("▯".repeat(10 - filled), NamedTextColor.GRAY)));
        }
    }

    private static void finish(Player player, Block block) {
        // Dispatched like a vanilla break: protection plugins can cancel it, BlockListener removes the block.
        new BlockBreakEvent(block, player).callEvent();
    }
}
