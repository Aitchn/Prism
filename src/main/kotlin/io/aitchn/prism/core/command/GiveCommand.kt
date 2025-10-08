package io.aitchn.prism.core.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.aitchn.prism.api.command.PrismSubCommand
import io.aitchn.prism.core.registry.PrismItemRegistry
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.argument.ArgumentTypes.player
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

internal object GiveCommand: PrismSubCommand {
    override val name: String = "give"

    override fun build(): LiteralArgumentBuilder<CommandSourceStack> = literal(name)
        .then(
            argument("target", player())
                .then(
                    argument("item", greedyString())
                        .suggests { _, builder ->
                            PrismItemRegistry.getItems().forEach { item ->
                                builder.suggest(item.id.asString())
                            }
                            builder.buildFuture()
                        }
                        // ✅ 沒有 amount → 預設給 1
                        .executes { ctx ->
                            giveItem(ctx, 1)
                        }
                        // ✅ 有 amount → 使用玩家輸入的值
                        .then(
                            argument("amount", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    val amount = IntegerArgumentType.getInteger(ctx, "amount")
                                    giveItem(ctx, amount)
                                }
                        )
                )
        )

    private fun giveItem(ctx: CommandContext<CommandSourceStack>, amount: Int): Int {
        val sender = ctx.source.sender

        // 權限檢查
        if (!sender.hasPermission("aurora.give")) {
            sender.sendMessage(Component.text("❌ 你没有权限使用此命令"))
            return 0
        }

        // 解析玩家
        val targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver::class.java)
        val target: Player? = targetResolver.resolve(ctx.source).firstOrNull()
        if (target == null) {
            sender.sendMessage(Component.text("❌ 找不到目标玩家"))
            return 0
        }

        // 解析物品
        val itemId = ctx.getArgument("item", String::class.java)
        val auroraItem = PrismItemRegistry.getItem(itemId)
        if (auroraItem == null) {
            sender.sendMessage(Component.text("❌ 找不到物品 $itemId"))
            return 0
        }

        // 建立物品 & 設定數量
        val stack = auroraItem.build()
        stack.amount = amount

        // 嘗試加入背包
        val remaining = target.inventory.addItem(stack)
        val remainingAmount = remaining.values.sumOf { it.amount }
        val actualAmount = amount - remainingAmount

        // 回饋訊息
        when {
            actualAmount == 0 -> sender.sendMessage(Component.text("❌ ${target.name} 的背包已满,无法给予物品"))
            remainingAmount > 0 -> sender.sendMessage(Component.text("⚠️ 只成功给予 ${target.name} $actualAmount/$amount 个 $itemId (背包空间不足)"))
            else -> sender.sendMessage(Component.text("✅ 已給 ${target.name} $actualAmount 個 $itemId"))
        }

        return Command.SINGLE_SUCCESS
    }

}