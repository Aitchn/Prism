package io.aitchn.prism.api.util

import net.kyori.adventure.key.Key
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack

/**
 * 直接在現有的 後面添加 value
 *
 * 例如:
 * ```
 * val key = Key.key("prism", "test")
 * key.add("item") // ex: prism:test_item
 * ```
 */
fun Key.add(value: String): Key = Key.key(namespace(), "${value()}_$value")
fun Key.conversion(): NamespacedKey = NamespacedKey(namespace(), value())
/**
 * 直接在現有的 後面添加 value
 *
 * 例如:
 * ```
 * val key = NamespacedKey("prism", "test")
 * key.add("item") // ex: prism:test_item
 * ```
 */
fun NamespacedKey.add(value: String): NamespacedKey = NamespacedKey(namespace(), "${value()}_$value")
fun NamespacedKey.conversion(): Key = Key.key(namespace(), value())

fun ItemStack.stackOf(amount: Int): ItemStack = apply { this.amount = amount }