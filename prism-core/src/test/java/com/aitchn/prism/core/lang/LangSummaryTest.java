package com.aitchn.prism.core.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.api.text.Message;
import com.aitchn.prism.core.content.MemoryContentSource;
import com.aitchn.prism.core.mod.ModCandidate;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.core.mod.ModReport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class LangSummaryTest {

    @Test
    void oneLinePerModWithTheMissingKeys() {
        String line = LangSummary.line("Demo", Map.of(
                "en_us", Map.of("a", "A", "b", "B", "c", "C"),
                "zh_tw", Map.of("a", "甲")), Set.of("item.demo.nameless"));
        assertEquals("PRISM_LANG Demo locales=en_us,zh_tw keys=4 missing=4 "
                + "(en_us: item.demo.nameless; zh_tw: b, c, item.demo.nameless)", line);
    }

    @Test
    void completeAndEmptyMods() {
        assertEquals("PRISM_LANG Demo locales=en_us,zh_tw keys=1 missing=0",
                LangSummary.line("Demo", Map.of("en_us", Map.of("a", "A"), "zh_tw", Map.of("a", "甲")), Set.of()));
        assertEquals("PRISM_LANG Bare locales=en_us keys=0 missing=0", LangSummary.line("Bare", Map.of(), Set.of()));
    }

    @Test
    void longListsAreCut() {
        Map<String, String> many = new java.util.TreeMap<>();
        for (int i = 0; i < 12; i++) {
            many.put("k" + (char) ('a' + i), "v");
        }
        String line = LangSummary.line("Demo", Map.of("en_us", many, "zh_tw", Map.of()), Set.of());
        assertTrue(line.startsWith("PRISM_LANG Demo locales=en_us,zh_tw keys=12 missing=12 (zh_tw: ka, kb"), line);
        assertTrue(line.endsWith(", kh (+4))"), line);
    }

    private static final class Bare implements PrismMod {
        @Override
        public void onInitialize(@NotNull ModContext context) {}
    }

    private static ModManager manager() {
        return new ModManager(key -> key.namespace().equals("minecraft"), key -> key.namespace().equals("minecraft"));
    }

    @Test
    void languageFilesAreLoadedFromTheJarAndMerged() {
        ModManager manager = manager();
        MemoryContentSource content = new MemoryContentSource()
                .put("prism/items/ruby.yml", "base: minecraft:paper\n")
                .put("prism/items/nameless.yml", "base: minecraft:paper\n")
                .put("prism/items/legacy.yml", "base: minecraft:paper\ncomponents:\n  prism:display_name: \"Legacy\"\n")
                .put("prism/lang/en_us.json", "{\"item.demo.ruby\": \"Ruby\", \"demo.hello\": \"Hello {0}\"}")
                .put("prism/lang/zh_tw.json", "{\"item\": {\"demo\": {\"ruby\": \"紅寶石\"}}}");
        ModReport report = manager.activate(new ModCandidate("Demo", "demo", "prism", content, new Bare(),
                Logger.getLogger("test")));
        assertTrue(report.active(), report.problems().toString());
        assertEquals("紅寶石", manager.translations().translate(Message.of("item.demo.ruby"), "zh_tw"));
        assertEquals("Hello Ann", manager.translations().translate(Message.of("demo.hello", "Ann"), "zh_tw"));
        assertEquals("PRISM_LANG Demo locales=en_us,zh_tw keys=3 missing=3 "
                + "(en_us: item.demo.nameless; zh_tw: demo.hello, item.demo.nameless)",
                manager.langSummary("Demo").orElseThrow());
        assertEquals(Message.of("Legacy"),
                new ItemTextRenderer(manager::translations, manager.items()).itemName(new PrismKey("demo", "legacy")));
        manager.deactivate("Demo");
        assertFalse(manager.translations().has("item.demo.ruby"));
        assertTrue(manager.langSummary("Demo").isEmpty());
    }

    @Test
    void aBrokenLanguageFileFailsTheMod() {
        ModManager manager = manager();
        MemoryContentSource content = new MemoryContentSource()
                .put("prism/lang/en_us.json", "{\"a\": 1}")
                .put("prism/lang/Chinese.json", "{}");
        ModReport report = manager.activate(new ModCandidate("Demo", "demo", "prism", content, new Bare(),
                Logger.getLogger("test")));
        assertFalse(report.active());
        assertEquals(2, report.problems().size(), report.problems().toString());
        assertEquals(List.of(), List.copyOf(manager.translations().locales().stream().filter(l -> !l.equals("en_us")).toList()));
    }
}
