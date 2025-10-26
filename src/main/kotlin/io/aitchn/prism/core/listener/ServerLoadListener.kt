package io.aitchn.prism.core.listener

import com.jeff_media.customblockdata.events.CustomBlockDataMoveEvent
import com.jeff_media.customblockdata.events.CustomBlockDataRemoveEvent
import io.aitchn.prism.PrismIndex
import io.aitchn.prism.api.util.PrismUtil
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent

object ServerLoadListener: Listener {

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk

        for (x in 0 until 16) {
            for (z in 0 until 16) {
                for (y in chunk.world.minHeight until chunk.world.maxHeight) {
                    val block = chunk.getBlock(x, y, z)
                    if (block.isEmpty) continue
                    if (PrismUtil.hasBlockFlag(PrismUtil.BLOCK_ID, block)) {
                        PrismIndex.add(block)
                    }
                }
            }
        }
    }

    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        PrismIndex.allIn(chunk).forEach { bp ->
            val block = chunk.world.getBlockAt(bp.x, bp.y, bp.z)
            PrismIndex.remove(block)
        }
    }

    @EventHandler
    fun onCustomBlockDataMove(event: CustomBlockDataMoveEvent) {
        PrismIndex.remove(event.block)
        PrismIndex.add(event.blockTo)
    }

    @EventHandler
    fun onCustomBlockDataRemove(event: CustomBlockDataRemoveEvent) {
        PrismIndex.remove(event.block)
    }
}