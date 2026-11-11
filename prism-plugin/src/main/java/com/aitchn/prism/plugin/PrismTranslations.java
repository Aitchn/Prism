package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.text.Message;
import com.aitchn.prism.api.text.TranslationService;
import com.aitchn.prism.core.lang.ItemTextRenderer;
import com.aitchn.prism.core.lang.Locales;
import com.aitchn.prism.core.lang.Translations;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@link TranslationService} over the mods' merged tables (an immutable snapshot the mod manager swaps on every
 * change). Locales are remembered per player from join and {@code PlayerLocaleChangeEvent} so that network threads can
 * read them without touching the player.
 */
final class PrismTranslations implements TranslationService {

    private final ItemTextRenderer renderer;
    private final PlatformScheduler scheduler;
    private final NamespacedKey identityKey;
    private final NamespacedKey textKey;
    private final Map<UUID, String> locales = new ConcurrentHashMap<>();

    PrismTranslations(Plugin prism, ItemTextRenderer renderer, PlatformScheduler scheduler) {
        this.renderer = renderer;
        this.scheduler = scheduler;
        this.identityKey = new NamespacedKey(prism, "item");
        this.textKey = new NamespacedKey(prism, "text");
    }

    ItemTextRenderer renderer() {
        return renderer;
    }

    /** Records the locale a client reported. */
    void remember(UUID player, java.util.Locale locale) {
        locales.put(player, Locales.normalize(locale == null ? null : locale.toString()));
    }

    void forget(UUID player) {
        locales.remove(player);
    }

    /**
     * The locale of a player by id, for network threads: the one recorded at join or on change, else
     * {@code fallback}'s (read from the player object, a plain field read).
     */
    String locale(UUID id, Player fallback) {
        String known = locales.get(id);
        if (known != null) {
            return known;
        }
        if (fallback == null) {
            return Locales.FALLBACK;
        }
        try {
            return Locales.normalize(fallback.locale().toString());
        } catch (RuntimeException e) {
            return Locales.FALLBACK; // PacketEvents' placeholder player before login has finished
        }
    }

    @Override
    public @NotNull String locale(@NotNull Player player) {
        return locale(player.getUniqueId(), player);
    }

    @Override
    public @NotNull String translate(@NotNull Message message, @NotNull String locale) {
        return renderer.translations().translate(message, locale);
    }

    @Override
    public @NotNull Component render(@NotNull Message message, @NotNull String locale) {
        return MiniMessage.miniMessage().deserialize(translate(message, locale));
    }

    @Override
    public void send(@NotNull Player player, @NotNull Message message) {
        onPlayer(player, () -> player.sendMessage(render(message, player)));
    }

    @Override
    public void sendActionBar(@NotNull Player player, @NotNull Message message) {
        onPlayer(player, () -> player.sendActionBar(render(message, player)));
    }

    private void onPlayer(Player player, Runnable task) {
        if (scheduler.isOwnedByCurrentThread(player)) {
            task.run();
        } else {
            scheduler.runOn(player, task, null);
        }
    }

    @Override
    public boolean has(@NotNull String key) {
        return renderer.translations().has(key);
    }

    @Override
    public @NotNull Set<String> locales() {
        return renderer.translations().locales();
    }

    @Override
    public @NotNull Message itemName(@NotNull PrismKey item) {
        return renderer.itemName(item);
    }

    @Override
    public @NotNull Message blockName(@NotNull PrismKey block) {
        return Message.of(ItemTextRenderer.nameKey("block", block));
    }

    @Override
    public @NotNull Component name(@NotNull ItemStack stack, @NotNull String locale) {
        ItemTextRenderer.Shown shown = shown(stack);
        if (shown == null || shown.name() == null) {
            return stack.effectiveName();
        }
        return line(renderer.translations(), shown.name(), locale);
    }

    @Override
    public @NotNull List<Component> lore(@NotNull ItemStack stack, @NotNull String locale) {
        List<Component> lines = new ArrayList<>();
        ItemTextRenderer.Shown shown = shown(stack);
        Translations tables = renderer.translations();
        if (shown != null) {
            shown.lore().forEach(line -> lines.add(line(tables, line, locale)));
        }
        List<Component> own = stack.lore();
        if (own != null) {
            lines.addAll(own);
        }
        return lines;
    }

    private ItemTextRenderer.Shown shown(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasItemMeta()) {
            return null;
        }
        var data = stack.getPersistentDataContainer();
        return renderer.shown(data.get(identityKey, PersistentDataType.STRING),
                data.get(textKey, PersistentDataType.STRING));
    }

    /** One rendered line: MiniMessage, not italic (lore would otherwise be italic by default). */
    static Component line(Translations tables, Message message, String locale) {
        return MiniMessage.miniMessage().deserialize(tables.translate(message, locale))
                .decoration(TextDecoration.ITALIC, false);
    }
}
