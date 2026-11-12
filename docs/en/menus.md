# Menus

Prism menus are chest containers that both editions can open: Java players directly, Bedrock players through Geyser.
This page covers the two kinds of menu and how to give a menu its own picture, live widgets and hidden slots
([ADR-0009](../adr/0009-slot-menus-and-item-data.md), [ADR-0019](../adr/0019-menu-gauges.md),
[ADR-0028](../adr/0028-menu-backgrounds-and-pixel-widgets.md)).

## Two kinds of menu

- **A list of buttons:** `Prism.menus().open(player, new MenuSpec(title, buttons), id -> ...)`. The player picks one.
- **A container with real slots:** implement `ContainerMenu` and call `Prism.menus().open(player, menu)`. Each slot is a
  `BUTTON`, an `INPUT` or an `OUTPUT`; Prism enforces the roles for every kind of click. `onRefresh` runs every
  `refreshTicks` to update live values.

Titles, labels, names and lore are translation keys (see [Translations](translations.md)).

## Gauge items

A gauge is an item with `prism:gauge` (`style: bar` or `arrow`, `color`, `steps` 1 to 32). `view.setGauge(slot, key,
fraction, name, lore)` shows the step nearest `fraction`. It takes a slot and looks the same on Java and Bedrock.

## Backgrounds and pixel widgets

A menu file describes the art of a menu. Java players with Prism's resource pack see a picture behind the slots,
arrows and bars drawn at any pixel position, and nothing where the picture has no slot.

### Worked example

The example mod's counter menu (`examples/example-mod`) has three rows. Its menu file is
`src/main/resources/prism/menus/counter.yml`, so its key is `examplemod:counter`:

```yaml
background: gui/counter        # assets/examplemod/textures/gui/counter.png, 176x71
rows: 3
slots: [11, 13, 15, 22]        # the slots the picture draws; all others are hidden
widgets:
  cycle:
    type: progress
    sprite: gui/counter_arrow  # 16x11, revealed left to right
    x: 62
    y: 38
    steps: 10
    fallback:
      slot: 12                 # hidden on Java; the gauge item for everyone else
      item: cycle_gauge
  store:
    type: bar
    sprite: gui/counter_store  # 8x50, revealed bottom up
    x: 156
    y: 18
    steps: 16
```

The menu names its art and moves the widgets:

```java
final class CounterMenu implements ContainerMenu {
    static final PrismKey ART = PrismKey.parse("examplemod:counter");

    @Override
    public PrismKey art() {
        return ART;
    }

    @Override
    public void onRefresh(MenuView view) {
        int cycle = count % 10;
        view.setWidget("cycle", cycle / 10.0, Message.of("examplemod.counter.cycle", cycle),
                List.of(Message.of("examplemod.counter.cycle_hint")));
    }

    private static void drawStore(MenuView view, ItemStack item) {
        view.setWidget("store", item == null ? 0 : (double) item.getAmount() / item.getMaxStackSize());
    }
}
```

The name and lore passed to `setWidget` are used by the fallback item; on Java the picture has no tooltip.

### The menu file

| Field | Meaning |
|---|---|
| `background` | Texture key: `gui/machine` is `assets/<namespace>/textures/gui/machine.png` in your jar. Optional. |
| `rows` | 1 to 6 (default 3). Replaces the menu's own `rows()`. |
| `title` | `{x: 8}` moves the title text sideways (pixels from the left edge); `false` hides it on Java. The text stays on the vanilla title line. |
| `slots` | Slot numbers and ranges (`"0-8"`) the picture uses. Every other slot is hidden. Omit to use all slots. |
| `widgets` | Named widgets, drawn in this order. |

Widget fields:

| Field | Meaning |
|---|---|
| `type` | `progress` (a sprite revealed in steps), `bar` (a fill from a sprite or a colour), `icon` (one sprite per state) |
| `x`, `y` | Top-left corner, in pixels from the container's top-left corner. |
| `sprite` | progress and bar: the full picture. |
| `color`, `width`, `height` | bar without a sprite: a generated fill of that colour and size. |
| `sprites` | icon: one texture per state (at most 16). |
| `direction` | `right` (progress default), `left`, `up` (bar default) or `down`: where the fill grows to. |
| `steps` | progress and bar: 1 to 64, default 16. |
| `fallback` | `{slot, item}` (a `prism:gauge` item) or for an icon `{slot, items: [...]}`, one item per state. |

`view.setWidget(id, fraction)` moves a progress or bar widget; `view.setWidgetState(id, state)` picks an icon's state.
Step 0 draws nothing; step `steps` is shown only when full.

### Texture sizes

- A glyph may be at most 256 pixels high (a client limit) and Prism holds the width to 256 too. A larger texture is
  left out with a `PRISM_PACK_WARNING`, as is a missing one.
- The vanilla container is **176** pixels wide. Its menu part is **`rows × 18 + 17`** high (71 for 3 rows, 125 for 6);
  the player's inventory part below it is 96 more (**`rows × 18 + 114`** in all). Draw only the menu part to keep the
  vanilla inventory below, or the whole height to replace it too.
- Slot `i` has its item at `x = 8 + (i % 9) × 18`, `y = 18 + (i / 9) × 18` (16×16). Draw the slot frame one pixel
  around that.
- The "Inventory" label of the player's inventory is drawn by the client over your picture.

### What each player sees

| | Java with the pack | Bedrock, or Java without the pack |
|---|---|---|
| Background | the picture | the plain chest |
| Title | at the art's x, or hidden | the translated title, always shown |
| Widgets | drawn and moving | their fallback items in their fallback slots |
| Hidden slots | empty and inert (the client still highlights them under the mouse) | empty and inert |

`view.showsArt()` is true for the first column. Prism resends the title only when a drawn step changes, at most every
other tick, so call `setWidget` as often as you like.

### Limits

- Changing a widget reopens the screen with the new title, which cancels an item drag the player is making at that
  moment.
- The title text cannot move up or down.
- A Java player whose pack is still loading sees boxes in the title until it has loaded.

When `pack.enabled` is `false`, or a button menu has more buttons than its art has usable slots, the menu is shown as a plain chest with fallback widgets (the second case logs `PRISM_MENU_WARNING` once).
