package io.aitchn.prism.core.item.furnace

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.api.util.conversion
import io.aitchn.prism.core.block.furnace.CopperFurnaceBlock
import io.aitchn.prism.core.block.furnace.EmeraldFurnaceBlock
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe

object EmeraldFurnaceBlockItem: PrismBlockItem() {
    override val block: PrismBlock = EmeraldFurnaceBlock
    override val id: Key = PrismUtil.key("emerald_furnace")
    override val name: Component = Component.translatable("item.prism.emerald_furnace", "Emerald Furnace")
    override val material: Material = Material.FURNACE

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(id.conversion(), build()).apply {
            shape("EEE", "EFE", "EEE")
            setIngredient('E', ItemStack(Material.EMERALD))
            setIngredient('F', GoldFurnaceBlockItem.build())
            group = id.asString()
        }
    )
}