# API gaps against large-mod workloads

The maintainers plan to test Prism by rebuilding mod-sized systems (a flora-magic mod, a storage-network mod and a
machines/energy mod) as plain plugins. This file tracks what the API cannot express yet. It is a working list, not a
promise. Update it when a milestone closes a gap or finds a new one.

| Area | Needed | Status |
|---|---|---|
| Items | definitions, components, use behavior | M1 done |
| Items | more events (hit, equip, tick in inventory), item tooltips, durability, cooldowns | open |
| Blocks | definitions, place/break/interact behaviors, display-entity appearance | M2 done |
| Blocks | place/remove lifecycle behaviors | done (AE2 validation) |
| Blocks | block states/rotation from placement, hardness and tool rules, sounds, light, redstone | open |
| Blocks | very large counts (one display entity per block) | open; NativeStateBackend in M5 |
| Block state | per-block persistent data, chunk-safe, versioned | open; carrier + display give identity only |
| Ticking | block entities that tick on the right Folia region | open; needs a `Tickable` capability on `PlatformScheduler` |
| Multiblocks | structure patterns, formed/unformed events, cross-region requests | open |
| Transport | item, fluid, energy and network capabilities between neighbours | open; needs a capability lookup API on blocks |
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
- `BlockService.place` has no placing player, so place behaviors see `player() == null`.
- No public runtime lookup of item and block definitions.
- Folia: grid-style state spans regions; the rule and the schedulers mods may use are not documented.
