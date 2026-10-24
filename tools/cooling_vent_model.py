#!/usr/bin/env python3
"""
Writes the Cooling Vent's block model and its blockstate. Its six textures are the artist's (2026-10-21);
this script once painted them and no longer writes any.

    python tools/cooling_vent_model.py

The model is a casing with a hole in it, and where the hole is, how deep the throat goes and how the
louvres sit in it are numbers the artist's textures were drawn to: the rim on cooling_vent_front.png is
RIM pixels wide, and the louvre's faces sit on the rows of cooling_vent_slat.png named below. Change a
number here and the texture has to be redrawn to match.

The vent is a box open at the front. Behind the opening, four louvres slope down and outward the way a
real vent's do, and behind those is a throat with a heat exchanger at the back of it - copper coils with
rift light between them, which is what the steam comes off. The back of the box is a plate with the shaft
socket in it. One model, facing north; the blockstate turns it to each of the six facings the way
vanilla's observer is turned, which CoolingVentModelTest holds it to.

The shell's faces carry no UVs of their own, so the game maps each from where it is in the block. That
keeps one side texture continuous across the four walls and the back plate - and is why that texture is
drawn the same either way round: the game maps a west face front-to-back and an east face back-to-front.
"""

import json
import math
import os

SIZE = 16
ASSETS = os.path.join("src", "main", "resources", "assets", "aerowarptics")
MODELS = os.path.join(ASSETS, "models", "block")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")

NAME = "cooling_vent"


# The box, in model pixels, facing north: the opening is at z = 0 and the shaft comes in at z = 16.
RIM = 2            # thickness of the four walls round the opening
THROAT = 12        # how far back the opening goes before it meets the back plate
SLATS = 4          # louvres in the opening
SLAT_DEPTH = 3     # front to back, before it is tilted
SLAT_THICK = 1
SLAT_TILT = -22.5  # degrees about x: the front edge drops, the way a louvre sheds rain
SLAT_Z = 2.0       # how far back the middle of each louvre sits

# Where each face of a louvre is on the slat texture: the top in the first SLAT_DEPTH rows, then these.
EDGE_ROW = 4       # the leading edge, then the trailing edge on the row after it
UNDER_ROW = 6      # the underside, SLAT_DEPTH rows of it


def slat_centres():
    """The height of each louvre's middle: evenly down the opening, a slat's pitch apart."""
    opening = SIZE - 2 * RIM
    pitch = opening / SLATS
    return [RIM + pitch * (index + 0.5) for index in range(SLATS)]


# ------------------------------------------------------------------------------------ the model

def wall(low, high, faces):
    """One piece of the shell. No UVs: the game maps each face from where it is in the block."""
    return {"from": list(low), "to": list(high), "faces": {
        name: ({"texture": texture, "cullface": name} if cull else {"texture": texture})
        for name, (texture, cull) in faces.items()}}


def louvre(centre):
    low_y = centre - SLAT_THICK / 2.0
    low_z = SLAT_Z - SLAT_DEPTH / 2.0
    width = SIZE - 2 * RIM
    return {
        "from": [RIM, low_y, low_z],
        "to": [SIZE - RIM, low_y + SLAT_THICK, low_z + SLAT_DEPTH],
        "rotation": {"origin": [SIZE / 2.0, centre, SLAT_Z], "axis": "x", "angle": SLAT_TILT},
        "faces": {
            "up": {"uv": [RIM, 0, RIM + width, SLAT_DEPTH], "texture": "#slat"},
            "north": {"uv": [RIM, EDGE_ROW, RIM + width, EDGE_ROW + SLAT_THICK], "texture": "#slat"},
            "south": {"uv": [RIM, EDGE_ROW + 1, RIM + width, EDGE_ROW + 1 + SLAT_THICK], "texture": "#slat"},
            "down": {"uv": [RIM, UNDER_ROW, RIM + width, UNDER_ROW + SLAT_DEPTH], "texture": "#slat"},
        },
    }


def model():
    far = SIZE - RIM
    elements = [
        # The back plate: the shaft socket outside, the heat exchanger inside.
        wall((0, 0, THROAT), (SIZE, SIZE, SIZE), {
            "south": ("#back", True), "north": ("#throat", False),
            "up": ("#side", True), "down": ("#side", True), "east": ("#side", True), "west": ("#side", True)}),
        # The four walls round the opening, rim outwards.
        wall((0, far, 0), (SIZE, SIZE, THROAT), {
            "north": ("#front", True), "up": ("#side", True), "down": ("#inner", False),
            "east": ("#side", True), "west": ("#side", True)}),
        wall((0, 0, 0), (SIZE, RIM, THROAT), {
            "north": ("#front", True), "down": ("#side", True), "up": ("#inner", False),
            "east": ("#side", True), "west": ("#side", True)}),
        wall((0, RIM, 0), (RIM, far, THROAT), {
            "north": ("#front", True), "west": ("#side", True), "east": ("#inner", False)}),
        wall((far, RIM, 0), (SIZE, far, THROAT), {
            "north": ("#front", True), "east": ("#side", True), "west": ("#inner", False)}),
    ]
    elements += [louvre(centre) for centre in slat_centres()]
    return {
        "parent": "block/block",
        "textures": {
            "side": f"aerowarptics:block/{NAME}_side",
            "front": f"aerowarptics:block/{NAME}_front",
            "back": f"aerowarptics:block/{NAME}_back",
            "inner": f"aerowarptics:block/{NAME}_inner",
            "throat": f"aerowarptics:block/{NAME}_throat",
            "slat": f"aerowarptics:block/{NAME}_slat",
            "particle": f"aerowarptics:block/{NAME}_side",
        },
        "elements": elements,
    }


def check_louvres_fit():
    """A tilted louvre must stay inside the opening it is in, clear of the rim and the block's face."""
    tilt = math.radians(SLAT_TILT)
    for centre in slat_centres():
        for dy in (-SLAT_THICK / 2.0, SLAT_THICK / 2.0):
            for dz in (-SLAT_DEPTH / 2.0, SLAT_DEPTH / 2.0):
                y = centre + dy * math.cos(tilt) - dz * math.sin(tilt)
                z = SLAT_Z + dy * math.sin(tilt) + dz * math.cos(tilt)
                if not (RIM <= y <= SIZE - RIM and 0.0 <= z <= THROAT):
                    raise SystemExit(f"a louvre at {centre} reaches ({y:.2f}, {z:.2f}), outside its opening")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")


def main():
    check_louvres_fit()
    write_json(os.path.join(MODELS, NAME + ".json"), model())
    print(f"  models/block/{NAME}.json")

    # The observer's table: a model whose working face is north, turned to each facing.
    block = f"aerowarptics:block/{NAME}"
    write_json(os.path.join(BLOCKSTATES, NAME + ".json"), {
        "variants": {
            "facing=north": {"model": block},
            "facing=east": {"model": block, "y": 90},
            "facing=south": {"model": block, "y": 180},
            "facing=west": {"model": block, "y": 270},
            "facing=up": {"model": block, "x": 270},
            "facing=down": {"model": block, "x": 90},
        }
    })
    print(f"  blockstates/{NAME}.json")


if __name__ == "__main__":
    main()
