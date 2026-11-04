package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import java.util.Optional;
import org.bukkit.NamespacedKey;

final class Keys {

    private Keys() {}

    /** The Bukkit key with the same text; empty only if Bukkit rejects it. */
    static Optional<NamespacedKey> bukkit(PrismKey key) {
        return Optional.ofNullable(NamespacedKey.fromString(key.toString()));
    }
}
