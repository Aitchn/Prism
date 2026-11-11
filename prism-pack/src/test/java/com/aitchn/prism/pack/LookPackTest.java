package com.aitchn.prism.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ComponentMap;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.look.DisplayLighting;
import com.aitchn.prism.core.look.DisplayPlan;
import com.aitchn.prism.core.look.LookAllocator;
import com.aitchn.prism.core.look.LookRequest;
import com.aitchn.prism.core.look.LookTable;
import com.aitchn.prism.core.look.RenderSettings;
import com.aitchn.prism.core.look.ResolvedPool;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LookPackTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private static BlockDefinition block(String key, ComponentMap components) {
        return new BlockDefinition(PrismKey.parse(key), "mod", PrismKey.parse("minecraft:barrier"), Optional.empty(),
                components, List.of());
    }

    private static ComponentMap render(PrismComponents.Render mode) {
        return ComponentMap.builder().put(PrismComponents.RENDER, mode).build();
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

    private static JavaPackBuilder.Resolved resolve(Map<String, byte[]> assets, List<BlockDefinition> blocks) {
        return JavaPackBuilder.resolve(new JavaPackBuilder.Input(Map.of("mod", assets), List.of(), blocks, 46, "t",
                allocate(blocks)));
    }

    private static String text(Map<String, byte[]> files, String path) {
        assertNotNull(files.get(path), path);
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    @Test
    void aBorrowedNoteBlockStateDrawsTheLooksModelAndEveryOtherStateKeepsTheVanillaOne() {
        var resolved = resolve(Map.of("assets/mod/textures/block/ruby.png", PNG),
                List.of(block("mod:ruby", ComponentMap.empty())));
        String file = text(resolved.files(), LookBlockstates.path("minecraft:note_block"));
        // the first look gets the first borrowed state: harp, note 0, powered
        assertTrue(file.contains("\"instrument=harp,note=0,powered=true\": {\n      \"model\": \"mod:block/ruby\""), file);
        assertEquals(1, file.split("mod:block/ruby", -1).length - 1);
        // all 27 instruments x 25 notes x 2 states are listed (a replacement file must name every state)
        assertEquals(1350, file.split("\"instrument=", -1).length - 1);
        assertTrue(file.contains("\"instrument=trumpet_weathered,note=3,powered=false\""));
        assertEquals(1349, file.split("minecraft:block/note_block", -1).length - 1);
        assertTrue(resolved.displayPlans().isEmpty());
        assertFalse(resolved.files().containsKey(LookBlockstates.SHELL_MODEL_PATH));
    }

    @Test
    void aRotatedLookKeepsItsRotationInTheBlockstate() {
        var rotating = block("mod:press", ComponentMap.builder()
                .put(PrismComponents.ROTATION, PrismComponents.Rotation.HORIZONTAL).build());
        var resolved = resolve(Map.of("assets/mod/textures/block/press.png", PNG), List.of(rotating));
        String file = text(resolved.files(), LookBlockstates.path("minecraft:note_block"));
        assertTrue(file.contains("\"y\": 270"), "west facing is y=270");
    }

    @Test
    void aDisplayLookGetsAnInvisibleShellAndOneItemModelPerFace() {
        var resolved = resolve(Map.of("assets/mod/textures/block/ruby.png", PNG),
                List.of(block("mod:ruby", render(PrismComponents.Render.DISPLAY))));
        Map<String, byte[]> files = resolved.files();
        // the look is the shell of its class: no custom model in any blockstate, one transparent cube
        assertTrue(files.containsKey(LookBlockstates.SHELL_MODEL_PATH));
        assertTrue(files.containsKey(LookBlockstates.SHELL_TEXTURE_PATH));
        String shellFamily = text(files, LookBlockstates.path("minecraft:spruce_leaves"));
        assertEquals(1, shellFamily.split("prism:block/shell", -1).length - 1);
        assertFalse(shellFamily.contains("mod:block/ruby"));
        DisplayPlan plan = resolved.displayPlans().get("mod:ruby");
        assertTrue(plan.isSplit());
        assertEquals(6, plan.faces().size());
        for (String face : plan.faces()) {
            assertTrue(face.startsWith("prism:face/"), face);
            String name = face.substring("prism:".length());
            String model = text(files, "assets/prism/models/" + name + ".json");
            assertTrue(model.contains("\"all\": \"mod:block/ruby\""), model);
            assertEquals(1, model.split("\"texture\"", -1).length - 1, "one face per face model");
            assertTrue(text(files, "assets/prism/items/" + name + ".json").contains(face));
        }
        // the six faces are six different models (each has its own direction)
        assertEquals(6, plan.faces().stream().distinct().count());
    }

    @Test
    void faceModelsAreSharedBetweenBlocksWithTheSameTexture() {
        var resolved = resolve(Map.of("assets/mod/textures/block/ruby.png", PNG),
                List.of(block("mod:ruby", render(PrismComponents.Render.DISPLAY)),
                        block("mod:ruby", render(PrismComponents.Render.DISPLAY))));
        assertEquals(1, resolved.displayPlans().size());
        // two different blocks with one texture each use the same six models
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("assets/mod/textures/block/a.png", PNG);
        assets.put("assets/mod/models/block/b.json",
                "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"mod:block/a\"}}"
                        .getBytes(StandardCharsets.UTF_8));
        var both = resolve(assets, List.of(block("mod:a", render(PrismComponents.Render.DISPLAY)),
                block("mod:b", render(PrismComponents.Render.DISPLAY))));
        assertEquals(both.displayPlans().get("mod:a").faces(), both.displayPlans().get("mod:b").faces());
    }

    @Test
    void aModelWithAVanillaParentPrismKnowsIsSplitByItsOwnTextureVariables() {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("assets/mod/models/block/log.json", ("{\"parent\":\"minecraft:block/cube_column\",\"textures\":"
                + "{\"end\":\"mod:block/log_top\",\"side\":\"mod:block/log\"}}").getBytes(StandardCharsets.UTF_8));
        var resolved = resolve(assets, List.of(block("mod:log", render(PrismComponents.Render.DISPLAY))));
        DisplayPlan plan = resolved.displayPlans().get("mod:log");
        assertTrue(plan.isSplit());
        String up = text(resolved.files(), "assets/prism/models/" + plan.faces().get(4).substring("prism:".length()) + ".json");
        assertTrue(up.contains("#end"), up);
        assertTrue(up.contains("\"end\": \"mod:block/log_top\""), up);
        String north = text(resolved.files(),
                "assets/prism/models/" + plan.faces().get(0).substring("prism:".length()) + ".json");
        assertTrue(north.contains("#side"), north);
    }

    @Test
    void aModelWithInnerFacesOrRotatedElementsIsDrawnWhole() {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("assets/mod/models/block/slab.json", ("{\"textures\":{\"all\":\"mod:block/slab\"},\"elements\":["
                + "{\"from\":[0,0,0],\"to\":[16,8,16],\"faces\":{\"up\":{\"texture\":\"#all\"},"
                + "\"down\":{\"texture\":\"#all\"}}}]}").getBytes(StandardCharsets.UTF_8));
        assets.put("assets/mod/models/block/turned.json", ("{\"textures\":{\"all\":\"mod:block/turned\"},\"elements\":["
                + "{\"from\":[0,0,0],\"to\":[16,16,16],\"rotation\":{\"angle\":22.5,\"axis\":\"y\","
                + "\"origin\":[8,8,8]},\"faces\":{\"up\":{\"texture\":\"#all\"}}}]}").getBytes(StandardCharsets.UTF_8));
        assets.put("assets/mod/models/block/odd.json",
                "{\"parent\":\"minecraft:block/stairs\",\"textures\":{\"all\":\"mod:block/odd\"}}"
                        .getBytes(StandardCharsets.UTF_8));
        var resolved = resolve(assets, List.of(block("mod:slab", render(PrismComponents.Render.DISPLAY)),
                block("mod:turned", render(PrismComponents.Render.DISPLAY)),
                block("mod:odd", render(PrismComponents.Render.DISPLAY))));
        for (String name : List.of("slab", "turned", "odd")) {
            DisplayPlan plan = resolved.displayPlans().get("mod:" + name);
            assertFalse(plan.isSplit(), name);
            assertEquals("mod:block/" + name, plan.whole(), name);
            assertTrue(resolved.files().containsKey("assets/mod/items/block/" + name + ".json"));
        }
    }

    // ---------------------------------------------------------------- one entity per block (ADR-0025)

    private static LookTable allocateDisplayFirst(List<BlockDefinition> blocks) {
        List<LookRequest> requests = new ArrayList<>();
        for (BlockDefinition block : blocks) {
            for (Appearances.Look look : Appearances.looks(block)) {
                requests.add(new LookRequest(look.id(), Appearances.physics(block), Appearances.render(block), false));
            }
        }
        return LookAllocator.allocate(ResolvedPool.standard(), requests, false, RenderSettings.DEFAULT);
    }

    private static JavaPackBuilder.Resolved resolveDisplayFirst(Map<String, byte[]> assets,
            List<BlockDefinition> blocks) {
        return JavaPackBuilder.resolve(new JavaPackBuilder.Input(Map.of("mod", assets), List.of(), blocks, 46, "t",
                allocateDisplayFirst(blocks), DisplayLighting.BLOCK));
    }

    @Test
    void underTheDefaultsEveryLookIsOneWholeItemModelAndNothingIsBorrowed() {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("assets/mod/textures/block/ruby.png", PNG);
        assets.put("assets/mod/textures/block/press.png", PNG);
        var rotating = block("mod:press", ComponentMap.builder()
                .put(PrismComponents.ROTATION, PrismComponents.Rotation.HORIZONTAL).build());
        var resolved = resolveDisplayFirst(assets, List.of(block("mod:ruby", ComponentMap.empty()), rotating));
        assertEquals(5, resolved.displayPlans().size(), "ruby and four facings of the press");
        for (DisplayPlan plan : resolved.displayPlans().values()) {
            assertFalse(plan.isSplit());
            assertFalse(plan.whole().isEmpty());
            assertTrue(resolved.files().containsKey("assets/" + plan.whole().replace(":", "/items/") + ".json"),
                    plan.whole());
        }
        assertEquals("mod:block/ruby", resolved.displayPlans().get("mod:ruby").whole());
        // the clear shell is vanilla's barrier: no borrowed family file, no shell model, no face models
        assertFalse(resolved.files().containsKey(LookBlockstates.path("minecraft:note_block")));
        assertFalse(resolved.files().containsKey(LookBlockstates.SHELL_MODEL_PATH));
        assertTrue(resolved.files().keySet().stream().noneMatch(path -> path.startsWith("assets/prism/models/face/")));
    }

    @Test
    void passableDisplayLooksStillGetTheirShellFile() {
        var flower = block("mod:flower", ComponentMap.builder()
                .put(PrismComponents.PASSABLE, new PrismComponents.Passable(true)).build());
        var resolved = resolveDisplayFirst(Map.of("assets/mod/textures/block/flower.png", PNG), List.of(flower));
        assertTrue(resolved.files().containsKey(LookBlockstates.SHELL_MODEL_PATH));
        assertTrue(resolved.files().containsKey(LookBlockstates.path("minecraft:tripwire")));
        assertEquals("mod:block/flower", resolved.displayPlans().get("mod:flower").whole());
    }

    @Test
    void aModelWithManyFacesIsReportedOnce() {
        StringBuilder elements = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i > 0) {
                elements.append(',');
            }
            elements.append("{\"from\":[0,").append(i).append(",0],\"to\":[16,").append(i + 1).append(",16],\"faces\":{")
                    .append("\"up\":{\"texture\":\"#all\"},\"down\":{\"texture\":\"#all\"},\"north\":{\"texture\":\"#all\"},")
                    .append("\"south\":{\"texture\":\"#all\"},\"east\":{\"texture\":\"#all\"},\"west\":{\"texture\":\"#all\"}}}");
        }
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("assets/mod/models/block/heavy.json", ("{\"textures\":{\"all\":\"mod:block/heavy\"},\"elements\":["
                + elements + "]}").getBytes(StandardCharsets.UTF_8));
        var heavy = block("mod:heavy", ComponentMap.builder()
                .put(PrismComponents.ROTATION, PrismComponents.Rotation.HORIZONTAL).build());
        var resolved = resolveDisplayFirst(assets, List.of(heavy, block("mod:light", ComponentMap.empty())));
        List<String> reports = resolved.warnings().stream().filter(w -> w.contains("faces;")).toList();
        assertEquals(1, reports.size(), resolved.warnings().toString());
        assertTrue(reports.getFirst().contains("5 elements and 30 faces"), reports.getFirst());
    }

    @Test
    void thePackIsTheSameBytesForTheSameContent() {
        List<BlockDefinition> blocks = new ArrayList<>();
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (int i = 0; i < 200; i++) {
            blocks.add(block("mod:b" + i, render(i % 3 == 0 ? PrismComponents.Render.DISPLAY : PrismComponents.Render.AUTO)));
            assets.put("assets/mod/textures/block/b" + i + ".png", PNG);
        }
        var input = new JavaPackBuilder.Input(Map.of("mod", assets), List.of(), blocks, 46, "t", allocate(blocks));
        List<BlockDefinition> reversed = new ArrayList<>(blocks);
        java.util.Collections.reverse(reversed);
        var shuffled = new JavaPackBuilder.Input(Map.of("mod", assets), List.of(), reversed, 46, "t", allocate(reversed));
        assertEquals(JavaPackBuilder.build(input).sha1(), JavaPackBuilder.build(shuffled).sha1());
        assertEquals(JavaPackBuilder.build(input).sha1(), JavaPackBuilder.build(input).sha1());
    }

    @Test
    void tenThousandBlocksBuildAPackAndEveryDisplayLookHasAPlan() {
        List<BlockDefinition> blocks = new ArrayList<>();
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (int i = 0; i < 10_000; i++) {
            blocks.add(block("synth:b" + i, ComponentMap.empty()));
            assets.put("assets/synth/textures/block/b" + i + ".png", PNG);
        }
        LookTable table = allocate(blocks);
        var result = JavaPackBuilder.build(new JavaPackBuilder.Input(Map.of("synth", assets), List.of(), blocks, 46,
                "t", table));
        assertEquals(10_000, table.placements().size());
        assertTrue(table.unplaced().isEmpty());
        assertEquals(table.count(LookTable.Tier.DISPLAY), result.displayPlans().size());
        assertTrue(table.count(LookTable.Tier.STATE) > 700);
        assertTrue(result.displayPlans().values().stream().allMatch(DisplayPlan::isSplit));
        assertTrue(result.fileCount() > 10_000);
        assertArrayEquals(result.zip(), JavaPackBuilder.build(new JavaPackBuilder.Input(Map.of("synth", assets), List.of(),
                blocks, 46, "t", table)).zip());
    }
}
