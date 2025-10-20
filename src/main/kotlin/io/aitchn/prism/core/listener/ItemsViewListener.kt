package io.aitchn.prism.core.listener

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.wrappers.BukkitConverters
import com.comphenix.protocol.wrappers.WrappedDataValue
import io.aitchn.prism.Prism
import io.aitchn.prism.api.util.stackOf
import io.aitchn.prism.core.registry.ItemModelRegistry
import io.aitchn.prism.core.registry.PrismItemRegistry
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object ItemsViewListener : PacketAdapter(
    params().plugin(Prism.instance).serverSide().types(
        PacketType.Play.Server.WINDOW_ITEMS,
        PacketType.Play.Server.SET_SLOT,
        PacketType.Play.Server.ENTITY_EQUIPMENT,
        PacketType.Play.Server.ENTITY_METADATA
    )
) {

    override fun onPacketSending(event: PacketEvent) {
        val viewer = event.player
        val p = event.packet

        when (event.packetType) {
            PacketType.Play.Server.WINDOW_ITEMS -> {
                val ints = p.integers
                val containerId = if (ints.size() > 0) ints.read(0) else -1

                val listMod = p.itemListModifier
                if (listMod.size() == 0) return
                val items = listMod.read(0)?.toMutableList() ?: return

                for (i in items.indices) {
                    val orig = items[i] ?: continue
                    transformOrNull(viewer, containerId, i, orig)?.let { items[i] = it }
                }
                listMod.write(0, items)

                val itemMod = p.itemModifier
                if (itemMod.size() > 0) {
                    itemMod.read(0)?.let { cursor ->
                        transformOrNull(viewer, containerId, -1, cursor)?.let { itemMod.write(0, it) }
                    }
                }
            }
            PacketType.Play.Server.SET_SLOT -> {
                val itemMod = p.itemModifier
                if (itemMod.size() == 0) return
                val item = itemMod.read(0) ?: return
                val containerId = p.integers.run { if (size() > 0) read(0) else -1 }
                val slot = p.shorts.run { if (size() > 0) read(0).toInt() else -1 }
                transformOrNull(viewer, containerId, slot, item)?.let { itemMod.write(0, it) }
            }
            PacketType.Play.Server.ENTITY_EQUIPMENT -> {
                val slotStackPairs = p.slotStackPairLists
                if (slotStackPairs.size() == 0) return
                val pairs = slotStackPairs.read(0)?.toMutableList() ?: return

                var modified = false
                for (i in pairs.indices) {
                    val pair = pairs[i]
                    val original = pair.second ?: continue
                    transformOrNull(viewer, null, pair.first.ordinal, original)?.let {
                        pairs[i] = com.comphenix.protocol.wrappers.Pair(pair.first, it)
                        modified = true
                    }
                }
                if (modified) {
                    slotStackPairs.write(0, pairs)
                }
            }
            PacketType.Play.Server.ENTITY_METADATA -> {
                val dataValueMod = p.dataValueCollectionModifier
                if (dataValueMod.size() == 0) return
                val dataValues = dataValueMod.read(0)?.toMutableList() ?: return
                val itemConverter = BukkitConverters.getItemStackConverter()

                var modified = false
                for (i in dataValues.indices) {
                    val dataValue = dataValues[i]
                    if (dataValue.index == 8) {
                        val nmsItem = dataValue.value
                        val bukkitItem = itemConverter.getSpecific(nmsItem) ?: continue
                        val transformed = transformOrNull(viewer, null, -1, bukkitItem) ?: continue
                        val nmsTransformed = itemConverter.getGeneric(transformed)
                        dataValues[i] = WrappedDataValue(
                            dataValue.index,
                            dataValue.serializer,
                            nmsTransformed
                        )
                        modified = true
                    }
                }
                if (modified) {
                    dataValueMod.write(0, dataValues)
                }
            }
        }
    }

    private fun transformOrNull(
        viewer: Player,
        containerId: Int?,
        slot: Int,
        original: ItemStack
    ): ItemStack? {
        val copy = original.clone()
        val item = PrismItemRegistry.getItem(copy)?.build(ItemModelRegistry.isEnabled(viewer)) ?: return null
        item.apply {
            stackOf(copy.amount)
        }
        return item
    }
}