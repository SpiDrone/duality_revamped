# Character creation — the connection points

You're drawing the screens. This is everything behind them.

The shape: the server holds a **draft** while a player is in the creator. Each screen sends what the
player clicked, the server re-checks it against its own rules, and pushes back a **view** — a
precomputed picture with every number a screen needs. Screens draw the view and never compute
anything themselves, so a screen can't disagree with the character that actually gets made.

## The one class your screens talk to

`ClientCharacterCreation`. Every getter is something to draw; every other method is a button.

```java
// is the creator open at all? your cue to open/close the screen
ClientCharacterCreation.isActive();
ClientCharacterCreation.step();            // which of the five
ClientCharacterCreation.statusMessage();   // last thing the server said — a refusal, a confirmation
```

Nothing here is authoritative. `allocate(STRENGTH, +1)` does **not** change what `skill(STRENGTH)`
returns — it asks the server, which answers with a new view a tick later. Draw from the view.

## Screen by screen

### 1 — Race

```java
for (RaceDefinition race : ClientCharacterCreation.allRaces()) {
    boolean locked = !ClientCharacterCreation.canSelect(race);   // draw it greyed, don't hide it
    race.displayName(); race.description(); race.iconHint();     // iconHint is yours, nothing else reads it
    // on press:
    ClientCharacterCreation.selectRace(race.id());
}
```

`allRaces()` is everything; `selectableRaces()` is only what this player has earned. A race is
selectable if it `startsUnlocked()` or its id is in the player's `unlocked_species` — the array
already on their player file. Only **Human** starts open.

Changing race resets the lineage, powers and points below it. The name survives.

### 2 — Subrace

```java
for (SubraceDefinition sub : ClientCharacterCreation.subraces()) {
    sub.displayName(); sub.description();
    sub.grantedAbilities();   // powers handed over free — show them, don't let them be clicked
    sub.skillBonusMap();      // e.g. {ATTUNEMENT: +2, ENDURANCE: -1} for the preview
    ClientCharacterCreation.selectSubrace(sub.id());
}
```

`subraces()` empty means this race has none and screen two can be skipped — `CreationStep.SUBRACE`
already counts as satisfied.

### 3 — Powers

```java
ClientCharacterCreation.abilityChoices();        // the pool, given race + lineage
ClientCharacterCreation.grantedAbilities();      // free ones — display only
ClientCharacterCreation.hasChosen(id);           // checkbox state
ClientCharacterCreation.abilityPicksRemaining(); // "2 picks left"
ClientCharacterCreation.toggleAbility(id);       // adds, or removes if already taken
```

Picking fewer than allowed is fine. Granted powers are never in the choosable pool.

### 4 — Skills

Seven stats, `SkillType`: STRENGTH, AGILITY, ENDURANCE, ATTUNEMENT, PRESENCE, INSIGHT, FORTUNE. Each
has `displayName()` and `description()` for hover text.

```java
ClientCharacterCreation.pointsBudget();          // what's actually spendable: 5 − race cost
ClientCharacterCreation.raceCost();              // what being this race cost
ClientCharacterCreation.pointsRemaining();       // of the budget
ClientCharacterCreation.skill(type);             // total: granted + bought
ClientCharacterCreation.baseSkill(type);         // the granted floor — can't be refunded below
ClientCharacterCreation.allocated(type);         // just what the player paid for
ClientCharacterCreation.canRaise(type);          // enable the + arrow
ClientCharacterCreation.canLower(type);          // enable the −
ClientCharacterCreation.allocate(type, +1);
ClientCharacterCreation.resetSkills();
```

Draw a skill as `baseSkill` + `allocated`, styled differently — the granted part was never the
player's to refund.

**Leftover points are not lost.** They're written to the character as `unspent_skill_points` and
spent later from the in-game stat screen via `CharacterAttributes.spendPoint(player, skill)`. So
walking off screen four with points in hand is a real choice, and the step counts as complete either
way. Creation caps a skill at `SkillType.MAX` (10).

## Free stats, and the point cost — two separate things

A race hands out two different kinds of thing, and they don't interact:

