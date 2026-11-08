# Mekanism on Prism (validation, not a product)

This module tests whether Prism can express a Mekanism-style mod as a plain plugin. It does not copy Mekanism and is
never published. Textures are my own flat placeholders.

It was first built before Prism had a block platform (PR #12, 13 gaps in [docs/gaps.md](../../docs/gaps.md)). Prism
then gained the generic pieces ([ADR-0007](../../docs/adr/0007-block-platform.md) to
[ADR-0010](../../docs/adr/0010-recipes.md)) and this is the rebuild on top of them. **Nothing Mekanism-specific is in
Prism**: joules, chemicals, machine recipes and the energy tablet are all in this plugin.

## What uses which Prism piece

| Mekanism part | Prism piece |
|---|---|
| Machines, generators, cables and tubes know they exist (also after a restart) | `BlockPlaceBehavior`, `BlockLoadBehavior` (load and unload), `BlockRemoveBehavior` |
| Energy, progress, items and tanks survive restarts and vanish with the block | per-block data (`CustomBlock.data()`), written every 5 s, on unload and on removal |
| Machines work without being touched | `BlockTickBehavior` (one task per chunk, region-correct) |
| Cables find machines, the tablet draws from any energy block | `BlockCapability` (`mekanism:energy`, `mekanism:chemical`) and `Prism.blocks().capability` |
| Machine GUI with input slot, output slot, energy meter, progress arrow, tanks, live refresh | `ContainerMenu` with `SlotRole.INPUT` / `OUTPUT`, `MenuView.setIcon`, `refreshTicks` |
| Machine looks different while working | `prism:variants: [idle, active]` and `BlockService.setVariant` (Java and Bedrock) |
| Energy Tablet keeps its charge in the item and shows it | `ItemService.editData`, `ItemLoreBehavior`, `ItemUseBehavior` with the clicked block |
| Machine recipes are data other mods can extend | `registerRecipeType("machine", ...)`, files in `prism/recipes/machine/` |
| Every item can be made in survival | Prism's `prism:shaped`, `prism:shapeless`, `prism:smelting`, `prism:blasting` recipes (real vanilla recipes) |

## Playing it (survival)

1. **Ores.** There is no world generation yet, so as a stopgap ore items are crafted: osmium ore = raw iron + lapis,
   tin ore = raw copper + clay ball, lead ore = raw iron + coal, uranium ore = raw gold + slime ball. A combiner turns
   a dust plus two cobblestone back into ore.
2. **Casing, cables, tubes.** Craft them from iron, osmium ingots, redstone and glass. Dusts smelt (or blast) into ingots.
3. **Power.** A *Solar Generator* charges in daylight with open sky. Join it to machines with *Universal Cables* (any
   face contact). Place cables, then right click a machine.
4. **Machines.** Right click opens the GUI. Put the ore in the left slot, take the result from the right slot. The
   machine's look changes while it works.
   - Enrichment chamber: ore to 2 dust (and dirty dust to dust). Crusher: ingot to dust, clump to dirty dust, gems to dust.
   - Electrolytic separator: right click it with a **water bucket** to fill 1,000 mB, then it makes hydrogen and oxygen.
   - Purification chamber (needs oxygen) and chemical injection chamber (needs hydrogen; Mekanism uses hydrogen chloride,
     this rebuild has no chlorine chain) give 3 clumps / 4 shards from one ore.
   - **Pressurized tubes** carry gas from the separator's output tanks to the other machines' input tanks.
5. **Metals (stage 1).** All seven metals (osmium, tin, lead, uranium, iron, gold, copper) have the same chain:
   ore -> enrichment (2 dust), purification with oxygen (3 clumps) or injection with hydrogen (4 shards; shards go
   through purification to clumps); clump -> crusher -> dirty dust -> enrichment -> dust -> furnace or energized
   smelter -> ingot. Nuggets and storage blocks are crafted.
6. **Alloys.** The metallurgic infuser takes a main item and an "infuse" item next to it: iron ingot + coal makes
   enriched iron, enriched iron + coal makes steel dust, copper ingot + tin dust makes bronze dust, iron + redstone
   makes an infused alloy, + diamond dust reinforced, + refined obsidian atomic. The osmium compressor makes refined
   obsidian and glowstone ingots (obsidian or glowstone dust + osmium dust). Control circuits are crafted from alloys.
7. **Upgrades.** Right click a machine holding a speed or energy upgrade (up to eight of each): speed adds one
   progress per tick per upgrade (and costs as much more energy), each energy upgrade saves 8%. A picked-up machine keeps
   its upgrades, energy and tank contents in the item (items in its slots drop separately); placing it restores them.
8. **Energy cubes** (four tiers) are batteries: cables fill them and drain them. **Precision sawmill:** logs to six
   planks. **Combiner:** dust + 2 cobblestone to ore.
9. **Chemistry (stage 2).** Gases and slurries are chemicals in tanks; pipes move them between machines.
   - Gas plant: *salt* in a chemical oxidizer makes chlorine, *sulfur dust* makes sulfur dioxide. A chemical infuser
     combines two gases: hydrogen + chlorine = hydrogen chloride, sulfur dioxide + oxygen = sulfur trioxide, sulfur
     trioxide + water = sulfuric acid (the last two on two separate infusers). Salt and sulfur dust are crafted for now
     (salt = bone meal + sand, sulfur dust = gunpowder + coal dust).
   - **5x chain:** ore + sulfuric acid in a *chemical dissolution chamber* gives dirty slurry; the *chemical washer*
     (slurry + water) makes clean slurry; the *chemical crystallizer* makes five crystals per ore. A crystal goes to the
     *injection chamber* with hydrogen chloride to become a shard, then the usual clump -> dirty dust -> dust -> ingot.
     The ore -> 4 shards step of stage 1 now needs hydrogen chloride too, as in the real mod.
   - **Tanks and faces.** A machine has up to two input tanks and two output tanks. By default every face of a machine
     talks to all of them, which is what a single gas needs. With two gases that goes wrong (the wrong gas fills the
     tank first), so right click a face with a **Configurator** to cycle what that face does: all tanks, first input,
     second input, first output, second output, off. Faces are kept when the machine is picked up and in saves.
   - Water goes into whichever tank takes it (washer and infuser: the second tank) with a bucket. Pipes share what a
     tank has between the machines that accept it, so one thirsty machine does not take everything.
   - *Pressurized reaction chamber* and plastics: bio fuel (crusher, from crops) + water + hydrogen = substrate;
     substrate + hydrogen + water = ethylene; ethylene + oxygen = HDPE pellet; four pellets craft a sheet, a sheet
     crafts two rods.
   - *Chemical tank* (any gas, 64,000 mB) and *fluid tank* (water, 32,000 mB) store chemicals; pipes fill and drain
     them.
10. **Item transport.** Logistical transporters join machines into an item network. Hopper on a machine: auto-eject
   on or off; Item Configurator (Configurator + hopper, shapeless): item access per face; redstone dust: redstone mode. Cables, tubes and
   transporters come in four tiers (8 of the tier below around the matching alloy).
11. **Factories.** Nine kinds (smelting, enriching, crushing, compressing, combining, purifying, injecting,
   infusing, sawing) in four tiers with 3, 5, 7 and 9 lanes. A factory is one block with the recipes of its machine;
   it runs up to `lanes` copies of a recipe at once (same time, energy and inputs times the copies), so it behaves
   like several machines side by side. Tiers are crafted from the tier below with circuits and alloy.
12. **Digital miner.** Needs power. It looks at 32 positions a tick in a square of columns below it, layer by layer
   (radius 4, 8, 12 or 16: right click with a compass), and breaks ores (any `*_ore` block and ancient debris, never a
   Prism block). Drops go into its output slot (use auto-eject and transporters); what does not fit drops beside it.
   1,000 J per mined block. Positions are looked at on the thread that owns them (Folia safe).
13. **Teleporter.** Right click travels to the next teleporter with the same frequency; a name tag with a name tunes
   it (empty name = public). The clicked teleporter pays 2,000 J plus 20 J per block (50,000 J for another world).
14. **Dynamic tank.** Build a closed hollow box (3 to 18 blocks a side) of Dynamic Tank blocks; replace some with Dynamic
   Valves (Dynamic Tank + hopper) and connect pipes to them. The interior stores 16,000 mB per block, chemicals only,
   and its content is kept when chunks unload or the server restarts. Extra casing touching the box breaks it, and so does a non-air block inside (looked at every 5 seconds by the box's corner casing, only in chunks its region owns; Prism's structure service matches fixed patterns, not boxes of any size). The
   Energy Tablet reads it. Checked inside the plugin because Prism has no structure service yet.
15. **Energy Tablet.** Right click a machine or generator: push 10,000 J into it. Sneak + right click: draw 10,000 J out.
   Right click a cable or tube: read the network. The charge is in the item's lore.

Differences from the real mod that are on purpose for now: factories have one input and output slot instead of one per lane, infusion types are items in a second slot (no infusion
tank), the osmium compressor takes osmium dust instead of osmium gas, the sawmill has no sawdust, tiers exist only for
energy cubes, cables, tubes and transporters, ore and salt and sulfur are crafted, chemical and item faces can be configured but not energy faces, transporters have no filters or sorters and no colouring tool, the miner has no custom filters, the teleporter has no portal frame. [inventory.yml](inventory.yml) tracks all of it.

## Layout

- `logic/` is Bukkit-free and unit tested: `Machine` (with save/load), `EnergyBuffer`, `ChemicalTank`, `Networks`,
  `Slots` (keeps a menu and a live machine consistent), `Cuboid` (multiblock validator, not wired yet).
- `Grid` is the running world (machines, networks, one lock). `MachineMenu` is the GUI. `*Behavior` are the Prism
  behaviors. `MachineRecipe` is the recipe file format. `SelfTest` runs on a real server (see below).
- Content: `src/main/resources/prism/{blocks,items,recipes}`.

## Checks

- Unit tests: logic, recipe files run on the machine logic, **every item can be obtained in survival** (a fixpoint over
  all recipes), every item and look has a texture.
- Smoke test (CI, real Paper, Folia and Purpur servers, no client): `MEKANISM_SELFTEST_OK` places a factory near spawn
  and proves placement, the capability lookup, energy over a cable, a data-driven recipe, per-block data, gas over a
  tube, and removal.
- Not covered by CI (needs clients): see "Hand tests" in [docs/gaps.md](../../docs/gaps.md).

## Coverage and chain tests

`inventory.yml` lists what the real base module has and marks each entry `done | partial | missing`.
`InventoryTest` prints `MEKANISM_COVERAGE` and fails below the target of the current stage. `ChainTest` runs
solar, cables, pipes and machines together in an in-memory world (`TestWorld` over the real `Plant`), including
unload/reload in the middle, broken cables, full outputs and random placing and breaking.
