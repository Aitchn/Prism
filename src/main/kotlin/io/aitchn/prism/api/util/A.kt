package io.aitchn.prism.api.util

import net.kyori.adventure.key.Key
import org.bukkit.NamespacedKey

fun Key.conversion(): NamespacedKey = NamespacedKey(namespace(), value())

fun NamespacedKey.conversion(): Key = Key.key(namespace(), value())