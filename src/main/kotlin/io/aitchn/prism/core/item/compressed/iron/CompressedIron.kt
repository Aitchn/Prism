package io.aitchn.prism.core.item.compressed.iron

import io.aitchn.prism.api.PrismItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.api.util.add
import io.aitchn.prism.api.util.conversion
import io.aitchn.prism.api.util.stackOf
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.recipe.CraftingBookCategory

object CompressedIron: PrismItem() {
    override val id: Key = PrismUtil.key("compressed_iron")
    override val name: Component = Component.translatable("item.prism.compressed_iron", "Compressed Iron")
    override val material: Material = Material.IRON_INGOT

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(id.add("craft").conversion(), build()).apply {
            shape("CCC", "CCC", "CCC")
            setIngredient('C', ItemStack(Material.IRON_BLOCK))
            group = id.asString()
            category = CraftingBookCategory.MISC
        },
        ShapelessRecipe(PrismUtil.namespacedKey("iron_uncraft"), ItemStack(Material.IRON_BLOCK).stackOf(9)).apply {
            addIngredient(build())
            group = "prism:iron"
            category = CraftingBookCategory.MISC
        }
    )
}