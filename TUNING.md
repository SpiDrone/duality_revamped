# Duality Underworld — worldgen tuning notes

Drop `src/main/resources/data/duality/` into your workspace, merging with
what's there.

## Vertical layout (min_y 0, height 64)

| y | contents |
|---|---|
| 0–3 | bedrock |
| 4–~29 | solid mass (room for pits, vaults, lower floors) |
| ~28–32 | floor band, zero-crossing ~y30, wobbles ±1 |
| ~30–38 | air, ~8 blocks headroom |
| ~38–64 | roof mass, ~26 blocks |

Both bands live in `density_function/underworld/cavern_shell.json` as
`y_clamped_gradient` blocks. Headroom = roof zero-crossing minus floor
zero-crossing; each crossing sits at the midpoint of its gradient's
`from_y`/`to_y`.

Wobble amplitude converts to blocks as `shift = 2 * amplitude`, because the
gradients run 1.0 to -1.0 over 4 blocks (slope 0.5/block). Floor is at 0.5
(±1 block), ceiling at -0.25 (±0.5). Keep these small relative to headroom —
at 8 blocks of clearance, large wobble makes the ceiling claustrophobic in
patches.

## Corridor width — THE ONE KNOB YOU'LL TUNE

`density_function/underworld/corridor_half_width.json`:

    "argument1": 0.125    <- base half-width (t)
    "argument1": -0.04    <- how much the region noise tightens/widens it

Width is linear in t. Generate, fly a corridor, count blocks, scale:

    new_t = 0.125 * (5 / measured_width)

The `-0.04` term makes dominion regions tighter than the wastes; set it to 0
to decouple them. Keep it at roughly a third of t.

## Corridor spacing = open/solid ratio

`worldgen/noise/underworld_corridor_a.json` and `_b.json`, `firstOctave`:

    -4  ->  ~16 block lattice, ~53% open   (cavernous)
    -5  ->  ~32 block lattice, ~29% open   [current]
    -6  ->  ~64 block lattice, ~15% open   (deep rock, sparse tunnels)

Open fraction is roughly `2 * width / lattice`. Larger lattice leaves
thicker solid masses between corridors — that's the material demon lairs get
carved into.

### IMPORTANT: t and firstOctave are coupled

Corridor width is `2t / |gradient|`, and the gradient halves every time the
lattice doubles. So each step of -1 in firstOctave DOUBLES corridor width at
fixed t. Halve t alongside it or the change buys you nothing.

Keep both corridor files identical — they're seeded separately by ID, so
identical parameters still give two independent fields. Keep `amplitudes`
short ([1.0, 0.35]); a wide spectrum reintroduces the width variance the
contour trick exists to suppress.

## Why connectivity survives going sparse

This is a contour union, not a density threshold. Air is carved where
`|noiseA| < t` OR `|noiseB| < t` — the thickened zero-contours of two
independent fields. Zero-contours are closed loops, and two families of
loops in general position always cross transversally. The maze is connected
by geometry, not by percolation, so open fraction can drop well below any
percolation threshold without fragmenting.

The real floor is ergonomic: corridors eventually get too thin to walk.

## Why the corridors come out sharp

Nothing here is wrapped in `minecraft:interpolated`, so `final_density` is
evaluated per block rather than on the 4x4 cell grid. Same path vanilla's
noodle caves take. That's what lets a 5-wide corridor actually be 5 wide.

## Biome split

`dimension/underworld.json` splits on temperature at 0.15, and the noise
router feeds `duality:underworld/region` into temperature — the same
function `corridor_half_width` reads. Look and shape stay in sync.

Move the 0.15 to change the dominion/wastes ratio. Lower = more dominion.

## Resolved: carvers field

`"carvers"` is a MAP in 1.21.1, not a list. A list throws
`IllegalStateException: Not a JSON object: []` at registry load. Both biome
files use `"carvers": {}`, which is valid and avoids depending on which
carving-step keys the enum still exposes.

