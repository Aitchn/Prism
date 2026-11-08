package com.aitchn.prism.core.look;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * The states of one vanilla block that custom looks may borrow. Every state in {@link #borrowable} is drawn by the
 * resource pack with a custom model, so a real vanilla block in that state would change its look. That is only
 * acceptable if the real block can be shown as another state that looks identical in vanilla: {@link #canonical}.
 * {@code invisible} lists the properties the block's vanilla blockstate file does not use to choose a model (checked by
 * {@code tools/verify-borrowed-families.py} against the game's assets), each with the value the real blocks are shown
 * with. {@code guard} lists properties that must have the given value for that to hold (a waterlogged block draws
 * water, so only waterlogged=false states are borrowed or canonicalised).
 *
 * @param block      block id, for example {@code minecraft:note_block}
 * @param physics    collision, occlusion and sound group of every state of the block, as the vanilla client sees them
 * @param borrow     every property of the block with the values borrowed states take, in enumeration order (the first
 *                   property varies slowest); a property with one value is pinned
 * @param invisible  property to the value real blocks are shown with
 * @param guard      property to the value it must have for canonicalisation to apply
 * @param shell      the state set aside as the invisible shell of this family's class, if the family has one
 * @param shellLayer the chunk layer of the block, which decides whether a transparent shell is invisible
 * @param breaking   the vanilla break behaviour of the block
 * @param vanilla    the model the game's own blockstate file gives a state
 * @param order      how borrowed states are ordered, to hand out the safest ones first; null for enumeration order
 */
public record BorrowedFamily(
        @NotNull String block,
        @NotNull PhysicsClass physics,
        @NotNull Map<String, List<String>> borrow,
        @NotNull Map<String, String> invisible,
        @NotNull Map<String, String> guard,
        @NotNull Optional<StateRef> shell,
        @NotNull Layer shellLayer,
        @NotNull ClientBreak breaking,
        @NotNull Function<StateRef, ModelRef> vanilla,
        Comparator<StateRef> order) {

    /**
     * The state a client is shown for real block state {@code state} of this block: invisible properties set to their
     * canonical values if the guards hold, else {@code state} itself.
     */
    public @NotNull StateRef canonical(@NotNull StateRef state) {
        for (Map.Entry<String, String> g : guard.entrySet()) {
            if (!g.getValue().equals(state.properties().get(g.getKey()))) {
                return state;
            }
        }
        Map<String, String> props = new TreeMap<>(state.properties());
        invisible.forEach((name, value) -> {
            if (props.containsKey(name)) {
                props.put(name, value);
            }
        });
        return new StateRef(block, props);
    }

    /**
     * All states a look may borrow: the product of {@link #borrow}, without the states that are their own canonical
     * form (those stay what real blocks show). Ordered by {@link #order}.
     */
    public @NotNull List<StateRef> borrowable() {
        List<Map<String, String>> product = new ArrayList<>();
        product.add(new LinkedHashMap<>());
        for (Map.Entry<String, List<String>> property : borrow.entrySet()) {
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> prefix : product) {
                for (String value : property.getValue()) {
                    Map<String, String> copy = new LinkedHashMap<>(prefix);
                    copy.put(property.getKey(), value);
                    next.add(copy);
                }
            }
            product = next;
        }
        List<StateRef> states = new ArrayList<>();
        for (Map<String, String> props : product) {
            StateRef state = new StateRef(block, props);
            if (!canonical(state).equals(state)) {
                states.add(state);
            }
        }
        if (order != null) {
            states.sort(order);
        }
        return states;
    }

    /** The state the game's own blockstate file draws with {@code state}'s model, for writing the replacement file. */
    public @NotNull ModelRef vanillaModel(@NotNull StateRef state) {
        return vanilla.apply(state);
    }
}
