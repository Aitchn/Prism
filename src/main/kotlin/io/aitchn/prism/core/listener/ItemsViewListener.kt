package io.aitchn.prism.core.listener

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.wrappers.BukkitConverters
import com.comphenix.protocol.wrappers.WrappedDataValue
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import io.aitchn.prism.Prism
import io.aitchn.prism.api.util.stackOf
import io.aitchn.prism.core.registry.ItemModelRegistry
import io.aitchn.prism.core.registry.PrismItemRegistry
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object ItemsViewListener : PacketAdapter(
    params().plugin(Prism.instance).serverSide().types(
        PacketType.Play.Server.WINDOW_ITEMS,
        PacketType.Play.Server.SET_SLOT,
        PacketType.Play.Server.SET_CURSOR_ITEM,
        PacketType.Play.Server.ENTITY_EQUIPMENT,
        PacketType.Play.Server.ENTITY_METADATA
    )
) {

    override fun onPacketSending(event: PacketEvent) {
        val viewer = event.player
        val p = event.packet

        when (event.packetType) {
            // 介面物品
            PacketType.Play.Server.WINDOW_ITEMS -> {
                val listMod = p.itemListModifier
                if (listMod.size() == 0) return
                val items = listMod.read(0)?.toMutableList() ?: return

                for (i in items.indices) {
                    val orig = items[i] ?: continue
                    transformOrNull(viewer, orig)?.let { items[i] = it }
                }
                listMod.write(0, items)

                val itemMod = p.itemModifier
                if (itemMod.size() > 0) {
                    itemMod.read(0)?.let { cursor ->
                        transformOrNull(viewer, cursor)?.let { itemMod.write(0, it) }
                    }
                }
            }
            // 游標物品
            PacketType.Play.Server.SET_CURSOR_ITEM -> {
                val itemMod = p.itemModifier
                if (itemMod.size() == 0) return
                val cursor = itemMod.read(0) ?: return
                transformOrNull(viewer, cursor)?.let { itemMod.write(0, it) }
            }
            // 插槽
            PacketType.Play.Server.SET_SLOT -> {
                val itemMod = p.itemModifier
                if (itemMod.size() == 0) return
                val item = itemMod.read(0) ?: return
                transformOrNull(viewer, item)?.let { itemMod.write(0, it) }
            }
            // 掉落物
            PacketType.Play.Server.ENTITY_EQUIPMENT -> {
                val slotStackPairs = p.slotStackPairLists
                if (slotStackPairs.size() == 0) return
                val pairs = slotStackPairs.read(0)?.toMutableList() ?: return

                var modified = false
                for (i in pairs.indices) {
                    val pair = pairs[i]
                    val original = pair.second ?: continue
                    transformOrNull(viewer, original)?.let {
                        pairs[i] = com.comphenix.protocol.wrappers.Pair(pair.first, it)
                        modified = true
                    }
                }
                if (modified) {
                    slotStackPairs.write(0, pairs)
                }
            }
            PacketType.Play.Server.ENTITY_METADATA -> {
                val entity = p.getEntityModifier(viewer.world).read(0) ?: return
                if (entity !is Item) return

                val dataValueMod = p.dataValueCollectionModifier
                if (dataValueMod.size() == 0) return
                val dataValues = dataValueMod.read(0)?.toMutableList() ?: return

                val itemSer = WrappedDataWatcher.Registry.getItemStackSerializer(false)
                val conv = BukkitConverters.getItemStackConverter() // NMS -> Bukkit

                var modified = false
                for (i in dataValues.indices) {
                    val dv = dataValues[i]

                    if (dv.serializer != itemSer) continue // is ItemStack
                    val bukkitItem = try { conv.getSpecific(dv.value) } catch (_: Throwable) { null } ?: continue

                    val transformed = transformOrNull(viewer, bukkitItem) ?: continue
                    val nmsTransformed = conv.getGeneric(transformed) // Bukkit -> NMS

                    dataValues[i] = WrappedDataValue(
                        dv.index,
                        dv.serializer,
                        nmsTransformed
                    )
                    modified = true
                }

                if (modified) dataValueMod.write(0, dataValues)
            }
        }
    }

    private fun transformOrNull(
        viewer: Player,
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