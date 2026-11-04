# Prism

Prism is a server-side content library for **Paper, Folia and Purpur**. Creators build "mod-like" content (items,
blocks, GUIs, models) as ordinary Bukkit plugins that depend on Prism. **Players install nothing but a resource
pack**, on Java and on Bedrock (through Geyser).

```
Plugin (Prism.jar)  ->  Prism (API + runtime)  ->  Mod (a plugin written by a creator)
```

> **Status: M0, project skeleton.** The build, platform detection, scheduler abstraction and smoke tests exist;
> there is no content model yet. See the [roadmap](docs/plans/architecture-and-roadmap.md) (Traditional Chinese
> planning draft) and the [ADRs](docs/adr/README.md).

## What works today

One `Prism.jar` that starts on Paper, Folia and Purpur, detects the platform and Minecraft version, and logs the feature
matrix:

```
Prism 0.1.0-SNAPSHOT running on Folia (FOLIA), Minecraft 26.3, regionized=true
Feature matrix:
  [x] DATA_COMPONENTS
  ...
PRISM_READY platform=FOLIA minecraft=26.3 regionized=true
```

Requires [PacketEvents](https://github.com/retrooper/packetevents) and
[BetterModel](https://github.com/toxicity188/BetterModel) on the server.

## Build

```bash
./gradlew build          # needs JDK 25
smoke-test/run.sh folia 26.3   # needs Docker, curl, jq
```

## Layout

See [ADR-0001](docs/adr/0001-module-layout.md). `prism-api` is what creators compile against; everything else is
internal.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE)
