package io.aitchn.prism.api

import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataContainer

abstract class PrismBlock {
    companion object {
        val BLOCK_ID = NamespacedKey("prism", "block_id")
    }

    open fun onChunkLoad(block: Block) {}
    open fun onChunkUnload(block: Block) {}
    open fun onCustomBlockDataMove(from: Block, to: Block, data: PersistentDataContainer) {}
    open fun onPlayerInteract(event: PlayerInteractEvent) {}

    open fun onCustomBlockDataRemove(block: Block, data: PersistentDataContainer) {}
    open fun onPlace(event: BlockPlaceEvent) {}
    open fun onBreak(event: BlockBreakEvent) {}
    // 漏斗使用
    open fun onInventoryMoveItem(event: InventoryMoveItemEvent, block: Block) {}
}