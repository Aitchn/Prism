package io.aitchn.prism.core.registry

import com.mojang.brigadier.tree.LiteralCommandNode
import io.aitchn.prism.api.command.PrismSubCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.literal

object PrismCommandRegistry {

    private val sub = mutableListOf<PrismSubCommand>()

    fun register(vararg subCommands: PrismSubCommand) {
        for (subCommand in subCommands) {
            if (sub.any { it.name == subCommand.name })
                throw IllegalArgumentException("Subcommand with name ${subCommand.name} already exists!")
            sub.add(subCommand)
        }
    }

    fun buildRoot(): LiteralCommandNode<CommandSourceStack> {
        val root = literal("prism")
        sub.forEach { root.then(it.build()) }
        return root.build()
    }
}