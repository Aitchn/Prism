package com.aitchn.prism.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ComponentMap;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.core.bedrock.SyntheticIds;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.look.DisplayLighting;
import com.aitchn.prism.core.look.LookAllocator;
import com.aitchn.prism.core.look.LookRequest;
import com.aitchn.prism.core.look.LookTable;
import com.aitchn.prism.core.look.ResolvedPool;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** {@code prism:tint} in the Java pack (item definitions with constant tints) and the Bedrock pack (baked textures). */
class TintPackTest {

    private static final String TINTED_MODEL = """
            {"parent": "minecraft:block/block",
             "textures": {"all": "m:block/lamp", "particle": "m:block/lamp"},
             "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
               "north": {"texture": "#all", "tintindex": 0}, "south": {"texture": "#all", "tintindex": 0},
               "east": {"texture": "#all", "tintindex": 0}, "west": {"texture": "#all", "tintindex": 0},
               "up": {"texture": "#all", "tintindex": 0}, "down": {"texture": "#all", "tintindex": 0}}}]}
            """;

    private static byte[] grey() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                image.setRGB(x, y, 0xFFFFFFFF);
            }
        }
        image.setRGB(1, 1, 0x80808080);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static BlockDefinition lamp(ComponentMap components) {
        return new BlockDefinition(PrismKey.parse("m:lamp"), "mod", PrismKey.parse("minecraft:barrier"),
                Optional.empty(), components, List.of());
    }

    private static ComponentMap tinted(PrismComponents.Render render) {
        return ComponentMap.builder()
                .put(PrismComponents.VARIANTS, new PrismComponents.Variants(List.of("idle", "active")))
                .put(PrismComponents.TINT, new PrismComponents.Tint(List.of(0xffffff),
                        Map.of("active", List.of(0x3ab5ff))))
                .put(PrismComponents.RENDER, render)
                .build();
    }

    private static Map<String, byte[]> assets() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("assets/m/models/block/lamp_idle.json", TINTED_MODEL.getBytes(StandardCharsets.UTF_8));
        files.put("assets/m/models/block/lamp_active.json", TINTED_MODEL.getBytes(StandardCharsets.UTF_8));
        files.put("assets/m/textures/block/lamp.png", grey());
        files.put("assets/m/textures/item/gem.png", grey());
        return files;
    }

    private static LookTable allocate(List<BlockDefinition> blocks) {
        List<LookRequest> requests = new ArrayList<>();
        for (BlockDefinition block : blocks) {
            for (Appearances.Look look : Appearances.looks(block)) {
                requests.add(new LookRequest(look.id(), Appearances.physics(block), Appearances.render(block), false));
            }
        }
        return LookAllocator.allocate(ResolvedPool.standard(), requests, false);
    }

    private static String text(Map<String, byte[]> files, String path) {
        assertNotNull(files.get(path), path);
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    @Test
    void displayLooksAndItemsGetConstantTints() throws IOException {
        List<BlockDefinition> blocks = List.of(lamp(tinted(PrismComponents.Render.DISPLAY)));
        ItemDefinition gem = new ItemDefinition(PrismKey.parse("m:gem"), "mod", PrismKey.parse("minecraft:paper"),
                ComponentMap.builder().put(PrismComponents.TINT, new PrismComponents.Tint(List.of(0x102030), Map.of()))
                        .build(), List.of());
        var resolved = JavaPackBuilder.resolve(new JavaPackBuilder.Input(Map.of("mod", assets()), List.of(gem), blocks,
                46, "t", allocate(blocks), DisplayLighting.BLOCK));
        String active = text(resolved.files(), "assets/m/items/block/lamp_active.json");
        assertTrue(active.contains("\"type\": \"minecraft:constant\""), active);
        assertTrue(active.contains("\"value\": " + 0x3ab5ff), active);
        assertTrue(text(resolved.files(), "assets/m/items/block/lamp_idle.json").contains("\"value\": " + 0xffffff));
        assertTrue(text(resolved.files(), "assets/m/items/gem.json").contains("\"value\": " + 0x102030));
        // the display layer draws that tinted item definition
        assertEquals("m:block/lamp_active",
                resolved.displayPlans().get("m:lamp#active").whole());
        assertTrue(resolved.warnings().stream().noneMatch(w -> w.contains("prism:tint")), resolved.warnings().toString());
    }

    @Test
    void faceModelsGetTintedItemDefinitions() throws IOException {
        List<BlockDefinition> blocks = List.of(lamp(tinted(PrismComponents.Render.DISPLAY)));
        Map<String, byte[]> assets = assets();
        // without a vanilla parent Prism cannot expand, so the model can be split by face
        byte[] splittable = TINTED_MODEL.replace("\"parent\": \"minecraft:block/block\",", "")
                .getBytes(StandardCharsets.UTF_8);
        assets.put("assets/m/models/block/lamp_idle.json", splittable);
        assets.put("assets/m/models/block/lamp_active.json", splittable);
        var resolved = JavaPackBuilder.resolve(new JavaPackBuilder.Input(Map.of("mod", assets), List.of(), blocks,
                46, "t", allocate(blocks), DisplayLighting.FACES));
        String face = resolved.files().entrySet().stream()
                .filter(e -> e.getKey().startsWith("assets/prism/items/face/") && e.getKey().contains("_t"))
                .map(e -> new String(e.getValue(), StandardCharsets.UTF_8)).findFirst().orElse("");
        assertTrue(face.contains("minecraft:constant"), face);
        long tintedFaces = resolved.files().keySet().stream()
                .filter(path -> path.startsWith("assets/prism/items/face/") && path.contains("_t")).count();
        assertTrue(tintedFaces > 0, resolved.files().keySet().toString());
    }

    @Test
    void borrowedStatesCannotBeTintedAndSayWhy() throws IOException {
        List<BlockDefinition> blocks = List.of(lamp(tinted(PrismComponents.Render.STATE)));
        var resolved = JavaPackBuilder.resolve(new JavaPackBuilder.Input(Map.of("mod", assets()), List.of(), blocks,
                46, "t", allocate(blocks), DisplayLighting.BLOCK));
        List<String> warnings = resolved.warnings().stream().filter(w -> w.contains("prism:tint")).toList();
        assertEquals(1, warnings.size(), resolved.warnings().toString());
        assertTrue(warnings.getFirst().contains("m:lamp#active"));
    }

    @Test
    void bedrockBakesTheTintIntoATextureCopy() throws IOException {
        List<BlockDefinition> blocks = List.of(lamp(tinted(PrismComponents.Render.DISPLAY)));
        List<String> looks = new ArrayList<>();
        blocks.forEach(b -> Appearances.looks(b).forEach(l -> looks.add(l.id())));
        var result = BedrockPackBuilder.build(new BedrockPackBuilder.Input(Map.of("mod", assets()), List.of(), "test",
                blocks, Map.of(), SyntheticIds.plan(looks, SyntheticIds.defaultBase(29_000))));
        Map<String, byte[]> files = unzip(result.mcpack());
        byte[] baked = files.get("textures/prism/blocks/prismb_m_block_lamp_t3ab5ff.png");
        assertNotNull(baked, files.keySet().toString());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(baked));
        assertEquals(0xFF3AB5FF, image.getRGB(0, 0));
        int half = image.getRGB(1, 1);
        assertEquals(0x80, half >>> 24);
        assertEquals(0x3a * 0x80 / 255, (half >> 16) & 0xFF);
        // idle is tinted white: a copy that multiplies by one
        assertTrue(files.containsKey("textures/prism/blocks/prismb_m_block_lamp_tffffff.png"));
        var active = result.syntheticBlocks().stream().filter(b -> b.lookId().equals("m:lamp#active")).findFirst()
                .orElseThrow();
        assertTrue(active.materials().values().stream()
                .allMatch(material -> material.texture().equals("prismb_m_block_lamp_t3ab5ff")),
                active.materials().toString());
        assertFalse(files.containsKey("textures/prism/blocks/prismb_m_block_lamp.png"));
    }

    @Test
    void untintedTexturesAreLeftAlone() {
        Map<String, byte[]> sources = Map.of(
                "assets/m/models/block/plain.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"m:block/p\"}}"
                        .getBytes(StandardCharsets.UTF_8),
                "assets/m/textures/block/p.png", new byte[] {1},
                "assets/m/models/block/leafy.json", "{\"parent\":\"minecraft:block/leaves\",\"textures\":{\"all\":\"m:block/p\"}}"
                        .getBytes(StandardCharsets.UTF_8));
        assertTrue(TintBake.tintIndexes(sources, PrismKey.parse("m:block/plain")).isEmpty());
        assertEquals(Map.of("assets/m/textures/block/p.png", 0),
                TintBake.tintIndexes(sources, PrismKey.parse("m:block/leafy")));
        assertEquals(null, TintBake.multiply(new byte[] {1, 2, 3}, 0xffffff));
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                files.put(entry.getName(), in.readAllBytes());
            }
        }
        return files;
    }
}
