#!/usr/bin/env python3
"""
Builds the Rift Chute's GeckoLib model, its animation and its texture sheet.

    python tools/chute_model.py

Three files come out of one place on purpose. A GeckoLib cube's UV rectangle is derived from its
*size* - a cube s(x,y,z) occupies 2*(sz+sx) by (sz+sy) pixels - so geometry and texture are the same
decision written twice, and writing them twice is how they drift. Here the cubes are declared once,
the UVs are packed by a shelf packer that cannot produce an overlap, and the painter walks the same
packing to fill the pixels.

`ResourceIntegrityTest.geoUvBoxesFitAndDoNotOverlap` enforces exactly the invariant this packer
provides, so a hand-edited geo file is the thing that fails and this generator is the thing that
does not.

No imaging library: a PNG is a handful of chunks around a zlib stream, and the whole sheet is drawn
with flat fills and one-pixel bevels.
"""

import json
import os
import struct
import zlib

SHEET = 256

ASSETS = os.path.join("src", "main", "resources", "assets", "aerowarptics")
GEO = os.path.join(ASSETS, "geo", "rift_chute.geo.json")
ANIM = os.path.join(ASSETS, "animations", "rift_chute.animation.json")
TEXTURE = os.path.join(ASSETS, "textures", "block", "rift_chute.png")
ITEM = os.path.join(ASSETS, "textures", "item", "rift_chute.png")

# Lifted from the Rift Gate so the chute reads as the same machine shop, exactly as the probe's
# textures were. Every colour in this mod's purple family comes from those two files.
DARK = (0x24, 0x1C, 0x30, 255)
BASE = (0x3A, 0x2E, 0x4A, 255)
LIGHT = (0x58, 0x44, 0x70, 255)
BRASS = (0xB0, 0x8D, 0x57, 255)
BRASS_DARK = (0x7A, 0x60, 0x38, 255)
RIFT = (0x9B, 0x6B, 0xFF, 255)
RIFT_HOT = (0xC7, 0x9B, 0xFF, 255)

# --------------------------------------------------------------------------- cubes
#
# Modelled on the Rift Drive's geometry, whose conventions are the ones that actually work in game:
#
#   * Cubes are centred on X and Z (-8..8) and sit on the floor at y=0, rising to y=16. A block model
#     that is not centred renders offset from the block it belongs to.
#   * No two cubes share a volume. This one is a tidiness rule for this model rather than a law -
#     the Rift Drive nests 59 pairs of cubes and looks perfect, because nested volumes are fine and
#     only *coincident coplanar faces* actually z-fight. Kept because this model needs no nesting.
#   * Every bone's pivot sits at that bone's own centre of rotation, never at [0, 0, 0]. A bone with
#     a pivot at the origin swings around the corner of the block instead of around itself, which
#     throws it clean out of its own square.
#
# The block faces north by default. GeckoLib's own GeoBlockRenderer reads HORIZONTAL_FACING and
# rotates the whole model, so nothing here turns and the renderer must not turn it a second time.

BONES = {
    "base":  [0, 0, 0],
    "frame": [0, 3, 0],
    "cap":   [0, 12, 0],
}

# An open cage, not a box.
#
# The chute is open on all four sides, so what is left is the structure holding the opening apart:
# a stepped plinth, four brass uprights, a floor and a lid. There is no rift in this model at all -
# the aperture inside is the same one a Rift Gate tears, drawn by RiftEffectManager as a billboard
# that turns to face the viewer, which is the only way one small pane reads from every side of a
# block you can see straight through.
#
# No facing either. A block open on all four sides has no front, so there is no FACING state, no
# rotation to get wrong, and one variant in the blockstate.
CUBES = [
    # bone,   origin,        size,        top,   side,  bottom,     ribbed
    ("base",  (-7, 0, -7),   (14, 1, 14), LIGHT, BASE,  DARK,       False),
    ("base",  (-6, 1, -6),   (12, 2, 12), BASE,  LIGHT, DARK,       False),
    ("base",  (-5, 3, -5),   (10, 1, 10), DARK,  BASE,  DARK,       False),

    # Four brass uprights at the corners. Everything between them is open.
    ("frame", (-7, 4, -7),   (2, 8, 2),   BRASS, BRASS, BRASS_DARK, True),
    ("frame", (5, 4, -7),    (2, 8, 2),   BRASS, BRASS, BRASS_DARK, True),
    ("frame", (-7, 4, 5),    (2, 8, 2),   BRASS, BRASS, BRASS_DARK, True),
    ("frame", (5, 4, 5),     (2, 8, 2),   BRASS, BRASS, BRASS_DARK, True),

    ("cap",   (-5, 12, -5),  (10, 1, 10), BASE,  BASE,  DARK,       False),
    ("cap",   (-7, 13, -7),  (14, 2, 14), LIGHT, BASE,  DARK,       False),
    ("cap",   (-5, 15, -5),  (10, 1, 10), LIGHT, LIGHT, DARK,       False),
]


