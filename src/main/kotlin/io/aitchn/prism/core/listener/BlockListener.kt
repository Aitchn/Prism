package io.aitchn.prism.core.listener

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
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
        (PrismItemRegistry.getItem(item) as? PrismBlockItem)
            ?.onBlockPlace(event)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val id = PrismUtil.getBlockFlag(PrismBlock.BLOCK_ID, block) ?: return
        (PrismItemRegistry.getItem(id) as? PrismBlockItem)
            ?.onBlockBreak(event)
    }
}