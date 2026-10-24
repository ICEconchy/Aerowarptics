# Rift Ward plan

A third relic. The Collapse Cradle makes and the Rift Plotter sends; the **Rift Ward** turns aside. It
is a 3x3x3 piece of forgotten technology that holds a field over a base, and a rift a stranger aims
into that field comes out at its rim instead - with a set piece the whole valley sees.

Written on 2026-10-07 against the working tree (1.3.1 unreleased, 1529 tests). The design was checked
against the code it hangs off and the invariants in AGENTS.md. Line numbers are as of that date and
will drift.

**Status: built, 2026-10-07 - every phase, unit-tested (177 classes, 1588 tests), not yet seen in game.**

Where the build settled something this plan left open, or differs from it:

- **The cue is its own packet,** `ClientboundWardCuePacket`, not a `Stage.DEFLECT` on the warp-effect packet: that
  one describes an aperture, and this describes a track between two places and the machine that drew it.
- **A turned ship's exit is not opened early.** The far aperture normally opens as the corridor begins; it would
  have stood at the rim while the tear was still being dragged there. The transit cue opens it as the hull
  crosses instead. An escort flying its own rift still opens its own early - a rough edge, not fixed.
- **A turned rift is kept clear of every field, not only the one that turned it,** and where fields overlap too
  thickly to turn from each in turn it is walked straight out of them. A test of three overlapping fields found
  the first version leaving ships inside.
- **No "Interference" verdict on the probe.** The scan's picture shows the interference, and whoever threw it is
  told in the action bar. A new verdict meant a new field through the sounding, its packet and its screen.
- **Only the wrench answers to `warpSounds`.** The toll, the break and the field dropping are how a ward's owner
  is told something came, and are not a pilot's to switch off.
