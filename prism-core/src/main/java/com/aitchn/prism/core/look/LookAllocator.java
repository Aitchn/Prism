package com.aitchn.prism.core.look;

import com.aitchn.prism.api.content.PrismComponents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

/**
 * Hands out client representations for looks, once per boot. Looks are visited in id order, so the same content gives
 * the same table and therefore the same resource pack bytes; there is no persistent assignment (the world stores
 * identity only).
 *
 * <ol>
 *   <li>A look that wants a state ({@code auto}, {@code state}) takes the next unused borrowed state of a family whose
 *       class (collision, occlusion, sound group) equals its own.
 *   <li>Otherwise, and for {@code display}, it takes the invisible shell of its class and is drawn by display
 *       entities. If no family offers a shell of exactly its class, the nearest one is used (same collision, then same
 *       occlusion, then same sound) and the table says so.
 * </ol>
 */
public final class LookAllocator {

    private LookAllocator() {}

    /**
     * @param solidShells also use shells on blocks the client draws in its solid layer (the note block): exact for the
     *                    default class, but only invisible if the client does not draw transparent texels opaque there
     */
    public static @NotNull LookTable allocate(@NotNull ResolvedPool pool, @NotNull Collection<LookRequest> requests,
            boolean solidShells) {
        Map<String, LookRequest> sorted = new TreeMap<>();
        for (LookRequest request : requests) {
            sorted.putIfAbsent(request.id(), request);
        }
        Map<PhysicsClass, Deque<StateRef>> free = new LinkedHashMap<>();
        Deque<StateRef> noteSlots = new ArrayDeque<>();
        for (ResolvedPool.Family family : pool.families()) {
            free.computeIfAbsent(family.family().physics(), k -> new ArrayDeque<>()).addAll(family.slots());
            if (family.family().block().equals("minecraft:note_block")) {
                noteSlots.addAll(family.slots());
            }
        }
        Map<String, LookTable.Placement> placements = new LinkedHashMap<>();
        List<String> unplaced = new ArrayList<>();
        List<String> warnings = new ArrayList<>(pool.warnings());
        Map<String, Integer> relaxed = new TreeMap<>();
        int fellBack = 0;
        for (LookRequest request : sorted.values()) {
            PhysicsClass wanted = request.physics();
            if (request.bedrockOnly()) {
                // Needs a state Geyser maps; there is no display layer for it.
                StateRef note = noteSlots.pollFirst();
                if (note == null) {
                    unplaced.add(request.id());
                } else {
                    Deque<StateRef> sameClass = free.get(noteClass(pool));
                    if (sameClass != null) {
                        sameClass.remove(note);
                    }
                    placements.put(request.id(), new LookTable.Placement(request.id(), LookTable.Tier.STATE, note,
                            wanted, noteClass(pool), true));
                }
                continue;
            }
            if (request.render() != PrismComponents.Render.DISPLAY) {
                Deque<StateRef> slots = free.get(wanted);
                StateRef state = slots == null ? null : slots.pollFirst();
                if (state != null) {
                    noteSlots.remove(state);
                    placements.put(request.id(), new LookTable.Placement(request.id(), LookTable.Tier.STATE, state,
                            wanted, wanted, false));
                    continue;
                }
                if (request.render() == PrismComponents.Render.STATE) {
                    fellBack++;
                }
            }
            Optional<Shell> shell = shellFor(pool, wanted, solidShells);
            if (shell.isEmpty()) {
                unplaced.add(request.id());
                continue;
            }
            if (!shell.get().physics().equals(wanted)) {
                relaxed.merge(wanted.id() + " -> " + shell.get().physics().id(), 1, Integer::sum);
            }
            placements.put(request.id(), new LookTable.Placement(request.id(), LookTable.Tier.DISPLAY,
                    shell.get().state(), wanted, shell.get().physics(), false));
        }
        if (fellBack > 0) {
            warnings.add(fellBack + " looks asked for prism:render state but no borrowed state of their class was left;"
                    + " they are drawn by the display layer");
        }
        relaxed.forEach((change, count) -> warnings.add(count + " display looks use a shell of another class ("
                + change + "): no vanilla block offers an invisible shell of exactly that class"));
        if (!unplaced.isEmpty()) {
            warnings.add(unplaced.size() + " looks got no client representation: " + unplaced.stream().limit(5).toList());
        }
        return new LookTable(pool, placements, unplaced, warnings);
    }

    private static PhysicsClass noteClass(ResolvedPool pool) {
        return pool.family("minecraft:note_block").map(f -> f.family().physics())
                .orElse(new PhysicsClass(Collision.FULL, true, SoundGroup.WOOD));
    }

    private record Shell(StateRef state, PhysicsClass physics) {}

    /** The shell closest to {@code wanted}: same collision always, then same occlusion, then same sound. */
    private static Optional<Shell> shellFor(ResolvedPool pool, PhysicsClass wanted, boolean solidShells) {
        Shell best = null;
        int bestRank = Integer.MAX_VALUE;
        for (ResolvedPool.Family family : pool.families()) {
            if (family.shell().isEmpty() || (family.family().shellLayer() == Layer.SOLID && !solidShells)) {
                continue;
            }
            PhysicsClass have = family.family().physics();
            if (have.collision() != wanted.collision()) {
                continue;
            }
            int rank = (have.occludes() == wanted.occludes() ? 0 : 2) + (have.sound() == wanted.sound() ? 0 : 1);
            if (rank < bestRank) {
                best = new Shell(family.shell().get(), have);
                bestRank = rank;
            }
        }
        return Optional.ofNullable(best);
    }
}
