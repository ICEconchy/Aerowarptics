# AeroWarptics

**Fold space. Move the whole ship.**

AeroWarptics is a Create: Aeronautics addon. Build a **Rift Drive** into a vessel, spin it with
rotational force, register **Warp Anchors** at the places you want to reach, and the entire airship —
hull, machinery, cargo and crew — travels through a rift to the far end.

A warp is **flown, not cut to**. The ship makes a run at the aperture, goes *through* it, keeps flying
down the throat behind it, and comes back out of a second aperture at the destination still moving.
You stay on the deck for all of it.

---

## The machines

### Rift Drive

A Create kinetic machine in four tiers, plus a creative one. It takes a shaft on the axis it faces,
draws stress, and needs a minimum RPM before it will charge at all.

| Tier | Min / optimal RPM | Stress | Range | Charge | Cooldown |
| --- | --- | --- | --- | --- | --- |
| Mk I | 64 / 128 | 16 su | 4,000 | 120 s | 120 s |
| Mk II | 96 / 192 | 24 su | 24,000 | 90 s | 90 s |
| Mk III | 128 / 256 | 32 su | 120,000 | 70 s | 70 s |
| Singularity | 192 / 256 | 48 su | 2,000,000 | 60 s | 300 s |
| Creative | 1 | none | 100,000,000 | instant | none |

Every number is a config default, not a constant.

It is built as a **tesseract**: a cage the size of the block with a second, smaller cage inside it,
joined corner to corner by eight diagonals. The rift hangs in the middle of the inner cage, with two
toothed rings turning around it on crossed axes like the rings of an armillary sphere. The harder the
drive is driven the faster everything turns, and the rift grows as the charge builds — so a drive
that's ready to go looks it from across a hangar.

The drive's screen is a **diagnostic panel and nothing else**: speed against requirement, stress,
charge, hull mass, bow setting, and a checklist of everything a jump still needs, in the order the
server checks them. There is no destination list and no launch button — a drive is a machine, not a
control. You set a course at a chart table and fire it with a redstone signal. What the console is for
is answering *"why has this not gone anywhere"*, which it does by naming the condition that is missing.

A **Rift Modulator** bolted beside the drive lets its pilot choose the rift's core and rim colour, its
theme and its intensity instead of the tier's own — sixteen swatches each (left click for the core,
right click for the rim), blended across the aperture's face rather than one flat tint, five themes, a
25%–200% strength dial, applied to the aperture, the corridor and the exit shockwave. It runs on a
trickle of Rift Essence spent only while the drive is actually warping, and falls back to the tier's own
colours the moment it runs dry or is taken away.

**A theme is geometry, not a palette.** Each changes what stands around the aperture for its whole
life, how it winds up before the hole exists, and how the pane comes apart:

- **Standard** — struck glass. Unchanged.
- **Clockwork** — a toothed ring gear with three counter-rotating satellites meshing outside it, and a
  working clock face with hands across the aperture. Opens as an iris: ten blades sweep aside.
- **Arcane** — twenty-four written glyphs circling the rim and a seven-pointed figure inscribed on the
  face. The circle is drawn, charged, and discharged inward.
- **Ember** — a mouth. Fangs bite inward over the rim and breathe; seven eyes watch from outside it,
  blinking on their own timers, pupils following you as you move.
- **Starlight** — three orbits genuinely tilted out of the aperture's plane, carrying ring bands and
  four planets that pass behind the rift and are hidden by it.

All of it is drawn in the colours the pilot picked, untinted — themes separate themselves by shape and
motion, never by overriding a chosen hue.

### Warp Anchor

A destination. Place one, name it, make it public or private, group it under a network label, switch
it on and off. The record lives in a server-wide file, so an anchor in an unloaded chunk is still a
valid destination — which is the entire point of long-range travel.

### Astrolabe Cartography Table

Nine blocks in a three-by-three square. Until all nine are square it is a stack of loose brass panels;
complete it and it becomes one table with one chart.

A formed table projects a **relief map of where the ship actually is** above itself — eighty blocks
square, one sample per block, given height and lit by a travelling scan line. Its chart lists every
reachable anchor with distance, cost, whether it is in range, and a **top-down survey of the ground
around the selected one**, shaded by vanilla's own map rules. You pick a destination looking at the
terrain you would arrive in.

### Rift Probe

Every other destination in this mod is a place somebody carried a block to, which makes the map a
closed loop: you can only travel to where you have already walked. The probe opens it.

Point the dial at one of eight directions, set a distance, and **scan ahead**. The probe tears a rift
open at that spot, holds the far ground long enough to read it, and brings back the same survey the
Astrolabe draws of an anchor — coastline, height, how much of the picture actually arrived. Send the
ship there on exactly that evidence.

The progress bar is the server generating the world. If it has not finished in time, the probe reads
what is there and tells you the map is only partly complete, rather than lying about it or waiting
forever.

### Rift Gate

**A doorway you build the size you need, and drive through.**

Lay a closed ring of **Rift Gate Frame** standing on end and put a **Rift Gate** controller anywhere in
it. The opening inside is found the way a nether portal's is — flood filled inward, bounded by frame —
so its size and proportions are yours. Feed the controller a shaft and some Rift Essence, name it, and
dial another gate.

