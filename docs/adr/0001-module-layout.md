# ADR-0001: Module layout and dependency direction

Status: Accepted (2026-11-04)

## Context

Prism is a server-side content library: Plugin (`Prism.jar`) -> Prism (API and runtime) -> Mod (content by
creators). The predecessor (Prism-old) mixed engine, content and platform code in one module, hard-coded Folia in 77
files and shipped verification code in the production jar. Creators need one small, stable artifact to compile
against, and the runtime must be testable without a server.

## Decision

A multi-module Gradle build (Kotlin DSL, version catalog, Java 25, MIT):

| Module | Role |
|---|---|
| `prism-api` | Public API. The only artifact creators depend on. |
| `prism-core` | Platform-neutral runtime. |
| `prism-platform:spi` | Internal SPI: `PlatformScheduler`, `ScheduledTask`, platform detection. |
| `prism-platform:paper`, `:folia` | Implementations of the SPI. |
| `prism-client` | Packet-level presentation. Hard-depends on PacketEvents. |
| `prism-model` | Blockbench model bridge. Hard-depends on BetterModel. |
| `prism-bedrock`, `prism-pack` | Bedrock integration; resource pack compiler. |
| `prism-plugin` | The Bukkit plugin. Shades every module into a single `Prism.jar`. |
| `prism-testkit` | Helpers for creators. Published to Maven. |

Dependencies point downwards only:

```
prism-plugin -> prism-platform:{paper,folia} -> prism-platform:spi -> prism-api
prism-plugin -> prism-client, prism-model, prism-bedrock, prism-pack -> prism-core -> prism-api (+ spi)
```

Rules, enforced by ArchUnit in `prism-plugin` (`ArchitectureTest`) and therefore by CI:

1. `prism-api` references no implementation module, no PacketEvents, no BetterModel and no Folia types.
2. PacketEvents types appear only in `prism-client`; BetterModel types only in `prism-model`. Creators see Prism
   abstractions, so a backend can be replaced without breaking mods.
3. `prism-core` never references Folia or the concrete platform modules.
4. The SPI knows neither its implementations nor core.

Hard dependencies (`plugin.yml` `depend`): `packetevents`, `BetterModel`. Soft: `Geyser-Spigot`, `floodgate`,
`PlaceholderAPI`.

`prism-api` is allowed to depend on `paper-api` (`ItemStack`, `Player` are not worth re-inventing) but does not do so
yet; the first such type arrives with M1.

## Alternatives considered

- **One module with packages.** Simple, but nothing stops a core class from importing Folia; this is exactly how
  Prism-old drifted. Module boundaries make the rules checkable.
- **One jar per platform.** Forces users to choose a file and doubles testing. A single jar detects the platform at
  runtime instead (ADR-0002).
- **Wrapping PacketEvents/BetterModel behind optional features.** Rejected for v1: custom items without PacketEvents
  are not a product. The hard dependency is accepted; keeping its types out of the API keeps the exit open.

## Consequences

- More build files, in exchange for enforceable layering.
- `prism-api` is published separately, so API compatibility can be checked (japicmp) once the first release exists.
- M0 ships `prism-client`, `prism-model`, `prism-bedrock`, `prism-pack` and `prism-testkit` as documented shells. Only
  dependency wiring and package contracts exist; behaviour arrives in M2-M4.
