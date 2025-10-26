package io.aitchn.prism

import com.comphenix.protocol.ProtocolLibrary
import com.jeff_media.customblockdata.CustomBlockData
import io.aitchn.prism.core.command.GiveCommand
import io.aitchn.prism.core.command.ItemModelCommand
import io.aitchn.prism.core.item.compressed.cobblestone.CompressedCobblestone
import io.aitchn.prism.core.item.compressed.cobblestone.DoubleCompressedCobblestone
import io.aitchn.prism.core.item.compressed.copper.CompressedCopper
import io.aitchn.prism.core.item.compressed.emerald.CompressedEmerald
import io.aitchn.prism.core.item.compressed.gold.CompressedGold
import io.aitchn.prism.core.item.compressed.iron.CompressedIron
import io.aitchn.prism.core.listener.ItemsViewListener
import io.aitchn.prism.core.listener.ServerLoadListener
import io.aitchn.prism.core.registry.PrismCommandRegistry
import io.aitchn.prism.core.registry.PrismItemRegistry
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

class Prism : JavaPlugin() {

    companion object {
        lateinit var instance: Prism
            private set
    }

    private fun register() {
        PrismItemRegistry.register(
            CompressedCobblestone,
            DoubleCompressedCobblestone,
            CompressedIron,
            CompressedCopper,
            CompressedEmerald,
            CompressedGold
        )

        PrismCommandRegistry.register(
            GiveCommand,
            ItemModelCommand
        )
    }

    override fun onEnable() {
        instance = this

        register()
        CustomBlockData.registerListener(this)

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(PrismCommandRegistry.buildRoot())
        }

        server.pluginManager.registerEvents(ServerLoadListener, this)

        ProtocolLibrary.getProtocolManager().addPacketListener(ItemsViewListener)

    }

    override fun onDisable() {
    }
}
