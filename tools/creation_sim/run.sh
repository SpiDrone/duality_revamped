#!/bin/sh
# Runs the character creator's rules outside Minecraft.
#
# The catalog, the draft, the point budget and the name rules import nothing from Minecraft, so a
# balance change or a new race can be checked in a couple of seconds rather than a client launch.
#
#   sh tools/creation_sim/run.sh
set -e
cd "$(dirname "$0")/../.."

OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

javac -nowarn -d "$OUT" \
	src/main/java/net/spidrotech/duality/charactercreation/SkillType.java \
	src/main/java/net/spidrotech/duality/charactercreation/CreationStep.java \
	src/main/java/net/spidrotech/duality/charactercreation/SubraceDefinition.java \
	src/main/java/net/spidrotech/duality/charactercreation/RaceDefinition.java \
	src/main/java/net/spidrotech/duality/charactercreation/CharacterDraft.java \
	src/main/java/net/spidrotech/duality/charactercreation/RaceCatalog.java \
	src/main/java/net/spidrotech/duality/charactercreation/DraftView.java \
	src/main/java/net/spidrotech/duality/charactercreation/CreationAction.java \
	src/main/java/net/spidrotech/duality/charactercreation/CharacterNameFormat.java \
	src/main/java/net/spidrotech/duality/charactercreation/CharacterIdentity.java

javac -nowarn -cp "$OUT" -d "$OUT" tools/creation_sim/CreationHarness.java
java -cp "$OUT" CreationHarness
