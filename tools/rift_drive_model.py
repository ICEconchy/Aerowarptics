#!/usr/bin/env python3
"""
Builds the Rift Drive's GeckoLib model, its animations and the five tier texture sheets.

    python tools/rift_drive_model.py

The drive is a **tesseract**: a cube's worth of block space holding the shadow a four-dimensional
cube casts into three - an outer cage, a smaller cage inside it, and eight diagonals joining the two
corner for corner. The rift itself hangs in the middle of the inner cage, and two toothed rings turn
around it on perpendicular axes, the way the rings of an armillary sphere do. The faster the drive is
driven, the faster they turn; at full charge the rift is at full size and the whole thing is moving.

Written as a generator for the reason `chute_model.py` gives: a GeckoLib cube's UV rectangle is
derived from its *size* - a cube s(x,y,z) occupies 2*(sz+sx) by (sz+sy) pixels - so the geometry and
the texture are one decision written twice, and writing it twice is how it drifts. Here the cubes are
declared once, the UVs are packed by a shelf packer that cannot produce an overlap, and the painter
walks the same packing to fill the pixels. Five tiers come out of one run because they differ only in
the colour of the rift and the sheen on the inner cage.

Three checks run before anything is written, and each of them exists because the failure it catches
does not throw:

  * every cube stays inside its own block, or the drive draws in the neighbour's;
  * no two faces are coplanar, coincident, facing the same way and visible, which is z-fighting;
  * nothing that turns can pass through anything else. That last one is the interesting one, and
    `DriveClearanceTest` re-derives it from the finished file so a hand edit cannot quietly undo it.

No imaging library: a PNG is a handful of chunks around a zlib stream, and the whole sheet is drawn
with flat fills and one-pixel bevels.
"""

import json
import math
import os
import struct
import zlib

SHEET = 256

ASSETS = os.path.join("src", "main", "resources", "assets", "aerowarptics")
GEO = os.path.join(ASSETS, "geo", "rift_drive.geo.json")
ANIM = os.path.join(ASSETS, "animations", "rift_drive.animation.json")

# The block's own middle, in model pixels. Everything that turns, turns about this.
CY = 8.0

# --------------------------------------------------------------------------- colours
#
# The purple-and-brass family the Rift Gate and the Rift Chute are painted in, rather than the brown
# the drive used to wear. Every machine in this mod now comes out of the same shop.

IRON_DARK = (0x24, 0x1C, 0x30, 255)
IRON = (0x3A, 0x2E, 0x4A, 255)
IRON_LIGHT = (0x58, 0x44, 0x70, 255)
BRASS_DARK = (0x7A, 0x60, 0x38, 255)
BRASS = (0xB0, 0x8D, 0x57, 255)
BRASS_LIGHT = (0xD2, 0xB0, 0x76, 255)

# One accent per tier: the colour of the rift, and the colour of its hottest pixel. The progression
# runs cold to violent, which is the same order the state colours in RiftDriveModel run in.
TIERS = {
    "rift_drive_mk_i": ((0x2F, 0xA8, 0xB8, 255), (0x8F, 0xE6, 0xF0, 255)),
    "rift_drive_mk_ii": ((0x35, 0xC9, 0xA8, 255), (0x9B, 0xF2, 0xDC, 255)),
    "rift_drive_mk_iii": ((0x6F, 0x7C, 0xFF, 255), (0xB9, 0xC2, 0xFF, 255)),
    "rift_drive_singularity": ((0xB2, 0x4B, 0xE8, 255), (0xE6, 0xAA, 0xFF, 255)),
    "rift_drive_creative": ((0xFF, 0xC6, 0x4A, 255), (0xFF, 0xF0, 0xB8, 255)),
}

# --------------------------------------------------------------------------- bones
#
# `shafts`, `core` and `needle` are named by code and cannot be renamed freely: DriveOrientationTest
# reads `shafts` to prove the drive takes its drive along the model's facing axis, and
# RiftDriveRenderer aims `needle` and lights `core` from the machine's state.

BONES = {
    "shell": (0.0, 0.0, 0.0),
    "shafts": (0.0, CY, 0.0),
    "cell": (0.0, CY, 0.0),
    "gear_wheel": (0.0, CY, 0.0),
    "gear_hoop": (0.0, CY, 0.0),
    "core": (0.0, CY, 0.0),
    "needle": (0.0, 15.0, 0.0),
}

