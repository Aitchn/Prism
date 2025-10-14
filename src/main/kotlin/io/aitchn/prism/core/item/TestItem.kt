package io.aitchn.prism.core.item

import io.aitchn.prism.api.PrismItem
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material

internal object TestItem: PrismItem() {
    override val id: Key = Key.key("prism", "test_item")
    override val name: Component = Component.translatable("item.prism.test_item", "Test Item")
    override val material: Material = Material.PAPER
}