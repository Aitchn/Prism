package io.aitchn.prism.core.item.hopper

import io.aitchn.prism.Prism
import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.api.util.conversion
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Hopper
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe

object DiamondHopperBlockItem: PrismBlockItem() {
    override val block: PrismBlock = object: PrismBlock() {
        override fun onInventoryMoveItem(event: InventoryMoveItemEvent, block: Block) {
            Bukkit.getScheduler().runTask(Prism.instance, Runnable {
                val hopper = block.state as? Hopper ?: return@Runnable
                hopper.transferCooldown = 1
                hopper.update()
            })
        }
    }
    override val id: Key = PrismUtil.key("diamond_hopper")
    override val name: Component = Component.translatable("item.prism.diamond_hopper", "Diamond Hopper")
    override val material: Material = Material.HOPPER

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(id.conversion(), build()).apply {
            shape("DDD", "DHD", "DDD")
            setIngredient('D', ItemStack(Material.DIAMOND))
            setIngredient('H', EmeraldHopperBlockItem.build())
            group = id.asString()
        }
    )
}