- **The scene has no lensing, no parted clouds and no tint in the corridor.** It has the tear, its streak and
  cracks, the dimming (through the storm's own sky), the ring at the break, sparks, the shake and - for those
  aboard - the view rolled over. The rest wants looking at in game before more is laid on it.
- **The panel counts rifts turned** rather than listing the last few.
- **No tank size in the config:** the tank is sixteen buckets, as the plotter's is, and `wardCapacity` is what
  the field runs on. `wardDimRange` and `wardBoomRange` became `wardSpectacleRange` and `wardTollRange`.
- **`/aerowarptics ward remove` on a ward that is not loaded** empties it and switches it off in its record
  rather than loading it to break it; the machine takes that up when it next loads.
- **Not built:** a Ponder scene (the plotter has none either), the `LargeHullTest` sweep (deflection's
  independence of the compass is `WardFieldTest`'s, and the clearance comes from `FlightGeometry`, which
  `LargeHullTest` already sweeps), and the wiki draft, which is outward-facing and was not started.
- **Left alone on purpose:** `/aerowarptics warp dryrun` does not ask the wards, since an operator's diagnostic
  that named them would be the warning the design withholds; and the drive's spin-up preload still claims the
  ground at the place aimed at, so a turned arrival loads its landing during the search instead.

Every item under "Left to the build" was built as proposed.

**Second round, the same day, after the first look in game** (the owner's words: the model should be "a stone
totem in a machine with no roof" and feel like "messing with the fabric of reality, which is pretty taboo"; the
panel should have the plotter's third-person camera, since the radius cannot be seen; nothing showed whether it
was on; and it was hard to test alone). What changed, and what in this document it replaces:

- **Who passes** is now three modes - turn everyone, let named networks through, turn only named networks -
  with the owner passing unless they tick *Turn me too*. The operator and anchor exceptions are gone, and with
  them the "An anchor is an invitation" decision above. A ward names up to eight networks, any on the server.
- **The machine** is a cracked stone totem bound in brass in a roofless ruin, not a weight on a chain. Its pieces
  lift apart when it is on and turn while the field stands. The gimbal arithmetic is gone.
- **The panel** is a sidebar with the camera flown out over the field and the rim drawn on the ground.
- **On or off** is said four ways: lit carvings, turning pieces, a drone, and block light.
- **Wrongness** round a standing ward: motes rising, dust creeping inward, a dark stain over the open top. The
  rim's motes rise now instead of settling.
- 178 test classes, 1605 tests. The model was looked at in an offline render before it was deployed; the rest is
  still unseen in game.

| Phase | What | Effort |
| --- | --- | --- |
| 0 | Groundwork: plotter owners, the appended enums, config | S |
| 1 | The rules, pure: field, access, charge, choreography | M |
| 2 | The machine: structure, registry, model, panel | L |
| 3 | Ship warps: drive, flotilla, beacon | M |
| 4 | Plotter exits and scrambled scans | M |
| 5 | The set piece: sky, relic, corridor, sound | L |
| 6 | Teaching and documents | M |

Each phase leaves the mod consistent. After phase 3 a ward works, silently; phase 5 is what makes it
worth building, and is the one that can only be judged in a running game.

---

## What the owner decided (2026-10-07)

These are settled. Everything under "Left to the build" further down is not.

- **It deflects, it does not refuse.** A warded arrival comes out at the field's rim.
- **The pilot is not told beforehand.** The chart, the console and the plotter's table show nothing; the
  crew learn in the corridor.
- **It turns aside ship warps, Rift Plotter exits and probe scans.** Not gates, not Rift Pearl hops, not
  a free cruise - a hull flown in under its own power is flight, not a rift.
- **An anchor is an invitation.** A pilot bound for an anchor inside the field that they may use is let
  through, whoever owns the ward. This is also what stops a ward being planted over a neighbour's anchor.
- **Landmark scale.** The deflection is a set piece on the Collapse's scale, seen and heard from far off.
- **Three ways to wear one down:** every deflection drains its charge; a Rift Storm thins the field; a
  higher drive tier is thrown less far out.
- **The radius is a slider on the panel,** up to a server cap, and upkeep scales with the area held.
- **A late relic,** crafted like the plotter with a found Relic Armillary.

---

## The rules

### The field

A vertical cylinder about the ward's middle, the full height of the world. A cylinder and not a sphere
because every arrival in this mod is placed horizontally and then given a height (`ArrivalHeight`), as
a scattered exit already is (`ScatterOffset` moves x and z only) - and because a dome can be warped
*over*.

Three figures, kept apart on purpose:

| Figure | What it is |
| --- | --- |
| Set radius | What the slider says. Upkeep is priced from this. |
| Standing radius | Set radius, times `wardStormThinning` while a storm rages in the ward's level. What a plotter and a scan meet. |
| Radius met by a drive | Standing radius, times one minus that tier's `wardPenetration`. A target outside it is not deflected at all. |

So a Singularity aimed at the outer part of a field lands where it aimed, and one aimed at the middle
is thrown only as far as its own, smaller circle. `RiftStorm.ragingOver` is per level, so a storm
thins every ward in the dimension at once.

### Who passes

Asked through `Membership`, never read off a mode (AGENTS.md, "Who may use a destination"). A new
pure `WardAccess.passes`, tested as a table like `NetworkAccess.permits`:

- the ward's owner, an operator, or a member of the ward's network - always;
- anyone, when the course is an **anchor** inside the field and `anchor.isVisibleTo` says they may use it;
- nobody else. No author at all (a drive armed before authors were kept, a plotter set down before
  plotters had owners) is a stranger - it must fail shut.

"Who" is the drive's `initiator`, the plotter's owner (new, phase 0), and the player who pressed Scan.
A beacon's summon is a fix, not an anchor, so a stranger's summon into a field is deflected.

### Where a deflected rift goes

`WardField.deflect`, pure. Along the line from where the ship is to where it aimed, the first point
that line meets the circle, then out by the hull's half-length (from `FlightGeometry`, never
`worldBounds()`) and a margin, so the whole vessel is outside. Three corners the arithmetic must own:

- **The ship starts inside the field** (or in another dimension, where there is no line): out along the
  radius through the target.
