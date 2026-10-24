# AeroWarptics

**Fold space. Move the whole ship.**

AeroWarptics is a Create: Aeronautics addon. Build a **Rift Drive** into a vessel, spin it with
rotational force, register **Warp Anchors** at the places you want to reach, and the entire airship —
hull, machinery, cargo and crew — travels through a rift to the far end.

The drive **propels the ship through the rift**. The ship flies into the rift, keeps flying down the
corridor behind it, and comes back out of a second rift at the destination still moving.
You stay on the deck for all of it.

---

## The machines

### Rift Drive

A Create kinetic machine in four tiers, plus a creative one. It takes a shaft on the axis it faces,
draws stress, and needs a minimum RPM before it will charge at all.

| Tier | Min / optimal RPM | Stress at optimal | Range | Charge | Cooldown |
| --- | --- | --- | --- | --- | --- |
| Mk I | 32 / 64 | 512 su | 4,000 | 120 s | 120 s |
| Mk II | 64 / 128 | 1,536 su | 24,000 | 90 s | 90 s |
| Mk III | 128 / 256 | 3,072 su | 120,000 | 70 s | 70 s |
| Singularity | 128 / 256 | 6,144 su | 2,000,000 | 60 s | 300 s |
| Creative | 1 | none | Infinite | instant, never spent | none |

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

A drive has to cool down after every warp. A **Cooling Vent** set against it and turned by a shaft halves
that, and blows the heat out through its louvres as steam when the ship arrives. Vents stack - each
halves what the last one left, up to four of them, for a sixteenth of the wait - and a block of **ice**
against the drive takes a tenth off with no shaft at all.

A **Rift Modulator** bolted beside the drive lets its pilot replace the tier's own rift colours with a
choice of their own. There are sixteen swatches for the core (left click) and the rim (right click),
blended across the rift's face rather than applied as one flat tint; fifteen themes; and a 25%–200%
strength dial. The choices apply to the rift, the corridor and the exit shockwave. It uses a trickle of
Rift Essence, only while the drive is warping, and falls back to the tier's own colours as soon as it
runs out of essence or is removed.

**A theme changes shapes, not just colours.** Each theme changes what stands around the rift while it is
open, how the rift winds up before it appears, and how its glass pane breaks apart:

- **Standard** — struck glass. Unchanged.
- **Clockwork** — a toothed ring gear with three counter-rotating satellites meshing outside it, and a
  working clock face with hands across the rift. Opens as an iris: ten blades sweep aside.
- **Arcane** — twenty-four written glyphs circling the rim and a seven-pointed figure inscribed on the
  face. The circle is drawn, charged, and discharged inward.
- **Ember** — a mouth. Fangs bite inward over the rim and breathe; seven eyes watch from outside it,
  blinking on their own timers, pupils following you as you move.
- **Starlight** — three orbits tilted out of the rift's plane, carrying ring bands and four planets
  that pass behind the rift and are hidden by it.
- **Seven borrowed jumps** — Starblocks, Bedrock, Boldly Gone, Ludicrous Speed, Eventful Horizon, Vworp
  and Improbability: affectionate nods to other works' hyperspace, jump drives, warp drives, plaid
  speed, gravity drives, time vortex and improbability drive, each with its own tunnel for the crew to
  ride through.
- **Ender** — the face is the End portal's own starfield, bleeding in while a pair of enderman eyes
  opens across the middle and stares at you. The pane teleports away, and the tunnel is the End, with
  eyes opening on its walls.
- **Sketch** — a cartoon of a hole, drawn in front of you in about a second: a bold outline, the inside
  scribbled in with your colour, a second line in your other one, and a shine. Nothing shatters. The
  pencil that drew it sticks around — twirling, tapping the rim, flipping end over end — ducks out of the
  way as your ship goes through, and finishes by scribbling the drawing out, screwing it up and lobbing
  it away. Inside, the tunnel is a paper tube with doodles sailing past down the wall.
- **Mostly Dangerous** — a capital ship arriving. An ink-black cloud boils out of nowhere, crawling with
  white, violet and blue lightning, and space bends around it. Your ship bores out of the middle, lit by
  the flashes inside it — and the moment you're clear, the cloud collapses in on itself and is gone,
  leaving smoke, wreckage and your engine trails hanging in the air.

