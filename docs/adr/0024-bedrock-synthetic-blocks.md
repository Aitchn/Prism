# ADR-0024: Synthetic block states for Bedrock (stage 3)

Status: Accepted (2026-11-08). Stage 3 of the block carrier redesign (stage 1 is ADR-0022, stage 2 is ADR-0023). The pure
logic (id allocation, shapes, hardness, pack and export output, section re-encoding through a palette reader) is covered
by unit tests, and the packet code was type-checked against the PacketEvents and Geyser sources. Nothing has run against
a real Geyser, a real Bedrock client or a real Paper (see "Not verified").

## Context

After stage 2 a Java client can show as many looks as a mod wants, but Bedrock still depends on Geyser's JSON block
mappings, which can only name vanilla Java states. Prism therefore offered Bedrock the same borrowed note block and
tripwire states as Java: 799 + 64 looks, with every other block invisible (display entities do not reach Bedrock) or
drawn by a per-player block change (ADR-0013). The goal of this stage is that every custom block look is a real Bedrock
custom block, with no count limit, consistent with what Java sees, and without changing a byte of what Java players
receive.

Geyser's API (2.9.5 and newer) can register a custom block together with a `JavaBlockState` that is **not** in the
vanilla list (`GeyserDefineCustomBlocksEvent.registerOverride`). A non-vanilla state needs a Java block state id that
the server may send. The Java protocol itself does not care what id a block change carries as long as the reader knows
it: Geyser, once it has registered the state, translates it like any other.

## Decision

### 1. One synthetic Java state id per look

`SyntheticIds.plan` gives every look of every block (any backend, drawn by a borrowed state, a shell or only for
Bedrock) one id, in look id order, starting at `base`:

- `base` is the server's block state count plus a margin of 4096, or `bedrock.synthetic-base` if set. Geyser fails its
  own start-up when the first non-vanilla id is not above its own vanilla state count, and a Geyser build (or a Geyser on
  a proxy) may know more states than the server. The gap costs nothing: Geyser fills it with air.
- The allocation is the same function of the content as the Java allocation of stage 2: the same content gives the same
  ids and the same Bedrock pack. Nothing is stored in the world; the index keeps look names.
- The plan is always computed (the pack build, the export and the self test use it), but the ids that viewers are sent
  (**active** ids) exist only once Geyser accepted them, or, in `proxy` mode, once the server has loaded. They are then
  frozen for the boot. Ids are positional, so a look added by a hot reload would shift every id after it while Geyser
  has already read the old ones; such a look is shown to Bedrock as its Java look until the next restart. A warning
  is not needed: the Java look is exactly what Bedrock showed before this stage.

### 2. Registration through the Geyser API

`prism-bedrock` has one class, `GeyserBlockRegistrar`, compiled against the Geyser API and loaded only when
`Geyser-Spigot` is installed (a `LinkageError` counts as "not usable"). It subscribes to
`GeyserDefineCustomBlocksEvent` and `GeyserDefineResourcePacksEvent`:

- Every block is **built before any is registered**, so one bad look cannot leave the others half registered.
- A Bedrock `CustomBlockData` per look: no properties, one state, geometry and material instances from the Bedrock pack
  builder, a selection box (the outline of a connected or modelled look), collision boxes from the physics class,
  `destructibleByMining(Float.MAX_VALUE)` (Geyser keeps break time from the Java state) and not in the creative
  inventory. The block name is `p_<readable>_<id>`: unique per boot.
- A non-vanilla `JavaBlockState` per look: `prism:<name>`, the synthetic id, its own state group, hardness, collision
  boxes, `canBreakWithHand`, piston behaviour `IMMOVEABLE`. Both go to `registerOverride(javaState, bedrockDefaultState)`.
- The pack is registered by path, so Geyser serves it. Because Geyser would otherwise load the same textures twice,
  Prism then **removes** its legacy `custom_mappings/prism_blocks.json` and `packs/prism.mcpack` from Geyser's folder; the
  item mappings stay (items are not part of this stage).
- Geyser starts on `ServerLoadEvent`, after Prism's mods are enabled, so the content is final when it asks. The Bedrock
  pack is built synchronously in that handler (once; the result is reused for the later pack event and a `/geyser
  reload`). On a very large content set this is a start-up cost on Geyser's thread.

