# Duality villages — living settlements

Not vanilla villages, and not villages full of vanilla villagers. These are settlements the mod
owns, populated by duality NPCs, kept in files so they carry on existing while nobody is looking at
them. A player who leaves for three weeks comes back to a village that spent three weeks being
somewhere: raided, warded, grown, starved, burned, emptied — or gone, having sent its people out to
found somewhere else.

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

Villages.found("Ashmere", VillageFaction.HUMAN, level, pos, 4);  // village + starting roster
Villages.create("Ashmere", VillageFaction.HUMAN, level, pos);    // just the village
Villages.enroll(village, entity, NpcJob.HERBALIST);   // give an existing mob a record and a job
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

## Who lives there, and what they eat

A village is a set of people who eat. Everyone consumes **one food a day**, named or not. Unnamed
population is subsistence — it feeds itself exactly and no more. Named NPCs are the whole
difference, and what they're worth is their `NpcJob`:

| job | food/day (net) | garrison | wards/day | build |
|---|---|---|---|---|
| `FARMER` | **+2** | 0.5 | — | — |
| `HUNTER` | +1.5 | 1.5 | — | — |
| `SHEPHERD` | +1 | 0.5 | — | — |
| `LABORER` | 0 | 0.5 | — | 0.4 |
| `THRALL` (evil settlements only) | +0.5 | 0.5 | — | 0.3 |
| `GUARD` | **−1** | **4.0** | — | — |
| `SMITH` | −1 | 1.5 | — | 0.2 |
| `MASON` | −1 | 0.5 | — | 1.0 |
| `MERCHANT` | −1 | 0.5 | — | — |
| `HERBALIST` | −0.5 | 0.5 | 0.05 | — |
| `WITCH` | −1 | 1.0 | **0.25** | — |
| `ELDER` / `CHILD` | −1 | — | — | — |

That table is the tension. **Guards don't grow food.** A village that puts everyone on the watch
starves; a village of nothing but farmers gets carried off one at a time. And a resident witch
renews wards faster than the 0.12/day decay, so a village with one never loses its protection while
nobody is looking — which is exactly why raiders come for her first.

Surplus goes in the pantry — and the pantry is a real chest, see below. A full one buys population
growth and the occasional birth. An empty one costs morale, prosperity and then people, and the ones
who don't grow food starve first — children and elders, then guards, then the farmers last of all.

The village manages itself, slowly. `VillageEconomy.neededJob` ranks what's missing by how fast the
lack kills you — food, then the watch, then wards, then trades — and **at most one person changes
trade per day**, hunger getting first claim. So a village reacts over a week, not overnight.

This is what makes an abduction expensive twice over. Losing the smith costs some prosperity. Losing
two farmers tips the food balance negative, and a month later the village that survived the raid is
starving because of it.

### Blight

Reassigning people fixes a badly-staffed village, so on its own the economy can never really starve
one. `BLIGHT` is what can: while `blightDays` is running, the fields yield 40% of normal, and no
amount of shuffling gets that back. Stores drain, people die, and the village keeps its guards
because putting them in a dead field would be pointless.

It's the one hunger a player can be asked to do something about — carry food in, lift the curse, or
find a whitelighter, who clears it on the way out. Sixty simulated days from the harness:

    [Farmstead] Blight -> OVERRUN (duality -12.0)
    Nothing will grow at Farmstead. The granary is empty and the fields are dead.
    day  3: Farmstead has eaten through its stores. They are going hungry.
    day  5: Someone starved at Farmstead.
    day  9: Someone starved at Farmstead.
    day 19: Someone starved at Farmstead.
    day 21: The blight on Farmstead's fields has broken. They can grow food again.
    came out of it with pop 6 (from 10)

## The pantry is a real chest

The village's food is whatever is in the containers inside a pantry building, and nowhere else. Food
in a chest in someone's house is their own business.

This is awkward, because the simulation runs on cold chunks where there are no chests to read. So
the ledger (`foodStores`) stays authoritative — it has to be, it's the thing that runs while nobody
is there — and `VillagePantry` reconciles it against the containers whenever they happen to be
loaded. Both directions matter:

- **The world talks back.** The difference between what's in the chests now and what was in them
  last time anyone looked is exactly what a player added or took. That lands on the ledger, so
  walking up and emptying your inventory into the village chest genuinely feeds them — and it's
  worth a nudge on the world duality score, because outside food is the one thing that breaks a
  blighted village's death spiral.
