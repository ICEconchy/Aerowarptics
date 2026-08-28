#!/usr/bin/env python3
"""
Draws the Rift Infused Goggles' inventory icon.

    python tools/goggles_texture.py

Brass frame, leather strap, and a lens with a rift in it - the same purple the Rift Gate's textures
use, so the thing on your face and the thing it lets you see are visibly the same substance. The PNG
writer is the handbook's; a second copy of it here would be a second copy to keep correct.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from handbook_texture import blank, write_png, SIZE  # noqa: E402

OUT = os.path.join("src", "main", "resources", "assets", "aerowarptics", "textures", "item")

# Brass, from AWBookStyle and the probe's textures.
BRASS = (0xB0, 0x8D, 0x57, 255)
BRASS_DARK = (0x7A, 0x60, 0x38, 255)
BRASS_LIGHT = (0xD4, 0xB0, 0x74, 255)
# The strap.
LEATHER = (0x3A, 0x2A, 0x1E, 255)
LEATHER_DARK = (0x20, 0x16, 0x0F, 255)
# The lens: the Rift Gate's purple, lit from the top left.
GLASS_DARK = (0x3A, 0x2E, 0x4A, 255)
GLASS = (0x6D, 0x36, 0x96, 255)
GLASS_LIT = (0x9B, 0x6B, 0xFF, 255)
GLINT = (0xE0, 0xC8, 0xFF, 255)


def goggles():
    px = blank()

    def put(x, y, colour):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            px[y][x] = colour

    def fill(x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                put(x, y, colour)

    # The strap, running off both edges the way a worn one does.
    fill(0, 6, 15, 9, LEATHER)
    fill(0, 6, 15, 6, LEATHER_DARK)
    fill(0, 9, 15, 9, LEATHER_DARK)

    def lens(left):
        top = 4
        # Housing: a brass ring around the glass.
        fill(left, top, left + 5, top + 7, BRASS_DARK)
        fill(left, top, left + 5, top, BRASS_LIGHT)
        fill(left, top + 1, left, top + 6, BRASS)
        fill(left + 5, top + 1, left + 5, top + 6, BRASS_DARK)

        # Corners off, so a lens reads as round rather than as a window.
        for corner_x, corner_y in ((left, top), (left + 5, top),
                                   (left, top + 7), (left + 5, top + 7)):
            put(corner_x, corner_y, (0, 0, 0, 0))

        # Glass, darker at the bottom right so the lens reads as curved.
        fill(left + 1, top + 1, left + 4, top + 6, GLASS)
        fill(left + 3, top + 4, left + 4, top + 6, GLASS_DARK)
        fill(left + 1, top + 1, left + 2, top + 2, GLASS_LIT)
        put(left + 1, top + 1, GLINT)
        # The tear, small enough to read as a mark on the glass rather than as a crack in it.
        put(left + 3, top + 2, GLASS_LIT)
        put(left + 2, top + 4, GLASS_LIT)

    lens(1)
    lens(9)

    # The bridge between the two housings.
    fill(7, 6, 8, 7, BRASS)
    fill(7, 8, 8, 8, BRASS_DARK)

    # Buckle, where the strap meets the right-hand housing.
    put(15, 7, BRASS)
    put(15, 8, BRASS_DARK)
    put(0, 7, BRASS_DARK)

    return px


def main():
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "rift_goggles.png")
    write_png(path, goggles())
    print("wrote " + path)


if __name__ == "__main__":
    main()