## Demon stronghold (multi-piece)

    worldgen/structure/demon_stronghold.json
    worldgen/structure_set/demon_strongholds.json
    worldgen/template_pool/demon_stronghold/*.json   (6 pools)
    structure/demon_*.nbt                            (6 templates)

    demon_hall              21x13x21   15x15 interior, 4 connectors
    demon_corridor_prison    9x13x13   lined hallway
    demon_corridor_bed       9x13x13   lined hallway
    demon_prison            19x13x19   13x13 interior, caged villagers
    demon_bedchamber        19x13x19   13x13 interior, lived-in quarters
    demon_entry             21x13x 9   lined tunnel out to the cavern

Chain: hall -> corridor -> room. `size: 3` in the structure JSON is exactly
that depth; raise it if you add pieces further down the chain (e.g. a stair
down to a lower level and rooms off it).

### Cavern door gate — `duality:cavern_jigsaw`

The structure type is `duality:cavern_jigsaw`
(`world/structure/CavernJigsawStructure.java`), not vanilla `minecraft:jigsaw`.
It assembles the layout, then only lets the stronghold generate if at least
one DOOR opens straight onto cavern — no mining to get in.

A door is a jigsaw block named `door_jigsaw` (`duality:demon_cavern_door`)
facing OUT of the structure, pool `minecraft:empty`, so assembly ignores it
and it's placed as air. `demon_entry` has one at its mouth. Add the same
marker to any new piece that should count as an entrance.

    "door_jigsaw": "duality:demon_cavern_door"
    "placement_attempts": 8   <- layouts tried per candidate chunk (new
                                 offset + rotation each time) before giving up
    "door_clearance": 3       <- blocks in front of the door that must be
                                 walkable: 2 air stacked, within 1 block of
                                 the door's floor level

The test reads the raw noise column (`ChunkGenerator.getBaseColumn`), so it
sees the labyrinth exactly as generated. Doors on a lower level are fine to
add — they can never pass (the cavern only exists at y30–38), so the main
level is still the one that has to connect.

Rejected layouts retry from the chunk's worldgen random, so `/locate` and
real generation agree. Lower `placement_attempts` if strongholds show up too
often; raise `door_clearance` to demand a deeper cavern outside the door.

`/place structure` goes through the gate too, and fails when no layout from
that chunk reaches a cavern. To look at the build regardless of terrain, use:

    /place jigsaw duality:demon_stronghold/start duality:demon_hall_exit 3 ~ ~ ~

The entry tunnel whose door didn't connect still dead-ends in rock.

### Vertical

All pieces 13 tall at absolute y28 -> world 28-40. Interior is world 30-38,
9 blocks. The cavern band is only 30-37, so rooms punch ~3 blocks up into the
ceiling rock — the complex is carved INTO the roof, not standing under it.

### Palette — no Nether blocks

Deepslate bricks/tiles/polished/cobbled, blackstone, polished blackstone
bricks (+ cracked and chiseled), obsidian, crying obsidian, coal, gilded
blackstone. Nether brick, soul sand and soul lanterns are all gone.

RAW deepslate appears ONLY as the outer casing, where it has to match natural
rock. Every surface a player can see from inside is a built block — including
the hallways, which are a lined tunnel bored through a solid deepslate block
rather than a raw-walled passage.

### Floor cracks

`Piece.floor_crack(path, salt, lava_chance)` cuts a 1-deep groove, fills the
bottom with lava or obsidian, and sets stair lips leaning in from either side.
`lava_chance` is out of 5 (default 2). Set 0 for a cold crack.

Cracks are deliberately offset from walking lines in corridors and the entry
tunnel — a lava groove down the middle of a 3-wide passage is a hazard, not
decor. The hall cracks DO cross open floor, on purpose; drop `lava_chance` to
0 there if playtesting makes it annoying.

If the stair lips lean the wrong way, flip `OPPOSITE[face]` to `face` in
`floor_crack` — which side the step rises on is a cosmetic coin-flip.

### Bed chamber

Six black beds in wall alcoves with chiseled headboards and red candles,
chests at the foot of each, barrels stacked in the corners, cobwebs, worn
patches of cobbled deepslate and coal in the floor, and a sunken 3x3 lava
hearth ringed in obsidian with a lantern on a chain above it.

Chests and barrels are placed with no block entity NBT, so they generate
empty. Point them at a loot table when you have one.

### Decoration helpers

`pick(options, *coords)` chooses a variant from a deterministic hash of the
coordinates — reproducible between runs, so regenerating does not reshuffle
the whole build. Increase the number of cracked/alternate entries in a
`wall_mix` tuple to age a room further.

### Iron bars

Structure placement does not run neighbour updates, so bar connection
properties are baked in by `resolve_bars()`. Without it the cage renders as
disconnected posts.

### Villagers

Four, in the `entities` list of `demon_prison.nbt`, inside a 7x7 barred cage
with a barred roof. Minimal NBT (`id` + `PersistenceRequired`); everything
else defaults on load.

### Finding it

    /place structure duality:demon_stronghold

`spacing: 12 / separation: 5` is dense for testing; raise for real play.
`terrain_adaptation` stays `"none"` — beard or encapsulate would pack stone
around the pieces and seal the entry tunnels.

### Casing tradeoff

The deepslate casing is a solid plug in the labyrinth; corridors that meet it
dead-end. The cavern door gate guarantees at least one way in from the maze;
it does not open up the other corridors the casing blocks.

### Regenerating

`build_stronghold.py` at the repo root rebuilds all six templates, no
dependencies. It deletes `structure/demon_*.nbt` first, so anything you add by
hand under that prefix will be wiped — rename it or add it to the script.

## Not included yet

- Mob spawns (`spawners` is `{}` in both biomes)
- Features (all 11 decoration steps empty)
- Structures — placement is simple now that the air band sits at a known Y:
  fixed start height around y31, `terrain_adaptation: "none"`,
  `structure_void` for archways. With ~27-block solid masses between
  corridors there's real room to carve lairs back into the rock.

# Demonic spider — behavior tuning

Every number below is a named constant, not a magic literal. The three marked
**measure in game** cannot be resolved by reading code; they need one play
session each.

## Chase speed — **measure in game**

`SpiderQueenEntity.CHASE_SPEED` (x the 0.3 MOVEMENT_SPEED attribute).

The brief is "slightly slower than the player", i.e. a sprinting player can
just barely open a gap. `1.0` is vanilla-spider chase pace and is the starting
point. Sprint one away down a flat corridor: if it falls behind quickly, raise
toward `1.15`; if it closes on a sprint, drop toward `0.9`. Change the
multiplier, not the attribute — the attribute also feeds the wander goal, and
raising it makes the idle spider skitter.

## Pathing around obstacles

How it works: the pathfinder only walks. Climbing comes from a fallback in
`DemonSpiderNavigation` that steers the spider straight at the target once a
path runs out, and walking into a wall is what starts a climb. That fallback is
now refused when it can't help (directly under the target with no wall, or a
ceiling overhead), which is what stopped the endless jumping in place.

| constant | default | what it does |
|---|---|---|
| `DemonSpiderNavigation.SEARCH_MULTIPLIER` | 4 | node budget x vanilla; how big a walking detour it can find |
| `DemonSpiderChaseGoal.STUCK_TICKS` | 40 (2s) | time without getting closer before it looks for another way |
| `DemonSpiderChaseGoal.DETOUR_RADIUS` | 10 | how far around the target it samples standing spots |
| `DemonSpiderChaseGoal.CLIMB_SUPPRESS_TICKS` | 100 (5s) | climbing is off this long after a failed approach |
| `SpiderQueenEntity.TARGET_MEMORY_TICKS` | 300 (15s) | how long it hunts a target it can't see |

If it misses obvious routes (stairs a short walk away), raise
`SEARCH_MULTIPLIER` before anything else. If it gives up on routes that need a
longer walk, raise `DETOUR_RADIUS`. If a blocked spider wanders between spots
too eagerly, raise `STUCK_TICKS`.

## Jump

| constant | default | what it does |
|---|---|---|
| `JUMP_WINDUP_TICKS` | 10 (0.5s) | crouch before launch; matches `jump_start` length |
| `LAND_ANIM_LEAD_TICKS` | 8 (0.4s) | how early `jump_land` starts before touchdown |
| `LEAP_COOLDOWN_TICKS` | 70 (3.5s) | spacing between leaps |
| `DemonSpiderLeapGoal.MIN_LEAP_DISTANCE` | 4.0 | closer than this, spit instead |
| `DemonSpiderLeapGoal.MAX_LEAP_DISTANCE` | 12.0 | past this the arc goes floaty |
| `DemonSpiderLeapGoal.LEAP_UP_THRESHOLD` | 1.5 | height gap that justifies a jump alone |

Keep `JUMP_WINDUP_TICKS` at 10 unless you re-export `jump_start` at a different
length — the wind-up is meant to end exactly on the launch, and a mismatch
either truncates the crouch or leaves the spider frozen after it finishes.

`LAND_ANIM_LEAD_TICKS` is the "lands on its feet" knob. `jump_land` is 0.6s
(12 ticks), so at 8 the spider is reaching its legs out on contact and settles
~4 ticks after. Raise it if it still looks like it lands flat; lower it if the
legs extend so early they hang in the air.

The arc itself is solved in `SpiderQueenEntity.launchLeapAt` — flight time
scales with horizontal distance, vertical impulse is derived from it. The one
fudge factor is `dragCompensation`, which scales the horizontal impulse back up
to cover MC's ~0.98/tick drag. If long leaps consistently fall short, raise the
`0.5` exponent multiplier; if they overshoot, lower it.

## Climb rotation — **measure in game**

`SpiderQueenRenderer.CLIMB_PITCH_DEGREES` (90), `CLIMB_WALL_INSET` (0.3),
`CLIMB_LIFT` (0.5).

-90 was tested in game and hung the spider upside down; +90 is the corrected
sign (belly on the wall, head up), derived from the axis math but not yet seen
in game.

The two offsets are still eyeballed. Pitching about the feet leaves the belly
half a hitbox off the wall and the long abdomen dipping into the floor.
`CLIMB_WALL_INSET` pulls it onto the wall — raise it if there's a visible gap,
lower it if legs sink into the blocks. `CLIMB_LIFT` raises it up the wall —
raise it if the abdomen clips the floor as it starts to climb.

The rotation is render-only and never touches the entity's real `yRot`, so
nudging these cannot break pathfinding. The lean speed is the `0.15` in
`tickClimbLean` — that is how fast it tips onto the wall.

## Spit — placeholder

`SpiderQueenEntity.performSpit` is a stub that only plays a sound. The timing
around it is finished: `DemonSpiderSpitGoal` holds the spider still, drives the
`attack` animation and calls `performSpit` on `RELEASE_TICK` (7), the frame the
animation throws the head forward. Drop a real projectile spawn into that
method and the attack is done — the likely home is this mod's existing
`ProjectileRegistry` / `AbilityProjectileBase` stack rather than a new entity.

`MIN_RANGE` 3.0 / `MAX_RANGE` 16.0 / `COOLDOWN_TICKS` 50 shape when it fires.
Note `MAX_RANGE` is capped in practice by the 16.0 `FOLLOW_RANGE` attribute.

## Animation wiring

`Modeldemonic_spider` is a Blockbench export that MCreator regenerates on every
build, and it extends `EntityModel`, which can't run keyframe animations — so
don't edit it, any change is silently reverted. The renderer instead uses
`creatures/DemonSpiderModel`, which bakes the same layer and owns all the
animation logic. It sits outside `client/model/` because MCreator deletes
files in that folder that aren't in its model list.

If the spider stops animating, check `SpiderQueenRenderer` still constructs a
`DemonSpiderModel` — regenerating the Spider Queen *element* (not the model)
resets the renderer back to the Blockbench class.
