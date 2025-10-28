package io.aitchn.prism.core.item.furnace

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.api.util.conversion
import io.aitchn.prism.core.block.furnace.CopperFurnaceBlock
import io.aitchn.prism.core.block.furnace.GoldFurnaceBlock
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe

object GoldFurnaceBlockItem: PrismBlockItem() {
    override val block: PrismBlock = GoldFurnaceBlock
    override val id: Key = PrismUtil.key("gold_furnace")
    override val name: Component = Component.translatable("item.prism.gold_furnace", "Gold Furnace")
    override val material: Material = Material.FURNACE

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(id.conversion(), build()).apply {
            shape("GGG", "GFG", "GGG")
            setIngredient('G', ItemStack(Material.GOLD_INGOT))
            setIngredient('F', IronFurnaceBlockItem.build())
            group = id.asString()
        }
    )
}