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
9. **Energy Tablet.** Right click a machine or generator: push 10,000 J into it. Sneak + right click: draw 10,000 J out.
   Right click a cable or tube: read the network. The charge is in the item's lore.

Differences from the real mod that are on purpose for now: infusion types are items in a second slot (no infusion
tank), the osmium compressor takes osmium dust instead of osmium gas, the sawmill has no sawdust, tiers exist only for
energy cubes, ore is crafted. [inventory.yml](inventory.yml) tracks all of it.

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
