# Duality villages — living settlements

Not vanilla villages. These are settlements the mod owns and keeps in files, so they can carry on
existing while nobody is looking at them. A player who leaves for three weeks comes back to a
village that spent three weeks being somewhere: raided, warded, grown, burned, or emptied.

The blocks on the ground are a rendering of the file. The file is the truth.

## The shape of it

    duality_data/
      world.json            world duality score + the simulation clock
      villages/vil_*.json   one file per settlement
      npcs/npc_*.json       one file per named villager, and they outlive their entity
      players/, characters/ (already there — DualityDatabaseManager)

`duality_data/` is the directory `DualityDatabaseManager` already writes character sheets into, so
villages sit next to the rest of the save's bookkeeping rather than in a second place.

## The API

```java
Villages.run("Ashmere", VillageEvent.VAMPIRE_RAID);   // fire an event, get a result back
Villages.run("Ashmere", "vampire_raid");              // same, from a string

Villages.create("Ashmere", VillageFaction.HUMAN, level, pos);
Villages.enroll(village, entity, "HERBALIST");        // give an existing mob a record
Villages.villageAt(level, pos);                       // what village is this
Villages.store().world().dualityScore();              // where the world stands
```

`VillageEventResult` says what happened: the outcome band, the duality delta, the narrative lines
(written to be shown to a player verbatim), and the ids of every NPC the event touched.

## How an event resolves

An event is a threat profile, not a script. It gets weighed against what the village actually has:

    defense (by threat type)  vs  threat x aggressor strength x world lean

Both sides then take a ±30% die roll, and the ratio picks a band:

| ratio | outcome | what lands |
|---|---|---|
| ≥ 1.35 | `REPELLED` | nothing, and morale goes up |
| ≥ 0.95 | `COSTLY` | held, but it cost something |
| ≥ 0.60 | `PARTIAL` | got in; someone taken, something lost |
| < 0.60 | `OVERRUN` | the full consequence |

Defense is three separate numbers, on purpose — a wall is worth nothing against a curse:

| threat | answered by |
|---|---|
| `PHYSICAL` | militia, fortification |
| `MAGICAL` | wards (walls barely count) |
| `SOCIAL` | morale, prosperity, population |

So there are two distinct ways to reinforce a village and two distinct ways to get it wrong. From
`tools/village_sim/run.sh`, raids turned back out of 400 identical vampire raids:

    undefended                          0
    8 militia                         272
    8 militia + walls 5               400

## The event catalogue

Hostile (`VillageEvent`, with the faction that has to be nearby for it to roll on its own):

| event | threat | needs nearby | notes |
|---|---|---|---|
| `VAMPIRE_RAID` | physical | vampire clan | takes prisoners — see below |
| `DEMON_ATTACK` | physical | demon hold | kills, burns buildings, breaks wards |
| `BANDIT_SHAKEDOWN` | social | outlaw camp | costs prosperity, grinds morale |
| `BLOOD_CURSE` | magical | — | walls are irrelevant; this is what wards are for |
| `PLAGUE` | social | — | nobody's fault, still bad |
| `DARK_PACT` | social | — | losing makes the village *stronger* and the world worse |

Boons:

| event | effect |
|---|---|
| `COVEN_PROTECTION` | a witch moves in, +3–5 wards |
| `WHITELIGHTER_VISIT` | morale, a thin ward |
| `SETTLERS_ARRIVE` | population, sometimes a new named resident |
| `CONSTRUCTION` | a building, prosperity, sometimes a wall |
| `GOOD_HARVEST` | prosperity, morale |
| `MILITIA_DRILL` | +1 militia, −2 prosperity |

Add an event: a constant in `VillageEvent` and a case in `VillageEvents.applyOutcome`. Give it a
non-zero `randomWeight` to let the background simulation roll it.

## The abduction chain

This is the part that makes quests out of arithmetic.

A vampire raid that gets in picks a resident — whoever is cheapest to carry off — and:

