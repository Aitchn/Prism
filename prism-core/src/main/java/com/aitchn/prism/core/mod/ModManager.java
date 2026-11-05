package com.aitchn.prism.core.mod;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.Behavior;
import com.aitchn.prism.api.content.BehaviorFactory;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.BehaviorType;
import com.aitchn.prism.api.content.ComponentDecoder;
import com.aitchn.prism.api.content.ComponentType;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.mod.ContentView;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.recipe.PrismRecipe;
import com.aitchn.prism.api.recipe.PrismRecipes;
import com.aitchn.prism.api.recipe.RecipeDecoder;
import com.aitchn.prism.api.recipe.RecipeType;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.api.registry.RegistrySnapshot;
import com.aitchn.prism.core.content.ContentLoader;
import com.aitchn.prism.core.content.Problem;
import com.aitchn.prism.core.content.TypeLookup;
import com.aitchn.prism.core.registry.MutableRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/**
 * Runs the mod lifecycle: initialize (register types), load and validate content, publish atomically, then notify.
 * A mod that fails any step publishes nothing and never affects other mods.
 */
public final class ModManager {

    static final String BUILTIN_OWNER = "prism";

    private final MutableRegistry<ComponentType<?>> componentTypes = new MutableRegistry<>();
    private final MutableRegistry<BehaviorType<? extends Behavior>> behaviorTypes = new MutableRegistry<>();
    private final MutableRegistry<ItemDefinition> items = new MutableRegistry<>();
    private final MutableRegistry<BlockDefinition> blocks = new MutableRegistry<>();
    private final MutableRegistry<RecipeType<?>> recipeTypes = new MutableRegistry<>();
    private final MutableRegistry<PrismRecipe<?>> recipes = new MutableRegistry<>();
    private final Map<String, Map<String, byte[]>> assets = new LinkedHashMap<>();
    private final Map<String, Map<PrismKey, byte[]>> models = new LinkedHashMap<>();
    private final Map<String, ModCandidate> active = new HashMap<>();
    private final Map<String, String> namespaceOwners = new HashMap<>();
    private final Map<String, ModReport> reports = new LinkedHashMap<>();
    private final Predicate<PrismKey> baseItemExists;
    private final Predicate<PrismKey> carrierBlockExists;

    public ModManager(@NotNull Predicate<PrismKey> baseItemExists, @NotNull Predicate<PrismKey> carrierBlockExists) {
        this.baseItemExists = Objects.requireNonNull(baseItemExists, "baseItemExists");
        this.carrierBlockExists = Objects.requireNonNull(carrierBlockExists, "carrierBlockExists");
        componentTypes.publish(BUILTIN_OWNER, Map.of(
                PrismComponents.DISPLAY_NAME.key(), PrismComponents.DISPLAY_NAME,
                PrismComponents.MAX_STACK_SIZE.key(), PrismComponents.MAX_STACK_SIZE,
                PrismComponents.MODEL.key(), PrismComponents.MODEL,
                PrismComponents.PLACES_BLOCK.key(), PrismComponents.PLACES_BLOCK,
                PrismComponents.MODEL_REF.key(), PrismComponents.MODEL_REF,
                PrismComponents.MINING.key(), PrismComponents.MINING,
                PrismComponents.ROTATION.key(), PrismComponents.ROTATION,
                PrismComponents.VARIANTS.key(), PrismComponents.VARIANTS,
                PrismComponents.LORE.key(), PrismComponents.LORE));
        Map<PrismKey, RecipeType<?>> builtinRecipes = new LinkedHashMap<>();
        PrismRecipes.ALL.forEach(type -> builtinRecipes.put(type.key(), type));
        recipeTypes.publish(BUILTIN_OWNER, builtinRecipes);
    }

    public @NotNull Registry<ItemDefinition> items() {
        return items;
    }

    public @NotNull Registry<BlockDefinition> blocks() {
        return blocks;
    }

