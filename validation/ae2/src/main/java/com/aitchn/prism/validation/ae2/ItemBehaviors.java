package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.api.item.ItemLoreBehavior;
import com.aitchn.prism.validation.ae2.crafting.PatternCodec;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import com.aitchn.prism.validation.ae2.runtime.StackCodec;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/** Lore lines computed from per-stack data. */
final class ItemBehaviors {

    private ItemBehaviors() {}

    /** Shows how full a storage cell is, from the contents kept in the stack's own data. */
    record CellLore() implements ItemLoreBehavior {
        static CellLore fromYaml(ConfigSection section) throws ContentException {
            section.rejectUnknownKeys(Set.of());
            return new CellLore();
        }

        @Override
        public @NotNull List<String> lore(@NotNull ItemStack stack) {
            Optional<StorageCell.Tier> tier = Prism.items().identify(stack).flatMap(Ae2Content::cellTier);
            if (tier.isEmpty()) {
                return List.of();
            }
            String contents = stack.getPersistentDataContainer().get(Ae2Content.CONTENTS, PersistentDataType.STRING);
            StorageCell cell = StorageCell.load(new UUID(0, 0), tier.get(), contents == null ? "" : contents);
            return List.of("<gray>" + cell.usedBytes() + "/" + tier.get().bytes() + " bytes, " + cell.types()
                    + " types");
        }
    }

    /** Shows what an encoded pattern makes. */
    record PatternLore() implements ItemLoreBehavior {
        static PatternLore fromYaml(ConfigSection section) throws ContentException {
            section.rejectUnknownKeys(Set.of());
            return new PatternLore();
        }

        @Override
        public @NotNull List<String> lore(@NotNull ItemStack stack) {
            String text = stack.getPersistentDataContainer().get(Ae2Content.PATTERN, PersistentDataType.STRING);
            return text == null ? List.of() : PatternCodec.decode(text).map(pattern -> {
                String name = StackCodec.stack(pattern.output(), 1).map(StackCodec::name).orElse("?");
                return List.of("<gray>Makes " + pattern.outputCount() + " "
                        + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(name));
            }).orElse(List.of());
        }
    }
}
