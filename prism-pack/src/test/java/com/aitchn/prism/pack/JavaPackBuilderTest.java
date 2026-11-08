package com.aitchn.prism.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ComponentMap;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.platform.MinecraftVersion;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class JavaPackBuilderTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private static ItemDefinition item(String key, ComponentMap components) {
        return new ItemDefinition(PrismKey.parse(key), "mod", PrismKey.parse("minecraft:paper"), components, List.of());
    }

    private static BlockDefinition block(String key) {
        return new BlockDefinition(PrismKey.parse(key), "mod", PrismKey.parse("minecraft:barrier"), Optional.empty(),
                ComponentMap.empty(), List.of());
    }

    private static Map<String, byte[]> entries(byte[] zip) throws Exception {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                out.put(entry.getName(), in.readAllBytes());
            }
        }
        return out;
    }

    private static String text(Map<String, byte[]> files, String path) {
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    @Test
    void connectedBlocksGetAModelPerMask() throws Exception {
        var connections = new PrismComponents.Connections(List.of("NORTH", "EAST", "SOUTH", "WEST"), List.of("self"),
                6, 4, Optional.empty());
        var cable = new BlockDefinition(PrismKey.parse("mod:cable"), "mod", PrismKey.parse("minecraft:barrier"),
                Optional.empty(), ComponentMap.builder().put(PrismComponents.CONNECTIONS, connections).build(),
                List.of());
        var result = JavaPackBuilder.build(new JavaPackBuilder.Input(
                Map.of("mod", Map.of("assets/mod/textures/block/cable.png", PNG)), List.of(), List.of(cable), 46,
                "test"));
        var files = entries(result.zip());
        assertTrue(result.blocksWithAppearance().contains(PrismKey.parse("mod:cable")));
        assertTrue(files.containsKey("assets/mod/items/block/cable_c0.json"));
        assertTrue(files.containsKey("assets/mod/items/block/cable_c15.json"));
        String model = text(files, "assets/mod/models/block/cable_c3.json");
        assertTrue(model.contains("\"all\": \"mod:block/cable\""), model);
        assertTrue(model.contains("[6, 6, 0]"), model);
        assertTrue(!files.containsKey("assets/mod/models/block/cable.json"));
    }

    @Test
    void passableBlocksAreCrossesOnTheTripwire() throws Exception {
        var flower = new BlockDefinition(PrismKey.parse("mod:flower"), "mod", PrismKey.parse("minecraft:barrier"),
                Optional.empty(), ComponentMap.builder().put(PrismComponents.PASSABLE, new PrismComponents.Passable(true))
                        .build(), List.of());
        int state = com.aitchn.prism.core.block.TripwireStates.OFFSET;
        var result = JavaPackBuilder.build(new JavaPackBuilder.Input(
                Map.of("mod", Map.of("assets/mod/textures/block/flower.png", PNG)), List.of(), List.of(flower), 46,
                "test", Map.of(state, new NativeBlockstates.StateModel(PrismKey.parse("mod:block/flower"), 0, 0))));
        var files = entries(result.zip());
        assertTrue(text(files, "assets/mod/models/block/flower.json").contains("minecraft:block/cross"));
        assertTrue(text(files, NativeBlockstates.TRIPWIRE_PATH).contains("mod:block/flower"));
        assertTrue(!files.containsKey(NativeBlockstates.PATH));
    }

    @Test
    void generatesItemDefinitionAndModelFromTexture() throws Exception {
        var result = JavaPackBuilder.build(new JavaPackBuilder.Input(
                Map.of("mod", Map.of("assets/mod/textures/item/ruby.png", PNG)),
                List.of(item("mod:ruby", ComponentMap.empty())), List.of(), 46, "test"));

        var files = entries(result.zip());
        assertTrue(result.itemsWithAppearance().contains(PrismKey.parse("mod:ruby")));
        assertTrue(text(files, "assets/mod/items/ruby.json").contains("\"model\": \"mod:item/ruby\""));
        assertTrue(text(files, "assets/mod/models/item/ruby.json").contains("minecraft:item/generated"));
        assertTrue(text(files, "pack.mcmeta").contains("\"pack_format\": 46"));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test
    void keepsModelsAndDefinitionsTheModShipped() throws Exception {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("assets/mod/models/item/ruby.json", "{\"custom\":true}".getBytes(StandardCharsets.UTF_8));
        var result = JavaPackBuilder.build(new JavaPackBuilder.Input(Map.of("mod", assets),
                List.of(item("mod:ruby", ComponentMap.empty())), List.of(), 46, "t"));
        assertEquals("{\"custom\":true}", text(entries(result.zip()), "assets/mod/models/item/ruby.json"));
        assertTrue(result.itemsWithAppearance().contains(PrismKey.parse("mod:ruby")));
    }

    @Test
    void explicitModelComponentIsHonoured() throws Exception {
        var components = ComponentMap.builder()
                .put(PrismComponents.MODEL, new PrismComponents.Model(PrismKey.parse("other:special/gem"))).build();
        var result = JavaPackBuilder.build(new JavaPackBuilder.Input(
                Map.of("mod", Map.of("assets/other/textures/special/gem.png", PNG)),
                List.of(item("mod:ruby", components)), List.of(), 46, "t"));
        assertTrue(text(entries(result.zip()), "assets/mod/items/ruby.json").contains("other:special/gem"));
    }

    @Test
    void itemsWithoutAssetsWarnAndAreNotListed() {
        var result = JavaPackBuilder.build(new JavaPackBuilder.Input(Map.of(),
                List.of(item("mod:ghost", ComponentMap.empty())), List.of(block("mod:ghost_block")), 46, "t"));
        assertFalse(result.itemsWithAppearance().contains(PrismKey.parse("mod:ghost")));
        assertFalse(result.blocksWithAppearance().contains(PrismKey.parse("mod:ghost_block")));
        assertEquals(2, result.warnings().size());
    }

    @Test
    void blocksGetTheirOwnItemDefinition() throws Exception {
        var result = JavaPackBuilder.build(new JavaPackBuilder.Input(
                Map.of("mod", Map.of("assets/mod/textures/block/ore.png", PNG)),
                List.of(), List.of(block("mod:ore")), 46, "t"));
        var files = entries(result.zip());
        assertTrue(text(files, "assets/mod/items/block/ore.json").contains("mod:block/ore"));
        assertTrue(text(files, "assets/mod/models/block/ore.json").contains("cube_all"));
        assertTrue(result.blocksWithAppearance().contains(PrismKey.parse("mod:ore")));
    }

    @Test
    void buildIsDeterministicAndOrderIndependent() {
        Map<String, byte[]> a = new LinkedHashMap<>();
        a.put("assets/mod/textures/item/a.png", PNG);
        a.put("assets/mod/textures/item/b.png", PNG);
        Map<String, byte[]> b = new LinkedHashMap<>();
        b.put("assets/mod/textures/item/b.png", PNG);
        b.put("assets/mod/textures/item/a.png", PNG);
        var one = JavaPackBuilder.build(new JavaPackBuilder.Input(Map.of("mod", a), List.of(), List.of(), 46, "t"));
        var two = JavaPackBuilder.build(new JavaPackBuilder.Input(Map.of("mod", b), List.of(), List.of(), 46, "t"));
        assertEquals(one.sha1(), two.sha1());
        assertArrayEquals(one.zip(), two.zip());
    }

    @Test
    void conflictsAndStrayFilesAreReported() {
        Map<String, Map<String, byte[]>> mods = new LinkedHashMap<>();
        mods.put("one", Map.of("assets/x/a.txt", new byte[] {1}, "readme.txt", new byte[] {1}));
        mods.put("two", Map.of("assets/x/a.txt", new byte[] {2}));
        var result = JavaPackBuilder.build(new JavaPackBuilder.Input(mods, List.of(), List.of(), 46, "t"));
        String all = result.warnings().toString();
        assertTrue(all.contains("both one and two"), all);
        assertTrue(all.contains("readme.txt"), all);
    }

    @Test
    void packFormatsFallBackToNewestKnown() {
        assertEquals(46, PackFormats.forVersion(MinecraftVersion.parse("1.21.4")).number());
        assertTrue(PackFormats.forVersion(MinecraftVersion.parse("1.21.5")).exact());
        var future = PackFormats.forVersion(MinecraftVersion.parse("26.3"));
        assertEquals(75, future.number());
        assertFalse(future.exact());
    }

    @Test
    void serverServesPacksByHashOnly() throws Exception {
        try (PackServer server = new PackServer("127.0.0.1", 0)) {
            server.start();
            server.publish("main", "abc123", new byte[] {1, 2, 3});
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port();
            var ok = client.send(HttpRequest.newBuilder(URI.create(base + "/abc123.zip")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, ok.statusCode());
            assertArrayEquals(new byte[] {1, 2, 3}, ok.body());
            var missing = client.send(HttpRequest.newBuilder(URI.create(base + "/nope.zip")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(404, missing.statusCode());
        }
    }

    @Test
    void aChangingSlotNeverEvictsAnotherSlotsPack() throws Exception {
        try (PackServer server = new PackServer("127.0.0.1", 0)) {
            server.start();
            server.publish("model", "keepme", new byte[] {9});
            for (int i = 0; i < 6; i++) {
                server.publish("main", "build" + i, new byte[] {(byte) i});
            }
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port();
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base + "/keepme.zip")).build(),
                    HttpResponse.BodyHandlers.ofByteArray()).statusCode());
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base + "/build5.zip")).build(),
                    HttpResponse.BodyHandlers.ofByteArray()).statusCode());
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base + "/build4.zip")).build(),
                    HttpResponse.BodyHandlers.ofByteArray()).statusCode());
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + "/build0.zip")).build(),
                    HttpResponse.BodyHandlers.ofByteArray()).statusCode());
        }
    }
}
