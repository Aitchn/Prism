package io.aitchn.prism.core.item

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Chest
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object TestChest: PrismBlockItem() {
    override val block: PrismBlock = object: PrismBlock() {
        override fun onPlace(event: BlockPlaceEvent) {
            val block = event.blockPlaced
            val state = block.state as Chest

            val item = ItemStack(Material.DIAMOND)
            val key = NamespacedKey("t","w")
            state.persistentDataContainer.set(key, PersistentDataType.BYTE_ARRAY, item.serializeAsBytes())
            val itemByte = state.persistentDataContainer.get(key, PersistentDataType.BYTE_ARRAY) ?: return
            val itemStack = ItemStack.deserializeBytes(itemByte)
            state.update(true)
            state.inventory.addItem(itemStack)
        }
    }
    override val id: Key = Key.key("test:test_chest")
    override val name: Component = Component.text("Test Chest")
    override val material: Material = Material.CHEST
}