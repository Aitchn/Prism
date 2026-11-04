# API gaps against large-mod workloads

The maintainers plan to test Prism by rebuilding mod-sized systems (a flora-magic mod, a storage-network mod and a
machines/energy mod) as plain plugins. This file tracks what the API cannot express yet. It is a working list, not a
promise. Update it when a milestone closes a gap or finds a new one.

| Area | Needed | Status |
|---|---|---|
| Items | definitions, components, use behavior | M1 done |
| Items | more events (hit, equip, tick in inventory), item tooltips, durability, cooldowns | open |
| Blocks | definitions, place/break/interact behaviors, display-entity appearance | M2 done |
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
- No Bedrock form/menu abstraction yet. Mods that need GUIs (AE2 terminals, Mekanism machines) need one `Menu` API
  with a container renderer for Java and a form renderer for Bedrock.
- Bedrock items are flat icons: no dynamic state and no 3D item models. Machines or tools that change their look
  with state need a separate item per state on Bedrock.
