# ADR-0015: Custom sounds, ambient block sounds and animated textures

Status: Accepted (2026-11-08). The Bedrock conversion is unit tested. The smoke test checks that the example's sound
and flipbook reach the Bedrock pack. Hearing and seeing them on clients is not covered (see below).

## Context

Rows 44 and 46 of [platform-triage.md](../platform-triage.md), plus E2 and E6 from the Mekanism effects report. Mods
ship their own sounds and animated textures. The Java pack already merged a mod's `assets/`, so Java worked, but the
Bedrock pack had neither. A block's looping sound (a machine's hum) had to be repeated by each plugin on a timer, and
it was easy to leave playing after the block went.

## Decision

- **Sounds use the Java layout:** `assets/<ns>/sounds.json` plus `assets/<ns>/sounds/**.ogg`. The Java pack takes
  them as they are.
  - The Bedrock pack builder turns every event into `sounds/sound_definitions.json`, keyed `<ns>:<event>`, and copies
    the `.ogg` files. Volume, pitch and stream are carried over.
  - Geyser forwards a custom sound event under that full name, so `playSound(location, "<ns>:<event>", ...)` works
    for both editions.
  - Vanilla overrides (the `minecraft` namespace) are skipped. Event references are not followed.
- **Animated textures use the Java `.png.mcmeta`.** For a block texture that has one, the Bedrock pack adds a
  `flipbook_textures.json` entry for the same strip, with `ticks_per_frame` taken from `frametime`.
- **`prism:ambient_sound`** (`sound`, `interval`, `volume`, `pitch` and optional `variants`) plays from the block's
  tick schedule (slot 15 of the block's 16). So:
  - it runs only while the chunk ticks;
  - it stops when the block is removed;
  - it plays only in the listed variants.
- The Bedrock pack log reports `sounds=N flipbooks=M`.

## Consequences

- A block can run at most 15 tick behaviors next to an ambient sound (16 before).
- Ambient sounds are clips repeated on an interval, not a stream that can be cut off. A clip about as long as the
  interval sounds continuous and stops within one interval.
- Animated item icons are not converted. Bedrock item atlases have no flipbook.

## Not verified by CI

- Switching the ruby counter to active on Java and on Bedrock: it hums once a second and its texture pulses. Switching
  it back to idle or breaking it stops the hum.
