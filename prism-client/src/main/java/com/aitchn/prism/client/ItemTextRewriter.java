package com.aitchn.prism.client;

import com.aitchn.prism.core.lang.ItemTextRenderer;
import com.aitchn.prism.core.lang.Locales;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.advancements.Advancement;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementDisplay;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementHolder;
import com.github.retrooper.packetevents.protocol.component.ComponentType;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.HashedComponentPatchMap;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.item.HashedStack;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.recipe.data.MerchantOffer;
import com.github.retrooper.packetevents.protocol.recipe.display.FurnaceRecipeDisplay;
import com.github.retrooper.packetevents.protocol.recipe.display.RecipeDisplay;
import com.github.retrooper.packetevents.protocol.recipe.display.ShapedCraftingRecipeDisplay;
import com.github.retrooper.packetevents.protocol.recipe.display.ShapelessCraftingRecipeDisplay;
import com.github.retrooper.packetevents.protocol.recipe.display.SmithingRecipeDisplay;
import com.github.retrooper.packetevents.protocol.recipe.display.StonecutterRecipeDisplay;
import com.github.retrooper.packetevents.protocol.recipe.display.slot.CompositeSlotDisplay;
import com.github.retrooper.packetevents.protocol.recipe.display.slot.ItemStackSlotDisplay;
import com.github.retrooper.packetevents.protocol.recipe.display.slot.SlotDisplay;
import com.github.retrooper.packetevents.protocol.recipe.display.slot.WithRemainderSlotDisplay;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCraftRecipeResponse;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMerchantOffers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRecipeBookAdd;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAdvancements;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.entity.Player;

/**
 * Shows every Prism stack in the viewer's language (ADR-0026). The server keeps one language-neutral form of each
 * stack; this listener adds the translated {@code item_name} and lore to every outgoing packet that carries items, and
 * takes them off again in the packets a client sends, so no translated stack ever reaches the server.
 *
 * <ul>
 *   <li>Out: set slot, window items (and the carried item), cursor item, player inventory slot, entity equipment,
 *       entity metadata (dropped items, item frames, item displays, anything with an item), merchant offer results,
 *       recipe book entries and ghost recipes, advancement icons.
 *   <li>In: creative inventory action (the client sends a whole stack: it is restored), click window (since 1.21.5 the
 *       client sends hashes of its stacks: the hashes of the added name and lore are dropped so that the server's
 *       comparison matches its own stack instead of resending the slot).
 * </ul>
 *
 * <p>Runs on network threads. Reads only immutable snapshots (the translation tables, the item registry) and the
 * locale map; it never touches the world or the server's inventories.
 */
final class ItemTextRewriter extends PacketListenerAbstract {

    /** The components the rewriter adds; a client's hash of them never matches the server's stack. */
    private static final Set<ComponentType<?>> ADDED = Set.of(ComponentTypes.ITEM_NAME, ComponentTypes.LORE);

    private final TextSource source;

