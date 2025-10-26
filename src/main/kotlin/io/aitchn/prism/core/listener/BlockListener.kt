package io.aitchn.prism.core.listener

import io.aitchn.prism.Prism
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.core.registry.PrismItemRegistry
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

object BlockListener: Listener {

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        val prismItem = PrismItemRegistry.getItem(item) ?: return
        prismItem.onBlockPlace(event)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val id = PrismUtil.getBlockFlag(PrismUtil.BLOCK_ID, block) ?: return
        val prismItem = PrismItemRegistry.getItem(id) ?: return
        prismItem.onBlockBreak(event)
    }
}