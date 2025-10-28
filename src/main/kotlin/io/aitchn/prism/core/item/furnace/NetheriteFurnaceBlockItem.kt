package io.aitchn.prism.core.item.furnace

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.api.util.conversion
import io.aitchn.prism.core.block.furnace.NetheriteFurnaceBlock
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.SmithingTransformRecipe

object NetheriteFurnaceBlockItem: PrismBlockItem() {
    override val block: PrismBlock = NetheriteFurnaceBlock
    override val id: Key = PrismUtil.key("netherite_furnace")
    override val name: Component = Component.translatable("item.prism.netherite_furnace", "Netherite Furnace")
    override val material: Material = Material.FURNACE

    override val recipes: List<Recipe> = listOf(
        SmithingTransformRecipe(
            id.conversion(),
            build(),
            RecipeChoice.ExactChoice(ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE)),
            RecipeChoice.ExactChoice(DiamondFurnaceBlockItem.build()),
            RecipeChoice.ExactChoice(ItemStack(Material.NETHERITE_INGOT))
        )
    )
}