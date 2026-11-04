package com.aitchn.prism.core.mod;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.Behavior;
import com.aitchn.prism.api.content.BehaviorFactory;
import com.aitchn.prism.api.content.BehaviorType;
import com.aitchn.prism.api.content.ComponentDecoder;
import com.aitchn.prism.api.content.ComponentType;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.mod.ContentView;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.api.registry.RegistrySnapshot;
import com.aitchn.prism.core.content.ItemContentLoader;
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
    private final Map<String, ModCandidate> active = new HashMap<>();
    private final Map<String, String> namespaceOwners = new HashMap<>();
    private final Map<String, ModReport> reports = new LinkedHashMap<>();
    private final Predicate<PrismKey> baseItemExists;

    public ModManager(@NotNull Predicate<PrismKey> baseItemExists) {
        this.baseItemExists = Objects.requireNonNull(baseItemExists, "baseItemExists");
        componentTypes.publish(BUILTIN_OWNER, Map.of(
                PrismComponents.DISPLAY_NAME.key(), PrismComponents.DISPLAY_NAME,
                PrismComponents.MAX_STACK_SIZE.key(), PrismComponents.MAX_STACK_SIZE));
    }

    public @NotNull Registry<ItemDefinition> items() {
        return items;
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
        Map<PrismKey, ItemDefinition> loaded = Map.of();
        if (problems.isEmpty()) {
            loaded = new ItemContentLoader(staging, baseItemExists)
                    .load(mod.content(), mod.contentRoot(), mod.namespace(), mod.id(), problems);
        }
        if (!problems.isEmpty()) {
            return fail(mod, problems);
        }

        try {
            componentTypes.publish(mod.id(), staging.components);
            behaviorTypes.publish(mod.id(), staging.behaviors);
            items.publish(mod.id(), loaded);
        } catch (IllegalStateException e) {
            rollback(mod.id());
            problems.add(new Problem("", e.getMessage()));
            return fail(mod, problems);
        }
        active.put(mod.id(), mod);
        namespaceOwners.put(mod.namespace(), mod.id());

        try {
            mod.entry().onContentLoaded(new View(ownItems(mod.id())));
        } catch (RuntimeException e) {
            deactivateInternal(mod.id(), false);
            problems.add(new Problem("", "onContentLoaded threw " + e));
            return fail(mod, problems);
        }
        ModReport report = new ModReport(mod.id(), ModReport.Status.ACTIVE, List.of(), loaded.size());
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
        items.unregisterOwner(id);
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

    private ModReport fail(ModCandidate mod, List<Problem> problems) {
        ModReport report = new ModReport(mod.id(), ModReport.Status.FAILED, List.copyOf(problems), 0);
        reports.put(mod.id(), report);
        return report;
    }

    private ModReport record(ModCandidate mod, List<Problem> problems, String reason, int count) {
        problems.add(new Problem("", reason));
        return fail(mod, problems);
    }

    private record View(RegistrySnapshot<ItemDefinition> items) implements ContentView {}

    /** Registrations staged during onInitialize; visible to the loader before anything is published. */
    private final class Staging implements ModContext, TypeLookup {

        private final ModCandidate mod;
        final Map<PrismKey, ComponentType<?>> components = new LinkedHashMap<>();
        final Map<PrismKey, BehaviorType<? extends Behavior>> behaviors = new LinkedHashMap<>();
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
