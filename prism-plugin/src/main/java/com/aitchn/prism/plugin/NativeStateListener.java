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
 * Keeps vanilla note block behaviour from changing or sounding a custom block drawn as a note block state: no
 * re-tuning by click, no instrument change from the block below, no note when powered. Ported from the M5b native
 * backend.
 */
final class NativeStateListener implements Listener {

    private final BlockService blocks;

    NativeStateListener(BlockService blocks) {
        this.blocks = blocks;
    }

    // HIGHEST: denying block use marks the event cancelled, so it must run after Prism's own NORMAL dispatch
    // (BlockListener), which skips cancelled events.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.NOTE_BLOCK) {
            return;
        }
        // Right click only: denying a left click would also stop the player from breaking the block.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && blocks.identify(event.getClickedBlock()).isPresent()) {
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
