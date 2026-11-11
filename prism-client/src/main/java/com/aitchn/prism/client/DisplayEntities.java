package com.aitchn.prism.client;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemModel;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBundle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Item display entities that exist only in the packets sent to one viewer (the display layer, ADR-0023 and ADR-0025).
 * Nothing is spawned on the server, so there is nothing to tick, save or load; the caller decides when a viewer needs
 * which entities and destroys them when the chunk goes away. Safe to call from any thread.
 *
 * <p>The entities are static: no interpolation, no shadow, no glow, no billboard, no brightness override (all left at
 * their defaults, which are off), and nothing is sent per tick. A look change sends one metadata packet.
 */
public final class DisplayEntities {

    /*
     * Entity data indexes. Entity: 0 to 7. Display: 8 interpolation delay, 9 transformation interpolation duration,
     * 10 position/rotation interpolation duration, 11 translation, 12 scale, 13 left rotation, 14 right rotation,
     * 15 billboard, 16 brightness override, 17 view range, 18 shadow radius, 19 shadow strength, 20 width, 21 height,
     * 22 glow colour override. ItemDisplay: 23 item, 24 item display context. Checked against the entity metadata of
     * Minecraft 26.1 (protocol 775) as PrismarineJS minecraft-data lists it, identical to 1.20.2 to 1.21.9; 26.3 itself
     * was not available to check (see ADR-0025).
     */
    private static final int TRANSLATION = 11;
    private static final int LEFT_ROTATION = 13;
    private static final int VIEW_RANGE = 17;
    private static final int ITEM = 23;

    /** The client disconnects on a bundle of more than 4096 packets; keep well below, delimiters excluded. */
    private static final int BUNDLE_LIMIT = 4000;

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
     *                    entity distance setting)
     */
    public static void spawn(Player viewer, int entityId, double x, double y, double z, String model,
            float[] translation, float[] rotation, float viewRange) {
        Batch batch = new Batch();
        batch.spawn(entityId, x, y, z, model, translation, rotation, viewRange);
        batch.send(viewer);
    }

    /** Removes the entities with {@code ids} from {@code viewer}'s world. */
    public static void destroy(Player viewer, int[] ids) {
        if (ids.length > 0) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(ids));
        }
    }

    /**
     * Packets for one viewer, collected and then sent together: inside bundles (the client applies a bundle in one
     * tick), written from one task on the connection's event loop so nothing else is interleaved, and flushed once.
     */
    public static final class Batch {

        private final List<PacketWrapper<?>> packets = new ArrayList<>();
        private final List<Integer> destroyed = new ArrayList<>();

        /** Adds the spawn and the metadata of a new item display. */
        public void spawn(int entityId, double x, double y, double z, String model, float[] translation,
                float[] rotation, float viewRange) {
            packets.add(new WrapperPlayServerSpawnEntity(entityId, Optional.of(UUID.randomUUID()),
                    EntityTypes.ITEM_DISPLAY, new Vector3d(x, y, z), 0f, 0f, 0f, 0, Optional.empty()));
            packets.add(new WrapperPlayServerEntityMetadata(entityId, data(model, translation, rotation, viewRange,
                    true)));
        }

        /** Adds a metadata packet that changes what an existing display draws, without respawning it. */
        public void update(int entityId, String model, float[] translation, float[] rotation) {
            packets.add(new WrapperPlayServerEntityMetadata(entityId, data(model, translation, rotation, 0f, false)));
        }

        /** Adds entities to remove; they are sent before everything else of the batch, in one packet. */
        public void destroy(int... ids) {
            for (int id : ids) {
                destroyed.add(id);
            }
        }

        public boolean isEmpty() {
            return packets.isEmpty() && destroyed.isEmpty();
        }

        /** How many spawn and metadata packets the batch holds. */
        public int size() {
            return packets.size();
        }

        /** Sends everything to {@code viewer}; does nothing if the viewer has no connection any more. */
        public void send(Player viewer) {
            if (isEmpty()) {
                return;
            }
            User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
            if (user == null || user.getChannel() == null) {
                return;
            }
            List<PacketWrapper<?>> all = new ArrayList<>(packets.size() + 1);
            if (!destroyed.isEmpty()) {
                all.add(new WrapperPlayServerDestroyEntities(destroyed.stream().mapToInt(Integer::intValue).toArray()));
            }
            all.addAll(packets);
            ChannelHelper.runInEventLoop(user.getChannel(), () -> {
                for (int start = 0; start < all.size(); start += BUNDLE_LIMIT) {
                    List<PacketWrapper<?>> part = all.subList(start, Math.min(all.size(), start + BUNDLE_LIMIT));
                    boolean bundle = part.size() > 1;
                    if (bundle) {
                        user.writePacket(new WrapperPlayServerBundle());
                    }
                    part.forEach(user::writePacket);
                    if (bundle) {
                        user.writePacket(new WrapperPlayServerBundle());
                    }
                }
                user.flushPackets();
            });
        }

        private static List<EntityData<?>> data(String model, float[] translation, float[] rotation, float viewRange,
                boolean spawn) {
            ItemStack stack = ItemStack.builder().type(ItemTypes.STONE).amount(1)
                    .component(ComponentTypes.ITEM_MODEL, new ItemModel(new ResourceLocation(model))).build();
            List<EntityData<?>> data = new ArrayList<>(4);
            // Defaults (zero translation, identity rotation) are left out of a spawn: the client starts with them.
            if (!spawn || translation[0] != 0 || translation[1] != 0 || translation[2] != 0) {
                data.add(new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F,
                        new Vector3f(translation[0], translation[1], translation[2])));
            }
            if (!spawn || rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0 || rotation[3] != 1) {
                data.add(new EntityData<>(LEFT_ROTATION, EntityDataTypes.QUATERNION,
                        new Quaternion4f(rotation[0], rotation[1], rotation[2], rotation[3])));
            }
            if (spawn && viewRange != 1f) {
                data.add(new EntityData<>(VIEW_RANGE, EntityDataTypes.FLOAT, viewRange));
            }
            data.add(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, stack));
            return data;
        }
    }
}
