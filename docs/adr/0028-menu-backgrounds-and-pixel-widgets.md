# ADR-0028: Menu backgrounds and pixel widgets

Status: Accepted (2026-11-12). The pure logic (glyph offsets and padding, advances, shifts, step slicing, glyph sharing,
title composition for Java and Bedrock, the menu file) is unit tested. A self test checks the pack and the titles on a
real server (see "Verification"). What a Java or Bedrock client draws is not verified in CI.

## Context

Row 49 of [platform-triage.md](../platform-triage.md), the half that [ADR-0019](0019-menu-gauges.md) left open. Machine
screens need a picture behind the slots, progress arrows, energy bars and tank gauges drawn at any pixel position, and
slots that look absent where the picture has none. A Prism menu ([ADR-0009](0009-slot-menus-and-item-data.md)) is a
vanilla chest container; until now a mod could only put icon items into its slots.

Requirements, as for the rest of Prism: generic primitives only; Paper, Folia and Purpur; the least server work
possible; Bedrock players (Geyser) get a sensible fallback; only `prism-client` touches PacketEvents; titles stay
translated per viewer ([ADR-0026](0026-per-viewer-item-text.md)).

## Decision

### 1. Menu files

The art of a menu is content in the mod jar: `<content root>/menus/<path>.yml`, key `<namespace>:<path>`, loaded and
validated with items and blocks (a broken file fails the mod) and published in a registry of its own.

```yaml
background: gui/machine      # assets/<ns>/textures/gui/machine.png
rows: 3                      # 1 to 6; replaces the menu's own row count
title: {x: 8}                # where the title text starts; `title: false` hides it (Java only)
slots: [11, 13, 15, 22]      # slot numbers or ranges ("10-16"); the slots the picture draws. Omitted: all
widgets:
  progress:
    type: progress           # progress, bar or icon
    sprite: gui/arrow
    x: 79                    # pixels from the container's top-left corner
    y: 34
    direction: right         # right (progress default), left, up (bar default), down
    steps: 16                # 1 to 64, default 16
    fallback: {slot: 12, item: arrow_gauge}   # a prism:gauge item shown where the picture is not
  energy:
    type: bar
    color: "#d03030"         # a generated fill instead of a sprite
    width: 6
    height: 50
    x: 8
    y: 18
  lit:
    type: icon
    sprites: [gui/off, gui/on]                 # one per state, at most 16
    x: 150
    y: 6
    fallback: {slot: 8, items: [gray_dye, lime_dye]}
```

A menu refers to its art by key: `ContainerMenu#art()` (default null) and `MenuSpec(title, buttons, art)` /
`MenuSpec#withArt`. A button list with art takes the art's rows and fills the slots the art uses, in slot order.

API added (`prism-api`, package `menu`):

```java
// ContainerMenu
default @Nullable PrismKey art() { return null; }
// MenuSpec
public MenuSpec(String title, List<MenuButton> buttons, @Nullable PrismKey art)
public MenuSpec withArt(@Nullable PrismKey art)
// MenuView
void setWidget(String id, double fraction, Message name, List<Message> lore);   // progress, bar
default void setWidget(String id, double fraction)
void setWidgetState(String id, int state, Message name, List<Message> lore);    // icon
default void setWidgetState(String id, int state)
boolean showsArt();
```

### 2. Backgrounds: a font glyph in the title

The Java pack gets one font, `prism:menu` (`assets/prism/font/menu.json`). The container title becomes a run of that
font's characters, followed by the translated title text in the default font:

- The client draws the title at (8, 6) from the container's corner, and a bitmap glyph with ascent `a` has its top
  `7 - a` below the line's top. So a picture whose top must be at `y` gets **ascent `13 - y`**: the background (y 0)
  has ascent 13. The client refuses an ascent larger than the height, so a picture near the top edge whose ascent
  would exceed its height gets transparent rows added below it.
