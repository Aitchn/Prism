package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/** Resolves {@code minecraft:*} keys to materials through the server's registry, so no version list exists. */
final class VanillaItems {

    private VanillaItems() {}

    static Optional<Material> material(PrismKey key) {
        if (!key.namespace().equals(PrismKey.MINECRAFT_NAMESPACE)) {
            return Optional.empty();
        }
        Material material = Registry.MATERIAL.get(NamespacedKey.minecraft(key.value()));
        return material != null && material.isItem() && !material.isAir() ? Optional.of(material) : Optional.empty();
    }

    static Optional<Material> blockMaterial(PrismKey key) {
        if (!key.namespace().equals(PrismKey.MINECRAFT_NAMESPACE)) {
            return Optional.empty();
        }
        Material material = Registry.MATERIAL.get(NamespacedKey.minecraft(key.value()));
        return material != null && material.isBlock() && !material.isAir() ? Optional.of(material) : Optional.empty();
    }

    static boolean exists(PrismKey key) {
        return material(key).isPresent();
    }

    static boolean blockExists(PrismKey key) {
        return blockMaterial(key).isPresent();
    }
}