- **Fields overlap:** deflect by the nearest centre, ask again of the result, at most a handful of
  times. Members of one ward are still strangers to the next.
- **The rim spot has no safe arrival:** the search must not wander back inside - candidates in the
  field are struck out - and the planner walks round the rim in steps either way. If the whole rim is
  rock, the warp is refused at the mooring with a new `WarpFailure.DESTINATION_WARDED`. That is the one
  time a pilot is told, and it keeps "a refusal happens before anything moves".

It is decided where a scatter is, in `RiftDriveBlockEntity.beginWarp` (about line 2065), on the
target after any scatter - and again on the fallback when a scattered spot proves unsafe, or a
Singularity scatters its way past a ward. The flight is planned to the rim from the start. **Nothing
changes mid-corridor on the server**; the surprise is the client's to stage.

A ward is not a clearance check. A drive with `clearanceChecks` off is deflected like any other, on
purpose, as `SHIP_FASTENED` still refuses - AGENTS.md should say so beside that rule.

### Charge

The ward banks Rift Essence as a kept charge, as the plotter does: drawn from its tank while it turns
at `wardMinimumSpeed` or faster, at a rate scaled by speed.

- **Upkeep** is per second, proportional to the set radius squared.
- **A deflection** costs a base plus a figure per unit of `WarpValidator.effectiveMass` - the whole
  vessel's, and a flotilla's summed. A plotter's exit costs a base plus a figure per block of its
  radius, and again each time a held lane is renewed. A scrambled scan costs a little.
- **It cannot pay:** the rift goes where it aimed. The field then **drops** and stays down until the
  charge is back above `wardRecoverFraction` of the tank, so a ward is not flickered on and off one
  millibucket at a time.

An unloaded ward still stands. Its record in the registry carries its charge and the game time it was
last counted, and upkeep since then is taken off when anything asks (`WardCharge`, pure, overworld
game time as `HoldLedger` uses). It draws nothing while unloaded - nothing is turning that anybody
can vouch for - so a base nobody is at is protected for as long as its banked charge lasts, and no
ward ever force-loads itself. `ChunkLoadingChokePointTest` stays as it is.

### Scans

A Rift Probe's sounding, and the plotter's survey table, read ground through `DestinationSurvey` and
`PlotterSurvey`. Columns inside a standing field, for a player who does not pass, come back as
seeded noise, and the probe's verdict gains "Interference". An Astrolabe's preview of an anchor the
player may use is left alone - the anchor's rule again.

This does give a ward away to anyone who scouts first. That is deliberate: the surprise is for the
pilot who did not look.

---

## The machine

A new `ward/` package, the plotter's sibling: one master, twenty-six parts, placed whole from one
item and never moved (`WardStructure` borrows `CradleStructure`). The floor is eight `RelicBaseBlock`s
(a `RiftWardBaseBlock` beside the cradle's and plotter's), so pipes and funnels can join it, and it is
turned through `RelicFloor` / `RelicFeed` / `RelicMaster` - shafts only in the middle of each side,
none passed on, the master in no network. `CradleStructureTest`'s count of shafts is extended to it.

**The look.** The cradle is rings and arms; the plotter a dish and a ball. The ward is a **weight on a
chain**: a great bronze plumb-bob hung in a gimbal under a verdigris crown, on the same rift-stone
plinth. While the field is whole it hangs dead still, however hard the machine is turned - which is
what reads as old and certain. Posed from code (`WardWorks`, pure), never keyframed, as the cradle is.
`tools/rift_ward_model.py` imports the cradle's palette and painters; AGENTS.md's table gains a row.

**The idle field shows almost nothing.** A few slow motes settling at the rim, in the carvings' own
colours, seen only from close by. No dome, no ring, no outline, no ghost through terrain
(`relic effects feel ancient`, 2026-10-06). The one bright thing is an instruction, not the relic's
own manner: while the panel is open, the rim is drawn for whoever has it open, with `LightStrokes`, so
a radius can be set by eye.