The first five are drawn in the colours the pilot picked, untinted — themes separate themselves by shape
and motion, never by overriding a chosen hue. The borrowed jumps and Ender bring their own colours,
because hyperspace is blue and the End is the End; Sketch keeps its paper and pencil and colours in with
yours.

### Warp Anchor

A destination. Place one, name it, choose who may travel to it, switch it on and off. The record lives
in a server-wide file, so an anchor in an unloaded chunk is still a valid destination — which is the
entire point of long-range travel.

An anchor is **Public**, **Private**, or open to a **Network**: a named group of players you choose. Type
a name into the anchor's Network box to found one, add your friends from its Members panel, and every
anchor and Rift Gate any of you put on it is shared between you and nobody else. The Astrolabe's chart
and a gate's dial list can be filtered to one network at a time.

### Astrolabe Cartography Table

Nine blocks in a three-by-three square. Until all nine are square it is a stack of loose brass panels;
complete it and it becomes one table with one chart.

A formed table projects a **relief map of where the ship actually is** above itself — eighty blocks
square, one sample per block, given height and lit by a travelling scan line. Its chart lists every
reachable anchor with distance, cost, whether it is in range, and a **top-down survey of the ground
around the selected one**, shaded by vanilla's own map rules. You pick a destination looking at the
terrain you would arrive in.

### Rift Probe

Every other destination in this mod is a place where somebody has placed an anchor, so you can only
travel to places that someone has already visited. The probe removes that limit.

Point the dial at one of eight directions, set a distance, and **scan ahead**. The probe opens a rift
at that spot, holds it open long enough to read the far ground, and brings back the same survey the
Astrolabe draws of an anchor: coastline, height, and how much of the area was actually mapped. You can
then send the ship there based on that survey.

The progress bar shows the server generating the world. If it has not finished in time, the probe reads
what is there and tells you the map is only partly complete, rather than presenting it as whole or
waiting forever.

### Rift Gate

**A doorway you build the size you need, and drive through.**

Build a closed ring of **Rift Gate Frame** standing upright and put a **Rift Gate** controller anywhere
in it. The opening inside is detected the same way a nether portal's is, by filling inward from the
frame, so you choose its size and proportions. Connect the controller to a shaft, supply some Rift
Essence, name it, and dial another gate.

Anything that crosses the gate's plane comes out of the far gate: **players and mobs on foot, riders on a
mount, or an Aeronautics land vehicle**. A car is a Sable sub-level exactly as an airship is,
so it goes through using the same code that carries a hull across a warp, passengers and cargo
included. Build a bigger ring to pass a bigger machine.

**Only the gate that dialled pays.** Essence opens the connection and rotation holds it, both scaled by
the size of the doorway. The gate at the other end needs nothing but to exist, so you can build a
destination gate at a mine and walk away from it. A gate that is not connected draws no stress at all.

A connected gate fills its ring with **Rift Portal**: a pane of shimmering violet that stands in the
world like a nether portal, lights the area around it, and takes the shape of whatever ring you built.
Walk into it and the gate does the rest.

**Going through takes about three seconds, and you see the journey.** You cannot be hurt, targeted or
pushed while it lasts, and the far side loads behind it. A vehicle with a crew aboard waits at the far
gate until they arrive. A server can set the length or switch it off (`gatePassageTicks`).

What the journey looks like is each player's own choice, from the **Passage** button on any gate's dial
panel:

- **Kawoosh** - the pane's light bursts over you and you are flung down a twisting tunnel of blue and
  violet plasma to a flare of light.
- **Timey Wimey** - a spiral of storm cloud, red sliding to blue, with lightning down its length.
- **Punch It** - the stars pull out into lines, and then there is only the blue-white lane.
- **Energise** - a column of light and a shimmer of rising motes; you arrive a speck at a time.
- **Full of Stars** - two planes of every colour streaming out of a burning slit.
- **Down the Pipe** - a green pipe in big square pixels, between an iris shutting and an iris opening.
- **Quiet** - a plain fade, with no motion at all.

Two gates are rarely the same size or facing the same way, so a crossing is carried over as a
**fraction of the opening** plus a **rotation** of everything else: enter at the top left of a large gate
and you leave at the top left of a small one, with your momentum and facing turned to match.

