package io.aitchn.prism.core.item.compressed.cobblestone

import io.aitchn.prism.api.PrismItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.api.util.conversion
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.recipe.CraftingBookCategory

internal object CompressedCobblestone: PrismItem() {
    override val id: Key = PrismUtil.key("compressed_cobblestone")
    override val name: Component = Component.translatable("item.prism.compressed_cobblestone", "Compressed Cobblestone")
    override val material: Material = Material.COBBLESTONE

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(PrismUtil.namespacedKey("compressed_cobblestone_craft"), build()).apply {
            shape("CCC", "CCC", "CCC")
            setIngredient('C', ItemStack(Material.COBBLESTONE))
            group = id.asString()
            category = CraftingBookCategory.MISC
        },
        ShapelessRecipe(PrismUtil.namespacedKey("compressed_cobblestone_uncraft"), ItemStack(Material.COBBLESTONE, 9)).apply {
            addIngredient(build())
            group = id.asString()
            category = CraftingBookCategory.MISC
        }
    )
}