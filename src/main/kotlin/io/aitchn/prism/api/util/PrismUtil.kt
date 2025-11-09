package io.aitchn.prism.api.util

import com.jeff_media.customblockdata.CustomBlockData
import io.aitchn.prism.Prism
import net.kyori.adventure.key.Key
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional
import org.bukkit.persistence.PersistentDataType
import org.joml.Vector3f

object PrismUtil {

    val BLOCK_ID = NamespacedKey("prism", "block_id")

    fun key(value: String): Key = Key.key("prism", value)
    fun namespacedKey(value: String): NamespacedKey = NamespacedKey("prism", value)

    fun getCustomBlockData(block: Block): CustomBlockData {
        return CustomBlockData(block, Prism.instance)
    }

    fun setBlockFlag(id: Key, block: Block, value: Key) {
        getCustomBlockData(block).set(id.conversion(), PersistentDataType.STRING, value.asString())
    }

    fun setBlockFlag(id: NamespacedKey, block: Block, value: Key) {
        getCustomBlockData(block).set(id, PersistentDataType.STRING, value.asString())
    }

    fun clearBlockFlag(block: Block) {
        getCustomBlockData(block).clear()
    }

    fun getBlockFlag(id: Key, block: Block): String? {
        return getCustomBlockData(block).get(id.conversion(), PersistentDataType.STRING)
    }

    fun getBlockFlag(id: NamespacedKey, block: Block): String? {
        return getCustomBlockData(block).get(id, PersistentDataType.STRING)
    }

    fun hasBlockFlag(id: Key, block: Block): Boolean {
        return getCustomBlockData(block).has(id.conversion(), PersistentDataType.STRING)
    }

    fun hasBlockFlag(id: NamespacedKey, block: Block): Boolean {
        return getCustomBlockData(block).has(id, PersistentDataType.STRING)
    }

    fun getBlockCenter(block: Block): Location {
        return block.location.add(0.5, 0.5, 0.5)
    }

    fun getDisplayFrontLocation(block: Block): Location {
        val data = block.blockData as? Directional
            ?: error("Block is not directional")

        val center = getBlockCenter(block)
        val dir = data.facing.direction.normalize()

        val outward = 0.5 + 0.01 // 真的推到方塊外
        val upOffset = if (data.facing == BlockFace.UP || data.facing == BlockFace.DOWN) 0.0 else 0.2

        return center.add(dir.multiply(outward)).add(0.0, upOffset, 0.0)
    }

    fun getDisplayFrontOffset(block: Block): Vector3f {
        val center = getBlockCenter(block)
        val front = getDisplayFrontLocation(block)

        val dx = (front.x - center.x).toFloat()
        val dy = (front.y - center.y).toFloat()
        val dz = (front.z - center.z).toFloat()

        return Vector3f(dx, dy, dz)
    }
}