### 3. Geometry, textures, collision, hardness

- Geometry and textures: the existing `BedrockPackBuilder`. For each look it computes one draw (the faces and materials
  of its model, the generated shape of a connected look from the same boxes as the Java model, or a tripwire-style
  cross or box geometry for a passable look) and produces the legacy output and the synthetic output from it.
- Collision (`BedrockShapes`, from the look's physics class): FULL gives the unit cube, NONE gives no box. Connected and
  model blocks keep full collision because Java draws them over a barrier. The outline of a partial shape is the
  selection box only, so a player aims at the shape and the server still enforces the class (a partial collision box
  per look would need server-side collision that the carrier does not have).
- Hardness (`BedrockHardness`): Geyser's `blockHardness` is `prism:mining` seconds divided by 1.5 (Bedrock's destroy
  time is the vanilla hardness, and one hardness unit is 1.5 seconds bare-handed); an unbreakable block is -1 and every
  block can be broken with a bare hand. Tools do not speed up a synthetic block on Bedrock (it has no tool tags).
  `BreakFeel`'s `block_break_speed` modifier is skipped for a Geyser viewer with a synthetic id, because Geyser reads
  that attribute and the break would otherwise be slowed twice.

### 4. Viewers

`BedrockDetector` (Floodgate first, then the Geyser API) already tells whether a connection is Bedrock. The packet
rewriter asks it only while synthetic ids are active (`LookSource.bedrockStates()`), so the cost for a Java-only server
is one volatile read per packet. For a Bedrock viewer:

- A position holding the carrier, or already holding the Java look (the server resending it), is shown as the synthetic
  id. Java canonicalization is not applied (the viewer never sees a borrowed vanilla state, so there is nothing to
  disguise).
- Foreign states (something that is not the carrier) are untouched and reported, as for Java viewers.
- Display layer entities are never sent to a Bedrock viewer (`DisplayLayer` has always skipped them); blocks that carry
  no look in the chunk (drawn over a barrier by a model, a connected shape or the `display` backend) are sent their
  synthetic id by `BedrockModelLooks`, per player, as before but with the new id.

### 5. Chunk encoding

A section's block states are a paletted container: 0 bits (one entry), a list (up to 16 entries, 4 bits), a map (up to
256, 5 to 8 bits) or the global palette (more than 8 bits, raw ids, a width fixed by the registry size). Palette entries
are varints, so a synthetic id costs nothing extra there. Only the global palette has a width:

- A vanilla client reads the global palette with the width the writer used. Geyser instead reads
  `readChunkSection(in, BLOCK_STATES.size(), ...)`, derived from the size of **its** registry, which after registration
  ends at the highest synthetic id. mcprotocollib may derive the width the same way rather than from the bits byte
  (not verified).
- So for a Bedrock viewer every section that is global-palette-encoded is rewritten with exactly
  `ceillog2(highest synthetic id + 1)` bits (`bedrockGlobalBits`). A section with custom blocks is decoded to its
  states, rewritten, and encoded again by `PalettedSection`, which chooses singleton, list, map or global like the
  vanilla writer does; a section without any is only re-widened if it is global. `PalettedSection.pack` was compared bit
  for bit against PacketEvents' own `BitStorage` for widths 1 to 32.
- PacketEvents is not used to write out-of-range ids: its `GlobalPalette` has a hard-coded 16 bits and `section.set`
  cannot hold them, so `BedrockSections` replaces the container's palette and storage fields directly.
- `BedrockSelfCheck` runs in the smoke test inside a real server: it builds sections with PacketEvents, rewrites them as
  a Java and a Bedrock viewer, round-trips both through `Chunk_v1_18.write/read`, and compares every position.

### 6. Fallback and modes

`bedrock.synthetic-blocks`:

- `auto` (default): register through the API when Geyser runs here. Without Geyser the server behaves as in stage 2 and
  logs one line saying so. If Geyser's API cannot be reached, a registration throws, or Geyser never asks for custom
  blocks (custom content off), Prism logs `PRISM_BEDROCK_SYNTHETIC_FAIL` with the reason, keeps sending Java looks, and
  writes the legacy Geyser files for the next Geyser start.
