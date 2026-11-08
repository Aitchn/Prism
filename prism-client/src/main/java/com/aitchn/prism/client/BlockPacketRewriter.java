package com.aitchn.prism.client;

import com.aitchn.prism.core.block.ChunkLooks;
import com.aitchn.prism.core.block.SectionStates;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Rewrites the outgoing block packets (chunk data, block change, multi block change) so that every custom block shows
 * its look to the viewer: the server stores an inert carrier block, the client is told it is a note block or tripwire
 * state that the resource pack draws. Only chunk sections that contain custom blocks are touched; everything else
 * passes through untouched. Runs on network threads and reads nothing but the immutable {@link ChunkLooks} snapshots.
 *
 * <p>The ids are those of the server version (what a plugin sends), so the carrier and look ids of a snapshot match what
 * the server writes; a protocol translation layer such as ViaVersion converts them after this listener.
 */
final class BlockPacketRewriter extends PacketListenerAbstract {

    private final LookSource source;
    private final ForeignChangeListener foreign;

    private BlockPacketRewriter(LookSource source, ForeignChangeListener foreign) {
        super(PacketListenerPriority.HIGH);
        this.source = source;
        this.foreign = foreign;
    }

    static BlockPacketRewriter create(LookSource source, ForeignChangeListener foreign) {
        return new BlockPacketRewriter(source, foreign);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            chunk(event);
        } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            single(event);
        } else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            multi(event);
        }
    }

    private void chunk(PacketSendEvent event) {
        World world = worldOf(event);
        if (world == null || !source.anyIn(world.getUID())) {
            return;
        }
        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        Column column = wrapper.getColumn();
        ChunkLooks looks = source.chunk(world.getUID(), column.getX(), column.getZ());
        if (looks.isEmpty()) {
            return;
        }
        ClientVersion version = serverVersion();
        int minSection = Math.floorDiv(world.getMinHeight(), 16);
        BaseChunk[] sections = column.getChunks();
        int changed = 0;
        for (int i = 0; i < sections.length; i++) {
            int sectionY = minSection + i;
            if (!looks.hasSection(sectionY)) {
                continue;
            }
            BaseChunk section = sections[i];
            int baseX = column.getX() << 4;
            int baseZ = column.getZ() << 4;
            changed += looks.rewriteSection(sectionY, new SectionStates() {
                @Override
                public int get(int x, int y, int z) {
                    return section.get(version, x, y, z).getGlobalId();
                }

                @Override
                public void set(int x, int y, int z, int state) {
                    section.set(version, x, y, z, state);
                }
            }, ClientStates::isLegacyCarrier, (x, y, z) -> foreign.foreign(world.getUID(), baseX + x, y, baseZ + z));
        }
        if (changed > 0) {
            event.markForReEncode(true);
        }
    }

    private void single(PacketSendEvent event) {
        World world = worldOf(event);
        if (world == null || !source.anyIn(world.getUID())) {
            return;
        }
        WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
        Vector3i at = wrapper.getBlockPosition();
        ChunkLooks looks = source.chunk(world.getUID(), at.getX() >> 4, at.getZ() >> 4);
        if (looks.isEmpty()) {
            return;
        }
        int state = wrapper.getBlockId();
        int rewritten = looks.rewrite(at.getX() & 15, at.getY(), at.getZ() & 15, state,
                ClientStates::isLegacyCarrier, (x, y, z) -> foreign.foreign(world.getUID(), at.getX(), y, at.getZ()));
        if (rewritten != state) {
            wrapper.setBlockId(rewritten);
            event.markForReEncode(true);
        }
    }

    private void multi(PacketSendEvent event) {
        World world = worldOf(event);
        if (world == null || !source.anyIn(world.getUID())) {
            return;
        }
        WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
        boolean changed = false;
        UUID id = world.getUID();
        for (WrapperPlayServerMultiBlockChange.EncodedBlock block : wrapper.getBlocks()) {
            ChunkLooks looks = source.chunk(id, block.getX() >> 4, block.getZ() >> 4);
            if (looks.isEmpty()) {
                continue;
            }
            int state = block.getBlockId();
            int rewritten = looks.rewrite(block.getX() & 15, block.getY(), block.getZ() & 15, state,
                    ClientStates::isLegacyCarrier, (x, y, z) -> foreign.foreign(id, block.getX(), y, block.getZ()));
            if (rewritten != state) {
                block.setBlockId(rewritten);
                changed = true;
            }
        }
        if (changed) {
            event.markForReEncode(true);
        }
    }

    /** The viewer's world, or null if it cannot be told (not a player yet, for example during login). */
    private static World worldOf(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return null;
        }
        try {
            return player.getWorld();
        } catch (RuntimeException e) {
            // PacketEvents hands out a placeholder player until login has finished; it has no world.
            return null;
        }
    }

    private static ClientVersion serverVersion() {
        ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        return version.toClientVersion();
    }
}
