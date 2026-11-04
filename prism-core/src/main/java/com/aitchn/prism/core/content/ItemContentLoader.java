package com.aitchn.prism.core.content;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.Behavior;
import com.aitchn.prism.api.content.BehaviorType;
import com.aitchn.prism.api.content.ComponentMap;
import com.aitchn.prism.api.content.ComponentType;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.api.content.ItemDefinition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/**
 * Reads {@code <root>/items/**.yml}. The file path below {@code items/} (without extension) becomes the key value in
 * the mod's namespace. All problems are collected, not just the first, so a creator fixes a file in one pass.
 */
public final class ItemContentLoader {

    private static final Set<String> ITEM_KEYS = Set.of("base", "components", "behaviors");

    private final TypeLookup types;
    private final Predicate<PrismKey> baseItemExists;

    public ItemContentLoader(@NotNull TypeLookup types, @NotNull Predicate<PrismKey> baseItemExists) {
        this.types = types;
        this.baseItemExists = baseItemExists;
    }

    public @NotNull Map<PrismKey, ItemDefinition> load(
            @NotNull ContentSource source,
            @NotNull String contentRoot,
            @NotNull String namespace,
            @NotNull String owner,
            @NotNull List<Problem> problems) {
        String itemsDir = contentRoot + "/items";
        Map<PrismKey, ItemDefinition> items = new LinkedHashMap<>();
        List<String> files;
        try {
            files = source.list(itemsDir);
        } catch (IOException e) {
            problems.add(new Problem(itemsDir, "cannot list content: " + e.getMessage()));
            return items;
        }
        for (String file : files) {
            if (!file.endsWith(".yml") && !file.endsWith(".yaml")) {
                continue;
            }
            try {
                PrismKey key = keyFor(itemsDir, file, namespace);
                items.put(key, loadItem(source, file, key, owner));
            } catch (ContentException e) {
                problems.add(new Problem(file, e.getMessage()));
            } catch (IOException e) {
                problems.add(new Problem(file, "cannot read file: " + e.getMessage()));
            } catch (RuntimeException e) {
                problems.add(new Problem(file, "unexpected " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return items;
    }

    private static PrismKey keyFor(String itemsDir, String file, String namespace) throws ContentException {
        String relative = file.substring(itemsDir.length() + 1);
        String value = relative.substring(0, relative.lastIndexOf('.'));
        try {
            return new PrismKey(namespace, value);
        } catch (IllegalArgumentException e) {
            throw new ContentException("", "file name is not a valid key: " + e.getMessage());
        }
    }

    private ItemDefinition loadItem(ContentSource source, String file, PrismKey key, String owner)
            throws ContentException, IOException {
        Object root = YamlFiles.parse(file, source.readText(file));
        MapConfigSection section = MapConfigSection.of("", root);
        section.rejectUnknownKeys(ITEM_KEYS);

        PrismKey base;
        try {
            base = PrismKey.parse(section.requireString("base"), PrismKey.MINECRAFT_NAMESPACE);
        } catch (IllegalArgumentException e) {
            throw new ContentException("base", e.getMessage());
        }
        if (!baseItemExists.test(base)) {
            throw new ContentException("base", "unknown vanilla item " + base);
        }

        ComponentMap components = loadComponents(section, key.namespace());
        List<Behavior> behaviors = loadBehaviors(section, key.namespace());
        return new ItemDefinition(key, owner, base, components, behaviors);
    }

    private ComponentMap loadComponents(MapConfigSection item, String namespace) throws ContentException {
        ComponentMap.Builder builder = ComponentMap.builder();
        ConfigSection section = item.getSection("components").orElse(null);
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

    private List<Behavior> loadBehaviors(MapConfigSection item, String namespace) throws ContentException {
        List<Behavior> behaviors = new ArrayList<>();
        for (ConfigSection entry : item.getSectionList("behaviors")) {
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
