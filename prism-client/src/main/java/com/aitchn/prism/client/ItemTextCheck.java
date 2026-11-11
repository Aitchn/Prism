package com.aitchn.prism.client;

import com.aitchn.prism.core.lang.ItemTextRenderer;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Runs a real server stack through the same conversion as a packet, without a client: Bukkit stack to network stack,
 * rewritten for a locale, restored, and back to a Bukkit stack. Used by the startup self test.
 */
public final class ItemTextCheck {

    /**
     * @param names    the plain name shown per locale
     * @param restored the stack after the round trip through each locale, as the server would receive it
     * @param plain    the stack converted there and back without any rewriting (to tell conversion loss apart)
     */
    public record Result(Map<String, String> names, Map<String, org.bukkit.inventory.ItemStack> restored,
            org.bukkit.inventory.ItemStack plain) {}

    private ItemTextCheck() {}

    public static Result run(ItemTextRenderer renderer, org.bukkit.inventory.ItemStack server, String... locales) {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, org.bukkit.inventory.ItemStack> restored = new LinkedHashMap<>();
        for (String locale : locales) {
            ItemStack packet = SpigotConversionUtil.fromBukkitItemStack(server);
            PacketItemText.toClient(renderer, packet, locale);
            Component name = packet.getComponent(ComponentTypes.ITEM_NAME).orElse(Component.empty());
            names.put(locale, PlainTextComponentSerializer.plainText().serialize(name));
            PacketItemText.toServer(renderer, packet);
            restored.put(locale, SpigotConversionUtil.toBukkitItemStack(packet));
        }
        org.bukkit.inventory.ItemStack plain = SpigotConversionUtil.toBukkitItemStack(
                SpigotConversionUtil.fromBukkitItemStack(server));
        return new Result(names, restored, plain);
    }
}
