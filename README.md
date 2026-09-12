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

**What it looks like is what it does.** The drive is a **tesseract**: an outer cage the size of the
block, a smaller cage hung in the middle of it, and eight diagonals joining the two corner for corner
— the shadow a four-dimensional cube casts into three, which is as close as a block-shaped object can
come to saying *this folds space*. The rift hangs inside the inner cage, and two toothed rings turn
around it on crossed axes, the way the rings of an armillary sphere do. Both readings come from the
machine rather than from a clip playing regardless: the animation controller multiplies its playback
speed by a figure that rises with the charge, so the gears wind up from a crawl to a blur as the drive
fills, and the rift is scaled by the renderer straight from the charge, so a drive a third full and a
drive ready to go can be told apart from across a hangar. The geometry, its nine clips and all five
tier sheets come out of `tools/rift_drive_model.py` in one run; the tiers differ in the colour of the
rift and the sheen on the inner cage, and in nothing else.

**Dressing the rift.** A **Rift Modulator** bolted against any face of a drive lets its pilot override
that tier palette: a core colour and a rim colour, each chosen from a swatch of sixteen - left click
sets the core, right click sets the rim - one of twelve themes, and how strongly the whole thing reads, from a quarter to double strength. Applied wherever
the drive's colour would otherwise show, from the aperture through the corridor wash to the exit
shockwave; the face itself blends core to rim rather than drawing one flat tint, so an unconfigured
Modulator - core and rim left equal - looks exactly like an undecorated drive. It costs a
trickle of Rift Essence, charged only while the linked drive is actually running a warp, and it takes
its own facing from the drive the moment it is placed rather than needing to be aimed. Run the tank dry,
or take the module away, and the rift falls straight back to the drive's own tier colours - a Modulator
is a filter over a warp, never a second thing the warp depends on to happen at all. A Display Link
mounted beside the module reads the drive it is dressing, the same eight readouts the drive itself
offers.

**Themes are geometry, not palettes.** Each one changes three things: what stands around the aperture
for as long as it exists, how it winds up before the hole is there, and how the pane comes apart. That
last one matters more than it sounds. Cells this small, seen for a second, all look alike in a still -
so **motion is the tell**, and a first pass at this which cut a gear-shaped fracture and then threw the
pieces away like glass just looked like glass cut into ten pieces.

| Theme | Standing around the rift | How it opens | How the pane goes | Inside the bore |
| --- | --- | --- | --- | --- |
| Standard | nothing | struck: cracks race out from an impact | thrown clear, tumbling face-over-edge | the plain lit tube |
| Clockwork | a toothed ring gear, three counter-rotating satellites meshing outside it, and a clock face with hands across the aperture | a mechanism winding up under load | ten iris blades sweep aside around the centre, never leaving the plane | the plain lit tube |
| Arcane | twenty-four written glyphs circling the rim, each flickering on its own clock, and a seven-pointed figure inscribed on the face | a circle drawn, charged, and discharged inward | ignites where it stands, rim to centre, and burns out | the plain lit tube |
| Ember | eighteen fangs biting inward over the rim and breathing, and seven eyes blinking on their own timers whose slit pupils follow you | a burn front spreading outward, fangs sharpening into place | chars, curls, lifts and darkens like burning paper | the plain lit tube |
| Starlight | three orbits tilted out of the aperture's plane, carrying ring bands and four planets | stars arriving one at a time out of nothing | disperses on a slow spiral, twinkling | the plain lit tube |
| Starblocks | a swirl of mottled blue and white arms turning in on the middle, starlines converging on it from every side | a still starfield drawing out into long white lines that meet at a vanishing point | pinned at its inner edge while its outer edge races away - smeared, not broken | a mottled blue-and-white helix turning round the crew, and starlines rushing past on every side |
| Bedrock | three segmented rings outside the rim, still turning; a gold event-horizon halo hugging the rim; light bent round it in stretched arcs; dark ripples rolling outward | the ship's rings spinning up, alternate ones counter-turning, while the halo tightens into being in the middle | wound round a black middle, hardest at the centre and gold at the rim - the frame bending, not the pieces moving | a black tube with faint gold ripples rolling past, and the halo waiting at the far end |
| Boldly Gone | a three-shell spatial bubble drawn out along the heading, stars streaking past it and a bright point at each end; at the moment it engages, a white flash and a blue ring blown outward from the rim | the lens stretching along one axis while stars streak past it as forward lines | stretched along one axis, white the instant it engages and cooling to warp blue | warp-field hoops, each warped out of round, rolling past, and long bright forward lines |
| Ludicrous Speed | a glowing tartan across the face in an uneven eleven-thread sett, warp and weft sliding at different rates, with bands of its colours round the rim | white starlines accelerating and fattening into blinding bands of colour, which then weave - warp first, then weft | snaps onto a crossing grid in the sett's own colours, warp and weft parting at different rates | the whole wall woven: warp running its length, weft hoops rolling past |
| Eventful Horizon | the drive's three gimbal rings, now enormous, turning violently round the hole on three axes, over a polar grid of spacetime twisted into the middle and jolted to a new shape every two ticks | a spiked core spinning up inside three rings on three axes | torn into the middle and pulled thin, shuddering - arc-light to red to black | the wall's spacetime grid bent and buckling, jolting between shapes |
| Vworp | soft spiral currents of orange and blue turning fast; a clock dial turning backwards round the rim; a police box tumbling end over end at the centre | the vortex winding in from the rim while the box fails to materialise in the middle, over and over | travels in five hard steps, alternate teeth orange and blue | the time vortex: wide currents wound round the tube, clock dials rolling past, and the box tumbling towards the crew from far down it |
| Improbability | one of a sperm whale, a bowl of petunias, a knitted doll, a cube, a cup of tea or a star - and the same thing in two more universes, faintly | the same, rerolled every seven ticks | each piece picks one of the other motions from its own seed | three of those forms hanging in the tube ahead, each on its own reroll |

