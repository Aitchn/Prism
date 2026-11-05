package com.aitchn.prism.plugin;

import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The {@link BlockContext} handed to the behaviors of one event. */
final class BlockEventContext implements BlockContext {

    private final CustomBlock custom;
    private final Player player;
    private final BlockFace face;
    private final Vector hit;
    private final EquipmentSlot hand;
    private boolean cancelled;
    private ItemStack placedFrom;
    private boolean passed;
    private boolean handled;

    BlockEventContext(CustomBlock custom, @Nullable Player player, @Nullable BlockFace face, @Nullable Vector hit,
            @Nullable EquipmentSlot hand) {
        this.custom = custom;
        this.player = player;
        this.face = face;
        this.hit = hit;
        this.hand = hand;
    }

    static BlockEventContext of(CustomBlock custom, @Nullable Player player) {
        return new BlockEventContext(custom, player, null, null, player == null ? null : EquipmentSlot.HAND);
    }

    @Override
    public @NotNull Block block() {
        return custom.block();
    }

    @Override
    public @NotNull BlockDefinition definition() {
        return custom.definition();
    }

    @Override
    public @NotNull CustomBlock custom() {
        return custom;
    }

    @Override
    public @Nullable Player player() {
        return player;
    }

    @Override
    public @Nullable BlockFace face() {
        return face;
    }

    @Override
    public @Nullable Vector hitPosition() {
        return hit == null ? null : hit.clone();
    }

    @Override
    public @Nullable EquipmentSlot hand() {
        return hand;
    }

    @Override
    public @Nullable ItemStack heldItem() {
        return player == null || hand == null ? null : player.getInventory().getItem(hand);
    }

    /** The placing stack for place behaviors; returns this. */
    BlockEventContext placedFrom(@Nullable ItemStack from) {
        this.placedFrom = from == null ? null : from.asOne();
        return this;
    }

    @Override
    public @Nullable ItemStack placedFrom() {
        return placedFrom == null ? null : placedFrom.clone();
    }

    @Override
    public void pass() {
        passed = true;
    }

    /** Runs one interact behavior and records whether it used the click. */
    void interact(BlockInteractBehavior behavior) {
        passed = false;
        behavior.onInteract(this);
        handled |= !passed;
    }

    /** Whether some interact behavior used the click. */
    boolean handled() {
        return handled;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}
