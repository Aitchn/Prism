# API gaps against large-mod workloads

The maintainers plan to test Prism by rebuilding mod-sized systems (a flora-magic mod, a storage-network mod and a
machines/energy mod) as plain plugins. This file tracks what the API cannot express yet. [platform-triage.md](platform-triage.md) decides, for every
gap below, whether it belongs to Prism or to the mod. It is a working list, not a
promise. Update it when a milestone closes a gap or finds a new one.

| Area | Needed | Status |
|---|---|---|
| Items | definitions, components, use behavior | M1 done |
| Items | more events (hit, equip, tick in inventory), item tooltips, durability, cooldowns | open |
| Blocks | definitions, place/break/interact behaviors, display-entity appearance | M2 done |
| Blocks | place/remove lifecycle behaviors | done (AE2 validation) |
| Blocks | block states/rotation from placement, hardness and tool rules, sounds, light, redstone | open |
| Blocks | very large counts (one display entity per block) | open; NativeStateBackend in M5 |
| Blocks | place/remove/load/neighbour lifecycle, facing, survival breaking | done (ADR-0007) |
| Block state | per-block persistent data, chunk-safe | done (ADR-0007, `CustomBlock.data()`) |
| Ticking | block entities that tick on the right Folia region | done (ADR-0007, `BlockTickBehavior`) |
| Multiblocks | structure patterns, formed/unformed events, cross-region requests | open |
| Transport | capability lookup between neighbours | done (ADR-0007); resource types stay in mods (see platform-triage.md) |
| GUI | `Menu` abstraction with slots, Java container + Bedrock form | M3 |
| Recipes | machine and crafting recipes as registry content | open |
| World | ore/flora generation, structures | open |
| Entities | custom mobs, BetterModel animations | M5 |
| Network scale | storage networks with many thousands of items need async-safe indexes | open |

## Found in M3 (Bedrock)

- ~~Bedrock registration with Geyser~~: Prism now writes its pack and item/block mappings into a local Geyser's
  folders, or to `plugins/Prism/geyser/` for a proxy (ADR-0008). A Geyser restart is still needed after content
  changes; a live Geyser extension is not implemented.
- ~~Menus with slots and live updates~~: `ContainerMenu` (ADR-0009). Text input and a native Bedrock form renderer
  are still open.
- Bedrock items are flat icons: no dynamic state and no 3D item models. Machines or tools that change their look
  with state need a separate item per state on Bedrock.

## Found in M5 (data)

- `DataStore` is a string key-value store. Machines and networks (AE2, Mekanism) need transactional multi-key writes,
  per-chunk or per-block attachment with automatic cleanup on unload/removal, and async access; none exist yet.

## Found in the AE2 validation

Details are in [validation/ae2.md](validation/ae2.md). The first version found the gaps that the block platform, slot
menus, item data and recipes (ADR-0007 to ADR-0010) then closed; AE2 has been migrated to them. What remains:

- A slotless item-handler capability (or a virtual `Inventory`) so a network's storage can feed hoppers and other mods.
- Connection-dependent block shapes and thin hitboxes (cables).
- Text input in menus (terminal search, crafting amounts).
- Batched variant switching for many blocks.
- Recipes that take items with per-stack data.
- Per-player translated lore; world generation for certus ore.

## Found in the Mekanism rebuild (`validation/mekanism`)

What was built: energy buffers, five recipe machines (enrichment, crusher, purification, injection, electrolytic
separator) forming the osmium ore chain, a solar generator, cable and pipe networks, a hollow-cuboid multiblock
validator, and the YAML content (9 blocks, 11 items). The logic is Bukkit-free and unit-tested; only a thin adapter
touches Prism. Items below are what Prism could not express and the mod had to work around or leave out.

