package io.aitchn.prism.core.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.aitchn.prism.api.command.PrismSubCommand
import io.aitchn.prism.core.registry.ItemModelRegistry
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.literal
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

object ItemModelCommand: PrismSubCommand {
    override val name: String = "itemmodel"

    override fun build(): LiteralArgumentBuilder<CommandSourceStack> = literal(name)
        .requires { it.sender is Player }
        .executes { ctx ->
            val player = ctx.source.sender as? Player
            if (player == null) {
                ctx.source.sender.sendMessage(
                    Component.text("Only players can execute this command")
                        .color(NamedTextColor.RED)
                )
                return@executes 0
            }

            // 切換狀態
            val newState = ItemModelRegistry.toggle(player)

            if (newState) {
                player.sendMessage(
                    Component.text("Items model has been enabled")
                        .color(NamedTextColor.GREEN)
                )
            } else {
                player.sendMessage(
                    Component.text("Items model has been disabled")
                        .color(NamedTextColor.RED)
                )
            }

            player.updateInventory()

            Command.SINGLE_SUCCESS
        }
}