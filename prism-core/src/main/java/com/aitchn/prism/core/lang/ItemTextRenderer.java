package com.aitchn.prism.core.lang;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.api.text.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Turns the language-neutral server form of a stack into what one player sees, and back.
 *
 * <p>A stack is Prism's when its persistent data has {@link #IDENTITY} (a custom item) or {@link #TEXT} (a
 * {@link TextSpec}, for example a menu icon). Toward a player, the stack gets an {@code item_name} (the translated
 * name) and its lore becomes the translated lines followed by whatever lore the server stack already had. From a
 * player (only creative mode sends whole stacks), the name is reset and exactly those leading lines are dropped again,
 * so the server never receives a translated stack: for a server stack without an {@code item_name},
 * {@code toServer(toClient(stack))} is the stack itself.
 *
 * <p>The line count used to restore does not depend on the language: every message is one line. Stateless and safe
 * on any thread; it reads only immutable snapshots.
 */
public final class ItemTextRenderer {

    /** Persistent data key of a custom item's identity. */
    public static final String IDENTITY = "prism:item";
    /** Persistent data key of a stack's {@link TextSpec}. */
    public static final String TEXT = "prism:text";

    /** One stack as seen by the renderer; the packet layer adapts its own item type to this. */
    public interface Target<L> {

        /** A string from the stack's persistent data, or null. */
        @Nullable String data(@NotNull String key);

        /** Sets the {@code item_name} to {@code miniMessage}. */
        void name(@NotNull String miniMessage);

        /** Puts the {@code item_name} back to the item type's default. */
        void resetName();

        /** The current lore lines (empty when there are none). */
        @NotNull List<L> lore();

        /** Replaces the lore; an empty list puts it back to the item type's default (no lore). */
        void lore(@NotNull List<L> lines);

        /** One lore line from MiniMessage text. */
        @NotNull L line(@NotNull String miniMessage);
    }

    /** What a stack shows, before translation. */
    public record Shown(@Nullable Message name, @NotNull List<Message> lore) {}

    private final Supplier<Translations> translations;
    private final Registry<ItemDefinition> items;

    public ItemTextRenderer(@NotNull Supplier<Translations> translations, @NotNull Registry<ItemDefinition> items) {
        this.translations = translations;
        this.items = items;
    }

    /** The current translation tables. */
    public @NotNull Translations translations() {
        return translations.get();
    }

    /** The name of the custom item {@code key} (see {@code TranslationService#itemName}). */
    public @NotNull Message itemName(@NotNull PrismKey key) {
        return itemName(key, items.get(key).orElse(null), translations.get());
    }

    static Message itemName(PrismKey key, @Nullable ItemDefinition definition, Translations tables) {
        String own = nameKey("item", key);
        if (tables.has(own) || definition == null) {
            return Message.of(own);
        }
        Optional<PrismKey> block = definition.components().get(PrismComponents.PLACES_BLOCK)
                .map(PrismComponents.PlacesBlock::block);
        if (block.isPresent() && tables.has(nameKey("block", block.get()))) {
            return Message.of(nameKey("block", block.get()));
        }
        return definition.components().get(PrismComponents.DISPLAY_NAME)
                .map(name -> Message.of(name.miniMessage()))
                .orElse(Message.of(own));
    }

    /** {@code item.mymod.gems.ruby} for {@code mymod:gems/ruby}. */
    public static @NotNull String nameKey(@NotNull String kind, @NotNull PrismKey key) {
        return kind + "." + key.namespace() + "." + key.value().replace('/', '.');
    }

    /** What a stack with this persistent data shows, or null when it is not Prism's. */
    public @Nullable Shown shown(@Nullable String identity, @Nullable String text) {
        TextSpec spec = TextSpec.decode(text).orElse(null);
        ItemDefinition definition = null;
        PrismKey key = null;
        if (identity != null) {
            try {
                key = PrismKey.parse(identity);
                definition = items.get(key).orElse(null);
            } catch (IllegalArgumentException e) {
                key = null;
            }
        }
        if (definition == null && spec == null) {
            return null;
        }
        Message name = null;
        List<Message> lore = new ArrayList<>();
        if (definition != null) {
            name = itemName(key, definition, translations.get());
            definition.components().get(PrismComponents.LORE)
                    .ifPresent(lines -> lines.lines().forEach(line -> lore.add(Message.of(line))));
        }
        if (spec != null) {
            if (spec.name() != null && !spec.name().key().isEmpty()) {
                name = spec.name();
            }
            lore.addAll(spec.lore());
        }
        if (name == null && lore.isEmpty()) {
            return null;
        }
        return new Shown(name, lore);
    }

    /** Rewrites {@code stack} for a viewer in {@code locale}. Returns false when it is not Prism's (untouched). */
    public <L> boolean toClient(@NotNull Target<L> stack, @NotNull String locale) {
        Shown shown = shown(stack.data(IDENTITY), stack.data(TEXT));
        if (shown == null) {
            return false;
        }
        Translations tables = translations.get();
        if (shown.name() != null) {
            stack.name(tables.translate(shown.name(), locale));
        }
        if (!shown.lore().isEmpty()) {
            List<L> lines = new ArrayList<>(shown.lore().size() + 4);
            for (Message line : shown.lore()) {
                lines.add(stack.line(tables.translate(line, locale)));
            }
            lines.addAll(stack.lore());
            stack.lore(lines);
        }
        return true;
    }

    /** Undoes {@link #toClient} on a stack a player sent. Returns false when it is not Prism's (untouched). */
    public <L> boolean toServer(@NotNull Target<L> stack) {
        Shown shown = shown(stack.data(IDENTITY), stack.data(TEXT));
        if (shown == null) {
            return false;
        }
        if (shown.name() != null) {
            stack.resetName();
        }
        int added = shown.lore().size();
        if (added > 0) {
            List<L> lines = stack.lore();
            stack.lore(new ArrayList<>(lines.subList(Math.min(added, lines.size()), lines.size())));
        }
        return true;
    }
}
