package com.aitchn.prism.core.content;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.Behavior;
import com.aitchn.prism.api.content.BehaviorType;
import com.aitchn.prism.api.content.ComponentMap;
import com.aitchn.prism.api.content.ComponentType;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.recipe.PrismRecipe;
import com.aitchn.prism.api.recipe.RecipeType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/**
 * Reads {@code <root>/items/**.yml}, {@code <root>/blocks/**.yml} and {@code <root>/recipes/**.yml}. The file path
 * below the folder (without extension) becomes the key value in the mod's namespace. All problems are collected, not just the first, so a
 * creator fixes a file in one pass.
 */
public final class ContentLoader {

    /** Everything one mod declared. */
    public record Loaded(Map<PrismKey, ItemDefinition> items, Map<PrismKey, BlockDefinition> blocks,
            Map<PrismKey, PrismRecipe<?>> recipes) {

        public Loaded(Map<PrismKey, ItemDefinition> items, Map<PrismKey, BlockDefinition> blocks) {
            this(items, blocks, Map.of());
        }
    }

    private static final Set<String> ITEM_KEYS = Set.of("base", "components", "behaviors");
    private static final Set<String> BLOCK_KEYS = Set.of("base", "drop", "components", "behaviors");

    private final TypeLookup types;
    private final Predicate<PrismKey> baseItemExists;
    private final Predicate<PrismKey> carrierBlockExists;

    public ContentLoader(
            @NotNull TypeLookup types,
            @NotNull Predicate<PrismKey> baseItemExists,
            @NotNull Predicate<PrismKey> carrierBlockExists) {
        this.types = types;
        this.baseItemExists = baseItemExists;
        this.carrierBlockExists = carrierBlockExists;
    }

    public @NotNull Loaded load(
            @NotNull ContentSource source,
            @NotNull String contentRoot,
            @NotNull String namespace,
            @NotNull String owner,
            @NotNull List<Problem> problems) {
        Map<PrismKey, ItemDefinition> items = new LinkedHashMap<>();
        Map<PrismKey, BlockDefinition> blocks = new LinkedHashMap<>();
        for (String file : yamlFiles(source, contentRoot + "/items", problems)) {
            read(file, contentRoot + "/items", namespace, problems, key -> items.put(key, loadItem(source, file, key, owner)));
        }
        for (String file : yamlFiles(source, contentRoot + "/blocks", problems)) {
            read(file, contentRoot + "/blocks", namespace, problems, key -> blocks.put(key, loadBlock(source, file, key, owner)));
        }
        Map<PrismKey, PrismRecipe<?>> recipes = new LinkedHashMap<>();
        for (String file : yamlFiles(source, contentRoot + "/recipes", problems)) {
            read(file, contentRoot + "/recipes", namespace, problems,
                    key -> recipes.put(key, loadRecipe(source, file, key, owner)));
        }
        return new Loaded(items, blocks, recipes);
    }

    private PrismRecipe<?> loadRecipe(ContentSource source, String file, PrismKey id, String owner)
            throws ContentException, IOException {
        MapConfigSection section = MapConfigSection.of("", YamlFiles.parse(file, source.readText(file)));
        PrismKey typeKey = parseKey("type", section.requireString("type"), id.namespace());
        RecipeType<?> type = types.recipe(typeKey).orElseThrow(() -> new ContentException(
                "type", "unknown recipe type " + typeKey + " (is the mod that provides it installed?)"));
        return decodeRecipe(id, owner, type, section.without("type"));
    }

    private static <R> PrismRecipe<R> decodeRecipe(PrismKey id, String owner, RecipeType<R> type, ConfigSection section)
            throws ContentException {
        R value;
        try {
            value = type.decoder().decode(section, id.namespace());
        } catch (RuntimeException e) {
            throw new ContentException("", "recipe type " + type.key() + " failed: " + e);
        }
        if (!type.valueType().isInstance(value)) {
            throw new ContentException("", "recipe type " + type.key() + " returned " + value.getClass().getName());
        }
        return new PrismRecipe<>(id, owner, type, value);
    }

    private interface Reader {
        void read(PrismKey key) throws ContentException, IOException;
    }

