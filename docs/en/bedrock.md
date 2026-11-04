# Bedrock

Bedrock players join through Geyser. Prism detects them (Floodgate, then Geyser) and exposes
`ClientProfile.supports(ClientCapability.X)` so a mod adapts by capability, not by edition.

| Feature | Java | Bedrock |
|---|---|---|
| Item appearance | full model | flat icon from the item's texture |
| Dynamic item state | yes | no |
| Blocks | carrier + display entity | carrier + display entity (Geyser translates) |
| BetterModel models | yes | no (not implemented) |
| Menus | chest container | chest container (Geyser translates); native forms not implemented |

Prism builds a Bedrock `.mcpack` and Geyser custom item mappings and serves them under `/bedrock/` on the pack
server. **Status:** registering them with Geyser (same server, or an extension on a proxy) is not implemented
yet; see [Known gaps](https://github.com/Aitchn/Prism/blob/main/docs/gaps.md). Nothing here has been verified on a real Bedrock client.
