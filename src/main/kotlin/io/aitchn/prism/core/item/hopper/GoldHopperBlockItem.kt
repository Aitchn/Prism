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

object GoldHopperBlockItem: PrismBlockItem() {
    override val block: PrismBlock = object: PrismBlock() {
        override fun onInventoryMoveItem(event: InventoryMoveItemEvent, block: Block) {
            Bukkit.getScheduler().runTask(Prism.instance, Runnable {
                val hopper = block.state as? Hopper ?: return@Runnable
                hopper.transferCooldown = 4
                hopper.update()
            })
        }
    }
    override val id: Key = PrismUtil.key("gold_hopper")
    override val name: Component = Component.translatable("item.prism.gold_hopper", "Gold Hopper")
    override val material: Material = Material.HOPPER

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(id.conversion(), build()).apply {
            shape("GGG", "GHG", "GGG")
            setIngredient('G', ItemStack(Material.GOLD_INGOT))
            setIngredient('H', IronHopperBlockItem.build())
            group = id.asString()
        }
    )
}