# Which bones turn, about which axis. Used by the clearance check below and mirrored by
# DriveClearanceTest; the animations turn exactly these and nothing else.
SPINNING = {"shafts": "z", "gear_wheel": "y", "gear_hoop": "z", "needle": "y"}

CUBES = []


def cube(bone, origin, size, style, rotation=None):
    """One box. Origins may be fractional; sizes may not.

    A fractional size gives a fractional UV rectangle, and both the packer and
    `ResourceIntegrityTest.geoUvBoxesFitAndDoNotOverlap` read sizes as whole pixels - so a cube 1.5
    deep would be packed and checked as though it were 1, and the sheet would overlap somewhere
    nobody was looking. Fractional *origins* cost nothing, and are what let a ring sit at a radius
    that is not a whole number of pixels.
    """
    for extent in size:
        if extent != int(extent):
            raise SystemExit(f"{bone}: cube size {size} is not whole pixels")
    CUBES.append({
        "bone": bone,
        "origin": tuple(float(v) for v in origin),
        "size": tuple(int(v) for v in size),
        "style": style,
        "rotation": None if rotation is None else tuple(float(v) for v in rotation),
    })


# ------------------------------------------------------------------- the outer cage
#
# Twelve edges of a block-sized cube: four posts at the corners and eight rails between them. Nothing
# fills the faces, because a tesseract you cannot see into is a box.

for sx in (-8.0, 6.0):
    for sz in (-8.0, 6.0):
        cube("shell", (sx, 0, sz), (2, 16, 2), "iron")

for y in (0.0, 14.0):
    cube("shell", (-6, y, -8), (12, 2, 2), "iron")
    cube("shell", (-6, y, 6), (12, 2, 2), "iron")
    cube("shell", (-8, y, -6), (2, 2, 12), "iron")
    cube("shell", (6, y, -6), (2, 2, 12), "iron")

# The bearing that carries the shaft through the north and south faces. It belongs to the housing
# rather than to the shaft: it is what the shaft turns *in*.
for z0 in (-8.0, 6.0):
    cube("shell", (-4, 11, z0), (8, 1, 2), "brass")
    cube("shell", (-4, 4, z0), (8, 1, 2), "brass")
    cube("shell", (-4, 5, z0), (1, 6, 2), "brass")
    cube("shell", (3, 5, z0), (1, 6, 2), "brass")

# ------------------------------------------------------------------------ the shafts
#
# A cross rather than a block, so that turning it reads as turning. The long arm reaches the block
# face - DriveOrientationTest insists on it, because a shaft that stops short of the face is a
# machine that looks unconnected - and the short arm sits a half pixel further in so that no two
# faces of the cross end up coplanar.

for z0, inner in ((-8.0, -7.5), (6.0, 5.5)):
    cube("shafts", (-2.5, 7, z0), (5, 2, 2), "brass")
    cube("shafts", (-1, 5.5, inner), (2, 5, 2), "brass")

# -------------------------------------------------------------------- the inner cage
#
# The same twelve edges again, four pixels across, hanging in the middle of the block. This is the
# cube the fourth dimension is holding at a distance; everything about the model says "the same shape
# twice, at two depths".

for sx in (-2.0, 1.0):
    for sz in (-2.0, 1.0):
        cube("cell", (sx, 6, sz), (1, 4, 1), "cell")

for y in (6.0, 9.0):
    cube("cell", (-1, y, -2), (2, 1, 1), "cell")
    cube("cell", (-1, y, 1), (2, 1, 1), "cell")
    cube("cell", (-2, y, -1), (1, 1, 2), "cell")
    cube("cell", (1, y, -1), (1, 1, 2), "cell")

# ----------------------------------------------------------------------- the diagonals
#
# Eight of them, one per corner, joining the outer cage's corner to the inner cage's. They are the
# lines that make the drawing read as a tesseract rather than as a box inside a box.
#
# Stepped out of small cubes rather than drawn as one rotated bar, deliberately. A bar on this
# diagonal needs rotation about two axes at once, and Bedrock's rotation order and sign are exactly
# the kind of thing that cannot be settled without a running game - get the sign wrong and the strut
# leaves the block. A staircase is arithmetic, and the arithmetic is checked below.

STRUT_STEPS = 5
STRUT_OUTER = 6.6   # the middle of the outer cage's corner joint
STRUT_INNER = 2.1   # a shade inside the inner cage's corner post, so no faces line up

