package io.aitchn.prism.core.item

import io.aitchn.prism.api.PrismItem
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material

object TestChest: PrismItem() {
    override val id: Key = Key.key("test:test_chest")
    override val name: Component = Component.text("Test Chest")
    override val material: Material = Material.PAPER
}