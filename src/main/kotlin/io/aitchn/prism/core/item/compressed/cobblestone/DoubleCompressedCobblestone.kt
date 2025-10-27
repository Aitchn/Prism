package io.aitchn.prism.core.item.compressed.cobblestone

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
import io.aitchn.prism.api.util.add
import io.aitchn.prism.api.util.conversion
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.recipe.CraftingBookCategory

object DoubleCompressedCobblestone: PrismBlockItem() {
    override val id: Key = Key.key("prism", "double_compressed_cobblestone")
    override val name: Component = Component.translatable("item.prism.double_compressed_cobblestone", "Double Compressed Cobblestone")
    override val material: Material = Material.COBBLESTONE
    override val block: PrismBlock = object: PrismBlock() {}

    override val recipes: List<Recipe> = listOf(
        ShapedRecipe(id.add("craft").conversion(), build()).apply {
            shape("CCC", "CCC", "CCC")
            setIngredient('C', CompressedCobblestoneItem.build())
            group = id.asString()
            category = CraftingBookCategory.MISC
        }
    )
}