Anything crossing the plane comes out the far side: **on foot, on a mob, or in an Aeronautics land
vehicle**. A car is a Sable sub-level exactly as an airship is, so it goes through on the same code
that carries a hull across a warp — passengers and cargo included. Build a bigger ring to pass a
bigger machine.

**Only the end that dialled pays.** Essence opens the connection, rotation holds it, both scaled by
the size of the doorway — and the gate at the other end needs nothing but to exist. A destination gate
is a doorway you build at a mine and walk away from. A gate standing dark draws no stress at all.

A connected gate fills its ring with **Rift Portal**: a pane of shimmering violet that stands in the
world like a nether portal, lights the room around it, and takes the shape of whatever ring you built.
Walk into it and the gate does the rest.

Two gates are rarely the same size or the same way round, so a crossing is carried over as a
**fraction of the opening** and a **rotation** of everything else: enter the top left of a large gate
and you leave by the top left of a small one, with momentum and facing turned to match.

### Rift Fissure

**Not every rift was opened on purpose.**

Ruins generate in the overworld where somebody's gate went wrong: a broken ring in a paved yard, a
sunken vault, a survey mast on a hill. Standing in one of them, invisible, is a **Rift Fissure** — a
tear that never closed, still leaking Rift Essence into an empty room.

You will walk through one and never know. **Rift Infused Goggles** are the only way to see it: Create's
goggles with a rift-ground lens, so they read every machine the engineer's pair does and show the one
thing nothing else will. Put them on and the tear shatters into view in front of you.

Stand a **Spatial Siphon** beside it and the fissure empties into it, closing visibly as it goes —
several times what one vessel holds, so bring pipes. When it is spent it seals over for good.

### Spatial Siphon

A glass vessel on a brass foot. Every time the ship it is bolted to comes out of a rift it catches a
draught of **Rift Essence** — a random amount, with a longer jump widening the odds rather than
guaranteeing anything.

Essence is a real fluid with a bucket and a fluid-handler capability, so Create's pipes can move it
into a tank. Gates spend it to open, and probes spend it to look. You warp to gather it; you spend it
to reach further.

---

## What a warp looks like

The aperture does not iris open. It arrives the way a stone arrives through a window: a hard point of
impact, radial cracks racing out from it crossed by concentric rings, then the panes between coming
loose and tumbling away as glass. A new rift hangs in a slowly turning cloud of its own wreckage.

Inside, the throat is a place rather than a lit pipe — debris to pass, a few things tearing past at
speed against a field that is otherwise fixed, bright ring gates every so often to measure your
progress by, and a light at the far end. Two players in the same corridor see the same things go by,
because all of it is cut deterministically from the rift's own seed rather than sent over the wire.

The crew stays aboard for the whole journey. Anyone who comes off the hull inside the fold — where
there is no ground to fall to — is handed their momentum back and put in their seat.

---

## Learning it

None of the above is discoverable on its own, so there are four ways in:

- **The Navigator's Handbook** — an in-game book with its own screen, and the only one of these four
  that walks the whole thing start to finish. Nine chapters, twenty pages, an animated diagram on
  most of them, ribbons along the top to jump by, and pages that turn like pages.
- **Ponder scenes** — hold **[W]** over any item in this mod for a scene in the same window Create
  uses for its own. Twelve of them, in two chapters: *Warp Travel* and *Rift Gates*.
- **20 advancements** on one tree, which double as the progression the mod otherwise only implies —
  from your first Rift Core to a two-thousand-block haul, a jump to somewhere nobody has been, and
  taking a vehicle through a gate.
- **JEI information pages** on every item, covering the part JEI cannot work out from a recipe: that a
  drive has to be built *into* an airship, that a gate needs a partner, that essence comes out of
  travelling rather than out of the ground.

Create's goggles (and Aeronautics' Aviator's Goggles) show tier, state, charge, RPM requirement and
stress on every machine that has them.

---

## Requirements

| | |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.233+ |
| Create | 6.0.10+ |
| Create: Aeronautics | 1.3.0+ (with Sable 2.x and Simulated 1.3.0+) |
| GeckoLib | 4.8.0+ |
| JEI | optional, client-side |

---

## Notes

**Everything is configurable.** Tier ranges, stress, charge and cooldown times, RPM requirements, warp
cost, probe pricing and reach, gate charges, and a server-wide range cap — all of it is server config,
not constants in the code.

**Cross-dimension warping is not implemented.** Sable binds a sub-level to a single level: its blocks
live in that level's plot grid and its rigid body belongs to that level's physics system, and there is
no supported way to hand all of that to another one. Rather than fake it by teleporting players out
from under their ship, the mod refuses the warp with a clear reason — and leaves a real extension
point for anyone who solves it. Gates are same-dimension for the same reason.

**Airship ownership.** Neither Sable nor Simulated tracks who owns a vessel, so authority over a drive
is proximity plus presence: you must be in reach of it and, by default, standing on the ship it is
about to move. Anchors and gates have real owners, and private ones are enforced.
