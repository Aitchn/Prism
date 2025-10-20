package io.aitchn.prism.core.registry

import org.bukkit.entity.Player
import java.util.UUID

object ItemModelRegistry {
    private val enabledPlayers = mutableListOf<UUID>()

    fun enable(player: Player) {
        enabledPlayers.add(player.uniqueId)
    }

    fun disable(player: Player) {
        enabledPlayers.remove(player.uniqueId)
    }

    fun toggle(player: Player): Boolean {
        return if (isEnabled(player)) {
            disable(player)
            false
        } else {
            enable(player)
            true
        }
    }

    fun isEnabled(player: Player): Boolean {
        return enabledPlayers.contains(player.uniqueId)
    }
}