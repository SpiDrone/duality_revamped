"""Builds the Demon Stronghold templates for the Duality Underworld.

Palette is deliberately NOT Nether-derived: deepslate bricks/tiles, blackstone,
obsidian, coal, gilded blackstone. Deepslate appears raw ONLY as the outer
casing, where it needs to match natural rock. Every interior surface the player
can see is a built block.

Vertical profile, shared by all pieces (13 tall, placed at absolute y28):

    y0      casing floor            world 28
    y1      floor surface           world 29   (player stands on y2)
    y2-y10  interior, 9 tall        world 30-38
    y11     ceiling                 world 39
    y12     casing cap              world 40

The cavern band is world 30-37, so the rooms punch ~3 blocks up into the roof
rock. That is the "walls 3 blocks taller" — the complex is carved into the
ceiling, not just standing in the void.

Pieces:
    demon_hall               21x13x21  start, 4 connectors, 15x15 interior
    demon_corridor_prison    9x13x13   lined hallway -> prison
    demon_corridor_bed       9x13x13   lined hallway -> bed chamber
    demon_prison             19x13x19  13x13 interior, caged villagers
    demon_bedchamber         19x13x19  13x13 interior, lived-in quarters
    demon_entry              21x13x9   lined tunnel out to the cavern

Pure stdlib, no dependencies.
"""

import gzip, struct, pathlib

# ============================================================== NBT writer
TYPE_ID = {"byte": 1, "int": 3, "double": 6, "string": 8, "list": 9, "compound": 10}


def _str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _payload(tag):
    kind, val = tag
    if kind == "byte":
        return struct.pack(">b", val)
    if kind == "int":
        return struct.pack(">i", val)
    if kind == "double":
        return struct.pack(">d", val)
    if kind == "string":
        return _str(val)
    if kind == "list":
        item_kind, items = val
        if not items:
            return struct.pack(">bi", 0, 0)
        return struct.pack(">bi", TYPE_ID[item_kind], len(items)) + b"".join(
            _payload((item_kind, i)) for i in items)
    if kind == "compound":
        out = b""
        for name, child in val.items():
            out += struct.pack(">b", TYPE_ID[child[0]]) + _str(name) + _payload(child)
        return out + struct.pack(">b", 0)
    raise ValueError(kind)


def write_nbt(path, root):
    body = struct.pack(">b", 10) + _str("") + _payload(("compound", root))
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(path, "wb") as f:
        f.write(body)


# ============================================================== palette
DEEPSLATE = "minecraft:deepslate"           # casing only
D_BRICK = "minecraft:deepslate_bricks"
D_BRICK_C = "minecraft:cracked_deepslate_bricks"
D_TILE = "minecraft:deepslate_tiles"
D_TILE_C = "minecraft:cracked_deepslate_tiles"
D_POLISHED = "minecraft:polished_deepslate"
D_COBBLED = "minecraft:cobbled_deepslate"
PB_BRICK = "minecraft:polished_blackstone_bricks"
PB_BRICK_C = "minecraft:cracked_polished_blackstone_bricks"
PB_CHISELED = "minecraft:chiseled_polished_blackstone"
BLACKSTONE = "minecraft:blackstone"
GILDED = "minecraft:gilded_blackstone"
OBSIDIAN = "minecraft:obsidian"
CRYING = "minecraft:crying_obsidian"
COAL = "minecraft:coal_block"
AIR = "minecraft:air"
LAVA = "minecraft:lava"
COBWEB = "minecraft:cobweb"
BARS = "minecraft:iron_bars"
CHAIN = "minecraft:chain"
CANDLE = "minecraft:red_candle"
LANTERN = "minecraft:lantern"
BED = "minecraft:black_bed"
CHEST = "minecraft:chest"
BARREL = "minecraft:barrel"
D_STAIR = "minecraft:cobbled_deepslate_stairs"
PB_STAIR = "minecraft:polished_blackstone_brick_stairs"

