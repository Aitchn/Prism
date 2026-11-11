package com.aitchn.prism.core.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.text.Message;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TranslationsTest {

    private static final Translations TABLES = Translations.merge(List.of(
            Map.of("en_us", Map.of(
                            "item.demo.ruby", "<red>Ruby",
                            "demo.used", "Used {0} times",
                            "demo.makes", "Makes {0} {1}",
                            "demo.only_english", "English only",
                            "demo.say", "Said: {0}"),
                    "zh_tw", Map.of(
                            "item.demo.ruby", "<red>紅寶石",
                            "demo.used", "已使用 {0} 次",
                            "demo.makes", "產出 {0} 個{1}"),
                    "fr_fr", Map.of("item.demo.ruby", "Rubis"),
                    "fr_ca", Map.of("demo.used", "Utilisé {0} fois")),
            Map.of("en_us", Map.of("item.demo.ruby", "Ignored: the first bundle wins"))));

    @Test
    void localeItselfComesFirst() {
        assertEquals("<red>紅寶石", TABLES.translate(Message.of("item.demo.ruby"), "zh_tw"));
        assertEquals("<red>Ruby", TABLES.translate(Message.of("item.demo.ruby"), "en_us"));
    }

    @Test
    void codesAreNormalised() {
        assertEquals("<red>紅寶石", TABLES.translate(Message.of("item.demo.ruby"), "zh-TW"));
        assertEquals("<red>紅寶石", TABLES.translate(Message.of("item.demo.ruby"), "ZH_tw"));
    }

    @Test
    void sameScriptChineseIsUsedBeforeEnglish() {
        assertEquals("<red>紅寶石", TABLES.translate(Message.of("item.demo.ruby"), "zh_hk"));
        // Simplified Chinese never falls back to Traditional Chinese.
        assertEquals("<red>Ruby", TABLES.translate(Message.of("item.demo.ruby"), "zh_cn"));
    }

    @Test
    void sameLanguagePrefersItsHomeCountry() {
        assertEquals("Rubis", TABLES.translate(Message.of("item.demo.ruby"), "fr_be"));
        assertEquals(List.of("fr_be", "fr_fr", "fr_ca", "en_us"), TABLES.chain("fr_be"));
        assertEquals("Utilisé 3 fois", TABLES.translate(Message.of("demo.used", 3), "fr_ca"));
    }

    @Test
    void englishThenTheKeyItself() {
        assertEquals("English only", TABLES.translate(Message.of("demo.only_english"), "zh_tw"));
        assertEquals("demo.nowhere", TABLES.translate(Message.of("demo.nowhere"), "zh_tw"));
        assertEquals("<gray>Literal {0}", TABLES.translate(Message.of("<gray>Literal {0}"), "en_us"));
        assertEquals("<gray>Literal 7", TABLES.translate(Message.of("<gray>Literal {0}", 7), "en_us"));
    }

    @Test
    void firstBundleWins() {
        assertEquals("<red>Ruby", TABLES.lookup("item.demo.ruby", "en_us"));
        assertNull(TABLES.lookup("demo.nowhere", "en_us"));
    }

    @Test
    void argumentsAreFormatted() {
        assertEquals("已使用 1,234,567 次", TABLES.translate(Message.of("demo.used", 1_234_567L), "zh_tw"));
        assertEquals("Used 2.5 times", TABLES.translate(Message.of("demo.used", 2.5), "en_us"));
        assertEquals("Used 3 times", TABLES.translate(Message.of("demo.used", 3.0), "en_us"));
        assertEquals("Used 0.33 times", TABLES.translate(Message.of("demo.used", 1 / 3.0), "en_us"));
        assertEquals("Used true times", TABLES.translate(Message.of("demo.used", true), "en_us"));
    }

    @Test
    void nestedMessagesAreTranslatedForTheSameViewer() {
        Message makes = Message.of("demo.makes", 4, Message.of("item.demo.ruby"));
        assertEquals("產出 4 個<red>紅寶石", TABLES.translate(makes, "zh_tw"));
        assertEquals("Makes 4 <red>Ruby", TABLES.translate(makes, "en_us"));
    }

    @Test
    void textArgumentsCannotInjectTags() {
        assertEquals("Said: \\<red>hi \\\\o/", TABLES.translate(Message.of("demo.say", "<red>hi \\o/"), "en_us"));
    }

    @Test
    void placeholdersWithoutArgumentsStay() {
        assertEquals("Makes 1 {1}", TABLES.translate(Message.of("demo.makes", 1), "en_us"));
        assertEquals("a {x} {} {12345} b", Translations.format("a {x} {} {12345} b", List.of("v"), String::valueOf));
        assertEquals("{0", Translations.format("{0", List.of("v"), String::valueOf));
    }

    @Test
    void localesAndKeys() {
        assertEquals(Set.of("en_us", "zh_tw", "fr_fr", "fr_ca"), TABLES.locales());
        assertTrue(TABLES.has("demo.makes"));
        assertFalse(TABLES.has("demo.nowhere"));
        assertEquals(Set.of("en_us"), Translations.EMPTY.locales());
    }

    @Test
    void messageArgumentsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> Message.of("k", new Object()));
        assertThrows(IllegalArgumentException.class, () -> Message.of("k", (Object) null));
    }
}