    /** Resource-pack files (path to bytes) of every active mod, in activation order. Paths start with {@code assets/}. */
    public synchronized @NotNull Map<String, Map<String, byte[]>> assets() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(assets));
    }

    /**
     * Blockbench models ({@code <root>/models/**.bbmodel}) of every active mod, keyed by model key. The key value is
     * the file path below {@code models/} without extension.
     */
    public synchronized @NotNull Map<PrismKey, byte[]> models() {
        Map<PrismKey, byte[]> all = new LinkedHashMap<>();
        models.values().forEach(all::putAll);
        return java.util.Collections.unmodifiableMap(all);
    }

    /** Recipe types: Prism's vanilla ones plus every active mod's. */
    public @NotNull Registry<RecipeType<?>> recipeTypes() {
        return recipeTypes;
    }

    /** Every active mod's recipes, keyed by recipe id. */
    public @NotNull Registry<PrismRecipe<?>> recipes() {
        return recipes;
    }

    public @NotNull Registry<ComponentType<?>> componentTypes() {
        return componentTypes;
    }

    public @NotNull Registry<BehaviorType<? extends Behavior>> behaviorTypes() {
        return behaviorTypes;
    }

    public synchronized @NotNull Optional<ModReport> report(@NotNull String id) {
        return Optional.ofNullable(reports.get(id));
    }

    public synchronized @NotNull List<ModReport> reports() {
        return List.copyOf(reports.values());
    }

    public synchronized boolean isActive(@NotNull String id) {
        return active.containsKey(id);
    }

    public synchronized @NotNull ModReport activate(@NotNull ModCandidate mod) {
        List<Problem> problems = new ArrayList<>();
        if (active.containsKey(mod.id())) {
            return record(mod, problems, "already active", 0);
        }
        String namespaceOwner = namespaceOwners.get(mod.namespace());
        if (namespaceOwner != null) {
            problems.add(new Problem("", "namespace '" + mod.namespace() + "' is already used by " + namespaceOwner));
        }
        if (new PrismKey(mod.namespace(), "x").isReserved()) {
            problems.add(new Problem("", "namespace '" + mod.namespace() + "' is reserved"));
        }
        if (!problems.isEmpty()) {
            return fail(mod, problems);
        }

        Staging staging = new Staging(mod);
        try {
            mod.entry().onInitialize(staging);
        } catch (RuntimeException e) {
            problems.add(new Problem("", "onInitialize threw " + e));
        }
        problems.addAll(staging.problems);
        ContentLoader.Loaded loaded = new ContentLoader.Loaded(Map.of(), Map.of());
        Map<String, byte[]> modAssets = Map.of();
        Map<PrismKey, byte[]> modModels = Map.of();
        if (problems.isEmpty()) {
            loaded = new ContentLoader(staging, baseItemExists, carrierBlockExists)
                    .load(mod.content(), mod.contentRoot(), mod.namespace(), mod.id(), problems);
            modAssets = readAssets(mod, problems);
            modModels = readModels(mod, problems);
        }
        if (!problems.isEmpty()) {
            return fail(mod, problems);
        }

        try {
            componentTypes.publish(mod.id(), staging.components);
            behaviorTypes.publish(mod.id(), staging.behaviors);
            items.publish(mod.id(), loaded.items());
            blocks.publish(mod.id(), loaded.blocks());
            recipeTypes.publish(mod.id(), staging.recipeTypes);
            recipes.publish(mod.id(), loaded.recipes());
        } catch (IllegalStateException e) {
            rollback(mod.id());
            problems.add(new Problem("", e.getMessage()));
            return fail(mod, problems);
        }
        active.put(mod.id(), mod);
        assets.put(mod.id(), modAssets);
        models.put(mod.id(), modModels);
        namespaceOwners.put(mod.namespace(), mod.id());

        try {
            mod.entry().onContentLoaded(new View(ownItems(mod.id()), ownBlocks(mod.id())));
        } catch (RuntimeException e) {
            deactivateInternal(mod.id(), false);
            problems.add(new Problem("", "onContentLoaded threw " + e));
            return fail(mod, problems);
        }
        ModReport report = new ModReport(mod.id(), ModReport.Status.ACTIVE, List.of(), loaded.items().size(), loaded.blocks().size());
        reports.put(mod.id(), report);
        return report;
    }

    /** Removes everything the mod registered and calls {@code onDisable}. No-op for unknown ids. */
    public synchronized void deactivate(@NotNull String id) {
        deactivateInternal(id, true);
        reports.remove(id);
    }

    private void deactivateInternal(String id, boolean notify) {
        ModCandidate mod = active.remove(id);
        if (mod == null) {
            return;
        }
        namespaceOwners.remove(mod.namespace(), id);
        assets.remove(id);
        models.remove(id);
        rollback(id);
        if (notify) {
            try {
                mod.entry().onDisable();
            } catch (RuntimeException e) {
                mod.logger().warning("onDisable threw " + e);
            }
        }
    }

    private void rollback(String id) {
        recipes.unregisterOwner(id);
        recipeTypes.unregisterOwner(id);
        items.unregisterOwner(id);
        blocks.unregisterOwner(id);
        behaviorTypes.unregisterOwner(id);
        componentTypes.unregisterOwner(id);
    }

    private RegistrySnapshot<ItemDefinition> ownItems(String id) {
        Map<PrismKey, ItemDefinition> own = new LinkedHashMap<>();
        for (PrismKey key : items.keys()) {
            if (items.ownerOf(key).filter(id::equals).isPresent()) {
                items.get(key).ifPresent(definition -> own.put(key, definition));
            }
        }
        MutableRegistry<ItemDefinition> view = new MutableRegistry<>();
        view.publish(id, own);
        return view.snapshot();
    }

    private RegistrySnapshot<BlockDefinition> ownBlocks(String id) {
        Map<PrismKey, BlockDefinition> own = new LinkedHashMap<>();
        for (PrismKey key : blocks.keys()) {
            if (blocks.ownerOf(key).filter(id::equals).isPresent()) {
                blocks.get(key).ifPresent(definition -> own.put(key, definition));
            }
        }
        MutableRegistry<BlockDefinition> view = new MutableRegistry<>();
        view.publish(id, own);
        return view.snapshot();
    }

    /** Pack assets live in the jar under {@code assets/}; they are read into memory once, never extracted. */
    private static Map<String, byte[]> readAssets(ModCandidate mod, List<Problem> problems) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try {
            for (String path : mod.content().list("assets")) {
                files.put(path, mod.content().readBytes(path));
            }
        } catch (java.io.IOException e) {
            problems.add(new Problem("assets", "cannot read pack assets: " + e.getMessage()));
        }
        return files;
    }

    private static Map<PrismKey, byte[]> readModels(ModCandidate mod, List<Problem> problems) {
        Map<PrismKey, byte[]> files = new LinkedHashMap<>();
        String dir = mod.contentRoot() + "/models";
        try {
            for (String path : mod.content().list(dir)) {
                if (path.endsWith(".bbmodel")) {
                    String value = path.substring(dir.length() + 1, path.length() - ".bbmodel".length());
                    try {
                        files.put(new PrismKey(mod.namespace(), value), mod.content().readBytes(path));
                    } catch (IllegalArgumentException e) {
                        problems.add(new Problem(path, "file name is not a valid key: " + e.getMessage()));
                    }
                }
            }
        } catch (java.io.IOException e) {
            problems.add(new Problem(dir, "cannot read models: " + e.getMessage()));
        }
        return files;
    }

    private ModReport fail(ModCandidate mod, List<Problem> problems) {
        ModReport report = new ModReport(mod.id(), ModReport.Status.FAILED, List.copyOf(problems), 0, 0);
        reports.put(mod.id(), report);
        return report;
    }

    private ModReport record(ModCandidate mod, List<Problem> problems, String reason, int count) {
        problems.add(new Problem("", reason));
        return fail(mod, problems);
    }

    private record View(RegistrySnapshot<ItemDefinition> items, RegistrySnapshot<BlockDefinition> blocks)
            implements ContentView {}

    /** Registrations staged during onInitialize; visible to the loader before anything is published. */
    private final class Staging implements ModContext, TypeLookup {

        private final ModCandidate mod;
        final Map<PrismKey, ComponentType<?>> components = new LinkedHashMap<>();
        final Map<PrismKey, BehaviorType<? extends Behavior>> behaviors = new LinkedHashMap<>();
        final Map<PrismKey, RecipeType<?>> recipeTypes = new LinkedHashMap<>();
        final List<Problem> problems = new ArrayList<>();

        Staging(ModCandidate mod) {
            this.mod = mod;
        }

        @Override
        public @NotNull String namespace() {
            return mod.namespace();
        }

        @Override
        public @NotNull java.util.logging.Logger logger() {
            return mod.logger();
        }

        @Override
        public <T> @NotNull ComponentType<T> registerComponent(
                @NotNull String path, @NotNull Class<T> valueType, @NotNull ComponentDecoder<T> decoder) {
            ComponentType<T> type = new ComponentType<>(key(path), valueType, decoder);
            if (components.putIfAbsent(type.key(), type) != null) {
                problems.add(new Problem("", "component type " + type.key() + " registered twice"));
            }
            return type;
        }

        @Override
        public <B extends Behavior> @NotNull BehaviorType<B> registerBehavior(
                @NotNull String path, @NotNull Class<B> behaviorType, @NotNull BehaviorFactory<B> factory) {
            BehaviorType<B> type = new BehaviorType<>(key(path), behaviorType, factory);
            if (behaviors.putIfAbsent(type.key(), type) != null) {
                problems.add(new Problem("", "behavior type " + type.key() + " registered twice"));
            }
            return type;
        }

        @Override
        public <R> @NotNull RecipeType<R> registerRecipeType(
                @NotNull String path, @NotNull Class<R> valueType, @NotNull RecipeDecoder<R> decoder) {
            RecipeType<R> type = new RecipeType<>(key(path), valueType, decoder);
            if (recipeTypes.putIfAbsent(type.key(), type) != null) {
                problems.add(new Problem("", "recipe type " + type.key() + " registered twice"));
            }
            return type;
        }

        @Override
        public @NotNull Optional<RecipeType<?>> recipe(@NotNull PrismKey key) {
            RecipeType<?> staged = recipeTypes.get(key);
            return staged != null ? Optional.of(staged) : ModManager.this.recipeTypes.get(key);
        }

        private PrismKey key(String path) {
            return new PrismKey(mod.namespace(), path);
        }

        @Override
        public @NotNull Optional<ComponentType<?>> component(@NotNull PrismKey key) {
            ComponentType<?> staged = components.get(key);
            return staged != null ? Optional.of(staged) : componentTypes.get(key);
        }

        @Override
        public @NotNull Optional<BehaviorType<? extends Behavior>> behavior(@NotNull PrismKey key) {
            BehaviorType<? extends Behavior> staged = behaviors.get(key);
            return staged != null ? Optional.of(staged) : behaviorTypes.get(key);
        }
    }
}