def check_no_overlap():
    """Keeps this model free of nested cubes.

    Not a general rule - the Rift Drive nests plenty and renders correctly - but this shape has no
    reason to nest anything, and a cube that ends up inside another is usually a typo in an origin
    rather than a decision. Touching faces are fine; only shared volume trips this.
    """
    boxes = []
    for bone, origin, size, *_ in CUBES:
        x0, y0, z0 = origin
        boxes.append((bone, x0, y0, z0, x0 + size[0], y0 + size[1], z0 + size[2]))
    for i in range(len(boxes)):
        for j in range(i + 1, len(boxes)):
            a, b = boxes[i], boxes[j]
            if (a[1] < b[4] and b[1] < a[4]
                    and a[2] < b[5] and b[2] < a[5]
                    and a[3] < b[6] and b[3] < a[6]):
                raise SystemExit(f"cubes overlap: {a[0]} and {b[0]} share a volume")


def check_inside_the_block():
    """Nothing may leave the block it belongs to.

    This is the check that actually catches typos. A cube whose origin is out by a sign renders
    hanging in the neighbouring block, which looks like a placement bug rather than a model one -
    exactly the kind of thing that gets debugged in the wrong file.
    """
    for bone, origin, size, *_ in CUBES:
        for axis, low, extent in zip("xyz", origin, size):
            lo, hi = (0, 16) if axis == "y" else (-8, 8)
            if low < lo or low + extent > hi:
                raise SystemExit(
                    f"{bone}: {axis} runs {low}..{low + extent}, outside {lo}..{hi}")


def pack(cubes):
    """Shelf-packs every cube's UV rectangle, left to right and top to bottom.

    Returns a list of (u, v) in the same order as the input. A shelf packer is not the tightest
    arrangement possible, but it is trivially correct: nothing on a shelf overlaps anything else on
    that shelf, and no shelf overlaps the one above it.
    """
    placements = []
    x = y = shelf_height = 0
    for _bone, _origin, size, *_ in cubes:
        sx, sy, sz = size
        width = 2 * (sz + sx)
        height = sz + sy
        if x + width > SHEET:
            x = 0
            y += shelf_height
            shelf_height = 0
        if y + height > SHEET:
            raise SystemExit(f"the cubes do not fit a {SHEET}x{SHEET} sheet")
        placements.append((x, y))
        x += width
        shelf_height = max(shelf_height, height)
    return placements


def geo(placements):
    bones = {}
    order = []
    for (bone, origin, size, *_), (u, v) in zip(CUBES, placements):
        if bone not in bones:
            bones[bone] = []
            order.append(bone)
        bones[bone].append({"origin": list(origin), "size": list(size), "uv": [u, v]})
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.rift_chute",
                "texture_width": SHEET,
                "texture_height": SHEET,
                "visible_bounds_width": 2,
                "visible_bounds_height": 2,
                "visible_bounds_offset": [0, 0.5, 0],
            },
            "bones": [{"name": name, "pivot": BONES[name], "cubes": bones[name]} for name in order],
        }],
    }


def animation():
    """One clip, and it barely moves.

    The rift inside is drawn by RiftEffectManager, not by this model, so there is nothing here that
    should be animated - the housing is a bracket holding a hole in space apart. A breathing bracket
    would be a distraction from the one thing on this block worth looking at.
    """
    return {
        "format_version": "1.8.0",
        "animations": {
            "animation.rift_chute.idle": {
                "loop": True,
                "animation_length": 6.0,
                "bones": {
                    "cap": {"position": {"0.0": [0, 0, 0], "3.0": [0, 0.15, 0], "6.0": [0, 0, 0]}},
                },
            },
        },
    }


# ------------------------------------------------------------------------- painting

def blank():
    return [[(0, 0, 0, 0)] * SHEET for _ in range(SHEET)]


def fill(px, x0, y0, x1, y1, colour):
    for y in range(max(0, y0), min(SHEET, y1)):
        for x in range(max(0, x0), min(SHEET, x1)):
            px[y][x] = colour


