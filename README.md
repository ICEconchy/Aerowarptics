# AeroWarptics

A Create: Aeronautics addon that lets an airship fold space.

Build a **Rift Drive** into a vessel, spin it with Create's rotational force, register **Warp Anchors**
at the places you want to reach, and the whole ship — hull, machinery, cargo and crew — travels through
a temporary rift to the far end.

* Minecraft 1.21.1 / NeoForge 21.1.233
* Create 6.0.10
* Create: Aeronautics 1.3.0 (Sable 2.0.3 + Simulated 1.3.0 + Aeronautics 1.3.0)
* GeckoLib 4.8.4

---

## What an "airship" actually is here

Create: Aeronautics does not carry its own ship object. An assembled vessel is a **Sable
`SubLevel`**: an independent plot of blocks living in reserved chunks of the parent `ServerLevel`,
driven around by Sable's physics pipeline. Aeronautics contributes the balloons, propellers and
levitite on top of it; Simulated contributes the assembler and the controls.

Everything in this addon follows from that:

| Question | Sable API used |
| --- | --- |
| Which airship is this machine on? | `BlockEntitySubLevelActor.sable$tick(ServerSubLevel)` |
| Where is the airship? | `SubLevel.logicalPose()` (`Pose3dc`) |
| Ship space ⇄ world space | `Pose3dc.transformPosition` / `transformPositionInverse` |
| How big / heavy is it? | `LevelPlot.getBoundingBox()`, `ServerSubLevel.getMassTracker()` |
| Move it | `PhysicsPipeline.teleport(body, position, orientation)` |
| Fly it | `PhysicsPipeline.resetVelocity` + `addLinearAndAngularVelocity`, re-commanded each tick |
| Per-ship persistent state | `ServerSubLevel.get/setUserDataTag()` |
| Carry passengers with it | `SubLevelHelper.push/popEntityLocal` |
| Draw effects at a ship-mounted block | `Sable.HELPER.projectOutOfSubLevel` |

A Rift Drive never scans the world looking for its ship. Sable calls `sable$tick` on it once per tick
*because* it is part of an airship, and that call is the answer.

---

## The machines

### Rift Drive

A Create kinetic machine in four tiers. It accepts a shaft on the axis it faces, draws stress, and
needs a minimum RPM before it will charge at all.

| Tier | Min / optimal RPM | Stress | Range | Charge time | Cooldown | Efficiency |
| --- | --- | --- | --- | --- | --- | --- |
| Mk I | 64 / 128 | 16 su | 4,000 | 120 s | 120 s | 1.0× |
| Mk II | 96 / 192 | 24 su | 24,000 | 90 s | 90 s | 1.6× |
| Mk III | 128 / 256 | 32 su | 120,000 | 70 s | 70 s | 2.4× |
| Singularity | 192 / 256 | 48 su | 2,000,000 | 60 s | 300 s | 3.2×, unstable exit |
| Creative | 1 / 1 | none | 100,000,000 | instant | none | no cost |

Every number above is a config default, not a constant.

Right-clicking a drive opens its **console**, which is a diagnostic panel and nothing else: speed
against requirement, stress, charge, hull mass, the bow setting, and a checklist of everything a jump
needs with each item either met or not. There is no destination list and no launch button, because a
drive is a machine rather than a control — you set a course at a chart table and fire it with a signal.
What the console is for is answering "why has this not gone anywhere", which it does by naming the
condition that is missing.

The **Creative Rift Drive** has nothing made it and nothing stops it: no recipe, no stress, no
cooldown, no charge time, no minimum speed, and no range cap. Almost all of that is just its own tier
numbers rather than a fork through the drive — one RPM to run, one tick to charge, no cooldown - so it
behaves like a drive in every way that matters for testing. The two things numbers cannot express are
handled explicitly: it never stalls for want of rotation mid-warp, and the server-wide range limit does
not apply to it, because that setting is a statement about what players may do.

### Astrolabe Cartography Table

Nine blocks in a three-by-three square, laid flat. Until all nine are square it is a stack of loose
brass panels that does nothing; complete it and it becomes one table with one chart.

A formed table projects a **relief map of where the ship actually is** above itself: eighty blocks
square, **one sample per block**, given height and lit by a scan line that travels across it. That
projection is built entirely on the client, because the ground being shown is ground the player's own
client already has loaded.

Right-click any of the nine cells to open the **chart**. It lists every anchor you may see with its
distance, cost and whether the drive on this hull can actually reach it, and it shows the selected
destination **from above**: a 129×129 survey of the ground around the anchor, one pixel to a block,
with **contour lines every 8 blocks** so you can read a valley off it before you arrive. That half
cannot be done on the client — those chunks have never been sent to it — so the server surveys the
ground once per destination looked at and sends the picture back. Only chunks that are already loaded
are read, so a chart can never drag terrain off disk on the server's tick thread; unexplored ground
comes back blank and the panel says what fraction that was, because blank means *nobody has been
there*, not *flat*.

Choosing a destination sets the course and stops there. Nothing on the table launches anything.

#### Why both maps look like maps

Both are drawn by [`TerrainPalette`](src/main/java/uk/co/iceconchy/aerowarptics/astrolabe/TerrainPalette.java),
which is vanilla's own rules and not an approximation of them: the same 62-entry `MapColor` table a
filled map uses, and the same **north-facing slope shading** — a cell is lit when it stands above the
cell behind it and shadowed when it falls away, with vanilla's checkerboard dither on the threshold so
large flats do not band. That one rule is most of what makes any Minecraft map legible; without it
terrain is a wash of flat colours with no shape in it. Open water has no slope to shade, so it is
shaded by **depth** instead, which is what gives a coastline its edge.