- **The ledger talks back.** Whatever the village ate or grew while you were away is pushed into the
  containers, so coming back after a hard month you find the pantry emptied rather than exactly as
  you left it.

If a pantry building is placed and loaded but has no container, the mod puts a chest at the surface
of that column — so a village always has somewhere for you to leave food. Break it and take the
bread and the ledger notices on the next sweep: the village really has been robbed.

Nutrition maps to food units at 4:1 (a loaf of bread ≈ 1.25 days for one villager). Anything with a
status effect on it — rotten flesh, pufferfish — doesn't count; a village isn't fed on things that
make people ill.

**No pantry, no future.** Capacity comes only from pantry buildings, so a village with nowhere to
put food is capped at `NO_PANTRY_CAPACITY` (8) and any bad week kills people. Building one is the
single biggest thing a settlement can do for itself, and the first thing `neededBuilding` returns.

## What buildings are for

Buildings used to be a count. Each one now does something specific, and the something is the reason
to build it:

| building | what it's for |
|---|---|
| `PANTRY` / `GRANARY` / `BARN` | **where the food physically is** — 60 / 140 / 40 units |
| `HOUSE` | houses 4; population past the housing stops growing |
| `FARM` | +2 food/day on top of the farmers |
| `CHURCH` | **doubles the odds of a whitelighter or coven visit**; blunts dark pacts and curses |
| `SHRINE` | keeps a thin ward lit on its own; blunts curses |
| `ALTAR` | the evil mirror of a church — favours dark pacts, repels whitelighters |
| `PALISADE` / `WATCHTOWER` | walls; the tower also garrisons and blunts raids |
| `BARRACKS` | garrison, and makes drilling worth doing |
| `SMITHY` | garrison and prosperity |
| `WELL` | blunts plague |
| `INFIRMARY` | blunts plague and blight |
| `TAVERN` | morale, and draws settlers |

Two of those columns aren't numbers. `favors` multiplies an event's odds of being rolled here (×2
per building, so two churches quadruple your whitelighter odds); `resists` multiplies the incoming
threat down (×0.75 per building, floored at 0.35). That's how a church buys better odds of being
blessed and a well makes a plague survivable.

A village builds what it would most regret not having, in order: somewhere to put food, somewhere to
live, more storage if the pantry is overflowing, a farm if the balance is thin, then holy ground, a
well, walls, a smithy.

## New settlements

Nothing places villages — there's no worldgen hook. The map gains settlements one of two ways:
`/village create` (or `Villages.found`), and villages founding their own.

A village that is fed, full and well off sends people out: population ≥ 18, prosperity ≥ 55, a mason
or laborer to build with, and a pantry filled past 60% of what it can hold — which, since capacity
*is* the pantry, means a village that wants to spread has to have built granaries first. Then a 6%
roll per day. `VillageExpansion` sites the
daughter 420–900 blocks out, no closer than 380 to anything existing, asks the bridge to snap it to
real ground (and never generates terrain to do it), then moves 5–8 population and one or two named
residents across — never the parent's last food producer.

It cuts both ways. A clan hold that keeps winning spreads too, and a faction spreading moves the
duality score with it. Left alone long enough, a corner of the map fills with vampire holds seeding
more vampire holds.

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
    /village create <name> <faction> [n]      where you're standing, with n named residents
    /village delete <village>
    /village event <village> <event>          fire one now, read the result
    /village simulate [days]                  run the background simulation forward
    /village set <village> <stat> <value>     militia|fortification|wards|morale|prosperity|population|radius|food
    /village npc add [job]                    enroll the nearest mob as a resident
    /village npc job <npc> <job>              reassign them — watch the food balance move
    /village npc list <village>
    /village npc info <npc>                   status, fate, where they are, how long they have
    /village npc rescue <npc>
    /village building add <type>              put one up where you stand — a pantry binds the chest
    /village building list <village>          what's built and what each one does
    /village pantry <village>                 reconcile the chests with the ledger, report both
    /village world [duality <n> | add <n>]

A five-minute loop to see the whole thing work:

    /village create Ashmere HUMAN 5           founds it with five named residents and jobs
    /village info Ashmere                     food balance, garrison, who does what
    /village npc job <a farmer> GUARD         now watch the food balance go negative
    /tp ~500 ~ ~ ; /village create Blackmere VAMPIRE_CLAN 4
    /village event Ashmere VAMPIRE_RAID
    /village npc list Blackmere               someone of Ashmere's is in there now
    /village simulate 8
    /village npc info <their id>              and now they're something else
    /village event Ashmere BLIGHT             and now the survivors are hungry
    /village pantry Ashmere                    find the chest; drop bread in it
    /village building add CHURCH               now whitelighters are twice as likely here

