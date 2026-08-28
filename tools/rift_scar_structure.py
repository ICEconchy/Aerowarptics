#!/usr/bin/env python3
"""
Builds the Rift Scar structure templates under data/aerowarptics/structure/.

    python tools/rift_scar_structure.py

A Rift Scar is where somebody tried to open a gate and space did not close again afterwards. Three
variants, one picked at random per site: the ring they were building, the vault they built it in, and
the mast they surveyed from. Each has a Rift Fissure hanging in it, which is invisible without Rift
Infused Goggles - so the ruin is the sign, and the goggles are what turn the sign into a place.

Written as code for the same reason the Ponder scenes are: a structure laid out by hand is a binary
blob nobody can review, and a change to one shows up in a diff as "the file changed". Here you can
read what a ruin is made of, and the NBT writer is the one the Ponder tool already uses rather than a
second copy of it that will eventually disagree.

Two conventions worth knowing before editing:

  * A position this script never writes to is left alone when the structure generates. That is what
    keeps a ruin from stamping a cube of air into a hillside - only the blocks named here are placed,
    so the ground fills in around them.
  * Which means air has to be asked for. Where a room needs to be hollow inside a hill, the air is
    placed deliberately, block by block, like everything else.
"""

import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# The NBT writer and the palette-interning scene builder, shared with the Ponder schematics.
from ponder_schematics import Scene  # noqa: E402

OUT = os.path.join("src", "main", "resources", "data", "aerowarptics", "structure", "rift_scar")

FISSURE = "aerowarptics:rift_fissure"
FRAME = "aerowarptics:rift_gate_frame"

AIR = "minecraft:air"
BRICK = "minecraft:stone_bricks"
CRACKED = "minecraft:cracked_stone_bricks"
MOSSY = "minecraft:mossy_stone_bricks"
CHISELLED = "minecraft:chiseled_stone_bricks"
SLAB = "minecraft:stone_brick_slab"
DEEPSLATE = "minecraft:polished_deepslate"
DEEP_BRICK = "minecraft:deepslate_bricks"
DEEP_CRACKED = "minecraft:cracked_deepslate_bricks"
OBSIDIAN = "minecraft:obsidian"
AMETHYST = "minecraft:amethyst_block"
BUDDING = "minecraft:budding_amethyst"
BRASS = "create:brass_block"
IRON = "create:industrial_iron_block"
CASING = "create:brass_casing"
SHINGLES = "create:oxidized_copper_shingles"
BARS = "create:brass_bars"

# Ruin, in rough order of how badly weathered it is. Picking from a weighted list rather than one
# block is what stops a wall reading as a wall somebody built last week.
MASONRY = [BRICK] * 3 + [CRACKED] * 4 + [MOSSY] * 3
DEEP_MASONRY = [DEEP_BRICK] * 3 + [DEEP_CRACKED] * 4 + [DEEPSLATE] * 3


class Ruin(Scene):
    """A scene with the handful of shapes a ruin is made of."""

    def __init__(self, size, seed):
        super().__init__(size)
        self.rng = random.Random(seed)

    def weathered(self, x, y, z, palette, missing=0.0):
        """One block of masonry, or nothing at all - which is what makes it a ruin."""
        if self.rng.random() < missing:
            return
        self.put(x, y, z, self.rng.choice(palette))

    def wall(self, x0, z0, x1, z1, y0, y1, palette, missing=0.0):
        """The four sides of a rectangle, hollow, between two heights."""
        for y in range(y0, y1 + 1):
            # Higher courses have fallen in more than lower ones, which is how a wall actually goes.
            decay = missing * (0.4 + 0.8 * (y - y0) / max(1, y1 - y0))
            for x in range(x0, x1 + 1):
                self.weathered(x, y, z0, palette, decay)
                self.weathered(x, y, z1, palette, decay)
            for z in range(z0 + 1, z1):
                self.weathered(x0, y, z, palette, decay)
                self.weathered(x1, y, z, palette, decay)

    def floor(self, x0, z0, x1, z1, y, palette, missing=0.0):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.weathered(x, y, z, palette, missing)

    def hollow(self, x0, y0, z0, x1, y1, z1):
        """Air, placed on purpose: a room that is a room even inside a hill."""
        self.fill(x0, y0, z0, x1, y1, z1, AIR)

    def ring(self, cx, cy, cz, radius, block, gaps=0.0, plane="xy"):
        """A ring of blocks standing on end, with pieces of it missing.

        Drawn by angle rather than by a circle test, so a small radius still closes up - the same
        reason the handbook's diagrams sample their arcs that way.
        """
        import math
        placed = set()
        steps = max(16, int(radius * 16))
        for step in range(steps):
            angle = step / steps * math.tau
            a = round(math.cos(angle) * radius)
            b = round(math.sin(angle) * radius)
            spot = (cx + a, cy + b, cz) if plane == "xy" else (cx, cy + b, cz + a)
            if spot in placed:
                continue
            placed.add(spot)
            if self.rng.random() < gaps:
                continue
            self.put(spot[0], spot[1], spot[2], block)
        return placed

    def rubble(self, x0, z0, x1, z1, y, palette, chance=0.3):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if self.rng.random() < chance:
                    self.put(x, y, z, self.rng.choice(palette))


