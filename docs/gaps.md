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

- Bedrock registration with Geyser (event on the same server, extension on a proxy) is not implemented yet; only the
  pack, mappings and HTTP distribution exist (M3b).
- `MenuSpec` covers button lists only (chest renderer, Bedrock via Geyser's container translation). Machine and
  terminal GUIs (AE2, Mekanism) need slots, live updates, text input and a native Bedrock form renderer.
- Bedrock items are flat icons: no dynamic state and no 3D item models. Machines or tools that change their look
  with state need a separate item per state on Bedrock.

## Found in M5 (data)

- `DataStore` is a string key-value store. Machines and networks (AE2, Mekanism) need transactional multi-key writes,
  per-chunk or per-block attachment with automatic cleanup on unload/removal, and async access; none exist yet.

## Found in the AE2 validation

Details and workarounds are in [validation/ae2.md](validation/ae2.md).

- Per-stack item data (cells, encoded patterns) and a canonical "same item" key: not in `ItemService`.
- Per-block data, listing custom blocks in a chunk, chunk load/unload callbacks, and cleanup when a block vanishes.
- `DataStore`: blocking, string-only, no transactions.
- `MenuSpec`: no slots or dragging, no click type, no live refresh, no text input, no fixed button positions.
- Block appearance: no neighbour-dependent models, thin hitboxes, per-state looks or placement facing.
- No neighbour-update callback or multiblock helper.
- No capability lookup between blocks (energy, items, fluids); a mod cannot feed or drain another mod's machines.
- No recipe registry, creative tab or item list for mod content.
- Closed by the block platform (#15) after AE2 was written: place/remove behaviors, the placing player, facing and per-block data. AE2 has not been migrated to them yet.
- No public runtime lookup of item and block definitions.
- Folia: grid-style state spans regions; the rule and the schedulers mods may use are not documented.

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
