package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.client.MenuTitleChannel;
import com.aitchn.prism.core.menu.MenuArt;
import com.aitchn.prism.core.menu.MenuGlyphs;
import com.aitchn.prism.core.menu.MenuLayout;
import com.aitchn.prism.core.menu.MenuTitles;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Menu art at run time (ADR-0028): the layouts of the current pack, who sees them, and the titles that draw them.
 *
 * <p>A viewer sees the art when they are a Java player, the art is in the pack, and they have not declined or failed
 * to load Prism's pack. Bedrock players (Floodgate or Geyser) and Java players without the pack get the translated
 * title with every private use character removed, and the widgets' fallback items.
 */
final class MenuArts implements Listener {

    private static final Key FONT = Key.key("prism", "menu");
    private static final TextReplacementConfig CLEAN = TextReplacementConfig.builder()
            .match(Pattern.compile("[\\x{E000}-\\x{F8FF}\\x{F0000}-\\x{10FFFD}]+")).replacement("").build();

    private final Registry<MenuArt> menus;
    private final Predicate<UUID> bedrock;
    private final UUID packId;
    private final Logger log;
    private final Set<UUID> withoutPack = ConcurrentHashMap.newKeySet();
    private final Set<PrismKey> warned = ConcurrentHashMap.newKeySet();
    private volatile Map<PrismKey, MenuLayout> layouts = Map.of();
    private volatile MenuTitleChannel channel;

    MenuArts(Registry<MenuArt> menus, Predicate<UUID> bedrock, UUID packId, Logger log) {
        this.menus = menus;
        this.bedrock = bedrock;
        this.packId = packId;
        this.log = log;
    }

    /** The layouts of the pack that was just resolved. */
    void update(Map<PrismKey, MenuLayout> layouts) {
        this.layouts = Map.copyOf(layouts);
    }

    /** The packet route for title changes; without it widgets do not move on Java (the first title still shows). */
    void channel(@Nullable MenuTitleChannel channel) {
        this.channel = channel;
    }

    /** The art {@code key} names, or null (an unknown key is reported once). */
    @Nullable MenuArt art(@Nullable PrismKey key) {
        if (key == null) {
            return null;
        }
        MenuArt art = menus.get(key).orElse(null);
        if (art == null && warned.add(key)) {
            log.warning("PRISM_MENU_WARNING no menu file for art " + key + "; the menu is shown as a plain chest");
        }
        return art;
    }

    /**
     * The art {@code key} names when its usable slots hold {@code buttons} buttons, or null: an art with too few
     * slots would hide some of the buttons, so the menu is shown as a plain chest instead (reported once).
     */
    @Nullable MenuArt art(@Nullable PrismKey key, int buttons) {
        MenuArt art = art(key);
        if (art == null) {
            return null;
        }
        int usable = 0;
        for (int slot = 0; slot < art.rows() * 9; slot++) {
            if (!art.hidden(slot)) {
                usable++;
            }
        }
        if (usable >= buttons) {
            return art;
        }
        if (warned.add(key)) {
            log.warning("PRISM_MENU_WARNING art " + key + " has " + usable + " usable slots for " + buttons
                    + " buttons; the menu is shown as a plain chest");
        }
        return null;
    }

    /** The layout {@code player} sees for {@code art}, or null when they see the plain chest. */
    @Nullable MenuLayout layoutFor(Player player, @Nullable MenuArt art) {
        if (art == null || bedrock.test(player.getUniqueId()) || withoutPack.contains(player.getUniqueId())) {
            return null;
        }
        return layouts.get(art.key());
    }

    /** The layout in the pack for {@code key}, whoever looks (for the self test). */
    @Nullable MenuLayout layout(PrismKey key) {
        return layouts.get(key);
    }

    /**
     * The title for one viewer: {@code text} (already translated) as it is when there is no art; with art but no
     * layout (Bedrock, no pack) the same text without private use characters; with a layout the glyph run and then
     * the text, or the run alone when the art hides the title.
     */
    @NotNull Component title(@NotNull Component text, @Nullable MenuArt art, @Nullable MenuLayout layout,
            @NotNull Map<String, Integer> values) {
        if (art == null) {
            return text;
        }
        if (layout == null) {
            return clean(text);
        }
        Component glyphs = Component.text(MenuTitles.glyphs(layout, values)).font(FONT).color(NamedTextColor.WHITE);
        return Component.text().append(glyphs).append(art.titleShown() ? text : Component.empty()).build();
    }

    /** {@code text} without private use characters (they would show as boxes on Bedrock). */
    static @NotNull Component clean(@NotNull Component text) {
        return text.replaceText(CLEAN);
    }

    /** Sends {@code title} to the container {@code player} has open; false when it could not be sent. */
    boolean retitle(Player player, int rows, Component title) {
        MenuTitleChannel route = channel;
        return route != null && route.retitle(player, rows, title);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!packId.equals(event.getID())) {
            return;
        }
        UUID player = event.getPlayer().getUniqueId();
        switch (event.getStatus().name()) {
            case "SUCCESSFULLY_LOADED" -> withoutPack.remove(player);
            case "DECLINED", "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD", "DISCARDED" -> withoutPack.add(player);
            default -> { } // accepted, downloaded: wait for the outcome
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        withoutPack.remove(event.getPlayer().getUniqueId());
        MenuTitleChannel route = channel;
        if (route != null) {
            route.forget(event.getPlayer().getUniqueId());
        }
    }

    /** Whether {@code text} (plain) has a private use character; for the self test. */
    static boolean hasPrivateUse(String text) {
        return MenuGlyphs.hasPrivateUse(text);
    }
}
