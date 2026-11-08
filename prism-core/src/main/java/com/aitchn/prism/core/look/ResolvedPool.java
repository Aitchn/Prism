package com.aitchn.prism.core.look;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * {@link LookPool} checked against the server it runs on: only states the server's block really has are offered, and a
 * family whose block or canonical state is missing (another Minecraft version) is dropped with a warning.
 *
 * @param families the usable families, in hand-out order
 * @param warnings what was dropped and why
 */
public record ResolvedPool(@NotNull List<Family> families, @NotNull List<String> warnings) {

    /**
     * @param family    the catalogue entry
     * @param allStates every state of the block on this server (the replacement blockstate file lists all of them)
     * @param slots     states looks can borrow, shell excluded
     * @param shell     the reserved invisible-shell state, if the family has one on this server
     */
    public record Family(@NotNull BorrowedFamily family, @NotNull List<StateRef> allStates,
            @NotNull List<StateRef> slots, @NotNull Optional<StateRef> shell) {}

    /** The catalogue against the states of the verified game version (for tests and offline tools). */
    public static @NotNull ResolvedPool standard() {
        return resolve(family -> LookPool.defaultStates(family));
    }

    /**
     * @param serverStates every state of a family's block on the running server; empty if the block does not exist
     */
    public static @NotNull ResolvedPool resolve(@NotNull Function<BorrowedFamily, List<StateRef>> serverStates) {
        List<Family> usable = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (BorrowedFamily family : LookPool.families()) {
            List<StateRef> all = serverStates.apply(family);
            if (all.isEmpty()) {
                warnings.add(family.block() + " has no states on this server; its family is not used");
                continue;
            }
            Set<StateRef> existing = new HashSet<>(all);
            boolean canonicalOk = true;
            for (StateRef state : all) {
                canonicalOk &= existing.contains(family.canonical(state));
            }
            if (!canonicalOk) {
                warnings.add(family.block() + ": a canonical state is missing on this server; its family is not used");
                continue;
            }
            List<StateRef> slots = new ArrayList<>();
            for (StateRef state : family.borrowable()) {
                if (existing.contains(state)) {
                    slots.add(state);
                }
            }
            Optional<StateRef> shell = family.shell().filter(slots::contains);
            shell.ifPresent(slots::remove);
            if (slots.isEmpty() && shell.isEmpty()) {
                warnings.add(family.block() + ": none of its states exist on this server; its family is not used");
                continue;
            }
            usable.add(new Family(family, List.copyOf(all), List.copyOf(slots), shell));
        }
        return new ResolvedPool(List.copyOf(usable), List.copyOf(warnings));
    }

    /** The slots of every family with exactly {@code physics}, in hand-out order. */
    public @NotNull List<StateRef> slots(@NotNull PhysicsClass physics) {
        List<StateRef> slots = new ArrayList<>();
        for (Family family : families) {
            if (family.family().physics().equals(physics)) {
                slots.addAll(family.slots());
            }
        }
        return slots;
    }

    /** The family of {@code block}, if it is usable. */
    public @NotNull Optional<Family> family(@NotNull String block) {
        return families.stream().filter(f -> f.family().block().equals(block)).findFirst();
    }
}