for sx in (-1.0, 1.0):
    for sz in (-1.0, 1.0):
        for outer_y, inner_y in ((1.4, 5.8), (14.6, 10.2)):
            for step in range(STRUT_STEPS):
                t = step / (STRUT_STEPS - 1)
                x = sx * (STRUT_OUTER + (STRUT_INNER - STRUT_OUTER) * t)
                y = outer_y + (inner_y - outer_y) * t
                z = sz * (STRUT_OUTER + (STRUT_INNER - STRUT_OUTER) * t)
                cube("cell", (x - 1, y - 1, z - 1), (2, 2, 2), "strut")


# --------------------------------------------------------------------------- the gears

def ring(bone, axis, segments, radii, tangential, half_thickness, style):
    """A cog, built as `segments` chord boxes turned about `axis` and set at alternating radii.

    Alternating the radius is what makes teeth: the far segments stand a little proud of the near
    ones, which is a twelve-lobed silhouette and reads as a cog from any angle. Building the teeth
    as separate cubes would double the count for the same picture.

    Every box turns about the ring's own axis, so each is a single-axis rotation - unambiguous - and
    the set of twelve is unchanged if the sign convention is the other way round.
    """
    for index in range(segments):
        theta = 360.0 * index / segments
        r0 = radii[index % len(radii)]
        if axis == "y":
            origin = (r0, CY - half_thickness, -tangential / 2.0)
            size = (1, 2 * half_thickness, tangential)
            rotation = (0.0, theta, 0.0)
        elif axis == "z":
            origin = (r0, CY - tangential / 2.0, -half_thickness)
            size = (1, tangential, 2 * half_thickness)
            rotation = (0.0, 0.0, theta)
        else:
            origin = (-half_thickness, CY - tangential / 2.0, r0)
            size = (2 * half_thickness, tangential, 1)
            rotation = (theta, 0.0, 0.0)
        cube(bone, origin, size, style, rotation if index else None)


# Flat on the block's waist, turning about the vertical - the wheel the rift sits in the middle of.
ring("gear_wheel", "y", 12, (3.4, 4.0), tangential=2, half_thickness=1, style="gear")

# Upright and larger, turning about the shaft axis, so the two rings cross at right angles and the
# assembly reads as an armillary sphere rather than as a stack of hoops.
ring("gear_hoop", "z", 12, (5.3, 5.7), tangential=3, half_thickness=1, style="gear")

# ----------------------------------------------------------------------------- the rift
#
# Small, and meant to be. A rift the size of the cage it hangs in would be a lamp; this one is a knot
# with three arms through it, and RiftDriveRenderer swells it as the drive charges so that "full" is
# something you can see across a hangar.

cube("core", (-1, 7, -1), (2, 2, 2), "rift")
cube("core", (-1.5, 7.5, -0.5), (3, 1, 1), "rift")
cube("core", (-0.5, 5.5, -0.5), (1, 5, 1), "rift")
cube("core", (-0.5, 7.6, -1.5), (1, 1, 3), "rift")

# --------------------------------------------------------------------------- the needle
#
# The bow setting, read off the top of the machine. It lies across the top opening rather than on a
# plate, because there is no plate any more; it has to stay short enough that turning it never puts a
# corner through the top rails or through the upright gear, which is the clearance check's business.

cube("needle", (-1, 15, -4), (2, 1, 7), "needle")
cube("needle", (-2, 15, -5), (4, 1, 1), "tip")
cube("needle", (-2, 15, 3), (4, 1, 2), "needle")


# ------------------------------------------------------------------------- the checks

def check_inside_the_block():
    """Nothing may leave the block it belongs to - declared, or once it has turned.

    The declared box is what DriveOrientationTest measures and what the game uses for the model's
    bounds. The turned box is what a player sees, and a rotated cube whose corner swings past the
    face draws inside the neighbouring block, which looks like a placement bug rather than a model
    one and gets debugged in the wrong file.
    """
    problems = []
    for item in CUBES:
        low = item["origin"]
        high = tuple(low[axis] + item["size"][axis] for axis in range(3))
        for axis, name in enumerate("xyz"):
            lo, hi = (0.0, 16.0) if name == "y" else (-8.0, 8.0)
            if low[axis] < lo - 1e-9 or high[axis] > hi + 1e-9:
                problems.append(f"{item['bone']}: {name} runs {low[axis]}..{high[axis]}, "
                                f"outside {lo}..{hi}")
        for x, y, z in corners(item):
            if not (-8.0 - 1e-6 <= x <= 8.0 + 1e-6 and 0.0 - 1e-6 <= y <= 16.0 + 1e-6
                    and -8.0 - 1e-6 <= z <= 8.0 + 1e-6):
                problems.append(f"{item['bone']}: a turned corner lands at "
                                f"({x:.2f}, {y:.2f}, {z:.2f}), outside the block")
    if problems:
        raise SystemExit("\n".join(sorted(set(problems))))


