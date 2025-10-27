package io.aitchn.prism.api

import io.aitchn.prism.api.util.PrismUtil
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

abstract class PrismBlockItem : PrismItem() {
    abstract val block: PrismBlock

    fun onBlockPlace(event: BlockPlaceEvent) {
        PrismUtil.setBlockFlag(PrismBlock.BLOCK_ID, event.block, id)
        block.onPlace(event)
    }

    fun onBlockBreak(event: BlockBreakEvent) {
        val loc = event.block.location
        event.isDropItems = false
        loc.world.dropItemNaturally(loc, build())
        block.onBreak(event)
    }
}