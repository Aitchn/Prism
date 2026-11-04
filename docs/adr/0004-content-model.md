# ADR-0004: Content model, registries and mod lifecycle

Status: Accepted (2026-11-04)

## Context

M1 needs a way for mods to declare content (YAML inside their jar) and attach Java logic, without the core knowing
any concrete item and without Prism-old's habit of dispatching on ids.

## Decision

- **Identity**: `PrismKey` (`namespace:value`). `prism` and `minecraft` are reserved. A mod's namespace defaults to the
  lower-cased plugin name; the content file `prism/items/gems/ruby.yml` is `<namespace>:gems/ruby`.
- **Registries**: `Registry<T>` is a read view; `snapshot()` returns an immutable `RegistrySnapshot<T>`. Writes are
  owner-bound and transactional (`MutableRegistry.publish` adds a whole batch or nothing; `unregisterOwner` removes
  everything of a mod).
- **Components** are immutable data decoded from YAML by a registered `ComponentType<T>`; **behaviors** are
  capability interfaces (`ItemUseBehavior`, ...) created by a registered `BehaviorType<B>`. A definition is
  identity + base item + `ComponentMap` + behaviors. Scalar component values arrive as `{value: ...}`.
- **Lifecycle** (`ModManager.activate`): `onInitialize` registers types into a staging area, content is loaded and
  validated with all problems collected, then types and definitions are published atomically, then
  `onContentLoaded`. Any failure leaves nothing registered and does not touch other mods. `deactivate` removes
  everything and calls `onDisable`.
- **Dispatch**: `BehaviorDispatcher` looks the definition up by key and runs the capability instances it has. It
  checks thread ownership first, and a throwing behavior is logged and skipped.
- **Discovery** (plugin module): a plugin that depends on Prism is a mod if its main class implements `PrismMod`,
  if it calls `Prism.mods().register(...)`, or if its jar has `prism/`. The jar is opened read-only for the duration
  of activation; nothing is extracted. `prism-mod.json` (optional) can override namespace and content path and state
  the minimum API (`"prismApi": "0.1"`, compared as major.minor).
- **Items**: the server stack is the vanilla carrier plus the key in persistent data (`ItemService`). Appearance is
  not stored on the stack (M2 decides it per viewer).
- **Testability**: `prism-api` and `prism-core` need no server. `prism-testkit` loads a mod's real content through the
  real lifecycle.

## Consequences

- `prism-api` depends on `paper-api` (compile-only) for `Player` and `ItemStack`; the YAML/registry/lifecycle code
  does not.
- SnakeYAML is used through the server's copy (compile-only), isolated in one class.
- Only items exist so far. Blocks, entities, menus, recipes and persistence get their own capability interfaces in
  later milestones; see [gaps](../gaps.md).
