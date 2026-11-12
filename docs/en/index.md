# Prism

Prism is a server-side content library for **Paper, Folia and Purpur**. You write "mod-like" content (items, blocks,
models, menus) as an ordinary Bukkit plugin that depends on Prism. **Players install only a resource pack**, on Java
and on Bedrock (through Geyser).

- [Getting started](getting-started.md): your first item in about 30 minutes
- [Concepts](concepts.md): content model, lifecycle, blocks, models
- [Bedrock](bedrock.md): what Bedrock players see and how to adapt
- [Block looks at scale](block-looks.md): borrowed states, the display layer, `prism:render`
- [Translations](translations.md): language files, per-player item names and lore, messages
- [World effects](effects.md): block particles, travelling visuals, beams, tints
- [Known gaps](https://github.com/Aitchn/Prism/blob/main/docs/gaps.md) and [ADRs](https://github.com/Aitchn/Prism/blob/main/docs/adr/README.md)

> Preview status: Prism is `0.1.0-SNAPSHOT`. The API is not frozen and nothing has been verified on a real Bedrock
> client yet. Items, blocks, BetterModel models and the pack pipeline are exercised by CI on Paper, Folia and Purpur.
