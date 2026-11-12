package com.aitchn.prism.pack;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.core.content.YamlFiles;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.Nullable;

/**
 * Constant tints ({@code prism:tint}) for Bedrock, which has no tint index on custom block or item textures: the colour
 * is multiplied into a copy of each texture that the Java model draws with a {@code tintindex} (ADR-0027). The Java
 * client multiplies the texel by the tint colour, so the copy looks the same as long as the texture is used only by
 * tinted faces, or only by faces of the same tint index.
 */
final class TintBake {

    /** Vanilla parents whose faces are tinted: parent to the texture variable drawn with tint index 0. */
    private static final Map<String, String> TINTED_PARENTS = Map.of(
            "minecraft:block/leaves", "all",
            "minecraft:block/tinted_cross", "cross",
            "minecraft:block/tinted_flower_pot_cross", "plant");

    private TintBake() {}

    /**
     * Texture path ({@code assets/<ns>/textures/....png}) to the tint index the model draws it with, for the textures of
     * faces with a {@code tintindex}. Walks the model's parent chain in the mods' assets: textures merge (children win),
     * elements come from the nearest model that has some. A texture used with two tint indexes keeps the first.
     */
    static Map<String, Integer> tintIndexes(Map<String, byte[]> sources, PrismKey model) {
        Map<String, String> textures = new LinkedHashMap<>();
        List<Map<?, ?>> chain = new ArrayList<>();
        List<?> elements = null;
        String tintedParentVariable = null;
        PrismKey current = model;
        for (int depth = 0; depth < 8 && current != null; depth++) {
            byte[] json = sources.get("assets/" + current.namespace() + "/models/" + current.value() + ".json");
            if (json == null) {
                tintedParentVariable = TINTED_PARENTS.get(current.toString());
                break;
            }
            Object parsed;
            try {
                parsed = YamlFiles.parse(current.toString(), new String(json, StandardCharsets.UTF_8));
            } catch (ContentException | RuntimeException e) {
                break;
            }
            if (!(parsed instanceof Map<?, ?> root)) {
                break;
            }
            chain.add(root);
            if (elements == null && root.get("elements") instanceof List<?> list) {
                elements = list;
            }
            current = root.get("parent") instanceof String parent ? parse(parent) : null;
        }
        for (int i = chain.size() - 1; i >= 0; i--) { // parents first, children override
            if (chain.get(i).get("textures") instanceof Map<?, ?> map) {
                map.forEach((k, v) -> textures.put(String.valueOf(k), String.valueOf(v)));
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        if (elements != null) {
            for (Object element : elements) {
                if (!(element instanceof Map<?, ?> e) || !(e.get("faces") instanceof Map<?, ?> faces)) {
                    continue;
                }
                for (Object face : faces.values()) {
                    if (face instanceof Map<?, ?> f && f.get("tintindex") instanceof Number index
                            && f.get("texture") instanceof String texture) {
                        String path = resolve(sources, textures, texture);
                        if (path != null) {
                            result.putIfAbsent(path, index.intValue());
                        }
                    }
                }
            }
        } else if (tintedParentVariable != null) {
            String path = resolve(sources, textures, "#" + tintedParentVariable);
            if (path != null) {
                result.put(path, 0);
            }
        }
        return result;
    }

    private static @Nullable PrismKey parse(String text) {
        try {
            return text.contains(":") ? PrismKey.parse(text) : new PrismKey("minecraft", text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code #variable} or a texture id, to the path of a texture the mods ship; null if none. */
    private static @Nullable String resolve(Map<String, byte[]> sources, Map<String, String> textures, String texture) {
        String value = texture;
        for (int depth = 0; value != null && value.startsWith("#") && depth < 8; depth++) {
            value = textures.get(value.substring(1));
        }
        if (value == null || value.startsWith("#")) {
            return null;
        }
        PrismKey key = parse(value);
        if (key == null) {
            return null;
        }
        String path = "assets/" + key.namespace() + "/textures/" + key.value() + ".png";
        return sources.containsKey(path) ? path : null;
    }

    /**
     * {@code png} with every texel multiplied by {@code rgb} ({@code 0xRRGGBB}), alpha kept, as a PNG; null if the bytes
     * are not an image ImageIO can read.
     */
    static byte @Nullable [] multiply(byte[] png, int rgb) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
            if (source == null) {
                return null;
            }
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int argb = source.getRGB(x, y);
                    int tr = ((argb >> 16) & 0xFF) * r / 255;
                    int tg = ((argb >> 8) & 0xFF) * g / 255;
                    int tb = (argb & 0xFF) * b / 255;
                    out.setRGB(x, y, (argb & 0xFF000000) | (tr << 16) | (tg << 8) | tb);
                }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.setUseCache(false);
            if (!ImageIO.write(out, "png", bytes)) {
                return null;
            }
            return bytes.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
