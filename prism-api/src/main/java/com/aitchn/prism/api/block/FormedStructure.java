package com.aitchn.prism.api.block;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

/**
 * A multiblock structure ({@code prism:structure}) that is standing: its controller, the way the pattern faces
 * ({@code NORTH} as written, or turned to {@code EAST}, {@code SOUTH} or {@code WEST}) and every member block the
 * pattern checked, in pattern order.
 */
public record FormedStructure(@NotNull Block controller, @NotNull BlockFace facing, @NotNull List<Block> members) {

    public FormedStructure {
        members = List.copyOf(members);
    }
}