1. **No block lifecycle events.** There is no placed / loaded / unloaded / neighbour-changed hook for custom blocks,
   only `BlockInteractBehavior` and `BlockBreakBehavior`. The mod registers a machine lazily on first right click, so
   a placed cable or generator does nothing until touched, and after a restart nothing is re-registered.
   Needed: `BlockPlaceBehavior`, chunk load/unload events and a "all custom blocks in this chunk" query.
2. **No block entity / per-block data.** Machine state (energy, items, tanks, progress) lives in the mod's own
   `Map<Pos, Machine>`. `DataStore` is a global key-value store with no chunk attachment, so persistence was not
   written. Needed: data attached to a block position, saved with the chunk, removed with the block, typed.
3. **Ticking is not in the API.** `PlatformScheduler` is internal. The mod calls Bukkit's `RegionScheduler` directly,
   which only works because it targets Paper/Folia. Cross-region work (a cable network spanning regions) needed a
   coarse lock and is not truly Folia-safe. Needed: a public `Tickable` capability plus a cross-region
   message/queue primitive.
4. **No capability lookup between blocks.** Cables find machines through a position map. Mekanism, AE2 and Botania all
   need "give me the energy/item/fluid/chemical handler on this face of the neighbour". Needed: a generic
   capability lookup (`BlockCapability<T>`) on `BlockService`, with sided access.
5. **No shared resource types.** Energy, gas and water were invented by the mod, so a second mod cannot plug into
   them. Needed: standard `Energy`, `Fluid`, `Chemical` handler interfaces in `prism-api` (or a documented
   convention), including simulate/commit semantics.
6. **Interaction context is too thin.** `BlockContext` has no clicked face, hand item or hit position, and
   `ItemUseContext` has no clicked block or face. Directional machines, wrenches and custom placement rules cannot be
   written. Needed: face, hit vector, hand and held stack on both contexts.
7. **Menus have no slots.** `MenuSpec` is a button list, so the machine GUI is faked with "insert held item" and
   "take output" buttons. Needed: real slot inventories with input/output slot rules, progress bars, and live
   updates (see "Found in M3").
8. **No recipe registry.** Recipes are Java objects in `Catalog`. Needed: a `RecipeType` registry loaded from
   `prism/recipes/*.yml`, so numbers and recipes can be data, and so other mods can add recipes (Mekanism and AE2
   both rely on this). Also no way to hook furnace recipes for dust smelting or to add vanilla-style crafting
   recipes for custom items.
9. **No multiblock support.** `Cuboid` validates a frame but nothing triggers re-validation. Needed: a structure
   service that listens to member block changes, reports formed/unformed and exposes the formed structure as one
   object (inventory, tank, controller).
10. **Block state and look.** A machine cannot show "active" vs "idle", or its facing, because blocks have exactly
    one appearance. Needed: block state properties mapped to appearances.
11. **World generation is absent.** Ores exist as items only; no ore blocks, no generation hook.
12. **Scale.** One display entity per block is fine for machines, not for a base with hundreds of cables. A
    `NativeStateBackend` (M5) or a batched renderer is needed for cable-heavy builds.
13. **Not attempted:** radiation, QIO storage, jetpacks/armor, configuration cards, side configuration, upgrades,
    teleporters. They depend on 1-7 and add no new gap categories.

## Mekanism rebuilt on the platform layer (`validation/mekanism`, after ADR-0007 to ADR-0010)

Gaps 1-4, 6-8, 10 and 12 of the section above are closed by Prism's block platform, visibility, menu, item-data and
recipe work; the Mekanism plugin was migrated and now uses all of them (see `validation/mekanism/README.md`). Gap 5
stayed plugin-owned on purpose (the energy and chemical types are in the plugin). Nothing Mekanism-specific was added
to Prism. Gaps 9 and 11 are still open (platform-triage rows 23 and 28).

New findings from the migration, each checked against the rule "would a mod with a completely different theme need
it?":