- Horizontally a bitmap glyph moves the pen by its drawn width (up to its rightmost column with any alpha) plus one.
  Prism re-encodes every glyph texture as RGBA and computes that advance itself. A `space` provider holds 22 shift
  characters (±1, ±2, ..., ±1024 pixels), so the server writes any move. The title is: shift −8 to the left edge,
  the background, for each drawn widget a shift to its x and its glyph, and finally a shift to the title's x followed
  by the title text (left out when the title is hidden). The text keeps vanilla's colour; the glyph run is white so
  the label colour does not tint the pictures.
- The title text cannot move vertically (it stays on the vanilla title line). The "Inventory" label of the player's
  inventory is vanilla's and is drawn as usual.

Texture rules: width and height at most **256** (the client's limit for a bitmap glyph's height; Prism holds the width
to the same limit). The usual sizes are vanilla's: **176** wide, **`rows × 18 + 17`** high to cover the menu part only
(the vanilla player inventory part stays below it), or **`rows × 18 + 114`** to cover the player inventory too (at most
222 for 6 rows). Slot centres are vanilla's: slot `i` has its item's top-left corner at
`(8 + (i % 9) × 18, 18 + (i / 9) × 18)`.

### 3. Pixel widgets: steps generated in the pack

- `progress` reveals its sprite in `steps` steps towards `direction`; `bar` does the same, from a sprite (the full
  bar, "strip") or from a generated filled rectangle (`color`, `width`, `height`); `icon` shows one of its sprites.
  Step 0 of a progress or bar draws nothing.
- The pack builder slices each step at build time (`round(step × length / steps)` pixels, at least 1), so step glyphs
  are exact pictures, not scaled.
- **Sharing.** A glyph is a texture plus an ascent. Textures are stored once by content
  (`assets/prism/textures/font/menu/<hash>.png`) and a character is given once per texture and ascent, whichever menu
  or widget uses it. Two steps that reveal the same pixels (more steps than the sprite is long, or clear columns) are
  one character. Characters are given in menu key order from U+E100 (shifts use U+E000 to U+E01A), so the same
  content makes the same font; the font holds at most 6144 pictures.
- **Steps** default to 16; at most 64 per progress or bar, 16 states per icon, 32 widgets per menu. A value is mapped to
  a step as gauges do: 0 only when empty, `steps` only when full.
- **Updates.** `setWidget` records the step. A task on the player's thread runs every 2 ticks while a menu with drawn
  widgets is open; when a drawn step changed since the last title it composes the title and, only if the glyph run
  differs from the one sent, sends it. Nothing is sent when nothing changed, and at most one title per menu every 2
  ticks however often the mod calls `setWidget`.
