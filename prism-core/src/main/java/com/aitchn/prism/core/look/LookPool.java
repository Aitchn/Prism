package com.aitchn.prism.core.look;

import com.aitchn.prism.core.block.NoteBlockStates;
import com.aitchn.prism.core.block.TripwireStates;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * The catalogue of vanilla block states Prism borrows for custom looks on Java (the S tier of ADR-0023), and how a real
 * block of those families is shown instead. Each family was checked against the game's blockstate files: the borrowed
 * states and their canonical form pick the same model (see {@code tools/verify-borrowed-families.py}). Order matters:
 * earlier families are handed out first, and the note block and tripwire families come first because Bedrock mappings
 * exist only for them (Geyser draws their states; see ADR-0023).
 *
 * <p>Left out on purpose, because a custom block drawn with one of their states would look or act differently from
 * what it is: mushroom blocks (the faces differ), anything waterlogged, fire and redstone ore (particles), leaves that
 * drop leaf particles (oak, jungle, acacia, dark oak, mangrove, cherry, pale oak), doors, trapdoors and fence gates
 * (the client changes their state on neighbour updates), and everything whose collision shape is neither a full cube
 * nor empty.
 */
public final class LookPool {

    private LookPool() {}

    private static final PhysicsClass WOOD_BLOCK = new PhysicsClass(Collision.FULL, true, SoundGroup.WOOD);
    private static final PhysicsClass STONE_BLOCK = new PhysicsClass(Collision.FULL, true, SoundGroup.STONE);
    private static final PhysicsClass GRASS_BLOCK = new PhysicsClass(Collision.FULL, true, SoundGroup.GRASS);
    private static final PhysicsClass GRASS_LEAVES = new PhysicsClass(Collision.FULL, false, SoundGroup.GRASS);
    private static final PhysicsClass STONE_PLANT = new PhysicsClass(Collision.NONE, false, SoundGroup.STONE);
    private static final PhysicsClass GRASS_PLANT = new PhysicsClass(Collision.NONE, false, SoundGroup.GRASS);

    /** The families, in the order they are handed out. */
    public static @NotNull List<BorrowedFamily> families() {
        List<BorrowedFamily> families = new ArrayList<>();
        families.add(noteBlock());
        families.add(tripwire());
        families.add(range("minecraft:target", GRASS_BLOCK, "power", 0, 15, Optional.empty(), Layer.SOLID,
                new ClientBreak(0.5, "hoe", false), state -> new ModelRef("minecraft:block/target")));
        families.add(facing("minecraft:dispenser", "dispenser"));
        families.add(facing("minecraft:dropper", "dropper"));
        families.add(leaves("minecraft:spruce_leaves", true));
        families.add(leaves("minecraft:birch_leaves", false));
        families.add(range("minecraft:sugar_cane", GRASS_PLANT, "age", 0, 15,
                Optional.of(StateRef.parse("minecraft:sugar_cane[age=15]")), Layer.CUTOUT,
                new ClientBreak(0, "none", false), state -> new ModelRef("minecraft:block/sugar_cane")));
        return List.copyOf(families);
    }

    /** The block ids of every family. */
    public static @NotNull List<String> blocks() {
        return families().stream().map(BorrowedFamily::block).toList();
    }

    /** The family of {@code block}, if it has one. */
    public static @NotNull Optional<BorrowedFamily> family(@NotNull String block) {
        return families().stream().filter(family -> family.block().equals(block)).findFirst();
    }

