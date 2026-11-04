package com.aitchn.prism.pack;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.core.content.YamlFiles;
import com.aitchn.prism.pack.json.JsonWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Compiles the Bedrock side of the content: a {@code .mcpack} that carries the item icons, and the Geyser custom
 * item mappings (format 2) that connect a Java {@code item_model} to a Bedrock custom item.
 *
 * <p>Bedrock gets a flat icon per item. Anything richer (3D item models, dynamic states) is a documented
 * degradation; see docs/adr/0006-bedrock.md. The output is deterministic, so the SHA-1 identifies the content.
 */
public final class BedrockPackBuilder {

    /** Input for one build. {@code assets} maps mod id to its pack files, as for the Java pack. */
    public record Input(
            @NotNull Map<String, Map<String, byte[]>> assets,
            @NotNull Collection<ItemDefinition> items,
            @NotNull String description) {}

    /**
     * @param mcpack           the Bedrock resource pack
     * @param mappings         Geyser custom item mappings, JSON
     * @param itemsWithIcon    items that got a Bedrock custom item; the others keep their vanilla Bedrock look
     */
    public record Result(
            byte @NotNull [] mcpack,
            @NotNull String sha1,
            @NotNull String mappings,
            @NotNull List<String> warnings,
            @NotNull Set<PrismKey> itemsWithIcon) {}

    private static final UUID HEADER = UUID.nameUUIDFromBytes("prism:bedrock:header".getBytes(StandardCharsets.UTF_8));
    private static final UUID MODULE = UUID.nameUUIDFromBytes("prism:bedrock:module".getBytes(StandardCharsets.UTF_8));

    private BedrockPackBuilder() {}

    public static @NotNull Result build(@NotNull Input input) {
        List<String> warnings = new ArrayList<>();
        Map<String, byte[]> sources = new LinkedHashMap<>();
        input.assets().values().forEach(sources::putAll);

        Map<String, byte[]> files = new TreeMap<>();
        Map<String, Object> textureData = new TreeMap<>();
        Map<String, List<Object>> byBase = new TreeMap<>();
        Set<PrismKey> done = new LinkedHashSet<>();

        for (ItemDefinition item : input.items()) {
            PrismKey model = item.components().get(PrismComponents.MODEL).map(PrismComponents.Model::model)
                    .orElseGet(() -> new PrismKey(item.key().namespace(), "item/" + item.key().value()));
            String texturePath = textureFor(sources, model);
            byte[] png = sources.get(texturePath);
            if (png == null) {
                warnings.add("bedrock " + item.key() + ": no texture " + texturePath
                        + "; it keeps the vanilla Bedrock look of " + item.base());
                continue;
            }
            String icon = item.key().namespace() + "_" + item.key().value().replace('/', '_');
            String packTexture = "textures/prism/" + item.key().namespace() + "/"
                    + item.key().value().replace('/', '_');
            files.put(packTexture + ".png", png);
            textureData.put(icon, Map.of("textures", packTexture));

            Map<String, Object> options = new LinkedHashMap<>();
            options.put("icon", icon);
            options.put("allow_offhand", true);
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("type", "definition");
            definition.put("model", item.key().toString());
            definition.put("bedrock_identifier", item.key().toString());
            item.components().get(PrismComponents.DISPLAY_NAME)
                    .ifPresent(name -> definition.put("display_name", plain(name.miniMessage())));
            definition.put("bedrock_options", options);
            byBase.computeIfAbsent(item.base().toString(), k -> new ArrayList<>()).add(definition);
            done.add(item.key());
        }

        Map<String, Object> itemTexture = new LinkedHashMap<>();
        itemTexture.put("resource_pack_name", "prism");
        itemTexture.put("texture_name", "atlas.items");
        itemTexture.put("texture_data", textureData);
        files.put("textures/item_texture.json", JsonWriter.write(itemTexture).getBytes(StandardCharsets.UTF_8));
        files.put("manifest.json", manifest(input.description()).getBytes(StandardCharsets.UTF_8));

        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("format_version", 2);
        mappings.put("items", new LinkedHashMap<String, Object>(byBase));
        byte[] zip = JavaPackBuilder.zip(files);
        return new Result(zip, JavaPackBuilder.sha1(zip), JsonWriter.write(mappings), List.copyOf(warnings),
                Set.copyOf(done));
    }

    /** The texture an item icon is cut from: layer0 of its model JSON when present, else the model's own name. */
    private static String textureFor(Map<String, byte[]> sources, PrismKey model) {
        String modelPath = "assets/" + model.namespace() + "/models/" + model.value() + ".json";
        byte[] json = sources.get(modelPath);
        if (json != null) {
            try {
                Object parsed = YamlFiles.parse(modelPath, new String(json, StandardCharsets.UTF_8));
                if (parsed instanceof Map<?, ?> root && root.get("textures") instanceof Map<?, ?> textures
                        && textures.get("layer0") instanceof String layer) {
                    PrismKey key = layer.contains(":") ? PrismKey.parse(layer)
                            : new PrismKey(model.namespace(), layer);
                    return "assets/" + key.namespace() + "/textures/" + key.value() + ".png";
                }
            } catch (RuntimeException | com.aitchn.prism.api.content.ContentException ignored) {
                // fall through to the default texture; the Java pack reports malformed models
            }
        }
        return "assets/" + model.namespace() + "/textures/" + model.value() + ".png";
    }

    /** Strips MiniMessage tags; Bedrock names are plain text. */
    static String plain(String miniMessage) {
        return miniMessage.replaceAll("<[^<>]*>", "");
    }

    private static String manifest(String description) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("name", "Prism");
        header.put("description", description);
        header.put("uuid", HEADER.toString());
        header.put("version", List.of(1, 0, 0));
        header.put("min_engine_version", List.of(1, 21, 0));
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("type", "resources");
        module.put("uuid", MODULE.toString());
        module.put("version", List.of(1, 0, 0));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format_version", 2);
        root.put("header", header);
        root.put("modules", List.of(module));
        return JsonWriter.write(root);
    }
}
