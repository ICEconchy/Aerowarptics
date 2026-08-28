#!/usr/bin/env python3
"""
Draws the Rift Beacon's inventory icon.

    python tools/beacon_texture.py

A brass hand-lamp with a rift caught in the glass and light standing straight up out of it, which is
what the item does when you use it. The palette is the goggles' - same brass, same rift purple - so
the two read as parts of one kit rather than as two mods' worth of item art. The PNG writer is the
handbook's; a second copy of it here would be a second copy to keep correct.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from handbook_texture import blank, write_png, SIZE  # noqa: E402

OUT = os.path.join("src", "main", "resources", "assets", "aerowarptics", "textures", "item")

# Brass, shared with the goggles and the probe.
BRASS = (0xB0, 0x8D, 0x57, 255)
BRASS_DARK = (0x7A, 0x60, 0x38, 255)
BRASS_LIGHT = (0xD4, 0xB0, 0x74, 255)
# The rift held in the glass.
GLASS_DARK = (0x3A, 0x2E, 0x4A, 255)
GLASS = (0x6D, 0x36, 0x96, 255)
GLASS_LIT = (0x9B, 0x6B, 0xFF, 255)
GLINT = (0xE0, 0xC8, 0xFF, 255)
# The beam standing out of the top, fading as it climbs.
BEAM_CORE = (0xD8, 0xC4, 0xFF, 235)
BEAM = (0x9B, 0x6B, 0xFF, 170)
BEAM_FAINT = (0x7A, 0x6B, 0xFF, 90)


def beacon():
    px = blank()

    def put(x, y, colour):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            px[y][x] = colour

    def fill(x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                put(x, y, colour)

    # The beam first, so the lamp's own metal draws over the bottom of it rather than under.
    # Three bands rather than a gradient: at sixteen pixels a smooth fade is four shades nobody can
    # tell apart, and the banding is what makes it read as light rather than as a purple stick.
    fill(7, 0, 8, 1, BEAM_FAINT)
    fill(7, 2, 8, 3, BEAM)
    fill(7, 4, 8, 4, BEAM_CORE)
    put(6, 3, BEAM_FAINT)
    put(9, 3, BEAM_FAINT)

    # Cap: the ring the beam comes out of.
    fill(5, 5, 10, 5, BRASS_LIGHT)
    fill(5, 6, 10, 6, BRASS)
    put(5, 5, BRASS)
    put(10, 5, BRASS_DARK)

    # Body: a squat lantern, lit from the top left.
    fill(4, 7, 11, 13, BRASS_DARK)
    fill(4, 7, 4, 13, BRASS)
    fill(4, 7, 11, 7, BRASS)

    # The window, and the rift behind it.
    fill(6, 8, 10, 12, GLASS)
    fill(9, 11, 10, 12, GLASS_DARK)
    fill(6, 8, 7, 9, GLASS_LIT)
    put(6, 8, GLINT)
    # The tear: a short diagonal, off-centre, so it reads as a crack in space rather than a pattern.
    put(8, 9, GLASS_LIT)
    put(7, 10, GLASS_LIT)
    put(8, 11, GLASS_LIT)

    # Foot, so the thing looks like it would stand up on a table.
    fill(3, 14, 12, 14, BRASS)
    fill(3, 15, 12, 15, BRASS_DARK)
    put(3, 14, BRASS_LIGHT)

    return px


def main():
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "rift_beacon.png")
    write_png(path, beacon())
    print("wrote " + path)


if __name__ == "__main__":
    main()