def shade(colour, factor):
    r, g, b, a = colour
    return (min(255, int(r * factor)), min(255, int(g * factor)), min(255, int(b * factor)), a)


def bevel(px, x0, y0, x1, y1, colour):
    """One-pixel border: lifted along the top and left, darkened along the bottom and right.

    An edge you can see is most of what separates a modelled object from a box, and it is the one
    piece of shading that has to be drawn rather than derived from the face.
    """
    fill(px, x0, y0, x1, y0 + 1, shade(colour, 1.25))
    fill(px, x0, y0, x0 + 1, y1, shade(colour, 1.15))
    fill(px, x0, y1 - 1, x1, y1, shade(colour, 0.65))
    fill(px, x1 - 1, y0, x1, y1, shade(colour, 0.75))


def paint(px, placements):
    """Fills each cube's box-UV region, face by face.

    Box UV lays a cube out as a known arrangement: a strip of top and bottom across the top of the
    region, then a strip of the four sides beneath it. Splitting the rect back into those faces is
    what lets each be lit differently - top bright, sides mid, bottom dark - and that single change
    is what makes a stack of boxes read as a solid object rather than a flat sticker.
    """
    for (_bone, _origin, size, top, side, bottom, ribbed), (u, v) in zip(CUBES, placements):
        sx, sy, sz = size
        # Top strip: top face at u+sz, bottom face at u+sz+sx, both sz deep.
        fill(px, u + sz, v, u + sz + sx, v + sz, top)
        bevel(px, u + sz, v, u + sz + sx, v + sz, top)
        fill(px, u + sz + sx, v, u + sz + sx + sx, v + sz, bottom)
        bevel(px, u + sz + sx, v, u + sz + sx + sx, v + sz, bottom)
        # Side strip: right, front, left, back - each sy tall.
        widths = [sz, sx, sz, sx]
        x = u
        for index, w in enumerate(widths):
            # Front and back catch a little more light than the two ends.
            tone = shade(side, 1.0 if index % 2 else 0.86)
            fill(px, x, v + sz, x + w, v + sz + sy, tone)
            if ribbed:
                # Horizontal banding every third pixel. A flat wall of one colour is what made the
                # first model read as a lectern; a surface with a rhythm on it reads as machined.
                for row in range(v + sz + 2, v + sz + sy - 1, 3):
                    fill(px, x + 1, row, x + w - 1, row + 1, shade(tone, 0.72))
            bevel(px, x, v + sz, x + w, v + sz + sy, tone)
            x += w


def item_icon():
    """A sixteen-square icon: the collar seen face on, with the rift lit in the middle of it.

    Drawn rather than rendered from the model, because an inventory icon has sixteen pixels to say
    what a block is and a shrunken three-quarter view of a purple box says nothing at all.
    """
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]

    def rect(x0, y0, x1, y1, colour):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[y][x] = colour

    rect(1, 1, 15, 15, BASE)
    rect(1, 1, 15, 2, LIGHT)
    rect(1, 14, 15, 15, DARK)
    rect(1, 1, 2, 15, shade(BASE, 1.12))
    rect(14, 1, 15, 15, shade(BASE, 0.72))
    rect(3, 3, 13, 13, DARK)
    rect(4, 4, 12, 12, shade(RIFT, 0.55))
    rect(5, 5, 11, 11, RIFT)
    rect(6, 6, 10, 10, RIFT_HOT)
    rect(7, 7, 9, 9, (255, 255, 255, 255))
    # Brass at the corners, the one thing that says "fitting" rather than "block of purple".
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[y][x] = BRASS
    return px


def png(path, px, size=SHEET):
    raw = bytearray()
    for row in px:
        raw.append(0)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(blob)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")


def main():
    check_no_overlap()
    check_inside_the_block()
    placements = pack(CUBES)
    write_json(GEO, geo(placements))
    write_json(ANIM, animation())
    px = blank()
    paint(px, placements)
    png(TEXTURE, px)
    png(ITEM, item_icon(), 16)
    used = max(v + size[2] + size[1] for (_b, _o, size, *_), (_u, v) in zip(CUBES, placements))
    print(f"rift chute: {len(CUBES)} cubes packed into {SHEET}x{used} of a {SHEET}x{SHEET} sheet")
    print(f"  {GEO}\n  {ANIM}\n  {TEXTURE}\n  {ITEM}")


if __name__ == "__main__":
    main()
