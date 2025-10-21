package io.aitchn.prism.api.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack

interface PrismSubCommand {
    val name: String
    val permission: String
    fun build(): LiteralArgumentBuilder<CommandSourceStack>
}