    /**
     * Every state of {@code family}'s block as the vanilla game of the verified version has them: the product of the
     * values the family lists plus the values it does not borrow. Stands in for the server's own list in tests.
     */
    public static @NotNull List<StateRef> defaultStates(@NotNull BorrowedFamily family) {
        Map<String, List<String>> all = new LinkedHashMap<>(DEFAULT_VALUES.getOrDefault(family.block(), Map.of()));
        family.borrow().forEach(all::putIfAbsent);
        List<Map<String, String>> product = new ArrayList<>();
        product.add(new LinkedHashMap<>());
        for (Map.Entry<String, List<String>> property : all.entrySet()) {
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> prefix : product) {
                for (String value : property.getValue()) {
                    Map<String, String> copy = new LinkedHashMap<>(prefix);
                    copy.put(property.getKey(), value);
                    next.add(copy);
                }
            }
            product = next;
        }
        List<StateRef> states = new ArrayList<>();
        product.forEach(props -> states.add(new StateRef(family.block(), props)));
        return states;
    }

    /** Values of properties a family does not borrow all of, as in the verified game version (the full state list). */
    private static final Map<String, Map<String, List<String>>> DEFAULT_VALUES = Map.of(
            "minecraft:note_block", Map.of("instrument", List.of("harp", "basedrum", "snare", "hat", "bass", "flute",
                    "bell", "guitar", "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo",
                    "pling", "trumpet", "trumpet_exposed", "trumpet_oxidized", "trumpet_weathered", "zombie",
                    "skeleton", "creeper", "dragon", "wither_skeleton", "piglin", "custom_head")),
            "minecraft:tripwire", Map.of("disarmed", List.of("false", "true")),
            "minecraft:spruce_leaves", Map.of("waterlogged", List.of("false", "true")),
            "minecraft:birch_leaves", Map.of("waterlogged", List.of("false", "true")),
            "minecraft:dispenser", Map.of("triggered", List.of("false", "true")),
            "minecraft:dropper", Map.of("triggered", List.of("false", "true")));

    // ---------------------------------------------------------------- the families

    /**
     * Note block: 16 instruments, 25 notes and powered give 799 states beyond the plain one, all drawn with one model.
     * The state is a number that real blocks keep changing (a player tunes it, a head above changes the instrument), so
     * real note blocks are always shown as harp, note 0, unpowered.
     */
    private static BorrowedFamily noteBlock() {
        Map<String, List<String>> borrow = new LinkedHashMap<>();
        borrow.put("instrument", NoteBlockStates.INSTRUMENTS);
        List<String> notes = new ArrayList<>();
        for (int note = 0; note < NoteBlockStates.NOTES; note++) {
            notes.add(Integer.toString(note));
        }
        borrow.put("note", notes);
        borrow.put("powered", List.of("false", "true"));
        Map<String, String> invisible = new LinkedHashMap<>();
        invisible.put("instrument", "harp");
        invisible.put("note", "0");
        invisible.put("powered", "false");
        return new BorrowedFamily("minecraft:note_block", WOOD_BLOCK, borrow, invisible, Map.of(),
                Optional.of(StateRef.parse("minecraft:note_block[instrument=pling,note=24,powered=true]")),
                Layer.SOLID, new ClientBreak(0.8, "axe", false), state -> new ModelRef("minecraft:block/note_block"),
                null);
    }

    /**
     * Tripwire with {@code disarmed=true} (vanilla string only takes it for the moment shears cut it): 64 states, no
     * collision, nothing hidden around them. {@code powered} is not drawn either, so real tripwire is shown unpowered and
     * armed. States with the fewest sides come first: the client connects sides to neighbouring strings and hooks.
     */
    private static BorrowedFamily tripwire() {
        Map<String, List<String>> borrow = new LinkedHashMap<>();
        List<String> both = List.of("false", "true");
        borrow.put("attached", both);
        borrow.put("disarmed", List.of("true"));
        borrow.put("east", both);
        borrow.put("north", both);
        borrow.put("powered", both);
        borrow.put("south", both);
        borrow.put("west", both);
        Map<String, String> invisible = new LinkedHashMap<>();
        invisible.put("disarmed", "false");
        invisible.put("powered", "false");
        Comparator<StateRef> fewestSides = Comparator.<StateRef>comparingInt(LookPool::sides)
                .thenComparing(StateRef::variantKey);
        return new BorrowedFamily("minecraft:tripwire", STONE_PLANT, borrow, invisible, Map.of(),
                Optional.of(StateRef.parse(
                        "minecraft:tripwire[attached=true,disarmed=true,east=true,north=true,powered=true,south=true,west=true]")),
                Layer.CUTOUT, new ClientBreak(0, "none", false), state -> {
                    var vanilla = TripwireStates.vanilla(bool(state, "attached"), bool(state, "east"),
                            bool(state, "north"), bool(state, "south"), bool(state, "west"));
                    return new ModelRef(vanilla.model(), 0, vanilla.y());
                }, fewestSides);
    }

    private static int sides(StateRef state) {
        int count = 0;
        for (String side : List.of("east", "north", "south", "west")) {
            count += bool(state, side) ? 1 : 0;
        }
        return count;
    }

    private static boolean bool(StateRef state, String property) {
        return "true".equals(state.properties().get(property));
    }

    /** A block whose only borrowed property is a number that its model ignores (target power, sugar cane age). */
    private static BorrowedFamily range(String block, PhysicsClass physics, String property, int min, int max,
            Optional<StateRef> shell, Layer layer, ClientBreak breaking, Function<StateRef, ModelRef> vanilla) {
        List<String> values = new ArrayList<>();
        for (int value = min; value <= max; value++) {
            values.add(Integer.toString(value));
        }
        return new BorrowedFamily(block, physics, Map.of(property, values), Map.of(property, Integer.toString(min)),
                Map.of(), shell, layer, breaking, vanilla, null);
    }

    /**
     * Dispenser and dropper: the six facings are drawn, {@code triggered} is not, so the six triggered states are
     * borrowed and real blocks are shown untriggered.
     */
    private static BorrowedFamily facing(String block, String model) {
        Map<String, List<String>> borrow = new LinkedHashMap<>();
        borrow.put("facing", List.of("north", "east", "south", "west", "up", "down"));
        borrow.put("triggered", List.of("true"));
        return new BorrowedFamily(block, STONE_BLOCK, borrow, Map.of("triggered", "false"), Map.of(), Optional.empty(),
                Layer.SOLID, new ClientBreak(3.5, "pickaxe", true), state -> switch (state.properties().get("facing")) {
                    case "east" -> new ModelRef("minecraft:block/" + model, 0, 90);
                    case "south" -> new ModelRef("minecraft:block/" + model, 0, 180);
                    case "west" -> new ModelRef("minecraft:block/" + model, 0, 270);
                    case "up" -> new ModelRef("minecraft:block/" + model + "_vertical");
                    case "down" -> new ModelRef("minecraft:block/" + model + "_vertical", 180, 0);
                    default -> new ModelRef("minecraft:block/" + model);
                }, null);
    }

    /**
     * Leaves of the kinds that only drip in the rain (spruce, birch): distance and persistent are not drawn, so the 14
     * dry states are borrowed (13 beyond the one real blocks are shown with). Leaves collide as a full cube and hide no
     * faces. Waterlogged leaves draw water and stay as they are.
     */
    private static BorrowedFamily leaves(String block, boolean withShell) {
        Map<String, List<String>> borrow = new LinkedHashMap<>();
        borrow.put("distance", List.of("1", "2", "3", "4", "5", "6", "7"));
        borrow.put("persistent", List.of("false", "true"));
        borrow.put("waterlogged", List.of("false"));
        Map<String, String> invisible = new LinkedHashMap<>();
        invisible.put("distance", "7");
        invisible.put("persistent", "false");
        String name = block.substring(block.indexOf(':') + 1);
        return new BorrowedFamily(block, GRASS_LEAVES, borrow, invisible, Map.of("waterlogged", "false"),
                withShell ? Optional.of(StateRef.parse(block + "[distance=6,persistent=true,waterlogged=false]"))
                        : Optional.empty(),
                Layer.CUTOUT, new ClientBreak(0.2, "hoe", false), state -> new ModelRef("minecraft:block/" + name), null);
    }
}
