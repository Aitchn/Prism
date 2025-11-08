package io.aitchn.prism.core.item.drawer

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.PrismBlockItem
import io.aitchn.prism.api.util.PrismUtil
import io.aitchn.prism.core.block.drawer.DrawerBlock
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material

object DrawerBlockItem: PrismBlockItem() {
    override val block: PrismBlock = DrawerBlock
    override val id: Key = PrismUtil.key("drawer")
    override val name: Component = Component.translatable("item.prism.drawer", "Drawer")
    override val material: Material = Material.BARREL
}