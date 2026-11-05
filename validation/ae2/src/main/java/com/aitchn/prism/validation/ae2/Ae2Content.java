package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ComponentType;
import com.aitchn.prism.api.registry.RegistrySnapshot;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import java.util.Locale;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/** Names and lookups for the mod's own items. */
final class Ae2Content {

    static final String NAMESPACE = "ae2";

    /** How much energy one charged certus quartz crystal gives. Declared in YAML as {@code ae2:energy: 1600}. */
    record Energy(int value) {}

    static volatile ComponentType<Energy> energy;
    private static volatile RegistrySnapshot<ItemDefinition> items;

    private Ae2Content() {}

    static void bind(@NotNull ComponentType<Energy> type) {
        energy = type;
    }

    static void bind(@NotNull RegistrySnapshot<ItemDefinition> snapshot) {
        items = snapshot;
    }

    static int energyOf(@NotNull PrismKey key) {
        RegistrySnapshot<ItemDefinition> snapshot = items;
        ComponentType<Energy> type = energy;
        if (snapshot == null || type == null) {
            return 0;
        }
        return snapshot.get(key).flatMap(definition -> definition.components().get(type)).map(Energy::value).orElse(0);
    }

    static PrismKey cellKey(StorageCell.Tier tier) {
        return new PrismKey(NAMESPACE, "storage_cell_" + tier.name().substring(1).toLowerCase(Locale.ROOT) + "k");
    }

    static PrismKey cellIcon(StorageCell.Tier tier) {
        return cellKey(tier);
    }

    static Optional<StorageCell.Tier> cellTier(PrismKey key) {
        for (StorageCell.Tier tier : StorageCell.Tier.values()) {
            if (cellKey(tier).equals(key)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}
