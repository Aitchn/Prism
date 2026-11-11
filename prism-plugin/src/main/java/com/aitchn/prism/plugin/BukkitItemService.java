package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.api.item.ItemLoreBehavior;
import com.aitchn.prism.api.text.Message;
import com.aitchn.prism.core.lang.TextSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
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
 *
 * <p>No text of any language is stored either (ADR-0026): the name comes from the item's key and the lore from its
 * static {@code prism:lore} plus the {@link ItemLoreBehavior} lines, which are kept in persistent data as translation
 * keys with arguments ({@code prism:text}). The packet layer turns them into words for each viewer.
 */
final class BukkitItemService implements ItemService {

    private final NamespacedKey identityKey;
    private final NamespacedKey textKey;
    private final Registry<ItemDefinition> definitions;
    private final Appearance appearance;
    private final Logger log;

    BukkitItemService(Plugin prism, Registry<ItemDefinition> definitions, Appearance appearance) {
        this.identityKey = new NamespacedKey(prism, "item");
        this.textKey = new NamespacedKey(prism, "text");
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
        renderText(definition, stack);
        return Optional.of(stack);
    }

    private void apply(ItemDefinition definition, ItemMeta meta) {
        meta.getPersistentDataContainer().set(identityKey, PersistentDataType.STRING, definition.key().toString());
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
        renderText(definition.get(), stack);
        return true;
    }

    /**
     * Re-applies the definition and recomputes the lore keys. Also drops any name or lore text stored on the stack:
     * stacks made before ADR-0026 had them baked in (in one language), and Prism owns both on its items. A name given
     * in an anvil is a custom name and stays.
     */
    @Override
    public void refresh(@NotNull ItemStack stack) {
        definition(stack).ifPresent(definition -> {
            stack.editMeta(meta -> {
                apply(definition, meta);
                meta.itemName(null);
                meta.lore(null);
            });
            renderText(definition, stack);
        });
    }

    /**
     * Brings a stack made before ADR-0026 (name and lore baked into it) to the current form, so it stacks with new
     * ones. Cheap for everything else: a stack without meta or without Prism's identity is not looked at further.
     *
     * @return whether {@code stack} was changed
     */
    boolean normalize(@NotNull ItemStack stack) {
        if (stack.isEmpty() || !stack.hasItemMeta()) {
            return false;
        }
        if (!stack.getPersistentDataContainer().has(identityKey, PersistentDataType.STRING)) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!meta.hasItemName() && !meta.hasLore()) {
            return false;
        }
        refresh(stack);
        return true;
    }

    /** Puts a menu icon's name and lore on {@code stack} as translation keys, after the stack's own lore keys. */
    void label(@NotNull ItemStack stack, @NotNull Message name, @NotNull List<Message> lore) {
        stack.editMeta(meta -> {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            List<Message> lines = new ArrayList<>(TextSpec.decode(data.get(textKey, PersistentDataType.STRING))
                    .map(TextSpec::lore).orElse(List.of()));
            lines.addAll(lore);
            TextSpec spec = new TextSpec(name.key().isEmpty() && name.args().isEmpty() ? null : name, lines);
            if (spec.isEmpty()) {
                data.remove(textKey);
            } else {
                data.set(textKey, PersistentDataType.STRING, spec.encode());
            }
        });
    }

    /** Every {@link ItemLoreBehavior}'s lines for this stack, kept as keys and arguments in {@code prism:text}. */
    private void renderText(ItemDefinition definition, ItemStack stack) {
        List<Message> lines = new ArrayList<>();
        ItemStack view = stack.clone();
        for (ItemLoreBehavior behavior : definition.behaviors(ItemLoreBehavior.class)) {
            try {
                lines.addAll(behavior.loreMessages(view));
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, "Lore of " + definition.key() + " failed", e);
            }
        }
        String encoded = lines.isEmpty() ? null : new TextSpec(null, lines).encode();
        String current = stack.getPersistentDataContainer().get(textKey, PersistentDataType.STRING);
        if (java.util.Objects.equals(encoded, current)) {
            return;
        }
        stack.editMeta(meta -> {
            if (encoded == null) {
                meta.getPersistentDataContainer().remove(textKey);
            } else {
                meta.getPersistentDataContainer().set(textKey, PersistentDataType.STRING, encoded);
            }
        });
    }

    @Override
    public boolean matches(@NotNull com.aitchn.prism.api.recipe.Ingredient ingredient, @NotNull ItemStack stack) {
        if (stack.isEmpty() || stack.getAmount() < ingredient.count()) {
            return false;
        }
        Optional<PrismKey> custom = identify(stack);
        if (ingredient.isTag()) {
            return custom.isEmpty() && VanillaRecipes.tag(ingredient.tag())
                    .map(tag -> tag.isTagged(stack.getType())).orElse(false);
        }
        if (custom.isPresent()) {
            return custom.get().equals(ingredient.item());
        }
        return VanillaItems.material(ingredient.item()).filter(material -> material == stack.getType()).isPresent();
    }

    @Override
    public @NotNull Optional<ItemStack> create(@NotNull com.aitchn.prism.api.recipe.ItemResult result) {
        if (definitions.contains(result.item())) {
            return create(result.item(), result.count());
        }
        return VanillaItems.material(result.item()).map(material -> new ItemStack(material, result.count()));
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