**Free stats** are granted outright. They never come out of the spending budget, and they can't be
refunded and moved onto another skill — those points were never the player's to move. Write them as
the *grant itself*:

```java
RaceDefinition.of(id, name, desc, subraces, abilityPool, picks,
                  Map.of(STRENGTH, 2, AGILITY, 2, PRESENCE, 1),  // ← free stats: "+2 Strength"
                  startsUnlocked, equippedTag,
                  4);                                             // ← pointCost, separate
```

`Map.of(STRENGTH, 2)` means **+2 Strength, free**, landing the character on a Strength of 3 (every
skill floors at `SkillType.MIN`, 1). `allocate(STRENGTH, -1)` on that character is refused —
*"Strength is already down to what being a Vampire gives you."*

**Point cost** is a separate charge against the 5-point budget for being that race at all. It can be
0. A race that grants +2 Strength and costs nothing leaves the player all five points to spend *and*
a Strength of 3 they can build on but never take apart.

| | reads | means |
|---|---|---|
| free stats | `race.freeStat(skill)` | points granted, above the floor |
| free stats, all | `race.freeStatMap()` | `{STRENGTH: 2, AGILITY: 2, PRESENCE: 1}` |
| with lineage folded in | `race.grantedPoints(skill, subrace)` | the actual floor for this build |
| cost | `race.pointCost()` | comes off the budget up front |
| budget | `draft.pointsBudget(race, subrace)` | `STARTING_POINTS − cost`, floored at 0 |

Lineages adjust free stats up or down (`skillBonusMap()`) and can charge a `pointCost` of their own
on top of the race's. Switching to a dearer lineage after spending refunds the difference
automatically rather than leaving the draft overspent — highest-spend skill first.

The starting catalog, for scale:

| race | cost | leaves | free stats |
|---|---|---|---|
| Human | 0 | 5 | Endurance +1, Presence +1 |
| Witch | 2 | 3 | Attunement +3, Insight +1 |
| Whitelighter | 3 | 2 | Attunement +2, Presence +2, Fortune +1 |
| Vampire | 4 | 1 | Strength +2, Agility +2, Presence +1 |
| Demon | 4 | 1 | Strength +3, Attunement +2, Endurance +1 |

A vampire spends 4 of its 5 on being a vampire, leaving one point of freedom — and separately
arrives with five points' worth of stats it never paid for. A human costs nothing, grants little,
and has all five to put wherever it likes.

### 5 — Appearance and name

**The skin editor already works.** Send the existing `SkinNetwork.EquipPartPayload` exactly as the
builder GUI does — `SkinManager` keeps the loadout in memory when there's no active character, and
`commit` writes whatever the player built onto the new sheet. Live preview comes free, no new
plumbing.

One caveat: `SkinUnlocks` grants hang off the *active* character, and mid-creation there isn't one,
so only parts with no unlock requirement are equippable during creation. That's probably what you
want (starter cosmetics only) — if not, that's the place to change it.

```java
ClientCharacterCreation.name();
ClientCharacterCreation.isNameUsable();          // shape check: 1–24 chars, letters/digits/space/'/-
ClientCharacterCreation.setName(field.getValue());
```

A name can still be refused on send for colliding with another of this player's living characters —
only the server knows those, so watch `statusMessage()`.

### Flow and the done button

```java
ClientCharacterCreation.canAdvance();            // "next" enabled on the current screen
ClientCharacterCreation.next();
ClientCharacterCreation.goTo(CreationStep.SKILLS);  // back is always allowed; forward past an
                                                    // unanswered question is refused
ClientCharacterCreation.isStepComplete(step);    // ticks in a stepper widget
ClientCharacterCreation.canCommit();             // every question answered
ClientCharacterCreation.commit();                // server replies with an inactive view — close
```

## What commit actually does

In this order, because the order matters:

1. `DualityDatabaseManager.createNewCharacter` — makes the sheet, links it to the player, makes it
   active.
2. Patches the sheet: `species_profiles[0].class` = race, `.subspecies` = lineage, a `skills` block,
   `unspent_skill_points`, `starting_abilities`, and the `char_creator` appearance from `SkinManager`.