## Tuning

Every number lives in one of four places:

| what | where |
|---|---|
| per-faction starting stats, defense formula | `VillageRecord` (`create`, `defenseAgainst`) |
| per-event threat, duality weight, roll odds | the `VillageEvent` table |
| outcome bands, variance, duality direction | `VillageEvents` (`resolveHostile`, `dualityDelta`) |
| what each job is worth per day | the `NpcJob` table |
| what each building is worth, and what it favours/resists | the `BuildingType` table |
| nutrition per food unit, granularity tolerance | `VillagePantry` constants |
| food rates, blight yield, job churn, build cost | `VillageEconomy` constants |
| when and where villages found new ones | `VillageExpansion` constants |
| day-to-day drift, event frequency, catch-up cap | `VillageSimulator` constants |

Check a change without launching the game:

    sh tools/village_sim/run.sh

The whole village core depends on nothing but Java and Gson — `VillageWorldBridge` is the only seam
that touches entities, and `VillageWorldBridge.NOOP` is a complete implementation of it. The harness
runs raids, round-trips the json, prints the defense curve, and plays out sixty days.

## The NPC entities

These are duality settlements with duality NPCs in them, so `VillageNpcTypes` is the single place
that answers "what does this record look like in the world", and it asks for the mod's own entities
first, every time. It tries ids most specific first:

    duality:npc_vampire_guard     species + job
    duality:npc_guard             job
    duality:npc_vampire           species
    duality:npc_witch_coven       faction
    duality:npc                   the general duality villager
    minecraft:villager            stand-in, until one of the above exists

`duality:npc` is registered (`village/entity/DualityNpcEntities`), so records resolve to the mod's
own entity rather than the vanilla stand-in. The more specific rows are still free: register any
subset and it's picked up immediately, because records store a species and a job rather than an
entity id, so there's no migration to do.

`DualityNpcEntity` holds no state beyond the id of the record it belongs to — job, status, fate and
history stay in the file, and `record()` is the source of truth. Its goals read the record every
time rather than caching, which is why an abducted NPC behaves correctly without anything having to
notify the entity. It renders with vanilla's villager model for now; per-species and per-job looks
switch on the synced `species()` / `jobId()` in `DualityNpcRenderer` and touch nothing else.

The exception is `/village npc add`, which pins whatever entity you pointed at onto the record — a
vanilla villager enrolled that way stays a vanilla villager. That's the "villagers might also live
there" case, and it's deliberately the only way to get one.

## Not built yet

- **Species and job NPC variants.** `duality:npc` exists; the more specific ids above don't, and
  every record currently resolves to the general one wearing a vanilla villager skin.
- **Worldgen placement.** Villages are founded by command or by other villages, never by the map
  generator.
- **A corpse entity.** `NpcRecord.corpseLocation` is recorded and `placeCorpse` logs it; there's no
  body to find in the world yet. The seam is `ServerVillageBridge.placeCorpse`.
- **NPC behaviour in the world.** A farmer still farms in the ledger, not in the fields. Residents
  now stay inside their village (`NpcStayHomeGoal`) and walk to the pantry chest
  (`NpcVisitPantryGoal`), but the trip is scenery: eating is still resolved entirely in the daily
  simulation and arriving consumes nothing. Making the walk *be* the meal means moving the food draw
  out of the day tick, which needs deciding whether an unloaded village still eats — it does today,
  and that's why the ledger owns it. Standing a shift at a workstation needs a job-to-building
  mapping, which doesn't exist yet: `NpcJob` and `BuildingType` have no link between them.
- **Buildings as structures.** A building is a type and a position, not blocks. Nothing raises a
  church; `/village building add` registers one wherever you've built it yourself, and the only
  block the mod ever places is the pantry chest.
- **Surfacing it to the player.** A raid announces itself to anyone within ~96 blocks and otherwise
  goes in the log. There's no quest UI, no rumour system, no "ask around about who went missing".
- **Player-driven reinforcement.** `/village set` moves the numbers; there's no in-game way to build
  a wall, pay for a ward, or hand a hungry village a stack of bread.
