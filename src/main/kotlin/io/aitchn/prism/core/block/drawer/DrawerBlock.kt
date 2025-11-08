package io.aitchn.prism.core.block.drawer

import io.aitchn.prism.api.PrismBlock
import io.aitchn.prism.api.util.PrismUtil
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Container
import org.bukkit.block.data.Directional
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

object DrawerBlock: PrismBlock() {

    val DRAWER_AMOUNT_0 = NamespacedKey("prism", "drawer_amount_0")

    override fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player

        val block = event.clickedBlock ?: return
        val direction = block.blockData as Directional

        if (event.blockFace == direction.facing) {
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.DENY)
            if (event.action == Action.RIGHT_CLICK_BLOCK) { // 儲存
                val inv = (block.state as Container).inventory
                val template = inv.getItem(0)
                val handItem = player.inventory.itemInMainHand

                val data = PrismUtil.getCustomBlockData(block)
                val amount0 = data.get(DRAWER_AMOUNT_0, PersistentDataType.LONG) ?: 0

                val handItemTemplate = handItem.clone().also { it.amount = 1 }

                // 偵測物品
                if (template == null || template.type.isAir) {
                    inv.setItem(0, handItemTemplate)
                } else {
                    if (!template.isSimilar(handItemTemplate)) return // 與模板不一致
                }

                // 數量
                data.set(DRAWER_AMOUNT_0, PersistentDataType.LONG, amount0 + handItem.amount)
                updateTextDisplay(block)
                player.inventory.setItemInMainHand(ItemStack(Material.AIR))
            }
        }
    }

    fun updateTextDisplay(block: Block) {
        val world = block.world
        val loc = PrismUtil.getBlockCenter(block)
        val direction = block.blockData as Directional

        val data = PrismUtil.getCustomBlockData(block)
        val amount0 = data.get(DRAWER_AMOUNT_0, PersistentDataType.LONG) ?: 0

        val existing = world.getNearbyEntitiesByType(TextDisplay::class.java, loc, 0.4, 0.4, 0.4).firstOrNull()

        if (existing != null) {
            // 更新文字
            existing.text(Component.text(amount0))
            return
        }

        // 沒有就新建一個
        world.spawn(loc, TextDisplay::class.java) { display ->
            display.text(Component.text(amount0))
            display.billboard = Display.Billboard.FIXED
            display.isSeeThrough = false
            display.isShadowed = false
            display.viewRange = 16f
            display.isPersistent = false // 不寫入存檔
            display.setRotation(yawForFacing(direction.facing), 0f)
            display.transformation = Transformation(
                PrismUtil.getDisplayFrontOffset(block),
                Quaternionf(),
                Vector3f(0.5f, 0.5f, 0.5f),
                Quaternionf()
            )
        }
    }

    fun yawForFacing(face: BlockFace): Float = when (face) {
        BlockFace.NORTH -> 180f
        BlockFace.SOUTH -> 0f
        BlockFace.WEST  -> 90f
        BlockFace.EAST  -> -90f
        BlockFace.UP, BlockFace.DOWN -> 0f // 如果有朝上/下你再決定怎轉
        else -> 0f
    }
}