package io.aitchn.prism.core.listener

import io.aitchn.prism.core.item.TestItem
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerLoadEvent

object ServerLoadListener: Listener {

    @EventHandler
    fun onServerLoad(event: ServerLoadEvent) {
        if (event.type == ServerLoadEvent.LoadType.STARTUP) {
            TestItem
        }
    }
}