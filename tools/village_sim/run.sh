#!/bin/sh
# Runs the village simulation outside Minecraft.
#
# The village core (records, event resolution, the day simulation) depends on nothing but Java and
# Gson, so balance changes can be checked in a couple of seconds instead of a couple of minutes of
# launching a client. Point GSON at any gson jar; the one Gradle already downloaded is easiest.
#
#   sh tools/village_sim/run.sh
#   GSON=/path/to/gson.jar sh tools/village_sim/run.sh
set -e
cd "$(dirname "$0")/../.."

if [ -z "$GSON" ]; then
	GSON=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches" -name 'gson-*.jar' 2>/dev/null | head -n 1)
fi
if [ -z "$GSON" ]; then
	echo "No gson jar found. Build the mod once, or set GSON=/path/to/gson.jar" >&2
	exit 1
fi

OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

javac -nowarn -cp "$GSON" -d "$OUT" \
	src/main/java/net/spidrotech/duality/village/VillageFaction.java \
	src/main/java/net/spidrotech/duality/village/NpcStatus.java \
	src/main/java/net/spidrotech/duality/village/NpcFate.java \
	src/main/java/net/spidrotech/duality/village/NpcJob.java \
	src/main/java/net/spidrotech/duality/village/WorldPoint.java \
	src/main/java/net/spidrotech/duality/village/NpcRecord.java \
	src/main/java/net/spidrotech/duality/village/ThreatType.java \
	src/main/java/net/spidrotech/duality/village/VillageRecord.java \
	src/main/java/net/spidrotech/duality/village/VillageEventOutcome.java \
	src/main/java/net/spidrotech/duality/village/VillageEventResult.java \
	src/main/java/net/spidrotech/duality/village/VillageEvent.java \
	src/main/java/net/spidrotech/duality/village/WorldDualityState.java \
	src/main/java/net/spidrotech/duality/village/VillageStore.java \
	src/main/java/net/spidrotech/duality/village/VillageWorldBridge.java \
	src/main/java/net/spidrotech/duality/village/VillageNames.java \
	src/main/java/net/spidrotech/duality/village/VillageEconomy.java \
	src/main/java/net/spidrotech/duality/village/VillageExpansion.java \
	src/main/java/net/spidrotech/duality/village/VillageEvents.java \
	src/main/java/net/spidrotech/duality/village/VillageSimulator.java

javac -nowarn -cp "$OUT:$GSON" -d "$OUT" tools/village_sim/VillageHarness.java
java -cp "$OUT:$GSON" VillageHarness
