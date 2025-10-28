package io.aitchn.prism.core.item.furnace

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.api.util.conversion
import io.aitchn.prism.core.block.furnace.CopperFurnaceBlock
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe

object CopperFurnaceBlockItem: PrismBlockItem() {
    override val block: PrismBlock = CopperFurnaceBlock
    override val id: Key = PrismUtil.key("copper_furnace")
    override val name: Component = Component.translatable("item.prism.copper_furnace", "Copper Furnace")
    override val material: Material = Material.FURNACE

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(id.conversion(), build()).apply {
            shape("CCC", "CFC", "CCC")
            setIngredient('C', ItemStack(Material.COPPER_INGOT))
            setIngredient('F', ItemStack(Material.FURNACE))
            group = id.asString()
        }
    )
}