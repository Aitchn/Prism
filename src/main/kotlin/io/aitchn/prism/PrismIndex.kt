package io.aitchn.prism

import org.bukkit.Chunk
import org.bukkit.block.Block
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object PrismIndex {
    data class BP(val w: UUID, val x: Int, val y: Int, val z: Int)
    data class CK(val w: UUID, val cx: Int, val cz: Int)

    private val map = ConcurrentHashMap<CK, MutableSet<BP>>()

    fun add(b: Block) {
        val ck = CK(b.world.uid, b.x shr 4, b.z shr 4)
        val bp = BP(b.world.uid, b.x, b.y, b.z)
        map.computeIfAbsent(ck) { ConcurrentHashMap.newKeySet() }.add(bp)
    }

    fun remove(b: Block) {
        val ck = CK(b.world.uid, b.x shr 4, b.z shr 4)
        map[ck]?.remove(BP(b.world.uid, b.x, b.y, b.z))
    }

    fun allIn(chunk: Chunk): Set<BP> =
        map[CK(chunk.world.uid, chunk.x, chunk.z)] ?: emptySet()
}