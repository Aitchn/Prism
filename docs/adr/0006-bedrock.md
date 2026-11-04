# ADR-0006: Bedrock support: ClientProfile, Bedrock pack and Geyser mappings

Status: Accepted (2026-11-04). Partly unverified: nothing here has been run against a real Bedrock client.

## Context

Bedrock players join through Geyser (same server, or a Geyser on a proxy such as Velocity). Prism content must stay
usable for them, and mods need a way to adapt when a client cannot show something.

## Decision

- **Detection**: Floodgate first, then the Geyser API, both by reflection (no compile or load time dependency). If
  neither is present every player is Java. `Prism.clients().profile(player)` returns a `ClientProfile`.
- **Capabilities, not editions**: mods ask `profile.supports(ClientCapability.X)`. The table lives in
  `prism-core` `Profiles`. Bedrock supports custom item appearance (flat icon), display entities, container menus and
  form menus; it does not support dynamic item state, BetterModel model entities or custom fonts.
- **Bedrock content** (`BedrockPackBuilder`, `prism-pack`): a deterministic `.mcpack` with one icon per item and
  Geyser custom item mappings (format 2) keyed on the Java `item_model`, which is the item's own key (ADR-0005).
  Items without a texture are skipped with a warning and keep the vanilla Bedrock look of their base item.
- **Distribution**: `PackServer` serves `/bedrock/manifest.json`, `/bedrock/<sha1>.mcpack` and `/bedrock/<sha1>.json`
  (immutable by hash). A Geyser on the same server can be handed the files directly; a Geyser extension on a
  proxy fetches them over HTTP and compares hashes. The extension and the in-process Geyser registration are the
  next increment (M3b) because they need the Geyser API on the compile classpath.
- **Models**: `BedrockGeometry` (prototype, see docs/prototypes/bedrock-model-mapping.md) converts BetterModel
  `.bbmodel` files to Bedrock geometry. It is not yet wired into the pack: complex models degrade to the base
  appearance until it has been verified on a client.

## Consequences

- Bedrock players see flat item icons and the carrier block plus display entities for blocks; this is a documented
  degradation, queryable through `ClientProfile`.
- The Bedrock pack is rebuilt together with the Java pack on every content change.

## Menus (M3b)

`Prism.menus().open(player, MenuSpec, handler)` shows a list of buttons. The first renderer is a chest container,
used for Java and for Bedrock (Geyser translates containers). A native Bedrock form renderer will reuse `MenuSpec`;
it is not built yet because it needs Floodgate/Cumulus on the compile classpath.
