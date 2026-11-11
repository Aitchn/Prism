package com.aitchn.prism.api.text;

import com.aitchn.prism.api.PrismKey;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Translations from the language files of every active mod ({@code prism/lang/<locale>.json} in the mod jar).
 *
 * <p>A locale is a Minecraft language code in lower case, such as {@code en_us} or {@code zh_tw}. A key is looked up
 * in this order: the locale itself, another locale of the same language (for {@code zh} only one of the same script:
 * {@code zh_hk} finds {@code zh_tw}, never {@code zh_cn}), {@code en_us}, and finally the key itself, used as literal
 * MiniMessage text.
 *
 * <p>Item names and lore are translated by Prism in the packets each player receives, so mods never put text of one
 * language on an item stack. Use this service for everything a mod sends itself: chat lines, action bars, titles.
 * Safe to call from any thread.
 */
public interface TranslationService {

    /** The locale every lookup falls back to. */
    String FALLBACK_LOCALE = "en_us";

    /** The locale {@code player}'s client reports, normalised ({@code en_us} when it reports none). */
    @NotNull String locale(@NotNull Player player);

    /** {@code message} as MiniMessage text in {@code locale}. */
    @NotNull String translate(@NotNull Message message, @NotNull String locale);

    /** Shortcut for {@code translate(Message.of(key, args), locale)}. */
    default @NotNull String translate(@NotNull String key, @NotNull String locale, @NotNull Object... args) {
        return translate(Message.of(key, args), locale);
    }

    /** {@code message} in {@code locale}, parsed into a component. */
    @NotNull Component render(@NotNull Message message, @NotNull String locale);

    /** {@code message} in {@code player}'s locale, parsed into a component. */
    default @NotNull Component render(@NotNull Message message, @NotNull Player player) {
        return render(message, locale(player));
    }

    /** Sends {@code message} to {@code player}'s chat in their locale. Callable from any thread. */
    void send(@NotNull Player player, @NotNull Message message);

    /** Shows {@code message} above {@code player}'s hotbar in their locale. Callable from any thread. */
    void sendActionBar(@NotNull Player player, @NotNull Message message);

    /** Whether any language file of an active mod defines {@code key}. */
    boolean has(@NotNull String key);

    /** Every locale some active mod has a language file for, plus {@link #FALLBACK_LOCALE}. */
    @NotNull Set<String> locales();

    /**
     * The name of the custom item {@code item}, as a message to show or to pass as an argument. Translation key
     * {@code item.<namespace>.<path>} ({@code /} in the path becomes {@code .}); an item that places a block also falls
     * back to that block's name, then to its {@code prism:display_name}.
     */
    @NotNull Message itemName(@NotNull PrismKey item);

    /** The name of the custom block {@code block}: translation key {@code block.<namespace>.<path>}. */
    @NotNull Message blockName(@NotNull PrismKey block);

    /**
     * The name {@code stack} shows to a player in {@code locale}, exactly as Prism renders it into their packets (for
     * a book, a chat line or a sign). A stack that is not Prism's shows its effective name.
     */
    @NotNull Component name(@NotNull ItemStack stack, @NotNull String locale);

    /**
     * The lore {@code stack} shows to a player in {@code locale}: the translated lines of a Prism item (static
     * {@code prism:lore}, then its {@code ItemLoreBehavior} lines), followed by any lore stored on the stack itself.
     */
    @NotNull List<Component> lore(@NotNull ItemStack stack, @NotNull String locale);
}