    ItemTextRewriter(TextSource source) {
        super(PacketListenerPriority.HIGH);
        this.source = source;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
            done(event, one(wrapper.getItem(), locale(event)));
        } else if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
            String locale = locale(event);
            boolean changed = all(wrapper.getItems(), locale);
            changed |= one(wrapper.getCarriedItem().orElse(null), locale);
            done(event, changed);
        } else if (type == PacketType.Play.Server.SET_CURSOR_ITEM) {
            done(event, one(new WrapperPlayServerSetCursorItem(event).getStack(), locale(event)));
        } else if (type == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            done(event, one(new WrapperPlayServerSetPlayerInventory(event).getStack(), locale(event)));
        } else if (type == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            String locale = locale(event);
            boolean changed = false;
            for (Equipment equipment : new WrapperPlayServerEntityEquipment(event).getEquipment()) {
                changed |= one(equipment.getItem(), locale);
            }
            done(event, changed);
        } else if (type == PacketType.Play.Server.ENTITY_METADATA) {
            metadata(event);
        } else if (type == PacketType.Play.Server.MERCHANT_OFFERS) {
            String locale = locale(event);
            boolean changed = false;
            for (MerchantOffer offer : new WrapperPlayServerMerchantOffers(event).getMerchantOffers()) {
                changed |= one(offer.getOutputItem(), locale);
            }
            done(event, changed);
        } else if (type == PacketType.Play.Server.RECIPE_BOOK_ADD) {
            String locale = locale(event);
            boolean changed = false;
            for (WrapperPlayServerRecipeBookAdd.AddEntry entry : new WrapperPlayServerRecipeBookAdd(event).getEntries()) {
                changed |= recipe(entry.getContents().getDisplay(), locale);
            }
            done(event, changed);
        } else if (type == PacketType.Play.Server.CRAFT_RECIPE_RESPONSE) {
            WrapperPlayServerCraftRecipeResponse wrapper = new WrapperPlayServerCraftRecipeResponse(event);
            done(event, recipe(wrapper.getRecipeDisplay(), locale(event)));
        } else if (type == PacketType.Play.Server.UPDATE_ADVANCEMENTS) {
            String locale = locale(event);
            boolean changed = false;
            for (AdvancementHolder holder : new WrapperPlayServerUpdateAdvancements(event).getAddedAdvancements()) {
                Advancement advancement = holder.getAdvancement();
                AdvancementDisplay display = advancement == null ? null : advancement.getDisplay();
                if (display != null) {
                    changed |= one(display.getIcon(), locale);
                }
            }
            done(event, changed);
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            WrapperPlayClientCreativeInventoryAction wrapper = new WrapperPlayClientCreativeInventoryAction(event);
            if (PacketItemText.toServer(source.renderer(), wrapper.getItemStack())) {
                event.markForReEncode(true);
            }
        } else if (type == PacketType.Play.Client.CLICK_WINDOW) {
            clicked(event);
        }
    }

    private void metadata(PacketSendEvent event) {
        WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
        String locale = null;
        boolean changed = false;
        for (EntityData<?> data : wrapper.getEntityMetadata()) {
            ItemStack stack = null;
            if (data.getValue() instanceof ItemStack item) {
                stack = item;
            } else if (data.getValue() instanceof Optional<?> optional && optional.orElse(null) instanceof ItemStack item) {
                stack = item;
            }
            if (PacketItemText.of(stack) != null) {
                if (locale == null) {
                    locale = locale(event);
                }
                changed |= one(stack, locale);
            }
        }
        done(event, changed);
    }

    private void clicked(PacketReceiveEvent event) {
        WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
        Map<Integer, Optional<HashedStack>> slots = wrapper.getHashedSlots();
        if (slots == null) {
            return; // before 1.21.5 the stacks of a click are only used to resync, never stored
        }
        boolean changed = false;
        Map<Integer, Optional<HashedStack>> cleaned = new LinkedHashMap<>();
        for (Map.Entry<Integer, Optional<HashedStack>> entry : slots.entrySet()) {
            HashedStack stack = entry.getValue().orElse(null);
            HashedStack clean = withoutAdded(stack);
            changed |= clean != stack;
            cleaned.put(entry.getKey(), Optional.ofNullable(clean));
        }
        HashedStack carried = wrapper.getCarriedHashedStack().orElse(null);
        HashedStack cleanCarried = withoutAdded(carried);
        changed |= cleanCarried != carried;
        if (changed) {
            wrapper.setHashedSlots(cleaned);
            wrapper.setCarriedHashedStack(Optional.ofNullable(cleanCarried));
            event.markForReEncode(true);
        }
    }

    /** {@code stack} without the hashes of the components the rewriter adds; the same object when it has none. */
    private static HashedStack withoutAdded(HashedStack stack) {
        if (stack == null) {
            return null;
        }
        Map<ComponentType<?>, Integer> added = stack.getComponents().getAddedComponents();
        boolean any = false;
        for (ComponentType<?> type : ADDED) {
            any |= added.containsKey(type);
        }
        if (!any) {
            return stack;
        }
        Map<ComponentType<?>, Integer> kept = new HashMap<>(added);
        kept.keySet().removeAll(ADDED);
        return new HashedStack(stack.getItem(), stack.getCount(),
                new HashedComponentPatchMap(kept, stack.getComponents().getRemovedComponents()));
    }

    private boolean recipe(RecipeDisplay<?> display, String locale) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return slots(shaped.getIngredients(), locale) | slot(shaped.getResult(), locale);
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return slots(shapeless.getIngredients(), locale) | slot(shapeless.getResult(), locale);
        }
        if (display instanceof FurnaceRecipeDisplay furnace) {
            return slot(furnace.getIngredient(), locale) | slot(furnace.getResult(), locale);
        }
        if (display instanceof StonecutterRecipeDisplay stonecutter) {
            return slot(stonecutter.getInput(), locale) | slot(stonecutter.getResult(), locale);
        }
        if (display instanceof SmithingRecipeDisplay smithing) {
            return slot(smithing.getTemplate(), locale) | slot(smithing.getBase(), locale)
                    | slot(smithing.getAddition(), locale) | slot(smithing.getResult(), locale);
        }
        return false;
    }

    private boolean slots(List<SlotDisplay<?>> displays, String locale) {
        boolean changed = false;
        for (SlotDisplay<?> display : displays) {
            changed |= slot(display, locale);
        }
        return changed;
    }

    /** Rewrites the stacks a slot display shows; only the kinds that can hold a full stack are walked. */
    private boolean slot(SlotDisplay<?> display, String locale) {
        if (display instanceof ItemStackSlotDisplay stack) {
            return one(stack.getStack(), locale);
        }
        if (display instanceof CompositeSlotDisplay composite) {
            return slots(composite.getContents(), locale);
        }
        if (display instanceof WithRemainderSlotDisplay remainder) {
            return slot(remainder.getInput(), locale) | slot(remainder.getRemainder(), locale);
        }
        return false;
    }

    private boolean all(List<ItemStack> stacks, String locale) {
        boolean changed = false;
        for (ItemStack stack : stacks) {
            changed |= one(stack, locale);
        }
        return changed;
    }

    private boolean one(ItemStack stack, String locale) {
        ItemTextRenderer renderer = source.renderer();
        return PacketItemText.toClient(renderer, stack, locale);
    }

    private static void done(PacketSendEvent event, boolean changed) {
        if (changed) {
            event.markForReEncode(true);
        }
    }

    private String locale(PacketSendEvent event) {
        Object player = event.getPlayer();
        return player instanceof Player bukkit ? source.locale(bukkit) : Locales.FALLBACK;
    }
}
