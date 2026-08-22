#!/usr/bin/env python3
"""
Builds the Ponder scene schematics under assets/aerowarptics/ponder/.

Ponder scenes are ordinary Minecraft structure templates: gzipped NBT holding a size, a block-state
palette and a list of positions. Create's own scenes are built by standing in a world with a
schematic wand, which is not something a build can reproduce - so this mod's are written down as
code instead, and this script is the thing that produces them.

That trade is deliberate. Laying a scene out by hand is faster the first time and impossible to
review afterwards, because the result is a binary blob with no history. Here the layouts are
readable, a change to one shows up as a diff, and regenerating is:

    python tools/ponder_schematics.py

Conventions, taken from Create's own scenes so ours sit at the same camera angle and scale:

  * The base plate is a five by five checkerboard of white concrete and snow at y=0, occupying
    x,z in 0..4. `SceneBuilder.configureBasePlate(0, 0, 5)` agrees with that.
  * Scene content starts at y=1.
  * Anything past x=4 or z=4 is off the plate. Create parks the power source out there, because a
    creative motor is how a scene gets rotation without teaching anybody to build one.
  * The origin corner (0, *, 0) is the one nearest the camera.
"""

import gzip
import os
import struct

# ---------------------------------------------------------------------------- nbt

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND = range(11)

# Minecraft 1.21.1. Writing the current version means the game loads these as-is; an older number
# would send them through the data fixers on every load for no reason.
DATA_VERSION = 3955


class Writer:
    def __init__(self):
        self.b = bytearray()

    def u1(self, v):
        self.b += struct.pack(">B", v)

    def i4(self, v):
        self.b += struct.pack(">i", v)

    def string(self, s):
        e = s.encode("utf-8")
        self.b += struct.pack(">H", len(e)) + e

    def compound(self, entries):
        for key, (tid, value) in entries.items():
            self.u1(tid)
            self.string(key)
            self.payload(tid, value)
        self.u1(END)

    def payload(self, tid, value):
        if tid == INT:
            self.i4(value)
        elif tid == STRING:
            self.string(value)
        elif tid == COMPOUND:
            self.compound(value)
        elif tid == LIST:
            element_type, items = value
            self.u1(element_type)
            self.i4(len(items))
            for item in items:
                self.payload(element_type, item)
        else:
            raise ValueError(f"this writer does not handle tag {tid}")


def int_list(values):
    return (LIST, (INT, list(values)))


def write_structure(path, size, palette, blocks):
    """palette: list of (name, {property: value}); blocks: {(x, y, z): palette index}."""
    root = {
        "size": int_list(size),
        "entities": (LIST, (END, [])),
        "blocks": (LIST, (COMPOUND, [
            {"pos": int_list(pos), "state": (INT, state)}
            for pos, state in sorted(blocks.items())
        ])),
        "palette": (LIST, (COMPOUND, [
            ({"Name": (STRING, name)} if not properties else
             {"Name": (STRING, name),
              "Properties": (COMPOUND, {k: (STRING, v) for k, v in sorted(properties.items())})})
            for name, properties in palette
        ])),
        "DataVersion": (INT, DATA_VERSION),
    }

    writer = Writer()
    writer.u1(COMPOUND)
    writer.string("")
    writer.compound(root)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    # mtime=0 so an unchanged scene produces an unchanged file rather than a fresh diff every run.
    with gzip.GzipFile(path, "wb", compresslevel=9, mtime=0) as handle:
        handle.write(bytes(writer.b))
    return len(blocks)


# --------------------------------------------------------------------------- scene

class Scene:
    """A scene under construction: places blocks by name and interns the palette as it goes."""

    def __init__(self, size):
        self.size = size
        self.palette = []
        self.index = {}
        self.blocks = {}

    def _state(self, name, properties):
        key = (name, tuple(sorted(properties.items())))
        if key not in self.index:
            self.index[key] = len(self.palette)
            self.palette.append((name, properties))
        return self.index[key]

    def put(self, x, y, z, name, **properties):
        for axis, value, limit in (("x", x, self.size[0]), ("y", y, self.size[1]), ("z", z, self.size[2])):
            if not 0 <= value < limit:
                raise ValueError(f"{name} at ({x},{y},{z}) is outside the scene: {axis}={value} of {limit}")
        self.blocks[(x, y, z)] = self._state(name, {k: str(v) for k, v in properties.items()})

    def fill(self, x0, y0, z0, x1, y1, z1, name, **properties):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.put(x, y, z, name, **properties)

    def base_plate(self, size=5, depth=None):
        """Create's checkerboard, so the eye can read distance across the floor.

        `depth` defaults to `size`, which is what every square scene wants; the chute's is a long
        thin bench and paving it square would put half the plate behind the camera.
        """
        for x in range(size):
            for z in range(depth if depth is not None else size):
                self.put(x, 0, z,
                         "minecraft:white_concrete" if (x + z) % 2 == 0 else "minecraft:snow_block")

    def motor_line(self, x, y, z, length, axis, facing):
        """A run of shaft ending in a creative motor, laid along `axis` in ascending order."""
        step = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}[axis]
        for i in range(length):
            self.put(x + step[0] * i, y + step[1] * i, z + step[2] * i,
                     "create:shaft", axis=axis, waterlogged="false")
        self.put(x + step[0] * length, y + step[1] * length, z + step[2] * length,
                 "create:creative_motor", facing=facing)

    def save(self, root, name):
        path = os.path.join(root, *name.split("/")) + ".nbt"
        count = write_structure(path, self.size, self.palette, self.blocks)
        print(f"  {name+'.nbt':36} {self.size[0]}x{self.size[1]}x{self.size[2]}"
              f"  {count:3d} blocks  {len(self.palette):2d} states")