- `proxy`: Geyser runs elsewhere. The ids are frozen when the server has loaded and written to
  `plugins/Prism/geyser/prism_synthetic_blocks.json`. Prism ships no proxy module. See the contract below.
- `off`: stage 2 behaviour, no synthetic ids.

## The proxy contract (no extension exists yet)

Geyser's JSON mappings cannot name a non-vanilla state, so a Geyser on a proxy needs an extension. A Prism Geyser
extension must, at `GeyserDefineCustomBlocksEvent`, read `prism_synthetic_blocks.json` (format 1: `base`, `state_count`,
`global_bits`, `piston_behavior` and one entry per block: `java_id`, `java_identifier`, `name`, `look`, `display_name`,
`geometry`, `material_instances`, `collision_box`, `selection_box`, `java_collision`, `transformation`, `destroy_time`,
`can_break_with_hand`), register each block and its non-vanilla state exactly as `GeyserBlockRegistrar` does, and
register the Bedrock pack. Requirements:

- the proxy's Geyser must have a vanilla state count at most `base` (otherwise raise `bedrock.synthetic-base` on the
  server); Geyser's registry then ends at `state_count`, which is the width the export states;
- the backend server must know which connections are Bedrock. That needs Floodgate on the backend (Prism asks its API);
  without it every viewer is a Java viewer and receives no synthetic id;
- the ids on the wire must reach Geyser unchanged: a ViaVersion in front of Geyser may clamp ids it does not know.

The class that does this for in-process Geyser is the reference implementation, and the export file is the stable
interface between the two.

## Alternatives considered

- **A pool of Java states that Bedrock draws identically (borrowing more vanilla states for Bedrock).** No Geyser API
  needed beyond JSON mappings, but the pool is finite (the same 799 + 64 plus what is left), and Java and Bedrock
  would still be unequal. It does not remove the limit.
- **Geyser JSON mappings only.** What stage 2 does. They cannot register states outside vanilla, so the number of looks
  stays bounded by the borrowed states.
- **A per-player block change for every custom block (as ADR-0013 does for model blocks).** Does not need the
  registry, but every block of every chunk costs a packet per Bedrock player, the chunk shows the carrier until it
  arrives, and each block's Bedrock block still has to be defined somewhere, which brings back the identifier limit of
  the JSON mappings (the legacy states).
- **Ids inside the vanilla range, replacing a vanilla block's states for Bedrock only.** A Geyser override for a vanilla
  state changes that block for every Bedrock player, including real blocks. Rejected for the same reason as ADR-0023
  rejects replacing vanilla models.
- **Rewriting the Java chunk for Geyser in the protocol layer of Geyser itself (a Geyser fork).** Not an option; Prism
  is a plugin.

## Consequences

- Bedrock sees every look of every block as a real custom block, with geometry, texture, collision and a hardness close
  to Java's. The 799 and 64 limits no longer apply to Bedrock when `auto` or `proxy` works.
- Java packets are unchanged (the tests assert that a Java viewer's sections never carry an id at or above the vanilla
  count).
- Prism writes the Bedrock pack into its own folder, and keeps Geyser's folder to the item mappings in `auto` mode.
- Ids are fixed for a boot; a look added by a hot reload needs a restart to become a real Bedrock block.
- Bedrock tool speed differs from Java: a synthetic block has no tool tags on Bedrock, so tools do not speed it up.

## Not verified

- A real Geyser registering the states (the call sequence follows the Geyser source and the API javadoc, not a run), a
  real Bedrock client drawing and breaking the blocks, a real Paper with PacketEvents sending the chunk packets.
- Whether mcprotocollib reads the global palette width from the bits byte or from the registry size. The writer
  assumes the latter (it is also correct if the former holds, because the width is written in the byte as well).
- ViaVersion between the server and Geyser passing ids it does not know.
- The Geyser API version in `gradle/libs.versions.toml` (`2.12.1-SNAPSHOT`): it only needs the custom block and resource
  pack events; the CI build resolves it, the sandbox this was written in could not.
- The cost of building the Bedrock pack of a very large content set on Geyser's start-up thread.
