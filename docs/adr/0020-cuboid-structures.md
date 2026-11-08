# ADR-0020: Boxes of variable size

Status: Accepted (2026-11-08). The box search is unit tested. The smoke test builds, fills, clears and dismantles a
box on Paper, Folia and Purpur. Building one by hand on clients is not covered (see below).

## Context

ADR-0018 matches fixed patterns. Tanks, boilers and similar structures are hollow boxes whose size the player picks
within limits, so one pattern per size does not work. The search has to find the box from the controller.

## Decision

- **`prism:cuboid`** on the controller: `walls` (block keys, custom or vanilla), `inside` (blocks allowed inside
  besides air, default none), `min` and `max` (outer side length, 3 to 32, defaults 3 and 9).
- **Search** (`core/structure/CuboidShape`, pure). For each side of the controller that is open (air or an allowed
  inside block), walk from that cell along each axis until a block that is neither. That gives the inside box, and
  one more layer around it is the shell. Then:
  - every outer side must measure `min` to `max`;
  - every shell cell must be one of the walls;
  - every inside cell must be air or an allowed inside block.
  The controller is therefore on a face of the shell, never on an edge or corner.
- **Same lifecycle as patterns.** `StructurePattern` and `CuboidShape` both implement `StructureShape`, and
  `StructureTracker` handles both:
  - placing and removing blocks, chunk loads and content changes;
  - the "part of it is unloaded" rule.
  A result is uncertain when any cell it needed could not be looked at.
- **What is saved.** The chunk index keeps a signature per controller: the facing for a pattern, and the facing plus
  both corners for a box. A box rebuilt at another size therefore gets `onBroken` and then `onFormed`.
- **API.** `FormedStructure` gained `min` and `max`, the corners of the bounding box. For a box, the inside is what
  lies strictly between them. `facing` points from the controller into the inside. `members` are the shell plus any
  allowed inside blocks. The inside air cells are watched, but they are not members.
- The example `examplemod:ruby_tank` lights up in a box of ruby blocks and glass, 3 to 7 blocks a side.

## Consequences

- The work grows with the volume: up to 32x32x32 cells are checked when a box forms, and every inside cell is
  watched. Mods should keep `max` as small as their design allows.
- If a smaller valid box exists from the controller's open side, that is the one found. Filling part of the inside
  with walls can therefore re-form a smaller box.
- The walls cannot be restricted by position: there are no edge-only or face-only blocks. A mod can check that in
  `onFormed` using `members`.

## Not verified by CI

- Building a 3x3x3 and a 7x7x7 ruby/glass box by hand with the tank in a wall, on Java and on Bedrock: the tank
  lights up. Breaking a wall or putting a block inside turns it off.