1. the record moves to the clan hold and is marked `CAPTIVE`,
2. the entity is despawned (or isn't, if the chunk is cold; the record is already right),
3. a **fate** is rolled, with a day on it.

| fate | days | what the player finds if they're late |
|---|---|---|
| `IMPRISONED` | open-ended | still alive — but captors get bored, ~8%/day |
| `TURNING` | 6 | a vampire, living at the hold as one of its own |
| `DRAINED` | 4 | a corpse |
| `SACRIFICE` | 3 | a corpse, and a better-warded hold |
| `ENSLAVED` | open-ended | alive, and not coming home on their own |

Nothing about this waits for the player. `VillageSimulator.resolveFates` runs it off the calendar.
Get there on day 3 and you rescue someone; get there on day 12 and you're fighting what's left of
them. `VillageSimulator.rescue(...)` is the payoff path.

Taken by something with no home the world knows about → `MISSING`, no trail. Deliberate: not every
disappearance should be solvable.

## The world duality score

`world.json`, −1000 (Damned) to +1000 (Radiant). Separate from the per-character `duality_score` on
the character sheet: this one belongs to the save and moves on its own.

- an evil event landing → evil, scaled by how badly it landed and how good the victim was
- an evil event repelled → a little good
- a boon on a good village → good; **a boon on a clan hold → evil**, because an evil place
  prospering is itself a slide
- a prisoner turned → −8, drained → −10, sacrificed → −15; a rescue → +12

And it feeds back: an evil world rolls more raids and its raiders hit harder
(`VillageSimulator.rollEvent`, `VillageEvents.resolveHostile`).

## Running in the background

Villages advance on the **day**, not the tick. Once per in-game day every settlement gets one pass:
drift (wards decay, stores grow, morale settles), maybe an event, and every prisoner's clock ticks.
Between passes it costs a modulo every five seconds.

- `ServerStartedEvent` → load, then work off the backlog, capped at `MAX_CATCHUP_DAYS` (30). A save
  left for a year doesn't spend a minute at boot generating raids nobody will read.
- `ServerTickEvent.Post` → day rollover, at most 3 days per check so `/time set` can't stall the
  server thread.
- `LivingDeathEvent` → an NPC whose entity gets killed is dead in the file too.

## Commands

All op (permission 2). `/village event` and `/village simulate` are how you test a chain that
otherwise takes twenty in-game days to play out.

    /village list | here | info <village> | log <village> [count]
    /village create <name> <faction>          where you're standing
    /village delete <village>
    /village event <village> <event>          fire one now, read the result
    /village simulate [days]                  run the background simulation forward
    /village set <village> <stat> <value>     militia|fortification|wards|morale|prosperity|population|radius
    /village npc add [role]                   enroll the nearest mob as a resident
    /village npc list <village>
    /village npc info <npc>                   status, fate, where they are, how long they have
    /village npc rescue <npc>
    /village world [duality <n> | add <n>]

A five-minute loop to see the whole thing work:

    /village create Ashmere HUMAN
    /village npc add FARMER                   (a few times, pointing at villagers)
    /tp ~500 ~ ~ ; /village create Blackmere VAMPIRE_CLAN
    /village event Ashmere VAMPIRE_RAID
    /village npc list Blackmere               someone of Ashmere's is in there now
    /village simulate 8
    /village npc info <their id>              and now they're something else

## Tuning

Every number lives in one of four places:

| what | where |
|---|---|
| per-faction starting stats, defense formula | `VillageRecord` (`create`, `defenseAgainst`) |
| per-event threat, duality weight, roll odds | the `VillageEvent` table |
| outcome bands, variance, duality direction | `VillageEvents` (`resolveHostile`, `dualityDelta`) |
| day-to-day drift, event frequency, catch-up cap | `VillageSimulator` constants |

Check a change without launching the game:

    sh tools/village_sim/run.sh

The whole village core depends on nothing but Java and Gson — `VillageWorldBridge` is the only seam
that touches entities, and `VillageWorldBridge.NOOP` is a complete implementation of it. The harness
runs raids, round-trips the json, prints the defense curve, and plays out sixty days.

## Not built yet

- **Worldgen placement.** Villages are registered by hand (`/village create`). Nothing places them.
- **A corpse entity.** `NpcRecord.corpseLocation` is recorded and `placeCorpse` logs it; there's no
  body to find in the world yet. The seam is `ServerVillageBridge.placeCorpse`.
- **A custom NPC entity.** `enroll` adopts whatever's standing there (vanilla villagers work), and
  the record stores the entity type, so swapping in a real NPC entity later needs no migration.
- **Surfacing it to the player.** A raid announces itself to anyone within ~96 blocks and otherwise
  goes in the log. There's no quest UI, no rumour system, no "ask around about who went missing".
- **Player-driven reinforcement.** `/village set` moves the numbers; there's no in-game way to build
  a wall or pay for a ward yet.