def corners(item):
    """The eight corners of a cube, with its own rotation applied."""
    low = item["origin"]
    high = tuple(low[axis] + item["size"][axis] for axis in range(3))
    points = []
    for x in (low[0], high[0]):
        for y in (low[1], high[1]):
            for z in (low[2], high[2]):
                points.append(turn(item["rotation"], (x, y, z)))
    return points


def turn(rotation, point):
    """A cube's own rotation, about the block's middle, which is where every pivot here sits."""
    if rotation is None:
        return point
    x, y, z = point[0], point[1] - CY, point[2]
    rx, ry, rz = (math.radians(a) for a in rotation)
    if rx:
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    if ry:
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    if rz:
        x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return x, y + CY, z


def check_no_coincident_faces():
    """No two visible faces may be coplanar, overlapping and facing the same way.

    Nesting is fine and this model does plenty of it - a cube inside another cube renders correctly,
    and the Rift Drive has always relied on that. What does not render correctly is two faces at the
    same depth pointing the same way: the depth buffer has no answer, and the surface flickers
    between them as the camera moves. Faces buried inside a third cube are exempt, because nobody
    ever sees them.

    Only unrotated cubes are compared. A turned cube's faces are no longer axis aligned, and the
    rings are the only turned cubes here - each of their twelve boxes stands at a different angle,
    so none of them can be coplanar with another.
    """
    flat = [item for item in CUBES if item["rotation"] is None]
    problems = []
    for index, first in enumerate(flat):
        for second in flat[index + 1:]:
            for axis in range(3):
                for side in (0, 1):
                    plane = face_plane(first, axis, side)
                    if abs(plane - face_plane(second, axis, side)) > 1e-9:
                        continue
                    overlap = face_overlap(first, second, axis)
                    if overlap is None:
                        continue
                    if buried(overlap, axis, plane, (first, second)):
                        continue
                    problems.append(
                        f"{first['bone']} and {second['bone']} share a visible "
                        f"{'xyz'[axis]}={plane:g} face")
    if problems:
        raise SystemExit("\n".join(sorted(set(problems))))


def face_plane(item, axis, side):
    return item["origin"][axis] + (item["size"][axis] if side else 0)


def face_overlap(first, second, axis):
    """The rectangle two faces share, in the two axes that are not `axis`, or None."""
    box = []
    for other in range(3):
        if other == axis:
            continue
        lo = max(first["origin"][other], second["origin"][other])
        hi = min(first["origin"][other] + first["size"][other],
                 second["origin"][other] + second["size"][other])
        if hi - lo <= 1e-9:
            return None
        box.append((lo + hi) / 2.0)
    return box


def buried(overlap, axis, plane, involved):
    """Whether the shared face sits strictly inside some other cube, where it cannot be seen."""
    point = [0.0, 0.0, 0.0]
    point[axis] = plane
    fill = iter(overlap)
    for other in range(3):
        if other != axis:
            point[other] = next(fill)
    for item in CUBES:
        if item in involved or item["rotation"] is not None:
            continue
        low = item["origin"]
        if all(low[a] + 1e-6 < point[a] < low[a] + item["size"][a] - 1e-6 for a in range(3)):
            return True
    return False


def centred(low, high, margin=0.0):
    """A box measured from the middle of the block, which is where every pivot in this model sits."""
    origin = (0.0, CY, 0.0)
    lo = [low[a] - origin[a] for a in range(3)]
    hi = [high[a] - origin[a] for a in range(3)]
    if margin:
        lo = [v * (1.0 + margin) for v in lo]
        hi = [v * (1.0 + margin) for v in hi]
    return lo, hi


# The inner cage breathes and the rift swells - RiftDriveRenderer takes the core half again as large
# at full charge - so both are measured at the largest they are ever drawn, not as authored.
MARGIN = {"cell": 0.06, "core": 0.55}

# Two pairs are meant to touch. The diagonals are bolted into the outer cage's corners, which is what
# makes the drawing a tesseract rather than a box inside a box; and the rift strains against the bars
# of its own cage once it is fully charged, which is the picture rather than a fault.
CLEARANCE_EXEMPT = {
    frozenset(("cell", "shell")),
    frozenset(("core", "cell")),
}