    private void read(String file, String dir, String namespace, List<Problem> problems, Reader reader) {
        try {
            reader.read(keyFor(dir, file, namespace));
        } catch (ContentException e) {
            problems.add(new Problem(file, e.getMessage()));
        } catch (IOException e) {
            problems.add(new Problem(file, "cannot read file: " + e.getMessage()));
        } catch (RuntimeException e) {
            problems.add(new Problem(file, "unexpected " + e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private static List<String> yamlFiles(ContentSource source, String dir, List<Problem> problems) {
        try {
            return source.list(dir).stream().filter(f -> f.endsWith(".yml") || f.endsWith(".yaml")).toList();
        } catch (IOException e) {
            problems.add(new Problem(dir, "cannot list content: " + e.getMessage()));
            return List.of();
        }
    }

    private static PrismKey keyFor(String dir, String file, String namespace) throws ContentException {
        String relative = file.substring(dir.length() + 1);
        String value = relative.substring(0, relative.lastIndexOf('.'));
        try {
            return new PrismKey(namespace, value);
        } catch (IllegalArgumentException e) {
            throw new ContentException("", "file name is not a valid key: " + e.getMessage());
        }
    }

    private ItemDefinition loadItem(ContentSource source, String file, PrismKey key, String owner)
            throws ContentException, IOException {
        MapConfigSection section = MapConfigSection.of("", YamlFiles.parse(file, source.readText(file)));
        section.rejectUnknownKeys(ITEM_KEYS);
        PrismKey base = parseVanilla(section, "base", baseItemExists, "item");
        return new ItemDefinition(
                key, owner, base, loadComponents(section, key.namespace()), loadBehaviors(section, key.namespace()));
    }

    private BlockDefinition loadBlock(ContentSource source, String file, PrismKey key, String owner)
            throws ContentException, IOException {
        MapConfigSection section = MapConfigSection.of("", YamlFiles.parse(file, source.readText(file)));
        section.rejectUnknownKeys(BLOCK_KEYS);
        PrismKey carrier = parseVanilla(section, "base", carrierBlockExists, "block");
        Optional<PrismKey> drop = Optional.empty();
        if (section.has("drop")) {
            try {
                drop = Optional.of(PrismKey.parse(section.requireString("drop"), key.namespace()));
            } catch (IllegalArgumentException e) {
                throw new ContentException("drop", e.getMessage());
            }
        }
        return new BlockDefinition(key, owner, carrier, drop,
                loadComponents(section, key.namespace()), loadBehaviors(section, key.namespace()));
    }

    private static PrismKey parseVanilla(
            ConfigSection section, String field, Predicate<PrismKey> exists, String kind) throws ContentException {
        PrismKey key;
        try {
            key = PrismKey.parse(section.requireString(field), PrismKey.MINECRAFT_NAMESPACE);
        } catch (IllegalArgumentException e) {
            throw new ContentException(field, e.getMessage());
        }
        if (!exists.test(key)) {
            throw new ContentException(field, "unknown vanilla " + kind + " " + key);
        }
        return key;
    }

    private ComponentMap loadComponents(MapConfigSection owner, String namespace) throws ContentException {
        ComponentMap.Builder builder = ComponentMap.builder();
        ConfigSection section = owner.getSection("components").orElse(null);
        if (section == null) {
            return builder.build();
        }
        for (String rawKey : section.keys()) {
            String path = "components." + rawKey;
            PrismKey typeKey = parseKey(path, rawKey, namespace);
            ComponentType<?> type = types.component(typeKey).orElseThrow(() -> new ContentException(
                    path, "unknown component type " + typeKey + " (is the mod that provides it installed?)"));
            Object node = ((MapConfigSection) section).value(rawKey);
            MapConfigSection data = node instanceof Map<?, ?> ? MapConfigSection.of(path, node)
                    : new MapConfigSection(path, Map.of("value", node == null ? "" : node));
            builder = put(builder, type, data, path);
        }
        return builder.build();
    }

    private static <T> ComponentMap.Builder put(
            ComponentMap.Builder builder, ComponentType<T> type, ConfigSection data, String path)
            throws ContentException {
        try {
            return builder.put(type, type.decoder().decode(data));
        } catch (RuntimeException e) {
            throw new ContentException(path, "decoder " + type.key() + " failed: " + e);
        }
    }

    private List<Behavior> loadBehaviors(MapConfigSection owner, String namespace) throws ContentException {
        List<Behavior> behaviors = new ArrayList<>();
        for (ConfigSection entry : owner.getSectionList("behaviors")) {
            PrismKey typeKey = parseKey(entry.path() + ".type", entry.requireString("type"), namespace);
            BehaviorType<? extends Behavior> type = types.behavior(typeKey).orElseThrow(() -> new ContentException(
                    entry.path() + ".type",
                    "unknown behavior type " + typeKey + " (is the mod that provides it installed?)"));
            ConfigSection withoutType = ((MapConfigSection) entry).without("type");
            try {
                behaviors.add(type.factory().create(withoutType));
            } catch (RuntimeException e) {
                throw new ContentException(entry.path(), "behavior " + typeKey + " failed: " + e);
            }
        }
        return behaviors;
    }

    private static PrismKey parseKey(String path, String text, String namespace) throws ContentException {
        try {
            return PrismKey.parse(text, namespace);
        } catch (IllegalArgumentException e) {
            throw new ContentException(path, e.getMessage());
        }
    }
}
