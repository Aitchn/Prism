# Docker smoke test

Boots a real server with Prism, PacketEvents and BetterModel and waits for Prism's `PRISM_READY` log line, which is
printed only after the scheduler self check (global, region and async tasks, plus a thread-ownership assertion) has
passed.

```bash
./gradlew build
smoke-test/run.sh paper 26.3
smoke-test/run.sh folia 26.3
smoke-test/run.sh purpur 26.3
```

Needs Docker, `curl` and `jq`. Server jars come from the PaperMC and Purpur download APIs; PacketEvents and BetterModel
come from Modrinth unless `PACKETEVENTS_JAR` / `BETTERMODEL_JAR` point at local files. Logs are kept in
`smoke-test/work/<platform>-<version>/server.log`.

Open question tracked in ADR-0002: whether PacketEvents and BetterModel are safe to call from Folia region threads.
This test only proves they load next to Prism there; it does not exercise their APIs yet.
