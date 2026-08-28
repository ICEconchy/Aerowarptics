#!/usr/bin/env python3
"""
Draws the Rift Portal's animated shimmer, and the animation metadata that drives it.

    python tools/rift_portal_texture.py

The pane standing in a Rift Gate's opening is a block now, and a block gets a texture rather than a
renderer. That texture has three jobs and they pull against each other:

  * **It must tile.** A gate can be thirty blocks across, and every one of them samples this same
    sixteen pixels. Anything with a feature in it - a blob, a bright corner, a diagonal streak -
    becomes a lattice of that feature the moment the opening is bigger than one block, which is what
    makes a hand-painted portal texture so hard to get right.
  * **It must loop.** Thirty-two frames play and then the first one comes back. A shimmer that jumps
    at the seam reads as a dropped frame rather than as motion.
  * **It must not read as a pattern.** Which is the awkward one, because the only way to satisfy the
    first two is periodic functions, and periodic functions are exactly what a pattern is.

So it is built out of travelling waves whose wavelengths divide the tile and whose speeds divide the
loop - which makes both seams exact by construction rather than by blending - and enough of them, at
angles that share no common factor, that the sum has a long visual beat. Nothing here is noise: every
pixel of every frame is a closed-form sum, so the texture is the same on every run and a change to one
wave is a change you can predict.

The palette is the violet the gates were already drawn in. That colour was a constant in
`ClientRuntime` when the aperture was a rendered billboard; it lives here now, because this is what
draws it.

**Two more strips come out of the same run**: `rift_portal_opening.png` and `rift_portal_closing.png`,
for the pane's own opening and closing animation - see `RiftPortalStage`. Both reuse `sample()` for
the shimmer underneath and multiply it by a seamless breathing pulse, dimming and thinning the pane as
the pulse falls rather than simply fading it, so an unformed doorway reads as thin and dark rather than
merely translucent. `opening` breathes up to full and back to nothing; `closing` is the same pulse
inverted, full at the seam and hollowed out in the middle. Neither is a literal progress bar - see the
long comment on `RiftPortalStage` for why a shared, tiling, looping texture cannot be one - they are a
motif that says "forming" or "unravelling" for as long as the state holds.
"""

import json
import math
import os
import struct
import zlib

SIZE = 16
FRAMES = 32

# The opening and closing strips are shorter loops, chosen to land close to this mod's own default
# durations - `gateDialTicks` and the fixed twenty-tick close - so that one breath of the pulse is
# roughly one real dial or one real close. Not phase-locked to either (see the module docstring and
# `RiftPortalStage`), just the same order of magnitude, so the motif does not read as arbitrary.
OPENING_FRAMES = 20
OPENING_FRAMETIME = 2  # 40 ticks a loop
CLOSING_FRAMES = 20
CLOSING_FRAMETIME = 1  # 20 ticks a loop

ASSETS = os.path.join("src", "main", "resources", "assets", "aerowarptics")
BLOCKS = os.path.join(ASSETS, "textures", "block")
TEXTURE = os.path.join(BLOCKS, "rift_portal.png")
META = TEXTURE + ".mcmeta"
TEXTURE_OPENING = os.path.join(BLOCKS, "rift_portal_opening.png")
META_OPENING = TEXTURE_OPENING + ".mcmeta"
TEXTURE_CLOSING = os.path.join(BLOCKS, "rift_portal_closing.png")
META_CLOSING = TEXTURE_CLOSING + ".mcmeta"

# --------------------------------------------------------------------------- colour
#
# The ramp the field is read through, darkest first. 0xA24BFF is the violet a gate has burned since
# the aperture was a drawn effect; the two either side of it are that colour taken down towards the
# indigo the Rift Gate's frame is painted in, and up towards white.

RAMP = [
    (0x10, 0x06, 0x22, 110),
    (0x2A, 0x0F, 0x55, 132),
    (0x4E, 0x1C, 0x94, 158),
    (0x7A, 0x33, 0xD6, 186),
    (0xA2, 0x4B, 0xFF, 208),
    (0xC5, 0x84, 0xFF, 228),
    (0xE8, 0xC6, 0xFF, 244),
    (0xFF, 0xFF, 0xFF, 255),
]

# Alpha climbs with brightness rather than sitting flat, which is the whole of why this reads as a
# surface with depth behind it. The dark of the field is nearly half transparent, so you see the far
# side of the ring through it; the filaments running across are almost solid. A pane at one alpha is
# a sheet of tinted glass, however good the colours on it are - and a nether portal is not a window.

# --------------------------------------------------------------------------- the field
#
# Each wave is (across, up, turns, phase, weight):
#
#   across, up  wavelengths across the tile, so both must be whole numbers or the tile will not meet
#               itself at its own edge;
#   turns       revolutions over the whole loop, again whole, and negative to travel the other way;
#   phase       radians, which is only there to stop every wave peaking at the origin together;
#   weight      how much of the sum it is.
#
# The wave numbers are deliberately coprime-ish and the turn counts deliberately different, so the
# combination only repeats when all of them line up - which is once per loop, which is the point.

WAVES = [
    (1, 0, 1, 0.00, 0.70),
    (0, 1, -1, 1.31, 0.52),
    (1, 1, 2, 2.44, 0.34),
    (2, -1, -1, 0.77, 0.24),
    (1, -2, 1, 3.61, 0.16),
    (2, 2, -2, 5.02, 0.11),
    (3, 1, 1, 4.20, 0.08),
]

