#!/usr/bin/env python3
"""
Draws the Rift Probe's block textures.

The rest of this mod's art comes out of a generator, and these are written the same way rather than
painted, so the palette is shared by construction instead of by eye. Every colour here is taken from
the Rift Gate's textures, which is why a probe looks like it belongs to the same machine shop.

    python tools/probe_textures.py

Sixteen by sixteen, RGBA, no dependencies - a PNG is a handful of chunks around a zlib stream, and
pulling in an imaging library to write three small ones would be the larger cost.
"""

import os
import struct
import zlib

SIZE = 16
OUT = os.path.join("src", "main", "resources", "assets", "aerowarptics", "textures", "block")

# Lifted from rift_gate_frame.png and rift_gate_end.png so the family matches.
DARK = (0x24, 0x1C, 0x30, 255)
BASE = (0x3A, 0x2E, 0x4A, 255)
LIGHT = (0x58, 0x44, 0x70, 255)
RIFT = (0x9B, 0x6B, 0xFF, 255)
GLOW = (0xC7, 0x9B, 0xFF, 255)
BRASS = (0xB0, 0x8D, 0x57, 255)
BRASS_DARK = (0x7A, 0x60, 0x38, 255)


def blank(colour=BASE):
    return [[colour for _ in range(SIZE)] for _ in range(SIZE)]


def write_png(path, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter: none. These are tiny; a filter would save nothing worth the code.
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


def bevel(pixels):
    """A lit top edge and a shadowed bottom one, which is most of what makes a square read as a face."""
    for x in range(SIZE):
        pixels[0][x] = LIGHT
        pixels[SIZE - 1][x] = DARK
    for y in range(SIZE):
        pixels[y][0] = LIGHT if y < SIZE // 2 else DARK
        pixels[y][SIZE - 1] = DARK
    return pixels


def side():
    """A banded housing with a lens slit in it, so the block has an obvious front-and-centre."""
    pixels = bevel(blank())

    # Brass bands top and bottom, the machine part of it.
    for x in range(1, SIZE - 1):
        pixels[2][x] = BRASS_DARK
        pixels[3][x] = BRASS
        pixels[SIZE - 4][x] = BRASS
        pixels[SIZE - 3][x] = BRASS_DARK

    # The slit: a dark recess with a rift-lit core, brightest in the middle.
    for x in range(4, SIZE - 4):
        pixels[7][x] = DARK
        pixels[10][x] = DARK
        near = 1.0 - abs(x - (SIZE - 1) / 2.0) / ((SIZE - 8) / 2.0)
        pixels[8][x] = GLOW if near > 0.55 else RIFT
        pixels[9][x] = RIFT if near > 0.3 else DARK
    pixels[8][3] = DARK
    pixels[9][3] = DARK
    pixels[8][SIZE - 4] = DARK
    pixels[9][SIZE - 4] = DARK

    # Bolts at the corners of the housing.
    for y, x in ((5, 2), (5, SIZE - 3), (SIZE - 6, 2), (SIZE - 6, SIZE - 3)):
        pixels[y][x] = BRASS
    return pixels


def top():
    """A dish: concentric brass rings around a rift-lit eye, because it is a thing that looks out."""
    pixels = bevel(blank())
    centre = (SIZE - 1) / 2.0
    for y in range(1, SIZE - 1):
        for x in range(1, SIZE - 1):
            distance = ((x - centre) ** 2 + (y - centre) ** 2) ** 0.5
            if distance < 1.6:
                pixels[y][x] = GLOW
            elif distance < 2.8:
                pixels[y][x] = RIFT
            elif distance < 3.6:
                pixels[y][x] = DARK
            elif distance < 5.0:
                pixels[y][x] = BRASS
            elif distance < 5.8:
                pixels[y][x] = BRASS_DARK
            elif distance < 6.8:
                pixels[y][x] = LIGHT
    # Four fixing points on the rim, on the diagonals, so the dish reads as mounted.
    for y, x in ((2, 2), (2, SIZE - 3), (SIZE - 3, 2), (SIZE - 3, SIZE - 3)):
        pixels[y][x] = BRASS
    return pixels


def bottom():
    """Plain plating. Nobody looks at it, but a missing texture is louder than a dull one."""
    pixels = bevel(blank(DARK))
    for y in range(3, SIZE - 3):
        for x in range(3, SIZE - 3):
            pixels[y][x] = BASE if (x + y) % 4 else LIGHT
    return pixels


def main():
    for name, build in (("rift_probe_side", side), ("rift_probe_top", top),
                        ("rift_probe_bottom", bottom)):
        path = os.path.join(OUT, name + ".png")
        write_png(path, build())
        print(f"  {name}.png  {SIZE}x{SIZE}  {os.path.getsize(path)} bytes")


if __name__ == "__main__":
    main()
