package com.aitchn.prism.plugin;

import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.core.block.DigMath;
import com.aitchn.prism.core.look.ClientBreak;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Makes digging a custom block that the client breaks itself feel like the custom block. The client predicts the break
 * time from the vanilla state it was shown (a borrowed state, or the shell of the display layer), so while a player digs
 * a custom block Prism gives them a {@code block_break_speed} modifier that scales that prediction to the custom
 * hardness and tool rules. The modifier lives only for the dig: it is removed when digging aborts or finishes and when
 * the player leaves, and it is never saved (a transient modifier). The server stays authoritative: a survival break that
 * arrives much earlier than the custom block allows is refused.
 */
final class BreakFeel implements Listener {

    /** A break is refused when less than this share of the custom break time has passed (vanilla's own is 0.7). */
    private static final double EARLIEST = 0.5;

    private final PrismBlockService blocks;
    private final Supplier<ClientLooks> looks;
    private final NamespacedKey key;
    private final Logger log;
    /** Players whose client draws the block as a Bedrock custom block: Geyser derives their break speed itself. */
    private final java.util.function.Predicate<UUID> syntheticViewer;
    private final Map<UUID, Dig> digs = new ConcurrentHashMap<>();

    private record Dig(Block block, long startedNanos, int ticks) {}

    BreakFeel(Plugin plugin, PrismBlockService blocks, Supplier<ClientLooks> looks,
            java.util.function.Predicate<UUID> syntheticViewer) {
        this.syntheticViewer = syntheticViewer;
        this.blocks = blocks;
        this.looks = looks;
        this.key = new NamespacedKey(plugin, "break_speed");
        this.log = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        clear(player);
        Block block = event.getBlock();
        if (player.getGameMode() != GameMode.SURVIVAL || !blocks.drawnAsLook(block)) {
            return;
        }
        Optional<CustomBlock> custom = blocks.get(block);
        String lookId = blocks.lookIdOf(block);
        if (custom.isEmpty() || lookId == null) {
            return;
        }
        PrismComponents.Mining mining = custom.get().definition().components().get(PrismComponents.MINING)
                .orElse(PrismComponents.Mining.DEFAULT);
        ItemStack held = player.getInventory().getItemInMainHand();
        int efficiency = held.getEnchantmentLevel(Enchantment.EFFICIENCY);
        int ticks = DigMath.ticksToBreak(mining, held.getType().name(), efficiency,
                level(player, PotionEffectType.HASTE), level(player, PotionEffectType.MINING_FATIGUE), true, false);
        // Recorded for every carrier, so the earliest-break check also covers a Geyser client breaking a display
        // block's synthetic look; Java clients cannot break that one themselves (DigListener digs it).
        digs.put(player.getUniqueId(), new Dig(block, System.nanoTime(), ticks));
        ClientBreak client = blocks.clientBreaks(block) ? looks.get().table().breaking(lookId) : null;
        if (client == null) {
            return;
        }
        if (looks.get().bedrockId(lookId) != com.aitchn.prism.core.block.BlockLook.NO_BEDROCK
                && syntheticViewer.test(player.getUniqueId())) {
            // A Geyser viewer is shown the synthetic block, whose hardness is the custom one already (ADR-0024). The
            // server's own earliest-break check above still applies.
            return;
        }
        double factor = DigMath.breakSpeedFactor(mining, client, held.getType().name(), efficiency);
        if (Double.isNaN(factor)) {
            log.fine("PRISM_BREAK_INSTANT " + lookId + " is drawn with a state the client breaks instantly");
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute != null) {
            attribute.addTransientModifier(new AttributeModifier(key, factor - 1,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    /** The server's own check: a survival client that finishes far too early is refused. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreakCheck(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Dig dig = digs.get(player.getUniqueId());
        if (dig == null || !dig.block().equals(event.getBlock()) || dig.ticks() <= 1
                || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        double elapsedTicks = (System.nanoTime() - dig.startedNanos()) / 50_000_000.0;
        if (elapsedTicks < dig.ticks() * EARLIEST) {
            event.setCancelled(true);
            player.sendBlockChange(event.getBlock().getLocation(), event.getBlock().getBlockData());
            blocks.resendLooks(player, event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler
    public void onAbort(BlockDamageAbortEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    private void clear(Player player) {
        digs.remove(player.getUniqueId());
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute != null) {
            attribute.removeModifier(key);
        }
    }

    private static int level(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }
}
