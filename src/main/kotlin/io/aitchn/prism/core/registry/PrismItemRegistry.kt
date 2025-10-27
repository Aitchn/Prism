package io.aitchn.prism.core.registry

import io.aitchn.prism.api.PrismItem
import io.aitchn.prism.api.PrismItem.Companion.ITEM_ID
import net.kyori.adventure.key.Key
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object PrismItemRegistry {

    private val items = mutableListOf<PrismItem>()

    fun register(vararg items: PrismItem) {
        for (item in items) {
            if (this.items.any { it.id == item.id })
                throw IllegalArgumentException("Item with id ${item.id} already exists!")

            this.items.add(item)
            item.recipes?.let { it.forEach { recipe -> Bukkit.addRecipe(recipe)} }
        }
    }

    fun getItems(): List<PrismItem> = items.toList()

    fun getItem(id: Key): PrismItem? = items.find { it.id == id }
    fun getItem(id: String): PrismItem? = items.find { it.id.toString() == id }
    fun getItem(item: ItemStack): PrismItem? {
        var id: String? = null
        item.editPersistentDataContainer { pdc ->
            id = pdc.get(ITEM_ID, PersistentDataType.STRING)
        }
        return id?.let { getItem(Key.key(it)) }
    }

    fun hasItem(id: Key): Boolean = items.any { it.id == id }
    fun hasItem(id: String): Boolean = items.any { it.id.toString() == id }
    fun hasItem(item: ItemStack): Boolean = getItem(item) != null
}