CHAIN_Y = {"axis": "y", "waterlogged": "false"}
LAVA_SRC = {"level": "0"}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}

# blocks iron bars should NOT treat as a neighbour to connect to
NON_CONNECT = {AIR, LAVA, COBWEB, CHAIN, CANDLE, LANTERN, "minecraft:jigsaw"}


def candle(n=1, lit=True):
    return {"candles": str(n), "lit": "true" if lit else "false", "waterlogged": "false"}


def lantern(hanging=True):
    return {"hanging": "true" if hanging else "false", "waterlogged": "false"}


def stair(facing, half="bottom"):
    return {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"}


def h(*args):
    """Small deterministic hash for palette variation — reproducible across runs."""
    v = 2166136261
    for a in args:
        v = ((v ^ (a & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    return v


def pick(options, *coords):
    return options[h(*coords) % len(options)]


# ============================================================== piece
SY = 13
FLOOR_Y = 1
IN_LO, IN_HI = 2, 10
CEIL_Y = 11

HALL_EXIT = "duality:demon_hall_exit"
WING_IN = "duality:demon_wing_entrance"
LINK_OUT = "duality:demon_link_exit"
ROOM_IN = "duality:demon_room_entrance"
CAVERN_DOOR = "duality:demon_cavern_door"   # must match door_jigsaw in the structure JSON


class Piece:
    def __init__(self, name, sx, sz):
        self.name, self.sx, self.sy, self.sz = name, sx, SY, sz
        self.blocks = {}
        self.entities = []

    def put(self, x, y, z, name, props=None, be=None):
        if 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz:
            self.blocks[(x, y, z)] = (name, props, be)

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, name, props)

    def get(self, x, y, z):
        e = self.blocks.get((x, y, z))
        return e[0] if e else None

    def occupied(self, x, z):
        """Something stands on the floor at this column."""
        return self.get(x, IN_LO, z) not in (None, AIR, COBWEB)

    def solid_box(self, block=DEEPSLATE):
        self.fill(0, 0, 0, self.sx - 1, self.sy - 1, self.sz - 1, block)

    def jigsaw(self, x, y, z, orientation, name, target, pool):
        self.put(x, y, z, "minecraft:jigsaw", {"orientation": orientation}, {
            "id": ("string", "minecraft:jigsaw"),
            "name": ("string", name),
            "target": ("string", target),
            "pool": ("string", pool),
            "final_state": ("string", "minecraft:air"),
            "joint": ("string", "aligned"),
            "selection_priority": ("int", 0),
            "placement_priority": ("int", 0),
        })

    def villager(self, x, y, z):
        self.entities.append((x + 0.5, float(y), z + 0.5))

    # ---------------------------------------------------------- decoration
    def floor_crack(self, path, salt=0, lava_chance=2):
        """A glowing fissure: 1-deep groove, lava in the bottom of some
        segments, stair lips leaning in from either side. lava_chance is out
        of 5 — set 0 for a cold crack in a walkway."""
        # Never undercut anything standing on the floor (pillars, candles,
        # furniture, wall bases) — the crack breaks around it instead. So call
        # this AFTER everything floor-standing has been placed.
        cut = [(x, z) for (x, z) in path
               if self.get(x, FLOOR_Y, z) is not None and not self.occupied(x, z)]
        pset = set(cut)
        for (x, z) in cut:
            self.put(x, FLOOR_Y, z, AIR)
            hot = h(x, z, salt) % 5 < lava_chance
            self.put(x, 0, z, LAVA if hot else OBSIDIAN, LAVA_SRC if hot else None)
        for (x, z) in cut:
            for face, (dx, dz) in STEP.items():
                nx, nz = x + dx, z + dz
                if (nx, nz) in pset or self.occupied(nx, nz):
                    continue
                if self.get(nx, FLOOR_Y, nz) in (None, AIR):
                    continue
                self.put(nx, FLOOR_Y, nz, pick([D_STAIR, PB_STAIR], nx, nz, salt),
                         stair(OPPOSITE[face]))

    def hanging_chain(self, x, z, top, length, tip=None, tip_props=None):
        for i in range(length):
            self.put(x, top - i, z, CHAIN, CHAIN_Y)
        if tip:
            self.put(x, top - length, z, tip, tip_props)

    def cobwebs(self, cells):
        for (x, y, z) in cells:
            if self.get(x, y, z) in (None, AIR):
                self.put(x, y, z, COBWEB)

    def resolve_bars(self):
        for (x, y, z), (nm, props, be) in list(self.blocks.items()):
            if nm != BARS:
                continue
            p = {"waterlogged": "false"}
            for face, (dx, dz) in STEP.items():
                nb = self.get(x + dx, y, z + dz)
                p[face] = "true" if (nb is not None and nb not in NON_CONNECT) else "false"
            self.blocks[(x, y, z)] = (nm, p, be)

    # ---------------------------------------------------------- output
    def write(self, root_dir):
        self.resolve_bars()
        palette, index = [], {}

        def state_id(nm, props):
            key = (nm, tuple(sorted(props.items())) if props else None)
            if key not in index:
                entry = {"Name": ("string", nm)}
                if props:
                    entry["Properties"] = ("compound", {k: ("string", v) for k, v in props.items()})
                index[key] = len(palette)
                palette.append(entry)
            return index[key]

        block_tags = []
        for pos in sorted(self.blocks):
            nm, props, be = self.blocks[pos]
            entry = {"pos": ("list", ("int", list(pos))), "state": ("int", state_id(nm, props))}
            if be:
                b = dict(be)
                b["x"], b["y"], b["z"] = ("int", pos[0]), ("int", pos[1]), ("int", pos[2])
                entry["nbt"] = ("compound", b)
            block_tags.append(entry)

        ent_tags = [{
            "pos": ("list", ("double", [ex, ey, ez])),
            "blockPos": ("list", ("int", [int(ex), int(ey), int(ez)])),
            "nbt": ("compound", {"id": ("string", "minecraft:villager"),
                                 "PersistenceRequired": ("byte", 1)}),
        } for (ex, ey, ez) in self.entities]

        root = {
            "DataVersion": ("int", 3955),
            "size": ("list", ("int", [self.sx, self.sy, self.sz])),
            "palette": ("list", ("compound", palette)),
            "blocks": ("list", ("compound", block_tags)),
            "entities": ("list", ("compound", ent_tags)),
        }
        out = root_dir / f"{self.name}.nbt"
        write_nbt(out, root)
        return out, len(block_tags), len(palette), len(ent_tags)


# ============================================================== room shell
def carve_room(p, inset=3, wall_mix=(D_BRICK, D_BRICK, D_BRICK_C, D_TILE)):
    """Casing (2 deepslate) + built wall at `inset`-1 + hollow interior.
    Interior spans [inset, size-1-inset] on both axes."""
    p.solid_box(DEEPSLATE)
    lo, hix, hiz = inset - 1, p.sx - inset, p.sz - inset
    for y in range(FLOOR_Y, CEIL_Y + 1):
        for x in range(lo, hix + 1):
            for z in (lo, hiz):
                p.put(x, y, z, pick(wall_mix, x, y, z))
        for z in range(lo, hiz + 1):
            for x in (lo, hix):
                p.put(x, y, z, pick(wall_mix, x, y, z))
    p.fill(inset, IN_LO, inset, hix - 1, IN_HI, hiz - 1, AIR)
    for x in range(lo, hix + 1):
        for z in range(lo, hiz + 1):
            p.put(x, CEIL_Y, z, pick((D_BRICK, D_BRICK_C, D_TILE), x, z, 7))
            p.put(x, FLOOR_Y, z, pick((D_TILE, D_TILE_C, D_POLISHED, PB_BRICK), x, z, 3))


def doorway(p, face, centre, width=3, height=4):
    half = width // 2
    for a in range(centre - half, centre + half + 1):
        for y in range(IN_LO, IN_LO + height):
            if face == "north":
                for z in range(0, 3):
                    p.put(a, y, z, AIR)
            elif face == "south":
                for z in range(p.sz - 3, p.sz):
                    p.put(a, y, z, AIR)
            elif face == "west":
                for x in range(0, 3):
                    p.put(x, y, a, AIR)
            elif face == "east":
                for x in range(p.sx - 3, p.sx):
                    p.put(x, y, a, AIR)


def door_frame(p, face, centre, width=3, height=4):
    """Chiseled surround so doorways don't read as holes punched in a wall."""
    half = width // 2
    axis, c = {"north": ("z", 2), "south": ("z", p.sz - 3),
               "west": ("x", 2), "east": ("x", p.sx - 3)}[face]
    for a in range(centre - half - 1, centre + half + 2):
        for y in range(IN_LO - 1, IN_LO + height + 1):
            edge = a in (centre - half - 1, centre + half + 1) or y in (IN_LO - 1, IN_LO + height)
            if not edge:
                continue
            x, z = (a, c) if axis == "z" else (c, a)
            if p.get(x, y, z) not in (None, AIR):
                p.put(x, y, z, PB_CHISELED)


# ============================================================== hall
def build_hall():
    p = Piece("demon_hall", 21, 21)
    carve_room(p, inset=3)
    lo, hi = 3, 17

    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            if (x + z) % 7 == 0:
                p.put(x, FLOOR_Y, z, BLACKSTONE)
            if h(x, z, 11) % 41 == 0:
                p.put(x, FLOOR_Y, z, GILDED)

    for cx, cz in ((6, 6), (6, 14), (14, 6), (14, 14)):
        for y in range(IN_LO, IN_HI + 1):
            p.put(cx, y, cz, OBSIDIAN if y % 4 == 0 else pick((D_BRICK, D_BRICK_C), cx, y, cz))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p.put(cx + dx, IN_LO, cz + dz, PB_CHISELED)
            p.put(cx + dx, IN_LO + 1, cz + dz, CANDLE, candle(3))
        p.put(cx, IN_HI + 1, cz, CRYING)

    for cx, cz in ((10, 6), (10, 14), (6, 10), (14, 10)):
        p.hanging_chain(cx, cz, CEIL_Y - 1, 3, LANTERN, lantern(True))
    p.hanging_chain(10, 10, CEIL_Y - 1, 4, LANTERN, lantern(True))

    # wall sconces: chiseled ledge against the wall, candles on top. Kept clear
    # of the doorways (9-11 on every face) and the door frames (8, 12).
    for a in (5, 15):
        for (x, z) in ((a, 3), (a, 17), (3, a), (17, a)):
            p.put(x, IN_LO + 1, z, PB_CHISELED)
            p.put(x, IN_LO + 2, z, CANDLE, candle(3))

    p.cobwebs([(4, IN_HI, 4), (16, IN_HI, 4), (4, IN_HI, 16), (16, IN_HI, 16),
               (4, IN_HI - 1, 5), (16, IN_HI - 1, 15)])

    # two fissures crossing the floor — the middle is deliberately left open.
    # Cut last so they break around the pillar bases instead of undercutting them.
    p.floor_crack([(4 + i, 5 + (i * i // 9) % 3) for i in range(13)], salt=1)
    p.floor_crack([(6 + (i // 4), 16 - i) for i in range(11)], salt=2)

    for face in ("north", "south", "west", "east"):
        doorway(p, face, 10)
        door_frame(p, face, 10)

    p.jigsaw(10, IN_LO + 1, 0, "north_up", HALL_EXIT, WING_IN, "duality:demon_stronghold/corridor_prison")
    p.jigsaw(10, IN_LO + 1, 20, "south_up", HALL_EXIT, WING_IN, "duality:demon_stronghold/corridor_bed")
    p.jigsaw(0, IN_LO + 1, 10, "west_up", HALL_EXIT, WING_IN, "duality:demon_stronghold/entrance")
    p.jigsaw(20, IN_LO + 1, 10, "east_up", HALL_EXIT, WING_IN, "duality:demon_stronghold/entrance")
    return p


# ============================================================== corridor
def build_corridor(name, far_pool, length=13):
    """Runs along Z. Near (south) jigsaw meets the hall, far (north) leads on.
    A solid deepslate block with a LINED tunnel bored through it — no raw
    deepslate on any surface the player can see."""
    p = Piece(name, 9, length)
    p.solid_box(DEEPSLATE)
    lo, hi = 3, 5
    top = IN_LO + 3

    for z in range(0, length):
        for y in range(FLOOR_Y, top + 2):
            for x in range(2, 7):
                if x in (2, 6) or y in (FLOOR_Y, top + 1):
                    p.put(x, y, z, pick((PB_BRICK, PB_BRICK, PB_BRICK_C, BLACKSTONE), x, y, z))
        p.fill(lo, IN_LO, z, hi, top, z, AIR)
        p.put(4, FLOOR_Y, z, pick((PB_BRICK, D_TILE, D_TILE_C), 4, z, 5))

    for z in range(2, length - 1, 4):
        for y in range(IN_LO, top + 1):
            p.put(2, y, z, PB_CHISELED)
            p.put(6, y, z, PB_CHISELED)
        for x in range(lo, hi + 1):
            p.put(x, top + 1, z, PB_CHISELED)
        p.put(4, top, z, CHAIN, CHAIN_Y)
        p.put(4, top - 1, z, LANTERN, lantern(True))

    for z in range(1, length - 1, 3):
        p.put(lo if (z // 3) % 2 == 0 else hi, IN_LO, z, CANDLE, candle(1 + (z % 3)))

    # offset to x=3 so the walkway at x4-5 stays clear of the groove
    p.floor_crack([(3, z) for z in range(3, length - 3)], salt=9)
    p.cobwebs([(lo, top, 1), (hi, top, length - 2)])

    p.jigsaw(4, IN_LO + 1, length - 1, "south_up", WING_IN, HALL_EXIT, "minecraft:empty")
    p.jigsaw(4, IN_LO + 1, 0, "north_up", LINK_OUT, ROOM_IN, far_pool)
    return p


# ============================================================== prison
def build_prison():
    p = Piece("demon_prison", 19, 19)
    carve_room(p, inset=3)
    lo, hi = 3, 15
    c0, c1 = 6, 12

    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            if h(x, z, 21) % 29 == 0:
                p.put(x, FLOOR_Y, z, COAL)

    for x in range(c0, c1 + 1):
        for z in range(c0, c1 + 1):
            if x in (c0, c1) or z in (c0, c1):
                for y in range(IN_LO, IN_LO + 4):
                    p.put(x, y, z, BARS)
            p.put(x, IN_LO + 4, z, BARS)
            p.put(x, FLOOR_Y, z, pick((D_COBBLED, BLACKSTONE), x, z, 4))
    for cx in (c0, c1):
        for cz in (c0, c1):
            for y in range(IN_LO, IN_LO + 4):
                p.put(cx, y, cz, pick((D_BRICK, D_BRICK_C), cx, y, cz))
            p.put(cx, IN_LO + 4, cz, PB_CHISELED)
            p.hanging_chain(cx, cz, CEIL_Y - 1, CEIL_Y - IN_LO - 6)

    for vx, vz in ((8, 8), (10, 10), (9, 7), (7, 10)):
        p.villager(vx, IN_LO, vz)

    for z in range(lo + 1, hi):
        p.put(lo, FLOOR_Y, z, OBSIDIAN)
        p.put(lo + 1, FLOOR_Y, z, LAVA, LAVA_SRC)
        p.put(lo + 2, FLOOR_Y, z, OBSIDIAN)
        p.put(lo + 2, IN_LO, z, pick((PB_BRICK, PB_BRICK_C), lo, z, 6))
    for z in range(lo + 2, hi - 1, 4):
        p.put(lo + 2, IN_LO + 1, z, CANDLE, candle(2))

    for cx, cz in ((9, 5), (9, 13), (5, 9), (13, 9)):
        p.hanging_chain(cx, cz, CEIL_Y - 1, 2, LANTERN, lantern(True))

    p.cobwebs([(hi, IN_HI, lo), (hi, IN_HI, hi), (hi - 1, IN_HI, lo + 1),
               (lo, IN_HI, hi), (c0 - 1, IN_LO + 5, c0 - 1)])

    doorway(p, "south", 9)
    door_frame(p, "south", 9)
    p.jigsaw(9, IN_LO + 1, 18, "south_up", ROOM_IN, LINK_OUT, "minecraft:empty")
    return p


# ============================================================== bed chamber
def build_bedchamber():
    p = Piece("demon_bedchamber", 19, 19)
    carve_room(p, inset=3, wall_mix=(D_BRICK, D_BRICK_C, D_TILE, D_TILE_C, BLACKSTONE))
    lo, hi = 3, 15

    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            r = h(x, z, 33) % 23
            if r == 0:
                p.put(x, FLOOR_Y, z, D_COBBLED)
            elif r == 1:
                p.put(x, FLOOR_Y, z, COAL)

    bunks = []
    for z in (5, 9, 13):
        bunks.append((lo, z, "east"))
        bunks.append((hi, z, "west"))
    for (bx, bz, inward) in bunks:
        dx, dz = STEP[inward]
        # bed "facing" points foot -> head, so the pillow sits against the wall
        # under the headboard and the foot points into the room
        p.put(bx, IN_LO, bz, BED, {"facing": OPPOSITE[inward], "occupied": "false", "part": "head"})
        p.put(bx + dx, IN_LO, bz + dz, BED, {"facing": OPPOSITE[inward], "occupied": "false", "part": "foot"})
        p.put(bx - dx, IN_LO, bz, PB_CHISELED)
        p.put(bx - dx, IN_LO + 1, bz, CANDLE, candle(1 + (bz % 3)))
        # the candle niche is cut into the wall line; back it with a built block
        # so the raw casing doesn't show through
        p.put(bx - dx * 2, IN_LO + 1, bz, PB_BRICK)
        if h(bx, bz, 5) % 3 == 0:
            p.put(bx, IN_LO + 2, bz, COBWEB)
        p.put(bx + dx * 2, IN_LO, bz + 1, CHEST,
              {"facing": OPPOSITE[inward], "type": "single", "waterlogged": "false"})

    # central hearth: sunken lava basin ringed in obsidian
    p.fill(8, FLOOR_Y, 8, 10, FLOOR_Y, 10, LAVA, LAVA_SRC)
    for x in range(7, 12):
        for z in range(7, 12):
            if x in (7, 11) or z in (7, 11):
                p.put(x, FLOOR_Y, z, OBSIDIAN)
                if (x + z) % 2 == 0:
                    p.put(x, IN_LO, z, pick((D_STAIR, PB_STAIR), x, z, 8),
                          stair("east" if x < 9 else "west"))
    p.hanging_chain(9, 9, CEIL_Y - 1, 3, LANTERN, lantern(True))

    for (sx, sz) in ((4, 4), (14, 4), (4, 14), (14, 14)):
        p.put(sx, IN_LO, sz, BARREL, {"facing": "up", "open": "false"})
        if h(sx, sz, 2) % 2:
            p.put(sx, IN_LO + 1, sz, BARREL, {"facing": "up", "open": "false"})
        # against the end wall — sx+1 collided with the z=13 bunk's foot chest
        north = sz < 9
        p.put(sx, IN_LO, sz - 1 if north else sz + 1, CHEST,
              {"facing": "south" if north else "north", "type": "single", "waterlogged": "false"})
        p.put(sx, IN_LO + 2, sz + 1, COBWEB)

    p.floor_crack([(5 + i, 13 + (i % 2)) for i in range(9)], salt=4)
    p.cobwebs([(lo, IN_HI, lo), (hi, IN_HI, hi), (lo + 1, IN_HI, lo),
               (hi, IN_HI - 1, hi - 1), (9, IN_HI, 4)])

    doorway(p, "north", 9)
    door_frame(p, "north", 9)
    p.jigsaw(9, IN_LO + 1, 0, "north_up", ROOM_IN, LINK_OUT, "minecraft:empty")
    return p


# ============================================================== entry tunnel
def build_entry():
    """Runs along X. West jigsaw meets the hall, east end opens to the cavern."""
    p = Piece("demon_entry", 21, 9)
    p.solid_box(DEEPSLATE)
    lo, hi = 3, 5
    top = IN_LO + 3

    for x in range(0, 21):
        for y in range(FLOOR_Y, top + 2):
            for z in range(2, 7):
                if z in (2, 6) or y in (FLOOR_Y, top + 1):
                    p.put(x, y, z, pick((PB_BRICK, PB_BRICK_C, BLACKSTONE, D_BRICK), x, y, z))
        p.fill(x, IN_LO, lo, x, top, hi, AIR)
        p.put(x, FLOOR_Y, 4, pick((PB_BRICK, D_TILE, D_TILE_C), x, 4, 5))

    for x in range(2, 20, 4):
        for y in range(IN_LO, top + 1):
            p.put(x, y, 2, PB_CHISELED)
            p.put(x, y, 6, PB_CHISELED)
        p.put(x, top, 4, CHAIN, CHAIN_Y)
        p.put(x, top - 1, 4, LANTERN, lantern(True))
    for x in range(1, 20, 3):
        p.put(x, IN_LO, lo if (x // 3) % 2 == 0 else hi, CANDLE, candle(1 + x % 3))

    p.floor_crack([(x, 3) for x in range(4, 17)], salt=6)

    # the mouth degrades into bare rock so it doesn't end on a clean seam
    for x in range(18, 21):
        for y in range(FLOOR_Y, top + 2):
            for z in range(2, 7):
                if p.get(x, y, z) not in (None, AIR) and h(x, y, z, 13) % 3 == 0:
                    p.put(x, y, z, D_COBBLED)

    p.jigsaw(0, IN_LO + 1, 4, "west_up", WING_IN, HALL_EXIT, "minecraft:empty")
    # Cavern door marker at the mouth, facing out. Not a connector (empty pool,
    # nothing targets it) — CavernJigsawStructure looks for it by name and only
    # lets the stronghold generate if at least one of these opens onto cavern.
    p.jigsaw(20, IN_LO, 4, "east_up", CAVERN_DOOR, "minecraft:empty", "minecraft:empty")
    return p


# ============================================================== run
# resolved from this file, not the cwd — the old "underworld/src/..." relative
# path never pointed at this workspace
root = pathlib.Path(__file__).resolve().parent / "src/main/resources/data/duality/structure"
for old in root.glob("demon_*.nbt"):
    old.unlink()

builders = [
    build_hall,
    lambda: build_corridor("demon_corridor_prison", "duality:demon_stronghold/prison"),
    lambda: build_corridor("demon_corridor_bed", "duality:demon_stronghold/bedchamber"),
    build_prison,
    build_bedchamber,
    build_entry,
]
for b in builders:
    piece = b()
    out, nb, np_, ne = piece.write(root)
    print(f"{out.name:26s} {out.stat().st_size:6d} B  {nb:5d} blocks  {np_:3d} states  {ne} entities")
