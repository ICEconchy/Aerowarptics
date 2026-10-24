# AeroWarptics

A Create: Aeronautics addon that lets an airship fold space.

Build a **Rift Drive** into a vessel, spin it with Create's rotational force, register **Warp Anchors**
at the places you want to reach, and the whole ship — hull, machinery, cargo and crew — travels through
a temporary rift to the far end.

* Minecraft 1.21.1 / NeoForge 21.1.233
* Create 6.0.10
* Create: Aeronautics 1.3.0 (Sable 2.0.3 + Simulated 1.3.0 + Aeronautics 1.3.0)
* GeckoLib 4.8.4

Licensed under [CC BY-NC-SA 4.0](LICENSE).

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

| Tier | Min / optimal RPM | Stress (per RPM) | Range | Charge time | Cooldown | Efficiency |
| --- | --- | --- | --- | --- | --- | --- |
| Mk I | 32 / 64 | 8 su (512 at optimal) | 4,000 | 120 s | 120 s | 1.0× |
| Mk II | 64 / 128 | 12 su (1,536) | 24,000 | 90 s | 90 s | 1.6× |
| Mk III | 128 / 256 | 12 su (3,072) | 120,000 | 70 s | 70 s | 2.4× |
| Singularity | 128 / 256 | 24 su (6,144) | 2,000,000 | 60 s | 300 s | 3.2×, unstable exit |
| Creative | 1 / 1 | none | 100,000,000 (shown as infinite) | instant | none | no cost, ever |

Every number above is a config default, not a constant.

A range at least as long as the widest world the game allows, corner to corner (about 84,853 km), cannot
refuse anything, so the screens show it as infinite rather than as a number - which is how the creative
drive's reads. A creative drive also pays nothing for a warp or a cruise (`WarpCost.Formula.free`); its
charge is never spent, and a cruise on one flies until the pilot stops it.

Create charges stress per RPM, so a drive's real draw is its figure times the speed it is turned at, and
gearing a shaft up raises the draw without adding any capacity. The defaults are sized to Simulated's
Portable Engine, which is what an airship actually carries: 2,048 su at 32 RPM, or 4,096 su at 64 RPM
superheated. At full rate a Mk I takes a quarter of one engine, a Mk II three quarters, a Mk III one
superheated engine, and a Singularity two, or a steam engine. Every optimal speed is 32 RPM doubled,
because cogs only double or halve - no speed controller needed. The first defaults (64/128 at 16 up to
192/256 at 48) forgot the multiplication and asked for one to six whole engines per drive.

**What it looks like is what it does.** The drive is a brass frame round a stone housing, with a ring
turning through the housing about the shaft's axis and the rift spinning in the middle of the ring. Both
readings come from the machine rather than from a clip playing regardless: the animation controller
multiplies its playback speed by a figure that rises with the charge, so the ring winds up from a crawl
to a blur as the drive fills, and while it charges the renderer shrinks the rift by how much charge is
still to come, so a drive a third full and a drive ready to go can be told apart from across a hangar.
The geometry, its nine clips and all five tier sheets are the artist's, drawn on one layout.

**Dressing the rift.** A **Rift Modulator** bolted against any face of a drive lets its pilot override
that tier palette: a core colour and a rim colour, each chosen from a swatch of sixteen - left click
sets the core, right click sets the rim - one of fifteen themes, and how strongly the whole thing reads, from a quarter to double strength. Applied wherever
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
| Standard | nothing | struck: cracks race out from an impact, kink, and web together with ring cracks as the panes between catch the light | breaks out ring by ring along the hull's heading, each piece tumbling and falling | the plain lit tube |
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
| Ender | a face of the End portal's own starfield; a pair of enderman eyes across the middle that follow you, blink now and then and twitch now and then; violet motes spiralling in, and a faint violet halo | the stare: motes drawn in from outside while the End bleeds through in patches and a pair of eyes opens across the middle, the head twitching as the pane goes | flickers where it was cut, then is simply somewhere else - once - and cools into the void | a tube of the End's starfield, with pairs of eyes opening on the walls, staring, and turning up somewhere else, and violet streaks rushing past |
| Sketch | a cartoon of a hole: a bold pencil outline, the inside scribbled in with the pilot's crayon, a second line in their other colour and a white shine - and a solid pencil that drew it, hanging about twirling, tapping the rim and flipping end over end while it waits | the pencil swoops in and draws the lot inside the opening; the face appears behind the crayon, band by band, as it is coloured in | nothing breaks - the hole is drawn, and at the close the pencil scribbles it out, screws the drawing up and lobs it away | a paper tube with doodles sailing past on the inside of it - an airship, a cloud, a star, birds, a sun, somebody's idle spiral - leaning where they were scribbled and boiling as they go |
| Mostly Dangerous | an ink-black cloud churning round the hole, turning faster at its middle, with lightning in white, violet and electric blue jumping across its edge and flashes going off deep inside it; space bends round it for a moment as it arrives, as a hull bores out of it, and as it goes | the cloud boils out of nothing, heard gathering, and the hole opens in the middle of it with a roll of thunder | nothing breaks - the moment the ship is clear, the cloud spins up and falls in on itself, ending on a flash, and leaves smoke, wreckage and the ship's glowing trails hanging where it came out | a black tube lit from within: violet and blue wisps streaming past on the wall, and stretches of it flaring as the lightning inside goes off |

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
lightning all agree; the Modulator's intensity still applies to them, its swatch does not - so while
one is chosen the Modulator's swatch grid is greyed out and ignores clicks, with a note in place of its
legend (`ThemeLook.takesSwatches`). The colours already picked are kept for the next theme that uses them.
Improbability rolls a fresh palette from each rift's own position, so the two ends of one jump are
never the same colour.

**The last two are different in kind, not only in colour** - `ThemeLook.Surface`. Ender brings its own
colours like the borrowed seven, and its face and bore are not the membrane at all but vanilla's End
portal shader, on a render type of the mod's own (`AWRenderTypes.RIFT_END_SKY`): still opaque, still
writing depth, so it hides a hull exactly as the membrane does, but two-sided, which vanilla's End portal
type is not. It is the only part of a rift that is not plain coloured quads. It is also the only theme
whose occluder arrives early: patches of the End bleed in during the wind-up, because the stare is meant
to be something looking out of the End, which it cannot do from in front of an intact sky. That only
ever adds occluder, so it cannot weaken the hiding.

Sketch is drawn in **ink**, and ink is dark - which additive light cannot draw. So a Sketch rift has no
fire, haze, far light, flash or lightning at all; its face and bore are flat paper, and every pencil
stroke and crayon scribble goes on the translucent glass pass instead, where it genuinely darkens what
is under it. Its paper and pencil are its own; the two crayons are the pilot's two swatches, so it is the
one theme after the first five where the swatch still does something.

It is also the one theme that does not break open. A drawing that simply appears is a picture, so
**something draws it**: a solid, modelled pencil (`PencilModel`) whose point follows a single timeline of
strokes (`SketchPlan`), one stroke at a time, hopping between them.

**The timing is the whole design.** A rift opens over twenty ticks and a hull can be at the plane by the
end of them - `transit` jumps the rift straight to open when one is - so everything that has to exist
before a ship arrives is drawn inside that one second: the outline, the colour, the inner line and the
shine. Nothing waits on an artist. The face is not grown as a circle either; it appears *behind the
crayon* as the hole is coloured in (`SketchPlan.front`), so being interrupted at any point still leaves a
complete hole rather than a half-drawn one.

Everything after that is **acting**, and all of it is interruptible: the pencil bobs, twirls, taps the
rim and flips end over end, gets out of the way when a hull comes through and peers after it, and comes
back to scribble the whole drawing out, crumple it into a ball (`PaperBall`) and lob it away. That is why
`SketchPencil` is one pure function of the clock and a handful of moments the rift knows about, rather
than a state machine: a ship can arrive halfway through a twirl and the close can land halfway through
that, and the pose an instant before any interruption is simply the same function evaluated a tick
earlier. `SketchPencilTest` samples it a hundred times a tick across every way a warp can treat a rift,
and proves the pencil never jumps, never lies in a hull's way once it has ducked, and is gone by the end.

**Mostly Dangerous is a fourth kind of face** (`ThemeLook.Surface.NEBULA`): a capital ship arriving out of
hyperspace. Its face and bore are still the opaque membrane, black to the edge with no fire round them,
and the cloud reaching far past the rim is soft puffs of dark (`Nebula`) on the translucent pass - dark,
like ink, so they cannot go on the additive one - drawn as a batch of their own ahead of the lightning
that plays over them. The puffs turn in rings, the inner ones faster, and every puff in a ring together, so
the cloud churns like a vortex without ever tearing itself a gap between the black hole and the black
cloud; `NebulaTest` measures that against the falloff the puffs are actually drawn with. Nothing breaks.
When the ship is clear, the cloud falls in on itself in the first third of its close - pulled in faster
than it shrinks, with the hole shrinking on the same curve so the cloud stays over it all the way down -
and the rest of the close is what it leaves behind: smoke and wreckage, and the trails hanging where the
ship came out.

It is also the only thing in the mod that **bends the view**, and so the only custom shader it has.
`GravityLens` copies the frame and redraws a disc round the rift out of the copy, each point showing what is
a little nearer the middle (`LensProfile`: seamless at its edge, never folding, only ever pulling inwards,
strongest just past the cloud), in brief pulses - as the cloud arrives, as a hull bores out of it, and as
it collapses. The copy is taken *after* every face and throat is drawn, which is not the obvious order: a
copy taken before them still holds the hull they are hiding, and the lens would draw it bent round the
outside of the rift. Taken after, it can only move about what was already on screen - `RiftPassOrderTest`
reads the order off the source. That needs one copy to hold both the world and the faces, which Fabulous
graphics (it draws them into separate layers) and shader packs (they draw the world somewhere else
entirely) do not give it - so under either, or with `riftLensing` off in the client config, the rift draws
a ring of bent light instead.

And it is the only theme that **lights the world**. The flashes inside the cloud as a hull comes out of it
are vanilla's own lightning flash - for anyone near enough to see what caused them - so the ship is lit up
by the storm it is coming out of, and the flashes honour the Hide Lightning Flashes accessibility setting
without any help from here.

Most of those seven also draw somewhere the originals never do: **inside the bore**. The crew fly
through the throat for the length of a warp, and that tube is where hyperspace, a warp bubble and the
time vortex actually happen - so each borrowed theme dresses its inside, just within the solid wall,
where nobody outside can see it. There is still nothing painted over the screen; `WarpCorridorOverlay`
explains why there never will be.

The seven themes after Starlight are **open nods to jumps from elsewhere**, named just off-target on
purpose, and Mostly Dangerous is an eighth; Ender is a nod to somewhere every player has already been. They borrow nothing but a look: each is still a palette, a fracture shape, a mote, a
sound, some furniture and a dressed bore, and the warp underneath is the same warp the mod has always run. Nothing about a Modulator
has ever been able to change how a jump behaves, and that has not changed to accommodate them.

None of this can weaken the thing an aperture is for. It is all additive glow that writes no depth, so
it can only ever add light to what is already on screen - it cannot punch a hole in the opaque membrane
and cannot reveal a hull inside the aperture, whether it is drawn outside the rim or straight across the
face. That is why Ember's fangs are allowed to reach *inside* the rim, which is most of what separates a
mouth from a doorway. The one thing that reads the screen rather than drawing on it, the lens, copies it
only once the faces and throats are down, so it can only move about what they already let be seen.

