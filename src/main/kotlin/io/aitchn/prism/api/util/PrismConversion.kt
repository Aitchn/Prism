package io.aitchn.prism.api.util

import net.kyori.adventure.key.Key
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack

fun Key.conversion(): NamespacedKey = NamespacedKey(namespace(), value())
fun NamespacedKey.conversion(): Key = Key.key(namespace(), value())

fun ItemStack.stackOf(amount: Int): ItemStack = apply { this.amount = amount }