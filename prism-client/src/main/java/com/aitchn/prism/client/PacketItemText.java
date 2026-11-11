package com.aitchn.prism.client;

import com.aitchn.prism.core.lang.ItemTextRenderer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A network item stack seen through {@link ItemTextRenderer.Target}: the persistent data Bukkit keeps in
 * {@code custom_data}, the {@code item_name} and {@code lore} components. Resetting a component sets it back to the
 * item type's default value, which removes the patch, so a restored stack encodes exactly like the original.
 */
final class PacketItemText implements ItemTextRenderer.Target<Component> {

    /** The compound Bukkit stores a stack's persistent data in, inside {@code custom_data}. */
    private static final String BUKKIT_VALUES = "PublicBukkitValues";

    private final ItemStack stack;
    private final NBTCompound customData;

    private PacketItemText(ItemStack stack, NBTCompound customData) {
        this.stack = stack;
        this.customData = customData;
    }

    /** A view of {@code stack}, or null when it cannot be Prism's (empty, or no custom data at all). */
    static @Nullable PacketItemText of(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasComponentPatches()) {
            return null;
        }
        NBTCompound custom = stack.getComponent(ComponentTypes.CUSTOM_DATA).orElse(null);
        return custom == null ? null : new PacketItemText(stack, custom);
    }

    /** Rewrites {@code stack} for a viewer in {@code locale}; false when it is not Prism's. */
    static boolean toClient(ItemTextRenderer renderer, @Nullable ItemStack stack, String locale) {
        PacketItemText view = of(stack);
        return view != null && renderer.toClient(view, locale);
    }

    /** Restores the server form of a stack a client sent; false when it is not Prism's. */
    static boolean toServer(ItemTextRenderer renderer, @Nullable ItemStack stack) {
        PacketItemText view = of(stack);
        return view != null && renderer.toServer(view);
    }

    @Override
    public @Nullable String data(@NotNull String key) {
        NBTCompound bukkit = customData.getCompoundTagOrNull(BUKKIT_VALUES);
        String value = bukkit == null ? null : bukkit.getStringTagValueOrNull(key);
        return value != null ? value : customData.getStringTagValueOrNull(key);
    }

    @Override
    public void name(@NotNull String miniMessage) {
        stack.setComponent(ComponentTypes.ITEM_NAME, text(miniMessage));
    }

    @Override
    public void resetName() {
        Optional<Component> base = stack.getType().getComponents(serverVersion()).getOptional(ComponentTypes.ITEM_NAME);
        stack.setComponent(ComponentTypes.ITEM_NAME, base);
    }

    @Override
    public @NotNull List<Component> lore() {
        return stack.getComponent(ComponentTypes.LORE).map(ItemLore::getLines).orElse(List.of());
    }

    @Override
    public void lore(@NotNull List<Component> lines) {
        if (lines.isEmpty()) {
            Optional<ItemLore> base = stack.getType().getComponents(serverVersion()).getOptional(ComponentTypes.LORE);
            stack.setComponent(ComponentTypes.LORE, base);
        } else {
            stack.setComponent(ComponentTypes.LORE, new ItemLore(List.copyOf(lines)));
        }
    }

    @Override
    public @NotNull Component line(@NotNull String miniMessage) {
        return text(miniMessage);
    }

    /** The version stacks are read and written in: what the server speaks (a protocol layer converts after us). */
    private static ClientVersion serverVersion() {
        return PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    }

    private static Component text(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }
}