- **Sending the title.** Paper's `InventoryView#setTitle(String)` is deprecated (and takes legacy text), and there is no
  component variant. So `prism-client` sends an open-screen packet with the container id and menu type of the screen
  the server last opened for that player (recorded from the server's own open-screen packets) and the new title, then
  Prism calls `Player#updateInventory()`, which resends the slots and the carried item. The server's inventory is
  untouched.
- The first title is already right: a menu with art makes its inventory after `onOpen` has set the widgets (the
  contents are moved over).

### 4. Hidden slots

Slots of the container that the art does not list are hidden for every viewer: clicks, drags and shift-clicks into
them are cancelled, `setItem`, `setIcon` and `setGauge` on them are ignored, and `onButton` is not called. The
background glyph covers the whole vanilla container picture, slot frames included, so an empty hidden slot shows only
what the background draws there. A Java client still draws its hover highlight over a hidden slot; that cannot be
switched off from the server.

### 5. Gauges and widgets

The item gauges of [ADR-0019](0019-menu-gauges.md) stay as they are. Pixel widgets are the richer option where the pack
is present: any size and position, more steps, no slot taken. A widget's `fallback` names a gauge item (progress, bar)
or one item per state (icon) and a slot; wherever the picture is not drawn, `setWidget` puts that gauge, at the same
step fraction, into that slot (usually a slot the art hides, so Java players see the picture there instead). A widget
without a fallback simply has no item form.

### 6. Who sees the art, and Bedrock

A viewer sees the art when they are a Java player (Floodgate / Geyser detection, the same as for the display layer),
the art is in the pack, and they have not declined or failed to load Prism's pack (resource pack status events).
Everyone else, Bedrock players first:

- **Title:** the translated title text with every private use character removed (the Java glyph characters would show
  as boxes, and Geyser shows the raw title on the chest UI). It is sent once with the menu and never resent.
- **Widgets:** the fallback items in their slots; widgets without a fallback are not shown.
- **Slots:** the chest with the same hidden slots (empty and inert) and the mod's own slots.

`MenuView#showsArt()` tells a mod which of the two a viewer gets.

### 7. Pack plumbing

`JavaPackBuilder.Input` has the menu files; `MenuArtPack` adds the font and its textures and returns a layout per menu
(glyph characters, positions, advances) that the server uses to write titles. A missing, unreadable or oversized
texture is a `PRISM_PACK_WARNING` naming the menu and widget; that picture is left out (a widget without its picture
uses its fallback item for everyone). A build with menus logs `PRISM_PACK_MENUS menus=<n> backgrounds=<n>
widgets=<n> glyphs=<n>`, a separate line so the existing `PRISM_PACK_READY` markers stay unchanged. An unknown art key
on a menu logs `PRISM_MENU_WARNING` once and shows the plain chest. The Bedrock pack is unchanged.

## Alternatives considered

- **`InventoryView#setTitle(String)`.** Deprecated in Paper since 1.21.1 and legacy text only: no custom font. Rejected
  for the packet route.
- **Reopening the inventory on each change.** Closes and opens the screen (cursor reset, sounds, `InventoryCloseEvent`
  for everything listening). Rejected.
- **Shaders or core shader edits** to draw pictures in containers. Version-fragile and all-or-nothing per pack.
  Rejected.
- **Item models in hidden slots** for the pictures. Limited to 16×16 slot cells (or scaled models that overlap
  unpredictably) and items show tooltips. Kept only as the fallback (gauges).
- **Scaling one glyph per widget** instead of a glyph per step. Bitmap glyphs cannot be cropped at run time; a scaled
  glyph distorts the sprite. Rejected.

## Consequences

- Any mod gets machine screens from a YAML file and a few textures; the menu code only calls `setWidget`.
- Cost: one boolean check per open menu with widgets every 2 ticks; an open-screen packet and a contents resend only
  when a drawn step changes. Menus without art are unchanged.
- Reopening the screen to change the title cancels a drag the player is making at that moment. Widgets with fewer
  steps change the title less often and keep that rare.
- A Java player who joins with Prism's pack still loading sees boxes until it loads; one who declined it gets the plain
  chest from the next menu on.
- Menu titles stay fixed per opening language, as in ADR-0026.

## Verification

- Unit tests (`prism-core`): `MenuGlyphsTest` (ascent and padding for every row, advance from alpha, every shift from
  −2047 to 2047, step slicing in four directions, private use detection), `MenuTitlesTest` (composed Java title, pen
  position after the run, title change only on a drawn step, hidden title, Bedrock title without glyphs, menu file
  decoding and rejection). `prism-pack`: `MenuArtPackTest` (font and textures, advances of sliced steps, identical steps
  shared, glyphs shared across menus, padding near the top, warnings for missing and oversized textures, same content
  same font). The example mod's test checks its menu file.
- Smoke test: `-Dprism.selftest.menuart=examplemod:counter` checks that the built pack holds the font and every glyph
  texture it names, that the Java title of the example menu (built with the same component code as a real menu) holds
  the background glyph and changes when a widget changes, and that the Bedrock title of a text with glyph characters
  has none left. It logs `PRISM_MENUART_SELFTEST_OK backgrounds=1 widgets=2 steps=26 bedrockTitle=clean` or
  `PRISM_MENUART_SELFTEST_FAIL <reason>`.

## Not verified by CI (hand test with real clients)

- Java: the ruby counter's menu shows its picture exactly over the container (no offset), the arrow fills as the count
  cycles, the bar follows the stored stack, items are drawn over the picture, hidden slots look empty and do nothing,
  the title text sits at its place, the carried item survives a title change.
- Java client versions: the order in which the container screen draws its title and its items (the picture must be
  behind the items).
- Bedrock through Geyser: the clean title, the cycle gauge in slot 12, hidden slots inert.
