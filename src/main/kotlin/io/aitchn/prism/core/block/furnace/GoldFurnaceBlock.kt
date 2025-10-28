package io.aitchn.prism.core.block.furnace

import io.aitchn.prism.api.PrismBlock
import org.bukkit.block.Furnace
import org.bukkit.event.block.BlockPlaceEvent

object GoldFurnaceBlock: PrismBlock() {

    override fun onPlace(event: BlockPlaceEvent) {
        val block = event.block
        val state = block.state as Furnace
        state.cookSpeedMultiplier = 6.0
        state.update(false, true)
    }
}