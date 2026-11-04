package com.aitchn.prism.plugin;

import com.aitchn.prism.api.block.BlockService;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Keeps vanilla note block behavior from changing or sounding a custom block: no re-tuning by right click, no
 * instrument recalculation from neighbors, no note when powered.
 */
final class NativeStateListener implements Listener {

    private final BlockService blocks;

    NativeStateListener(BlockService blocks) {
        this.blocks = blocks;
    }

    // HIGHEST: denying block use also cancels the event, so it must come after Prism's own NORMAL dispatch
    // (BlockListener, ItemListener), which skip cancelled events.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.NOTE_BLOCK) {
            return;
        }
        Action action = event.getAction();
        if ((action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK)
                && blocks.identify(event.getClickedBlock()).isPresent()) {
            // Block interaction is denied (no tuning, no note); item use and Prism behaviors still run.
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (event.getBlock().getType() == Material.NOTE_BLOCK && blocks.identify(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNote(NotePlayEvent event) {
        if (blocks.identify(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
