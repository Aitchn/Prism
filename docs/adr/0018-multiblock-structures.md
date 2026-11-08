# ADR-0018: Multiblock structures

Status: Accepted (2026-11-08). The smoke test checks four things on a real server: a structure forms only when its
last block is placed, it breaks when a member is removed, it forms again when that member comes back, and it releases
its members when the controller goes.

## Context

Tanks, furnaces, reactors and altars are structures: a controller plus a pattern of blocks around it. Rows 23 and 40
of [platform-triage.md](../platform-triage.md) cover this. The matching works the same in every mod:
- find the pattern when blocks change;
- remember which blocks belong to which controller;
- notice when one goes.

What a formed structure does (capacity, recipes, rendering) differs per mod, so only matching belongs in Prism.

## Decision

- **`prism:structure`** on the controller block gives the pattern:
  - `layers` run bottom to top, rows north to south split by `/`, columns west to east;
  - `key` maps single characters to custom or vanilla block keys;
  - `@` is the controller, a space is any block, `.` is air;
  - `rotate` (default true) also tries the pattern turned to face east, south and west.
- **`core.structure.StructurePattern`** compiles the pattern into cell offsets per rotation. It knows how far a cell can
  be from the controller (`reach`), and finds the first rotation that matches. It is pure code with unit tests.
- **`StructureTracker`** (plugin) re-checks structures at these moments:
  - **A block is placed or removed:** custom or vanilla, through the same neighbour hook as before, so a vanilla break
    is checked one tick later. Prism re-checks the structure the block belonged to, if any, and every unformed
    controller whose pattern reaches the block. Controllers are found through the chunk index, so the search is
    limited to loaded chunks within the largest pattern's reach.
  - **The controller is removed:** the structure breaks before the controller's remove behaviors run.
  - **The chunk loads:** a structure that still matches is restored silently if it was formed before (its facing is
    stored in the chunk index). Otherwise it is announced. One that no longer matches gets `onBroken`, unless part
    of it is in a chunk that is not loaded yet: then it stays formed. Each chunk load also re-checks the controllers
    in nearby chunks whose pattern reaches into it, so the load order does not matter.
  - **Content changes** (a mod is enabled, disabled or reloaded): every loaded controller is re-checked on its own
    thread. A structure whose pattern changed or was removed is released.
- **API**:
  - `StructureBehavior.onFormed(context, FormedStructure)` and `onBroken(context)`;
  - `BlockService.structure(controller)` and `controllerOf(member)`;
  - `FormedStructure` (controller, facing, members).

## Consequences

- A cell in an unloaded chunk, or in another Folia region, does not match, so a new structure forms only when all of
  it is loaded and owned by one region, which is the normal case for player-built structures. An already formed one
  is not broken just because part of it is unloaded.
- One block belongs to at most one structure. Overlapping structures are not tracked separately.
- Changes made by other plugins that write blocks directly (WorldEdit), and explosions, are noticed at the next change
  nearby or the next chunk load, not at once.
- Air cells (`.`) are watched too: filling one breaks the structure.

## Not verified by CI

- A player building the example's 3x3 floor and placing the core sees the core light up on Java and Bedrock, and
  sees it go dark when a floor block is broken.
