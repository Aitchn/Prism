package com.aitchn.prism.client;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemModel;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Item display entities that exist only in the packets sent to one viewer (the display layer, ADR-0023). Nothing is
 * spawned on the server, so there is nothing to tick, save or load; the caller decides when a viewer needs which
 * entities and destroys them when the chunk goes away. Safe to call from any thread.
 */
public final class DisplayEntities {

    // Entity data indexes of Display and ItemDisplay (the same from 1.20.2 on).
    private static final int TRANSLATION = 11;
    private static final int LEFT_ROTATION = 13;
    private static final int VIEW_RANGE = 17;
    private static final int ITEM = 23;

    private DisplayEntities() {}

    /** A new entity id that cannot clash with a real entity of the server. */
    public static int nextId() {
        return SpigotReflectionUtil.generateEntityId();
    }

    /**
     * Shows {@code viewer} an item display of {@code model} (an item model id such as {@code prism:face/ab12}).
     *
     * @param x           centre of the cell the entity is placed in
     * @param translation where the model is moved to, relative to the entity (x, y, z)
     * @param rotation    the model's turn as a quaternion (x, y, z, w), applied before the translation
     * @param viewRange   the display's view range multiplier (1.0 is the client's default of 64 blocks scaled by the
     *                    render distance)
     */
    public static void spawn(Player viewer, int entityId, double x, double y, double z, String model,
            float[] translation, float[] rotation, float viewRange) {
        ItemStack stack = ItemStack.builder().type(ItemTypes.STONE).amount(1)
                .component(ComponentTypes.ITEM_MODEL, new ItemModel(new ResourceLocation(model))).build();
        List<EntityData<?>> data = List.of(
                new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F,
                        new Vector3f(translation[0], translation[1], translation[2])),
                new EntityData<>(LEFT_ROTATION, EntityDataTypes.QUATERNION,
                        new Quaternion4f(rotation[0], rotation[1], rotation[2], rotation[3])),
                new EntityData<>(VIEW_RANGE, EntityDataTypes.FLOAT, viewRange),
                new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, stack));
        var players = PacketEvents.getAPI().getPlayerManager();
        players.sendPacket(viewer, new WrapperPlayServerSpawnEntity(entityId, Optional.of(UUID.randomUUID()),
                EntityTypes.ITEM_DISPLAY, new Vector3d(x, y, z), 0f, 0f, 0f, 0, Optional.empty()));
        players.sendPacket(viewer, new WrapperPlayServerEntityMetadata(entityId, data));
    }

    /** Removes the entities with {@code ids} from {@code viewer}'s world. */
    public static void destroy(Player viewer, int[] ids) {
        if (ids.length > 0) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(ids));
        }
    }
}
