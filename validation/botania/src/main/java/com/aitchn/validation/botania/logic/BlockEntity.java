package com.aitchn.validation.botania.logic;

/**
 * Per-block state of a Botania block. Instances are mutable and thread safe by themselves ({@code synchronized}),
 * because a burst or a flower in one Folia region may hand mana to a block owned by another region.
 */
public sealed interface BlockEntity permits ManaCollector, Flower, Apothecary {

    Pos pos();

    /** One-line text form kept in the DataStore. */
    String serialize();

    /** Advances one game tick. Runs on the thread that owns {@link #pos()}. */
    void tick(WorldView world, ManaNetwork network);

    /** Parses what {@link #serialize()} wrote. */
    static BlockEntity deserialize(Pos pos, String text) {
        String[] parts = text.split(";", -1);
        return switch (parts[0]) {
            case "pool" -> Pool.fromParts(pos, parts);
            case "spreader" -> Spreader.fromParts(pos, parts);
            case "flower" -> Flower.fromParts(pos, parts);
            case "apothecary" -> Apothecary.fromParts(pos, parts);
            default -> throw new IllegalArgumentException("unknown block entity '" + parts[0] + "'");
        };
    }
}
