package io.aitchn.prism.core.item.compressed.cobblestone

import io.aitchn.prism.api.PrismItem
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material

object DoubleCompressedCobblestone: PrismItem() {
    override val id: Key = Key.key("prism", "double_compressed_cobblestone")
    override val name: Component = Component.translatable("item.prism.double_compressed_cobblestone", "Double Compressed Cobblestone")
    override val material: Material = Material.COBBLESTONE
}