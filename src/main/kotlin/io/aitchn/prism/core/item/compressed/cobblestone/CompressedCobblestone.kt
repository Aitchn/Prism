package io.aitchn.prism.core.item.compressed.cobblestone

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

internal object CompressedCobblestone: PrismItem() {
    override val id: Key = PrismUtil.key("compressed_cobblestone")
    override val name: Component = Component.translatable("item.prism.compressed_cobblestone", "Compressed Cobblestone")
    override val material: Material = Material.COBBLESTONE

    override val recipes: List<Recipe> = listOf(
        // 鵝卵石 -> 壓縮
        ShapedRecipe(id.add("craft").conversion(), build()).apply {
            shape("CCC", "CCC", "CCC")
            setIngredient('C', ItemStack(Material.COBBLESTONE))
            group = id.asString()
            category = CraftingBookCategory.MISC
        },
        // 二重壓縮 -> 壓縮
        ShapelessRecipe(id.add("uncraft").conversion(), build().stackOf(9)).apply {
            addIngredient(DoubleCompressedCobblestone.build())
            group = id.asString()
            category = CraftingBookCategory.MISC
        },
        // 壓縮 -> 鵝卵石
        ShapelessRecipe(PrismUtil.namespacedKey("cobblestone_uncraft"), ItemStack(Material.COBBLESTONE).stackOf(9)).apply {
            addIngredient(build())
            group = "prism:cobblestone"
            category = CraftingBookCategory.MISC
        }
    )
}