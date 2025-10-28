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
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe

object CopperHopperBlockItem: PrismBlockItem() {
    override val block: PrismBlock = object: PrismBlock() {
        override fun onInventoryMoveItem(event: InventoryMoveItemEvent, block: Block) {
            Bukkit.getScheduler().runTask(Prism.instance, Runnable {
                val hopper = block.state as? org.bukkit.block.Hopper ?: return@Runnable
                hopper.transferCooldown = 0
                hopper.update()
            })
        }
    }
    override val id: Key = PrismUtil.key("copper_hopper")
    override val name: Component = Component.translatable("item.prism.copper_hopper", "Copper Hopper")
    override val material: Material = Material.HOPPER

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(id.conversion(), build()).apply {
            shape("CCC", "CHC", "CCC")
            setIngredient('C', ItemStack(Material.COPPER_INGOT))
            setIngredient('H', ItemStack(Material.HOPPER))
            group = id.asString()
        }
    )
}