1. **No block drop customisation (Prism).** `drop:` is a fixed item. A machine cannot drop an item that carries its
   state, and `BlockContext.heldItem()` is null in `onPlace`, so a placed item's data cannot reach the new block
   either. Any block that carries data (a chest keeping its contents, a charged battery) needs both directions: a way to
   fill the dropped stack from the block's data and to read the placing stack in `onPlace`. Mekanism machines therefore
   lose their energy when broken; their items are dropped by the plugin in `BlockRemoveBehavior`.
2. **A click that did nothing still counts as handled (Prism).** Any `BlockInteractBehavior` that runs swallows the
   click (no item use, no placement against the block), even without `cancel()`. The Mekanism cables and tubes therefore
   have no interact behavior and the Energy Tablet reads them through item use. Sneaking already bypasses block
   interaction. A way to say "not mine, let it through" would let one behavior serve both.
3. **Menu clicks arrive a tick late (Prism, design note).** `onSlotChanged` reports a click one tick after it happened,
   while a live machine keeps changing the same slots. The plugin needed `Slots.reconcileInput/Output` and a rule to
   redraw a slot only when no click is waiting. Any machine GUI would need the same; a synchronous accept/reject hook
   or a documented helper would remove the guesswork. A tiny window remains where a machine consumes input in the same
   tick a player takes it (at most one operation's input).
4. **No global timer in `PrismScheduler` (Prism, small).** There is only a one-shot `runGlobal`, and calling it from
   inside itself runs in the same tick forever (it hung the smoke servers), so the plugin uses Bukkit's global region
   timer directly. A `runGlobalTimer` in `PrismScheduler` would remove that.
5. **Block look from neighbours (Prism, later, rows 13-14).** Cables are full cubes with one look. Cables that visibly
   connect need a look chosen from a neighbour mask and non-full hitboxes. Variants are named looks only.
6. **Ore needs world generation (Prism, row 28).** Ore items are crafted as a stopgap so every item is obtainable.
7. **Unsaved window (plugin).** Machine state is cached and written every 5 s, on unload and on removal; a crash loses
   at most 5 s of progress. Plugin disable cannot write because it is not on the owning thread.
8. **Plugin-owned and kept there:** energy and chemical semantics, the machine recipe format, conduits and their limits,
   the tablet, the hydrogen-for-hydrogen-chloride simplification, `Slots`, the global lock.

### Hand tests (Java client, and Bedrock through Geyser)

CI cannot join a client. Run each on a Java client and on a Bedrock client (after one Geyser restart for new content).

1. Craft everything: the recipe book lists ore, ingots, casing, cables, tubes, all five machines, the generator and the
   tablet; each crafts in a crafting table; dusts smelt in a furnace and a blast furnace. A custom ingredient cannot be
   used as its carrier (a dust is not paper).
2. Place a solar generator, two cables, an enrichment chamber. Blocks are visible with their textures; right click the
   generator in daylight: the action bar shows stored energy rising. Break and re-place: the block drops its item.
3. Right click the enrichment chamber: the GUI shows the energy meter rising, put an osmium ore in the left slot,
   shift-click from the inventory works, the arrow fills, 2 osmium dust appear in the right slot, take them (click and
   shift-click). The block's look switches to the lit texture while working and back afterwards.
4. Restart the server mid-operation: the machine keeps its energy, items and progress.
5. Break a machine that holds items: they drop. Break it while another player has the GUI open: that player's items are
   handed over once, nothing is duplicated.
6. Separator: right click with a water bucket (becomes an empty bucket), connect a tube to a purification chamber:
   oxygen arrives (tank icons update), hydrogen is not accepted there; the injection chamber takes hydrogen.
7. Energy Tablet: crafted empty; sneak + right click the generator draws energy and the lore shows the charge; right
   click a machine pushes it; right click a cable shows the network line; the lore survives a relog and a chest.
8. Two players on one machine: the second sees "Someone else is using this machine".
9. Bedrock only: all GUI slots clickable by touch and controller, item icons show for custom items, the machine looks
   switch.

