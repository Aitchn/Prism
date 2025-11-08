package io.aitchn.prism.core.item.compressed.gold

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

object CompressedGoldItem: PrismItem() {
    override val id: Key = PrismUtil.key("compressed_gold")
    override val name: Component = Component.translatable("item.prism.compressed_gold", "Compressed Gold")
    override val material: Material = Material.GOLD_INGOT

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(id.add("craft").conversion(), build()).apply {
            shape("CCC", "CCC", "CCC")
            setIngredient('C', ItemStack(Material.GOLD_BLOCK))
            group = id.asString()
            category = CraftingBookCategory.MISC
        },
        ShapelessRecipe(id.add("uncraft").conversion(), ItemStack(Material.GOLD_BLOCK).stackOf(9)).apply {
            addIngredient(build())
            group = "prism:gold_block"
            category = CraftingBookCategory.MISC
        }
    )
}