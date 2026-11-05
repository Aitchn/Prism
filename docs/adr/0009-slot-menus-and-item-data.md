# ADR-0009: Slot menus and per-stack item data

Status: Accepted (2026-11-05). The click rules are unit tested. Menus need a player, so the smoke test cannot open
one (see below).

## Context

Machines, storage terminals and generators all need a GUI with real slots that updates live, and items that carry
their own values (charge, stored amount, uses). Until now Prism only had button lists (`MenuSpec`). Rows 17 and 19 of
[platform-triage.md](../platform-triage.md).

## Decision

- **`ContainerMenu`.** A mod implements title, rows, `role(slot)`, `accepts(slot, stack)` and callbacks:
  `onOpen`, `onButton`, `onSlotChanged`, `onRefresh` every `refreshTicks`, and `onClose`. `MenuView` reads and writes
  slots in place, so live values do not flicker. Opened with `MenuService.open(player, menu)`.
- **Roles are enforced by Prism**, not by every mod.
  - `SlotRules` (core, pure logic, unit tested) decides every click kind: take, put, swap, number key or offhand
    swap, shift in and out, drop, double-click collect, creative clone, and drags.
  - Shift-clicking from the player's inventory is done by Prism itself. It only fills `INPUT` slots that accept the
    item, stacking first.
  - A double click in the player's inventory is denied, because it would also pull items out of the menu.
- **Threads.** Every callback runs on the player's thread. The refresh timer follows the player
  (`PrismScheduler.runOnTimer`, new, backed by Folia's entity scheduler). Touching a block goes through `runAt`.
- **Rendering.** A chest container. Geyser translates it for Bedrock, so both editions get the same slots. A native
  Bedrock form is still a later row (18).
- **Per-stack data.** It is the stack's own `PersistentDataContainer`. `ItemService.editData(stack, edit)` writes it
  and keeps Prism's identity key intact. Stacks with different data do not stack, which is the intended behaviour.
- **Lore.** `prism:lore` holds static lines. `ItemLoreBehavior.lore(stack)` adds computed lines. Prism renders them
  on create, on `editData` and on `refresh`. Lore lives on the stack, so Geyser shows it on Bedrock too.

## Consequences

- Items in menu slots belong to the mod's model. The example `CounterMenu` shows the pattern:
  - the item is stored in per-block data;
  - one viewer per block, because two viewers could duplicate the item;
  - the block drops the item when it is broken;
  - if the block is broken while the menu is open, the viewer gets the item on close.
- Lore is server-side text. It is not localised per player yet.

## Not verified in CI (hand test with real clients)

- `examplemod:ruby_counter`, right click:
  - the count updates live;
  - the switch button changes the block's look;
  - the store slot keeps its item across restarts;
  - breaking the block drops the item;
  - shift-click, number keys and drag all behave correctly.
- `examplemod:ruby`: using it increases "Used N times".
- The same on Bedrock through Geyser.