Two details are worth calling out. Starlight's orbits are **genuine circles in three dimensions**, not
ellipses drawn flat, so a planet passes in front of the hole on one side and behind it on the other -
and the far half is hidden by the aperture's own opaque face for free, because the glow pass is
depth-tested. Nothing arranges that; it falls out of drawing the orbit honestly. And every stroke of all
of it is drawn in **the colours the pilot picked, untinted**: themes separate themselves by shape and
motion and never by overriding a chosen hue, which is the point of having a sixteen-swatch picker at
all. A hell portal in pale blue is entirely possible.

That holds for the five originals, and deliberately not for the seven after them. **The borrowed
themes bring their own colours** - see `ThemeLook` - because each is trying to look like something the
player has already seen, and most of what makes hyperspace recognisable is that it is blue and white.
The palette is applied when the rift is created, so the face, the bore, the fire, the glass and the
lightning all agree; the Modulator's intensity still applies to them, its swatch does not.
Improbability rolls a fresh palette from each rift's own position, so the two ends of one jump are
never the same colour.

Most of those seven also draw somewhere the originals never do: **inside the bore**. The crew fly
through the throat for the length of a warp, and that tube is where hyperspace, a warp bubble and the
time vortex actually happen - so each borrowed theme dresses its inside, just within the solid wall,
where nobody outside can see it. There is still nothing painted over the screen; `WarpCorridorOverlay`
explains why there never will be.

The seven themes after Starlight are **open nods to jumps from elsewhere**, named just off-target on
purpose. They borrow nothing but a look: each is still a palette, a fracture shape, a mote, a
sound, some furniture and a dressed bore, and the warp underneath is the same warp the mod has always run. Nothing about a Modulator
has ever been able to change how a jump behaves, and that has not changed to accommodate them.

None of this can weaken the thing an aperture is for. It is all additive glow that writes no depth, so
it can only ever add light to what is already on screen - it cannot punch a hole in the opaque membrane
and cannot reveal a hull inside the aperture, whether it is drawn outside the rim or straight across the
face. That is why Ember's fangs are allowed to reach *inside* the rim, which is most of what separates a
mouth from a doorway.

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

**The opening is the size limit.** This is not a balance decision: a doorway is a doorway. A vehicle
wider or taller than the ring has nowhere to be at the far end that is not inside the frame it would be
arriving through. Something too big is stopped dead on the near side and told why. Building a bigger
ring is how you pass a bigger machine, and that is what makes free-form sizing the point of the block
rather than a convenience.

#### What it costs

**Essence to open, essence and rotation to hold** - and **only at the end that dialled**. A dial
spends Rift Essence from the gate you are standing at, a base charge plus a per-block charge so that a
bigger doorway is a bigger tear. That same gate then pays twice over for as long as the connection
stands: stress, and a second helping of essence every second. Let its shaft stall or its tank run dry
and the connection drops.

The upkeep is the newer of the two and the one that changes how a gate is played. Essence used to be a
price you paid once, which meant a doorway was a thing you built and then simply had - the only ongoing
cost was stress, and stress is a number you solve once by building a bigger engine. A tear is not like
that. Something is holding it apart, and this is what that costs: a base rate plus a per-block rate, on
the same reasoning as the dial, taken from the tank once a second. A gate left standing open across a
base is now a bill, which is what makes a **Spatial Siphon**'s output something to plan around rather
than something that accumulates. Both figures are config, and setting them to zero puts the old
behaviour back exactly.

The gate at the *other* end pays nothing and needs nothing but to exist, be enabled, and not already be
busy. That is the point: a destination gate is a doorway you build at a mine and walk away from, and
requiring a working drive at both ends means every remote gate is a second base. It also has to be
checked at only one end in the code, which the first version got wrong - a passive far gate hung up on
its very first tick and dragged the dialling one down with it, so gates simply did not work.

A gate that is not holding an aperture costs **nothing at all** - no stress, no essence - which is what
stops a player being punished for building a doorway they use twice a day. Create caches a block's
impact, so the gate detaches and re-attaches itself from the kinetic network whenever that changes -
both when an aperture appears or goes, and when this end stops being the one paying for it.

The upkeep is simulated before it is spent, so a gate that is a millibucket short shuts with that
millibucket still in it. It reads differently at the tank window: not enough is a supply problem, and
an empty tank is a leak, and a gate that drained itself on the way down would report the first as the
second.

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
- **A gate works the same whether it stands on the ground or rides a hull.** Build one on an airship or
  a land vehicle and it opens, dials, and notices crossings exactly as a grounded one does - every
  position it works with is carried across the boundary between the hull's own plot and the world it
  is really moving through, so the check is always made against where things actually are rather than
  where the blocks happen to sit in storage. A traveller's facing turns correctly whatever heading the
  hull is sitting at, not just the four a grounded gate is limited to; a *vehicle* crossing a gate that
  is itself aboard another hull is turned the same simplified way a grounded gate has always turned one
  - honest for a level deck, an approximation for one actively banking as something drives through it.

#### The pane

The hole in a gate is a **block**. When a gate takes a connection it fills every cell of its opening
with **Rift Portal** blocks and empties them again when it lets go, the same way a Nether portal
stands in its own frame - lit by the game's light engine, drawn from an animated shimmer that tiles
and loops, non-solid so you walk straight through, and as unbreakable as bedrock because there is no
sensible thing for a pickaxe to do to a hole in space. The cells come from the opening's own mask, so
an L-shaped ring gets an L-shaped pane rather than a rectangle bulging through the frame.

It is **semi-transparent**, and deliberately not evenly so: the alpha climbs with the brightness of the
shimmer, so the dark of the field is nearly half see-through and the filaments running across it are
almost solid. A pane at one alpha is a sheet of tinted glass however good the colours on it are, and a
portal is not a window.

**Opening and closing are their own animations, not just the idle shimmer switched on and off.** A
pane carries a `stage` blockstate - `opening`, `open`, `closing` - which the gate writes as it moves
through its own states: `DIALLING` shows `opening`, the held `OPEN` state shows the calm shimmer,
`CLOSING` shows `closing`. Each stage is its own texture, generated from the same shimmer field as the
idle one but multiplied by a seamless breathing pulse - dimmed and thinned rather than merely faded,
so a half-formed pane reads as thin and dark instead of a see-through version of the finished thing.
`opening` breathes up from nothing to full and back down, over and over, for as long as the gate is
dialling; `closing` is the same pulse the other way round, full at the seam and hollowed out in the
middle, for as long as it takes the connection to fall away.