**The panel.** Radius slider, charge and tank, upkeep per minute, a network button (the gate dial's),
an on/off switch, and the last few deflections. No slots. It must fit 320 pixels (`AWLayouts`,
`ScreenLayoutTest`), and every string goes through `RelicTextTest`. Refusals ride the panel packet,
because the panel stays open.

**The registry.** `WardRegistry`, world data like `WarpAnchorRegistry`: id, dimension, position, set
radius, owner, network id, on/off, charge and when it was counted. The block re-asserts its record
when its chunk loads, so it knows its network **by id**, and `WarpNetworks.detach` must tell loaded
wards and leave unloaded ones to `NetworkRules.mayStay`. Miss that and a ward taken off a network puts
itself back.

**What it tells the world.** A comparator reads its charge; it gives a redstone pulse on a
deflection; a Display Link source reads radius, charge and the last deflection (held as a lambda, not
the holder). `CapabilityCoverageTest` and the part block's pass-through cover the rest.

**The recipe.** Mechanical crafting, proposed: Relic Armillary, 2 Folded Warp Crystal, 2 Spatial
Stabiliser, 4 Warp Glass, Rift Core, Precision Mechanism, 4 Rift Casing. `RecipeBalanceTest` prices it
against the plotter; `ProgressionActTest` is unaffected.

---

## The set piece

Timing is one pure class, `WardChoreography`, read by the server (which sends the cues and decides
the ranges) and by every client that draws - the Collapse's rule. The server sends one new cue,
`Stage.DEFLECT` (appended to `ClientboundWarpEffectPacket.Stage`, never inserted), carrying where the
rift aimed, where it went, and the ward's middle. It goes out as the corridor begins, to players near
the field and, by name, to the crew - who are thousands of blocks away, as the transit cue's rule
already has it.

Six beats, on the clock of the ship's own corridor so the last one meets the hull coming out:

| Beat | Over the field | At the relic | Aboard |
| --- | --- | --- | --- |
| **Reach** | The sky dimples over the place aimed at; a tear begins, in the ship's own livery. | The weight stirs on its chain. | Nothing yet. |
| **Catch** | The tear stops half open. Light bends across the field from that point (`GravityLens.bend`, with `bentLight`'s fallback under shader packs and Fabulous). Daylight dims as the Collapse dims it. | The weight swings hard toward the tear and holds there, chain taut. One toll, low, heard at landmark range. | The starlines bend sideways; the corridor takes on verdigris; the screen shakes; the toll, muffled. |
| **Skid** | The tear is dragged down the sky to the rim, shedding shards and a long streak. Clouds part along its track. | The weight follows it round, slowly. | The far aperture ahead slides off the bow. |
| **Break** | At the rim it bursts open - the boom, a ring of pushed air and dust. | The weight is let go and swings free. | A lurch, and daylight. |
| **Emerge** | The hull comes out of it: the ordinary `WARP_EXIT`, at the rim. | | |
| **Settle** | The dimming lifts. | The swing dies away over a long while; the glow is lower by what was spent. | |

Two more, shorter:

- **Giving way.** A ward that cannot pay shows the Reach, the weight swings - and the chain slackens.
  A falling tone, the motes at the rim drop, and the rift opens where it aimed.
- **A plotter's exit** gets Catch, Skid and Break at local scale; a lane held open keeps the weight
  leaning toward it.

**Rules it keeps.** No camera is taken. Nothing is pushed, pulled or hurt and no block changes - the
ring of air is drawn, not felt (the Collapse has forces; this does not need them). The hull's
attitude and velocity are untouched, so `AttitudeHold` and `HullJump` have nothing new to answer for.
Every new sound is named in `WarpSoundSwitch`; the shake and the bend honour `warpFov` and the
existing shake setting; a client switch `wardSpectacle` turns the sky off. The sky pieces hang in
`SkyFrame` and follow the storm's draw order, and under a shader pack fall back as the storm's do.
A curve that jumps at a beat is a piece that teleports - `WardChoreographyTest` walks every join, a
hundred samples a tick.

The arrival is still announced to Spatial Wave Detectors, from the rim, by the path that already does.

---

## Phases

### 0. Groundwork

- `RiftPlotterBlockEntity` gains an owner (its placer), saved and kept as a gate's is. Old plotters
  load with none.
- Append `WarpFailure.DESTINATION_WARDED`, `PlotFailure` nothing (a plotter is never refused by a
  ward), `Stage.DEFLECT`. Bump `AWNetwork.VERSION`.
- `AWConfig`, server: `wardEnabled`, `wardMaxRadius`, `wardMinimumSpeed`, `wardStressImpact`,
  `wardTank`, `wardDrawRate`, `wardUpkeep`, `wardDeflectBase`, `wardDeflectPerMass`,
  `wardPlotterBase`, `wardPlotterPerRadius`, `wardScanCost`, `wardRecoverFraction`,
  `wardStormThinning`, `wardPenetration` (one per tier), `wardDeflectsPlotters`, `wardScramblesScans`,
  `wardDimRange`, `wardBoomRange`. Client: `wardSpectacle`, `wardMotes`. Numbers are in "Left to the
  build".

### 1. The rules, pure

`WardField` (the three radii, `deflect`, overlap, the rim walk), `WardAccess`, `WardCharge` (upkeep
over elapsed time, the price of each kind of deflection, drop and recovery), `WardChoreography`,
`WardWorks`. No Minecraft in any of them.

Tests: `WardFieldTest` (a deflected point is always outside the radius it met, by the hull's
half-length; the same answer whichever compass heading the ship came from; inside-start and
cross-dimension; two and three overlapping fields end outside all of them), `WardAccessTest` (the
table, and that no author fails shut), `WardChargeTest` (a ward counted once after an hour equals one
counted every tick; never below nought; recovery has a gap), `WardChoreographyTest`, `WardWorksTest`.

### 2. The machine

Blocks, item, block entity, registry, capabilities, the model script, renderer (`DrawnAboard.redraw`
and `offScreen`, and `getRenderBoundingBox` on the renderer), the panel and its packets, the network
hook-up, recipe, lang.

Tests: `WardStructureTest`, `WardRigTest` (the model's pivots against `WardWorks`),
`WardRegistryTest` (a record survives save and load; a ward re-asserting itself does not undo a
detach), the existing coverage tests (`LangCoverageTest`, `ResourceIntegrityTest`,
`CapabilityCoverageTest`, `RenderBoundsTest`, `DrawnAboardTest`, `ScreenLayoutTest`, `RelicTextTest`,
`NetworkTextTest`) extended, each guard broken once to see it fail.

### 3. Ship warps

- `beginWarp`: after the scatter, ask `Wards.turnAside(level, target, from, initiator, course, tier,
  mass)`; plan to what it answers; pay only once the plan has a flight; trace the lot (which ward,
  which radius, what it cost, or why it passed).
- `SafeArrival.find` takes a set of fields no candidate may land in.
- **Flotillas.** The flagship's target is deflected with the *formation's* reach as its half-length, so
  every slot is outside; escorts still go through `WarpFlight.planTo` and never ask for themselves.
  With Rift set to One, `FleetPassage` is planned on the deflected target. One `DEFLECT` cue per fold
  (`FoldLog.firstTo`).
- **Beacon.** A summon is a fix; nothing special, but the holder is sent the cue too.
- **Cross-dimension.** Asked in the destination's level, radial deflection. Nothing remembered by ship
  UUID, so `CrossingHandoff` carries nothing new.

Tests: `WardWiringTest` reads off the source that both plan calls in `beginWarp` go through the ward
and that a paid deflection follows a planned flight; `LargeHullTest` gains a sweep of deflected long
hulls across headings.

### 4. Plotter exits and scans

- `RiftPlotterBlockEntity.exitFrame()` is asked through the ward at preflight and again at the far
  end, where the price is already asked again. The exit keeps its facing. The table's hologram goes
  on showing the exit where it was plotted.
- A held lane pays the ward at each renewal; a ward that drops lets the *next* tear through, never
  moves a standing exit.
- `DestinationSurvey` and `PlotterSurvey` take a mask of columns to scramble; `ProbeVerdict` gains
  Interference.

Tests: `PlotGeometryTest` (a deflected exit is still built from a yaw and a pitch, so nothing comes
out rolled), `WardScanTest` (a member's survey is untouched; a stranger's differs only inside the
field; the noise is the same on a second scan).

### 5. The set piece

`WardSpectacle` (sky, on `SkyFrame`), the relic's poses and glow layer, the corridor's bend in
`HyperspaceField` / `WarpCorridorOverlay`, `WarpScreenShake`, sounds, the rim motes (`WardMotes`,
after `CradleLinks`).

Tests cover timing, vertex formats (`RiftVertexFormatTest`), pass order (`RiftPassOrderTest`, if the
lens is used), sound names. **How it looks cannot be tested** - not the dimming, the skid against
real clouds, the bend aboard, nor whether the idle motes are quiet enough. This phase is finished
when it has been watched from the ground and from a deck.

### 6. Teaching and documents

A handbook chapter (`GuideBook`, even pages), a Ponder scene, two advancements (deflecting your first
rift; being deflected), a JEI page, CHANGELOG, README's design record, `docs/mod-description.md`,
AGENTS.md (layout row, generated-resources row, and the invariants below), and a wiki draft to be
shown before it is sent.

---

## Invariants this adds to AGENTS.md

- A ward is asked through `Wards`, by everything that brings a rift out - a new kind of warp that
  plans its own arrival and does not ask lands strangers in the middle of a base.
- Who passes is `WardAccess`, through `Membership`; no author fails shut.
- A deflection is planned before anything moves and paid for only once it is planned.
- The pilot's screens never show a ward. A chart that greys a warded anchor has told them.
- The relic's idle field is a force, not a display.
- No ward loads a chunk; an unloaded one lives on its banked charge.

---

## Risks

- **Griefing by field.** Anchors are honoured, but a ward over somebody else's land still turns
  aside their beacon summons and probe fixes unless they are on its network. The server cap on radius
  is the main defence; `/aerowarptics ward list|remove` for operators is in phase 2. A spacing rule
  (no new ward whose field would cover another owner's) is cheap to add if servers ask.
- **A rim with nowhere to land** - a base in a canyon. The rim walk and the one refusal cover it, but
  it is the path most likely to surprise, and it reveals the ward.
- **Formations.** Deflecting a fleet by its whole reach can put the flagship a long way out. Unit
  tests hold the geometry; whether it feels fair is a playtest.
- **A long corridor** (`warpTimeScaling`). The beats are fitted to `corridorTicks()`, never
  `tier.warpTicks()`, with a floor so a short hop still gets the Catch and the Break.
- **Everything else this year is unseen in game.** The ward stands on the plotter, networks and the
  relic floor, none of which has been confirmed running. Phase 2 onward wants those looked at first.

---

## Left to the build

Proposed, not decided - say if any should be otherwise.

1. **The bob on a chain** as the ward's mechanism, and a toll as its voice.
2. **No forces in the set piece** - drawn air only, unlike the Collapse.
3. **An unloaded ward runs on banked charge** and draws nothing.
4. **The plotter's survey table is scrambled too,** as following from "probe scans".
5. **Operators pass every ward,** as they do every anchor.
6. **Numbers,** all config: radius 16 to 96 (cap 256); tank 16,000 mB; upkeep 1 mB a second at radius
   32, by area; deflection 500 mB plus mass; a plotter's exit 250 plus 40 a block of radius; storm
   thinning 0.6; penetration 0 / 0.15 / 0.3 / 0.5 for Mk I to the Singularity, 1 for Creative.
7. **The rim drawn while the panel is open** - the one bright guide. Worth asking how the cradle's
   motes read in game before the ward's are built on them.
