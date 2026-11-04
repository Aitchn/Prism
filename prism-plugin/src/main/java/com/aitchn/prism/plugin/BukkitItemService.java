package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.registry.Registry;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side items carry only identity: a vanilla carrier material plus the Prism key in the item's persistent data.
 * Appearance is decided per viewer later (see the presentation layer), never stored on the stack.
 */
final class BukkitItemService implements ItemService {

    private final NamespacedKey identityKey;
    private final Registry<ItemDefinition> definitions;

    BukkitItemService(Plugin prism, Registry<ItemDefinition> definitions) {
        this.identityKey = new NamespacedKey(prism, "item");
        this.definitions = definitions;
    }

    @Override
    public @NotNull Optional<ItemStack> create(@NotNull PrismKey key, int amount) {
        Optional<ItemDefinition> found = definitions.get(key);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ItemDefinition definition = found.get();
        Optional<Material> material = VanillaItems.material(definition.base());
        if (material.isEmpty()) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(material.get(), amount);
        stack.editMeta(meta -> apply(definition, meta));
        return Optional.of(stack);
    }

    private void apply(ItemDefinition definition, ItemMeta meta) {
        meta.getPersistentDataContainer().set(identityKey, PersistentDataType.STRING, definition.key().toString());
        definition.components().get(PrismComponents.DISPLAY_NAME).ifPresent(name ->
                meta.itemName(MiniMessage.miniMessage().deserialize(name.miniMessage())));
        definition.components().get(PrismComponents.MAX_STACK_SIZE).ifPresent(size ->
                meta.setMaxStackSize(size.value()));
    }

    @Override
    public @NotNull Optional<PrismKey> identify(@NotNull ItemStack stack) {
        if (stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        String raw = data.get(identityKey, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(PrismKey.parse(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
