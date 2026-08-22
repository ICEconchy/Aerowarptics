#!/usr/bin/env python3
"""
Draws the Navigator's Handbook's inventory icon.

    python tools/handbook_texture.py

Written rather than painted, for the same reason as the Rift Probe's textures beside it: the palette
is then shared by construction rather than by eye. Every colour here is taken from AWBookStyle, so
the item in your hand and the book on your screen are the same object - a leather board, brass bands
on the spine, a block of paper, and the rift stamped on the cover.

Sixteen by sixteen, RGBA, no dependencies.
"""

import os
import struct
import zlib

SIZE = 16
OUT = os.path.join("src", "main", "resources", "assets", "aerowarptics", "textures", "item")

NONE = (0, 0, 0, 0)
# AWBookStyle.LEATHER_DARK, LEATHER, LEATHER_LIGHT.
DARK = (0x20, 0x16, 0x0F, 255)
LEATHER = (0x3A, 0x2A, 0x1E, 255)
LIGHT = (0x4E, 0x3A, 0x29, 255)
# AWBookStyle.BRASS and BRASS_DARK.
BRASS = (0xB0, 0x8D, 0x57, 255)
BRASS_DARK = (0x7A, 0x60, 0x38, 255)
# AWBookStyle.PAPER, PAPER_SHADE, PAPER_EDGE.
PAPER = (0xE9, 0xDC, 0xB8, 255)
PAPER_SHADE = (0xC6, 0xB3, 0x88, 255)
PAPER_EDGE = (0xA3, 0x8F, 0x66, 255)
# AWBookStyle.RIFT_GLOW, and the deeper purple the Rift Gate's own textures use.
RIFT = (0x9B, 0x6B, 0xFF, 255)
GLOW = (0xE0, 0xC8, 0xFF, 255)


def blank():
    return [[NONE for _ in range(SIZE)] for _ in range(SIZE)]


def write_png(path, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter: none. Sixteen square is not worth a filter.
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(kind, payload):
        return (struct.pack(">I", len(payload)) + kind + payload
                + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    data = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(data)


def handbook():
    px = blank()

    def put(x, y, colour):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            px[y][x] = colour

    def fill(x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                put(x, y, colour)

    # The board, stood on end: spine at the left, paper block at the right.
    fill(2, 1, 13, 14, LEATHER)
    fill(2, 1, 13, 1, DARK)
    fill(2, 14, 13, 14, DARK)
    fill(2, 1, 2, 14, DARK)
    fill(3, 2, 11, 2, LIGHT)

    # The paper. Two columns rather than one, so the block reads as leaves rather than as a stripe.
    fill(12, 2, 12, 13, PAPER_SHADE)
    fill(13, 2, 13, 13, PAPER)
    put(13, 1, PAPER_EDGE)
    put(13, 14, PAPER_EDGE)
    for y in (4, 7, 10, 12):
        put(12, y, PAPER_EDGE)
        put(13, y, PAPER_SHADE)

    # Brass bands on the spine, where a bound book carries them.
    fill(3, 4, 4, 5, BRASS_DARK)
    fill(3, 4, 4, 4, BRASS)
    fill(3, 10, 4, 11, BRASS_DARK)
    fill(3, 10, 4, 10, BRASS)

    # A brass corner on the cover, matching the one the screen draws.
    fill(9, 12, 11, 12, BRASS_DARK)
    fill(11, 11, 11, 12, BRASS_DARK)

    # The rift, stamped on the cover: a tear, not an oval.
    put(8, 5, RIFT)
    fill(7, 6, 8, 6, RIFT)
    fill(7, 7, 9, 7, RIFT)
    put(8, 7, GLOW)
    fill(7, 8, 9, 8, RIFT)
    put(8, 8, GLOW)
    fill(7, 9, 8, 9, RIFT)
    put(8, 10, RIFT)

    return px


def main():
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "handbook.png")
    write_png(path, handbook())
    print("wrote " + path)


if __name__ == "__main__":
    main()
