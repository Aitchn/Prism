package com.aitchn.prism.api.block;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

/**
 * A multiblock structure that is standing.
 *
 * @param controller the controlling block
 * @param facing     for a pattern ({@code prism:structure}) the way it faces: {@code NORTH} as written, or turned to
 *                   {@code EAST}, {@code SOUTH} or {@code WEST}; for a box ({@code prism:cuboid}) the way from the
 *                   controller into the inside, any of the six faces
 * @param members    every non-air block of the structure except the controller (for a box, the shell and the allowed
 *                   blocks inside)
 * @param min        the lowest corner of the structure's bounding box, controller included
 * @param max        the highest corner; for a box, the inside is everything strictly between {@code min} and
 *                   {@code max}
 */
public record FormedStructure(@NotNull Block controller, @NotNull BlockFace facing, @NotNull List<Block> members,
        @NotNull Block min, @NotNull Block max) {

    public FormedStructure {
        members = List.copyOf(members);
    }
}
