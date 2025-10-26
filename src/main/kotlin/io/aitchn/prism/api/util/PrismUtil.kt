package io.aitchn.prism.api.util

import com.jeff_media.customblockdata.CustomBlockData
import io.aitchn.prism.Prism
import net.kyori.adventure.key.Key
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataType

object PrismUtil {

    val BLOCK_ID = NamespacedKey("prism", "block_id")

    fun key(value: String): Key = Key.key("prism", value)
    fun namespacedKey(value: String): NamespacedKey = NamespacedKey("prism", value)

    fun setBlockFlag(id: Key, block: Block, value: Key) {
        val data = CustomBlockData(block, Prism.instance)
        data.set(id.conversion(), PersistentDataType.STRING, value.asString())
    }

    fun setBlockFlag(id: NamespacedKey, block: Block, value: Key) {
        val data = CustomBlockData(block, Prism.instance)
        data.set(id, PersistentDataType.STRING, value.asString())
    }

    fun getBlockFlag(id: Key, block: Block): String? {
        val data = CustomBlockData(block, Prism.instance)
        return data.get(id.conversion(), PersistentDataType.STRING)
    }

    fun getBlockFlag(id: NamespacedKey, block: Block): String? {
        val data = CustomBlockData(block, Prism.instance)
        return data.get(id, PersistentDataType.STRING)
    }

    fun hasBlockFlag(id: Key, block: Block): Boolean {
        val data = CustomBlockData(block, Prism.instance)
        return data.has(id.conversion(), PersistentDataType.STRING)
    }

    fun hasBlockFlag(id: NamespacedKey, block: Block): Boolean {
        val data = CustomBlockData(block, Prism.instance)
        return data.has(id, PersistentDataType.STRING)
    }
}