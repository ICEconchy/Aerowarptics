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


def write_structure(path, size, palette, blocks, nbt=None):
    """palette: list of (name, {property: value}); blocks: {(x, y, z): palette index}; nbt: {(x, y, z): {key:
    string}} for block entities that need data of their own - a barrel's loot table - written as strings."""
    nbt = nbt or {}

    def entry(pos, state):
        block = {"pos": int_list(pos), "state": (INT, state)}
        if pos in nbt:
            block["nbt"] = (COMPOUND, {key: (STRING, value) for key, value in sorted(nbt[pos].items())})
        return block

    root = {
        "size": int_list(size),
        "entities": (LIST, (END, [])),
        "blocks": (LIST, (COMPOUND, [entry(pos, state) for pos, state in sorted(blocks.items())])),
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
        self.nbt = {}

    def _state(self, name, properties):
        key = (name, tuple(sorted(properties.items())))
        if key not in self.index:
            self.index[key] = len(self.palette)
            self.palette.append((name, properties))
        return self.index[key]

    def put(self, x, y, z, name, /, **properties):
        for axis, value, limit in (("x", x, self.size[0]), ("y", y, self.size[1]), ("z", z, self.size[2])):
            if not 0 <= value < limit:
                raise ValueError(f"{name} at ({x},{y},{z}) is outside the scene: {axis}={value} of {limit}")
        self.blocks[(x, y, z)] = self._state(name, {k: str(v) for k, v in properties.items()})
        self.nbt.pop((x, y, z), None)

    def data(self, x, y, z, **tags):
        """Block entity data for what stands at a position - put it there first. Strings only, which is all a
        loot table needs."""
        if (x, y, z) not in self.blocks:
            raise ValueError(f"nothing at ({x},{y},{z}) to give data to")
        self.nbt[(x, y, z)] = dict(tags)

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
        count = write_structure(path, self.size, self.palette, self.blocks, self.nbt)
        print(f"  {name+'.nbt':36} {self.size[0]}x{self.size[1]}x{self.size[2]}"
              f"  {count:3d} blocks  {len(self.palette):2d} states")


# --------------------------------------------------------------------------- scenes

SIPHON = "aerowarptics:spatial_siphon"
ANCHOR = "aerowarptics:warp_anchor"
ASTROLABE = "aerowarptics:astrolabe"
DRIVE = "aerowarptics:rift_drive_mk_ii"
PROBE = "aerowarptics:rift_probe"
CHUTE = "aerowarptics:rift_chute"
MODULATOR = "aerowarptics:rift_modulator"
COUPLER = "aerowarptics:rift_coupler"
GATE = "aerowarptics:rift_gate"
FISSURE = "aerowarptics:rift_fissure"
FRAME = "aerowarptics:rift_gate_frame"
EXTENDER = "aerowarptics:drive_extender"
STABILISER = "aerowarptics:spatial_stabiliser"
DETECTOR = "aerowarptics:spatial_wave_detector"
BRAZIER = "aerowarptics:rift_brazier"
CRADLE = "aerowarptics:collapse_cradle"
VENT = "aerowarptics:cooling_vent"


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


def drive_extender():
    """A drive buried in the middle of a three-cube of extenders, with power arriving at one face.

    The drive goes in the centre rather than on a face on purpose: it is the arrangement players do
    not believe works, and the shaft coming in at the bottom of the cube - two blocks from the drive
    and touching nothing but casing - is the whole reason a formed extender is a kinetic relay.

    The extenders are written unformed, so the scene can complete the cube itself and the forming is
    something the player watches rather than something already done when the lights come up.
    """
    scene = Scene((6, 5, 6))
    scene.base_plate()
    for x in range(1, 4):
        for y in range(1, 4):
            for z in range(1, 4):
                if (x, y, z) != (2, 2, 2):
                    scene.put(x, y, z, EXTENDER, formed="false")
    scene.put(2, 2, 2, DRIVE, facing="south")
    scene.motor_line(2, 1, 4, 1, "z", "north")
    return scene


def spatial_stabiliser():
    """A stabiliser bolted to a drive, with a lever on top of it.

    No motor, unlike every other drive scene here. A stabiliser needs the drive beside it and nothing
    else - it never asks whether the drive is turning - and the lesson is the switch, so a shaft would
    only invite the guess that one is needed. The lever sits on the machine's own cap so that what the
    scene throws is unmistakably this machine's switch.
    """
    scene = Scene((6, 5, 6))
    scene.base_plate()
    scene.put(2, 1, 2, DRIVE, facing="south")
    scene.put(1, 1, 2, STABILISER, powered="false")
    scene.put(1, 2, 2, "minecraft:lever", face="floor", facing="south", powered="false")
    return scene


def cooling_vent():
    """A turning drive with four coolers round it: three vents and a block of ice.

    Four, because four is how many count - a fifth would be a cooler doing nothing in a scene that could
    not say which. Each vent is backed onto the drive with its louvres away from it, the way one lands
    when it is placed on a drive, which is also the arrangement that needs no shaft of its own: the motor
    turns the drive and the drive turns the vents. The south face is the motor's and the north is the
    ice's, so the vents take west, east and the top.
    """
    scene = Scene((6, 5, 6))
    scene.base_plate()
    scene.put(2, 1, 2, DRIVE, facing="south")
    scene.motor_line(2, 1, 3, 1, "z", "north")
    scene.put(1, 1, 2, VENT, facing="west")
    scene.put(3, 1, 2, VENT, facing="east")
    scene.put(2, 2, 2, VENT, facing="up")
    scene.put(2, 1, 1, "minecraft:ice")
    return scene


def spatial_wave_detector():
    """A detector wired to a lamp, and a Warp Anchor across the plate for something to arrive at.

    Everything ships quiet - detector at nought, dust unpowered, lamp dark - because the scene sets
    each block's signal itself, by position, to show a strong reading and then a weak one. The dust
    runs straight east from the detector so the fall-off along it reads left to right.

    No airship and no gate: a Ponder level has neither, and the lesson is what the detector does when
    something arrives, not what arrived. The anchor stands in only as the place a ship would be sent.
    """
    scene = Scene((6, 5, 6))
    scene.base_plate()
    scene.put(1, 1, 1, DETECTOR, power=0)
    for x in (2, 3):
        scene.put(x, 1, 1, "minecraft:redstone_wire",
                  east="side", west="side", north="none", south="none", power=0)
    scene.put(4, 1, 1, "minecraft:redstone_lamp", lit="false")
    scene.put(3, 1, 3, ANCHOR, facing="north", waterlogged="false")
    return scene


def rift_modulator():
    """A drive under power, a Modulator bolted to its side, and a trickle of essence reaching it."""
    scene = Scene((6, 5, 6))
    scene.base_plate()
    scene.put(2, 1, 2, DRIVE, facing="south")
    scene.motor_line(2, 1, 3, 2, "z", "north")
    # Bolted against the drive's own west face, facing the same way the drive does - the facing it
    # took from the drive the moment it was placed.
    scene.put(1, 1, 2, MODULATOR, facing="south")
    scene.put(1, 1, 1, "create:fluid_pipe",
              north="true", south="true", east="false", west="false", up="false", down="false",
              waterlogged="false")
    scene.put(1, 1, 0, "create:fluid_tank", bottom="true", top="false", shape="window")
    scene.put(1, 2, 0, "create:fluid_tank", bottom="false", top="true", shape="window")
    return scene


def rift_coupler():
    """Two drives under power, a flagship's and an escort's, each with a Rift Coupler on top.

    Side by side across the plate, four blocks apart, because a flotilla is ships kept apart: the
    escort's slot is shown as an outline round its own drive and the harmonic tether as a line between
    the two couplers. No airship, as in every scene here - a Ponder level has none - so the scene teaches
    the order of things, which is what nothing else in the game spells out.
    """
    scene = Scene((7, 5, 7))
    scene.base_plate(7)
    for x in (1, 5):
        scene.put(x, 1, 1, DRIVE, facing="south")
        scene.motor_line(x, 1, 2, 2, "z", "north")
        # Standing on the drive, facing the way it does: the facing it takes from the drive when placed.
        scene.put(x, 2, 1, COUPLER, facing="south")
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


def rift_gate_rail():
    """
    The same ring as `rift_gate`, with a railway run into the bottom row of its opening.

    Its own schematic rather than the shared gate one, because a lane needs scenery the other two
    scenes have no use for: the opening's bottom row sits a block above the ground a gate stands on,
    so a track into it runs along a low bed, and the bed and the track would be two more things for
    the assembly and dialling scenes to keep hidden.

    The ring stands across X at z=4, which leaves four cells of approach in front of it and room for
    the controller's shaft behind. The lane cell is (3, 2, 4) - the middle of the bottom row, directly
    above the controller - and the track runs up to it along z, so a straight `zo` track is the whole
    run. Nothing stands in the opening: the storyboard puts the pane in itself, at the beat the gate
    dials for a train.
    """
    scene = Scene((7, 6, 7))
    scene.base_plate(7)

    ring_z = 4
    for x in range(1, 6):
        for y in range(1, 6):
            if x in (1, 5) or y in (1, 5):
                scene.put(x, y, ring_z, FRAME)
    # The controller in the bottom row, under the lane cell, with its shaft behind the ring.
    scene.put(3, 1, ring_z, GATE, facing="south")
    scene.motor_line(3, 1, ring_z + 1, 1, "z", "north")

    # The lane: straight track on a low bed, running level into the bottom row of the opening.
    for z in range(0, ring_z):
        scene.put(3, 1, z, "create:railway_casing")
        scene.put(3, 2, z, "create:track", shape="zo", turn="false", waterlogged="false")
    return scene


def rift_fissure():
    """A scar with a tear standing in it, and a vessel to close it with.

    The tear is a real Rift Fissure block, which renders nothing at all - that being the entire point
    of one. The scene does not draw a stand-in for it, for the same reason the warp scenes do not draw
    an airship: a lie about what a player will see is worse than an honest gap, and what this lesson
    is actually about is the goggles and the siphon, both of which are here.
    """
    scene = Scene((6, 5, 6))
    scene.base_plate()

    # What is left of somebody else's gate, broken at two corners so it reads as a ruin rather than
    # as a gate somebody is still building.
    for x, y in ((1, 1), (2, 1), (1, 2), (3, 2), (1, 3), (3, 3)):
        scene.put(x, y, 2, FRAME)
    scene.put(0, 1, 2, "minecraft:cracked_stone_bricks")
    scene.put(4, 1, 1, "minecraft:mossy_stone_bricks")

    scene.put(2, 2, 2, FISSURE)
    # Near enough to draw from once the scene reveals it: a siphon reaches a few blocks.
    scene.put(1, 1, 4, SIPHON)
    return scene


def rift_beacon():
    """A drive under power, and open ground to call it to.

    No airship, because a Ponder level has none - so the scene teaches the two gestures, which are
    the part a player can act on and the part nothing else explains.
    """
    scene = Scene((6, 5, 6))
    scene.base_plate()
    scene.put(1, 1, 1, DRIVE, facing="south")
    scene.motor_line(1, 1, 2, 3, "z", "north")
    return scene


def rift_brazier():
    """A fan blowing across a brazier onto a depot, and a lever beside the brazier.

    Everything ships quiet - the brazier unlit, the lever off - because the scene lights it by position:
    the lever at (2, 1, 1) and the brazier beside it at (2, 1, 2). No shaft to the fan: a Ponder level does not
    carry the air, and the lesson is the catalyst, not the fan.
    """
    scene = Scene((6, 4, 6))
    scene.base_plate()
    scene.put(0, 1, 2, "create:encased_fan", facing="east")
    scene.put(2, 1, 2, BRAZIER, powered="false", lit="false")
    scene.put(2, 1, 1, "minecraft:lever", face="floor", facing="south", powered="false")
    scene.put(4, 1, 2, "create:depot")
    return scene


def collapse_cradle():
    """A cradle in the middle, a ring of eight depots round it, and a stabiliser at each edge of the plate.

    The cradle is three blocks each way: its master at (3, 1, 3), in the middle of its floor, and the other
    twenty-six its parts, each told where in the machine it stands - which is all a part knows.

    The stabilisers ship switched off, at (0, 1, 3), (6, 1, 3), (3, 1, 0) and (3, 1, 6), because the scene
    switches them on by position - evenly spaced, which is the lesson.
    """
    scene = Scene((7, 5, 7))
    scene.base_plate(7)
    for dx in (-1, 0, 1):
        for dy in (0, 1, 2):
            for dz in (-1, 0, 1):
                if (dx, dy, dz) != (0, 0, 0):
                    scene.put(3 + dx, 1 + dy, 3 + dz, CRADLE + "_part", x=dx + 1, y=dy, z=dz + 1)
    scene.put(3, 1, 3, CRADLE)
    for x, z in ((1, 1), (3, 1), (5, 1), (1, 3), (5, 3), (1, 5), (3, 5), (5, 5)):
        scene.put(x, 1, z, "create:depot")
    for x, z in ((0, 3), (6, 3), (3, 0), (3, 6)):
        scene.put(x, 1, z, STABILISER, powered="false")
    return scene


def rift_wellhead():
    """A three-block frame round a fissure, its collar in the south side, and a stabiliser either side of it.

    The frame is the one a player builds, a moment before it forms: Rift Casing for its edges and corners, Warp
    Glass in the middle of each face, the fissure at (3, 2, 3) and the collar at (3, 2, 4) facing south, out of
    the machine. A three-cube's faces are all middle, so its glass is six blocks less the collar's.

    The stabilisers ship switched off, at (0, 1, 3) and (6, 1, 3), because the scene switches them on by position.
    """
    scene = Scene((7, 5, 7))
    scene.base_plate(7)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                faces = sum(1 for d in (dx, dy, dz) if d != 0)
                if faces == 0:
                    scene.put(3, 2, 3, FISSURE, harnessed="false")
                elif (dx, dy, dz) == (0, 0, 1):
                    scene.put(3, 2, 4, "aerowarptics:rift_collar", facing="south", formed="false")
                else:
                    scene.put(3 + dx, 2 + dy, 3 + dz,
                              "aerowarptics:warp_glass" if faces == 1 else "aerowarptics:rift_casing")
    for x in (0, 6):
        scene.put(x, 1, 3, STABILISER, powered="false")
    return scene


def rift_sunderer():
    """
    The gate ring's five by five frame with a three by three opening, standing across X - and a Rift Sunderer in
    the bottom of it where a gate's controller would go, facing south, its one shaft on its back to the north.

    The scene bursts some of the frame itself, so the schematic is the ring a player builds, whole.
    """
    scene = Scene((6, 6, 6))
    scene.base_plate()

    ring_z = 2
    for x in range(0, 5):
        for y in range(1, 6):
            if x in (0, 4) or y in (1, 5):
                scene.put(x, y, ring_z, FRAME)
    scene.put(2, 1, ring_z, "aerowarptics:rift_sunderer", facing="south", charge="0")
    scene.put(2, 1, ring_z - 1, "create:shaft", axis="z", waterlogged="false")
    scene.put(2, 1, ring_z - 2, "create:creative_motor", facing="south")
    return scene


SCENES = {
    "spatial_siphon": spatial_siphon,
    "warp_anchor": warp_anchor,
    "astrolabe": astrolabe,
    "rift_probe": rift_probe,
    "rift_chute": rift_chute,
    "rift_modulator": rift_modulator,
    "rift_coupler": rift_coupler,
    "rift_drive": rift_drive,
    "drive_extender": drive_extender,
    "spatial_stabiliser": spatial_stabiliser,
    "cooling_vent": cooling_vent,
    "spatial_wave_detector": spatial_wave_detector,
    "rift_gate": rift_gate,
    "rift_gate_rail": rift_gate_rail,
    "rift_fissure": rift_fissure,
    "rift_beacon": rift_beacon,
    "rift_brazier": rift_brazier,
    "collapse_cradle": collapse_cradle,
    "rift_wellhead": rift_wellhead,
    "rift_sunderer": rift_sunderer,
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
