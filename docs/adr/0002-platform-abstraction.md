# ADR-0002: Platform abstraction and Folia support

Status: Accepted (2026-11-04)

## Context

Prism-old compiled against `folia-api` and called region schedulers directly in 77 files, so it was only
trustworthy on Folia. Prism must run on Paper, Folia and Purpur from one jar, on a Minecraft version that is not
fixed (first development target 26.3, floor 1.21.4 for `item_model`).

## Decision

**One jar, runtime detection.** `PlatformDetector` probes marker classes (Folia first, then Purpur, then Paper) and
rejects anything else. The result and the Minecraft version form a `PlatformInfo` (in `prism-api`, registered as a
Bukkit service). `PlatformInfo` lives in the API rather than the SPI because creators legitimately ask "what am I
running on?"; the SPI keeps only what is internal.

**Feature flags, not version checks.** `Feature` declares the minimum Minecraft version (and optional platform) of
each capability. `FeatureMatrix.of(version, platform)` answers `supports(Feature)`. Code that needs a capability asks
the matrix; unsupported means "report it", never "crash". Versions are never hard-coded outside the `Feature` table.

**`PlatformScheduler` is the only door to threads.** Operations: `runGlobal`, `runAt(Location)`, `runOn(Entity,
retired)`, `runAsync`, delayed and repeating region tasks, and `isOwnedByCurrentThread` for location and entity.
Tasks are always enqueued, never run inline, so behaviour is identical on both platforms. `Paper` and `Purpur` use
the main thread (`PaperPlatformScheduler`); `Folia` uses global, region, entity and async schedulers
(`FoliaPlatformScheduler`). The plugin instantiates exactly one of them, so the other class is never loaded.

**Compile against `paper-api` only.** Paper's API already declares the region/entity scheduler types (Paper runs them
on the main thread), so no module needs `folia-api`. The Folia module is the only one allowed to reference
`io.papermc.paper.threadedregions..`; ArchUnit fails the build otherwise, and also if `prism-core` ever imports a
platform module. Purpur has no module of its own: it runs the Paper scheduler, and `PlatformKind.PURPUR` unlocks
optional Purpur features later.

**Startup self check.** On enable, Prism runs a global, an async and a region task, asserts thread ownership in the
region task, then logs `PRISM_READY platform=<KIND> minecraft=<VERSION> regionized=<bool>`. The Docker smoke test
(`smoke-test/`) waits for that line on Paper, Folia and Purpur.

## Alternatives considered

- **Separate `Prism-Paper.jar` and `Prism-Folia.jar`.** Users must pick correctly; two test matrices. Rejected.
- **Always use the region schedulers** (they work on Paper). Hides ownership bugs on Paper and relies on Paper's
  emulation staying faithful. An explicit SPI also keeps `BukkitScheduler` semantics available and testable.
- **Version `if` chains.** The mess Prism-old's feature probing led to. Rejected in favour of the table.

## Consequences

- Cross-region work is request-and-callback with no atomicity guarantee. The API documentation must say so.
- The build uses a dynamic `paper-api` version (`26.2.build.+`) until a milestone needs a pinned snapshot; the
  runtime is verified on 26.3 by the smoke test.
- Open (from the planning doc): safe usage of PacketEvents and BetterModel on Folia region threads, and a startup
  version-range check for both. M0 only proves they load beside Prism.
