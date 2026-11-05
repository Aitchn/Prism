package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.content.ComponentType;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/** Names, keys and lookups for the mod's own content. */
public final class Ae2Content {

    public static final String NAMESPACE = "ae2";

    /** Offered by energy cells; mods agreeing on this key and {@link EnergySink} can charge ME networks. */
    public static final BlockCapability<EnergySink> ENERGY_SINK =
            BlockCapability.of(new PrismKey(NAMESPACE, "energy_sink"), EnergySink.class);

    /** Per-stack data of a storage cell: the serialized contents. Absent when the cell is empty. */
    public static final NamespacedKey CONTENTS = NamespacedKey.fromString("ae2:contents");
    /** Per-stack data of an encoded pattern. */
    public static final NamespacedKey PATTERN = NamespacedKey.fromString("ae2:pattern");
    /** Per-block data of an energy cell: its share of the network's stored energy. */
    public static final NamespacedKey ENERGY = NamespacedKey.fromString("ae2:energy");

    /** How much energy one charged certus quartz crystal gives. Declared in YAML as {@code ae2:energy: 1600}. */
    record Energy(int value) {}

    static volatile ComponentType<Energy> energy;

    private Ae2Content() {}

    static void bind(@NotNull ComponentType<Energy> type) {
        energy = type;
    }

    static int energyOf(@NotNull PrismKey key) {
        ComponentType<Energy> type = energy;
        if (type == null) {
            return 0;
        }
        return Prism.items().definition(key).flatMap(definition -> definition.components().get(type))
                .map(Energy::value).orElse(0);
    }

    public static PrismKey cellKey(StorageCell.Tier tier) {
        return new PrismKey(NAMESPACE, "storage_cell_" + tier.name().substring(1).toLowerCase(Locale.ROOT) + "k");
    }

    public static Optional<StorageCell.Tier> cellTier(PrismKey key) {
        for (StorageCell.Tier tier : StorageCell.Tier.values()) {
            if (cellKey(tier).equals(key)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    public static NamespacedKey slotKey(String prefix, int slot) {
        return NamespacedKey.fromString(NAMESPACE + ":" + prefix + slot);
    }
}
