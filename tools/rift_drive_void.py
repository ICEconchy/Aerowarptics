#!/usr/bin/env python3
"""
Builds the two sheets a Rift Storm lays over the Rift Drive: patches of void, and the violet rim round them.

    python tools/rift_drive_void.py

The drive itself - its geometry, its clips and its five tier sheets - is the artist's, and nothing here
writes any of it. These two sheets have to land on the same texels the artist's metal does, so they are read
off the shipped model rather than drawn by hand: change the model and re-run this.

During a Rift Storm the drive is drawn a second and third time over itself, with these sheets, by
StormCorruptionLayer. Both sheets carry the same alpha, and that alpha is not a transparency. It is the order
in which the void takes each texel - a patch's middle first, its edge last - because the dissolve shader the
layer draws with (vanilla's rendertype_entity_alpha, the dragon's death) keeps a texel only while its alpha is
at least the vertex alpha. The layer lowers that threshold as the storm gathers, and the patches grow.

The patches are spheres in the model's own space rather than blots on the sheet, so one patch runs round the
corner of a post and along the rail beside it instead of stopping square at every face edge. Each texel is
taken back to the point of the face it paints, through the face's own UV rectangle, and asked how deep inside
a sphere it is. A texel the artist used on more than one face takes the deepest of them.

The core and the needle are left clean - the rift is not what is being eaten, and the needle is a reading a
pilot still needs - and so is any texel they share with the metal, since the sheet cannot tell the two uses
apart. DriveVoidTextureTest checks all of it against the shipped files.

No imaging library: a PNG is a handful of chunks around a zlib stream.
"""

import json
import math
import os
import random
import struct
import zlib

ASSETS = os.path.join("src", "main", "resources", "assets", "aerowarptics")
GEO = os.path.join(ASSETS, "geo", "rift_drive.geo.json")
VOID_SHEET = os.path.join(ASSETS, "textures", "block", "rift_drive_void.png")
RIM_SHEET = os.path.join(ASSETS, "textures", "block", "rift_drive_void_rim.png")

# The artist's names for the rift and the needle. RiftDriveRenderer and DriveVoidTextureTest use the same.
CLEAN = {"cube", "needle"}

VOID_SEED = 0x7E1D
VOID_PATCHES = 16
VOID_DEEP = (0x0B, 0x06, 0x14)
VOID_SHALLOW = (0x1C, 0x0C, 0x33)
VOID_STARS = ((0xFF, 0xFF, 0xFF), (0xD8, 0xC4, 0xFF), (0x8F, 0xE6, 0xF0))
RIM_INNER = (0x8A, 0x6B, 0xFF)
RIM_OUTER = (0xC0, 0x60, 0xFF)


def void_seeds():
    """Where the void breaks through: spheres scattered through the block, seeded so a re-run matches."""
    rng = random.Random(VOID_SEED)
    seeds = []
    for _ in range(VOID_PATCHES):
        centre = (rng.uniform(-8.0, 8.0), rng.uniform(0.0, 16.0), rng.uniform(-8.0, 8.0))
        seeds.append((centre, rng.uniform(2.5, 5.0)))
    return seeds


def turn(rotation, pivot, point):
    """A cube's own rotation about its pivot, x then y then z, as Blockbench applies it."""
    if not rotation:
        return point
    x, y, z = (point[i] - pivot[i] for i in range(3))
    rx, ry, rz = (math.radians(a) for a in rotation)
    if rx:
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    if ry:
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    if rz:
        x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return x + pivot[0], y + pivot[1], z + pivot[2]


def on_face(face, origin, size, a, b):
    """The point on a cube's face a share `a` across its texture and `b` down it, each 0..1."""
    ox, oy, oz = origin
    w, h, d = size
    top = oy + h - b * h
    if face == "north":
        return ox + w - a * w, top, oz
    if face == "south":
        return ox + a * w, top, oz + d
    if face == "east":
        return ox + w, top, oz + d - a * d
    if face == "west":
        return ox, top, oz + a * d
    if face == "up":
        return ox + a * w, oy + h, oz + b * d
    return ox + a * w, oy, oz + d - b * d    # down