# ------------------------------------------------------------------------- the ruins

def ring_site():
    """The gate they were building, and the hole where it went wrong.

    Read from the outside this is a broken circle of Rift Gate Frame standing in a paved yard, which
    is a thing a player of this mod recognises immediately - and the one piece of it they cannot see
    is the reason it was abandoned.
    """
    ruin = Ruin((13, 10, 13), seed=0x5CA1)

    ruin.floor(1, 1, 11, 11, 1, MASONRY, missing=0.35)
    ruin.floor(3, 3, 9, 9, 1, [BRICK, CHISELLED, IRON], missing=0.15)
    ruin.hollow(2, 2, 2, 10, 8, 10)

    # The ring itself, standing across the yard, with a third of it gone.
    ruin.ring(6, 5, 6, 3.6, FRAME, gaps=0.3, plane="xy")
    ruin.put(6, 2, 6, CASING)
    ruin.put(6, 1, 6, OBSIDIAN)

    # What is left of the gantry that held it up.
    for z in (4, 8):
        ruin.put(2, 2, z, IRON)
        ruin.put(2, 3, z, BARS)
        ruin.put(10, 2, z, IRON)
    ruin.put(2, 4, 6, BRASS)

    ruin.rubble(2, 2, 10, 10, 2, [SLAB, CRACKED, MOSSY], chance=0.18)

    # The tear, hanging in the middle of the ring where the aperture should have been.
    ruin.put(6, 5, 6, FISSURE)
    return ruin


def vault_site():
    """The room they worked in, sunk into the ground with its roof fallen in."""
    ruin = Ruin((13, 11, 13), seed=0xBEEF)

    ruin.floor(2, 2, 10, 10, 1, DEEP_MASONRY, missing=0.1)
    ruin.wall(2, 2, 10, 10, 2, 6, MASONRY, missing=0.22)
    ruin.hollow(3, 2, 3, 9, 6, 9)

    # A roof over most of it. The open corner is what lets a player in, and what lets them see there
    # is a room here at all.
    for x in range(2, 11):
        for z in range(2, 11):
            if x >= 8 and z >= 8:
                continue
            ruin.weathered(x, 7, z, [SHINGLES] * 4 + [CRACKED] * 2, missing=0.18)

    # The plinth, and what is left of the machine that stood on it.
    ruin.fill(5, 2, 5, 7, 2, 7, OBSIDIAN)
    ruin.put(6, 3, 6, CASING)
    ruin.put(5, 3, 5, AMETHYST)
    ruin.put(7, 3, 7, AMETHYST)
    ruin.put(5, 3, 7, BUDDING)
    ruin.put(6, 2, 3, BRASS)
    ruin.put(6, 2, 9, IRON)

    ruin.rubble(3, 3, 9, 9, 3, [SLAB, MOSSY], chance=0.12)

    # Two of them here, one over the plinth and one against the wall it tore through.
    ruin.put(6, 5, 6, FISSURE)
    ruin.put(4, 4, 8, FISSURE)
    return ruin


def mast_site():
    """A survey mast on open ground. The smallest of the three, and the easiest to walk past."""
    ruin = Ruin((11, 12, 11), seed=0x5EED)

    ruin.floor(3, 3, 7, 7, 1, MASONRY, missing=0.25)
    ruin.hollow(3, 2, 3, 7, 9, 7)

    # The mast: iron up to a brass head, leaning where the ground gave.
    for y in range(2, 8):
        ruin.put(5, y, 5, IRON if y < 6 else BRASS)
    ruin.put(5, 8, 5, CASING)
    ruin.put(4, 4, 5, BARS)
    ruin.put(6, 5, 5, BARS)

    # Four footings, one of them thrown clear.
    for x, z in ((3, 3), (7, 3), (3, 7)):
        ruin.put(x, 2, z, CHISELLED)
        ruin.put(x, 1, z, DEEPSLATE)
    ruin.put(8, 2, 8, CRACKED)

    ruin.rubble(3, 3, 7, 7, 2, [SLAB, MOSSY, CRACKED], chance=0.2)

    # High up, level with the head of the mast: the thing it was built to look at.
    ruin.put(5, 9, 5, FISSURE)
    return ruin


def main():
    print("rift scar templates:")
    for name, builder in (("ring", ring_site), ("vault", vault_site), ("mast", mast_site)):
        ruin = builder()
        fissures = sum(1 for index in ruin.blocks.values()
                       if ruin.palette[index][0] == FISSURE)
        ruin.save(os.path.dirname(OUT), "rift_scar/" + name)
        print(f"    {fissures} fissure(s)")


if __name__ == "__main__":
    main()
