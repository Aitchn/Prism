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


### Stage 1 additions (hand tests)

10. Metal chain on Java and Bedrock: from one raw iron to an iron ingot through purification (oxygen), crusher,
    enrichment chamber and energized smelter; repeat with osmium through the injection chamber (4x). Cables and
    tubes join everything; an energy cube charged with the tablet powers the line.
11. Second slot: in a combiner and in a metallurgic infuser the extra slot sits right of the main slot; shift-click
    puts coal/redstone/tin dust into the right slot; other machines show a grey pane there.
12. Upgrades: right click a machine with a speed upgrade ten times: the action bar counts to 8 / 8, then says it
    cannot take more; the arrow fills much faster. Break the machine (survival) and place the dropped item again: the
    upgrades and the stored energy are still there.
13. Energy cube: place next to a cable; it charges from a generator and feeds machines when the generator is gone; its
    GUI shows the meter. Break it and place it again: the charge is kept.
14. All eight storage blocks place and break; nuggets/ingots/blocks craft both ways.

### Stage 2 additions (hand tests)

15. Gas plant on Java and Bedrock: separator filled with a water bucket, a chemical oxidizer with salt, a chemical
    infuser between them; use the Configurator on the infuser's faces (action bar names the face and its mode) so
    hydrogen enters the first tank and chlorine the second; hydrogen chloride appears in the output tank and the tank
    icons change (green dye).
16. The 5x chain by hand: sulfur -> acid line into the dissolution chamber, an osmium ore in its slot; slurry in the
    washer (put water in with a bucket), crystals in the crystallizer, five crystals out. Carry them to the injection
    chamber, then purification, crusher, enrichment chamber and smelter: five ingots from one ore.
17. Second tank slot: machines with two gas inputs show a second tank icon left of the first; other machines show a
    grey pane there.
18. Pick up and place a configured machine: faces, upgrades and tank contents come back.
19. Storage tanks: pipes fill a chemical tank, a second pipe on another face drains it into a machine; the fluid tank
    only takes water.

### Stage 3 additions (hand tests)

20. Item line without hands: enrichment chamber -> logistical transporter -> a second machine (for example a crusher,
    fed with ore dust or ingots). Click the enrichment chamber with a hopper: action bar says "Auto-eject on". Items
    walk across; with auto-eject off they stay.
21. Item faces: right click a machine face with the Item Configurator (Configurator + hopper) cycles input and output / input only / output
    only / nothing for transporters. Chemical faces (no sneaking) behave as before.
22. Redstone: right click with redstone dust cycles ignore / with a signal / without a signal. A lever next to the
    machine starts and stops it.
23. Tiers: advanced, elite and ultimate cables, tubes and transporters are crafted from 8 of the tier below around the
    matching alloy. A long cable run of basic cables starves a big machine; swap in ultimate cable and it fills.
24. Colours: only the logistical transporter network with equal colour joins; there is no colouring tool yet, so on a real server
    every transporter is uncoloured.

### Stage 4 additions (hand tests)

25. Factories: craft a Enriching Factory (iron, basic circuits, steel casing around an enrichment chamber), feed it
    a stack of osmium ore: it works on three ores at once (lore/GUI show the same single input slot) and makes six
    dust in the time one chamber needs for two.
26. Tier up: surround the basic factory with alloy and circuits to get the advanced one (5 lanes), and so on to the
    ultimate (9). Pick it up and place it again: energy, upgrades and tank contents stay.
27. A factory feeding a second factory through a logistical transporter (item configurator for faces): a stack
    walks through both without hands.

### Stage 4b additions (hand tests)

28. Digital miner: place it above an ore vein, feed it power (cable or energy tablet), watch the GUI output slot fill
    with raw ore. Use a compass on it to change the radius. With a full output slot the next drops fall beside it.
29. Teleporter: place two, tune both with a name tag called "home" (anvil rename), power both. Right click one: you
    arrive on the other; the clicked one loses 2,000 J plus 20 J per block. With no power the action bar says how much
    it needs.
30. Dynamic tank: build a 3x3x3 hollow box of Dynamic Tank blocks, swap one for a Dynamic Valve. Pipe a gas into the
    valve: the tablet shows the filled amount (16,000 mB per interior block). Remove one casing: the tablet says it is
    not a complete box; put it back: the content is still there. Restart the server: the content survives.

## Effects and visuals of the Mekanism rebuild (what is done, what Prism cannot present yet)

Done in the plugin with plain world calls (no Prism addition needed; Java and Bedrock both play vanilla sounds and particles):
working machines hum and spark, the miner shows a spark on every ore it takes, teleporters play portal particles and the
enderman sound at both ends. Idle and active textures, progress arrow, energy bar and tank icons are menu and variant
content that already exist.

Generic gaps found (a mod with another theme needs the same; none is Mekanism specific):

| # | Gap | Why it matters beyond Mekanism |
|---|-----|--------------------------------|
| E1 | Block light level per variant (machines glow while active) | any lit machine, lamp, furnace |
| E2 | Animated block and item textures (frame strips) in the pack, Java `.mcmeta` and Bedrock flipbook | any animated panel, fluid, glow |
| E3 | Non-full block shapes and hitboxes (already rows 13/14): thin cables with visible arms, glass tanks | pipes, rails, wires, fences of any mod |
| E4 | Appearance from neighbours (cable arms follow connections; already row 14) | connected blocks of any kind |
| E5 | Armor and held item models with custom rendering (jetpack, scuba gear, atomic disassembler) | any armor or tool mod |
| E6 | A looping sound bound to a block (stops when it goes), instead of the plugin repeating one on a timer | machines, jukebox style blocks |
| E7 | Persistent particle emitters or beams between two points (miner laser, teleporter column) | lasers, portals, rituals |
| E8 | Entities as block parts (rotating or moving parts such as the robit) | any moving machine |

Plugin side, not Prism: choosing which sound or particle a machine plays, and when.
