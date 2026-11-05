#!/usr/bin/env bash
# Boots a real Paper / Folia / Purpur server in Docker with Prism, PacketEvents and BetterModel installed, and
# checks that Prism detected the right platform and that its scheduler self check passed.
#
# Usage: smoke-test/run.sh <paper|folia|purpur> <minecraft-version> [prism-jar]
# Env:   PACKETEVENTS_JAR, BETTERMODEL_JAR  use local jars instead of downloading from Modrinth
#        STARTUP_TIMEOUT                    seconds to wait for PRISM_READY (default 300)
set -euo pipefail

PLATFORM="${1:?platform: paper|folia|purpur}"
MC_VERSION="${2:?minecraft version, e.g. 26.3}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PRISM_JAR="${3:-$(ls "$ROOT"/prism-plugin/build/libs/Prism-*.jar | head -n1)}"
WORK="$ROOT/smoke-test/work/$PLATFORM-$MC_VERSION"
TIMEOUT="${STARTUP_TIMEOUT:-300}"
NAME="prism-smoke-$PLATFORM"
EXPECTED="$(echo "$PLATFORM" | tr '[:lower:]' '[:upper:]')"

rm -rf "$WORK" && mkdir -p "$WORK/plugins"
cp "$PRISM_JAR" "$WORK/plugins/Prism.jar"
# The example mod proves the whole path: discovery, YAML validation, registration and item creation on a real server.
EXAMPLE_JAR="${EXAMPLE_MOD_JAR:-$(ls "$ROOT"/examples/example-mod/build/libs/*.jar | grep -v -- '-sources' | head -n1)}"
cp "$EXAMPLE_JAR" "$WORK/plugins/ExampleMod.jar"
# The Botania validation mod exercises ticking, persistence and cross-region work on top of Prism (optional).
BOTANIA_JAR="${BOTANIA_MOD_JAR:-$(ls "$ROOT"/validation/botania/build/libs/*.jar 2>/dev/null | grep -v -- '-sources' | head -n1 || true)}"
if [[ -n "$BOTANIA_JAR" ]]; then
  for marker in 'PRISM_MOD_ACTIVE Botania' 'BOTANIA_SELFTEST_OK'; do
    found=""
    for ((i = 0; i < 120; i++)); do
      if docker logs "$NAME" 2>&1 | grep -q "$marker"; then found=1; break; fi
      sleep 1
    done
    if [[ -z "$found" ]]; then
      echo "FAIL: expected log line not seen: $marker" >&2
      docker logs "$NAME" 2>&1 | grep -E 'PRISM_|BOTANIA_|Botania' >&2 || true
      exit 1
    fi
  done
fi
if docker logs "$NAME" 2>&1 | grep -qE 'BOTANIA_SELFTEST_FAIL|PRISM_MOD_FAILED|EXAMPLE_ITEM_FAIL|EXAMPLE_LORE_FAIL|EXAMPLE_RECIPES_FAIL|PRISM_RECIPE_SKIPPED|PRISM_PACK_FAIL|PRISM_BLOCK_SELFTEST_FAIL|PRISM_PLATFORM_SELFTEST_FAIL|PRISM_NATIVE_SELFTEST_FAIL|PRISM_NATIVE_OUT_OF_STATES|PRISM_GEYSER_INSTALL_FAIL'; then
  echo "FAIL: a mod failed to activate" >&2
  exit 1
fi
# Informational: BetterModel model loading is not asserted yet.
docker logs "$NAME" 2>&1 | grep -E 'PRISM_MODEL|PRISM_EXTERNAL_PACK|PRISM_PACK_WARNING' || true
echo "OK: $PLATFORM $MC_VERSION"
