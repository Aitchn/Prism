package io.aitchn.prism.api

import io.aitchn.prism.api.util.PrismUtil
import org.bukkit.block.Container
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

abstract class PrismBlockItem : PrismItem() {
    abstract val block: PrismBlock

    fun onBlockPlace(event: BlockPlaceEvent) {
        PrismUtil.setBlockFlag(PrismBlock.BLOCK_ID, event.block, id)
        block.onPlace(event)
    }

    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val loc = block.location
        val world = loc.world
        event.isDropItems = false

        val state = block.state
        if (state is Container) {
            val inv = state.inventory
            for (item in inv.contents) {
                if (item == null) continue
                world.dropItemNaturally(loc, item)
            }
            inv.clear()
        }

        loc.world.dropItemNaturally(loc, build())
        this.block.onBreak(event)
    }
}