# --------------------------------------------------------------------------- scenes

SIPHON = "aerowarptics:spatial_siphon"
ANCHOR = "aerowarptics:warp_anchor"
ASTROLABE = "aerowarptics:astrolabe"
DRIVE = "aerowarptics:rift_drive_mk_ii"
PROBE = "aerowarptics:rift_probe"
CHUTE = "aerowarptics:rift_chute"
GATE = "aerowarptics:rift_gate"
FRAME = "aerowarptics:rift_gate_frame"


def spatial_siphon():
    """The siphon, and somewhere for what it catches to go."""
    scene = Scene((6, 4, 6))
    scene.base_plate()
    scene.put(1, 1, 2, SIPHON)
    # A pipe west into the siphon, east into the tank, so the drain direction reads straight off it.
    scene.put(2, 1, 2, "create:fluid_pipe",
              north="false", south="false", east="true", west="true", up="false", down="false",
              waterlogged="false")
    scene.put(3, 1, 2, "create:fluid_tank", bottom="true", top="false", shape="window")
    scene.put(3, 2, 2, "create:fluid_tank", bottom="false", top="true", shape="window")
    return scene


def warp_anchor():
    """One anchor, and the room around it that a hull has to fit into."""
    scene = Scene((6, 5, 6))
    scene.base_plate()
    scene.put(2, 1, 2, ANCHOR, facing="north", waterlogged="false")
    return scene


def astrolabe():
    """Nine cells. The scene forms them itself, so they start unformed."""
    scene = Scene((6, 4, 6))
    scene.base_plate()
    scene.fill(1, 1, 1, 3, 1, 3, ASTROLABE, formed="false")
    return scene


def rift_chute():
    """Two chutes, each fed through a funnel, with a belt at either end.

    Both on the ground, because a Ponder level has no airship in it - and the lesson here is the
    pairing, the funnel and the essence, none of which needs a ship to demonstrate.
    """
    scene = Scene((10, 4, 6))
    scene.base_plate(9, 5)
    # Sending side: a belt running east into a chute that faces the belt.
    scene.put(1, 1, 2, "create:belt", facing="east", part="start", slope="horizontal", casing="false")
    scene.put(2, 1, 2, "create:belt", facing="east", part="end", slope="horizontal", casing="false")
    # A funnel between the belt and the chute: a chute is an inventory, not a belt attachment.
    scene.put(3, 2, 2, "create:andesite_funnel", facing="up", powered="false", waterlogged="false")
    scene.put(3, 1, 2, CHUTE)
    # Receiving side, set apart so the gap between them is the thing the rift crosses.
    scene.put(6, 1, 2, CHUTE)
    scene.put(7, 1, 2, "create:belt", facing="east", part="start", slope="horizontal", casing="false")
    scene.put(8, 1, 2, "create:belt", facing="east", part="end", slope="horizontal", casing="false")
    # Essence for the sending end, which is the end that pays.
    scene.put(3, 1, 1, "create:fluid_pipe",
              north="true", south="true", east="false", west="false", up="false", down="false",
              waterlogged="false")
    scene.put(3, 1, 0, "create:fluid_tank", bottom="true", top="true", shape="window")
    return scene


def rift_probe():
    """A probe with a pipe of essence reaching it, since a sounding is paid for rather than free."""
    scene = Scene((6, 4, 6))
    scene.base_plate()
    scene.put(1, 1, 2, PROBE)
    scene.put(2, 1, 2, "create:fluid_pipe",
              north="false", south="false", east="true", west="true", up="false", down="false",
              waterlogged="false")
    scene.put(3, 1, 2, "create:fluid_tank", bottom="true", top="false", shape="window")
    scene.put(3, 2, 2, "create:fluid_tank", bottom="false", top="true", shape="window")
    return scene


def rift_drive():
    """A drive with rotation reaching it from off the plate."""
    scene = Scene((6, 5, 6))
    scene.base_plate()
    # Facing south puts the drive's shaft axis along Z, which is where the power comes in from.
    scene.put(2, 1, 2, DRIVE, facing="south")
    scene.motor_line(2, 1, 3, 2, "z", "north")
    return scene


def rift_gate():
    """
    A five by five ring standing across X, with a three by three opening.

    <p>The controller sits in the bottom row of the ring: it counts as frame, so it may be anywhere
    in the circle, and the bottom is where a shaft can reach it without crossing the opening.
    """
    scene = Scene((6, 6, 6))
    scene.base_plate()

    ring_z = 2
    for x in range(0, 5):
        for y in range(1, 6):
            edge = x in (0, 4) or y in (1, 5)
            if edge:
                scene.put(x, y, ring_z, FRAME)
    # The controller replaces one frame block, facing along Z so rotation arrives from behind.
    scene.put(2, 1, ring_z, GATE, facing="south")
    scene.motor_line(2, 1, ring_z + 1, 1, "z", "north")
    return scene


SCENES = {
    "spatial_siphon": spatial_siphon,
    "warp_anchor": warp_anchor,
    "astrolabe": astrolabe,
    "rift_probe": rift_probe,
    "rift_chute": rift_chute,
    "rift_drive": rift_drive,
    "rift_gate": rift_gate,
}


def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        "..", "src", "main", "resources", "assets", "aerowarptics", "ponder")
    root = os.path.normpath(root)
    print(f"writing scenes into {root}")
    for name, build in SCENES.items():
        build().save(root, name)


if __name__ == "__main__":
    main()