Two things had to be got right to make a sample per block affordable:

- **The wire.** A destination survey travels as one byte a cell, packed exactly as vanilla packs a map
  — six bits of palette index, two of brightness — plus one byte of height for the contours. A whole
  129×129 survey is about 33 KB, sent once when the player clicks a different anchor.
- **The draw.** The chart bakes that into a `DynamicTexture` once and blits it. Painting sixteen
  thousand cells as sixteen thousand rectangles every frame, to redraw a picture that only changes on
  a click, is not a thing worth doing.

There was also a plain bug in the old version worth naming, because it is the kind that hides in plain
sight: `MapColor.calculateRGBColor` returns **ABGR**, the byte order vanilla's map *texture* wants, and
both maps were reading it as ARGB. Red and blue were swapped everywhere — grass came out blue-green and
water came out orange — which reads as "the projection looks wrong" rather than as a one-line mistake in
a shift. `TerrainPalette` scales the colour's own channels instead, and
[`TerrainPaletteTest`](src/test/java/uk/co/iceconchy/aerowarptics/TerrainPaletteTest.java) asserts that
grass is greener than it is blue and water is bluer than it is red.

### Rift Gate

A doorway you build the size you need, and drive through.

Lay a **closed ring of Rift Gate Frame** standing on end, in either vertical plane, and put a **Rift
Gate** controller anywhere in it - including a corner, which is where most people put one. The opening
inside is found the way a nether portal's is, flood filled inward and bounded by frame, so its size and
proportions are yours. Feed the controller a shaft and a supply of Rift Essence, right-click it, name
it, and dial another gate.

Anything that crosses the plane comes out of the far gate: **on foot, on a mob, and in an Aeronautics
land vehicle**. A vehicle is a Sable sub-level exactly as an airship is - a wheel mount is a
`BlockEntitySubLevelActor`, the same as a Rift Drive - so a car goes through on the same code that
carries a hull across a warp, passengers and cargo included. An airship will go through too if the ring
is big enough, which is a consequence rather than a feature, but a welcome one.

**The opening is the size limit.** This is not a balance decision. The aperture hides what falls inside
its own silhouette and nothing else, so a vehicle wider or taller than the ring would be visible
sticking out of the portal at both ends of the journey at once - the one thing the illusion cannot
survive. Something too big is stopped dead on the near side and told why. Building a bigger ring is how
you pass a bigger machine, and that is what makes free-form sizing the point of the block rather than a
convenience.

#### What it costs

**Essence to open, rotation to hold** - and **only at the end that dialled**. A dial spends Rift
Essence from the gate you are standing at, a base charge plus a per-block charge so that a bigger
doorway is a bigger tear, and that same gate then draws stress for as long as the connection stands.
Let its shaft stall and the connection drops.

The gate at the *other* end pays nothing and needs nothing but to exist, be enabled, and not already be
busy. That is the point: a destination gate is a doorway you build at a mine and walk away from, and
requiring a working drive at both ends means every remote gate is a second base. It also has to be
checked at only one end in the code, which the first version got wrong - a passive far gate hung up on
its very first tick and dragged the dialling one down with it, so gates simply did not work.

A gate that is not holding an aperture draws **nothing at all**, which is what stops a player being
punished for building a doorway they use twice a day. Create caches a block's impact, so the gate
detaches and re-attaches itself from the kinetic network whenever that changes - both when an aperture
appears or goes, and when this end stops being the one paying for it.

This is also the first thing that consumes what a **Spatial Siphon** collects. You warp to gather
essence; you spend essence to run doorways.

#### Where you come out

Two gates are rarely the same size and usually not the same way round, so a crossing is carried over as
a **fraction** of the opening and a **rotation** of everything else. Enter the top left of a large gate
and you leave by the top left of a small one, rather than being deposited in its frame. Momentum and
facing turn with you, so driving in at speed means driving out at speed, pointed the right way.

The part worth testing is the handedness. A portal that reflects instead of rotating puts a vehicle out
with its left on its right, which looks like a turn from one side and shows up later as a car that
steers backwards. `GateTraversalTest` pins it with a round trip: going through and coming straight back
has to return you to the same part of the opening, the same height and the same heading. A reflection
passes every other test in the file and fails that one, because reflecting twice is not a rotation.

There is deliberately **no half turn** anywhere in that maths. A gate's plane has two faces, and which
one you leave by is decided by which way you were going - turning somebody a hundred and eighty degrees
as well would send them back the way they came.

#### The rest of the rules

- **Same dimension only.** Sable cannot move a sub-level between levels, so a gate in another dimension
  is somewhere a vehicle could never follow its driver. A door that takes half of what goes through it
  is worse than no door, so the dial list is filtered rather than the refusal being a surprise.
- **Gates are named, owned and access-controlled**, on the same rules as Warp Anchors and reusing the
  same `WarpAnchorAccess` - so who may travel where is written once and behaves the same at a chart
  table and at a doorway.