**Trains go through too.** Pick a gate on the dial and press **Set Route**, then run straight Create
track into the bottom of the opening. Track comes out of the far gate, two blocks of it, and you build on
from there. Schedules find their way through, a train that reaches a shut gate waits while the gate dials
for it, and the gate shuts again once the last carriage is through - the train that opens the gate pays,
as a dial does. Routes can lead to a gate in another dimension, which trains can follow and nothing else
can. Riders pass through the **Rift Corridor**, a few seconds of track in a violet void, with the
starlines streaming past. A redstone signal on the controller holds trains back.

### Rift Plotter

**Put a rift anywhere in the world, and send things through it - freight or fire.**

An old machine, rebuilt: a field generator three blocks across, with a bronze dish on a turntable and the
Relic Armillary spinning at its focus. Type the coordinates of a target and its table shows a **3D model
of the ground there** (five chunks out, loaded just for you) that you can rotate. Set the exit around the
target: **how far out, in which direction, and how high**, facing the target or aimed off to the side to
dive, and **how fast things leave it**: at their own speed, at a set speed, or at rest for a gentle
delivery.

Set the entry in the world, not in a menu. The table folds into a sidebar, the camera moves out beside
the machine, and a hologram of the rift stands where it will open while the dish swings round to point at it.
Put it where you like and turn it to face any way you like, so it can sit across a runway beside the machine.

Then choose how it fires, in any combination: a **dock helper** that catches a ship waiting behind the
rift, lines it up and launches it through, as wide and as deep as you set it, with an outline in the air showing pilots where to wait (the far
rift stays shut until something is actually coming through); **fire when a ship docks**, for hands-off shipping; **fire on
redstone**; or **hold it open as a lane**, which costs essence every second. Whatever goes through (**an
airship and its crew, a volley of arrows, lit TNT, cannon shells, a raiding party on foot**) comes out
at the far end.

**There is no range limit, but distance costs Rift Essence.** The machine charges as you pipe essence in,
so a target on the far side of the world simply takes longer. The defenders get one warning: the far rift
opens over their heads before anything comes out of it. Servers that want no weapons can switch plotters
off.

### Rift Ward

**Hold a place shut.**

The third old machine, and the one that looks like it should have been left alone: a cracked stone totem
standing in a roofless machine, clamped and chained in brass. Feed it rotation and Rift Essence and its
pieces lift apart, turn and light, and it holds a field over the ground round it, as wide as you set.
Whoever warps a ship at your base, or plots a rift into it, **comes out at the field's edge instead** -
and nothing warns them first. You choose whom it turns: everyone, everyone but the networks you name, or
only the networks you name.

When it turns a ship aside, the whole valley knows: the rift is caught over the base, dragged across the
sky to the edge, and breaks open there while the ward tolls. A Rift Probe sees only interference where
your base is. Its panel lifts the camera out over the field, so you set the radius by looking at it.

It is not a wall. Every rift it turns costs it charge, and a fleet costs more; run it dry and the field
drops. A Rift Storm thins it. Better drives push further in before they are turned. And anyone can still
fly in the ordinary way.

### Rift Fissure

**Not every rift was opened on purpose.**

Ruins generate in the overworld where somebody's gate went wrong: a broken ring in a paved yard, a
sunken vault, a survey mast on a hill. In the middle of each is an invisible **Rift Fissure**: a rift
that never closed, still leaking Rift Essence into an empty room.

You could walk through one and never know. **Rift Infused Goggles** are the only way to see it. They are
Create's goggles with a rift-ground lens, so they read every machine the engineer's goggles do, and they
also reveal fissures. Put them on and the fissure appears in front of you.

Stand a **Spatial Siphon** beside a fissure and it drains into the siphon, visibly shrinking as it goes.
A fissure holds several times what one siphon does, so bring pipes. When it is empty it seals for good
and leaves a **Scar Heart** behind. Or leave it open: an open fissure is a free place to rift items,
forever. A **Scar Chart** leads you to the nearest scar nobody has charted.

### Spatial Siphon

A glass vessel on a brass foot. Every time the ship it is bolted to comes out of a rift, it catches some
**Rift Essence**. The amount is random, and a longer jump improves the odds of a larger catch without
guaranteeing it.

Essence is a real fluid with a bucket and a fluid-handler capability, so Create's pipes can move it
into a tank. Gates spend it to open, and probes spend it to scan. You warp to gather it, and you spend it
to reach further. In a Rift Storm, a siphon under open sky also fills with the rain.

---

## Building the next drive

**Each new drive is made from something only the last one could reach.** Each tier asks for a different
step - build, open, fold, collapse - and every step is something the mod is about.

