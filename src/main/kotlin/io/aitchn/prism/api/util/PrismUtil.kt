package io.aitchn.prism.api.util

import net.kyori.adventure.key.Key
import org.bukkit.NamespacedKey

object PrismUtil {

    fun key(value: String): Key = Key.key("prism", value)
    fun namespacedKey(value: String): NamespacedKey = NamespacedKey("prism", value)
}