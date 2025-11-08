package io.aitchn.prism.core.listener

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.core.registry.PrismItemRegistry
import org.bukkit.Bukkit
import org.bukkit.block.Hopper
import org.bukkit.entity.minecart.HopperMinecart
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.PlayerInteractEvent

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

    @EventHandler
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        when (val h = event.initiator.holder) {
            is Hopper -> {
                val block = h.block
                val id = PrismUtil.getBlockFlag(PrismBlock.BLOCK_ID, block) ?: return
                (PrismItemRegistry.getItem(id) as? PrismBlockItem)
                    ?.block?.onInventoryMoveItem(event, block)
            }
            is HopperMinecart -> {
            }
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val prismItem = event.item?.let {PrismItemRegistry.getItem(it)}
        val prismBlock = event.clickedBlock?.let {
            val id = PrismUtil.getBlockFlag(PrismBlock.BLOCK_ID, it)?: return@let null
            (PrismItemRegistry.getItem(id) as? PrismBlockItem)?.block
        }

        if (event.player.isSneaking) {
            event.player.sendMessage("你正在蹲下互動")
            prismBlock?.onPlayerInteract(event)
        } else {
            event.player.sendMessage("你正在互動")
        }
    }
}