Right-clicking a drive opens its **console**, which is a diagnostic panel and nothing else: speed
against requirement, stress, charge, hull mass, the bow setting, and a checklist of everything a jump
needs with each item either met or not. There is no destination list and no launch button, because a
drive is a machine rather than a control — you set a course at a chart table and fire it with a signal.
What the console is for is answering "why has this not gone anywhere", which it does by naming the
condition that is missing. Its buttons only change how the drive behaves - the bow, waypoint or free
cruise, and whether it checks for terrain before a warp (see [Safe arrival](#safe-arrival)) - or bring a
sequence to a stop.

Every word on the console is measured against the room it is drawn in: `ConsoleTextTest` reads the
default font's real glyph widths and fails on any label that would be cut short with an ellipsis or
scrolled by a vanilla button. The window is 320 by 232, inside what Auto GUI scale guarantees, and
shrinks to fit rather than hanging off the screen when a GUI scale is set larger than Auto would pick.

The **Creative Rift Drive** has nothing made it and nothing stops it: no recipe, no stress, no
cooldown, no charge time, no minimum speed, and no range cap. Almost all of that is just its own tier
numbers rather than a fork through the drive — one RPM to run, one tick to charge, no cooldown - so it
behaves like a drive in every way that matters for testing. The two things numbers cannot express are
handled explicitly: it never stalls for want of rotation mid-warp, and the server-wide range limit does
not apply to it, because that setting is a statement about what players may do.

#### Drive Extenders

A drive can be built out into a cube of **Drive Extenders**, from two blocks along an edge up to seven,
and is then drawn the size of the cube: one model, scaled evenly and centred on the frame. Until the
cube is complete the extenders are inert casing that serves nobody — the Astrolabe's rule, that a
machine which half-works while half-built is one players cannot reason about. Complete it and every
extender *is* the drive to anything outside: a shaft against any face of the cube turns it, a Modulator
or Stabiliser against any face finds the drive exactly as one beside the drive does, right-clicking any
block opens the drive's console, and goggles on any block read the drive. A bigger drive flies no
further; what the frame buys is size and surface.

**The drive may sit anywhere in the cube**, buried in the middle included, and a drive on its own takes
a shaft on any face too. Power reaches a buried drive because a formed extender is a Create kinetic
relay: a shaft on every face, no stress of its own. The drive's load is therefore counted on whatever
turns the frame, as though the shaft ran straight to the drive — reading a nearby shaft's speed and
charging from it instead would have been free power as far as Create's stress was concerned. A loose
extender has no shaft anywhere, and a formed one refuses a shaft against a block of a *different*
drive's frame, so two frames built side by side stay two machines rather than silently sharing one
network. Forming and unforming go through `KineticBlockEntity.switchToBlockState`, and the block counts
`FORMED` as a kinetic change, because by default Create only re-links a block whose axis changed and a
frame would otherwise form connected to nothing.

The first frames did not work like this. The drive had to sit in the middle of a face its shaft ran
through, because it only took power along its own axis — and in play that meant a finished three-cube
came out as a two-by-two in one corner of it, since a drive placed anyhow fits *some* two-cube and
hardly ever the single three-cube the rule allowed. Once power could arrive from anywhere the rule had
nothing left to protect.

The largest complete cube wins; among complete cubes of the same size, the one most nearly centred on
the drive, so a drive set in a block of casing bigger than any frame grows evenly about itself; then the
lowest corner. `DriveStructure.findBest` reads the thirteen-block box once into a running total and tests
each of the seven hundred and eighty-three candidates from eight of its corners, rather than walking
every candidate's cells — a hundred thousand reads for a nearly finished seven-cube, which is precisely
what is checked as a player lays its last blocks. `DriveStructureTest` holds that against the long walk
over a few hundred random scatters, and was checked by breaking a term of the sum.

Everything else follows the Astrolabe. Building beside a finished drive cannot take its frame apart.
Every pointer is a **step** — an extender's to its drive, a drive's to its frame's corner — so a frame
survives the hull assembling into the plot grid. Formed extenders are invisible and declared
non-occluding, since the model drawn over them is a cage; were they occluding, every block face pressed
against the frame would be culled and the cage would show holes. Forming happens on placement and
breaking; the drive also looks over its own frame every two seconds, because assembling a
hull puts blocks down without anybody placing them, and whether Sable fires the ordinary hooks in a
useful order is not something the frame should have to trust. Reach to a drive's console is measured to
the nearest part of its frame rather than to the drive block, so the far side of a seven-cube is within
reach whenever the cube is.

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

#### Why a table moves as one piece

To anything that moves blocks, a table is a single object. Its cells declare themselves attached to one
another through Create's `BlockMovementChecks`, the same way Create keeps a door or a bed together. A
Physics Assembler that reaches any cell therefore takes the whole table. Before that, only the cells
inside the glue went aboard, because a table's thirteen-pixel sides attach to nothing.

Each cell also checks its table once whenever it loads. Sable writes assembled blocks straight into
the plot without calling `onPlace`, so the placement rules never see them arrive. A cell that turns up
without the rest of its table breaks that table up and re-forms whatever is there. The check is not
optional: a formed cell whose origin is missing draws nothing at all, and that is what a half-carried
table looked like on the deck.

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

#### The passage

A crossing used to be one tick. It is a **passage** now: about three seconds between walking into the
pane and coming out of the far gate, with the journey drawn over the traveller's view.

The traveller is put at the far gate **at once**, and that is the whole trick of it. Their client starts
loading the far side the moment they go in, behind a tunnel they are being shown instead, and by the time
the tunnel lets them out the ground is there. Held at the near gate until the end, they would arrive to
a view of nothing, loading.

But that means there is somebody standing at the far gate for three seconds who cannot see it. So for
as long as the passage lasts they **cannot be hurt, cannot be taken for a target, cannot be knocked
about, are not drawn for anybody else, and cannot move** - their own keys do nothing until the far side
begins to show, because a client that let them walk would be walking them blind. Then they arrive, with
the momentum they went in with, and a moment's further grace while the light clears.

None of that is done by *setting* anything on the player. The obvious ways to make somebody safe -
invulnerable, weightless, a spectator - are every one of them saved with the player, and a server that
stopped in the wrong three seconds would start again with one who could not be hurt and nothing left that
knew to put it right. `GatePassages` keeps a ledger instead, and the game asks it: whether this entity
may be hurt, whether that mob may turn on it, whether a blow may move it. A passage the server forgets
is simply over. Gravity is left alone for a duller reason - a player held in the air is thrown off the
server for flying after four seconds - so somebody who comes out with nothing under them falls while the
tunnel plays, unhurt, and lands as it clears.

**Players, on foot or riding, and the crew of a vehicle.** A vehicle with anybody aboard is held still at
the far gate until they arrive - commanded to a stop every tick, the way a warp commands a hull's speed -
and driven off then at the speed it went in. A vehicle with nobody aboard, a mob and a dropped item all
cross instantly, as before: nobody is in them to be shown anything.

**What it looks like is each player's own.** The **Passage** button on any gate's dial panel steps through
the styles - shift-click to go back - and its tooltip says what the one chosen looks like. It plays
nothing: the place to see a passage is a gate. It writes `gatePassage` in that player's client config and
tells the server nothing, so it is never greyed out on somebody else's gate, and nothing about it is stored
on a gate. The styles are borrowed, as a Rift Modulator's themes are, under names of their own:

| Style | What it is |
| --- | --- |
| **Kawoosh** | The pane's light bursts over the view in rings and clears into a twisting conduit of blue and violet plasma, light streaking down its walls, to a flare at the far end. The default. |
| **Timey Wimey** | A vortex of storm cloud wound into a spiral, ember red sliding to cold blue and back, turning hard with lightning cracking down its length. |
| **Punch It** | The stars hold for a breath, pull out into lines, and then there is only the blue-white lane rushing past. |
| **Energise** | No journey: a column of light, a curtain of threads and a shimmer of gold and blue motes rising through it, thickening to white - and thinning, a grain at a time, into the far side. |
| **Full of Stars** | Two planes of every colour streaming out of a burning slit and past, turning as they come. |
| **Down the Pipe** | An iris shuts, a green pipe goes by in three flat greens and big square pixels, and an iris opens on wherever it came out. |
| **Quiet** | No tunnel and no motion: the view dims, holds, and brightens. For anybody who would rather not be flown down a tunnel on every trip to the mine, and what is drawn if a style's shader does not load. |

Each but the last is one quad on a shader of its own, drawn in the GUI's pass - after the world, and after
whatever a shader pack made of it - because a picture that replaces the whole view is the one thing light
hung in the world cannot be. None has a texture: each is arithmetic on where a pixel is. A style is handed
the same few numbers as every other and may do what it likes with them, with one rule: **while the veil is
whole, nothing of the world shows.** It decides what the veil is made of and where it thins first - a
dissolve, an iris - never whether there is one.

The timing is one pure function of three numbers - how long ago the passage began, how long the server
said it would last, and when it said it was over - so that a server running a tick late is waited for in
white rather than flickered at, and `GatePassageTest` can walk every case a hundred times a tick.
`gatePassageTicks` in the server config sets the length, and nought puts gates back to instant.

The shaders are the part that cannot be reasoned about, so they are not: `PassageShaderTest` makes a hidden
window with the kind of OpenGL context the game asks for, compiles every shader the mod ships on the real
driver, draws every style across a whole passage at four shapes of screen and reads the pixels back - solid
everywhere while the veil is whole, gone everywhere once it is not, one flat field as the far side begins to
show. Set `PASSAGE_SHEETS` to a folder and it also leaves a contact sheet of each style there, which is how
they were looked at before anybody walked through a gate.

#### The rest of the rules

- **Same dimension only.** Sable cannot move a sub-level between levels; the only way a warp takes one
  into another dimension is to rebuild the whole vessel at once, and a vehicle half through a doorway is
  not a thing that can be rebuilt. A door that takes half of what goes through it is worse than no door,
  so the dial list is filtered rather than the refusal being a surprise.
- **Gates are named, owned and access-controlled**, on the same rules as Warp Anchors and reusing the
  same `WarpAnchorAccess` - so who may travel where is written once and behaves the same at a chart
  table and at a doorway. A gate goes on a [network](#networks) the way an anchor does, and its dial list
  is filtered the way a chart is.
- **A gate's owner is whoever set its controller down.** Gates had no owner before networks: the record
  was made as the ring formed, a moment before anybody is told who placed the block, so anyone could
  rename any gate and a Private one could be dialled only by operators. The controller remembers its
  placer now and claims the record. A gate built before that stays unowned, and anybody's.
- **A gate mended is the gate it was.** Its record goes when its ring comes apart and is made afresh when
  the ring is closed again, and it used to come back unnamed and public - so knocking one block out of a
  private gate's ring and putting it back opened that gate to everybody. The controller keeps the name,
  the access and the network across the break.
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

#### Sounding another dimension

The box in the corner of the probe's panel names the dimension being scanned, and opens into a list of
the others. Pick one and the next sounding is thrown there: the ticket is placed in that dimension, the
survey is read from it, and the fix that comes back carries its dimension with it. A `WarpCourse` to a
fix now says which dimension the fix is in, because the same three numbers are a different place in
every one of them, and a course that forgot would still fly - to ground nobody looked at.

Three decisions sit behind the list.

*Where the sounding starts.* "Three thousand blocks north-east" needs somewhere to be north-east of, and
the ship's position is a position in this dimension. The game already says what a place here is called
over there - the coordinate scale a Nether portal uses - so a sounding into the Nether starts from the
spot a portal built under the ship would come out at, and the range is then blocks of the ground being
scanned. `ProbeAim` is that arithmetic, kept apart from the level so it can be tested.

*What the list holds.* The probe's own dimension, always, and first. The rest are the dimensions a ship
could then be sent into: cross-dimension warps have to be on, with Dimensional Sable installed, and
Sable has to keep the two dimensions on the same size of grid - the part of a crossing's preflight that
is about the dimensions and not the ship, so it can be asked with no ship at all. A scan of somewhere
no drive could follow is essence spent on a picture and a server asked to generate ground for nothing.
On a server that does not cross dimensions the box is a label: it names where the probe is, and does not
open.

*Whose list it is.* The server's: `probe.probeDimensions`, the vanilla three by default. Every other way
into a dimension in this mod needs somebody to have walked there first, carrying an anchor. A probe
does not, so "every dimension the server has" - which on a modded server includes the pocket dimensions
mods keep for their own machinery - is something an owner has to ask for, with `"*"`.

A reading belongs to the dimension it was taken in as it does to its bearing, so moving the box
discards it. A probe carried across by its own ship drops its reading too: left aimed at "here", it is
now aimed somewhere else, over a map of the dimension it has just left.

The survey itself is unchanged, which has one consequence worth knowing. It reads the top of each
column, and under a ceiling the top of every column is the ceiling: a scan of the Nether is a map of
the bedrock roof, and a ship sent there arrives above it. That is a safe place to put a ship and the
honest answer to the question the survey asks. What "the ground" should mean inside a cave the size of
a dimension is a different question, and not one this answers.

### Rift Plotter

Players wanted a weapon, and this is one - and a freight terminal, because it is the same machine: it tears a
rift beside itself and opens the far end anywhere in the same world, round a target they choose. Whatever crosses the near rift while it stands - an
airship loaded with whatever an airship can be loaded with, a volley of arrows, a crate of lit TNT, a
cannon shell, a raiding party on foot - comes out of the far one, at the speed it went in, pointed where the
far rift points.

It is the Collapse Cradle's sibling, and deliberately built the same way: three blocks by three by three,
placed whole from one item, refusing pistons, contraptions and decks (`PlotterStructure` borrows the
cradle's footprint arithmetic outright rather than keeping a copy that could drift). The same carved green
stone over the same purple-and-brass plinth - the model is an artist's, like the cradle's. It is a **field
generator**, not a gun - it was first built with a lens-barrel, and read as a cannon, which is not what a machine
for shipping and war alike should say. Between four crystal-topped pillars a turntable and yoke carry the Relic
Armillary stood on its edge - rings nested one in the next round a crystal ball - and as the field builds the rings
spin up about the beam and the ball at their focus tumbles. The yoke really aims. The turntable turns it to the
bearing the entry lies on and the trunnions tip it to the entry's elevation, at a machine's pace, so moving the
entry is watched being followed; the beam that tears the entry leaves from the ball. The rings point at where the
entry *is*, which is not always the way the entry faces - see below. (GeckoLib turns a bone the other way round
about the upright from Minecraft's yaw, so the turntable is given the yaw negated - `PlotterWorks.turntableYaw`.
Given it as it stood, the yoke faced the entry's mirror image, which looked right only when the entry was due
north or south of the machine.)

Both relics stand on a floor that works: the eight blocks round the master in the bottom layer have block entities
of their own (`RelicBaseBlock`), so a Create pipe or funnel joins any of them. It has to be done that way because
Create's pipes and funnels ask for a block entity before they ask for a capability - the machine's inert body, which
has none, takes no pipe at all. A shaft turns the machine through the block in the middle of any side of that floor,
or from under its middle, and no rotation passes through it: each of those four blocks has the one shaft, facing out,
the master has one on its underside, nothing in the machine is linked to anything else in it, and the machine goes by
whichever of the five is turning fastest (`RelicFloor`), which is also the one its stress is counted on.

#### The far end is described from the target

A gunner does not think "open a hole at (2130, 94, -5871)". They think "come in from the north-east, sixty
out, forty up, diving at it". So the plot is written that way round (`Plot`): the **target** is typed in as
coordinates, and the exit is placed *round* it - a distance across the ground, a compass bearing, a height
over the target's ground - and faces the target unless the player aims it off by a turn and a tip. Moving the
exit round the target keeps it aimed. The target's height is never the player's to type; the server reads the
ground there when the far end has loaded, because a typed Y is how you plot an exit inside a hill.

Typed coordinates, with **no range limit**, was the user's call. Distance is paid for instead
(`PlotCost`): a floor, a price per kilometre from the lens to the exit, and a price per block of radius. A
shot at the other side of the world is not refused - it is expensive. That is why the plotter **charges**
rather than checking its tank: the tank is a buffer, the charge is what has been drawn out of it so far, and
the draw rate climbs with the shaft's speed. A plot larger than the tank holds is a plot that needs a supply
line, not one the machine turns down. The charge is kept when a strike is stood down or refused at the far
end - drawn, not spent - and only opening the rifts spends it.

#### A strike, not a doorway

The entry is **temporary**: torn by the plot, standing for `plotterOpenSeconds`, then healing. A standing
teleporter to anywhere is a different mod, and a Rift Gate already covers going somewhere and coming back.
Stress follows the same rule as a gate's - only while a strike is under way - so a plotter nobody is firing
costs nothing to keep. Let the shaft slow below the minimum with the rifts open and they collapse.

The sequence, as `RiftPlotterBlockEntity` runs it:

1. **Preflight** (`PlotRules.preflight`), before a single millibucket is drawn: allowed, free, aimed, turning,
   inside the world. There is no closest an exit may be: a plotter
   sending freight across a base is as much a plotter as one shelling the far side of the world.
2. **Charging.** Essence is drawn into the charge. The far end's ground is held through `Holds` the whole time -
   it is almost always somewhere nobody is, and often somewhere never generated.
3. **Reaching.** Paid for, and waiting for the far end to be entity-ticking. Then its ground is read, which
   moves the exit's real height - so the price is asked again (charging on if the real plot costs more), and
   `PlotRules.farEnd` asks whether either disc is more than half buried. Half, not any: a strike aimed out of a
   hillside's shoulder is a choice; a rift that is mostly rock has nowhere for anything to be.
4. **Open.** The entry tears. The exit does not - not yet: its ground is held and its place known, but nothing
   at the target shows until something is on its way through. Every tick the entry stands, everything near it is
   asked how long it is from crossing (`PlotGeometry.ticksToCross`, on its motion since the last sighting), and
   the exit tears open once anything is due inside two seconds, or the dock is pushing a ship through. It heals
   three seconds after the last of the traffic, while the entry stands on for the rest of its window, and tears
   again for the next. So a lane waiting for freight shows nothing at the far end, and a strike's first warning is
   its own arrival - with just long enough for anyone looking up to see the tear before the shot comes out of it.
   The exit is kept up on the clients near it in steps (`RIFT_HOLD` cues), since how long it will stand is not
   known when it opens. Whatever crosses the entry is carried.
5. **Closing**, and back to rest.

A strike does not survive the machine being unloaded. Its far end is held on timers, its sightings are in
memory and its rifts are cues; a plotter that loads mid-strike stands down and keeps what it drew.

#### Shipping or war: how it fires

The same machine is a freight terminal and a battery, and which it is is a handful of settings (`Firing`) mixed
as the player likes - there is no mode switch, because the two are not exclusive:

- **Dock helper.** A vehicle that slows down in the dock - a cylinder behind the entry, on the side it is flown
  into - is caught, and gently **lined up** (`DockAlign`): its middle glided onto the rift's axis a couple of
  blocks short of the plane, and the hull turned level and end-on. A hull has no bow of its own, so "end-on" is
  whichever of its four horizontal ends needs the least turning, unless only the other way round fits the rift.
  The pull is proportional to what is left to close and capped, and the turn a few degrees a tick, so a caught
  ship drifts in and settles rather than being yanked; `DockAlignTest` flies a simulated hull in to prove it.
  Once it is lined up and the rift is open, it is pushed straight through. It is steered with velocity, never a
  teleport (`Airship.steerVelocity`, which swings any docked parts round with it as a rigid body). A pilot is
  never trapped: crouch aboard and the dock lets go, and leaves that ship alone long enough to fly clear. Anything
  faster than `plotterDockCaptureSpeed` is left alone, so a bombing run straight through is not slowed down.
  While the helper is on, the dock stands drawn in the air for anyone near (`PlotterGuides`): the cylinder a ship
  is caught in, with bands of light running along its walls to the rift, the mark it is held at, arrowheads
  crawling along the axis, and the rim where the rift will tear. Amber while it waits, brighter while it holds a
  ship, teal once the ship is lined up. It is drawn to be seen at noon: every line is a quad a few pixels wide at
  any distance, alpha-blended over a dark edging (`LightStrokes`), with a faint copy through terrain so it can be
  found behind a hill. The first one was GL lines in additive light, which at midday were white on white and
  nearly invisible. `plotterDockGuide` in the client config turns it off.
  **The dock is the size the plotter is set to** (`Firing.dockRadius`, `dockDepth`): how far out from the rift's
  axis it catches, and how far back from the entry. Both start at *Auto*, which is the dock every plotter had before
  it could be sized - half as wide again as its rift and six blocks more, so it widens with the rift, and as deep as
  the server's `plotterDockReach`. Set, a dock stops following the rift: two plotters side by side want narrow docks
  that do not take each other's ships, a harbour mouth wants one that can be flown into without looking. The server
  caps what may be set (`plotterMaxDockRadius`, `plotterMaxDockDepth`) and tells the table, and tells every client
  its own Auto depth, so the cylinder drawn is the dock that catches - `PlotterDockWiringTest` reads both off the
  source, since a machine that took the sliders and went on catching in its old dock would look exactly right.
  A hull longer than its dock is deep is carried by the dock to a mark *behind the dock's back wall*, half its own
  length from the rift; it is held all the way there (`DockAlign.keepDepth`), where it used to be let go on the way,
  caught again and let go again.
- **Fire when a ship docks.** A lined-up ship sets the plotter off by itself after a short wait, and the tear heals
  behind it once it is through - a send per ship, paid per ship.
- **Fire on redstone.** A rising edge fires, as the table's Engage does. A lane opened that way stands while the
  signal is high and closes when it falls, so Create's logic can hold a lane open.
- **Hold open as a lane.** Once open, the rifts stay open, paying `plotterLaneUpkeepPercent` of the plot's price
  every second - a lane across the world costs more to hold than one down the road, as it cost more to open -
  until stood down, starved or switched off.

**Exit speed** is the other half of shipping: things can leave the far rift at the speed they went in (turned by
the rift), at a set speed straight out of its front, or at rest - a delivery that arrives and stops.

#### Crossing, and carrying

The entry is a way through **from either face**. Crossing it backwards uses the entry turned half round its own
up, so whatever goes in comes out of the exit's front: a pilot never has to know which face is which, and a
crate dropped into a floor rift from above goes where one lobbed up through it would.

A crossing is a **line between two sightings the plotter took itself** (`PlotGeometry.crossing`), meeting the
disc inside its rim. The Rift Gate's rule - never infer a crossing from an entity's own previous position - is
kept, and extended: a gate only asks which side of the plane something is on, and a cannon shell covers more
ground in a tick than a gate's catchment is deep. The line catches it; `plotterCatchment` is how far either side
the plotter looks.

What crosses is carried by one rotation - the one that takes the entry's frame onto the exit's
(`PlotGeometry.turn`). Both frames are built the same way round from a yaw and a pitch (up is the look turned a
quarter up in its own vertical plane, so a disc facing straight up still has an up), which is what makes that a
proper rotation rather than a mirror: a hull comes out the right way up, a player keeps their bearing relative
to the rift, and speed is kept. The place on the disc is kept too, and so is the ground covered past the plane,
so a shell does not lose a tick of flight.

Vehicles are carried as a gate carries them, by `Airship.relocate` - the only way a hull may jump - with every
part of the vessel, a `HullJump` to every client, and the crew aboard. A hull has to **fit**: its shadow on the
rift's face, measured off its own block box turned by its orientation (never its world box, for the reason
`FlightGeometry` gives), must fit inside the circle. One that does not, or that is tied to the ground, is turned
back and stopped rather than half swallowed. A carried hull is then held loaded for `plotterArrivalHoldSeconds`
under its own purpose (`HoldPurpose.PLOTTED`, a Sable ship ticket on a lease): an uncrewed hull put down on ground
nobody is near would otherwise be filed away by Sable the moment it arrived, and never reach its target.

Loose things - players, creatures, projectiles, TNT - go through as well unless `plotterCarriesEntities` is off,
their facing turned with them. Anything aboard a hull goes with the hull.

#### The table

Right-clicking opens the plotting table (`RiftPlotterScreen`): a model of the ground round the target, in three
dimensions, and tabs of controls beside it - the target and the exit are set on the model.

**The entry is set in the world.** Three sliders naming a yaw, a pitch and a distance were exactly as hard to
read as that sounds, so the Entry tab folds the table into a sidebar down the right of the screen and takes the
camera out of the player's head (`PlotterCameraRig`). The game draws from whichever entity it is told is the
camera; this gives it a client-only marker, never added to the world, moved round an orbit about the entry and
turned to look at it - dragged to circle, scrolled to come closer, and gliding after the entry as it is moved.
Because the camera is not the player, the player is drawn standing at their machine. The view is forced to first
person and the HUD hidden while it flies, and both are put back exactly as they were. A hologram of the entry
stands where it will tear (`PlotterPreview`, drawn by `PlotterEffects`): its rim, the way through it, the beam out
to it from the ball, and the dock behind it with the mark a ship is held at - drawn from the unsent sliders, so it
moves as they do, and drawn a second time faintly through the ground.

**Where the entry is and which way it faces are two things.** They were one: the entry was thrown out along a
line from the lens and sat square to that line, like a plate held out at arm's length, so the only way to face a
rift along a runway was to build the machine at the end of the runway. Now the plot (`Plot`) keeps the bearing,
elevation and reach that say where the rift is torn, and beside them a switch, `inputTurned`, with a yaw and a
pitch of its own. Off - which is what a new plotter has, and what every plot saved before this loads as - the rift
faces straight out as it always did, wherever it is thrown. On, it stays where it was thrown and faces the way it
was told, in world terms: moving it round the machine afterwards does not turn it. The dock is behind the way it
faces, so it swings round with it. Turned or not, the entry is built from a yaw and a pitch by the same
`PlotGeometry.frame` as the exit - never by rolling the outward disc about one of its own axes, which would leave
its up lying over and bring every hull out of the far end on its side. `PlotGeometryTest` flies a level hull
through an entry turned every way round and has it come out level.

The sidebar has four tabs, six rows each, because that is all the smallest screen holds: **Rift** (bearing,
elevation, how far it is thrown, radius), **Face** (face away from the machine, or the facing and tip it is turned
to), **Dock** (the helper, how far out and how far back it catches, fire when a ship docks) and **Fire** (redstone,
lane, how long a strike stands).

The model is a **survey** (`PlotterSurvey`), the Astrolabe's idea in a new shape. The chart's is flat, a byte of
relief against its anchor; this one travels a whole height per column, because it is a solid the player turns in
their hands and aims into. It reaches `plotterSurveyChunks` out from the target (five by default: eleven chunks
across), and only those chunks are loaded to build it, on an expiring ticket through `Holds`
(`PlotterSurveys`, the plotter's `PreviewHolds`). On the client it is built once into a solid of columns
(`HologramMesh`, pure and counted in `HologramTest`) - a top for every known block, a wall only where a column
stands over its neighbour, a skirt round the edge - uploaded as one vertex buffer and drawn in its own viewport
with its own depth. The pointer picks a block by walking a ray through the columns cell by cell
(`HologramPick`): stepping a fixed distance at a time picked the block behind a ridge seen edge on.

It is meant to be cinematic. The model rises out of the table column by column under a scan line when it
arrives. **Engage** sweeps the view down behind the exit, looking along the line of fire at the target. In the
world the strike is a set piece in beats, timed by `PlotterChoreography` and drawn by `PlotterSpectacle`:

- **Charging.** The dish swings to its aim and the ball spins up. A beam gathers from the ball to where the entry
  will tear, thickening with the charge and wound about with turning strands of the field; a star of light swells
  at its end, rings close in on it faster as the charge fills, arcs jump from the ball to the star, and a drone
  under it all climbs in pitch.
- **Reaching.** Paid for, waiting on the far end: the beam at full, the star straining, cracks running out across
  the disc the tear will fill, and an inhale.
- **The tear.** The star bursts out past the rim, a flash, a boom, a shockwave rolling out across the rift's plane
  and a spray of sparks, and the aperture opens in the rifts' own crimson or whatever a Rift Modulator beside the
  machine dresses them in.
- **Open.** Tethers of light from the ball hold the rim, turning with it, until the rift heals.

Each beat's curve meets the next where they join (`PlotterChoreographyTest`), and each piece is drawn twice -
alpha-blended in the rift's colour, so the strike has a body at noon, and as additive fire, which is how it looks
at night. A docked ship is held on a thread of sparks from the ball, which brightens as it lines up. (A map of the
target used to rise over the machine as well; it was taken out, because a block-high slab of someone else's
ground hanging over the plotter hid the machine and said nothing the table had not.) The camera flies only round the machine, never to the far end: the client does not have the far end's chunks,
so there is nothing there to fly it to.

`plotterEnabled` turns plotters off for a server that does not want weapons; they can still be built and aimed,
and say why they will not open.

### Rift Ward

The plotter made the mod something players could aim at each other, and its only answer was that the
defenders see the far rift open. The ward is the answer that was missing: the third relic, and the one that
refuses. It holds a field over the ground round it, and a rift a stranger aims into that field comes out at
the field's rim instead.

**It deflects; it does not refuse.** That was the owner's choice, and it shapes everything else. A refusal
would have been the cleaner fit to "a refusal happens before anything moves" - but a pilot told "destination
warded" at their own console has been told where the base is and that it is defended, for nothing. A
deflection says nothing until the ship is in the corridor. So the chart, the validator and the console know
nothing of wards (`WardWiringTest` holds them to that), and the one refusal left, `DESTINATION_WARDED`, is
only for a field with no room anywhere round its rim.

The deflection is nevertheless decided before anything moves. `Wards.turnAside` is asked where
`beginWarp` already moved a target for a Singularity's scatter - after the scatter, and again on the
fallback when a scattered spot proves unsafe - and the flight is *planned* to the rim. Nothing changes
mid-corridor on the server; the surprise is staged on the client. Asking changes nothing: a ward is paid
(`Wards.commit`) only once the flight is certain, after the last thing in the pre-flight that can refuse.

**The field is a circle on the map and the whole height of the world** (`WardField`, pure). Every arrival
here is placed sideways first and given a height after, as a scatter is; a dome would only be something to
warp over. A turned rift comes out where the line it was flying first meets the field, pushed out by the
hull's own reach - and a formation's, so every slot of a fold is outside - and is then kept that far from
*every* field, not only the one it was aimed into. Where fields overlap so thickly that turning from each in
turn only goes round in circles, it is walked straight out of the lot. (The first version turned from each
field in turn and stopped; a test of three overlapping fields found the ones it left inside.)

**Three radii, and they are not one number.** What the owner set, which upkeep is paid on; that thinned by a
Rift Storm, which a plotter and a probe meet; and that less a drive tier's push into it, which a drive
meets. So a Singularity aimed at the outer half of a field lands where it aimed.

**Whom it turns is three settings and nothing behind them** (`WardMode`, `WardAccess`): everyone; everyone
but the members of the networks it names; or only the members of the networks it names. Its owner comes home
in every mode, unless they tick *Turn me too* - which exists so that one player can try their own ward. The
first version had more to it: operators passed, and so did anyone bound for an anchor inside the field that
they could use. That made a ward impossible to test alone and hard to predict, and the owner threw both out
in favour of a rule a player can read off the panel. A ward may name any network on the server, not only its
owner's, because shutting out a rival's is half of what the third mode is for; so a network comes off a ward
only when it is disbanded. A rift nobody can be named for is in no network: turned by the first two modes,
passed by the third. That is why plotters gained an owner.

**A ward works while nobody is there**, which is when it is needed, and never loads a chunk to do it. The
registry holds each ward's charge and when it was counted; upkeep since then is taken off whenever something
asks (`WardCharge.after`, which a test holds equal to counting every tick for an hour). An unloaded ward
draws nothing, so a base is protected for as long as its banked charge lasts. Coming back, the block takes
its charge from the record - the one a rift was charged against in the meantime.

**The set piece** is `WardChoreography`, one pure function of a scene's age and length, laid over the ship's
own time in the corridor so the last beat meets the hull coming out: the tear reaching open over the place
aimed at, caught half open as the weight is hauled over and the ward tolls, dragged across the sky to the
rim, and bursting there. The far aperture's usual early open is held back for a turned ship - the transit
cue, which can open an aperture by itself, opens it as the hull crosses - so nothing stands at the rim before
the tear is dragged to it. Nothing in the scene pushes, pulls or hurts; the Collapse has forces, and this did
not need them.

**It is a stone totem in a machine with no roof, and it should look like something that ought to have been
left alone.** It was first a bronze weight on a chain under a crown of lintels, and in game read as a tidy
roofed machine - nothing about it said that holding a place shut means leaning on what the place is made of.
Now four pillars stand open to the sky at different heights, one broken off, and between them a carved totem
of rift-stone cracked across into four pieces, with a thread of void through it. The player's brass on this
relic is restraint: a clamp ring round the totem's waist, braces and chains out to the pillars, plates bolted
over its foot. `WardWorks` poses it. Off, the pieces sit on one another, dark. On, they lift apart; while the
field stands each turns against its neighbours with the field's light in the gaps; out of charge they hang
open and still, guttering. Catching a rift, the upper pieces are dragged towards the tear against the clamp
and ring back. GeckoLib mirrors x as it bakes a model, so `shearX` is negated, and the model script checks
that against GeckoLib's own arithmetic. The model was looked at before it shipped this time, in an offline
render of every texel through that same arithmetic.

**A ward says it is on four ways** - lit carvings, turning pieces, a low drone, and light on the ground (the
master block's `LIT` state) - because the first version said it none, and a field is invisible. Round a
standing ward things go the wrong way: motes rise off the ground at the rim and out of the open top, dust
creeps inward to the plinth, and a dark stain wavers over the totem. Still no line, ring or outline: a force,
not a display, as the cradle's pull was sent back to be.

**Its panel is set by looking.** A field is a circle up to a couple of hundred blocks across and nobody at
the machine can see where it ends, so the panel is a strip down the side of the screen (`WardLayout`) with
the world undimmed beside it, and the camera leaves the player's head for as long as it is open - the
plotter's `PlotterCameraRig`, borrowed whole. The rim as the slider has it is drawn on the ground, and the
camera draws back to keep it in view.

Not seen in a running game. What only that can judge: how the tear reads against real sky and cloud, whether
the dimming and the toll carry, the wrench aboard, whether the motes and the stain are quiet enough, how the
camera and the rim read from the panel, and the totem in motion - the first model was seen in game and sent
back; this one has only been seen in the offline render.

### Spatial Siphon

A glass vessel on a brass foot. Every time the ship it is bolted to comes out of a rift, it catches a
draught of **Rift Essence** — a random amount, with a longer jump widening the odds rather than
guaranteeing anything. The yield is deliberately a lottery rather than a rate: it should be a by-product
of travelling, not a reason to bounce a ship between two anchors.

**One draught per jump, however many siphons are aboard.** The draught is rolled once for the ship and
poured into the vessels in turn, each taking what it has room for (`SiphonPool`) - exactly how a fissure
shares its drain. More siphons add room, not yield. Each used to roll for itself, which the old comment
allowed only because "there is no rate to balance yet"; once essence became a price - a rifting brazier,
a Rift Pearl, a collapse - a deck of ten siphons catching ten draughts made the price meaningless.
`SiphonPoolTest` holds it.

**Rift rain.** During a Rift Storm a siphon under open sky fills with the rain, which "is not water" -
`riftRainRate`, one millibucket a second, three times that above `riftRainCloudHeight`. The rain is a
budget per chunk of open ground, or per ship, shared between the vessels standing in it (`RainLedger`), so
a ring of siphons fills no faster than one. Aboard a ship "open sky" is asked in plot space, which means
"not under its own deck", and the altitude is the vessel's real one, through the ship's pose. It is the one
road to essence for a player who has no drive at all.

It is a real fluid with a bucket and a fluid-handler capability, so Create's pipes can drain it into a
tank.

### Spatial Stabiliser

A lidded drum on a plinth, with springs up both flanks and a gyroscope on top. It is a **Rift Drive
module**: placed against any face of a drive aboard an airship, and switched on and off by redstone and
nothing else. While it has a signal it **cushions the hull against sharp changes in its motion**. Light
the burners under a hot-air balloon on an unstabilised ship and it leaps; on a stabilised one it eases
upward. The core and the gyroscope's eye glow while the signal is on, so a pilot can read the lever's
position from across the deck. The goggles say whether it is actually biting.

**What it does, exactly.** Every tick Sable folds whatever is acting on the hull - lift, thrust,
gravity, drag - into a new velocity. The stabiliser compares that with the velocity it left the hull at
a tick earlier and hands back most of the difference (`stabiliserCushion`, three quarters by default),
so the ship answers its machinery as though it were four times heavier. It also bleeds away a small
share of whatever motion is left (`stabiliserSettle`), which is what makes a push that never lets up
settle at a gentler speed instead of reaching the same speed later. Rotation is treated the same way.
The arithmetic is `StabiliserCushion`, deliberately free of Minecraft so `StabiliserCushionTest` can fly
a toy hull through it.

**What it leaves alone, and why.**

- **Impacts.** A hull hitting a hillside changes speed in one tick by far more than a balloon ever
  could. Handing a share of that back would push the ship into the hillside again next tick, and the
  solver would spend the landing fighting the stabiliser. A change above `stabiliserJoltLimit` passes
  through untouched.
- **Teleports.** A warp or a Rift Gate moves a hull with `relocate` and may hand it new momentum as it
  does. The tell is the hull turning up somewhere its velocity could not have carried it, and that tick
  is taken as a fresh start rather than a change to smooth. Otherwise a gate's momentum carry would be
  dragged back towards the speed the vehicle had on the near side.
- **Warps.** While its drive is running a sequence, the drive commands the hull's velocity outright
  every tick, and the flight is planned in full before the aperture opens. A stabiliser pulling against
  that would drag the ship off course, so it stands by. This is why it is a module rather than a
  free-standing block: sitting beside the drive is how it knows.
- **A ship at rest.** Corrections too small to matter are dropped. Each nudge wakes the rigid body, and
  a moored ship that is woken every tick never goes back to sleep.

**They do not stack.** Each stabiliser measures against the velocity *it* left the hull at, so two
aboard would each absorb three quarters of what the other let through. Two would make the ship sixteen
times heavier and three would make it sixty-four. The obvious move of bolting on another to make it
stronger would quietly turn the ship into a brick. So the first stabiliser to tick on a hull claims it
for that tick (`HullClaims`), and any other aboard stands by and says so in the goggles.

**It has a second job on the ground.** Switched on within reach of a Collapse Cradle it steadies a collapse -
see the Collapse, below. A cradle never stands on a ship, so the two jobs never meet: a stabiliser with no ship
to cushion looks for a cradle it stands round (`CollapseCradleBlockEntity.steadiedFrom`, twice a second, among
the block entities of the loaded chunks about it) and its goggles say that, not that it has no drive.

**Why the ordinary tick and not Sable's physics tick.** `sable$physicsTick` is only called for block
entities in Sable's actor list, and membership of that list depends on a block having *changed* since
the plot was built - the same trap `Airship.machines` reads chunks directly to avoid. A stabiliser on a
ship read back off disk could silently never be called. The ordinary tick always runs, sees exactly one
physics step between calls, and adjusts velocity through the same pipeline calls a warp flies ships
with (`Airship.nudgeVelocity`, which adds to the velocity rather than replacing it as `driveVelocity`
does).

The block state carries only `powered`, the switch's position. Whether the switch is biting changes
every time a warp starts and stops, and putting that in the block state too would rewrite a block in the
plot each time - a collider rebuilt under a ship in flight.

This is the one piece of the mod that cannot be meaningfully checked without a running game.
`StabiliserCushionTest` proves the arithmetic against a toy hull. Whether the default strengths *feel*
right against real Aeronautics balloons and Sable's own drag is a play-test question, and the four
numbers are in the server config so they can be tuned without a rebuild.

### Cooling Vent

A brass-rimmed casing with a louvred opening in one face, a copper heat exchanger glowing at the back of
its throat, and a shaft socket in the back plate. It is a **Rift Drive module**: set against any face of
a drive - or of its frame of extenders - and **turned by a shaft, it halves the time the drive takes to
cool down after a warp**, and blows the heat out through the louvres as steam when the ship arrives.

**The numbers.** Each turning vent leaves half of what the last one left (`cooling.ventCooldownMultiplier`),
so one vent leaves a half, two a quarter, and four a sixteenth - a Mk I's two minutes becomes seven and a
half seconds. **A block of ice against the drive does the same job passively**, the way an unfuelled
Blaze Burner heats a boiler: no shaft, no stress, nothing used up, and a tenth off rather than a half
(`cooling.iceCooldownMultiplier`). The two share one limit - **four coolers count on a drive**
(`cooling.mostCoolers`), vents and ice together. Vents are counted first and ice fills what room they
leave, so swapping a block of ice for a vent can never make a drive cool slower; were the limit filled in
the order things were found, a pilot who bolted a vent on beside four blocks of ice would find it surplus.
"Ice" is a block tag of the mod's own, `aerowarptics:drive_coolant`, which holds vanilla's `minecraft:ice`
and so every kind of it; a pack can add to it. The arithmetic is `DriveCooling`, free of Minecraft, and
`DriveCoolingTest` reads it against the shipped defaults.

**Fixed as the warp completes.** The drive counts what is cooling it at the moment the hull settles and
sets its cooldown then, exactly as a Rift Storm's share is fixed then, and for the same reason: the
console counts a cooldown down in seconds, and one that could be shortened part-way through would make
that number a guess. A vent started half-way through a cooldown is in time for the next one. The storm's
share and the coolers' multiply - a storm and one vent leave a quarter.

**What it costs.** A vent is a Create stress consumer like any other (`cooling.ventStressImpact`, 4 su
per RPM) and does nothing below `cooling.ventMinimumRpm` (32). Its louvres point out of whatever it was
placed on and the shaft comes in at the back, so one set straight onto a drive has its back to the drive -
and a drive carries rotation on every face, as its extenders do, so that vent is turned by the drive, at
the drive's speed and so at the drive's price: 256 su on a Mk I's 64 RPM, 1,024 on a Mk III's 256. Turned
round with a wrench and fed from a shaft of its own at 32 RPM it costs 128. It only has to be *beside*
its drive, not backed onto it. An overstressed network reads as stopped, so a vent on a stalled network
cools nothing rather than cooling for free.

**Why a status and not a block state.** Whether a vent is venting changes with every warp, and each
rewrite of a block in a ship's plot is a collider rebuilt under a ship in flight - the stabiliser's
reason. The vent sends its status to the client instead, the goggles read it, and the steam is drawn from
it: `VentPlume` is the curve - a hard blast as the drive starts to cool, falling away over a couple of
seconds to a breath that lasts as long as the cooldown does, so a glance across the deck says whether the
drive has finished. A vent a player walks up to mid-cooldown joins the plume past its blast. Every way a
vent can be idle is named in the goggles - not beside a drive, not turning fast enough, or surplus
because the drive already has its four - and the drive's own readout says how many vents and how much ice
are counted and what they leave of the cooldown, which is the only place ice says anything at all.

The steam itself, and whether the default stress is the right price for a sixteenth of a cooldown, are
play-test questions; the five numbers are in the server config.

### Spatial Wave Detector

A brass-cornered casing with a wave running through a window in each side and ripples on top. It is a
**redstone source that pulses when something arrives through a rift nearby**: an airship finishing a
warp, or a player, mob or vehicle stepping out of a Rift Gate. Departures are never heard - a ship
leaving is something its own crew already knows about, and a door that rang both ways would ring twice
for every visitor.

**The signal is graded by distance**, the way a sculk sensor grades a vibration: 15 right beside it,
falling to 1 at the very edge of its range, and never nought inside it (`WaveReach.strength`). One
detector is a rangefinder as well as an alarm. Arrivals are measured as boxes, not points - a hull's
assembly bounds, a traveller's bounding box - so a hundred-block ship whose bow comes out beside a
detector has arrived beside it, whatever the distance to its middle. Several arrivals during one pulse
restart it and hold the strongest reading. The block lights with the signal, so it can be read from
across a yard as well as wired.

**The range is set on the block**, with Create's value box on any side, from 1 to 256 blocks
(default 32). The server caps it with `detectorMaxRange` (128 by default) and applies the cap on the
server as the value arrives, so the box a player sees always shows the range actually in force - the
client never reads server config to draw it. `detectorPulseTicks` sets how long a pulse lasts.

**Nothing scans.** The drive (at `onEmerged`, the moment the hull is back in the world) and the gate (as
it moves each traveller) call `SpatialWaveDetectorBlockEntity.announce`, which walks only the detectors
loaded in that level. A detector costs nothing between arrivals. Detectors aboard a ship are measured
from where they are in the world, so one on the deck of the arriving ship hears it at full strength.

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

With [Curios](https://www.curseforge.com/minecraft/mc-mods/curios) installed they also go in its head
slot, as Create's goggles do, so a helmet can be worn over them. Letting them in is only data (the
`curios:head` item tag and a head slot assigned to players under `data/aerowarptics/curios`); `isWorn`
then asks Curios as well as the helmet slot, and Create's own goggles renderer draws them. Everything
that names Curios is in `compat/curios`, reached only through `CuriosBridge`, which answers "no" when
Curios is absent - `CuriosCompatTest` holds that line.

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

**What is left is a Scar Heart.** A tear that seals leaves one behind: one, whatever it held, spawned as an
item with an unlimited lifetime because a fissure often seals with nobody watching, and never through a
loot table, because a fissure is nothing a player collects. The heart is the one thing a Singularity Core
can collapse into - see Crafting. So a fissure is a choice: leave it open as a free rifting catalyst for as
long as you like, or drain it for its essence and its heart. It never touches the ground round it; it once
grew Warp Crystal Ore into the stone, and that was retired so the choice is between those two alone.

**A fissure refuses to move.** Leaving one open is worth something now, so a hull assembled round a tear
must not carry it off. It answers Create's movement check with a flat no, and Simulated's physics assembler
asks that of every block it gathers and every neighbour before adding it. (Sable's own `/sable assemble`
gathers without asking.)

### Warp Anchor

A destination. Place one, right-click to name it, say who may travel to it, put it on a network, switch it
on or off. The record is kept in a server-wide `SavedData` file, so an anchor in an unloaded chunk is
still a valid destination — which is the point of long-range travel.

An anchor that gets assembled *into* an airship withdraws itself: its position would be a coordinate
inside Sable's plot grid, which nobody can navigate to.

### Networks

A **network** is a named group of players who share destinations. An anchor or a gate has three access
modes — **Public** (anybody), **Network** (the players in its network) and **Private** (its owner) — and
Network is the middle the first two never had: a base shared with four friends used to mean making it
public to the whole server.

Type a name into a machine's Network box and save. A name no network has **founds one**, with whoever
typed it as its owner; a name one already has puts the machine on it, if the machine's owner is in that
network. The **Members** button beside the box opens the network's panel, where its owner adds players
(by name, and online), removes them, renames the network or disbands it. Any member may put machines of
their own on the network; only its owner manages who is in it. `/aerowarptics network` does all of this
for an operator, and the three things the panel will not: found a network for somebody else, hand one to
a new owner, and add a player who is offline.

- **A network is a destination list's filter as well.** The strip above the Astrolabe's chart and a
  gate's dial list steps through every network the player can see a destination on. The filter is
  applied **on the server, before the list is cut** to the 256 rows one packet carries: a list cut to the
  nearest 256 first would have nothing left of a network whose anchors are all far away, which is the
  one case a filter is most wanted for. A public anchor on a network is offered to everybody, so a
  network also works as a plain category — "Server hubs" — with nobody in it but its owner.
- **A machine remembers its network by id, never by name.** An anchor's block is what re-asserts its
  record every time its chunk loads, so a network remembered by name and renamed while the anchor was
  unloaded would be renamed back by the first anchor to wake up. The name is looked up when it is shown.
- **Leaving a network never opens anything.** A member who leaves or is removed takes their machines off
  the network; a network disbanded takes every machine off it. Anything that was set to Network access
  comes off as **Private**. The records are changed whether or not the blocks are loaded, a loaded anchor
  is told as well, and an unloaded one checks for itself when it next loads (`NetworkRules.mayStay`) —
  as does Network access with no network behind it, which is Private under a name that promises more.
- **It is the machine's owner who has to be in the network**, not whoever is standing at the panel. An
  operator setting up somebody else's anchor cannot leave it on a network its owner is not in; it would
  come straight off again the next time the anchor loaded.
- **Who may travel where is one rule** (`NetworkAccess.permits`), asked by anchors and gates alike and
  tested as a table. An operator bypasses the mode, as before, so an admin can always recover a ship. A
  mode the server has switched off falls back to the owner alone. And as before, a redstone launch
  replays the permission the course was set under: a member removed after setting a course still has
  that one course, exactly as an anchor made Private after the fact behaves.
- **Text typed into the old Network box is adopted.** The box existed before any of this and nothing read
  it. Such a label becomes a network its anchor's owner owns, the next time the anchor loads — or joins
  one they are already in. Access is left as it was, so nobody gains or loses a destination.

`allowAnchorNetworks`, `maxNetworksPerPlayer` (16) and `maxNetworkMembers` (64) are in the server
config's `permissions` block.

---

## The warp

The drive propels the ship through the rift. The ship flies into the rift, keeps flying down the
corridor behind it, and comes back out of a second rift at the far end still moving.

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
   of its wind-up follow the same figure, so an underpowered drive audibly labours. The wind-up is a
   sound of its own: a hum that starts low as the rings begin to align and climbs with the spin until
   the rift opens, wavering slightly if the drive then has to hold at full spin. It is the one drive
   sound each client plays for itself rather than being sent, because a sound the server has played
   once cannot be turned afterwards (`SpinUpVoice` is the curve, `DriveSpinUpSound` the loop). Before
   it there was one clunk and then silence for as long as the spin took. The landing is
   claimed the moment spin-up starts, so the ground the arrival search is about to read loads in the
   background while the drive winds. A drive that reaches full spin before its landing has finished
   loading holds there — its goggle readout says it is waiting for the landing — for up to
   `arrivalPreloadTimeoutTicks`, rather than making the server generate hundreds of chunks in one tick.
6. **Planning** — the *whole* journey is worked out before anything opens: where the ship will come
   out and whether that volume and its run-out are clear. A warp with nowhere to land is refused while
   the ship is still safely at its mooring. The arrival coast is claimed here too, with self-expiring
   tickets laid from where the hull will reappear to where it will rest, and claimed again at the
   crossing, so the ground under the hull is held at the exact moment it is put down on it.
7. **The run** — an aperture tears open ahead of the bow and the server takes the helm, driving the
   hull at it. The pilot's controls, the ship's own thrust and gravity are all overridden until it
   comes to rest, and so is its attitude: it is held at the angle it launched at (see *Keeping the
   ship's angle* below).
8. **The passage** — the run ends when the *bow* touches the plane, not the ship's centre, and the
   hull then spends three seconds being drawn the rest of the way in. Each part of it disappears as
   it crosses (see below). Only once the stern is inside does the teleport happen.
9. **The corridor** — the hull simply keeps flying down the throat, out of sight, for a few seconds.
   A fold in space is not a distance, so it does not need to go anywhere; what sells the journey is
   what the crew sees, and that is a screen effect. As the corridor starts, the far aperture tears open
   at the destination, so anyone waiting there watches a rift form and *then* a ship come through it.
   How long it lasts is the tier's `warpTicks`, the same for a hop as for a haul - unless a server
   sets `flight.warpTimeScaling`, which lengthens it with distance on a straight line, a square root
   or a logarithm (`CorridorTime`; `warpTimeMultiplier` is what one kilometre adds, and
   `warpTimeLimitTicks` the most any warp is stretched to). It only ever lengthens: every curve starts
   at the tier's own figure. A corridor that outlasts its tickets renews the claim on the landing and
   on the ground it left as it goes.
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

### Keeping the ship's angle

Commanding the hull's velocity every tick, and zeroing its spin as it goes, reads as though nothing can
turn it. But Sable calls the drive once a tick and then runs two physics substeps, and in each of them
every sail and other lift block pushes on the hull with a force proportional to its speed, at that
block's own position. A warp flies a hull at thirty to a hundred and forty blocks a second — far faster
than it ever flies itself — so a lift block not balanced by another about the centre of mass twists it
hard. Zeroing the spin next tick stops it turning faster; it does nothing about the angle already
turned. So the hull leaned a little further every tick of the run in, was put back at the crossing
(which restores the attitude the flight was planned at), and leaned again coming out of the far rift.

`AttitudeHold` closes that. The flight renews a hold on the hull every tick, and after every physics
substep — Sable posts an event there — a held hull is put back to its launch attitude about its own
centre of mass and its spin taken away, its position and commanded motion left alone. Done per substep
rather than per tick, the lean never builds up, so no pose the server keeps or sends ever has it. The
hold lapses a few ticks after the last renewal, so a drive that dies mid-flight cannot pin a hull for
ever; `flight.holdAttitude` turns it off.

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
the moment the teleport lands, which is what the `updateLastPose()` there is for. The first fix
applied that same collapse to the copies the clients hold, and it was not enough, because a client
does not draw a sub-level where the server last put it. Sable draws it a tick and a half in the past,
**interpolated** between the poses it has buffered, and a teleport arrives in that buffer as just
another pose. So for one tick the client puts the hull half-way between the two ends — in chunks it
has not loaded, where nothing draws: the ship "vanishes" — and a one-shot collapse spent itself on that
half-way step and let the second half through as motion.

The crew had it worse. A player standing on a deck is carried each tick by the change between the
hull's last pose and its current one, and Sable keeps that change as the player's **borrowed motion**,
a vector separate from vanilla's delta movement that it goes on adding while they are off the deck,
losing only one percent a tick. Interpolated across a warp the change is a couple of thousand blocks:
the player is carried that far and keeps going. Meanwhile the server had already teleported them to
the far side, a tick before their client had the hull there, so even a short jump dropped them onto
nothing. Only somebody in a seat escaped, because a seat is fixed to the plot. That is what "launched
off the ship when not seated" was.

So the notice now carries the jump itself — a `HullJump`, the rigid transform from the hull's old
placement to its new one — and `relocate` sends it for every jump, not just a warp's, to exactly the
clients Sable has tracking the hull. `FoldCrossings` puts every pose that client holds for the hull
that is still on the near side through that transform: each buffered snapshot, and then the current
and last poses, **re-sampled** from the corrected buffer at the same moments Sable last sampled them
(an interpolated pose is on neither side, so it cannot simply be moved). As far as that client is
concerned the hull was always on the far side, and there is no jump left for anything to interpolate
or be carried by. A player the server took across is told so and moved by the same transform in the
same moment, instead of being sent a world-space teleport — so they keep their plank of the deck in
their own view of it, not merely the coordinates it has on the server.

The near side is looked for again for a second afterwards, on both `ClientTickEvent.Pre` and `.Post`,
because the notice and Sable's snapshots are independent streams and a snapshot sent before the jump
can land after the notice. Anything the hull did not carry — someone in the air above the deck at the
instant of the jump, whom Sable does not count as aboard — `relocate` takes anyway if they are inside
the hull's own footprint.

Borrowed motion has one more job. The corridor holds the hull still, so anyone in the air when it
stops went on at passage speed, and the old "take back the ship's momentum" zeroed only vanilla's
delta movement, which does nothing to Sable's vector. `WarpPassengers.calm` now settles both, and on
the player's own client too (`ClientboundSettlePacket`), leaving them only what the hull is still
doing — nothing once it has stopped, its flight speed if they are being put back on a moving deck. And
a client that finds its player borrowing a speed past the jump threshold drops it: nothing flies a
hundred and twenty-eight blocks a tick, so only a misread teleport could have lent it.

**A deck position worked out against the wrong pose is refused on the server.** A player standing on a
ship tells the server where they are in the ship's own plot coordinates, and Sable turns that back into
the world with the ship's pose. Sable sends a ship's poses on a channel of their own, with no ordering
against the notice of a jump, so a pose from just before the jump can land just after it and put the
client's copy of the ship back at the near end for a tick - while the player has already been carried to
the far one. That tick's deck position is off by the length of the warp. On a long enough warp it falls
outside Sable's plot grid, where Sable neither converts it nor sets it aside, and the server was handed
twenty-million-block plot coordinates as a world position. Vanilla refuses a move like that as "moved too
quickly", but never for a single-player world's owner, and Sable widens that to anybody on a ship. So the
server swept a collision box twenty million blocks long and froze for most of a minute: the ship hung in
an empty world mid-warp, menus would not open, and the rest of the warp played out a minute late.
`PlayerMoveGuard` puts the refusal back for moves of over a kilometre in a tick - nothing real comes near
it - and vanilla does what it always does, keeping the server's position and snapping the client to it,
which is on the far deck where they belong.

### Breaking space open

An aperture does not iris open. It arrives the way a stone arrives through a window, in three beats:

1. **The crack.** A white crush of pulverised glass where the blow landed, and fourteen radial
   fractures racing out of it, each at its own pace. Then ring cracks zip across between them, from
   the impact outwards, each only once both the radials it bridges have got that far, and here and
   there a short branch forks off a radial and runs out. As the web fills in, the cells between the
   cracks start to catch the light at slightly different angles, and just before the break every
   crack flares. There is no hole yet — the world behind the rift is still there to see, through a
   cracked pane.
2. **The break.** The pane goes from the impact outwards, a ring at a time, and the hole arrives
   fast rather than easing. The web goes with the glass it is drawn in: a crack stays exactly as long
   as the glass either side of it.
3. **The drift.** Seventy pieces burst away, mostly along the way the hull is travelling, like a
   window punched from one side. They tumble, fall, and fade over the next couple of seconds, well
   outliving the opening, so a new rift hangs in a slowly settling cloud of broken glass.

That fracture pattern — radial cracks plus concentric rings — is the one struck glass actually makes.
What makes it read as a *window* rather than a diagram of one is that it is not a clean polar grid.
`RiftShatter.Web` kinks each radial where it crosses a ring and lets each ring come out lumpy, and
those crossings are the corners every piece is cut at. The pieces that fall are exactly the cells the
eye has just watched being drawn. The old version drew thirteen straight spokes and then swapped in a
tidy wheel of wedges, which read as a star and then as a pie.

A fracture runs straight and turns sharply where it meets another, so the bends inside one run are
kept small. A crack that wiggles along its length is lightning, and a rift already has plenty of that.

`RiftShatter` cuts the pane deterministically from the rift's own position, the same way `RiftTear`
shapes the rim. Nothing about the fracture goes over the wire: two players watching one aperture see
the same glass break because both worked it out from the same coordinates. The entry and exit rifts of
a jump are metres apart, so they break differently from each other.

The cracks are a hairline core of near-white light with a soft glow in the rift's colour either side.
They are never thinner than about a pixel at the distance they are seen from, or a far pane's cracks
would flicker in and out between rows. The cells' sheen is not "how square-on is this cell", which
lights a flat pane evenly when seen face-on. It reflects the line of sight off each slightly sagging
cell and asks whether the reflection points at the sky, so neighbouring cells mirror different things.

Shards are drawn **translucent, not additive**, and on their own render type that writes no depth. A
fragment passing in front of a burning rim should darken it; drawn additively every piece disappears
into the light it came off. Each piece is a faint body inside a bright cut edge of fixed width, because
a pane is as thick as it is however it broke. The edge is what makes a translucent tile read as a
piece of something solid. Each piece keeps its own frame and tumbles in it, and its brightness is keyed
to how square-on it happens to be turned — so the field glitters as it drifts, which is the one thing
that says "hard reflective surface" without a texture to say it with. Everything about the throw eases
off except gravity, so a piece bursts clear, coasts, and is dropping by the time it fades.

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
every ring closes on exactly one turn and every ring meets the one inside it. It also checks that each
piece's real outline meets its neighbours' corner to corner. However far the web wanders, no two
cracks may cross and no two rings may touch. The whole web must be drawn before the pane goes, and no
crack may start from a crack that is not there yet.

One of those caught a real bug before it shipped. A quad is drawn as two triangles either side of the
diagonal from its first corner. Where a crack bends hard, a piece's corner can be pushed in far enough
that only one diagonal lies inside it, and split along the other it folds over its own outside.
`Outline.firstCorner` picks the diagonal that works, and the test holds every piece to it.

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

The mass term is why a drive's range is two numbers. The tier's `maximumRange` is how far it could
throw nothing at all; a hull spends part of every charge on its own weight, and runs out of charge
before it runs out of range (`WarpCost.Formula.reach`). The console shows both - **Max Range** and,
under it, **Ship Range**, amber when the hull is costing it distance and *Too heavy* when it is costing
it all of it - and the Astrolabe's header leads with the ship's. Showing only the tier's made every
anchor between the two read as a mistake: in range by the header, refused by the drive.

A warp into another dimension has no distance to measure — the two sets of coordinates are not in the
same space — so it counts as `range.crossDimensionDistance` blocks (1000 by default), and is priced,
range-checked and spun up for as a jump of that length. It used to skip the range and the price
altogether, which would have made the far side of a dimension the one place any drive reached for free.

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

It is the hull and `arrivalClearance`, and **not the aperture**. The arrival used to be widened to the
rift's radius on every side, underneath included, for the same reason the launch once was — and it
went for the same reason too: the rift is a picture, not a solid. At the arrival end it did real harm.
The margin is scaled from the hull, so on a big enough ship it pushed the tested volume further into
the ground below the keel than the search is allowed to climb, and a four-hundred-block hull was
refused a landing over flat ground on every attempt.

**A pilot can switch all of it off, per drive.** The console's *Clearance checks* button turns off the
bow guard, the corridor proof and the arrival search together, for that drive only. Off, a warp is
flown straight out of the mooring and set down at the search's own first candidate - straight above the
target, the arrival height over it - without a single block being read at either end, into whatever is
standing there. It exists because every check here has at some point refused a ship whose pilot could
see perfectly well there was nothing in the way, and a pilot who can see that should not have to wait
for the next fix. The checks are *skipped*, not run and ignored, so a switched-off drive does not pay
for a scan it will not act on. A free cruise is unaffected past its bow guard: slowing for terrain ahead
is how a cruise is flown, not a check made before it sets off. The switch is refused mid-sequence, saved
with the drive, shown in gold on the console and on the goggles while off, and written to the server log
whenever anyone flips it, because a ship found inside a hillside prompts exactly that question.

### Very large ships

Nothing in a warp should be a size limit on ships, and each of these once was — all of them silently,
refusing, freezing or dropping a large hull while a small one never noticed:

- **The hull is measured the way it sits.** `FlightGeometry` turns the plot's block box by the ship's
  orientation and reads its extent *along* the bearing, *across* it and up. The aperture, the passage
  and the run-out all come from those. They used to come from the hull's world bounding box, whose X
  extent depends on the compass: the same ship got a forty-block rift flying north and a
  two-hundred-and-seventy-block one flying east, and every margin and claim sized from it followed.
- **Ground is claimed along runs, not in one square.** A single square has to be as wide as the thing
  it covers is long, and was capped long before a big ship was covered. The arrival ticket was a square
  about the resting place, capped at eight chunks; a four-hundred-block hull reappears more than five
  hundred blocks behind that, over ground nothing held — which is what Sable answers by unloading the
  hull, and very likely the `INTERRUPTED` stall seen at the breach on large hulls. Tickets are now a
  chain of squares along the coast, the corridor and the preloaded landing, ends first, within
  `maxArrivalChunks`.
- **The ship is held, not the ground.** Every warp holds its ship with Sable's own force-load ticket,
  and so does `keepAirshipsLoaded` afterwards. Sable then keeps the ground under the whole hull loaded
  wherever it is put, at any size - see [Keeping things loaded](#keeping-things-loaded).
- **Too big to check is its own answer.** An arrival that needs more than `maxArrivalChunks` is
  refused at once as `arrival_unproven` — "the landing could not be checked in full" — instead of after
  four thousand candidates as "no safe arrival", which sent pilots looking for a different anchor when
  the anchor was never the problem.

What remains: a hull at a steep diagonal is still tested by its world-aligned box, so a very long ship
parked at forty-five degrees reads more terrain than it really occupies, at the bow guard and on
arrival. `LargeHullTest` sweeps hulls up to a thousand blocks across headings and pins each of the
above.

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

### Into another dimension

Experimental, and needs two things: `permissions.allowCrossDimensionWarp` on (it is off by default), and
[Dimensional Sable](https://modrinth.com/mod/dimensional-sable) installed on the server. Without both,
an anchor in another dimension is listed but refused as out of reach.

Sable binds a sub-level to one `ServerLevel` — its blocks in that level's plot grid, its rigid body in that
level's physics world — and has no way to hand one to another level. Dimensional Sable (MIT, by
HollowEgg) does: it saves the vessel, rebuilds it in the destination with everything joined to it,
carries across what it finds aboard, and removes the original. This mod calls it and does nothing of
the sort itself. It is reached through a method handle (`compat/dimensionalsable/DimensionalSableBridge`)
rather than compiled against, so the jar is not needed to build, and a later version that changes the
one method this mod calls reads as "not installed" — with a warning in the log — rather than as a crash
in the middle of a warp. Its own README says the Simulated team is building an official API for this;
the bridge is the one class to swap when that lands.

**When.** At the crossing — the tick a same-dimension warp teleports, with the hull deep inside the
throat — never when the rift opens. The arrival is proven and `CrossDimensionWarp.preflight` asked at the
mooring, so a ship with nowhere to go is refused before it leaves; the crossing asks again. The
preflight exists because Dimensional Sable throws partway through for two things it does not check:

- *No free slot* on the far grid for every part of the vessel (`dimension_no_room`). Sable lays each
  level's ships out on a fixed grid, and a slot stays taken while its ship is on disk.
- *A hull too tall for the far dimension* (`dimension_too_tall`). Sections are put back at the index they
  were saved at, and the Overworld has twenty-four where the Nether and the End have sixteen, so a hull
  reaching more than about sixty blocks above its plot's centre has sections the Nether cannot hold.

**When it fails anyway.** It builds every copy before it removes any original, so a throw leaves the
originals standing and copies in the destination. `CrossDimensionWarp.cross` takes back out every
sub-level that was not in the destination before the call, and the warp's ordinary recovery puts the hull
back at its mooring. Nothing is left in two places.

**What the rebuild changes, and who repairs it.** Dimensional Sable rewrites the positions it knows
about (vanilla, Create and Aeronautics machines, Simulated joints), but a rebuilt ship is otherwise a
new sub-level, and this mod has its own memories of the old one:

- *The drive is rebuilt too*, as a different block entity at a different plot position. A drive read back
  mid-flight normally treats it as an interrupted warp. Saved for a crossing, it writes `CrossingHandoff`
  instead — where it was, which ship it was on, that ship's user data, which bearings were turning — and
  the copy that reads it finishes the crossing on its first tick (`finishCrossing`). That tag is written
  only for the instant of that save, and is the one thing allowed to carry a flight through a reload.
- *The ship's UUID changes*, so the crew manifest moves to the new one (`CrewManifest.crossed`).
- *The ship's user data is not carried*, and this mod's claim on the ship lives there. The copy puts it
  back, pointed at the drive's new position.
- *Every block aboard moves by the same amount*, so the drive's own move says how far every seat
  remembered in ship space has to follow.
- *Riders are stood up* and their seats moved separately; they are sat back down.
- *Contraptions are taken apart*, and bearings are left stopped. Bearings on the hull that were running
  are started again a tick after arrival; pistons, pulleys and gantries stay as blocks.

The hull keeps its orientation through the crossing rather than taking the flight's planned one; the two
only differ by whatever the hull turned in the corridor, where its rotation is held at zero. The crossing
runs in one tick, which a very large vessel will make heavy; `[warp] crossed` logs how long it took.

**The ship that arrived and could not be seen.** The first crossing tried in a real game arrived intact
on the server and vanished from the player's view, with the client log full of "sub-level movement
packet for a non-existent sub-level". The cause is in Sable, and plain portal travel can hit it too.
Sable tells a client to forget a ship by its *grid slot*, not its dimension, and only notices a player
has left a level on that level's next tick - by which time the client is in the new dimension and has
been sent the ship there. Both grids fill from slot (0, 0), so a ship parked in the Overworld's slot
(0, 0) had its late removal delete the Nether's slot (0, 0): the ship that had just arrived.
`airship/DimensionChangeTracking` takes a player off the old level's tracking lists the moment their
dimension change completes, so Sable has nothing left to send.

Gates stay in their own dimension. A Rift Probe can scan another - see
[Sounding another dimension](#sounding-another-dimension). A Rift Beacon bound before a crossing still
points at the old dimension and drive position until it is bound again.

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
at vanilla's density and vanilla's shading, and it is vanilla's own drops: `RiftRainTexture` reads
whatever `rain.png` the resource packs supply and recolours it as it loads (`RiftRainTint`, tested
directly). Vanilla's rain is a single mid blue drawn at many transparencies - a mask - and a vertex tint
can only multiply, so no tint turns it purple; the mask is kept and only the colour behind it replaced.
It is a `SimpleTexture` under a name with no file behind it, so a resource reload rebuilds it from the new
rain, and no file of Mojang's is shipped. The storm's rain used to be a texture of its own - brighter,
denser, and glowing at night - and was the most distracting thing on screen in a storm. It falls in every
biome: it is not water.

It stops on a ship, which the level's heightmap has never heard of - a ship's blocks stand in its plot.
Vanilla's rain stops on a deck only because Sable wraps the heightmap reads inside vanilla's own two rain
methods, and the storm's rain, built the way those are but not by them, fell through every hull until it
asked for itself. Each of its reads of the heightmap - the drops, the splashes, the muffled sound under a
roof - is paired with a read of the ships over the column (`RainCovers`; the walk down through a hull's
plot is `RainCover`, tested directly). Sable answers in whole blocks, as vanilla's rain needs; a ship rides
at any height and the storm's rain can end anywhere, so this closes on the deck's own surface, and the
drops end on the hull where the frame draws it rather than where the last tick left it.

Overhead, distant bolts of rift fire flash the sky and ground violet (respecting vanilla's *Hide
Lightning Flashes*). The rest of the storm's sky hangs at infinity, in the sky's own rotation-only frame
(`SkyFrame`), each piece at its own share of the distance to the far plane so the nearer of any two is
always the same one: tears furthest out, then the vortex, the aurora, and the streaks nearest.

- **Tears** (`RiftStormTears`) open in the sky as a bright seam, part to show the End's starfield - the
  same `RIFT_END_SKY` surface an Ender rift's face is painted with - hang open, and heal. That surface is
  opaque and cannot fade, so a tear only ever closes: its opening is scaled by the storm's strength, and
  a clearing storm shuts every tear. It writes depth, so it is drawn first.
- **The vortex** (`RiftStormVortex`) is a slow spiral of violet-dark cloud round the zenith, with a faint
  light in its eye. Dark, so it is on `RIFT_SKY_SHADE`, blended by alpha; additive light cannot draw dark.
- **The aurora** (`RiftStormAurora`) is additive, so it barely shows against a bright day and dominates
  the night, which is what an aurora does anyway.
- **Streaks** (`RiftStormStreaks`) are shards of rift fire falling across the sky with a fading trail,
  sometimes breaking into two or three.
- **Cloud flashes** (`RiftStormCloudFlashes`) are sheet lightning: a soft glow hung in the cloud layer, drawn
  over the clouds, and a little of the same light lent to the whole deck's colour. They answer to
  `riftLightning` and *Hide Lightning Flashes* as well as `riftStormWeather`.

Their shapes - how a tear opens, how the vortex winds, how a flash stutters - are in `RiftStormShapes`,
free of Minecraft and sampled finely by its test for jumps and for anything left hanging at the end. Where
the clouds cannot be got out from under (Fabulous graphics, a shader pack, clouds off) the sky is drawn
straight after the sky dome instead and the clouds cover it, and the cloud glow is left to the deck's
colour alone.

**Rift Drives show the storm.** Every drive a storm changes - all but the Creative one - grows patches of
void edged in violet while it rages (`StormCorruptionLayer`). The drive is drawn twice more over itself
through vanilla's dissolve render type, `RenderType.dragonExplosionAlpha` - the ender dragon's death - whose
shader keeps a texel only where the texture's alpha is at least the vertex alpha, and draws it unlit. The
two sheets, `rift_drive_void.png` and its rim, come out of `tools/rift_drive_void.py`, read off the drive's
own UV layout, and their alpha is the order the void takes each texel in: spheres in model space, so a patch runs
round a post's corner instead of stopping at each face. Lowering the threshold as the storm gathers grows
the patches; the rim's threshold is always a band lower than the void's, so there is always an edge, and
at the start of a storm only specks of rim. A cutout render type with a faded colour cannot do this: in
1.21.1 the cutout shaders test the texture's alpha before the vertex colour is multiplied in. The core and
the needle are left clean. The thresholds are in `StormCorruption`, tested directly.

`MixinTargetTest` reads the mixins as bytecode and proves every injection lands on exactly one place in
the game's real classes, and that every mixin is registered - a mixin missing from the config compiles
and silently never applies. Nothing is synchronised between players beyond "a storm is raging";
weather is not an event at a place, so each client decides for itself where the fire falls.

### What goes with the ship, and what holds it back

A vessel is often more than one sub-level. A tender locked on with a Docking Connector, a turret on a
Swivel Bearing, a pair of Springs between two hulls: Sable joins each of these with a physics joint, and
follows them from machine to machine to find the whole chain (`SubLevelHelper.getConnectedChain`). A warp
moves **all of it**. `Airship.relocate` carries every part through the hull's own jump, with its own
passengers and its own notice to the clients that have it, and the flight commands every part the same
speed as the hull, so the vessel flies as one body rather than a hull towing dead weight through a joint.
The warp is priced on the whole vessel's mass.

It used to move the hull alone. A joint is anchored at a point in each of the two plots it joins, so the
part left behind was pulled after the hull across the whole jump. Moving every part by one rigid transform
leaves each anchor exactly where the other expects it.

Sable's walk reads each plot's list of *actors*, and a machine is only on that list if its block has
changed since the plot was made - the same lifecycle that once lost this mod its drive. A Swivel Bearing
missing from it is a bearing whose turret is not in the chain, and a turret not in the chain is, to the
clearance checks, another ship standing inside the hull's own box: every launch refused, on every
bearing, with no block to name. So `Airship` asks the same question of every machine actually standing
in each plot's chunks as well, and puts the two answers together (`VesselChain`). It never finds less
than Sable does. The trace prints both counts, and a bow guard fouled by a hull names it.

What cannot be carried stops the warp before anything moves: `Fastenings` looks for it by name, since
Sable has no way to list the joints on a body, and the drive refuses with *The ship is tied down*, naming
the thing and where it is. That is anything joined to **the ground** - a Docking Connector locked to one
on a pier, a rope to a post, a Sticker, a Swivel Bearing standing on the ground, a Physics Staff lock, a
Spring, a Plunger - and anything joined to **another ship** that Sable's chain does not include. The bug
that started this was the first of those: the drive could not move a hull locked to the ground, so it sat
through its whole run at the rift, was teleported anyway, and was dragged straight back when the
connector let go. Sable physics being paused refuses the same way, because nothing flies.

Two things are refused even between parts of the same vessel. **A rope** is a strand simulated in world
space with a joint at each end; its ends can be moved with their plots, but nothing moves its middle, so a
teleport would leave it hanging where the ship was. **Merging Glue** that is still merging is two
sub-levels part-way to becoming one, which is a few ticks' wait.

The check is made when a course is fired, again after the spin-up (a connector can find its partner and
lock while the drive turns), by a cruise, by an escort joining a fold, and by a Rift Gate for anything
crossing it. It is **not** part of the clearance checks, and the console switch does not skip it: that
switch is about terrain, and a tied-down ship cannot fly its run at the rift whatever the pilot thinks of
the terrain. The console's first requirement turns red while a ship is tied down, and the Fleet Board
reads *Tied down* for a ship in a flotilla. `/aerowarptics warp dryrun` lists every tie it found - the
ones the warp carries as well as the one that stops it.

A Handle gripped from off the ship also joins it to the world, but who is gripping it is private to the
Handle and a crew member steadying themselves on one aboard makes no joint at all, so it is left alone.

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

A drive also gives a signal **out**, to a comparator: 0 idle, 1 cooling down or clearing a fault, 2 to
13 as it charges in twelve even steps, 14 fully charged, and 15 from the moment a course is locked until
the hull has settled at the far end (`DriveSignal`). Fourteen is exactly the state a launch signal is
taken in, so a comparator and a repeater are enough to make a ship throw its own lever when it is ready;
fifteen holds through the whole warp rather than dropping as the rift opens, because a warp is one thing
to a circuit. A formed Drive Extender reads as its drive.

A **Spatial Stabiliser** reads redstone the other way: by **level**, not edge. It is a switch rather
than a trigger, on for as long as it has a signal and off the moment it loses it, so the same lever that
fires a drive's rising edge can hold a stabiliser on.

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

The idea in one line: **each new drive is made from something only the last one could reach.** The
ladder asks for a different act at each step - **build**, then **open**, then **fold**, then **collapse** -
and every act is one of the mod's own verbs rather than more mining.
`docs/crafting-plan.md` is the full design; this is what was built.

| Tier | Act | What it asks for |
| --- | --- | --- |
| Mk I | build | the workshop only: Rift Casing, a Rift Core off an assembly line, plain lenses. No essence and no rift anywhere in its tree - the first rift is torn blind |
| Mk II | open | Opened Rift Lenses, which only rifting makes |
| Mk III | fold | Folded Warp Crystal, folded at a Collapse Cradle for essence - so a cradle, and so a Relic Armillary out of a ruin |
| Singularity | collapse | a Singularity Core collapsed out of a Scar Heart, and Pearlescent Warp Crystal folded at the same cradle |

Everything that tears a rift of its own - the drives from Mk II up, gate controllers, chutes, probes and
couplers - is built with an opened lens. What receives, listens, sees or dresses a rift keeps the plain one.

**Rifting** is a Create fan process, registered into `create:fan_processing_type` beside washing and
haunting. Items blown through a *standing* rift are rifted: a Rift Fissure (free, for as long as it stays
open, which turns a found scar into the best industrial site in the world), a lit **Rift Brazier** (two
millibuckets a second while it has a signal - a Blaze Burner the rift has got into, its head dark and
its eyes burning, turning to watch whoever is near, and burning with rift fire: flames drawn the way a rift
is, a void-dark core in each tongue and a hot torn rim rippling up it, parting on the near side so the head
shows through (`RiftFlame`); drawn by its renderer in its own frame, so they burn aboard a ship too. Its
collision stops at the firebox, nine pixels high, so a fan's air passes over it and the cage above is only
bars), or an open gate's Rift Portal pane. A pool of essence is deliberately not one - a placed bucket would
be a free catalyst for ever. Create does not tell a fan type which catalyst an item passed, which is why a
brazier burns by the second rather than by the item. A creature in the stream takes a chorus-fruit hop,
through the chorus fruit's own teleport event so a protection mod can stop it, and never one a ship or a
contraption is carrying, a boss, or anything in `#c:teleporting_not_supported` (`RiftHop`).

**Folding** is the Collapse Cradle's lesser work. What hangs over its heart is folded - with what its cup holds,
where a recipe asks for a second thing - for Rift Essence from its tank, up to sixteen crafts a run, while the
cradle turns at `collapseFoldMinimumSpeed` or faster: Warp Crystal into **Folded Warp Crystal** (100 mB each),
Folded Warp Crystal and a Shulker Shell into **Pearlescent** (250 mB), a compass into a **Ship's Compass**
(50 mB). A fold is a few seconds of machine rather than a minute of spectacle: the stack rises to just
under the rings, a small rift draws it up into their middle while the outer ring whirls, it folds with a flash,
and what it made is lowered back down and set in the tray. Refusals come first and are
named (`CradleFold`), as a collapse's are, and the whole run's essence is paid as it begins. Folding once
happened aboard a ship, in a Fold Crucible, on one long enough jump; that tied a crafting step to the one
part of the mod most likely to stall on a large hull, and the crucible is gone. Nothing folded is needed to
build the cradle (`ProgressionActTest.theCradleNeedsNothingFolded`), so the Mk III needs a cradle and a found
Relic Armillary, and no circle. A **Ship's Compass** comes out unbound, since a cradle is never aboard:
sneak-use it on a Rift Drive and it points at that ship ever after, bound by plot position as a beacon is.

**The Collapse** is a ritual at a **Collapse Cradle**: a Rift Core and a Scar Heart in its slots, offerings
on up to eight Create Depots round it, four buckets of essence in its tank, turned from below at the
Singularity's own speeds (if you cannot turn the cradle, you cannot fly the drive it makes), started from its
panel or on a redstone rising edge. Pre-flight refusals are named (`CollapseFailure`) and come before anything
is spent, and nothing new starts until a settled core is back down from the last.
Over about a minute it spins up, opens a rift (dressed by a Rift Modulator beside it), draws the offerings in
and winds; instability builds from a storm, from turning below optimal and from a tank running short, and is
damped by switched-on Spatial Stabilisers - more of them, down to a floor, and only if they stand evenly:
balance is the length of the sum of their offsets over their count, so a ring of opposites cancels and a
lopsided cage wobbles (`CollapseInstability`). It ends settled (the core on the cradle), flung (the core
thrown clear, glowing - it "declines to be precise", the Singularity's vice at birth) or spat (nothing made;
the Rift Core and every offering thrown back out whole). **The heart and the essence are the only stake.**
The offerings are the grind; the heart is the adventure, and a Scar Chart makes finding another scar a trip
rather than a search. A ritual holds its own chunk on the expiring reach ticket, pauses whenever it cannot
tick, and never resolves while paused (`CollapseRitual`).

**What the cradle counts, it pulls on.** A depot one block too far out, a ninth depot, and a stabiliser
nobody switched on all look exactly like ones doing their job, until a collapse is flung for want of them. So
each client draws the machine's pull on its ring (`CradleLinks`): motes of the light its carvings glow with,
rising out of every linked depot and every switched-on stabiliser, wandering in along a line nobody can see as
dust does in a draught, and gone as they reach the rim of the rings. A depot's drift in along the bow an
offering will fly; a stabiliser's come straight. They move at under a block a second, a new link throws a few
sparks once, and they fade while a collapse runs, when the offerings have left their depots and the collapse's
own lightning is what reaches for the stabilisers. `cradleLinks` in the client config turns them off.

*It is a force, not a display.* The first version drew threads of light - pulses, rings round each block, a
dark edging so they read at noon - and was sent back at once: too noticeable, and a hologram laid over a machine
that is meant to be ancient. That manner is the Rift Plotter's, whose guides are instructions to a pilot. The
cradle shows nothing with an edge; all there is to see is what the pull moves.

**It links to eight depots, and keeps to them.** `CradleRing.linked` carries the links from one look to the
next: a depot that is linked keeps its place until it goes, and only a place that is free is given out, to the
nearest depot without one. The first rule was simply "the nearest eight", and it failed the moment the links
could be seen: every depot set down nearer than the furthest took a link off another, so to a player building
round the machine each new depot linked, long past the eighth. The cradle looks once a second, saves which
eight (the order depots were set down in is not something a world remembers) and tells every client, since a
client cannot work it out either. Stabilisers need no memory - every switched-on one in reach counts - so a
client finds those for itself, by the reach the cradle sent it, never the server's config.

Who is in the ring is one rule, `CradleRing` - pure, and asked by the cradle that draws the offerings, the
client that draws the motes, and the stabiliser whose goggles now say **Linked to a Collapse Cradle** or
**Steadying a collapse** there, where they once named the fault of a stabiliser with no drive.
`CradleRingTest` holds the rule and the motes' curves, and `CradleRingWiringTest` that all three ask it.

**It is a set piece, and a landmark.** Where everything is and when is `CollapseChoreography` - pure, so
`CollapseChoreographyTest` can hold that no offering jumps at a beat, the rift never cuts inside the hole
it closes onto, the hole never shrinks before the hush and everything is swallowed by the end. The beats:
the core rises and the offerings lift off their depots; a rift tears open and they fly into orbit round it,
while a column of light climbs 320 blocks out of it and the day dims round the site (`RiftStormSky.lend`,
reaching 160 blocks); at the fold the rift closes down onto a ball of darkness - opaque and writing depth,
so the disc of light turning round it passes behind it and the offerings vanish into it - with space bent
past its edge through `GravityLens.bend`; through the winding the orbit tightens and quickens, lightning
reaches for each stabiliser and the Scar Heart beats faster; in the hush the loops cut out, the column falls
into the hole, the heart is drawn up and the hole shrinks to a point; then the flash, a boom the server
plays at a volume heard 128 blocks off, and a ring of air along the ground. `CollapseSpectacle` draws it on
each client, from a clock the cradle runs on between syncs, and the cradle's renderer draws the items.

What shows how unsteady it is is where its instability is **heading** - now, plus what the rest of the
winding adds at the current rate (`CollapseRitual.heading`) - not where it has got to, so a lopsided cage
looks lopsided from the moment the rift opens: the hole wobbles, the disc flickers and sags away from the
side the stabilisers stand on, lightning strikes the ground. For steady conditions the heading told at the
first tick is exactly where the ritual ends, and `CollapseRulesTest` holds that.

**It pushes back.** As it winds it draws loose items, experience and creatures in, gently and then hard in
the hush, within `collapsePullRange` - players by their own clients from the same arithmetic, since a
player's movement is theirs, and never faster on the ground than a player can walk away. At the collapse
everything within `collapseBlastRange` of the point is knocked back, less behind cover, and a flung or spat
collapse hurts what lives there (`collapseBlastDamage` at the point, half for a flung core, nothing for a
settled one) through the `aerowarptics:collapse` damage type, which is in `#minecraft:is_explosion` so Blast
Protection answers it. It breaks no block, hurts no item and leaves armour stands standing, and the blast
goes before anything is thrown out, so the collapse never scatters its own results.

**The cradle is forgotten technology, and moves like a machine.** It is one of *theirs* - whoever left the
broken gate rings and sunken vaults in the rift scars - rebuilt to a pattern nobody understands: three blocks
across and three high, a purple-and-brass plinth, four carved green pillars and a roof with a lock ring set in
it. The model is an artist's. The one part of it nobody can make any more is the **Relic Armillary**, three bronze rings nested one in
the next, which the recipe asks for and only a ruin's cache hands out (a barrel in every scar, one roll in
0.4 per barrel); everything bright on the machine - the lock ring, the pistons, the sun gear - is the player's
brass. It is placed whole from one item, like a bed: a master
block in the middle of the floor holds the ritual and takes the shaft, and twenty-six part blocks
(`CradleStructure`) pass it every click, pipe, funnel, signal and pair of goggles. It refuses to be pushed,
moved by a contraption, built into a ship or set down on a deck - a twenty-seven-block machine carried by
something that moves blocks one at a time arrives in pieces. Nothing on it is animated: every moving part is
posed each frame from the ritual's own clock (`CradleWorks`). The sun gear and the pistons work with the shaft;
the Rift Core hangs over the heart, rises to just under the rings and is taken the rest of the way by the tearing
rift; the lock ring on the roof turns as the ritual starts; the rings lie flat until the core is among them,
tumble inside one another from the fold, and brake flat in the hush, after which a settled core comes back down to
hang over the heart. Each ring, turned any way, clears the one round it (`CradleRigTest`), which is why the rift
and the hole are sized to fit inside the innermost ring and the offerings orbit outside the pillars. The carvings in the stone and bronze light through a glow mask drawn additively as the ritual builds.

**It looks like it is failing when it is.** From the same heading the scene reads, and from the moment it is
known: past a third of the way to a spit the machine starts to go, and by nine-tenths it is going everywhere at
once - the whole frame shudders, the outer ring rocks on its rollers and carries the other two with it, the
sun gear slips in jerks, the pistons misfire, the lock rattles, sparks fly off the rings and joints, smoke vents from the plinth, something clanks as it
gives and something hisses, and the carvings stutter and run from violet to an angry red. All of it is
smaller than the machine's own clearances - the rings rock under two degrees. A steady collapse does none of
it.

**Storms gate nothing.** They are rare, and only a Stormglass - which a storm must set first - can call one,
so they modulate: a storm shortens a collapse and makes it wilder, and rains essence into open vessels. A
**Stormglass** reads clear, gathering, raging or still, honestly, because the server decides the calm in
advance; the reading rides the storm packet and is resent whenever it changes. Broken, it calls a storm -
refused while one rages, while the weather is frozen, within `callMinimumCalmTicks` of the last one, and
always when natural storms are off.

**Stock Create steps, put to work.** The Rift Core is a sequenced assembly on a Precision Mechanism, two
loops of tube, crystal, brass and a press. A **Rift Pearl** is 250 mB of essence in an ender pearl, filled by
a spout and 200 mB back at a drain - essence in item form, for recipes that cannot take a fluid, and the
Rift Beacon's topping. Thrown, it hops whatever living thing it hits somewhere within eight blocks, as a
chorus fruit hops whoever eats it, through the same event and with the same exceptions as a rifting hop
(`riftPearlHops`). It moves what it hits and never its thrower: carrying the thrower would have to fire
the ender pearl's own teleport event, which claim mods watch by that type. **Rift Casing** is warp dust applied to a brass casing, and every machine that took a
brass casing takes one. The Spatial Stabiliser takes Simulated's Gyroscopic Mechanism. The Cooling Vent is
the stabiliser's shape with iron bars for louvres and a Create Propeller behind them, cheap enough that a
full set of four costs less than the Mk I they cool.

**Three recipe types of the mod's own** - `aerowarptics:rifting`, `aerowarptics:folding`,
`aerowarptics:collapse` - each shaped like a Create processing recipe with a JEI category, so a pack can hang
its own progression on them. Recipes load before a world's server config, and the mod has no common config,
so the old ladder is not a switch: it is the **Classic Recipes** data pack, registered with
`PackSource.FEATURE` so it is listed and left off, and turned on per world.

A **Warp Anchor** takes a plain lens rather than a core, deliberately. Anchors are placed in numbers - a
network of twenty should be a project, not a wall - so the expensive component lives in the drive that
reaches them, not in every destination. The same reasoning applies to the **Astrolabe**: a craft makes three
panels and a table needs nine, and the whole table still costs less than the cheapest drive it steers.
**Drive Extenders** come sixteen to a craft and are priced in whole frames - a three-cube costs less than
the cheapest drive, and a full seven-cube less than the top one. The **Creative Rift Drive** has no recipe,
and `ResourceIntegrityTest` asserts that it has none rather than merely skipping it.

**The tests hold the shape.** `RecipeBalanceTest` prices both the default recipes and the Classic overlay by
crafting effort and holds each tier to one of the tier below and a step of ×1.7-3.0: by default 68.5 →
145.5 → 290.5 → 656, and Classic's 64.5 → 139.5 → 280.5 → 598. A price proves nothing about an act, so
`ProgressionActTest` reads the recipes as a graph: there must be *some* road to a Mk I with no essence, rift,
fold or collapse in it, and *every* road to a Mk II must pass an opened lens, to a Mk III a fold, to a
Singularity a collapse and a fold - and the cradle that folds must need nothing folded. `IngredientProducerTest`
checks every ingredient against every recipe and loot table on the classpath, Create's and vanilla's
included, because Create's chromatic line and Aeronautics' levitite both shipped in these recipes with a
price and no way to obtain them.

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
uses for its own. Nineteen of them: the Spatial Siphon, the Warp Anchor, the Astrolabe, the Rift Probe,
the Rift Drive, the Drive Extender, the Rift Chute, the Rift Modulator, the Spatial Stabiliser, the
Cooling Vent, the Spatial Wave Detector, the Rift Beacon, the Rift Brazier, the Collapse Cradle (folding and collapsing), two for the Rift Gate — building the ring, and getting something
through it — and two for the Rift Fissure, which is finding one and closing it. They are grouped into two chapters of the Ponder index, `Warp Travel` and `Rift Gates`, so
a player who only wanted a doorway between two bases does not have to read about airships to find it.

The Navigator's Handbook is listed in both chapters and has no scene of its own, deliberately. A scene
shows a mechanism working and a book has no mechanism; a Ponder scene explaining the book that explains
the mod would be a lesson about a table of contents. What it wants is to be somewhere the index will
show it, which is what a tag does.

Six scenes do not show the thing they are about, deliberately. A warp moves a Sable sub-level and a
Ponder scene has no airship in it; a gate's aperture is drawn by the client from live gate state and a
Ponder level has none; a rift's colour only means anything once one is actually torn, and a Ponder
level tears none either. Rather than animate a lie, those scenes teach what a player can act on before
they reach the console — where the machine goes, what it consumes, what decides whether it will run —
and leave the spectacle to be seen from the deck. That is the more useful half anyway: what people get
wrong about a gate is the ring, the power and the pairing, none of which an animation would have told
them.

Three scenes reach into their own build rather than only talking about it, and each is a small argument
about what a scene is for. The **Drive Extender**'s completes a cube of casing round a buried drive and
forms it, the way the Astrolabe's scene forms its table and for the same reason — forming is a server's
decision and a Ponder level makes none — writing each cell's pointer, then the drive's frame, then the
states, in the order a real level would. The **Spatial Stabiliser**'s is the one scene here that does
show the thing it is about: it lifts the machine off the plate twice, once with the switch off and once
with it on, because what a stabiliser changes is not where a hull goes but how sharply it gets there,
and that is exactly what a still picture cannot say. The **Spatial Wave Detector**'s cannot
show an arrival, so it marks where one happens and writes the reading onto the detector, the dust and
the lamp block by block - a full one and then a faint one - because the lesson is that the signal is
graded, and a Ponder level does not carry redstone on its own.

All three are held to their schematics by `PonderSceneryTest`, which reads the shipped `.nbt` by
position rather than trusting the script that wrote it. A storyboard that reaches into a build by
coordinate breaks silently when the build moves: a frame declared round a cube that is no longer there
draws a drive the wrong size with loose casing inside it, and a stabiliser that shipped switched *on* is
switched off by the one instruction meant to switch it on, leaving every sentence after it backwards.
Nothing throws and nothing is logged.

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

The machines' screens share a palette: the drive's console, the Astrolabe's chart, a gate's dial panel, an anchor's settings,
the probe, a chute's panel and a modulator's panel — which share a palette, a set of drawn shapes and,
the part that was missing, a layout — plus the Navigator's Handbook, which deliberately shares only the
last of those. A
handbook is not a machine, and dressing it in the machines' brass-on-near-black would have made it read
as one more readout screen rather than as something you sat down with, so it has leather boards,
parchment and its own palette in `AWBookStyle`.

The **Collapse Cradle's panel** is the other exception, and the only screen with slots. The cradle is the end
of the game, so its panel is dressed as the machine is: a slab of rift-stone in a verdigris frame with brass
plates bolted at the corners and a carved channel round the edge that lights as it works. The machine is
drawn in elevation on the left with its slots where they are on it - the arms in the rings, the cup in the
plinth, the tray below - and the offerings on their depots down either side; a dial in the middle shows
where a collapse's instability is heading against the config's bands; the tank, the shaft and a plot of the
stabilisers' lean are on the right; and under them, Begin, with what it would make and cost or why it would
refuse. It fails with the machine: the carving flickers red, cracks spread in from the corners and the needle
will not keep still. Everything on it comes from a `CradlePanel` the server builds from the same plans a
redstone pulse would act on, so it never offers a start the cradle then refuses. `CradleLayout` is its
arithmetic, and `CradleLayoutTest` holds it to 320 pixels and its parts apart.

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

## Keeping things loaded

Minecraft only runs the parts of a world somebody is near, and nearly everything this mod does happens
somewhere nobody is: at an anchor across the map, aboard a ship parked in the wild, at the far end of a
gate. So the mod loads what it needs - and only while it needs it. **Nothing here is an always-on chunk
loader.** Every hold is asked for by something happening now, is renewed while that goes on, and lets go by
itself when it stops. `/aerowarptics holds list` shows what is held, by what, and for how much longer.

| What | Holds | For how long |
| --- | --- | --- |
| A warp | its own ship | from lock-in until ten seconds after it lands |
| `keepAirshipsLoaded` | a drive-carrying ship where it was left | while its drive is there; let go if it drifts off unmanned |
| An Astrolabe | the ground round the anchor a player is looking at | about thirty seconds, one anchor per player |
| A Rift Beacon | the ground its ship was last seen over, if that ship is not loaded | until the ship is back, then the warp holds it |
| A Rift Chute | its partner - or the ship its partner is aboard | while items are being offered to it, and fifteen seconds after |
| A Rift Gate | both ends, and the ship either end is aboard | while connected |

**A ship is held with Sable's own ticket, never by forcing the ground under it.** Sable keeps a ship only
while *every* column under it is in block-ticking range - not merely loaded - and unloads one with nobody
aboard the moment that stops being true. The mod used to force the ground a parked ship was over, renewed
every twenty seconds, and the tickets a warp laid down reached only "loaded" at their edges. A ship a warp
had just put down - a beacon's summon above all, with nobody aboard - landed on ground that was not
enough, and Sable filed it away under whichever column failed. A large hull never came back from that: no
player's simulation distance covers all of it at once, so the whole of its footprint is never ticking
together again. Sable's ticket instead makes Sable keep the ground under the ship itself, wherever it goes,
through a warp's teleport included.

**Nothing can outlive its reason.** Sable saves its tickets with the world, which is what brings a parked
ship back after a restart - and what would keep a ship loaded forever if nothing let go. So each hold is
also written to the mod's own ledger with a lease in game time, renewed by whatever holds it; the lease
stands still while the server is down, so a restart neither spends it nor refreshes it. A hold nobody
renews - a drive broken by something that skipped its removal, a ledger restored from an older backup than
the tickets - is let go within a minute. Everything that is not a ship is an ordinary expiring ticket, which
has nothing to release and so nothing to leak.

**Never a chunk in plot space.** A ship's blocks live in reserved chunks at enormous coordinates that
Sable serves itself. A ticket placed there is what once stopped every world with a drive in it from
finishing its save on exit, and left empty `r.40001` region files behind. Every request to load anything
goes through one class, which refuses plot space outright; a machine aboard a ship that wants its far end
loaded is reaching for a *ship*, and is sent to where that ship was last seen.

**Ships that are not loaded can still be found.** Sable gives a ship back the same plot slot when it
reloads, so a plot position - all a beacon or a chute knows about a machine aboard - still names that ship.
The mod notes where each ship carrying one of its machines is, and again at the moment Sable unloads it;
reaching for one loads the ground it was last over, block-ticking, until Sable rebuilds it.

**A level with a held ship keeps ticking.** Sable's ticket does not count as a forced chunk, and a
dimension with no players and none stops ticking its block entities after fifteen seconds - the drive of a
held ship included, while its hull carries on without it. One ordinary forced chunk, under a held ship and
never ticking, keeps the level going, and goes with the last held ship.

**Removing the mod is safe.** Sable logs a ticket of a type it does not know and skips it, and NeoForge
drops forced chunks belonging to a mod that is not there; the ships load like any other build.

For a ship stranded before any of this existed, `/aerowarptics holds reach <x> <z> <radius>` loads a square
of ground for fifteen seconds; a ship Sable filed away there comes back, and if it carries a drive, the
drive holds it from then on.

---

## Configuration

`aerowarptics-server.toml` — range, cost formula, arrival search and clearance buffer, the
flight through the rift (aperture stand-off and size, approach speed, passage duration, run-out
length), permissions, failure behaviour, Rift Storms, cooling (what a vent and a block of ice each leave
of a cooldown, and how many count), diagnostics, and a block of settings per drive tier.
Corridor duration and run-out time come from the tier, so a Singularity drive crosses faster than a Mk I.
The corridor needs no settings of its own: it is flown inside the entry aperture, at the speed the hull is
already doing. The one thing a server can add is distance - `warpTimeScaling` and the two settings beside
it make a longer jump a longer corridor, and are off unless asked for.

It is a NeoForge *server* config, so it is **per world**: `saves/<world>/serverconfig/` in single player,
`world/serverconfig/` on a dedicated server. A copy dropped in `config/` is not read; one in
`defaultconfigs/` seeds each new world.

Its `loading` block switches each on-demand hold on or off - the Astrolabe's preview, the beacon's reach, a
chute holding its partner - and sets how far an unmanned ship may drift before residency lets it go.

`config/aerowarptics-client.toml` — particle density, rift distortion, rift lightning, corridor
effects, screen shake, effect volume, Rift Storm weather, and the client's own trace switch. Two of
them are there for players a warp is unpleasant for rather than for taste: `warpFov` stops the field of
view stretching in a corridor or a cruise, and `warpSounds` drops a warp's sounds altogether. The sounds
are dropped by name on their way into the sound engine (`WarpSoundFilter`), since most of them are
played by the server and arrive like any other sound - which is also why a brazier or a probe that
borrows one goes quiet with the drives. `effectVolume` is applied in the same place, to every sound in
the mod's own namespace: each is played at half what it was asked for, times the setting (0 to 2), and a
sound asked for louder than full keeps its reach without the loudness (`SoundMix`).

---

## Diagnosing a problem

A screenshot says what something looked like, and a report says "it didn't warp". Neither says why.
The trace does: one switch, `debug.trace`, and the mod writes its reasoning to `latest.log` — at INFO,
so there is no logging configuration to change to collect it.

**Turning it on.** `/aerowarptics trace on` — for operators, or the owner of a single-player world even
with cheats off — sets it and saves it. Or set `debug.trace = true` in the world's
`aerowarptics-server.toml`, which is under `saves/<world>/serverconfig/`, **not** `config/`; editing the
wrong file is the commonest reason a log comes back with nothing in it. Then reproduce the problem and
send `latest.log`. Every player connected while it is on traces their own side into their own log too.
`/aerowarptics trace report` writes the header below, and the whole state of the drive aboard the
player's ship, on demand. It is verbose — a warp writes a line every tick of its flight — so it is off by
default and should be turned off again afterwards.

**What it writes.** Every line starts with a topic in brackets, and comes from the AeroWarptics logger,
so searching the log for `aerowarptics` finds all of it.

| Topic | What |
| --- | --- |
| `[trace]` | the header: versions, which config file was read, every value in it, and each tier's real reach |
| `[drive]` | state changes, every refusal, redstone signals ignored and why, console actions, spin-up |
| `[course]` `[chart]` | courses set and by whom; what each player's chart showed them, when it changes |
| `[access]` | a player refused command of a drive: how far from it, whether aboard |
| `[claim]` | a hull's warp lock - who holds it, a stale one released, one left from a restart |
| `[launch]` `[arrival]` | the flight plan's checks: what the bow guard hit, how many places the arrival search tried |
| `[warp]` | the flight itself, below |
| `[crew]` `[crossing]` `[residency]` | who boarded, who came adrift and was put back; dimension crossings; a ship let go for drifting |
| `[hold]` | every ship held and let go, and why; leases that lapsed; reaches for unloaded ships; the chunk keeping each level awake |
| `[gate]` `[probe]` `[fleet]` `[beacon]` `[anchor]` `[storm]` `[detector]` `[siphon]` `[rifting]` `[fold]` `[collapse]` | each of those, their refusals first |
| `[network]` | networks founded, renamed and disbanded; members added and put out, and by whom; machines taken off a network; every refusal, with who was refused what |
| `[client:...]` | the player's side: each rift cue and whether their client had the ship, hull jumps, the messages they were shown, graphics and shader state |

**Refusals carry the whole situation.** A reason on its own is often not enough - most have several
causes - so every refusal and abort is followed by the drive, the ship and, when a course is set, its
price:

```
[drive] (…) REFUSED destination_too_far - mk_i CHARGED mode=WAYPOINT charge=100.0% rpm=64.00 (needs 32, full rate at 64) … | ship=… mass=203114 … | to (1520, 70, -300) in minecraft:overworld: distance=1532 mass=203114 cost=(base 0.200 + distance 0.061 + mass 0.762) / efficiency 1.00 = 1.023 of a full charge (OVER A FULL CHARGE); range 128-4000; reach at this mass 958 -> destination_too_far
```

That is the shape of the report that prompted the trace to cover refusals at all: an anchor 1,532 blocks
away, a Mk I whose chart says it reaches 4,000, refused as "beyond drive range". A jump is only in range while it fits in one full charge, and a
200,000-mass hull spends three quarters of that charge on itself. The header's reach table shows the
same thing for every tier before anybody has pulled a lever:

```
[trace] tier mk_i: range 128-4000, efficiency 1.00; reach on a full charge by hull mass: 0=4000 25k=4000 50k=4000 100k=4000 200k=1250 400k=NONE 800k=NONE
```

`/aerowarptics warp dryrun` answers the same questions without launching, pre-flight first (power,
charge, the hull's lock, the course's price), and writes its answer into the trace as well as chat.

### The flight

Every warp writes its own account, about ten lines plus one a tick:

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

A crossing into another dimension logs two more lines whether tracing is on or not, because it is the
heaviest and newest step a warp takes:

```
[warp] crossed 8f3c… minecraft:overworld -> minecraft:the_nether parts=2 bearings=2 in 41.20ms
[warp] handoff drive=(20484108, 70, 20478016) in minecraft:the_nether resumes stage=CORRIDOR plot moved (4096, 64, -2048) ship 8f3c… -> 1d70…
```

A refused crossing says why at WARN (`[warp] crossing of … refused: dimension_too_tall`), and a crossing
Dimensional Sable threw on is logged at ERROR with its stack trace. Dimensional Sable's own
`/sable dimension_set <sub_level> <dimension> [position]` runs its move alone, with no rift and none of
this mod's repairs - useful for telling its behaviour apart from the warp's.

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

The mods this builds against live in `Depends/`. Create, Sable and the Aeronautics bundle ship several
of their libraries as jar-in-jar artifacts; those are unpacked into `Depends/lib/` so javac can see
them. Both directories are on the compile classpath and on the dev runtime classpath.

**`Depends/` is not in the repository** — those jars belong to their authors and are not ours to
redistribute. Before the first build, download these from CurseForge or Modrinth:

```
Depends/
  create-1.21.1-6.0.10.jar
  create-aeronautics-bundled-1.21.1-1.3.0.jar
  sable-neoforge-1.21.1-2.0.3.jar
  geckolib-neoforge-1.21.1-4.8.4.jar
  jei-1.21.1-neoforge-19.44.0.403.jar
```

then copy every jar from each one's `META-INF/jarjar/` folder into `Depends/lib/` (a jar is a zip, so
any archive tool opens it):

| Outer jar | Nested jars to copy into `Depends/lib/` |
| --- | --- |
| `create` | Flywheel, Ponder, Registrate |
| `create-aeronautics-bundled` | Aeronautics, Simulated, Offroad |
| `sable-neoforge` | Sable Rapier, Sable Companion, Veil |

The file names inside `Depends/lib/` do not matter; every `*.jar` there is picked up. Without these
the build fails to compile with hundreds of missing Create and Sable symbols.

[Dimensional Sable](https://modrinth.com/mod/dimensional-sable) is optional and not needed to build: the
mod reaches it by name at runtime. Drop `dimensional_sable-1.0.5.jar` into `Depends/` to have it in
`runClient`, and `DimensionalSableBridgeTest` then also checks that the method this mod calls is still
there.

[Curios](https://www.curseforge.com/minecraft/mc-mods/curios) is optional too, but it is the one
dependency fetched from a Maven repository (`maven.theillusivec4.top`), and only its API jar, at
compile time - Gradle downloads it, so there is nothing to put in `Depends/`. The version is
`curios_version` in `gradle.properties`.

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

The machines' models and textures are an artist's, drawn in Blockbench, and nothing regenerates them; the
scripts left in `tools/` that write block models or the storm's overlay on the Rift Drive say what they write.

---

## Limitations

**Warps into other dimensions are experimental, off by default, and need Dimensional Sable.** It rebuilds
the ship rather than moving it — see [Into another dimension](#into-another-dimension) — so contraptions
on bearings are taken apart for the trip, and the crossing takes a noticeable tick on a very large hull.
The integration has not yet been verified in a running game.

**A sounding of a dimension with a ceiling maps the ceiling:** the survey reads the top of each column,
so a scan of the Nether shows the bedrock roof and sends the ship above it - see
[Sounding another dimension](#sounding-another-dimension). A sounding also cannot
survive a reload — the chunk ticket holding that ground open has gone, so a probe interrupted mid-
sounding comes back idle rather than waiting on chunks nothing is keeping loaded.

**Ropes cannot go through a rift,** even between two parts of one ship - see
[What goes with the ship](#what-goes-with-the-ship-and-what-holds-it-back). **Carried parts are not
clearance-checked:** the launch and arrival checks measure the hull's own plot, so a ship docked alongside
can come out of a warp inside something the hull's landing missed. Both the ties and the carrying are
unverified in a running game.

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