def texels(rect_uv, rect_size):
    """Every texel whose centre lies inside a face's UV rectangle, with its share across and down the face."""
    u, v = rect_uv
    w, h = rect_size
    if abs(w) < 1e-6 or abs(h) < 1e-6:
        return
    u0, u1 = sorted((u, u + w))
    v0, v1 = sorted((v, v + h))
    for y in range(math.floor(v0), math.ceil(v1)):
        for x in range(math.floor(u0), math.ceil(u1)):
            if u0 <= x + 0.5 < u1 and v0 <= y + 0.5 < v1:
                yield x, y, (x + 0.5 - u) / w, (y + 0.5 - v) / h


def overlay(geometry, width, height):
    """The void sheet and its rim, alpha for alpha the same, and the share of the metal's texels taken."""
    seeds = void_seeds()
    depth = {}
    clean = set()
    for bone in geometry["bones"]:
        for cube in bone.get("cubes", []):
            origin, size = cube["origin"], cube["size"]
            pivot = cube.get("pivot", bone.get("pivot", [0, 0, 0]))
            for face, rect in cube["uv"].items():
                for x, y, a, b in texels(rect["uv"], rect["uv_size"]):
                    if not (0 <= x < width and 0 <= y < height):
                        continue
                    if bone["name"] in CLEAN:
                        clean.add((x, y))
                        continue
                    point = turn(cube.get("rotation"), pivot, on_face(face, origin, size, a, b))
                    deepest = 0.0
                    for centre, radius in seeds:
                        deepest = max(deepest, 1.0 - math.dist(point, centre) / radius)
                    depth[(x, y)] = max(depth.get((x, y), 0.0), deepest)

    void_px = [[(0, 0, 0, 0)] * width for _ in range(height)]
    rim_px = [[(0, 0, 0, 0)] * width for _ in range(height)]
    taken = 0
    metal = [texel for texel in depth if texel not in clean]
    for x, y in metal:
        d = depth[(x, y)]
        # Never a full 255: the layer's threshold at no storm at all is exactly one, and a texel that met it
        # would show a speck of void with no storm to put it there.
        alpha = round(254 * d)
        if alpha <= 0:
            continue
        taken += 1
        colour = blend(VOID_SHALLOW, VOID_DEEP, d)
        # A star wherever the void is deep enough to have any, and a hash says so: the same stars every run,
        # and never in a patch's first thin edge.
        if d > 0.3 and (x * 73856093 ^ y * 19349663) % 17 == 0:
            colour = VOID_STARS[(x * 31 + y * 17) % len(VOID_STARS)]
        void_px[y][x] = colour + (alpha,)
        rim_px[y][x] = blend(RIM_OUTER, RIM_INNER, d) + (alpha,)
    return void_px, rim_px, taken, len(metal)


def blend(first, second, amount):
    return tuple(int(first[i] + (second[i] - first[i]) * amount) for i in range(3))


def png(path, px):
    height, width = len(px), len(px[0])
    raw = bytearray()
    for row in px:
        raw.append(0)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    with open(path, "wb") as handle:
        handle.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                     + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def main():
    with open(GEO, encoding="utf-8") as handle:
        geometry = json.load(handle)["minecraft:geometry"][0]
    width = geometry["description"]["texture_width"]
    height = geometry["description"]["texture_height"]
    void_px, rim_px, taken, metal = overlay(geometry, width, height)
    png(VOID_SHEET, void_px)
    png(RIM_SHEET, rim_px)
    print(f"the storm's void over {taken} of the drive's {metal} metal texels ({taken / max(1, metal):.0%}),"
          f" on a {width}x{height} sheet:")
    print(f"  {VOID_SHEET}\n  {RIM_SHEET}")


if __name__ == "__main__":
    main()