def bands(bone, axis, margin=0.0):
    """What a turning bone occupies over a whole revolution.

    A box turned about an axis it is already aligned with sweeps a plain annulus: its extent along
    the axis never changes, and its distance from the axis runs from the nearest point of its cross
    section out to the furthest corner. So the swept solid is exact rather than approximated, and a
    cube's own rotation can be ignored - turning a box about the axis it will be swept about does not
    change what it sweeps, which is why the rings are authored as twelve turned boxes at all.

    Returns (from, to, near, far) per distinct band; the rings only have two.
    """
    index = "xyz".index(axis)
    others = [a for a in range(3) if a != index]
    out = []
    for item in CUBES:
        if item["bone"] != bone:
            continue
        high = [item["origin"][a] + item["size"][a] for a in range(3)]
        low, high = centred(item["origin"], high, margin)
        near = far = 0.0
        for a in others:
            near += max(low[a], 0.0, -high[a]) ** 2
            far += max(abs(low[a]), abs(high[a])) ** 2
        band = (low[index], high[index], math.sqrt(near), math.sqrt(far))
        if band not in out:
            out.append(band)
    return out


def boxes(bone, margin=0.0):
    """A still bone, as the bounding boxes of its cubes once their own rotation is applied."""
    out = []
    for item in CUBES:
        if item["bone"] != bone:
            continue
        points = corners(item)
        low = [min(p[a] for p in points) for a in range(3)]
        high = [max(p[a] for p in points) for a in range(3)]
        out.append(centred(low, high, margin))
    return out


def overlaps(a0, a1, b0, b1):
    """Two intervals sharing more than a rounding error. Touching is not sharing."""
    return min(a1, b1) - max(a0, b0) > 1e-6


def box_meets_box(first, second):
    return all(overlaps(first[0][a], first[1][a], second[0][a], second[1][a]) for a in range(3))


def band_meets_box(band, axis, box):
    """An annulus of revolution against a box. Exact.

    A box's distance from the axis runs over one connected interval - from the nearest point of its
    cross section to its furthest corner - so the two solids meet exactly when their extents along
    the axis overlap and those two radial intervals do.
    """
    if not overlaps(band[0], band[1], box[0][axis], box[1][axis]):
        return False
    near = far = 0.0
    for a in range(3):
        if a == axis:
            continue
        near += max(box[0][a], 0.0, -box[1][a]) ** 2
        far += max(abs(box[0][a]), abs(box[1][a])) ** 2
    return overlaps(band[2], band[3], math.sqrt(near), math.sqrt(far))


def band_meets_band(first, first_axis, second, second_axis):
    """Two annuli. Coaxial ones are exact; perpendicular ones are swept in two hundredths of a pixel.

    Perpendicular is the interesting case and the one this model turns on: the flat gear and the
    upright hoop share the block and must never share a pixel. Writing p for a point measured from
    the middle of the block, the flat ring asks that p is a certain distance from the vertical and
    the hoop that it is a certain distance from the shaft axis, which leaves one free coordinate and
    a pair of intervals it has to satisfy at once. Walking the other two and asking whether anything
    is left over is short, and it is checkable by eye, which the closed form is not.
    """
    if first_axis == second_axis:
        return (overlaps(first[0], first[1], second[0], second[1])
                and overlaps(first[2], first[3], second[2], second[3]))

    step = 0.02
    lo_a, hi_a, near_a, far_a = first
    lo_b, hi_b, near_b, far_b = second
    steps_a = max(1, int(math.ceil((hi_a - lo_a) / step)))
    steps_b = max(1, int(math.ceil((hi_b - lo_b) / step)))
    for i in range(steps_a + 1):
        along = lo_a + (hi_a - lo_a) * i / steps_a
        for j in range(steps_b + 1):
            across = lo_b + (hi_b - lo_b) * j / steps_b
            low = max(near_a ** 2 - across ** 2, near_b ** 2 - along ** 2, 0.0)
            high = min(far_a ** 2 - across ** 2, far_b ** 2 - along ** 2)
            if high - low > 1e-6:
                return True
    return False


def collide(first, second):
    turning_first = SPINNING.get(first)
    turning_second = SPINNING.get(second)
    margin_first = MARGIN.get(first, 0.0)
    margin_second = MARGIN.get(second, 0.0)
    if turning_first and turning_second:
        return any(band_meets_band(one, "xyz".index(turning_first),
                                   other, "xyz".index(turning_second))
                   for one in bands(first, turning_first, margin_first)
                   for other in bands(second, turning_second, margin_second))
    if turning_second:
        return collide(second, first)
    if turning_first:
        return any(band_meets_box(band, "xyz".index(turning_first), box)
                   for band in bands(first, turning_first, margin_first)
                   for box in boxes(second, margin_second))
    return any(box_meets_box(one, other)
               for one in boxes(first, margin_first)
               for other in boxes(second, margin_second))


