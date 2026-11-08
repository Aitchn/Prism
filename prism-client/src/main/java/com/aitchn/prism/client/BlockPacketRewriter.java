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
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.Palette;
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
        if (world == null) {
            return;
        }
        boolean bedrock = bedrockViewer(event);
        // A Geyser viewer is shown synthetic states only it knows, so it never needs the canonical siblings (nothing it
        // is shown is a borrowed vanilla state), and every chunk has to be checked for the width of a global palette.
        ClientCanon canon = bedrock ? ClientCanon.none() : source.canon();
        if (!bedrock && !(source.anyIn(world.getUID()) || !canon.isEmpty())) {
            return;
        }
        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        Column column = wrapper.getColumn();
        ChunkLooks looks = source.chunk(world.getUID(), column.getX(), column.getZ());
        int globalBits = bedrock ? source.bedrockGlobalBits() : 0;
        if (looks.isEmpty() && canon.isEmpty() && !(bedrock && globalBits > 0)) {
            return;
        }
        ClientVersion version = serverVersion();
        int minSection = Math.floorDiv(world.getMinHeight(), 16);
        BaseChunk[] sections = column.getChunks();
        int changed = 0;
        for (int i = 0; i < sections.length; i++) {
            int sectionY = minSection + i;
            BaseChunk section = sections[i];
            int baseX = column.getX() << 4;
            int baseZ = column.getZ() << 4;
            if (bedrock && section instanceof Chunk_v1_18 modern) {
                if (looks.hasSection(sectionY)) {
                    changed += BedrockSections.rewrite(modern, looks, sectionY, globalBits,
                            ClientStates::isLegacyCarrier,
                            (x, y, z) -> foreign.foreign(world.getUID(), baseX + x, y, baseZ + z));
                } else if (BedrockSections.normalizeWidth(modern, globalBits)) {
                    changed++;
                }
                continue;
            }
            if (!canon.isEmpty() && needsCanon(section, canon)) {
                changed += canonicalize(section, version, canon);
            }
            if (!looks.hasSection(sectionY)) {
                continue;
            }
            changed += looks.rewriteSection(sectionY, new SectionStates() {
                @Override
                public int get(int x, int y, int z) {
                    return section.getBlockId(x, y, z);
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
        if (world == null) {
            return;
        }
        boolean bedrock = bedrockViewer(event);
        ClientCanon canon = bedrock ? ClientCanon.none() : source.canon();
        if (!(source.anyIn(world.getUID()) || !canon.isEmpty())) {
            return;
        }
        WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
        Vector3i at = wrapper.getBlockPosition();
        ChunkLooks looks = source.chunk(world.getUID(), at.getX() >> 4, at.getZ() >> 4);
        int state = wrapper.getBlockId();
        int rewritten = shown(looks, canon, world.getUID(), at.getX(), at.getY(), at.getZ(), state, bedrock);
        if (rewritten != state) {
            wrapper.setBlockID(rewritten);
            event.markForReEncode(true);
        }
    }

    private void multi(PacketSendEvent event) {
        World world = worldOf(event);
        if (world == null) {
            return;
        }
        boolean bedrock = bedrockViewer(event);
        ClientCanon canon = bedrock ? ClientCanon.none() : source.canon();
        if (!(source.anyIn(world.getUID()) || !canon.isEmpty())) {
            return;
        }
        WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
        boolean changed = false;
        UUID id = world.getUID();
        for (WrapperPlayServerMultiBlockChange.EncodedBlock block : wrapper.getBlocks()) {
            ChunkLooks looks = source.chunk(id, block.getX() >> 4, block.getZ() >> 4);
            int state = block.getBlockId();
            int rewritten = shown(looks, canon, id, block.getX(), block.getY(), block.getZ(), state, bedrock);
            if (rewritten != state) {
                block.setBlockId(rewritten);
                changed = true;
            }
        }
        if (changed) {
            event.markForReEncode(true);
        }
    }

    /**
     * The state to send for {@code state} at a world position: the look (the synthetic state for a Geyser viewer) if it
     * is a custom block, else the canonical sibling of a borrowed family's real state.
     */
    private int shown(ChunkLooks looks, ClientCanon canon, UUID world, int x, int y, int z, int state,
            boolean bedrock) {
        int localX = x & 15;
        int localZ = z & 15;
        if (looks.has(localX, y, localZ)) {
            return looks.rewrite(localX, y, localZ, state, ClientStates::isLegacyCarrier,
                    (lx, ly, lz) -> foreign.foreign(world, x, ly, z), bedrock);
        }
        return canon.of(state);
    }

    /** Whether the viewer of this packet is a Geyser connection that knows the synthetic states. */
    private boolean bedrockViewer(PacketSendEvent event) {
        return source.bedrockStates() && event.getPlayer() instanceof Player player
                && source.isBedrock(player.getUniqueId());
    }

    /** Whether a section holds a state that {@code canon} replaces (read from its palette, no block is visited). */
    private static boolean needsCanon(BaseChunk section, ClientCanon canon) {
        if (section instanceof Chunk_v1_18 modern) {
            Palette palette = modern.getChunkData().palette;
            if (!(palette instanceof GlobalPalette)) {
                for (int i = 0; i < palette.size(); i++) {
                    if (canon.affects(palette.idToState(i))) {
                        return true;
                    }
                }
                return false;
            }
        }
        return true;
    }

    /** Replaces every state of a section that {@code canon} replaces; returns how many blocks changed. */
    private static int canonicalize(BaseChunk section, ClientVersion version, ClientCanon canon) {
        int changed = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int state = section.getBlockId(x, y, z);
                    int shown = canon.of(state);
                    if (shown != state) {
                        section.set(version, x, y, z, shown);
                        changed++;
                    }
                }
            }
        }
        return changed;
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
