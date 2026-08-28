#!/usr/bin/env python3
"""
Builds the Rift Modulator's GeckoLib model, its animation and its texture sheet.

    python tools/rift_modulator_model.py

Same reasoning as `tools/chute_model.py`, which this is built the same way as: a GeckoLib cube's UV
rectangle is derived from its *size*, so geometry and texture are the same decision written twice, and
writing them twice is how they drift. Cubes are declared once here, UVs are packed by a shelf packer
that cannot produce an overlap, and the painter walks the same packing to fill the pixels.

`ResourceIntegrityTest.geoUvBoxesFitAndDoNotOverlap` enforces exactly the invariant this packer
provides, so a hand-edited geo file is the thing that fails and this generator is the thing that does
not.
"""

import json
import os
import struct
import zlib

SHEET = 128

ASSETS = os.path.join("src", "main", "resources", "assets", "aerowarptics")
GEO = os.path.join(ASSETS, "geo", "rift_modulator.geo.json")
ANIM = os.path.join(ASSETS, "animations", "rift_modulator.animation.json")
TEXTURE = os.path.join(ASSETS, "textures", "block", "rift_modulator.png")
# No textures/item file: this block is drawn entirely by GeoBlockItemRenderer, straight off the
# block's own geometry and skin, so a flat icon here would be dead weight nobody looks at -
# ResourceIntegrityTest.everyGeoRenderedItemIsDrawnRatherThanDrawnUp holds that.

# The mod's own purple family, lifted from the Gate and the Chute so a Modulator reads as the same
# machine shop rather than a new material palette turning up on one block.
DARK = (0x24, 0x1C, 0x30, 255)
BASE = (0x3A, 0x2E, 0x4A, 255)
LIGHT = (0x58, 0x44, 0x70, 255)
BRASS = (0xB0, 0x8D, 0x57, 255)
BRASS_DARK = (0x7A, 0x60, 0x38, 255)
# Near-white rather than the rift's own purple: the lens is the one bone the renderer re-tints to
# whatever colour the player has chosen, and a texture already coloured would fight that multiply
# instead of letting it through clean. See RiftModulatorRenderer.
LENS = (0xDC, 0xDC, 0xE4, 255)
LENS_HOT = (0xF2, 0xF0, 0xFF, 255)

# --------------------------------------------------------------------------- cubes
#
# Same conventions as the Rift Drive and the Rift Chute: cubes centred on X and Z (-8..8), standing on
# the floor at y=0. The block faces north by default (-Z) - GeckoLib's block renderer reads FACING and
# turns the whole model, so nothing here turns and the renderer must not turn it a second time except
# to lift the pivot for the two vertical facings, exactly as RiftDriveRenderer already does.

BONES = {
    "base": [0, 0, 0],
    "column": [0, 3, 0],
    "lens": [0, 8, 0],
}

CUBES = [
    # bone,     origin,         size,       top,      side,       bottom,     ribbed
    ("base",    (-6, 0, -6),    (12, 2, 12), LIGHT,    BASE,       DARK,       False),
    ("base",    (-5, 2, -5),    (10, 1, 10), BASE,     LIGHT,      DARK,       False),
    ("column",  (-2, 3, -2),    (4, 5, 4),   BRASS,    BRASS,      BRASS_DARK, True),
    # A small plate on the front face - the one thing on this model that says which way it is
    # looking, for a block whose whole model is otherwise symmetric about its own vertical axis.
    # It sits flush against the pillar's own north face (z=-2) rather than inside its volume.
    ("column",  (-1, 4, -3),    (2, 2, 1),   BRASS,    BRASS_DARK, BRASS_DARK, False),
    # Starts exactly where the pillar ends, not part way down it - a collar sitting inside the
    # pillar it is meant to be crowning is the overlap this generator's own check exists to catch.
    ("column",  (-3, 8, -3),    (6, 1, 6),   BRASS_DARK, BRASS,    BRASS_DARK, False),
    ("lens",    (-3, 9, -3),    (6, 5, 6),   LENS_HOT, LENS,       DARK,       False),
    ("lens",    (-2, 14, -2),   (4, 1, 4),   LIGHT,    LENS,       DARK,       False),
]


def check_no_overlap():
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
    for bone, origin, size, *_ in CUBES:
        for axis, low, extent in zip("xyz", origin, size):
            lo, hi = (0, 16) if axis == "y" else (-8, 8)
            if low < lo or low + extent > hi:
                raise SystemExit(
                    f"{bone}: {axis} runs {low}..{low + extent}, outside {lo}..{hi}")


def pack(cubes):
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
                "identifier": "geometry.rift_modulator",
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
    """One clip: the lens breathes, the way the chute's cap does. Nothing else moves - the colour and
    the sparks around it are the renderer's and WarpEffects' job, not this model's."""
    return {
        "format_version": "1.8.0",
        "animations": {
            "animation.rift_modulator.idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "lens": {"scale": {
                        "0.0": [1, 1, 1],
                        "2.0": [1.05, 1.05, 1.05],
                        "4.0": [1, 1, 1],
                    }},
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
    fill(px, x0, y0, x1, y0 + 1, shade(colour, 1.25))
    fill(px, x0, y0, x0 + 1, y1, shade(colour, 1.15))
    fill(px, x0, y1 - 1, x1, y1, shade(colour, 0.65))
    fill(px, x1 - 1, y0, x1, y1, shade(colour, 0.75))


def paint(px, placements):
    for (_bone, _origin, size, top, side, bottom, ribbed), (u, v) in zip(CUBES, placements):
        sx, sy, sz = size
        fill(px, u + sz, v, u + sz + sx, v + sz, top)
        bevel(px, u + sz, v, u + sz + sx, v + sz, top)
        fill(px, u + sz + sx, v, u + sz + sx + sx, v + sz, bottom)
        bevel(px, u + sz + sx, v, u + sz + sx + sx, v + sz, bottom)
        widths = [sz, sx, sz, sx]
        x = u
        for index, w in enumerate(widths):
            tone = shade(side, 1.0 if index % 2 else 0.86)
            fill(px, x, v + sz, x + w, v + sz + sy, tone)
            if ribbed:
                for row in range(v + sz + 2, v + sz + sy - 1, 3):
                    fill(px, x + 1, row, x + w - 1, row + 1, shade(tone, 0.72))
            bevel(px, x, v + sz, x + w, v + sz + sy, tone)
            x += w


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
    used = max(v + size[2] + size[1] for (_b, _o, size, *_), (_u, v) in zip(CUBES, placements))
    print(f"rift modulator: {len(CUBES)} cubes packed into {SHEET}x{used} of a {SHEET}x{SHEET} sheet")
    print(f"  {GEO}\n  {ANIM}\n  {TEXTURE}")


if __name__ == "__main__":
    main()
