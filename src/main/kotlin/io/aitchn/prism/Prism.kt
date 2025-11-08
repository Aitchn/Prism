package io.aitchn.prism

import com.comphenix.protocol.ProtocolLibrary
import com.jeff_media.customblockdata.CustomBlockData
import io.aitchn.prism.core.command.GiveCommand
import io.aitchn.prism.core.command.ItemModelCommand
import io.aitchn.prism.core.item.compressed.cobblestone.CompressedCobblestoneBlockItem
import io.aitchn.prism.core.item.compressed.cobblestone.DoubleCompressedCobblestoneBlockItem
import io.aitchn.prism.core.item.compressed.copper.CompressedCopperItem
import io.aitchn.prism.core.item.compressed.emerald.CompressedEmeraldItem
import io.aitchn.prism.core.item.compressed.gold.CompressedGoldItem
import io.aitchn.prism.core.item.compressed.iron.CompressedIronItem
import io.aitchn.prism.core.item.drawer.DrawerBlockItem
import io.aitchn.prism.core.item.furnace.CopperFurnaceBlockItem
import io.aitchn.prism.core.item.furnace.DiamondFurnaceBlockItem
import io.aitchn.prism.core.item.furnace.EmeraldFurnaceBlockItem
import io.aitchn.prism.core.item.furnace.GoldFurnaceBlockItem
import io.aitchn.prism.core.item.furnace.IronFurnaceBlockItem
import io.aitchn.prism.core.item.furnace.NetheriteFurnaceBlockItem
import io.aitchn.prism.core.item.hopper.CopperHopperBlockItem
import io.aitchn.prism.core.item.hopper.DiamondHopperBlockItem
import io.aitchn.prism.core.item.hopper.DirtHopperBlockItem
import io.aitchn.prism.core.item.hopper.EmeraldHopperBlockItem
import io.aitchn.prism.core.item.hopper.GoldHopperBlockItem
import io.aitchn.prism.core.item.hopper.IronHopperBlockItem
import io.aitchn.prism.core.item.hopper.NetheriteHopperBlockItem
import io.aitchn.prism.core.listener.BlockListener
import io.aitchn.prism.core.listener.CraftListener
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
            // 壓縮方塊
            CompressedCobblestoneBlockItem,
            DoubleCompressedCobblestoneBlockItem,
            CompressedIronItem,
            CompressedCopperItem,
            CompressedEmeraldItem,
            CompressedGoldItem,

            // 熔爐
            CopperFurnaceBlockItem,
            IronFurnaceBlockItem,
            GoldFurnaceBlockItem,
            EmeraldFurnaceBlockItem,
            DiamondFurnaceBlockItem,
            NetheriteFurnaceBlockItem,

            // 漏斗
            DirtHopperBlockItem,
            CopperHopperBlockItem,
            IronHopperBlockItem,
            GoldHopperBlockItem,
            EmeraldHopperBlockItem,
            DiamondHopperBlockItem,
            NetheriteHopperBlockItem,

            DrawerBlockItem
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
        server.pluginManager.registerEvents(BlockListener, this)
        server.pluginManager.registerEvents(CraftListener, this)

        ProtocolLibrary.getProtocolManager().addPacketListener(ItemsViewListener)

    }

    override fun onDisable() {
    }
}
