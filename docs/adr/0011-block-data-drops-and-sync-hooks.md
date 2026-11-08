# ADR-0011: Block data in drops, click pass-through, synchronous slot checks, global timer

Status: Accepted (2026-11-05). The drop round trip and the global timer are covered by the platform self test in the
smoke test. Clicking by hand is not covered (see below).

## Context

Rows 31 to 34 of [platform-triage.md](../platform-triage.md). Four small gaps that every machine-style plugin hits:

- a broken block lost its data (charge, contents, settings) because its drop was a fresh item;
- an interact behavior swallowed every click, so players could not place blocks against a machine;
- a menu learned about a slot change one tick late, with no way to refuse it from the current model state;
- there was no server-wide repeating task, only per-location and per-entity timers.

## Decision

- **`BlockDropBehavior.onDrop(context, drop)`** runs when a block drops its item (survival break, or
  `remove(block, true)`), before `BlockRemoveBehavior`, while the per-block data still exists. The plugin writes into
  the stack with `ItemService.editData`, which also re-renders lore.
- **`BlockContext.placedFrom()`** gives place behaviors a one-item copy of the stack the player placed. Code can pass one
  with the new `BlockService.place(key, location, placer, facing, from)`; the old overloads pass null.
- **`BlockContext.pass()`**: an interact behavior that calls it did not use the click. The click is cancelled only if
  some behavior ran without passing, or one called `cancel()`. Before this, any behavior that ran owned the click.
- **`ContainerMenu.allowChange(view, slot)`** is asked synchronously before the player changes an input or output slot
  by click, shift-click, number key, drop or drag. False cancels the click; for a shift-click into the menu the slot is
  skipped. **`MenuView.isChangePending(slot)`** is true between the click and `onSlotChanged`.
- **`PrismScheduler.runGlobalTimer(task, delay, period)`**: Paper's main-thread timer, Folia's global region
  `runAtFixedRate`. It owns no region, so the task hops with `runAt` for blocks.

## Consequences

- No new state: the drop carries data in the item's own persistent data, which already survives chests and restarts.
- `pass()` changes nothing for existing behaviors; they keep owning the click unless they opt out.
- `onSlotChanged` stays one tick late on purpose: Bukkit applies the click after the event, so the slot content is
  only final then. `allowChange` is for decisions, `onSlotChanged` for copying content.

## Not verified by CI

- Breaking a counter by hand in survival and placing the dropped item (Java and Bedrock): the lore and count should
  carry over.
- Clicking the counter with a block in hand places that block against it; with an empty hand (or a non-block item)
  the menu opens.
- Refusal in a real click: break a counter while another player has its menu open; the stored slot then rejects
  every click.
