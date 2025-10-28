package io.aitchn.prism.core.item.furnace

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.api.util.conversion
import io.aitchn.prism.core.block.furnace.DiamondFurnaceBlock
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe

object DiamondFurnaceBlockItem: PrismBlockItem() {
    override val block: PrismBlock = DiamondFurnaceBlock
    override val id: Key = PrismUtil.key("diamond_furnace")
    override val name: Component = Component.translatable("item.prism.diamond_furnace", "Diamond Furnace")
    override val material: Material = Material.FURNACE

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(id.conversion(), build()).apply {
            shape("DDD", "DFD", "DDD")
            setIngredient('D', ItemStack(Material.DIAMOND))
            setIngredient('F', EmeraldFurnaceBlockItem.build())
            group = id.asString()
        }
    )
}