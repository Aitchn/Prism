# ADR-0019: Gauges in menus

Status: Accepted (2026-11-08). The icons and both packs are unit tested. The smoke test checks that the example's
gauge reaches the Bedrock pack. Seeing the icons move on clients is not covered (see below).

## Context

Row 49 of [platform-triage.md](../platform-triage.md). Every machine GUI shows energy, tank levels and progress. Until
now a menu could only pick a vanilla item and write a text bar into the lore, which a player must hover to read.
Bedrock players see the same chest container through Geyser, so whatever is drawn must be an item icon that both
editions can show.

## Decision

- **`prism:gauge`** on an item: `style` (`bar` fills bottom up, `arrow` fills left to right), `color` (`#rrggbb`) and
  `steps` (1 to 32, default 8). The item has `steps + 1` looks, empty to full.
- **Prism draws the icons** (`GaugeIcons`, 16x16 PNG, deterministic) into
  `assets/<ns>/textures/item/<name>/gauge_<n>.png` unless the mod ships that file, which then wins.
- **Java:** one item definition per step, `assets/<ns>/items/<name>/gauge_<n>.json`, so the step is the stack's
  `item_model` `<ns>:<name>/gauge_<n>`. The item itself looks full.
- **Bedrock:** one Geyser custom item per step, mapped from the same `item_model`, so the same component change shows
  the step on both editions. The Bedrock pack log reports `gauges=N` (step icons).
- **`MenuView.setGauge(slot, key, fraction, name, lore)`** picks the step with `Gauge.step`: 0 only when empty and
  `steps` only when full, so a nearly empty or nearly full value still differs from the ends.
- The example counter shows a cycle arrow. The Mekanism rebuild's machine GUIs use gauges for energy, progress and
  chemical tanks.

## Consequences

- Each gauge costs `steps + 1` Bedrock custom items. Thirty-three at most per gauge.
- A gauge is coarse: 16 steps is a 6 % resolution. Exact values stay in the name and lore.
- Full custom menu backgrounds (the other half of row 49) are done in
  [ADR-0028](0028-menu-backgrounds-and-pixel-widgets.md): Java only (a font glyph in the title), with these gauges as
  the Bedrock fallback of its pixel widgets.

## Not verified by CI

- Opening the ruby counter on Java and on Bedrock: the cycle arrow next to the clock fills step by step and empties
  every ten counts.
- Opening a Mekanism machine while it works: the energy bar, the progress arrow and the tank bars move on both
  editions.