- **Rifting.** Blow items through an open rift with an Encased Fan, the way you would wash or haunt them.
  The rift can be a Rift Fissure, a lit **Rift Brazier**, or an open Rift Gate's pane. A Rift Lens comes
  out as an **Opened Rift Lens**, and every drive from the Mk II up is built with them.
- **Folding.** The **Collapse Cradle** folds a stack at a time for Rift Essence: its arms hand it up to a small
  rift in the middle of its rings and take back what comes down. Warp Crystal becomes the **Folded Warp
  Crystal** a Mk III is built from, and a compass becomes a **Ship's Compass** that points at whichever ship's
  drive you bind it to.
- **The Collapse.** The Singularity Core is collapsed out of a **Scar Heart** at a **Collapse Cradle** - a
  forgotten machine three blocks high, rebuilt round a **Relic Armillary** you can only find in the ruins.
  You place the offerings on Create Depots around it, fill its tank with essence, turn it at a Singularity
  drive's speed, and steady it with Spatial Stabilisers spaced evenly around it. Its arms lift the core to
  the rift, its iris opens, and its rings spin around the hole. It is a landmark: the offerings orbit a rift
  that folds into a black hole, a column of light climbs into a darkening sky, and at the end everything
  falls into a point with a flash and a boom. If the collapse is unsteady, the machine shakes, sparks and
  smokes, the core is flung clear, and anyone too close is hurt. If it is very unsteady, the cradle spits
  everything back out and keeps only the Scar Heart. Its own panel shows the tank, the shaft, the
  stabilisers and how unstable the collapse is, and starts it.
- **Storm work.** A **Stormglass**, set out in a Rift Storm, forecasts the next one - and broken, calls it.

Every process is a data-pack recipe type with its own JEI page, and a **Classic Recipes** data pack brings
back the old ladder for any world that wants it.

---

## What a warp looks like

By default the rift does not open like an iris. It appears the way a stone shatters a window: a hard
point of impact, radial cracks racing out from it and crossed by concentric rings, then the panes
between them coming loose and tumbling away as glass. A new rift hangs in a slowly turning cloud of its
own wreckage.

Inside, the corridor is a place rather than a plain lit tunnel: debris to pass, a few objects rushing
past against an otherwise fixed background, bright ring gates at intervals to show your progress, and a
light at the far end. Two players in the same corridor see the same things go by, because all of it is
generated from the rift's own seed instead of being sent over the network.

The crew stays aboard for the whole journey. Anyone who comes off the hull during the warp, where there
is no ground to fall to, keeps their momentum and is put back in their seat.

---

## Learning it

None of the above is discoverable on its own, so there are four ways in:

- **The Navigator's Handbook** — an in-game book with its own screen, and the only one of these four
  that walks through everything from start to finish. Twelve chapters, thirty pages, an animated diagram
  on most of them, ribbons along the top to jump between chapters, and pages that turn like real pages.
- **Ponder scenes** — hold **[W]** over any item in this mod for a scene in the same window Create
  uses for its own. There are nineteen, in two chapters: *Warp Travel* and *Rift Gates*.
- **30 advancements** on one tree, which double as the progression the mod otherwise only implies —
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
| Dimensional Sable | optional, server-side - only for warps into other dimensions |
| Curios | optional - lets Rift Goggles be worn in a Curios head slot |

---

## Notes

**Everything is configurable.** Tier ranges, stress, charge and cooldown times, RPM requirements, warp
cost, probe pricing and reach, gate charges, and a server-wide range cap — all of it is server config,
not constants in the code.

**Warps into other dimensions are experimental, and off unless a server turns them on.** They also need
[Dimensional Sable](https://modrinth.com/mod/dimensional-sable) on the server: Sable keeps a ship in one
dimension, and Dimensional Sable rebuilds the whole vessel on the far side as it passes through the fold
— crew, cargo and machinery included. Propellers and other contraptions on bearings are taken apart for
the trip and put back together on arrival. A crossing costs the same as a jump of a set length (1000
blocks by default). A Rift Probe can scan the other dimensions a server allows, from a list on its
panel. Gates stay in their own dimension, except for trains: a gate can be routed to one in another
dimension, and only trains go through it.

**Airship ownership.** Neither Sable nor Simulated tracks who owns a vessel, so authority over a drive
is proximity plus presence: you must be in reach of it and, by default, standing on the ship it is
about to move. Anchors and gates have real owners, and private ones are enforced.
