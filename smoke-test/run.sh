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
# The Mekanism rebuild (validation/mekanism) is a real mod on the platform: blocks with data, ticking, capabilities,
# menus, recipes. It runs its own self test (MEKANISM_SELFTEST_OK) because no client joins.
MEKANISM_JAR="${MEKANISM_JAR:-$(ls "$ROOT"/validation/mekanism/build/libs/*.jar | grep -v -- '-sources' | head -n1)}"
cp "$MEKANISM_JAR" "$WORK/plugins/Mekanism.jar"

download_server() {
  case "$PLATFORM" in
    paper|folia)
      # https://docs.papermc.io/misc/downloads-api
      local url
      url="$(curl -fsSL "https://fill.papermc.io/v3/projects/$PLATFORM/versions/$MC_VERSION/builds/latest" \
        | jq -r '.downloads["server:default"].url')"
      curl -fsSL -o "$WORK/server.jar" "$url" ;;
    purpur)
      curl -fsSL -o "$WORK/server.jar" "https://api.purpurmc.org/v2/purpur/$MC_VERSION/latest/download" ;;
    *) echo "unknown platform: $PLATFORM" >&2; exit 2 ;;
  esac
}

# Newest release file on Modrinth that is built for a Bukkit-family loader.
download_modrinth() {
  local slug="$1" out="$2" url
  url="$(curl -fsSL "https://api.modrinth.com/v2/project/$slug/version?loaders=%5B%22paper%22,%22folia%22,%22spigot%22,%22bukkit%22%5D" \
    | jq -r '[.[] | select(.version_type=="release")][0].files | (map(select(.primary))[0] // .[0]).url')"
  curl -fsSL -o "$out" "$url"
}

download_server
if [[ -n "${PACKETEVENTS_JAR:-}" ]]; then cp "$PACKETEVENTS_JAR" "$WORK/plugins/"; else download_modrinth packetevents "$WORK/plugins/packetevents.jar"; fi
if [[ -n "${BETTERMODEL_JAR:-}" ]]; then cp "$BETTERMODEL_JAR" "$WORK/plugins/"; else download_modrinth bettermodel "$WORK/plugins/BetterModel.jar"; fi

cp "$ROOT/smoke-test/Dockerfile" "$WORK/Dockerfile"
IMAGE="prism-smoke:$PLATFORM-$MC_VERSION"
docker build -q -t "$IMAGE" "$WORK" >/dev/null

docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" "$IMAGE" >/dev/null
cleanup() { docker logs "$NAME" > "$WORK/server.log" 2>&1 || true; docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

ready=""
for ((i = 0; i < TIMEOUT; i++)); do
  ready="$(docker logs "$NAME" 2>&1 | grep -m1 'PRISM_READY' || true)"
  [[ -n "$ready" ]] && break
  if [[ "$(docker inspect -f '{{.State.Running}}' "$NAME")" != "true" ]]; then break; fi
  sleep 1
done

if [[ -z "$ready" ]]; then
  echo "FAIL: PRISM_READY not seen within ${TIMEOUT}s (or the server exited). Last log lines:" >&2
  docker logs --tail 80 "$NAME" >&2 || true
  exit 1
fi
echo "$ready"

if ! grep -q "platform=$EXPECTED " <<<"$ready"; then
  echo "FAIL: expected platform=$EXPECTED" >&2
  exit 1
fi
if docker logs "$NAME" 2>&1 | grep -q 'PRISM_SCHEDULER_FAIL'; then
  echo "FAIL: scheduler self check reported a failure" >&2
  exit 1
fi
# Mods enable before the first tick, so these lines normally exist already; allow a few seconds anyway.
for marker in 'PRISM_MOD_ACTIVE ExampleMod items=3 blocks=4' 'PRISM_MODELS_SYNCED changed=1' 'EXAMPLE_ITEM_OK examplemod:ruby' 'EXAMPLE_LORE_OK' 'EXAMPLE_RECIPES_OK polishing=1' 'PRISM_RECIPES_READY added=[0-9][0-9]* skipped=0' 'PRISM_PACK_READY sha1=' 'PRISM_BEDROCK_PACK_READY sha1=.* blocks=[0-9][0-9]* ' 'PRISM_DATA_OK' 'PRISM_BLOCK_SELFTEST_OK examplemod:ruby_block' 'PRISM_NATIVE_SELFTEST_OK examplemod:ruby_block' 'PRISM_GEYSER_EXPORTED' 'PRISM_PLATFORM_SELFTEST_OK examplemod:ruby_counter' 'PRISM_ORE_SELFTEST_OK examplemod:ruby_ore' 'PRISM_MOD_ACTIVE Mekanism items=23 blocks=9' 'MEKANISM_RECIPES_OK machines=5 recipes=13' 'MEKANISM_SELFTEST_OK'; do
  found=""
  for ((i = 0; i < 90; i++)); do
    if docker logs "$NAME" 2>&1 | grep -q "$marker"; then found=1; break; fi
    sleep 1
  done
  if [[ -z "$found" ]]; then
    echo "FAIL: expected log line not seen: $marker" >&2
    docker logs "$NAME" 2>&1 | grep -E 'PRISM_|EXAMPLE_|ExampleMod|MEKANISM_|Mekanism' >&2 || true
    echo "--- errors and stack traces ---" >&2
    docker logs "$NAME" 2>&1 | grep -E 'ERROR|SEVERE|Exception|Caused by|^[[:space:]]+at (com\.aitchn|io\.papermc\.paper\.threadedregions)' | head -80 >&2 || true
    exit 1
  fi
done
if docker logs "$NAME" 2>&1 | grep -qE 'PRISM_MOD_FAILED|EXAMPLE_ITEM_FAIL|EXAMPLE_LORE_FAIL|EXAMPLE_RECIPES_FAIL|PRISM_RECIPE_SKIPPED|PRISM_PACK_FAIL|PRISM_BLOCK_SELFTEST_FAIL|PRISM_PLATFORM_SELFTEST_FAIL|PRISM_NATIVE_SELFTEST_FAIL|PRISM_ORE_SELFTEST_FAIL|PRISM_ORE_UNKNOWN_BLOCK|PRISM_NATIVE_OUT_OF_STATES|PRISM_GEYSER_INSTALL_FAIL|MEKANISM_SELFTEST_FAIL|MEKANISM_RECIPES_FAIL'; then
  echo "FAIL: a mod failed to activate" >&2
  exit 1
fi
# Informational: BetterModel model loading is not asserted yet.
docker logs "$NAME" 2>&1 | grep -E 'PRISM_MODEL|PRISM_EXTERNAL_PACK|PRISM_PACK_WARNING' || true
echo "OK: $PLATFORM $MC_VERSION"
