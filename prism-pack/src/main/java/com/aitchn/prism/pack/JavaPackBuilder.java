package com.aitchn.prism.pack;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.pack.json.JsonWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jetbrains.annotations.NotNull;

/**
 * Compiles the Java resource pack: merges every mod's {@code assets/}, generates the item definitions
 * ({@code assets/<ns>/items/*.json}) that connect an {@code item_model} component to a model, generates trivial
 * models from textures, and writes a byte-for-byte deterministic zip. Same input, same SHA-1.
 */
public final class JavaPackBuilder {

    /** Input for one build. {@code assets} maps mod id to its pack files. */
    public record Input(
            @NotNull Map<String, Map<String, byte[]>> assets,
            @NotNull Collection<ItemDefinition> items,
            @NotNull Collection<BlockDefinition> blocks,
            int packFormat,
            @NotNull String description) {}

    /**
     * @param itemsWithAppearance  items that got an item definition; only these may carry {@code item_model}
     * @param blocksWithAppearance blocks that got one, drawn with item model {@code <ns>:block/<value>}
     */
    public record Resolved(
            @NotNull Map<String, byte[]> files,
            @NotNull List<String> warnings,
            @NotNull Set<PrismKey> itemsWithAppearance,
            @NotNull Set<PrismKey> blocksWithAppearance) {}

    public record Result(
            byte @NotNull [] zip,
            @NotNull String sha1,
            @NotNull List<String> warnings,
            int fileCount,
            @NotNull Set<PrismKey> itemsWithAppearance,
            @NotNull Set<PrismKey> blocksWithAppearance) {}

    private JavaPackBuilder() {}

    /** Zips {@link #resolve}'s files. Cheap to call resolve alone when only the appearance sets are needed. */
    public static @NotNull Result build(@NotNull Input input) {
        Resolved resolved = resolve(input);
        byte[] zip = zip(resolved.files());
        return new Result(zip, sha1(zip), resolved.warnings(), resolved.files().size(),
                resolved.itemsWithAppearance(), resolved.blocksWithAppearance());
    }

    /** Merges assets and generates definitions and models, without compressing anything. */
    public static @NotNull Resolved resolve(@NotNull Input input) {
        List<String> warnings = new ArrayList<>();
        Map<String, byte[]> files = new TreeMap<>();
        Map<String, String> origin = new LinkedHashMap<>();
        input.assets().forEach((mod, modFiles) -> modFiles.forEach((path, data) -> {
            if (!path.startsWith("assets/")) {
                warnings.add(mod + ": ignored " + path + " (pack files must live under assets/)");
                return;
            }
            if (path.equals("pack.mcmeta")) {
                return;
            }
            String previous = origin.put(path, mod);
            if (previous != null && !previous.equals(mod)) {
                warnings.add(path + " is provided by both " + previous + " and " + mod + "; " + mod + " wins");
            }
            files.put(path, data);
        }));

        Set<PrismKey> itemsOk = new LinkedHashSet<>();
        Set<PrismKey> blocksOk = new LinkedHashSet<>();
        for (ItemDefinition item : input.items()) {
            PrismKey model = item.components().get(PrismComponents.MODEL).map(PrismComponents.Model::model)
                    .orElseGet(() -> new PrismKey(item.key().namespace(), "item/" + item.key().value()));
            String definitionPath = "assets/" + item.key().namespace() + "/items/" + item.key().value() + ".json";
            if (generate(files, warnings, item.key().toString(), definitionPath, model, "minecraft:item/generated", "layer0",
                    "item")) {
                itemsOk.add(item.key());
            }
        }
        for (BlockDefinition block : input.blocks()) {
            PrismKey model = block.components().get(PrismComponents.MODEL).map(PrismComponents.Model::model)
                    .orElseGet(() -> new PrismKey(block.key().namespace(), "block/" + block.key().value()));
            String definitionPath =
                    "assets/" + block.key().namespace() + "/items/block/" + block.key().value() + ".json";
            if (generate(files, warnings, "block " + block.key(), definitionPath, model, "minecraft:block/cube_all",
                    "all", "block")) {
                blocksOk.add(block.key());
            }
        }

        files.put("pack.mcmeta", mcmeta(input).getBytes(StandardCharsets.UTF_8));
        return new Resolved(files, List.copyOf(warnings), Set.copyOf(itemsOk), Set.copyOf(blocksOk));
    }

    /**
     * Writes the item definition for {@code model} unless the mod already shipped one. A missing model JSON is
     * generated from a texture of the same name; with neither, nothing is written and the owner is warned.
     */
    private static boolean generate(
            Map<String, byte[]> files,
            List<String> warnings,
            String label,
            String definitionPath,
            PrismKey model,
            String parent,
            String textureLayer,
            String kind) {
        if (files.containsKey(definitionPath)) {
            return true;
        }
        String modelPath = "assets/" + model.namespace() + "/models/" + model.value() + ".json";
        String texturePath = "assets/" + model.namespace() + "/textures/" + model.value() + ".png";
        if (!files.containsKey(modelPath)) {
            if (!files.containsKey(texturePath)) {
                warnings.add(label + ": no model " + modelPath + " and no texture " + texturePath
                        + "; it keeps the vanilla look of its base " + kind);
                return false;
            }
            Map<String, Object> textures = new LinkedHashMap<>();
            textures.put(textureLayer, model.toString());
            Map<String, Object> generated = new LinkedHashMap<>();
            generated.put("parent", parent);
            generated.put("textures", textures);
            files.put(modelPath, JsonWriter.write(generated).getBytes(StandardCharsets.UTF_8));
        }
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("type", "minecraft:model");
        inner.put("model", model.toString());
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("model", inner);
        files.put(definitionPath, JsonWriter.write(definition).getBytes(StandardCharsets.UTF_8));
        return true;
    }

    private static String mcmeta(Input input) {
        Map<String, Object> supported = new LinkedHashMap<>();
        supported.put("min_inclusive", PackFormats.MINIMUM);
        supported.put("max_inclusive", 9999);
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("pack_format", input.packFormat());
        pack.put("supported_formats", supported);
        pack.put("min_format", PackFormats.MINIMUM);
        pack.put("max_format", 9999);
        pack.put("description", input.description());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("pack", pack);
        return JsonWriter.write(root);
    }

    private static byte[] zip(Map<String, byte[]> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.setLevel(Deflater.BEST_COMPRESSION);
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
                out.putNextEntry(entry);
                out.write(file.getValue());
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