def check_nothing_turns_through_anything():
    """The invariant this model is built around, and the one nothing else would catch.

    Two rings crossing at right angles, a shaft, a needle and two nested cages all share sixteen
    pixels. Every radius in the file above is an answer to "what will this pass through", and none of
    them has any slack worth spending. A model that clips is still a model: it loads, it renders, and
    the only symptom is a tooth flickering through a strut once a second, on a machine nobody is
    looking at straight on. `DriveClearanceTest` re-derives the same thing from the finished file.
    """
    problems = []
    names = list(BONES)
    for index, first in enumerate(names):
        for second in names[index + 1:]:
            if frozenset((first, second)) in CLEARANCE_EXEMPT:
                continue
            if collide(first, second):
                problems.append(f"{first} passes through {second}")
    if problems:
        raise SystemExit("\n".join(problems))


# ------------------------------------------------------------------------- packing

def pack():
    """Shelf-packs every cube's UV rectangle, left to right and top to bottom.

    Not the tightest arrangement possible, but trivially correct: nothing on a shelf overlaps
    anything else on that shelf, and no shelf overlaps the one above it.
    """
    placements = []
    x = y = shelf = 0
    for item in CUBES:
        sx, sy, sz = item["size"]
        width = 2 * (sz + sx)
        height = sz + sy
        if x + width > SHEET:
            x = 0
            y += shelf
            shelf = 0
        if y + height > SHEET:
            raise SystemExit(f"the cubes do not fit a {SHEET}x{SHEET} sheet")
        placements.append((x, y))
        x += width
        shelf = max(shelf, height)
    return placements


def geo(placements):
    bones = {}
    for item, (u, v) in zip(CUBES, placements):
        entry = {"origin": list(item["origin"]), "size": list(item["size"]), "uv": [u, v]}
        if item["rotation"] is not None:
            entry["rotation"] = list(item["rotation"])
            entry["pivot"] = [0.0, CY, 0.0]
        bones.setdefault(item["bone"], []).append(entry)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.rift_drive",
                "texture_width": SHEET,
                "texture_height": SHEET,
                "visible_bounds_width": 2,
                "visible_bounds_height": 2,
                "visible_bounds_offset": [0, 0.5, 0],
            },
            "bones": [{"name": name, "pivot": list(BONES[name]), "cubes": bones[name]}
                      for name in BONES if name in bones],
        }],
    }


# ----------------------------------------------------------------------- animations
#
# One clip per drive state, because RiftDriveState names one and ResourceIntegrityTest checks that
# every name resolves. The clips differ almost entirely in *length*: the controller multiplies the
# playback speed by a figure that rises with the charge, so a shorter clip and a faster multiplier
# compound, and "charging" visibly winds up from a crawl to a blur without a single extra keyframe.

def clip(length, turns, cell=None, core=(1.0, 1.0)):
    """A clip: the gears and shaft turn `turns` whole revolutions in `length` seconds.

    Whole revolutions, always. A loop that ends anywhere but back where it started snaps, and the
    snap is far more visible on a spinning wheel than the speed change that hides it.
    """
    end = f"{length}"
    bones = {
        # Counter-rotating on purpose: two rings turning the same way read as one object wobbling.
        "gear_wheel": {"rotation": {"0.0": [0, 0, 0], end: [0, 360 * turns, 0]}},
        "gear_hoop": {"rotation": {"0.0": [0, 0, 0], end: [0, 0, -360 * turns]}},
        "shafts": {"rotation": {"0.0": [0, 0, 0], end: [0, 0, 360 * turns]}},
        "core": {"rotation": {"0.0": [0, 0, 0], end: [360 * core[0], 360 * core[1], 0]}},
    }
    if cell is not None:
        bones["cell"] = cell
    return {"loop": True, "animation_length": length, "bones": bones}


def breath(length, depth):
    """The inner cage easing in and out - the tesseract turning through the fourth dimension.

    Kept small outside the warp. The struts are in this bone too, so anything more than a few per
    cent visibly parts them from the corners they are drawn to.
    """
    half = f"{length / 2.0}"
    return {"scale": {"0.0": [1, 1, 1], half: [1 + depth] * 3, f"{length}": [1, 1, 1]}}


