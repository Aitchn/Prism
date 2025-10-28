package io.aitchn.prism.core.item.furnace

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.api.util.conversion
import io.aitchn.prism.core.block.furnace.IronFurnaceBlock
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe

object IronFurnaceBlockItem: PrismBlockItem() {
    override val block: PrismBlock = IronFurnaceBlock
    override val id: Key = PrismUtil.key("iron_furnace")
    override val name: Component = Component.translatable("item.prism.iron_furnace", "Iron Furnace")
    override val material: Material = Material.FURNACE

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(id.conversion(), build()).apply {
            shape("III", "IFI", "III")
            setIngredient('I', ItemStack(Material.IRON_INGOT))
            setIngredient('F', CopperFurnaceBlockItem.build())
            group = id.asString()
        }
    )
}