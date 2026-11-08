package com.aitchn.prism.pack;

import com.aitchn.prism.core.bedrock.BedrockShapes;
import com.aitchn.prism.core.bedrock.SyntheticBlock;
import com.aitchn.prism.core.bedrock.SyntheticIds;
import com.aitchn.prism.pack.json.JsonWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * The synthetic Bedrock blocks as JSON (format 1), written next to the Bedrock pack for a Geyser extension on a proxy.
 * Geyser's own JSON mappings cannot name a state above the vanilla range, so a Geyser that does not run on the Prism
 * server needs an extension that reads this file and registers the same overrides the in-process registration does
 * (see docs/adr/0024-bedrock-synthetic-blocks.md for the contract). Box coordinates are exactly what Geyser's API takes:
 * collision and selection in Bedrock's frame (pixels, x and z centred, x mirrored), {@code java_collision} in block
 * units (middle and size) for the Java-side collision table.
 */
final class SyntheticExport {

    static final int FORMAT = 1;

    private SyntheticExport() {}

    static @NotNull String json(@NotNull SyntheticIds.Plan plan, @NotNull List<SyntheticBlock> blocks) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format_version", FORMAT);
        root.put("base", plan.base());
        // Geyser's registry ends at the highest id actually registered (a look without a texture gets none).
        int stateCount = blocks.stream().mapToInt(SyntheticBlock::javaId).max().orElse(plan.base() - 1) + 1;
        root.put("state_count", stateCount);
        root.put("global_bits", SyntheticIds.bitsFor(stateCount));
        root.put("piston_behavior", SyntheticBlock.PISTON_BEHAVIOR);
        List<Object> list = new ArrayList<>();
        for (SyntheticBlock block : blocks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("java_id", block.javaId());
            entry.put("java_identifier", block.javaIdentifier());
            entry.put("name", block.name());
            entry.put("look", block.lookId());
            entry.put("display_name", block.displayName());
            if (block.geometry() != null) {
                entry.put("geometry", block.geometry());
            }
            Map<String, Object> materials = new LinkedHashMap<>();
            block.materials().forEach((face, material) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("texture", material.texture());
                m.put("render_method", material.renderMethod());
                materials.put(face, m);
            });
            entry.put("material_instances", materials);
            entry.put("collision_box", boxes(BedrockShapes.collisionForBedrock(block.collision())));
            entry.put("selection_box", box(BedrockShapes.selection(block.selection())));
            List<Object> javaCollision = new ArrayList<>();
            for (BedrockShapes.Box box : block.collision()) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("middle", List.of(box.middleX(), box.middleY(), box.middleZ()));
                b.put("size", List.of(box.sizeX(), box.sizeY(), box.sizeZ()));
                javaCollision.add(b);
            }
            entry.put("java_collision", javaCollision);
            if (block.rotationX() != 0 || block.rotationY() != 0) {
                entry.put("transformation", Map.of("rotation", List.of(block.rotationX(), block.rotationY(), 0)));
            }
            entry.put("destroy_time", block.destroyTime());
            entry.put("can_break_with_hand", block.canBreakWithHand());
            list.add(entry);
        }
        root.put("blocks", list);
        return JsonWriter.write(root);
    }

    private static List<Object> boxes(List<BedrockShapes.BedrockBox> boxes) {
        List<Object> result = new ArrayList<>();
        boxes.forEach(box -> result.add(box(box)));
        return result;
    }

    private static Map<String, Object> box(BedrockShapes.BedrockBox box) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("origin", List.of(box.originX(), box.originY(), box.originZ()));
        result.put("size", List.of(box.sizeX(), box.sizeY(), box.sizeZ()));
        return result;
    }
}
