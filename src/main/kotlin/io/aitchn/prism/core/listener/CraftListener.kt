package io.aitchn.prism.core.listener

import io.aitchn.prism.core.registry.PrismItemRegistry
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.PrepareItemCraftEvent

object CraftListener: Listener {

    @EventHandler
    fun onPrepareItemCraft(event: PrepareItemCraftEvent) {
        val recipe = event.recipe ?: return
        val matrix = event.inventory.matrix

        val customItems = matrix.filter { it != null && PrismItemRegistry.hasItem(it) }
        if (customItems.isEmpty()) return

        val recipeKey = when (recipe) {
            is org.bukkit.inventory.ShapedRecipe -> recipe.key
            is org.bukkit.inventory.ShapelessRecipe -> recipe.key
            else -> null
        }

        if (recipeKey?.namespace.equals("minecraft", true)) {
            event.inventory.result = null
        }
    }
}