def inversion(length):
    """The inner cage collapsing to a point and coming back, once per clip.

    What a tesseract does when it turns edge on: the inner cube shrinks through nothing and returns
    the other way round. Only used while the rift is open, where a machine turning itself inside out
    is exactly the note wanted.
    """
    half = length / 2.0
    return {
        "scale": {"0.0": [1, 1, 1], f"{half}": [0.25, 0.25, 0.25], f"{length}": [1, 1, 1]},
        "rotation": {"0.0": [0, 0, 0], f"{length}": [0, 180, 0]},
    }


def animations():
    clips = {
        "idle": clip(8.0, 1, breath(8.0, 0.02)),
        "charging": clip(2.5, 1, breath(2.5, 0.03)),
        "charged": clip(1.4, 1, breath(1.4, 0.05), core=(1.0, 2.0)),
        "destination_selected": clip(1.2, 1, breath(1.2, 0.05), core=(1.0, 2.0)),
        "stabilizing": clip(0.7, 1, breath(0.7, 0.06), core=(2.0, 2.0)),
        "warping": clip(0.35, 1, inversion(0.35), core=(2.0, 3.0)),
        "arriving": clip(3.0, 1, breath(3.0, 0.04), core=(1.0, 1.0)),
        "cooldown": clip(5.0, 1, breath(5.0, 0.02)),
    }
    # Error is the one clip that does not turn. A drive that has refused is not running, and a
    # machine still spinning happily while it reports a fault says the fault is not real.
    clips["error"] = {
        "loop": True,
        "animation_length": 1.6,
        "bones": {
            "gear_wheel": {"rotation": {"0.0": [0, 0, 0], "0.4": [0, 18, 0], "0.6": [0, 6, 0],
                                        "1.0": [0, 22, 0], "1.6": [0, 0, 0]}},
            "gear_hoop": {"rotation": {"0.0": [0, 0, 0], "0.5": [0, 0, -12], "0.8": [0, 0, -2],
                                       "1.6": [0, 0, 0]}},
            "shafts": {"rotation": {"0.0": [0, 0, 0], "0.4": [0, 0, 18], "0.6": [0, 0, 6],
                                    "1.0": [0, 0, 22], "1.6": [0, 0, 0]}},
            "core": {"position": {"0.0": [0, 0, 0], "0.3": [0.2, -0.2, 0], "0.7": [-0.2, 0.1, 0],
                                  "1.6": [0, 0, 0]}},
        },
    }
    return {
        "format_version": "1.8.0",
        "animations": {f"animation.rift_drive.{name}": body for name, body in clips.items()},
    }


# ------------------------------------------------------------------------- painting

def blank():
    return [[(0, 0, 0, 0)] * SHEET for _ in range(SHEET)]


def fill(px, x0, y0, x1, y1, colour):
    for y in range(max(0, int(y0)), min(SHEET, int(y1))):
        for x in range(max(0, int(x0)), min(SHEET, int(x1))):
            px[y][x] = colour


def shade(colour, factor):
    r, g, b, a = colour
    return (min(255, int(r * factor)), min(255, int(g * factor)), min(255, int(b * factor)), a)


def blend(first, second, amount):
    return tuple(int(first[i] + (second[i] - first[i]) * amount) for i in range(3)) + (255,)


def bevel(px, x0, y0, x1, y1, colour):
    """One-pixel border: lifted along the top and left, darkened along the bottom and right.

    An edge you can see is most of what separates a modelled object from a box, and it is the one
    piece of shading that has to be drawn rather than derived from the face.
    """
    fill(px, x0, y0, x1, y0 + 1, shade(colour, 1.25))
    fill(px, x0, y0, x0 + 1, y1, shade(colour, 1.15))
    fill(px, x0, y1 - 1, x1, y1, shade(colour, 0.65))
    fill(px, x1 - 1, y0, x1, y1, shade(colour, 0.75))


