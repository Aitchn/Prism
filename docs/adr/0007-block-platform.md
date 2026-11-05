# ADR-0007: Block platform (lifecycle, per-block data, ticking, capabilities)

Status: Accepted (2026-11-05). Server-side parts are covered by the smoke test; what a player sees is not (see below).

## Context

The Botania, AE2 and Mekanism rebuilds all had to re-implement the same things around custom blocks: finding them
again after a restart, keeping per-block state, ticking, talking to neighbours and breaking them in survival.
[platform-triage.md](../platform-triage.md) decides which of those are Prism's job.

## Decision

- **Index.** Which custom block is where is stored in the chunk's persistent data (`prism:b/<x>/<y>/<z>` → key, facing,
  data) and mirrored in memory for loaded chunks. `BlockService.get/identify` are constant time. Worlds from before the
  index are migrated from the display entity markers when their entities load.
- **Per-block data.** `CustomBlock.data()` is a `PersistentDataContainer` stored inside that entry: saved with the
  chunk, removed with the block, typed with Bukkit's `PersistentDataType`. Every change is written back at once, so
  there is no save call and nothing is lost on a crash beyond what the server itself loses.
- **Lifecycle behaviors.** `BlockPlaceBehavior` (player or mod, after the block exists), `BlockRemoveBehavior` (any
  cause, before the data goes), `BlockBreakBehavior` (player, cancellable), `BlockLoadBehavior` (chunk load including
  chunks loaded before Prism, and unload), `BlockNeighborBehavior` (adjacent custom or vanilla placement/break done by
  players or through `BlockService`).
- **Ticking.** `BlockTickBehavior` with an interval. All ticking blocks of a chunk share one region task, started when
  the chunk activates and stopped when it unloads.
- **Scheduler.** `Prism.scheduler()` exposes the platform scheduler and documents the cross-region rule.
- **Capabilities.** `BlockCapability<T>` is a key plus an interface; `BlockCapabilityProvider` answers it per side;
  `BlockService.capability` looks it up. Prism answers only `PrismCapabilities.INVENTORY` for vanilla containers.
  Energy, fluids, mana and the like are defined by mods; Prism never ships those types.
- **Interaction.** `BlockContext` carries the custom block, clicked face, hit position, hand and held item;
  `ItemUseContext` carries the clicked block and face. Right clicks in the air now reach item behaviors (they were
  dropped because Bukkit reports them as cancelled).
- **Placement.** `prism:rotation` (`horizontal`/`all`) stores a facing chosen from the placer and rotates the display.
  Placement is refused inside entities and fires a `BlockPlaceEvent` first so protection plugins can veto it.
- **Survival breaking.** The `barrier` carrier cannot be broken by a survival client. Prism runs the dig itself from
  `prism:mining` (`hardness` seconds by hand, `tool` type), shows the crack and an action-bar progress bar, and ends
  with a real `BlockBreakEvent`.
- **Getting items.** `/prism give` and the `/prism items` browser (chest menu, so it works through Geyser).

## Consequences

- Mods no longer need their own position maps, region schedulers or chunk listeners.
- The crack animation is drawn on the carrier; for invisible carriers only the progress bar and sounds show. The
  Bedrock visibility work (native block states, Geyser mappings) addresses this together with Bedrock block display.

## Not verified in CI (needs a real client)

- Survival breaking on Java and through Geyser: timing, progress bar, drop, particles.
- Placement facing visible on the display entity; placing next to and on top of custom blocks.
- `/prism items` clicking on Java and Bedrock.
