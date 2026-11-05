package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.api.item.ItemLoreBehavior;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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
    private final Appearance appearance;
    private final Logger log;

    BukkitItemService(Plugin prism, Registry<ItemDefinition> definitions, Appearance appearance) {
        this.identityKey = new NamespacedKey(prism, "item");
        this.definitions = definitions;
        this.appearance = appearance;
        this.log = prism.getLogger();
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
        renderLore(definition, stack);
        return Optional.of(stack);
    }

    private void apply(ItemDefinition definition, ItemMeta meta) {
        meta.getPersistentDataContainer().set(identityKey, PersistentDataType.STRING, definition.key().toString());
        definition.components().get(PrismComponents.DISPLAY_NAME).ifPresent(name ->
                meta.itemName(MiniMessage.miniMessage().deserialize(name.miniMessage())));
        if (appearance.hasItem(definition.key())) {
            // item_model is also what Geyser keys its Bedrock custom items on, so one component serves both editions.
            Keys.bukkit(definition.key()).ifPresent(meta::setItemModel);
        }
        definition.components().get(PrismComponents.MAX_STACK_SIZE).ifPresent(size ->
                meta.setMaxStackSize(size.value()));
    }

    @Override
    public boolean editData(@NotNull ItemStack stack, @NotNull Consumer<PersistentDataContainer> edit) {
        Optional<ItemDefinition> definition = definition(stack);
        if (definition.isEmpty()) {
            return false;
        }
        stack.editMeta(meta -> {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            String identity = data.get(identityKey, PersistentDataType.STRING);
            edit.accept(data);
            if (identity != null) {
                data.set(identityKey, PersistentDataType.STRING, identity); // the identity cannot be edited away
            }
        });
        renderLore(definition.get(), stack);
        return true;
    }

    @Override
    public void refresh(@NotNull ItemStack stack) {
        definition(stack).ifPresent(definition -> {
            stack.editMeta(meta -> apply(definition, meta));
            renderLore(definition, stack);
        });
    }

    /** Static {@code prism:lore} lines, then every {@link ItemLoreBehavior}'s lines for this stack. */
    private void renderLore(ItemDefinition definition, ItemStack stack) {
        List<String> lines = new ArrayList<>(definition.components().get(PrismComponents.LORE)
                .map(PrismComponents.Lore::lines).orElse(List.of()));
        ItemStack view = stack.clone();
        for (ItemLoreBehavior behavior : definition.behaviors(ItemLoreBehavior.class)) {
            try {
                lines.addAll(behavior.lore(view));
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, "Lore of " + definition.key() + " failed", e);
            }
        }
        if (lines.isEmpty() && !stack.getItemMeta().hasLore()) {
            return;
        }
        List<Component> lore = new ArrayList<>(lines.size());
        for (String line : lines) {
            lore.add(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        stack.editMeta(meta -> meta.lore(lore.isEmpty() ? null : lore));
    }

    @Override
    public @NotNull Optional<ItemDefinition> definition(@NotNull PrismKey key) {
        return definitions.get(key);
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
