package io.aitchn.prism

import io.aitchn.prism.core.TestItem
import io.aitchn.prism.core.command.GiveCommand
import io.aitchn.prism.core.listener.ServerLoadListener
import io.aitchn.prism.core.registry.PrismCommandRegistry
import io.aitchn.prism.core.registry.PrismItemRegistry
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

class Prism : JavaPlugin() {

    private fun register() {
        PrismItemRegistry.register(
            TestItem
        )

        PrismCommandRegistry.register(
            GiveCommand
        )
    }

    override fun onEnable() {

        register()

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(PrismCommandRegistry.buildRoot())
        }

        server.pluginManager.registerEvents(ServerLoadListener, this)

    }

    override fun onDisable() {
    }
}
