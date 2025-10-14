package io.aitchn.prism.core.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerLoadEvent

object ServerLoadListener: Listener {

    @EventHandler
    fun onServerLoad(event: ServerLoadEvent) {
    }
}