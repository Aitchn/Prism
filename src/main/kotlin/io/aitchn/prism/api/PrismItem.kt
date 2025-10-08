package io.aitchn.prism.api

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType



abstract class PrismItem {
    abstract val id: Key
    abstract val name: Component
    abstract val material: Material

    companion object {
        val ITEM_ID = NamespacedKey("prism", "item_id")
    }

    /**
     * 名稱方面是唯一不可更改 更新程序是
     * `Paper` -> `editItemStack()` -> `build()`
     */
    open fun editItemStack(item: ItemStack) {}

    @Suppress("UnstableApiUsage")
    fun build(): ItemStack {
        require(material.isItem) {"Item material cannot be an item!"}
        val item = ItemStack(material)
        editItemStack(item)

        item.editPersistentDataContainer { pdc ->
            pdc.set(ITEM_ID, PersistentDataType.STRING, id.asString())
        }

        item.setData(DataComponentTypes.ITEM_NAME, name)

        return item
    }

}