# A second, much sparser set, read through a sharp curve rather than a smooth one. This is what puts
# the filaments in - two or three bright threads drifting across the surface - and it is the
# difference between a shimmer and a gradient breathing.
#
# Low wave numbers matter more here than anywhere else. The lit set is where this field crosses zero,
# which for a wavelength of the whole tile is one long curve across it; shorten the wavelength and the
# same rule lights a scatter of unconnected pixels, which is violet static rather than a thread. That
# is what the first draft of this file did.
THREADS = [
    (1, 2, -1, 0.42, 1.00),
    (2, -1, 1, 2.10, 0.55),
]

TAU = math.pi * 2.0


def wave_sum(waves, x, y, t):
    """Sum of travelling waves at a pixel and a moment. Periodic in x, y and t by construction."""
    total = 0.0
    weight = 0.0
    for across, up, turns, phase, amount in waves:
        angle = TAU * (across * x / SIZE + up * y / SIZE + turns * t) + phase
        total += amount * math.sin(angle)
        weight += amount
    return total / weight


def filaments(x, y, t):
    """Thin bright threads: the thread field folded so that only its zero crossings light up.

    A smooth field on its own gives smooth clouds, and clouds are what a nebula looks like rather
    than what a tear in space looks like. Folding at zero and narrowing the result leaves a curve a
    pixel or two wide travelling across the tile, which is the one feature here that reads as
    structure rather than as texture.
    """
    value = abs(wave_sum(THREADS, x, y, t))
    return max(0.0, 1.0 - value * 3.4)


def sample(x, y, t):
    """One pixel, as (r, g, b, a)."""
    base = wave_sum(WAVES, x, y, t) * 0.5 + 0.5
    # Pushed hard towards the dark end. A pane that is mostly bright has nothing for the threads to
    # be brighter than, and reads as a sheet of lilac rather than as depth with something moving in
    # it; most of this surface wants to be the colour of the inside of a hole.
    level = base ** 2.0 * 0.70 + filaments(x, y, t) * 0.52
    return ramp(min(1.0, level))


def ramp(level):
    """Reads the palette at a fraction, interpolating between its stops."""
    span = level * (len(RAMP) - 1)
    low = min(len(RAMP) - 2, int(span))
    fraction = span - low
    first = RAMP[low]
    second = RAMP[low + 1]
    return tuple(int(round(first[channel] + (second[channel] - first[channel]) * fraction))
                 for channel in range(4))


def frames():
    rows = []
    for frame in range(FRAMES):
        t = frame / FRAMES
        for y in range(SIZE):
            rows.append([sample(x + 0.5, y + 0.5, t) for x in range(SIZE)])
    return rows


# --------------------------------------------------------------------------- opening and closing

def breathe(t, rising):
    """A seamless pulse: 0 at both ends of the loop and 1 in the middle, or the mirror of that.

    `rising` is `True` for opening: nothing, building to full, easing back to nothing, over and over -
    read as a tear trying to catch and gathering strength each time. `False` is closing: full at the
    seam and hollowed out in the middle - the same doorway losing its grip and catching itself again.
    Smoothstepped rather than a plain triangle so the turn at the peak is a curve, not a corner; a
    corner is the one shape in this whole file that would read as a mistake rather than as motion.
    """
    triangle = 1.0 - abs(2.0 * t - 1.0)
    eased = triangle * triangle * (3.0 - 2.0 * triangle)
    return eased if rising else 1.0 - eased


def staged_sample(x, y, t, level):
    """The calm shimmer, dimmed and thinned by how present the pane is this frame.

    Thinned as well as dimmed - alpha falls with `level` too - because a pane that only darkened
    would still read as a solid sheet with the lights off, and an unformed doorway ought to look like
    less of a doorway, not a dark one. Blended towards the palette's own coldest stop rather than
    towards black, so a half-formed pane is still unmistakably this rift's colour and not soot.
    """
    r, g, b, a = sample(x, y, t)
    cold_r, cold_g, cold_b, _cold_a = RAMP[0]
    blended = (
        int(round(cold_r + (r - cold_r) * level)),
        int(round(cold_g + (g - cold_g) * level)),
        int(round(cold_b + (b - cold_b) * level)),
    )
    alpha = int(round(a * (0.12 + 0.88 * level)))
    return blended + (alpha,)


def staged_frames(frame_count, rising):
    rows = []
    for frame in range(frame_count):
        t = frame / frame_count
        level = breathe(t, rising)
        for y in range(SIZE):
            rows.append([staged_sample(x + 0.5, y + 0.5, t, level) for x in range(SIZE)])
    return rows


# --------------------------------------------------------------------------- writing

def png(path, rows, width, height):
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    blob = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(blob)


def metadata(frametime):
    """Not interpolated, whatever the frametime. Thirty-two frames of a field this smooth are already
    smooth, and interpolation would cost the filaments their edges - which are the only hard edges in
    the texture and the whole reason it reads as threads rather than as fog.
    """
    return {"animation": {"frametime": frametime, "interpolate": False}}


def write_strip(texture, meta, rows, frame_count, frametime):
    png(texture, rows, SIZE, SIZE * frame_count)
    os.makedirs(os.path.dirname(meta), exist_ok=True)
    with open(meta, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(metadata(frametime), handle, indent=2)
        handle.write("\n")
    print(f"  {texture} — {frame_count} frames\n  {meta}")


def main():
    print("rift portal:")
    idle = frames()
    opening = staged_frames(OPENING_FRAMES, rising=True)
    closing = staged_frames(CLOSING_FRAMES, rising=False)

    write_strip(TEXTURE, META, idle, FRAMES, 2)
    write_strip(TEXTURE_OPENING, META_OPENING, opening, OPENING_FRAMES, OPENING_FRAMETIME)
    write_strip(TEXTURE_CLOSING, META_CLOSING, closing, CLOSING_FRAMES, CLOSING_FRAMETIME)


if __name__ == "__main__":
    main()
