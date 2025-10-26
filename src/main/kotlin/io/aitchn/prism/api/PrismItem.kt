package io.aitchn.prism.api

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType



abstract class PrismItem {
    abstract val id: Key
    abstract val name: Component
    abstract val material: Material
    open val itemModel: Key? = null
    open val recipes: List<Recipe>? = null

    companion object {
        val ITEM_ID = NamespacedKey("prism", "item_id")
    }

    /**
     * 名稱方面是唯一不可更改 更新程序是
     * `Paper` -> `editItemStack()` -> `build()`
     *
     * `item_name` 使用 name
     * `item_model` 使用 itemModel
     * 這是無法更改的地方
     */
    open fun editItemStack(item: ItemStack) {}

    @Suppress("UnstableApiUsage")
    fun build(itemModelEnabled: Boolean = false): ItemStack {
        require(material.isItem) {"Item material cannot be an item!"}
        val item = ItemStack(material)
        editItemStack(item)

        item.editPersistentDataContainer { pdc ->
            pdc.set(ITEM_ID, PersistentDataType.STRING, id.asString())
        }

        item.setData(DataComponentTypes.ITEM_NAME, name)

        itemModel?.let {
            if (itemModelEnabled) {
                item.setData(DataComponentTypes.ITEM_MODEL, it)
            }
        } ?: item.resetData(DataComponentTypes.ITEM_MODEL)

        return item
    }

    open fun onBlockPlace(event: BlockPlaceEvent) {}
    open fun onBlockBreak(event: BlockBreakEvent) {}

}