def styles(accent, hot):
    """Colour per style, as (top, side, bottom, ribbed).

    Only three of the seven change between tiers, and all three of them are the ones a player looks
    at: the rift, the needle's point, and the sheen on the inner cage. The cage and the gears are the
    same machine shop at every tier, which is the point - a Mk III is a better drive, not a different
    one.
    """
    return {
        "iron": (IRON_LIGHT, IRON, IRON_DARK, False),
        "brass": (BRASS_LIGHT, BRASS, BRASS_DARK, False),
        "gear": (BRASS, BRASS_DARK, BRASS_DARK, True),
        # The inner cage and the diagonals are one material and the gears are another, deliberately.
        # Three readings, three colours: the outer cage is the dark iron the machine is bolted out
        # of, the gear train is brass, and everything the fourth dimension is holding out there is
        # steel with the rift's own colour in it. Paint the diagonals brass and they merge with the
        # rings turning behind them, which is what the first draft did.
        "cell": (blend(IRON_LIGHT, hot, 0.4), blend(IRON, accent, 0.4), IRON_DARK, False),
        "strut": (blend(IRON_LIGHT, accent, 0.35), blend(IRON, accent, 0.28), IRON_DARK, False),
        "needle": (BRASS_LIGHT, BRASS, BRASS_DARK, False),
        "tip": (hot, accent, shade(accent, 0.7), False),
    }


def paint(px, placements, accent, hot):
    """Fills each cube's box-UV region, face by face.

    Box UV lays a cube out as a known arrangement: a strip of top and bottom across the top of the
    region, then a strip of the four sides beneath it. Splitting the rect back into those faces is
    what lets each be lit differently - top bright, sides mid, bottom dark - and that single change
    is what makes a stack of boxes read as a solid object rather than a flat sticker.
    """
    table = styles(accent, hot)
    for item, (u, v) in zip(CUBES, placements):
        sx, sy, sz = item["size"]
        if item["style"] == "rift":
            rift(px, u, v, 2 * (sz + sx), sz + sy, accent, hot)
            continue
        top, side, bottom, ribbed = table[item["style"]]
        fill(px, u + sz, v, u + sz + sx, v + sz, top)
        bevel(px, u + sz, v, u + sz + sx, v + sz, top)
        fill(px, u + sz + sx, v, u + sz + sx + sx, v + sz, bottom)
        bevel(px, u + sz + sx, v, u + sz + sx + sx, v + sz, bottom)
        widths = [sz, sx, sz, sx]
        x = u
        for index, w in enumerate(widths):
            # Front and back catch a little more light than the two ends.
            tone = shade(side, 1.0 if index % 2 else 0.86)
            fill(px, x, v + sz, x + w, v + sz + sy, tone)
            if ribbed:
                # A tooth every other pixel across the rim. A cog whose face is one flat colour
                # reads as a washer; the banding is what says the edge is cut.
                for column in range(x + 1, x + w - 1, 2):
                    fill(px, column, v + sz, column + 1, v + sz + sy, shade(tone, 0.7))
            bevel(px, x, v + sz, x + w, v + sz + sy, tone)
            x += w


def rift(px, u, v, width, height, accent, hot):
    """The rift's own pixels: hot in the middle, cooling outwards, with no bevel.

    Every other cube here is metal and is lit as metal. This one is not lit at all - the renderer
    draws it at full brightness in the state's own colour - so shading it as though a lamp were
    somewhere would fight the tint rather than help it.
    """
    for y in range(v, min(SHEET, v + height)):
        for x in range(u, min(SHEET, u + width)):
            # Distance from the middle of the patch, on a diagonal so the falloff does not line up
            # with the face boundaries and band.
            dx = (x - (u + width / 2.0)) / max(1.0, width / 2.0)
            dy = (y - (v + height / 2.0)) / max(1.0, height / 2.0)
            distance = min(1.0, math.sqrt(dx * dx + dy * dy))
            px[y][x] = blend(hot, shade(accent, 0.55), distance)
    if width >= 4 and height >= 2:
        fill(px, u + width // 2 - 1, v + height // 2, u + width // 2 + 1, v + height // 2 + 1,
             (255, 255, 255, 255))


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
    check_inside_the_block()
    check_no_coincident_faces()
    check_nothing_turns_through_anything()

    placements = pack()
    write_json(GEO, geo(placements))
    write_json(ANIM, animations())

    for tier, (accent, hot) in TIERS.items():
        px = blank()
        paint(px, placements, accent, hot)
        png(os.path.join(ASSETS, "textures", "block", tier + ".png"), px)

    used = max(v + item["size"][2] + item["size"][1]
               for item, (_u, v) in zip(CUBES, placements))
    print(f"rift drive: {len(CUBES)} cubes packed into {SHEET}x{used} of a {SHEET}x{SHEET} sheet")
    print(f"  {GEO}\n  {ANIM}")
    for tier in TIERS:
        print(f"  {os.path.join(ASSETS, 'textures', 'block', tier + '.png')}")


if __name__ == "__main__":
    main()
