package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.client.ItemTextCheck;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Optional startup check of per-viewer item text (ADR-0026), enabled with {@code -Dprism.selftest.lang=<item key>}
 * (and with the default item whenever {@code -Dprism.selftest} is set). No client joins the smoke test, so it runs a
 * real stack through PacketEvents' own conversion, as a packet would:
 *
 * <ol>
 *   <li>two locales ({@code en_us}, {@code zh_tw}) see different names for the same server stack;
 *   <li>both rewritten stacks, restored as if a client sent them back, equal the original and are similar to it;
 *   <li>a stack with a baked name and lore (the form before ADR-0026) is normalised and then equals a new one.
 * </ol>
 */
final class ItemTextSelfTest implements Listener {

    static final String DEFAULT_ITEM = "examplemod:ruby";

    private final String item;
    private final BukkitItemService items;
    private final PrismTranslations translations;
    private final PlatformScheduler scheduler;
    private final Logger log;

    ItemTextSelfTest(String item, BukkitItemService items, PrismTranslations translations, PlatformScheduler scheduler,
            Logger log) {
        this.item = item;
        this.items = items;
        this.translations = translations;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        scheduler.runGlobal(() -> {
            try {
                run();
            } catch (RuntimeException | LinkageError e) {
                log.severe("PRISM_LANG_SELFTEST_FAIL " + item + " " + e);
            }
        });
    }

    private void run() {
        PrismKey key = PrismKey.parse(item);
        Optional<ItemStack> created = items.create(key, 2);
        if (created.isEmpty()) {
            log.severe("PRISM_LANG_SELFTEST_FAIL " + key + " is not registered");
            return;
        }
        ItemStack server = created.get();
        List<String> problems = new ArrayList<>();
        if (server.getItemMeta().hasItemName() || server.getItemMeta().hasLore()) {
            problems.add("the server stack has text baked in");
        }
        ItemTextCheck.Result result = ItemTextCheck.run(translations.renderer(), server, "en_us", "zh_tw");
        String english = result.names().get("en_us");
        String chinese = result.names().get("zh_tw");
        if (english.isBlank() || chinese.isBlank() || english.equals(chinese)) {
            problems.add("names en_us=\"" + english + "\" zh_tw=\"" + chinese + "\" are not two translations");
        }
        if (!result.plain().equals(server)) {
            problems.add("PacketEvents' conversion alone changes the stack");
        }
        for (Map.Entry<String, ItemStack> restored : result.restored().entrySet()) {
            if (!restored.getValue().equals(server) || !restored.getValue().isSimilar(server)) {
                problems.add("the stack restored from " + restored.getKey() + " differs: " + restored.getValue());
            }
        }
        ItemStack legacy = server.clone();
        legacy.editMeta(meta -> {
            meta.itemName(Component.text("Baked name"));
            meta.lore(List.of(Component.text("Baked lore")));
        });
        boolean normalised = items.normalize(legacy);
        if (!normalised || !legacy.equals(server) || !legacy.isSimilar(server)) {
            problems.add("a baked stack was not normalised (changed=" + normalised + "): " + legacy);
        }
        if (problems.isEmpty()) {
            log.info("PRISM_LANG_SELFTEST_OK " + key + " locales=2 restored=equal legacy=normalised en_us=\""
                    + english + "\" zh_tw=\"" + chinese + "\"");
        } else {
            log.severe("PRISM_LANG_SELFTEST_FAIL " + key + " " + String.join("; ", problems));
        }
    }
}
