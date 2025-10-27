package io.aitchn.prism.core.listener

import com.jeff_media.customblockdata.events.CustomBlockDataMoveEvent
import com.jeff_media.customblockdata.events.CustomBlockDataRemoveEvent
import io.aitchn.prism.PrismIndex
import io.aitchn.prism.api.PrismBlockItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.core.registry.PrismItemRegistry
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.persistence.PersistentDataType

object ServerLoadListener: Listener {

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk

        for (x in 0 until 16) {
            for (z in 0 until 16) {
                for (y in chunk.world.minHeight until chunk.world.maxHeight) {
                    val block = chunk.getBlock(x, y, z)
                    if (block.isEmpty) continue
                    val id = PrismUtil.getBlockFlag(PrismUtil.BLOCK_ID, block) ?: continue
                    (PrismItemRegistry.getItem(id) as? PrismBlockItem)?.block?.onChunkLoad(block)
                }
            }
        }
    }

    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        PrismIndex.allIn(chunk).forEach { bp ->
            val block = chunk.world.getBlockAt(bp.x, bp.y, bp.z)
            val id = PrismUtil.getBlockFlag(PrismUtil.BLOCK_ID, block) ?: return@forEach
            (PrismItemRegistry.getItem(id) as? PrismBlockItem)?.block?.onChunkUnload(block)
        }
    }

    @EventHandler
    fun onCustomBlockDataMove(event: CustomBlockDataMoveEvent) {
        val id = event.customBlockData.get(PrismUtil.BLOCK_ID, PersistentDataType.STRING)?: return
        (PrismItemRegistry.getItem(id) as? PrismBlockItem)?.block?.onCustomBlockDataMove(event.block, event.blockTo, event.customBlockData)
    }

    @EventHandler
    fun onCustomBlockDataRemove(event: CustomBlockDataRemoveEvent) {
        val id = event.customBlockData.get(PrismUtil.BLOCK_ID, PersistentDataType.STRING)?: return
        val item = PrismItemRegistry.getItem(id)
        (item as? PrismBlockItem)?.block?.onCustomBlockDataRemove(event.block, event.customBlockData)
        when (val bukkitEvent = event.bukkitEvent) {
            is EntityExplodeEvent -> {
                val customBlocks = mutableListOf<Block>()
                var customBlockAmount = 0
                bukkitEvent.blockList().forEach { block ->
                    val id = PrismUtil.getBlockFlag(PrismUtil.BLOCK_ID, block) ?: return@forEach
                    val item = PrismItemRegistry.getItem(id) ?: return@forEach
                    block.world.dropItemNaturally(block.location, item.build())
                    PrismUtil.clearBlockFlag(block)
                    block.type = Material.AIR
                    customBlockAmount ++
                }
                customBlocks.forEach { bukkitEvent.blockList().remove(it) }
                Bukkit.getLogger().info { "CustomBlockExplodeEvent 本次炸毀: $customBlockAmount 全部炸毀 ${bukkitEvent.blockList().size}" }
            }
        }
    }
}