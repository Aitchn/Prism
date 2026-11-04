# Contributing to Prism

Thanks for helping. A few ground rules keep the architecture honest.

## Setup

- JDK 25 (the build uses a Java 25 toolchain). Docker is only needed for the smoke test.
- `./gradlew build` compiles every module, runs the unit tests and the ArchUnit layering rules, and produces
  `prism-plugin/build/libs/Prism-<version>.jar`.
- `smoke-test/run.sh <paper|folia|purpur> <minecraft-version>` boots a real server; see `smoke-test/README.md`.

## Rules that CI enforces

- `prism-core` must not import Folia or platform-implementation types. All thread handling goes through
  `PlatformScheduler`. See [ADR-0002](docs/adr/0002-platform-abstraction.md).
- `prism-api` must not expose PacketEvents, BetterModel or Folia types.
- Never compare Minecraft versions in feature code; add a `Feature` and ask `FeatureMatrix`.
- Compiler warnings are errors.

## Conventions

- Code, comments, API documentation and ADRs are in English. User documentation is bilingual (English is the source,
  Traditional Chinese the translation).
- Documents carry no version suffix in their file name; version differences belong in the changelog and ADRs.
- A significant design change needs an ADR in `docs/adr/` with the alternatives considered.
- Keep pull requests small and say how you tested them.

## License

By contributing you agree that your work is released under the [MIT License](LICENSE).
