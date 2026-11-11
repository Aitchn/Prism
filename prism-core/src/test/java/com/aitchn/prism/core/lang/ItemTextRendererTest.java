package com.aitchn.prism.core.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ComponentMap;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.text.Message;
import com.aitchn.prism.core.registry.MutableRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ItemTextRendererTest {

    /**
     * A stack modelled like a network item: a type, a count, and component patches over the type's defaults. Its
     * {@link #bytes()} are a deterministic serialisation, so "equal" below means byte for byte.
     */
    static final class Stack implements ItemTextRenderer.Target<String> {
        final String type;
        final int count;
        final Map<String, String> data = new TreeMap<>();
        String itemNamePatch; // null: the type's default name
        List<String> lorePatch; // null: the type's default (no lore)

        Stack(String type, int count) {
            this.type = type;
            this.count = count;
        }

        Stack copy() {
            Stack copy = new Stack(type, count);
            copy.data.putAll(data);
            copy.itemNamePatch = itemNamePatch;
            copy.lorePatch = lorePatch == null ? null : List.copyOf(lorePatch);
            return copy;
        }

        byte[] bytes() {
            StringBuilder out = new StringBuilder(type).append('*').append(count);
            data.forEach((k, v) -> out.append("|d:").append(k).append('=').append(v));
            if (itemNamePatch != null) {
                out.append("|item_name=").append(itemNamePatch);
            }
            if (lorePatch != null) {
                out.append("|lore=").append(String.join("\n", lorePatch));
            }
            return out.toString().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public @Nullable String data(@NotNull String key) {
            return data.get(key);
        }

        @Override
        public void name(@NotNull String miniMessage) {
            itemNamePatch = miniMessage;
        }

        @Override
        public void resetName() {
            itemNamePatch = null;
        }

        @Override
        public @NotNull List<String> lore() {
            return lorePatch == null ? List.of() : lorePatch;
        }

        @Override
        public void lore(@NotNull List<String> lines) {
            lorePatch = lines.isEmpty() ? null : List.copyOf(lines);
        }

        @Override
        public @NotNull String line(@NotNull String miniMessage) {
            return "L(" + miniMessage + ")";
        }
    }

    private static final PrismKey RUBY = new PrismKey("demo", "gems/ruby");
    private static final PrismKey ORE_ITEM = new PrismKey("demo", "ore");
    private static final PrismKey OLD = new PrismKey("demo", "old");

    private static ItemTextRenderer renderer() {
        MutableRegistry<ItemDefinition> items = new MutableRegistry<>();
        items.publish("demo", Map.of(
                RUBY, new ItemDefinition(RUBY, "demo", PrismKey.parse("minecraft:paper"), ComponentMap.builder()
                        .put(PrismComponents.LORE, new PrismComponents.Lore(List.of("demo.lore.heal")))
                        .build(), List.of()),
                ORE_ITEM, new ItemDefinition(ORE_ITEM, "demo", PrismKey.parse("minecraft:paper"), ComponentMap.builder()
                        .put(PrismComponents.PLACES_BLOCK, new PrismComponents.PlacesBlock(new PrismKey("demo", "ruby_ore")))
                        .build(), List.of()),
                OLD, new ItemDefinition(OLD, "demo", PrismKey.parse("minecraft:paper"), ComponentMap.builder()
                        .put(PrismComponents.DISPLAY_NAME, new PrismComponents.DisplayName("<blue>Old"))
                        .build(), List.of())));
        Translations tables = Translations.merge(List.of(Map.of(
                "en_us", Map.of("item.demo.gems.ruby", "<red>Ruby", "demo.lore.heal", "<gray>Right click to heal",
                        "demo.lore.used", "<gray>Used {0} times", "block.demo.ruby_ore", "Ruby Ore"),
                "zh_tw", Map.of("item.demo.gems.ruby", "<red>紅寶石", "demo.lore.heal", "<gray>右鍵治療",
                        "demo.lore.used", "<gray>已使用 {0} 次", "block.demo.ruby_ore", "紅寶石礦"))));
        return new ItemTextRenderer(() -> tables, items);
    }

    private static Stack ruby(int uses) {
        Stack stack = new Stack("minecraft:paper", 3);
        stack.data.put(ItemTextRenderer.IDENTITY, RUBY.toString());
        stack.data.put("demo:uses", Integer.toString(uses));
        stack.data.put(ItemTextRenderer.TEXT,
                new TextSpec(null, List.of(Message.of("demo.lore.used", uses))).encode());
        return stack;
    }

    @Test
    void eachLocaleSeesItsOwnText() {
        ItemTextRenderer renderer = renderer();
        Stack english = ruby(3);
        Stack chinese = ruby(3);
        assertTrue(renderer.toClient(english, "en_us"));
        assertTrue(renderer.toClient(chinese, "zh_tw"));
        assertEquals("<red>Ruby", english.itemNamePatch);
        assertEquals(List.of("L(<gray>Right click to heal)", "L(<gray>Used 3 times)"), english.lorePatch);
        assertEquals("<red>紅寶石", chinese.itemNamePatch);
        assertEquals(List.of("L(<gray>右鍵治療)", "L(<gray>已使用 3 次)"), chinese.lorePatch);
        assertNotEquals(english.itemNamePatch, chinese.itemNamePatch);
    }

    @Test
    void roundTripIsByteForByte() {
        ItemTextRenderer renderer = renderer();
        for (String locale : List.of("en_us", "zh_tw", "zh_hk", "ja_jp")) {
            Stack server = ruby(7);
            byte[] original = server.bytes();
            Stack client = server.copy();
            renderer.toClient(client, locale);
            assertNotEquals(new String(original, StandardCharsets.UTF_8),
                    new String(client.bytes(), StandardCharsets.UTF_8));
            assertTrue(renderer.toServer(client));
            assertEquals(new String(original, StandardCharsets.UTF_8),
                    new String(client.bytes(), StandardCharsets.UTF_8), locale);
        }
    }

    @Test
    void loreTheServerStackHadIsKeptAfterTheTranslatedLines() {
        ItemTextRenderer renderer = renderer();
        Stack server = ruby(1);
        server.lorePatch = List.of("L(Stored: 5)");
        byte[] original = server.bytes();
        Stack client = server.copy();
        renderer.toClient(client, "en_us");
        assertEquals(List.of("L(<gray>Right click to heal)", "L(<gray>Used 1 times)", "L(Stored: 5)"),
                client.lorePatch);
        renderer.toServer(client);
        assertEquals(new String(original, StandardCharsets.UTF_8), new String(client.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void iconsCarryTheirOwnNameAndLore() {
        ItemTextRenderer renderer = renderer();
        Stack icon = new Stack("minecraft:arrow", 1);
        icon.data.put(ItemTextRenderer.TEXT,
                new TextSpec(Message.of("<yellow>Next page"), List.of(Message.of("Page {0}/{1}", 2, 5))).encode());
        Stack client = icon.copy();
        assertTrue(renderer.toClient(client, "zh_tw"));
        assertEquals("<yellow>Next page", client.itemNamePatch);
        assertEquals(List.of("L(Page 2/5)"), client.lorePatch);
        renderer.toServer(client);
        assertEquals(new String(icon.bytes(), StandardCharsets.UTF_8), new String(client.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void anEmptyIconNameKeepsTheItemsOwnName() {
        ItemTextRenderer renderer = renderer();
        Stack icon = ruby(0);
        icon.data.put(ItemTextRenderer.TEXT, new TextSpec(Message.of(""), List.of()).encode());
        renderer.toClient(icon, "zh_tw");
        assertEquals("<red>紅寶石", icon.itemNamePatch);
    }

    @Test
    void namesFallBackToTheBlockThenToDisplayName() {
        ItemTextRenderer renderer = renderer();
        assertEquals(Message.of("item.demo.gems.ruby"), renderer.itemName(RUBY));
        assertEquals(Message.of("block.demo.ruby_ore"), renderer.itemName(ORE_ITEM));
        assertEquals(Message.of("<blue>Old"), renderer.itemName(OLD));
        assertEquals(Message.of("item.demo.unknown"), renderer.itemName(new PrismKey("demo", "unknown")));
        Stack ore = new Stack("minecraft:paper", 1);
        ore.data.put(ItemTextRenderer.IDENTITY, ORE_ITEM.toString());
        renderer.toClient(ore, "zh_tw");
        assertEquals("紅寶石礦", ore.itemNamePatch);
        assertNull(ore.lorePatch);
    }

    @Test
    void foreignStacksAreUntouched() {
        ItemTextRenderer renderer = renderer();
        Stack vanilla = new Stack("minecraft:stone", 64);
        vanilla.lorePatch = List.of("L(x)");
        byte[] before = vanilla.bytes();
        assertFalse(renderer.toClient(vanilla, "en_us"));
        assertFalse(renderer.toServer(vanilla));
        Stack unknown = new Stack("minecraft:paper", 1);
        unknown.data.put(ItemTextRenderer.IDENTITY, "gone:item");
        assertFalse(renderer.toClient(unknown, "en_us"));
        Stack broken = new Stack("minecraft:paper", 1);
        broken.data.put(ItemTextRenderer.IDENTITY, "not a key");
        broken.data.put(ItemTextRenderer.TEXT, "garbage");
        assertFalse(renderer.toClient(broken, "en_us"));
        assertEquals(new String(before, StandardCharsets.UTF_8), new String(vanilla.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void textSpecEncodingRoundTrips() {
        TextSpec spec = new TextSpec(Message.of("k:{0}", "a;b:c", 12L, 1.5, false),
                List.of(Message.of("x", Message.of("y", "<red>", Message.of("z"))), Message.of("")));
        String encoded = spec.encode();
        TextSpec decoded = TextSpec.decode(encoded).orElseThrow();
        assertEquals(encoded, decoded.encode());
        assertEquals(spec, decoded);
        assertTrue(TextSpec.decode("P1-0;").orElseThrow().isEmpty());
        for (String bad : List.of("", "P2-0;", "P1x0;", "P1-1;", "P1-0;junk", "P1n3:abc1;q", "P1-1;99999999999:a0;")) {
            assertTrue(TextSpec.decode(bad).isEmpty(), bad);
        }
        List<Object> args = new ArrayList<>();
        args.add(3);
        // Integers come back as longs; the encoding (and so the stack) is still the same.
        TextSpec ints = new TextSpec(null, List.of(new Message("n", args)));
        assertEquals(ints.encode(), TextSpec.decode(ints.encode()).orElseThrow().encode());
    }
}