3. `CharacterAttributes.apply` — real attribute modifiers on the player.
4. Ability ids into the `UNLOCKED_ABILITIES` attachment.
5. The race's `equippedTag` into `EquippedAbilities` — the string `VampireRank#isVampire` and the
   `Ret*` procedures already read. Every other race's tag is stripped, so a player whose vampire died
   doesn't come back as a human who still reads as one.
6. `SkinManager.onActiveCharacterChanged` — reloads appearance and unlocks **from the sheet**, which
   is why the sheet has to be complete first.

## What a point is worth

`CharacterAttributes` is the only file that knows. Per point above 1:

| skill | effect |
|---|---|
| STRENGTH | +0.25 attack damage |
| AGILITY | +2% movement speed |
| ENDURANCE | +1 max health (half a heart) |
| ATTUNEMENT | +1 orbing proficiency, −4% orb charge time |
| FORTUNE | +0.5 vanilla luck |
| PRESENCE, INSIGHT | **nothing yet** — sheet-only |

PRESENCE and INSIGHT having no attribute is deliberate, not an oversight: they're numbers other
systems can start reading before they move anything. `CharacterAttributes.skillOf(player, PRESENCE)`
works today, and village standing is the obvious first customer.

Modifiers are permanent and keyed per skill, so re-applying replaces rather than stacks. That matters
because it runs on every login, every respawn and every character switch — not just once.

## Adding content

**`RaceCatalog.registerDefaults()` is the file to edit.** Everything else is machinery. The five
races in there are starting data wired to ability ids that actually exist today; the numbers are a
starting point, not a balance pass. Add a race and screens one and two gain an option with no other
change.

One rule the harness enforces: a race plus any one of its lineages must not cost more than
`STARTING_POINTS`. Over-budget is treated as "nothing to spend" rather than a negative budget, but
it's an authoring mistake and the test will say so.

Check a change in a couple of seconds, no client launch:

    sh tools/creation_sim/run.sh

90 checks: catalog consistency (unique ids, in-range skills, granted powers not double-offered,
nothing priced past the pool), lock enforcement, free stats being genuinely free and genuinely
unremovable, race costs, the point budget, pick limits, what changing your mind cleans up, name
rules, and commit gating.

## Driving it from chat

Every connection point is reachable from `/character` (op-gated), through the same `act()` the
packets use — so you can play the whole flow and argue with the balance before drawing a button.

    /character begin
    /character races                  what screen one would draw, locked ones marked
    /character race witch
    /character subraces               descriptions, grants, stat bonuses
    /character subrace elemental
    /character abilities              the pool, with picks marked
    /character ability lightning_hands_normal
    /character skills                 the stat line, or the live character's if not creating
    /character skill attunement 2
    /character name Mera Holt
    /character status                 the whole DraftView, as the screens see it
    /character commit
    /character list | switch <id> | spend <skill>

## Wire format

Three payloads, same shape as `SkinNetwork`:

| payload | direction | when |
|---|---|---|
| `SyncRaceCatalogPayload` | S→C | on join — every race and lineage |
| `SyncDraftPayload` | S→C | after every change — the whole `DraftView` |
| `CreationActionPayload` | C→S | every button, as one `CreationAction` enum + arg + amount |

One action packet rather than five, so adding a screen doesn't mean adding a payload type. Every
action is re-validated server-side: a race the player hasn't earned, a sixth point out of five, or a
power outside the pool gets a refusal and an unchanged draft.

## Not built

- **The screens.** Yours.
- **An opening trigger.** `CharacterCreation.beginIfNeeded` fires on login and on respawn when the
  player has no living character. Your screen opens off `ClientCharacterCreation.isActive()` — wire
  that to a `ScreenEvent` or a client tick check.
- **The in-game stat screen.** `CharacterAttributes.unspentPoints` and `spendPoint` are the whole
  backend for it; there's no GUI and no packet for it yet.
- **Race abilities beyond unlocking.** Commit puts ability ids in `UNLOCKED_ABILITIES`. Whether a
  race can *use* a given power at a given rank is still the ability's own condition to check.
