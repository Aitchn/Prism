package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.menu.MenuArt;
import com.aitchn.prism.core.menu.MenuGlyphs;
import com.aitchn.prism.core.menu.MenuLayout;
import com.aitchn.prism.core.menu.MenuTitles;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.pack.JavaPackBuilder;
import com.aitchn.prism.pack.MenuArtPack;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

/**
 * Optional startup check of menu art (ADR-0028), enabled with {@code -Dprism.selftest.menuart=<menu key>}. No client
 * joins the smoke test, so it checks what the server can: the Java pack holds the {@code prism:menu} font and every
 * glyph texture it names; the menu's Java title holds the background glyph and changes when a widget changes; the
 * Bedrock title of a text with private use characters has none left. Logs exactly one line,
 * {@code PRISM_MENUART_SELFTEST_OK backgrounds=<n> widgets=<n> steps=<n> bedrockTitle=clean} or
 * {@code PRISM_MENUART_SELFTEST_FAIL <reason>}.
 */
final class MenuArtSelfTest implements Listener {

    private static final Pattern TEXTURE = Pattern.compile("\"prism:font/menu/([0-9a-f]+)\\.png\"");

    private final PrismKey key;
    private final ModManager mods;
    private final PackManager pack;
    private final MenuArts arts;
    private final PlatformScheduler scheduler;
    private final Logger log;

    MenuArtSelfTest(PrismKey key, ModManager mods, PackManager pack, MenuArts arts, PlatformScheduler scheduler,
            Logger log) {
        this.key = key;
        this.mods = mods;
        this.pack = pack;
        this.arts = arts;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        scheduler.runGlobal(() -> {
            try {
                String failure = run();
                if (failure != null) {
                    log.severe("PRISM_MENUART_SELFTEST_FAIL " + failure);
                }
            } catch (RuntimeException | LinkageError e) {
                log.severe("PRISM_MENUART_SELFTEST_FAIL " + key + " " + e);
            }
        });
    }

    /** Null when everything holds (the OK line is logged), else the reason. */
    private String run() {
        MenuArt art = mods.menus().get(key).orElse(null);
        if (art == null) {
            return key + " is not a registered menu file";
        }
        JavaPackBuilder.Resolved resolved = pack.resolveNow();
        MenuArtPack.Result menus = resolved.menus();
        MenuLayout layout = menus.layouts().get(key);
        if (layout == null || layout.background() == null) {
            return key + ": the pack has no background glyph for it (warnings: " + resolved.warnings() + ")";
        }
        if (arts.layout(key) == null) {
            return key + ": the running server has no layout for it";
        }
        byte[] fontBytes = resolved.files().get(MenuArtPack.FONT_PATH);
        if (fontBytes == null) {
            return "the pack has no " + MenuArtPack.FONT_PATH;
        }
        String font = new String(fontBytes, StandardCharsets.UTF_8);
        List<MenuLayout.Glyph> glyphs = new ArrayList<>();
        glyphs.add(layout.background());
        layout.widgets().values().forEach(widget -> glyphs.addAll(widget.frames()));
        for (MenuLayout.Glyph glyph : glyphs) {
            if (!font.contains(glyph.text())) {
                return "the font has no glyph U+" + Integer.toHexString(glyph.text().charAt(0));
            }
        }
        Matcher textures = TEXTURE.matcher(font);
        int named = 0;
        while (textures.find()) {
            named++;
            String path = MenuArtPack.TEXTURE_DIR + textures.group(1) + ".png";
            if (!resolved.files().containsKey(path)) {
                return "the font names " + path + " but the pack does not hold it";
            }
        }
        if (named != menus.glyphs()) {
            return "the font names " + named + " textures for " + menus.glyphs() + " glyphs";
        }

        Map<String, Integer> start = MenuTitles.initial(art);
        Component text = Component.text("Menu");
        String before = plain(arts.title(text, art, layout, start));
        if (!before.equals(MenuTitles.java(layout, start, "Menu"))) {
            return "the title component and the composed title differ";
        }
        if (!before.contains(layout.background().text())) {
            return "the Java title has no background glyph";
        }
        if (layout.widgets().isEmpty()) {
            return key + ": the pack draws none of its widgets";
        }
        Map<String, Integer> moved = new java.util.LinkedHashMap<>(start);
        MenuLayout.WidgetGlyphs first = layout.widgets().values().iterator().next();
        moved.put(first.widget().id(), first.widget().kind() == MenuArt.Kind.ICON
                ? first.widget().steps() - 1 : first.widget().steps());
        String after = plain(arts.title(text, art, layout, moved));
        if (after.equals(before)) {
            return "the Java title does not change when widget " + first.widget().id() + " changes";
        }

        String bedrock = plain(arts.title(Component.text("Menu" + layout.background().text() + MenuGlyphs.shift(-5)),
                art, null, start));
        if (MenuArts.hasPrivateUse(bedrock) || !bedrock.equals("Menu")
                || MenuArts.hasPrivateUse(MenuTitles.bedrock(after))) {
            return "the Bedrock title is not clean: \"" + bedrock + "\"";
        }
        log.info("PRISM_MENUART_SELFTEST_OK backgrounds=" + menus.backgrounds() + " widgets=" + menus.widgets()
                + " steps=" + menus.steps() + " bedrockTitle=clean");
        return null;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
