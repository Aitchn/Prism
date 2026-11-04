# ADR-0003: Mods are plain plugins; content lives in the jar

Status: Accepted (2026-11-04)

## Context

Creators already know Bukkit. A custom class loader or a new mod file format would cost them that knowledge and cost
us a loader to maintain. Prism-old also scanned an external `prism_register/<namespace>/` folder; it was rarely used
and had to be cleaned by hand after every test run.

## Decision

- A Prism mod is a Bukkit plugin with `depend: [Prism]`. Prism does not load jars itself.
- Content (YAML, assets) is read from the mod jar's classpath, by default under `prism/`. Prism never extracts or
  copies mod content into a server folder, and there is no external registry directory.
- Zero configuration: the namespace defaults to the lower-cased plugin name and the content path to `prism/`. A
  `prism-mod.json` inside the jar is an optional override and is not required for anything.
- Entry point (M1): the main class implements `PrismMod`, or calls `Prism.mods().register(this, initializer)` in
  `onEnable`.
- Server operators who want to change a mod's content use that mod's own config.

## Consequences

- Class loading, `folia-supported`, commands and permissions stay with Paper.
- Prism cannot isolate a broken mod's classes, only its content: a mod that fails validation is not activated as a
  whole, and other mods are unaffected.
- M0 contains no mod loading code. This record fixes the direction so no external-registry design is introduced.
