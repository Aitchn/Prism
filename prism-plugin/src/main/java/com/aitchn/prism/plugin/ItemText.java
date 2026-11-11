package com.aitchn.prism.plugin;

import com.aitchn.prism.client.PacketRewriting;
import com.aitchn.prism.client.TextSource;
import com.aitchn.prism.core.lang.ItemTextRenderer;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Wires per-viewer item text (ADR-0026): the packet rewriter, and the startup self test when it is asked for. */
final class ItemText {

    private ItemText() {}

    /** @return a handle that unregisters the packet rewriter */
    static Runnable install(Plugin prism, BukkitItemService items, PrismTranslations translations,
            PlatformScheduler scheduler) {
        Runnable uninstall = PacketRewriting.installItemText(new TextSource() {
            @Override
            public ItemTextRenderer renderer() {
                return translations.renderer();
            }

            @Override
            public String locale(Player player) {
                return translations.locale(player);
            }
        });
        String item = System.getProperty("prism.selftest.lang",
                System.getProperty("prism.selftest") == null ? null : ItemTextSelfTest.DEFAULT_ITEM);
        if (item != null) {
            prism.getServer().getPluginManager().registerEvents(
                    new ItemTextSelfTest(item, items, translations, scheduler, prism.getLogger()), prism);
        }
        return uninstall;
    }
}