- **A crossing is a change of side between two sightings the gate took itself.** Standing in the
  opening is not going anywhere, and a first sighting is never a crossing - something that appears
  already past the plane has not gone through it. The sightings have to be the gate's own: working it
  out from an entity's previous position instead means depending on when in the tick that entity
  happened to move, which for a player is whenever their packet arrived, and a gate that notices a
  crossing only sometimes is a gate players stop trusting. `GateWatchTest` covers the rules, including
  the two that bite - a traveller sent through is forgotten so they cannot bounce, and anything that
  leaves the catchment starts again rather than being teleported the moment it wanders back.
- **The far end is held loaded** while a connection stands, by a ticket that expires and is renewed
  rather than one that is released. Something that must be renewed cannot leak; it can only stop.
- **A gate that unloads drops its connection but keeps its registration.** Chunk unload and block break
  are different events, and treating them alike would delete a gate from the world every time nobody
  was standing near it.

The aperture is the same one a Rift Drive tears, down to the shattering - the pane cracks, breaks and
falls away in glass exactly as described under [Breaking space open](#breaking-space-open). It grows no
throat, because a doorway is not a tunnel, and it is **held** rather than timed: the gate renews it from
its own client tick, so somebody who walks up to a gate that opened before they arrived still sees it
standing.

Closing is the same fracture run the other way. Every piece comes back out of the dark, turning as it
falls, and lands where it was cut from - **from the rim inwards**, so the hole shuts down to a point
rather than fading out evenly, and there is a flash as it seals. Deliberately not the opening played
backwards, which reads as a rewind: the shards arrive from outside rather than retracing the paths they
left by.

That close used to be invisible, and for a reason worth writing down. A held aperture's hold is
effectively forever, and ages are interpolated as **floats** - past sixteen million, a float cannot
tell one tick from the next, so the entire twenty-tick close landed inside a single representable step
and the hole simply snapped out of existence. Letting go now ends the hold *at the moment it happens*
rather than winding the clock forward to the end of it, which keeps the whole animation in numbers
small enough to count in. The renderer learned two things for this - an aperture can be an ellipse now, fitted to a
rectangular ring, and it can be kept up indefinitely instead of running on an open-hold-close clock.

### Spatial Siphon

A glass vessel on a brass foot. Every time the ship it is bolted to comes out of a rift, it catches a
draught of **Rift Essence** — a random amount, with a longer jump widening the odds rather than
guaranteeing anything. Several aboard one hull each roll separately.

Nothing consumes the essence yet. It is a real fluid with a bucket and a fluid-handler capability, so
Create's pipes can drain it into a tank, and it is there to be the raw material dimensional travel gets
built on later. The yield is deliberately a lottery rather than a rate: it should be a by-product of
travelling, not a reason to bounce a ship between two anchors.

### Warp Anchor

A destination. Place one, right-click to name it, choose public or private, group it under a network
label, switch it on or off. The record is kept in a server-wide `SavedData` file, so an anchor in an
unloaded chunk is still a valid destination — which is the point of long-range travel.

An anchor that gets assembled *into* an airship withdraws itself: its position would be a coordinate
inside Sable's plot grid, which nobody can navigate to.

---

## The warp

A warp is flown, not cut to. The ship makes a run at an aperture, goes *through* it, keeps flying
down the throat behind it, and comes back out of a second aperture at the far end still moving.

```
   APPROACH          TRANSIT + CORRIDOR           BREACH        EMERGE
 ┌────┐          ((entry))                     ((exit))
 │ship│──▶──▶──▶ ─┃═══ ship ══▶ ═══════════┓  ┏━━━━┃ship┃──▶──▶  ┌─────┐
 └────┘            hidden, inside the throat     the far throat  │ rest│
                                  │                              └─────┘
                        one teleport, in here
```

`┃ship┃` is the hull inside an aperture, hidden by it. **Nothing is teleported until it is**, and the
one teleport there is happens deep inside a throat at both ends.

The whole journey is a single continuous motion. The run at the aperture eases down so the bow reaches
the plane already doing passage speed; the passage and the corridor hold that speed; the teleport
carries the momentum across unchanged, and both apertures face the same way so the direction survives
it too; and only once the ship is back in open air does the speed bleed away to nothing. There is no
boundary at which the ship's motion changes — `WarpSpeedTest` pins each of those joins, because a step
change in a ship's motion is something a passenger feels even when they cannot see what caused it.

1. **Charging** — rotational force accumulates charge. Below the tier's minimum RPM the drive bleeds
   charge instead.
2. **Destination** — the pilot picks an anchor in the Rift Navigation console.
3. **Validation** — the server re-derives everything: which airship, which anchor, the distance, the
   cost, whether the player may command this drive.
4. **Lock** — the drive claims its airship. A second drive on the same ship is refused.
5. **Spinning up** — the drive winds itself up. How much winding a jump needs comes from how far it
   reaches, on a log curve between the tier's `stabilizeTicks` and `stabilizeTicksFar`; how *fast* it
   winds comes from the shaft, at full rate at the tier's optimal RPM and a crawl at its minimum. So
   the answer to "why is this taking so long" is always either "you picked somewhere far away" or
   "spin it faster", and both are things a pilot can see and act on. The machine's tempo and the pitch
   of its wind-up follow the same figure, so an underpowered drive audibly labours.
6. **Planning** — the *whole* journey is worked out before anything opens: where the ship will come
   out and whether that volume and its run-out are clear. A warp with nowhere to land is refused while
   the ship is still safely at its mooring. The arrival chunks are claimed here too, with a
   self-expiring ticket, so they load quietly during the flight rather than off disk at the exact
   moment the hull is coming out of the aperture.
7. **The run** — an aperture tears open ahead of the bow and the server takes the helm, driving the
   hull at it. The pilot's controls, the ship's own thrust and gravity are all overridden until it
   comes to rest.
8. **The passage** — the run ends when the *bow* touches the plane, not the ship's centre, and the
   hull then spends three seconds being drawn the rest of the way in. Each part of it disappears as
   it crosses (see below). Only once the stern is inside does the teleport happen.
9. **The corridor** — the hull simply keeps flying down the throat, out of sight, for a few seconds.
   A fold in space is not a distance, so it does not need to go anywhere; what sells the journey is
   what the crew sees, and that is a screen effect. As the corridor starts, the far aperture tears open
   at the destination, so anyone waiting there watches a rift form and *then* a ship come through it.
10. **The crossing** — the single teleport, deep inside one throat and into another, with the hull's
    speed and heading carried across intact.
11. **The breach** — the passage mirrored. The hull is entirely *behind* the far aperture, hidden by
    it, and flies out nose first over another three seconds.
12. **Emergence** — clear of the aperture and still moving, the hull settles into the place the
    planner picked on a decelerating run that lands exactly on the mark, and the rift collapses behind
    it. No parting command to stop: a ship should settle, not halt.
13. **Cooldown** — the drive vents and recovers.

There is exactly **one** teleport in a warp, and it happens while the hull is completely hidden inside
an aperture at both ends, so it is never visible as a jump. Everything else is genuine velocity, so
passengers, cargo and attached machinery are carried by the same Sable code that carries them in
normal flight.

### Not throwing the crew across the map

Commanding real velocity is what makes the journey look right, and it has one sharp edge. Inside the
corridor the hull is doing about nine blocks a tick — a hundred and eighty a second — and Sable carries
whatever is standing on it. But something can come off: a player who jumps at the wrong moment, or one
the collision solver loses for a tick while the deck is moving faster than the deck is thick. By then
the ship's motion has been imparted to them, and a person carrying a hundred and eighty blocks a second
is not dropped, they are *fired*.

[`WarpPassengers`](src/main/java/uk/co/iceconchy/aerowarptics/warp/WarpPassengers.java) keeps a manifest
for the duration of the flight — who is aboard, and **where they are standing in ship space**, which is
the only frame in which "put them back" means anything once the deck has moved. Every tick, before the
hull is moved, anyone on the manifest who is no longer aboard gets two things done to them:

| | |
| --- | --- |
| **Always** | The borrowed momentum is taken back. A player's own client is authoritative about where they are, so this is a real motion packet and not just a field on the server. |
| **Only inside the fold** | They are put back in their seat. |

The split matters in both directions. On the approach and on the way out the world is real and falling
off a ship is an ordinary thing to do, so they are allowed to — teleporting them back would be a trap,
not a rescue. Between the bow entering the aperture and the stern leaving the far one there is no
ground, no air and nowhere to walk to, so a passenger left behind there is a passenger *deleted*.

However the journey ends — arrival or abort — the manifest is settled last, **after** the hull has been
put wherever it is finally going to be. So somebody who came off mid-corridor arrives with the ship
rather than at the coordinates of a rift that no longer exists, and nobody takes fall damage for a
descent the server was flying.

### Breaking space open

An aperture does not iris open. It arrives the way a stone arrives through a window, in three beats:

1. **The crack.** A hard point of impact, and thirteen radial fractures racing outwards from it,
   crossed by four concentric rings. There is no hole yet — the world behind the rift is still there
   to see, with a lit fracture over the top of it.
2. **The break.** The panes between the fractures come loose, the hole arrives fast rather than
   easing, and a pane of space goes.
3. **The drift.** Fifty-two shards tumble away and fade over the next couple of seconds, well outliving
   the opening, so a new rift hangs in a slowly turning cloud of broken glass.

That fracture pattern — radial cracks plus concentric rings — is the one struck glass actually makes,
which is most of why it reads as breaking rather than as an effect.

`RiftShatter` cuts the pane deterministically from the rift's own position, the same way `RiftTear`
shapes the rim. Nothing about the fracture goes over the wire: two players watching one aperture see
the same glass break because both worked it out from the same coordinates. The entry and exit rifts of
a jump are metres apart, so they break differently from each other.

Shards are drawn **translucent, not additive**, and on their own render type that writes no depth. A
fragment passing in front of a burning rim should darken it; drawn additively every piece disappears
into the light it came off. Each one keeps its own frame and tumbles in it, and its brightness is keyed
to how square-on it happens to be turned — so the field glitters as it drifts, which is the one thing
that says "hard reflective surface" without a texture to say it with.

**The shatter is a presentation, not a mechanism.** Everything below this depends on the face being a
complete, opaque surface before a hull reaches it, so:

- the whole break runs inside the opening budget, which the flight planner floors at twenty ticks of
  approach, and `RiftShatterTest` asserts every shard is loose before the hole finishes opening;
- the hole is exactly zero for the entire crack phase — a partly-open aperture with a fracture painted
  over it would be neither one thing nor the other;
- and if a hull arrives early anyway, because the rift stands close or the run at it is brisk, the
  aperture **stops animating and is simply there**. A ship arriving is not a cue to keep playing an
  opening; it is a cue to be open. That is the same reasoning the throat already used.

The fracture also has to tile the pane it cuts up. A gap between cells is a piece of glass that never
existed and an overlap is two pieces in the same place, so the test sweeps sixty-four seeds asserting
every ring closes on exactly one turn and every ring meets the one inside it.

### Disappearing into a rift

The face of an aperture is **opaque and writes depth**, and it is drawn after the world's blocks. That
is the whole mechanism: anything past its plane is painted over and genuinely gone, while anything in
front of it survives the depth test and stays visible. A hull flying through is therefore occluded for
real, section by section, as it crosses.

It is worth being clear about what this is *not*. Sable draws airship blocks through vanilla chunk
rendering, so cutting the hull itself at the portal plane would mean replacing Minecraft's core chunk
shaders with ones that carry a clip plane — global surgery, from an addon, that would fight shader
packs and Flywheel. The aperture approach needs none of that, works on any driver, and gets the same
read: the ship goes into the hole and does not come out of this side.

The rim is **torn rather than cut**, wandering with angle and time — but only ever *outwards* from a
circle sized to cover the hull. A tear that bit inwards would open a window in the very surface doing
the hiding. That invariant is not left to inspection: `RiftTear` holds the shape as plain arithmetic
and `RiftTearTest` sweeps thousands of angles asserting the rim never drops below the covering circle,
never overruns its stated reach, and meets itself where the loop closes.

An aperture also has **depth**. A flat face only hides what is directly behind it, so a hull halfway
through one is still in plain view to anyone standing off to the side. While a ship is actually
passing through, the aperture grows a closed **throat** behind its mouth — a bore long enough to
contain the whole hull, tapering shut at the far end — so there is no angle left to see it from. The
throat runs whichever way the hull is hidden: forwards on the aperture a ship flies into, backwards on
the one it comes out of, and it shares the mouth's torn rim exactly so the two meet on a common edge.
It exists only while something is inside it; an aperture sitting open does not trail a bore behind it.

Making that bore read as a *tunnel* rather than as a shape took five things, because an opaque object
with no surface detail and a hard silhouette is exactly what the eye files as a blob:

- **Ribs.** Sixteen rings down its length, bunched towards the mouth where they can still be resolved,
  with alternate rings standing slightly proud. They are what give the eye something to measure depth
  against, and something for motion to pass over.
- **A gradient that reads all the way down.** The falloff used to bottom out a third of the way in,
  leaving two thirds of the bore uniformly black. It now decays gently over the full length, onto a
  floor tinted with the rift's own colour — low-intensity energy rather than missing pixels.
- **A band of light running the length**, away from the mouth on an aperture a ship goes into and
  towards it on one it comes out of, so the tunnel always shows the direction of travel.
- **A haze sleeve.** A soft additive flare around the outside, fading over a few blocks, so the bore
  has an atmosphere to sit in instead of a hard edge cut against the sky.
- **Gates.** Every fourth rib is lit as a bright ring rather than another stripe. A hull passing
  through one is the clearest speed cue the corridor has, because it is a discrete event at a known
  distance rather than a gradient sliding by.

The far end used to fade to black before it closed, so what a player saw was a bore receding into
darkness rather than a cone ending in a point. It now has a **light at the end of it** — a small white
core in a halo of the rift's colour, breathing slowly, sized off the bore where it actually sits rather
than off the mouth. "Not obviously ending" is not the same as going somewhere, and in a place with no
landmarks a light you close on is the only progress cue available.

### What is in the tunnel

A lit pipe is not a place. What makes the bore one is having things in it to pass: broken glass still
turning over from the aperture that tore open to let the ship in, heavier wreckage of somewhere else,
and the occasional streak of light running the other way. `RiftDebris` furnishes every bore with
ninety-six pieces, cut deterministically from the rift's own seed like everything else here, so two
players in one corridor see the same things go by without a byte being sent about them.

**Almost none of it moves**, and that is the whole design rather than an economy. The hull covers the
length of the bore in about five seconds, and that is the one motion cue in the corridor that is real
rather than painted. Debris moving fast *as well* would cross the view inside a single frame and simply
never be seen — which is precisely why the corridor was a screen effect before it was a place. So the
field is fixed in the tunnel and the ship supplies the speed. A slow drift and a tumble keep it from
being a sculpture.

A sixth of the field is given real speed and drawn as **streaks** instead, running against the ship so
they close with it rather than dawdling alongside. Each is drawn as long as it is fast, turned to face
the viewer, and fading from head to tail — the head is where the thing is and the tail is where it was.
A few things tearing past is energy; everything tearing past is a blur.

The two kinds of debris are drawn to be told apart at a glance. **Glass** glints: each piece tumbles in
its own frame and brightens as it turns face-on, the same trick the shatter uses, because a flash is
what says "hard reflective surface" with no texture to say it with. **Wreckage** is drawn dark and
nearly solid, so it silhouettes against the lit wall behind it. Both are lit by the bore they are in,
running band included — a pulse that swept over the wall and left the things floating in front of it
unchanged would give the whole effect away as paint.

Positions **wrap** rather than clamping. A piece that ran off the end and stopped would leave the
tunnel emptying out behind the ship over a long corridor, which is the exact opposite of what a
corridor should do as you get further into it. `RiftDebrisTest` holds the parts that would fail
silently: nothing ever leaves the bore however long it drifts, nothing sits close enough to the axis to
end up inside the hull or far enough out to be embedded in the wall, and the two speeds stay two
speeds — a drifter given a streak's velocity is invisible, and a streak given a drifter's is a
stationary line hanging in the tunnel.

The taper is a hole in the occluder as far as anything inside it is concerned, so the hull must be
clear of it — and used not to be. The depth was sized by scaling only the passage while the corridor
run, flown down the same bore, grew independently; on a long corridor the bow finished about a block
inside the closing cone. `ThroatCoverageTest` now holds the bore full width past everything it hides,
with real margin, at every hull size.

Players on deck need no special handling. Entities are drawn before the occluder, so the same geometry
that hides the hull hides everyone standing on it.

Fire is drawn additively over the face and past the rim, nudged a few centimetres towards whichever
side the camera is on so it does not z-fight with the surface it is burning around. While a hull is
actually going through, the aperture is told once and runs the extra fire off its own clock, rather
than the server streaming a cue every tick for three seconds.

For the crew, the wash into the corridor starts when the **bow** goes in, not when the ship teleports.
Their own camera crosses the aperture partway through the passage, and without that they would watch
the world go dark and then carry on flying through it for another second.

### Which way is forward

Nothing in Sable or Aeronautics marks a bow, so the pilot marks it, on the drive, in the airship's own
frame. The setting is one of four quarter turns - **Forward**, **Right**, **Back**, **Left** - measured
from the Rift Drive's own front face.

Relative, not absolute, and that is the whole point. A compass bearing would be a lie the moment the
vessel came about: set it to north before a long turn and the rift would tear itself open across the
beam. Because the setting lives in ship space, it is only turned into a world bearing at the instant a
flight is planned, using the hull's current pose. Aim it at the nose once and it stays aimed at the
nose, whatever heading the ship is on and however far it has been sailed since.

The drive wears a **needle** on its crown, balanced across the upright gimbal, which points at the
current setting. It swings a quarter turn each time the setting is cycled, so the bearing can be read
off the machine from the deck without opening anything. The needle and the flight planner are driven
from the same value, and a test holds them to it: if the needle points somewhere the ship would not
actually go, the build fails.

A drive taking power from a *vertical* shaft has no front face to measure from, so the hull's own north
stands in as the reference. The four settings still cover all four ways round and the needle still
reads the same rule, so it remains something a pilot can aim - it just cannot be read off the block's
face.

### Cost

```
cost = baseWarpCost + distance × distanceMultiplier + (mass ÷ massReference) × sizeMultiplier
       ────────────────────────────────────────────────────────────────────────────────────
                                    tier costEfficiency
```

`mass` is Sable's own figure for the airship. If the mass tracker has not been built yet — briefly
after assembly — the bounding-box volume stands in.

### Safe arrival

The airship is never dropped onto the anchor block. The search starts from a **clearance point**: the
height at which the hull's underside would sit a configured buffer above the anchor, worked out from
the ship's own footprint rotated into the orientation it will arrive with. A skiff clears the anchor
by a few blocks; a two-hundred-block dreadnought starts a hundred blocks up, because that is where its
keel has to be for its deck not to be inside the mountain.

From there the search **climbs the anchor's column**, and only once every height in that column is
ruled out does it start stepping sideways in widening rings.

```
   blocked column?  ↑ ↑ ↑        then, and only then, rings:   ↑   ↑ ↑ ↑   ↑
   come in higher.  │ │ │                                      │   │ │ │   │
                    ● ● ●                                      ●   ● ● ●   ●
                      ▲ anchor                                   r=1   r=2
```

The volume tested at each candidate is not just the hull: it is the hull swept backwards along the
approach, covering the whole run-out from the exit aperture. If nothing fits, the warp is refused
before it starts and the ship stays where it is.

### Failure

The default consequences of a failed warp are: the warp stops, some charge is lost, the drive sits in
its fault state for a while. **The airship is never damaged or deleted.** `failure.dangerousFailures`
adds a shove and nothing more.

---

### Where the controls live

Three jobs, three places, and none of them overlaps:

| | Where | What it does |
| --- | --- | --- |
| **Choose** | Astrolabe Cartography Table | Sets the course. This is where permission is checked. |
| **Launch** | Redstone into the Rift Drive | Fires the standing course. Grants no access of its own. |
| **Diagnose** | Rift Drive console | Read-only. Says what is still missing. |

**Note that this makes a redstone input the only way to launch.** A drive with a course set and every
requirement met will sit there indefinitely until something gives it a rising edge — a vanilla lever
on the deck is enough. That is a deliberate consequence of taking destination selection off the drive:
the alternative was a launch button on the diagnostic panel, which would have made the panel a control
again.

A ship therefore needs three things fitted, not one: a drive, a table to plan at, and something to
pull. It also means launching can be automated, which the previous arrangement could not do.

Both the table and the drive find each other the same way the drive finds its airship — Sable hands
out the sub-level a block belongs to, and everything on that hull is simply everything in its plot.
There is nothing to link by hand, and a ship can carry several tables that all set the same drive's
course. Machines aboard a hull are read straight out of the plot's chunks rather than out of Sable's
actor list, because membership of that list depends on a block having *changed* since the plot was
created — so a machine that arrived on an already-assembled hull may never appear in it. That
distinction cost this mod a "cannot find drive" bug once already.

### Redstone

A Rift Drive starts a warp on a **rising redstone edge**. Edge rather than level, so a lever left on
does not batter the drive with attempts twenty times a second, and a drive coming off cooldown under a
live signal stays put until somebody actually flips something.

It fires the drive's **standing course** and nothing else. A course is set when a player picks a
destination at an Astrolabe Cartography Table, and that is the moment they are checked for permission,
for reach, and for whether they can see that anchor at all. A signal replays that decision; it never
makes one. Wiring a drive up therefore grants no access the person who set the course did not already
have, and a drive with no course set ignores redstone entirely.

The drive also remembers *who* set the course, so a warp started by a circuit is still attributed to a
person — they are who the launch and any abort are reported to, rather than the news going nowhere
because no player was holding the control at the time.

This is also what makes **Create Simulated's Throttle Lever** work as a launch control with no
integration at all: it is a redstone source, so it needs nothing from this mod beyond the signal it
already emits. A vanilla lever, button or pressure plate does just as well, as does anything that can
be automated.

Turn it off with `permissions.allowRedstoneInitiation` if a warp should always need a hand on a
control.

## Using it

1. Assemble an airship with Create: Aeronautics as usual.
2. Mount a **Rift Drive** on it and drive it with a shaft at or above its minimum RPM. It fits inside
   its own block, so it goes wherever there is room for it.
3. Place **Warp Anchors** wherever you want to travel to and name them.
4. Lay nine **Astrolabe** blocks in a three-by-three square somewhere on the deck. It will click
   together and start projecting a map of wherever you are.
5. Right-click the drive and set the **Bow** control — `Forward`, `Right`, `Back` or `Left`, measured
   from the drive's own front face. Watch the needle on top of the machine and stop when it points at
   the nose. The compass point in brackets is only a readout of where that is pointing right now.
6. Right-click the table and pick a destination. The chart shows distance, cost, whether it is
   reachable, and the ground around it from above. Clicking one sets the course — there is no confirm
   step, because setting a course does not move anything.
7. Check the drive's console: every requirement should be ticked except the last.
8. **Give the drive a redstone signal.** A lever on the deck will do. Stay aboard — the fold is only
   visible from the deck.
9. Optionally, bolt a **Spatial Siphon** somewhere on the hull and let it fill as you travel.

The corridor wash is deliberately **non-directional**. It used to be streaks racing outwards from the
centre of the screen, which reads as "you are travelling that way" — except the centre of the screen is
wherever the player happens to be looking, so turning your head turned the direction of travel with it.
An effect that lies about which way the ship is going is worse than no effect, so what is there now is
true from every angle: colour drawn in from every edge, the view closing, and a slow shimmer of
horizontal bands.

Wearing Create's goggles (or Aeronautics' Aviator's Goggles) shows the drive's tier, state, charge and
RPM requirement, plus its stress impact in Create's usual format.

---

## Crafting

Each drive tier consumes exactly **one** of the tier below plus an upgrade kit, and the whole cost of
building one roughly doubles each step — ×2.16, ×2.01, ×2.13 by crafting effort. Two components are
made in Create machines rather than on a grid: a **Rift Lens** is compacted out of amethyst and end
stone powder under heat, and a **Rift Core** is mixed hot from brass, levitite and precision parts.

A **Warp Anchor** takes a lens rather than a core, deliberately. Anchors are placed in numbers — a
network of twenty should be a project, not a wall — so the expensive component lives in the drive that
reaches them, not in every destination. The same reasoning applies to the **Astrolabe**: a craft makes
three panels and a table needs nine, and the whole table still costs less than the cheapest drive it
steers. A chart room should be an afternoon's brass.

The **Creative Rift Drive** has no recipe, and `ResourceIntegrityTest` asserts that it has none rather
than merely skipping it — a creative-only item that quietly became craftable is a balance hole nobody
would think to look for.

`RecipeBalanceTest` asserts all of that: one predecessor per tier, a step of between ×1.7 and ×3.0,
an anchor under half the cost of the cheapest drive, and every ingredient priced. This is not
belt-and-braces — an earlier version of these recipes quietly asked for *four complete Mk III drives*
and five Singularity Cores to build one Singularity, which cascaded to something like thirty-six
Refined Radiance. Nobody writes that on purpose; it is what happens when a 5×5 pattern is drawn for how
it looks and never counted up.

## Configuration

`config/aerowarptics-server.toml` — range, cost formula, arrival search and clearance buffer, the
flight through the rift (aperture stand-off and size, approach speed, passage duration, run-out
length), permissions, failure behaviour, and a block of settings per drive tier. Corridor duration and
run-out time come from the tier, so a Singularity drive crosses faster than a Mk I. The corridor needs
no settings of its own: it is flown inside the entry aperture, at the speed the hull is already doing.

`config/aerowarptics-client.toml` — particle density, rift distortion, corridor effects, screen shake,
effect volume.

---

## Diagnosing a warp

When a warp *looks* wrong, a screenshot rarely says why. Set `debug.traceWarps = true` in
`config/aerowarptics-server.toml` and every warp writes its own account to `latest.log` — at INFO, so
there is no logging configuration to change to collect it. Off by default; a warp costs about ten
lines when it is on.

```
[warp] plan drive=(112, 78, -340) ship=8f3c… bow=(0.00, 0.00, -1.00) hull=(24.00, 14.00, 62.00) span=62.00 mass=41820.00
[warp]   entry rift=(112.50, 82.00, -425.00) r=17.15 standoff=24.00
[warp]   transit run=66.00 ticks=60 speed=1.10b/t
[warp]   corridor (112.50, 900.00, -363.00) -> (112.50, 900.00, -1173.00) ticks=90
[warp]   exit rift=(2044.50, 96.00, 811.00) r=17.15 emerge from=… to=… ticks=35
[warp] APPROACH -> TRANSIT after 37t  centre=(112.50, 82.00, -394.00) bow=+0.04 centre=+31.04 stern=+62.04 CROSSING
[warp] TRANSIT -> CORRIDOR after 60t  centre=(112.50, 82.00, -460.00) bow=-66.00 centre=-35.00 stern=-4.00 THROUGH
[warp] teleport into the corridor (…) -> (…) (818.00 blocks) crew=2
```

Everything on a crossing line is a distance **still to go**: positive has not reached the aperture
plane, negative is through it. The two that matter:

- `APPROACH -> TRANSIT` should show `bow` at about **zero**. If it is strongly negative the run
  overshot; if strongly positive it gave up early and hit its tick limit.
- `TRANSIT -> CORRIDOR` should show `stern` **negative**, and the line should say `THROUGH`. If it
  does not, the hull was teleported with some of it still in plain sight — which is exactly the bug
  that made ships appear to vanish on contact with a rift.

`WarpTraceTest` pins that sign convention, so the numbers in the log mean what this says they mean.

---

## Building

```bash
./gradlew build
```

Every build drops its jar straight into the Minecraft instance named by `deploy_dir` in
`gradle.properties`, so the only step between changing code and flying a ship is a rebuild. Earlier
builds of this mod are cleared out of that folder first — two `aerowarptics-*.jar` files would stop
the game starting on a duplicate mod id.

Deployment is a finalizer of `build`, so a jar that compiles still lands even if a test fails; a
compile failure produces no jar and copies nothing. Blank `deploy_dir` to turn it off, override it per
machine in `~/.gradle/gradle.properties`, or for one build with `-Pdeploy_dir=...`. If the game is
running it will hold a lock on the jar and the copy will fail loudly rather than leaving you testing
the previous build without knowing.

The mods this builds against live in `Depends/`. Create and the Aeronautics bundle ship several of
their libraries as jar-in-jar artifacts; those are unpacked into `Depends/lib/` so javac can see them.
Both directories are on the compile classpath and on the dev runtime classpath.

```bash
./gradlew test
```

---

## Limitations

**Cross-dimension warping is not implemented.** Sable binds a sub-level to one `ServerLevel`: its
blocks live in that level's plot grid, its rigid body belongs to that level's physics system, and
`PhysicsPipeline.teleport` takes a position and an orientation but no destination level. Sable 2.0.3
exposes no way to hand a sub-level, its plot chunks and its loading tickets to another level, and
rebuilding the ship block by block in the target dimension is exactly what an addon should not do.

Rather than fake it by teleporting players, the addon refuses the warp with a clear reason and leaves
a real extension point: register a `CrossDimensionWarp.Handler` and set `allowCrossDimensionWarp`, and
the rest of the pipeline — anchors, cost, validation, the state machine, the effects — already works
across dimensions unchanged.

**Airship ownership.** Neither Sable nor Simulated tracks who owns a vessel, so authority over a drive
is proximity plus presence: you must be within reach of it and, by default, standing on the ship it is
about to move. Anchors have real owners, and private anchors are enforced.

**A warp does not resume across a restart.** If the server stops mid-sequence, the drive comes back in
its fault state and the ship's claim is released. A hull that was out in the corridor is put back where
it set off from: the departure pose is written onto the airship itself, not onto the drive, so it
survives independently of the machine that started the journey.

**Textures and models are procedurally generated.** The geometry and its texture sheet come out of one
generator script, so box UVs cannot drift apart and the resource-integrity test can prove they do not
overlap. Three things do most of the work of making that look like modelling rather than boxes:

- **The painter is face-aware.** Box UV lays a cube out as a known arrangement — a strip of top and
  bottom over a strip of the four sides — so a rect can be split back into faces and each lit
  differently. Top faces bright, sides mid, bottoms dark. That single change is what makes a stack of
  boxes read as a solid object instead of a flat sticker, and it puts the shading on the same footing
  as the blocks around it.
- **Every face is bevelled**, one pixel, darker at the border and lifted on top edges. An edge you can
  see is most of what separates a modelled object from a box.
- **Cubes can be rotated**, which is what allows chamfered plinths and raked panels — shapes the
  model previously could not express at all.
- **The sheet size is per-model**, so the Astrolabe's three-block span gets a 512 sheet while everything
  else stays on 256. A packer that could only work at one size would have forced the big table's cubes
  to share pixels with each other.

Detail is per-face too, so a row of bolts follows the face it is on instead of marching across a seam
onto the next one.

**Animations are generated from the same place.** A clip is a table of numbers, and a table of numbers
maintained by hand drifts; generating them means a change to how the drive idles is one edit rather
than nine, and every state is guaranteed to touch the same bones so nothing is left frozen in a pose
the previous state put it in. The gear train is the machine's pulse — how fast the wheels turn is how
hard it is working — and the strain states displace the housing and shafts as well, so a drive at the
edge of what it can hold looks like one.

An artist would still want to replace the sheets by hand, but the shapes are now worth painting.