That is a motif rather than a literal progress bar, and deliberately so. The honest version - a pane
that grows from nothing to full height over the exact ticks a dial takes - needs something that knows
when each individual pane started, and a block with no block entity has nowhere to keep that. What
Minecraft's animated textures actually run on is the world's own tick counter, the same clock for
every copy of the texture everywhere, not a stopwatch that starts when a particular block is placed -
so a strip built to play once from a placement would instead show whatever frame the global clock
happened to be on, and every gate in the world would be out of step with its own doorway. A looping
pulse sidesteps that: it always reads as "forming" or "unravelling," for as long as the state holds,
regardless of when it started. This is the same trade this mod made once already for the frame's own
ring animation, in the version before this one, and it is made again here now that the animation has
moved from the ring onto the pane itself.

**The pane moves nobody.** A Nether portal teleports from `entityInside`, and that is exactly the
thing a gate must not do: a crossing here is a change of side between two sightings the gate took
itself, for the reasons under [the rest of the rules](#the-rest-of-the-rules), and it stays in the
controller where both sides can be seen at once. The block is scenery over the top of it - which also
means a pane that somehow outlives its gate is inert rather than a trap.

The gate owns the cells and re-asserts them every validation sweep, so a pane interfered with heals
itself; and the block keeps a backstop for the one case a gate cannot cover, a ring broken while its
chunks were not loaded. On a random tick it walks out in the four directions of its own plane, and if
every walk does not end on frame it removes itself. That is the same question the Nether portal asks
for the same reason: an unbreakable block with nothing holding it up is a permanent scar.

##### What it used to be, and why it changed

Until this version the opening was **drawn** rather than built: a client-side aperture, the same one a
Rift Drive tears, held up by the gate's own client tick and bent to the ring by sampling the opening's
reach at a hundred and ninety-two angles. It looked good and it cost a great deal to keep honest.
It had to be renewed at every viewer or it faded; it had to be fitted to an ellipse and then pulled
back in wherever the mask said the hole was not; it was invisible to Ponder, which has no live gate
state to read; and it lit nothing, because a drawn thing is not in the world.

A block is all of that for free. `RiftEffectManager` keeps the apertures that genuinely are effects -
the ones an airship flies through, and the small one inside a Rift Chute - and the doorway you can
walk up to and touch is now a thing that is actually there.

One lesson from the drawn version is worth keeping, because it cost a day. A held aperture's hold is
effectively forever, and ages are interpolated as **floats** - past sixteen million, a float cannot
tell one tick from the next, so an entire twenty-tick close landed inside a single representable step
and the hole simply snapped out of existence. Letting go ends the hold *at the moment it happens*
rather than winding the clock forward to the end of it, which keeps the animation in numbers small
enough to count in. That is still how the chute and the warp rifts close.

### Rift Probe

Every other destination in this mod is a place somebody carried a block to. That makes the map a
closed loop — you can only warp to where you have already walked — and the probe is what opens it.

Point the dial at one of eight bearings, set a distance on the slider, and throw a **sounding**. The
probe tears a rift open at that spot, holds the far ground long enough to read it, and brings back the
same top-down survey the Astrolabe draws of an anchor. You then decide on exactly the evidence you
would have had anywhere else: the shape of the coastline, how high the ground stands, how much of the
picture actually came back.

The waiting is not theatre. `DestinationSurvey` reads only chunks that are already loaded, which is
right for an anchor — somebody has been there — and useless for a probe, where nobody has and the
terrain may not exist yet. So `ProbeTicket` claims the region first and the probe polls until enough
of it has arrived. **The progress bar is the server generating the world.** When it has not finished
by the configured timeout, the probe reads what is there and reports the reading as *thin* rather than
either lying about it or waiting forever.

Bearings are normalised, which matters more than it sounds: a north-east sounding of three thousand
travels three thousand blocks, not four thousand two hundred. Built from raw `(1, 1)` offsets, diagonal
soundings would quietly cost the same and reach forty percent further, and nobody would ever report
that as a bug — they would just stop using the other six directions.

Rift Essence pays for it, and reaching further costs more. That is the loop closing: a Spatial Siphon
collects essence from journeys you have already made, and a probe spends it finding somewhere you have
not.

What comes back is a **fix**, not an anchor — a bare position, with nothing at the far end. Setting a
course from it arms the drive exactly as choosing an anchor does, and the flight is identical. Bring an
anchor with you if you want to come back.

#### Courses that are not anchors

This is the one change the probe forced on the rest of the mod. The drive used to store a destination
as an anchor UUID and re-resolve it through the registry at four separate points. A sounding has no
anchor at the far end and never will, so a destination is now a `WarpCourse`: either an anchor to look
up or a fix to fly at, exactly one of the two, enforced in the constructor. A course that was neither
would be a drive that believes it has somewhere to go and cannot say where — which surfaces as an
aborted warp halfway down a corridor rather than as a refusal at the console.

Everything downstream asks the course rather than the registry. The two things only an anchor can
answer — is it switched off, may this player see it — are simply skipped for a fix, which is correct:
a position cannot be switched off, and the sounding that produced it was already vouched for.

### Spatial Siphon

A glass vessel on a brass foot. Every time the ship it is bolted to comes out of a rift, it catches a
draught of **Rift Essence** — a random amount, with a longer jump widening the odds rather than
guaranteeing anything. Several aboard one hull each roll separately.

Nothing consumes the essence yet. It is a real fluid with a bucket and a fluid-handler capability, so
Create's pipes can drain it into a tank, and it is there to be the raw material dimensional travel gets
built on later. The yield is deliberately a lottery rather than a rate: it should be a by-product of
travelling, not a reason to bounce a ship between two anchors.

### Rift Fissure

Every other rift in this mod is one a machine tore and closed again. A fissure is one that was torn
and never closed, and it is the only thing here a player finds rather than builds.

**Ruins generate in the overworld** where somebody's gate went wrong — a broken ring standing in a
paved yard, a sunken vault with its roof fallen in, a survey mast on a hill. Three variants, one per
site, written by `tools/rift_scar_structure.py` for the same reason the Ponder scenes are: a structure
laid out by hand is a binary blob nobody can review afterwards.

Inside one, invisible, is the fissure. It has **no model, no hit box, no light and no drops**, which
is a stronger claim than it sounds:

- The block's render shape is `INVISIBLE`, and what a player with goggles sees is the client's own rift
  effects — the same tear an airship flies through, held by a holder id from the fissure's client tick.
  So it looks like every other torn rift in the mod rather than like a special case, and taking the
  goggles off lets the aperture collapse the way an unrenewed one always does.
- **The hit box depends on who is looking.** Minecraft asks for a block's shape with the looking entity
  in the collision context, which is exactly the question "who wants to know" — so `getShape` returns
  nothing at all unless that entity is wearing Rift Infused Goggles. Without this the block draws a
  selection outline around thin air, which gives the whole thing away. The *collision* shape is empty
  for everybody, deliberately: a wall that exists for some players and not others desynchronises.
- No light level, for the same reason. A glow in an empty room is a sign saying where the invisible
  thing is.

**Rift Infused Goggles** are Create's own `GogglesItem` with a rift-ground lens. Create keeps a list of
predicates rather than one item id, precisely so an addon can add a pair, so these read every machine
in every mod that answers to goggles — and this mod asks a second, narrower question of the head slot
for the thing only they can do.

**Emptying one is the Spatial Siphon's other job.** Stand a vessel within a few blocks and the fissure
hands essence over a tick at a time, the tear shrinking visibly as it goes, until it seals over for
good. The *fissure* does the handing out rather than the siphon doing the taking, and that is the only
arrangement that survives two vessels round one tear: the reservoir is the finite thing, so whatever
owns it has to be what shares it, or two siphons each politely taking "what is left" take it twice.

How much any one holds is rolled from **its own position** rather than from the level's random, which
means the answer exists before the first tick, is the same for the server and every client without a
packet, and does not change if the chunk unloads before anything has drawn from it. `RiftFissureTest`
pins that, along with conservation — what the vessel gains is what the tear loses, to the millibucket,
at every rate that divides into a reservoir and every rate that does not.

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

### Telling the client it was a teleport

Sable syncs a sub-level's pose as a stream of snapshots, and there is no "that was a teleport" flag
anywhere in it. A client simply sees the ship somewhere else on the next tick and carries on — which
is fine for a ship that flew there, and badly wrong for one that jumped four thousand blocks.

`SubLevelEntityCollision` sweeps an entity against a moving platform by mapping the entity's box into
the sub-level's frame at the **previous** pose and at the **current** one, then unioning the two.
Across a long warp that union is a box the length of the whole journey, and once its volume passes
`1.25e8` Sable logs

```
Enormous local sub-level collision bounds, quitting.
```

and abandons the collision entirely. With no collision there is no deck under the crew, so they drop
through the ship; and with the hull's client-side bounds nonsense, it stops being drawn — which looks
exactly like the airship having been deleted. **One cause, both symptoms**, and only on long jumps
because a short one stays under the limit.

The line falls in an awkward place. Two real warps from the same session, back to back: 4,626 blocks
tripped it and 4,221 blocks did not, both landing within a few percent of the limit. That is why the
symptom read as intermittent rather than as "long warps are broken".

The server never had the problem — `Airship.relocate` collapses the pose delta on its own sub-level
the moment the teleport lands, which is what the `updateLastPose()` there is for. `FoldCrossings` is
that same collapse applied to the copies the clients hold: when a hull crosses the fold, everyone who
can see either end is sent the ship's id, and their client collapses the delta as soon as the jump
shows up.

It is a *window* rather than a single instruction, because the notice and Sable's pose snapshot are
independent streams with no ordering between them — the notice usually arrives while the client still
believes the ship is at the old position, and collapsing then would do nothing at all. So the notice
opens a watch, and the collapse happens on whichever tick the jump actually appears. The watch runs
on both `ClientTickEvent.Pre` and `.Post`: entity movement happens *inside* the client tick, so a
check that only ran afterwards would be a tick late and the crew would already be falling.

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

Every surface a rift is made of is **two-sided**, and all of them have to be. People stand on both
sides of an aperture, and a gate is walked through from either end. The additive half of it used to
borrow `RenderType.lightning()`, which does not disable culling - so the fire, the torn rim, the cracks
and the seal were all drawn on one face only, and the other side of a gate was a bare dark disc. It
also writes depth, which is not a thing a glow should do to the world behind it, and it draws into the
weather buffer. `AWRenderTypes.RIFT_FIRE` is the same additive blend with none of that.

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

### Lightning

The aperture sparks, the corridor sparks, and every so often one reaches all the way down and hits
the ground. A handful of points around the torn rim discharge outward into open air; a handful more,
further down the bore, arc across it from one point of the wall to another; and a couple more, rarer
than either, reach straight down from the rift for whatever ground is under it. `RiftLightning`
generates the whole field the same way `RiftShatter` and `RiftDebris` do — deterministically, from the
rift's own seed, so two players watching one aperture see the same bolts without a byte being sent
about them.

Each rift carries a small **fixed number of emitters**, not a dice roll every tick. Every emitter has
its own period and its own phase, so "random sparking" is really a handful of independent clocks
landing on different beats: read the field at any moment and some are dark while one or two are
mid-flash. A dice roll every tick would drift out of sync between two clients the moment either of
them dropped a frame; a clock cannot.

A bolt off the rim is **free at one end**: it jags outward from its point on the rim and narrows to
nothing at its tip, the same shape a crack running out from an impact takes. A bolt across the corridor
is **anchored at both ends**: it jumps from one point of the bore's wall to another, dipping towards
the axis at the middle of its arc so it reads as crossing the open bore rather than merely running
along the wall it starts and ends on — pinched to nothing at both ends rather than at one. A ground
strike is anchored at both ends too, the same way, except its two ends are a point near the rift and
wherever the ground actually is — the one bolt drawn in raw world space rather than against the rift's
own local frame, because there is no "local frame" that reaches from an aperture down to a block that
might be forty blocks below it at any angle.

Neither is a particle. All three are geometry — a jagged strip of quads, drawn additively through the
same `AWRenderTypes.RIFT_FIRE` pass the fire and the cracks already use — for the same reason a spark
at corridor speed would cross the view in a single frame if it were a particle, which is the same
reasoning `WarpCorridorOverlay` gives for keeping the corridor's own screen effect off streaks with a
direction.

A bolt draws a fresh path every time it fires rather than replaying the last one: which flash an
emitter is currently on feeds the same hash `RiftShatter` uses, so a new cycle number is a new set of
kicks along the path without anything needing to be remembered between flashes. `RiftLightningTest`
holds the parts that would fail silently: a field where nothing ever flashes, one where every emitter
agrees, or one that draws the same bolt twice in a row.

**A ground strike is rarer than the other two on purpose** — real lightning striking the ground is the
exception, not the rule, and a field as dense as the rim's own sparks would read as artillery rather
than as weather. It is also allowed to simply not happen: the client raycasts straight down from a
point near the rift, and a flash that finds nothing solid within forty-eight blocks — open sky under a
high-flying drive, most often — draws nothing that cycle rather than a bolt trailing off into empty
air.

**Whatever a strike actually hits gets marked.** `RiftShimmer` stands a soft, breathing glow just proud
of the struck block's own faces for a few seconds, tinted the rift's own colour, fading in fast and out
slowly. It is deliberately its own class, independent of the rift that threw the strike: the mark
belongs to the block for exactly as long as it stands there, the way `SummonBeacons`' pillar belongs to
the ground it was called down onto rather than to whatever is doing the calling. A second strike on a
block already shimmering refreshes the mark rather than stacking a second one on top of it.

Toggled independently as `riftLightning` in `config/aerowarptics-client.toml`, the same as the rift's
own distortion disc — this covers all three kinds, the shimmer included.

### Which way is forward

Nothing in Sable or Aeronautics marks a bow, so the pilot marks it, on the drive, in the airship's own
frame. The setting is one of four quarter turns - **Forward**, **Right**, **Back**, **Left** - measured
from the Rift Drive's own front face.

Relative, not absolute, and that is the whole point. A compass bearing would be a lie the moment the
vessel came about: set it to north before a long turn and the rift would tear itself open across the
beam. Because the setting lives in ship space, it is only turned into a world bearing at the instant a
flight is planned, using the hull's current pose. Aim it at the nose once and it stays aimed at the
nose, whatever heading the ship is on and however far it has been sailed since.

The drive wears a **needle** across the top of its outer cage, which points at the current setting. It swings a quarter turn each time the setting is cycled, so the bearing can be read
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
height at which the hull's underside would sit the destination's **arrival height** above the anchor,
worked out from the ship's own footprint rotated into the orientation it will arrive with. A skiff clears the anchor
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

**The arrival height belongs to the destination**, not to the server. It used to be one figure,
`arrivalGroundBuffer`, and no one figure is right everywhere: six blocks suits a meadow, a harbour
wants a ship low enough to step off, and an anchor on a peak wants the hull clear of the ridges round
it. So a Warp Anchor carries its own height, set on its panel beside its name, and a Rift Probe has a
height slider whose value travels with the course it sends. `arrivalGroundBuffer` is now only where a
new anchor or probe starts, and what a Rift Beacon and anything saved before this existed use.
`maxArrivalHeight` caps what a player may ask for, and is applied again when the flight is planned so
lowering it catches heights set before the change.

An anchor course carries no height; the anchor's is read when the rift opens, the same moment its
position is, so an owner who raises their anchor while a ship is spinning up is heard. A fix course
does carry one, because by the time it fires the probe that chose it may have been re-aimed or broken.
Either way the height is where the search *starts*, never a promise: a column blocked at that height
still means "come in higher", and the probe's panel says **Arrives from** for exactly that reason.

### Rift Storms

A rare weather in which space is already coming apart, and it cuts both ways. A drive tearing it open
has less to recover from afterwards, so a warp completed during a storm earns a shortened cooldown
(`riftStorm.cooldownMultiplier`, a half by default). But every drive's exit becomes as unsure as a
Singularity's: the Singularity's own `instability` is added on top of the drive's, and the same
scatter roll decides. The Singularity is left as it is - it is the drive the storm borrows from, and
charging it its own figure twice would punish most the one drive already unstable - and a Creative
drive is left alone because it is defined by having no penalties.

Both effects are fixed at the moment the drive acts: the scatter roll when the rift opens, the cooldown
when the warp completes. A storm passing mid-cooldown does not lengthen it again, and one arriving
part-way through does not shorten it. The drive keeps the length its cooldown started at, so the
console's progress bar starts empty rather than half full.

The storm is server-wide, like vanilla weather, and rages only over dimensions with a sky and no
ceiling - vanilla's own test for whether it can rain. It freezes with `doWeatherCycle`. Its clock
(`RiftStormCycle`) and its arithmetic (`RiftStormRules`) are free of Minecraft and tested directly.

`/weather rift_storm [duration]` is grafted onto vanilla's `/weather` rather than living under
`/aerowarptics`, because that is where an operator will look for weather. Brigadier merges a literal
registered under an existing name into the node already there, keeping its operator gate. `clear` is
wrapped rather than replaced: vanilla's own command runs, then the rift storm is calmed too. Rain and
thunder leave a storm alone.

On the client a storm darkens the world the way vanilla's thunder does, and only in the ways vanilla's
thunder does: the sky, the clouds, the fog's *colour* and the daylight, toward violet rather than grey,
with a faint floor so a storm at night still glows (`RiftStormPalette`, tested directly). Never the
fog's distance and never a wash over the screen - those are what the warp corridor stopped doing,
because they dyed the deck the player stood on. NeoForge has an event for the fog colour but none for
the sky, the clouds or the daylight, so those three are a client mixin each (`ClientLevelMixin`), each
adjusting vanilla's finished value at `RETURN` and handing it back untouched when no storm is drawing.

Vanilla reads the rain level for more than rain, so the storm changes that reading only where it is
made (`LevelRendererMixin`): raised in `renderSky`, so the sun, moon and stars are veiled; lowered in
`renderSnowAndRain` and `tickRain`, so vanilla's pale rain fades out as the storm's violet rain
(`RiftStormRain`) fades in. The level's real rain level is never touched - that would make the client
believe it was raining, and drip water off leaves in a desert. The rain is built the way vanilla's is,
but coloured in its own texture (`tools/rift_rain_texture.py`) because a vertex tint can only take
colour away, and shaded with a floor under its block light so it glimmers at night. It falls in every
biome: it is not water.

Overhead, distant bolts of rift fire flash the sky and ground violet (respecting vanilla's *Hide
Lightning Flashes*), and an aurora (`RiftStormAurora`) hangs at infinity - drawn at `AFTER_SKY` in the
sky's own rotation-only frame, before any terrain exists to occlude it, so everything drawn later
simply paints over it. Additive, so it barely shows against a bright day and dominates the night,
which is what an aurora does anyway.

`MixinTargetTest` reads the mixins as bytecode and proves every injection lands on exactly one place in
the game's real classes, and that every mixin is registered - a mixin missing from the config compiles
and silently never applies. Nothing is synchronised between players beyond "a storm is raging";
weather is not an event at a place, so each client decides for itself where the fire falls.

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

### Reading a machine from somewhere else

Every machine in this mod answers a **Display Link**. Point one at a Rift Drive, Rift Gate, Rift Probe,
Spatial Siphon, Rift Chute or Rift Modulator, open the link, and pick what to report:

| Machine | Reports |
| --- | --- |
| Rift Drive | tier, state, charge, sequence progress, cooldown, course, bow setting, last fault |
| Rift Gate | state, essence, fill level, the opening's size, what a connection would cost, last fault |
| Rift Probe | state, bearing, range, scan progress, essence, fill level, what a scan would cost |
| Spatial Siphon | whether it is drawing, essence, fill level, room left |
| Rift Chute | why nothing is moving, whether the link is open, essence, fill level, what is in the tray |
| Rift Modulator | the same eight readouts as the Rift Drive, read through whichever drive it is dressing |

**One link reports one value**, which is Create's own arrangement rather than a limitation: a console
that reports four things is four links stacked up the side of one Display Board, each writing its own
row. An earlier version of this offered a single "Overview" line with everything on it, and it read
badly everywhere — truncated on a nixie tube, clipped on a sign, and stripped of its separators on a
Display Board, whose flap alphabet has no em dash in it.

The numeric readings — charges, progress, essence, costs — are bare numbers, so a nixie tube can carry
them and a Display Board renders them through its numeric flaps rather than spinning the alphabet round
to reach a digit. Readings with nothing to say clear the target instead of writing "None": no fault, no
course set, an empty chute tray. So does a machine that has been broken, so a sign never keeps a stale
reading of something that is no longer there.

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

## Teaching the mod

None of the above is discoverable. A Rift Gate is forty blocks of wall that does nothing until it is
powered, dialled and paired with a second gate somewhere else, and a player who builds one and waits
learns nothing from the silence. Four separate systems address that, aimed at four moments.

### The Navigator's Handbook

The others each answer a question a player has already got as far as asking. Ponder shows one machine
working, JEI says what an item is, and the advancement tree says what to do next. None of them answers
*how do I fly a ship somewhere*, because that is eight steps across four blocks and no single scene
holds it — so there is a book.

Craft it from a book, a brass sheet and an amethyst shard, right-click, and it opens as a book: two
pages at a time, a ribbon per chapter along the top board, arrows at the corners, a contents page that
jumps. Nine chapters — the drive, anchors and charts, launching, the probe, gates, essence and cargo,
fissures, and what to do when nothing happens — twenty pages, most with an animated diagram at the top
of it.

The animation is not decoration and neither are the diagrams. A book that jumped between spreads would
give a reader no sense of where they were in it, which is the one sense a book has and a wiki does not:
a page turned forward comes off the right, a page turned back comes off the left, and after two of them
nobody needs telling which arrow does what. The leaf is drawn squashing towards the spine — a
horizontal scale about the gutter, which is as close to foreshortening as a flat GUI gets — with a
shadow thrown on the sheet it is passing over, because a cross-fade carries none of that. And the
interesting half of most of these machines is a thing that happens *over time*: a shaft turning, a
needle settling on the bow, a traveller entering one gate and leaving another the right way round. A
still picture of a Rift Gate is a rectangle.

What is written lives in `guide/GuideBook.java` and what it looks like lives in the screen, and both
halves are free of Minecraft where they can be — which is what lets `GuidebookTest` prove things a
guide book normally has to be read to discover:

- every line has a translation, so no page renders a raw key;
- every page has room for the words on it, measured with a font model that errs towards over-wide, so
  no sentence is drawn below the paper where nothing would ever tell you it was there;
- every chapter fills whole spreads, so no chapter starts on the wrong side of somebody else's;
- every ribbon lands on its own chapter, and the steps in a chapter run 1, 2, 3 with nothing skipped;
- and every diagram stays inside the box its page gave it, walked across its whole animation rather
  than checked on one frame.

That last one is why `AWDraw` exists: the drawings are written against a single method that puts a
coloured rectangle somewhere, so the screen hands over `graphics::fill` and a test hands over something
that writes the rectangles down. Four of these diagrams overhung their box on the first attempt, by two
to nine pixels each — a marker ring that grew too far, a ring gate half as tall again as its space —
and the symptom would have been faint purple over the first line of the text underneath. Nobody reports
that as a bug. Everybody notices the page looking wrong.

### Ponder scenes

Holding **[W]** over any of this mod's items plays a scene explaining it, in the same window Create
uses for its own. Twelve of them: the Spatial Siphon, the Warp Anchor, the Astrolabe, the Rift Probe,
the Rift Drive, the Rift Chute, the Rift Modulator, the Rift Beacon, two for the Rift Gate — building
the ring, and getting something through it — and two for the Rift Fissure, which is finding one and
closing it. They are grouped into two chapters of the Ponder index, `Warp Travel` and `Rift Gates`, so
a player who only wanted a doorway between two bases does not have to read about airships to find it.

The Navigator's Handbook is listed in both chapters and has no scene of its own, deliberately. A scene
shows a mechanism working and a book has no mechanism; a Ponder scene explaining the book that explains
the mod would be a lesson about a table of contents. What it wants is to be somewhere the index will
show it, which is what a tag does.

Five scenes do not show the thing they are about, deliberately. A warp moves a Sable sub-level and a
Ponder scene has no airship in it; a gate's aperture is drawn by the client from live gate state and a
Ponder level has none; a rift's colour only means anything once one is actually torn, and a Ponder
level tears none either. Rather than animate a lie, those scenes teach what a player can act on before
they reach the console — where the machine goes, what it consumes, what decides whether it will run —
and leave the spectacle to be seen from the deck. That is the more useful half anyway: what people get
wrong about a gate is the ring, the power and the pairing, none of which an animation would have told
them.

Ponder scenes are ordinary Minecraft structure templates, and Create builds its own by standing in a
world with a schematic wand. That is not something a build can reproduce, so this mod's are written as
code and generated:

```bash
python tools/ponder_schematics.py
```

The layouts are then readable, a change to one shows up as a diff, and the base plate and camera
conventions match Create's so the scenes sit at the same angle and scale.

The text needs more care than it looks like it does. Ponder resolves scene text through `I18n` and
**does not** fall back to the string written in the storyboard, so a missing entry is not an untranslated
line — it is a raw `aerowarptics.ponder.rift_gate.text_3` shown to the player mid-tutorial. Worse, the
keys are positional: `text_1`, `text_2` and so on are handed out in the order the `.text(...)` calls
run, so a sentence inserted into the middle of a scene silently renumbers every sentence after it and
puts the wrong words under the right picture. Keeping two files in step by hand loses that game, so the
entries are generated from the storyboards:

```bash
python tools/ponder_lang.py
```

`PonderTextTest` runs the same parse and fails the build when the language file has drifted, when a
scene has no schematic, or when a translation belongs to a scene that no longer exists.

### Advancements

Twenty advancements on one tree, rooted at the Rift Core, branching into the anchor-and-chart line,
the drive-and-warp line, the gates, and the goggles-and-fissure line. They double as the progression
the mod otherwise only implies.

Most hang off vanilla's `inventory_changed`, because "you are holding one" is what most of them mean.
Four things have no vanilla equivalent and get a trigger of their own:

- `aerowarptics:warp_completed` fires for **everyone aboard** when a hull finishes a jump — not just
  whoever pressed the button, because a warp is something a crew goes through together. It reports the
  distance actually crossed, the drive tier that did it, and whether the destination was a probe's fix
  rather than a placed anchor — so `The Long Way Round` can ask for two thousand blocks and
  `Nobody Has Been Here` can ask for a blind jump, without either number or flag being baked in.
- `aerowarptics:gate_travel` fires when a player comes out of the far side of a gate, and reports
  whether they crossed on foot or as cargo. The two are separate achievements: walking in needs a
  dialled gate and nothing else, while taking a vehicle through needs one wide enough at *both* ends,
  which is the part people get wrong. Left together, the hard one would be handed out for the easy one.
- `aerowarptics:fissure_closed` fires for everyone standing near a Rift Fissure as it empties and seals.
  Near, rather than whoever placed the siphon: nothing in the mod records who placed one, and inventing
  an owner for a vessel to hand out an advancement would be a new concept introduced for the sake of a
  toast. Being there is the better story anyway — the tear visibly shrinks as it drains. It reports
  what the fissure held when it was found, so a later advancement can ask for a big one.
- `aerowarptics:ship_summoned` fires when a Rift Beacon calls its bound airship down. It exists because
  the summoner is, by definition, *not aboard*, and so would never appear in `warp_completed`'s crew.
  It is raised when the drive accepts the summon rather than when the hull lands: by then the drive has
  already agreed to everything it agrees to for any warp, and the flight itself is the crew's
  advancement rather than the caller's.

The tree is declared once and generated, because every entry needs the same five things said the same
way and the titles live in a different file from the advancements that use them:

```bash
python tools/advancements.py
```

`AdvancementTest` guards the failures that are silent rather than loud — a criterion naming a trigger
the mod never registers simply never fires, a parent pointing at nothing drops a whole branch off the
screen, and a missing title renders as its own lang key. It checks the tree is connected and acyclic,
that every custom trigger is one `AWCriteria` actually registers, that every icon is an item that
exists, and that nothing is left translated that no advancement uses.

### JEI

This mod adds no recipe types of its own — everything is crafted on a bench or mixed in a basin, and
JEI finds those unaided. What it cannot find is the part that matters: that a drive has to be *built
into* an airship, that a gate needs a partner, that essence comes out of travelling rather than out of
the ground. Those go on each item's information page, which is the first place a player looks after
"how do I make it".

They are kept short on purpose. The long version is the Ponder scene, and anything written twice
eventually disagrees with itself, so the JEI pages say what the item is and what commonly goes wrong
with it and leave the walkthrough alone.

The plugin class is only ever loaded by JEI itself, so the mod runs unchanged without it; the
dependency is declared optional and client-side.

## The screens

Eight of them: the drive's console, the Astrolabe's chart, a gate's dial panel, an anchor's settings,
the probe, a chute's panel and a modulator's panel — which share a palette, a set of drawn shapes and,
the part that was missing, a layout — plus the Navigator's Handbook, which deliberately shares only the
last of those. A
handbook is not a machine, and dressing it in the machines' brass-on-near-black would have made it read
as one more readout screen rather than as something you sat down with, so it has leather boards,
parchment and its own palette in `AWBookStyle`.

Before, each screen carried its own handful of magic offsets, and the only way to discover that two
panels overlapped or that one hung off the edge was to open the game and look at it. Two of them had
exactly one pixel of margin left, entirely by accident. So the layouts now live in `AWLayouts` as
plain arithmetic with no Minecraft in it at all, which means `ScreenLayoutTest` can call them
directly and check that:

- nothing starts outside the window or runs past its interior — measured against the interior, because
  Catnip's box draws its border *outside* the bounds it is given, so the last eight pixels of a window
  are frame rather than floor;
- no two panels share a pixel, and neighbouring framed panels leave room for both their borders;
- every panel is big enough to hold two lines and every button wide enough for its label;
- the terrain preview panel is exactly the size of the survey grid it draws, checked against
  `DestinationSurvey`'s own arithmetic so the two cannot drift;
- each list shows enough rows to be worth scrolling.

That test found two real bugs the moment it was written. One was a caption drawn ten pixels above a
six-pixel band, which put it on top of the panel above it.

### Motion

Deliberately restrained, and all of it attached to something that is actually happening:

- **Bars chase their readings.** A drive told its charge is now eighty percent slides there rather than
  jumping, and a bar that is filling carries a travelling highlight — so "charging" and "stuck at 40%"
  look different, which they did not before.
- **Selection slides.** The marker beside the chosen row on a list eases between rows instead of
  teleporting, which makes it obvious that the list moved rather than the selection.
- **A live gate breathes.** The one connected gate in the dial list pulses gently. It is the single
  fact on that screen worth seeing without reading.
- **The probe's needle turns the short way round.** Re-aiming from north-west to north swings
  forty-five degrees, not three hundred and fifteen. Without that the instrument reads as broken
  rather than adjusted.
- **The handbook turns its pages.** A leaf squashes into the spine and opens out of it on the other
  side, casting a shadow on the sheet below; the ink on a fresh spread settles line by line rather than
  landing all at once; and every diagram on it runs off the same clock. See *The Navigator's Handbook*
  above for why none of that is ornament.
- **The probe sweeps while it is reaching.** There is genuinely no progress to report while chunks
  generate beyond "still going", so the ring is scanned rather than a percentage being invented.

`AWAnim` holds the easing and the chasing value, and is free of Minecraft so the curves can be tested.
The failure mode of an easing function is not a crash — it is a bar that never quite arrives, or one
that overshoots into a shape the eye reads as a glitch — so both are asserted.

### Items in the inventory

Four of these machines have no baked model at all — what stands in the world is GeckoLib and nothing
else — so the item form of each had a flat sixteen-pixel sprite standing in for it. A sprite of a
machine stops agreeing with the machine the moment either one changes, and these had already drifted.

They are now drawn from the block. `GeoBlockItem` carries the geometry, the skin and the idle
animation; the item model is vanilla's `builtin/entity` marker plus the standard block display
transforms, and one `GeoBlockItemRenderer` draws the lot — so a new drive tier needs a texture and a
registry line and nothing else. A Rift Drive turns in your hand, in a hotbar and in an item frame, and
it turns because it is the same model, not because somebody drew a second one.

Two details are decided rather than defaulted. A siphon's fill bone is hidden on the item, because an
unplaced vessel that looks full of essence you have not collected yet is a small lie told in every
slot it sits in. And the animation is keyed per item type rather than per stack, so a double chest of
drives is one animation state rather than fifty-four identical ones.

The Astrolabe is deliberately not one of them. Its GeckoLib model is the whole assembled three-by-three
table and the item is one ninth of that, so the item is the panel's own baked model — which is exactly
what an unformed cell looks like in the world.

`ResourceIntegrityTest` holds all three halves together: the model declares `builtin/entity`, the flat
sprite is gone and stays gone, and something is registered to do the drawing. That last one matters
because `builtin/entity` with no renderer behind it is not a missing texture and not an error — it is
an item that is simply not there.

## Configuration

`config/aerowarptics-server.toml` — range, cost formula, arrival search and clearance buffer, the
flight through the rift (aperture stand-off and size, approach speed, passage duration, run-out
length), permissions, failure behaviour, Rift Storms, and a block of settings per drive tier. Corridor duration and
run-out time come from the tier, so a Singularity drive crosses faster than a Mk I. The corridor needs
no settings of its own: it is flown inside the entry aperture, at the speed the hull is already doing.

`config/aerowarptics-client.toml` — particle density, rift distortion, rift lightning, corridor
effects, screen shake, effect volume, Rift Storm weather.

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

Some resources are generated rather than written by hand. Regenerate them after changing what they
are built from, and re-run the tests, which are what notice if you forget:

```bash
python tools/ponder_schematics.py
```

```bash
python tools/ponder_lang.py
```

```bash
python tools/advancements.py
```

```bash
python tools/probe_textures.py
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

**A sounding cannot leave its own dimension,** for the same reason a warp cannot: a probe hands the
drive a position in the ship's own level, and the drive has nowhere else to take it. It also cannot
survive a reload — the chunk ticket holding that ground open has gone, so a probe interrupted mid-
sounding comes back idle rather than waiting on chunks nothing is keeping loaded.

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
the previous state put it in. The gear train is the machine's pulse — how fast the rings turn is how
hard it is working — and while the rift is open, the drive's inner cage collapses through a point and
comes back the other way round, which is what a tesseract does when it turns edge on.

**Moving parts are checked against each other.** The drive has two rings turning about perpendicular
axes, a shaft, a needle and two nested cages sharing sixteen pixels, and every radius in its generator
is an answer to "what will this pass through". A model that clips still loads and still renders; the
only symptom is a gear tooth flickering through a strut once a second, on a machine nobody is looking
at straight on — silent, which is what most of this suite is for. So the generator refuses to write
one, and `DriveClearanceTest` re-derives the same thing from the shipped file: each turning part swept
into the annulus it occupies over a revolution, and every pair of parts required to be disjoint but
the two that are meant to meet.

An artist would still want to replace the sheets by hand, but the shapes are now worth painting.
