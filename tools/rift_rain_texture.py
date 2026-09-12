#!/usr/bin/env python3
"""
Draws the rain that falls during a Rift Storm.

    python tools/rift_rain_texture.py

Written rather than borrowed. Vanilla's rain texture is pale blue, and a vertex tint can only take
colour away from a texture, never add it: tinting blue drops purple leaves them a muddy blue-violet
at best. So the drops are drawn in the rift's own colours here and the renderer leaves them alone -
a bright lilac head, a violet tail thinning to nothing behind it.

Sixty-four square, where vanilla's is sixty-four by two hundred and fifty-six. A texture taller than
it is wide is taken by this mod's resource test to be an animation strip, and this one is not - it is
one tile of rain that repeats down the column. The renderer samples it at half a tile per block to
keep the drops the length vanilla's are.

The drops wrap top to bottom, so the tile repeats without a seam. Placement is seeded, so re-running
the script writes the same file.

RGBA, no dependencies.
"""

import os
import random
import struct
import zlib

SIZE = 64
OUT = os.path.join("src", "main", "resources", "assets", "aerowarptics", "textures", "environment")

# The palette of the rift the drive vents: RiftStormSky's CORE and HALO, and the Handbook's RIFT_GLOW.
HEAD = (0xF0, 0xE6, 0xFF)
BODY = (0xB8, 0x8C, 0xFF)
TAIL = (0x8A, 0x6B, 0xFF)

DROPS = 22
SEED = 0x5157


def write_png(path, pixels):
    """Writes an RGBA PNG, sized from the pixels it was handed. See handbook_texture.py."""
    height = len(pixels)
    width = len(pixels[0]) if height else 0
    for index, row in enumerate(pixels):
        if len(row) != width:
            raise ValueError("row %d is %d wide, expected %d" % (index, len(row), width))

    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(kind, payload):
        return (struct.pack(">I", len(payload)) + kind + payload
                + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    data = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(data)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rain():
    px = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]
    rng = random.Random(SEED)

    # Columns are dealt out rather than rolled, so no two drops share one and none is left crowded.
    columns = list(range(2, SIZE - 2, 3))
    rng.shuffle(columns)

    for drop in range(DROPS):
        x = columns[drop % len(columns)]
        head = rng.randrange(SIZE)
        length = rng.randint(7, 13)
        strength = rng.uniform(0.7, 1.0)
        # The texture scrolls downward, so the head is the lowest pixel of the drop and the tail
        # trails up behind it.
        for step in range(length):
            y = (head - step) % SIZE
            t = step / (length - 1)
            colour = mix(HEAD, BODY, t * 2) if t < 0.5 else mix(BODY, TAIL, (t - 0.5) * 2)
            alpha = round(255 * strength * (1.0 - t) ** 1.4)
            if alpha > px[y][x][3]:
                px[y][x] = colour + (alpha,)
            # A faint fringe either side of the head only, so the drop reads as falling light rather
            # than a hairline scratched down the column.
            if step < 3:
                fringe = round(alpha * 0.25)
                for side in (x - 1, x + 1):
                    if fringe > px[y][side][3]:
                        px[y][side] = TAIL + (fringe,)
    return px


def main():
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "rift_rain.png")
    write_png(path, rain())
    print("wrote " + path)


if __name__ == "__main__":
    main()
