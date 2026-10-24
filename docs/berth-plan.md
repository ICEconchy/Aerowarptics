# The Berth - plan

> **Status, 2026-10-12.** Phases 0-8 are built and unit-tested; see AGENTS.md, "Current state", for what has
> and has not been seen in a running game. **Not built** from this plan: the Cast Off row on the drive console,
> the "Berths 2/5" figure on the Astrolabe's chart, the Fleet Board's own notice for a fleet through one rift
> (the drive refuses it and the dry run says why), the "Lane fouled" self-test line on the panel (the dry run
> names a fouled slip instead), the Display Link source, the Ponder scene, the advancements and
> `/aerowarptics berth`. Changed from the plan: a berth moors a ship **level**, not at the attitude it flew at
> (a hull a seventh of a degree over was refused a slip built to fit it), and the slip a berth is set down with
> is 24 x 13 x 13.

## Context

Players build airship stations and want to warp *into* them. Today a warp to a Warp Anchor comes out
wherever `SafeArrival.find` finds clear air above the anchor, facing however the ship left; the pilot
then has to fly the last stretch into the dock by hand, and bashes the station doing it.

**The Berth** is a single block that marks out a parking slip belonging to a Warp Anchor. A ship that
warps to that anchor is given a free berth it fits, is turned in the fold to the berth's facing, flies
out of its exit rift straight down the berth's lane, stops centred in the slip, and is then held there:
clamped still and kept loaded. A ship flown in slowly by hand is caught and lined up the same way.

### Decisions the owner made (2026-10-12, binding)

| Question | Answer |
| --- | --- |
| Targeting | **Via the station's anchor.** The pilot sets a course to the anchor as now; the station hands out a berth as the rift opens. Berths are not chart destinations. |
| Attitude | **Turned to the berth's facing.** A warp turns a hull for the first time. |
| Holding | **Clamped still, kept loaded, and a tractor** that catches ships flown in by hand. |
| No room | **Refuse the warp**, at the mooring, with a named reason. |
| Flotillas | **Each ship gets its own berth**; any ship without one refuses the whole fold. Rift set to One is refused at a station. |
| Power | **None.** A passive block. |
| Who | **The anchor's access, narrowed per berth**: Anyone / a network / owner only. |
| Overstay | **Indefinitely**, until the crew or the berth's owner casts off. |

### Decisions left to me (say if any is wrong)

- **A berth lies level and is ground-only.** Facing is a yaw (5° steps); ships arrive horizontally. A
  berth set down in a ship's plot says "needs solid ground" and does nothing. Carrier decks and
  vertical landing pads are follow-ups, not v1.
- **Bow first or stern first** is a per-berth switch: the ship always ends bow along the facing, and the
  switch says which way it travels in (so a dead-end slip can be backed into and flown out of forwards).
  "Bow" is the drive's own heading setting (`shipSpaceBow()`).
- **Best fit wins**: of the free berths open to the pilot that the vessel fits, the smallest; ties go to
  the berth's priority number, then its distance from the anchor.
- **An anchor with at least one enabled berth is a station**, and a station gives out berths only -
  there is no "or land nearby". Fix courses (probe, beacon), plotter exits, gates and free cruises are
  untouched, but an ordinary arrival search now refuses any candidate that overlaps a berth's box.
- **A Rift Ward is asked first.** A rift turned to a rim gets no berth. A scattered arrival
  (Singularity, storms) gets no berth either and lands by the ordinary search; it can fly in and be
  tractored.
- **`clearanceChecks` off still gets a berth** (assignment is not a terrain check - like `SHIP_FASTENED`
  and wards); only the "is the slip and its lane clear" read is skipped.
- **Cast off**: automatic when the ship's own drive starts any sequence or cruise; a **Cast Off** row on
  the drive console; a button on the berth's panel (owner, or anyone aboard the moored ship); a redstone
  pulse on the berth. Crouch breaks a ship free *only while it is being tractored in* (the plotter's
  rule) - never once moored, because people sneak on decks all day.
- **Comparator** (`BerthSignal`, pure): 0 empty, 4 reserved for an inbound warp, 8 drawing a ship in,
  15 moored. Plus a Display Link source.
- **Recipe** (shaped, mid-game, beside the detector): brass sheets, a Rift Casing, a Rift Lens, two
  chains, an electron tube; one per craft. Priced in `RecipeBalanceTest`; a Classic Recipes twin only if
  the ingredients differ there.

---

## Gameplay, as the player meets it

1. Place a **Berth** at the edge of a slip. It links to the nearest Warp Anchor the placer owns within
   `berth.linkReach` (default 128); the panel can pick another. Up to `berth.maxPerAnchor` (16).
2. Use it: a **sidebar panel with the fly-out camera** (the ward's, `PlotterCameraRig`) and the slip
   drawn in the world while it is open (`LightStrokes`, four layers, visible at noon): the box, an arrow
   for the facing, and the **lane** behind it that an arriving hull flies down. Sliders: length, width,
   height, offset from the block (three), facing, priority; switches: enabled, bow/stern first, tractor
   on/off; reserve: Anyone / Network (named) / Owner. Name box. Status lines: Empty / Reserved for
   *ship* / Drawing in / Moored: *ship*, and **"Lane fouled at x y z"** from a once-a-second self-test
   so a builder sees a girder in the way before a ship finds it.
3. A pilot sets a course to the anchor exactly as now. The Astrolabe's row for a station shows
   **Berths 2/5**. On the redstone edge the drive asks the station:
   - a berth is found → the rift is planned into it; the berth shows Reserved;
   - none free → refused `NO_FREE_BERTH`; none big enough → `BERTH_TOO_SMALL`; none open to this pilot
     → `BERTH_FORBIDDEN`; the only candidates have a fouled slip or lane → `BERTH_OBSTRUCTED`. All
     refused before anything moves, unpenalised, traced with the numbers.
4. The ship turns in the fold, comes out of an exit rift standing in the lane, coasts into the slip and
   stops. The berth takes it: **moored** - held motionless and level, kept loaded with nobody aboard.
5. Leaving: start the drive (auto cast-off) or press Cast Off. A cast-off ship still fills the slip
   until it has left the box; the tractor ignores it until then.
6. Flying in by hand: a ship moving slower than `berth.captureSpeed`, that fits, with someone aboard the
   berth is open to (or uncrewed and the berth is Anyone), entering the box is drawn to the mark, turned
   to the facing (whichever end needs least turning, unless the switch forces one) and moored.

---

## Design

### New package `berth/`

| Class | Kind | What |
| --- | --- | --- |
| `Berth` | record | id, name, dimension, block pos, anchor id, owner, box (centre offset, L/W/H), yaw, priority, enabled, sternFirst, tractor, `BerthReserve` + network id, occupant (ship UUID, drive id, state, since), reservation (ship, until). NBT + stream codec, `load` tolerant of missing fields. |
| `BerthBox` | pure | the oriented slip: `frame()`, world corners, `mark(vessel size)` (centre in plan, keel on the floor), `lane(runOut, hullLength)`, `contains`, `overlaps(AABB)`. |
| `BerthFit` | pure | whether a vessel's own box, turned to the arrival attitude, fits L/W/H less `arrival.clearance`; uses `FlightGeometry.extentAlong`. Never a world box (the compass bug). |
| `BerthAssign` | pure | the choice: filters (enabled, free, not reserved, permitted, fits), best fit, priority, distance; returns the berth or the *reason* none (the four refusals), as a table test. Also the many-ships form for a fleet: largest ship first, each to its best fit, all or nothing. |
| `BerthAccess` | pure | `BerthReserve` × owner × network membership → permitted. Defers to `Membership`; Network with no network fails shut (the anchors' rule). No operator exception beyond the anchor's own. |
| `BerthMooring` | pure | the clamp and tractor arithmetic: wanted attitude (`yawTurning`), `steer` (reuses `DockAlign.steer`), lined-up test, capture test, `BerthSignal`. |
| `BerthRegistry` | SavedData | server-wide, on the overworld, as `WardRegistry`: by id, by anchor, in dimension; reservations and occupants live here so an unloaded berth still answers. |
| `Berths` | service | the one place a warp asks and a berth is taken - see below. Ticks from `ServerTickEvent`. |
| `BerthBlock`, `BerthBlockEntity`, `BerthItem` | block | horizontal-facing block with `STATE` (empty/reserved/moored) for its lamp; BE registers its record on load, re-asserts on chunk load, takes its occupant *from* the record (`adopt`), calls `DrawnAboard` nothing (not GeckoLib), refuses `BlockMovementChecks` and plot space. |
| `BerthCommands` | command | `/aerowarptics berth list|castoff|clear` (operator). |

### `Berths` - ask, then commit (the wards' shape)

- `Berths.assign(destination, anchor, airship, bow, who, geometry)` → `Assignment` (berth, arrival
  centre, arrival orientation, approach vector) or a refusal. **Changes nothing.**
- `Berths.reserve(assignment, ship, driveId, etaTicks)` - called in `beginWarp` beside
  `Wards.commit`, after the last `abort`. A reservation is a lease on overworld game time
  (flight length + margin) so an aborted or lost warp frees the slip by itself; `abort()` frees it at once.
- `Berths.moored(...)` - called from `completeWarp`; marks the occupant, starts the clamp, awards the
  advancement, takes the hold.
- `Berths.castOff(ship | berth, why)`; `Berths.mooredAt(ship)` for the console and the trace.
- **Server tick, not the block's** (the gate-passage rule: a block that unloads must not leave a ship
  held or dropped). For every record with an occupant whose ship is present (`Holds.present`): steer
  it to its mark with `Airship.steerVelocity` (parts swing with it) and renew `AttitudeHold`; renew its
  `HoldPurpose.BERTHED` ship hold on a lease. Skips a ship whose drive has a sequence running. For every
  enabled tractor berth whose ground is loaded: once a second look for sub-levels in the box, catch by
  `BerthMooring`. A record whose ground is loaded with no berth block on it is forgotten (`Wards.live`'s
  rule); an occupant whose ship has been gone past its lease is cleared.
- Nothing here loads a chunk. Ships are held only through `Holds.holdShip`.

### The turned arrival (`warp/`, `drive/`)

A flight has always had one heading and one attitude. A berthed one has two of each.

- `WarpFlight`: add `departureOrientation` beside `arrivalOrientation`, and plan the far end on an
  **exit heading** distinct from the entry's. New `WarpFlight.planInto(airship, centre, orientation,
  approach, corridorTicks, emergeTicks)`; `build()` takes both headings and turns `originToCentre` by
  the yaw between the two attitudes (today it assumes they are equal). `attitude()` answers by stage -
  departure up to the crossing, arrival after. Saved/loaded with a default so a flight saved mid-warp
  by an older build reads as untuned.
- `SafeArrival.exactly(airship, centre, orientation)` - the existing `exactly`, turned, measured from
  `vesselShipBounds()`; and the single-candidate proof of slip + lane with the existing
  `arrivalSegments` / `clearance` (no search).
- `RiftDriveBlockEntity`:
  - `beginWarp`: after `planPastWards`, if the rift was **not** turned or scattered and the anchor is a
    station, replace the plan with `planBerth(...)` (assign → `planInto` → proof unless checks are off →
    next-best berth on `BERTH_OBSTRUCTED`). Refusal aborts with the berth's reason. Reserve beside
    `Wards.commit`.
  - `tickFlight`: `AttitudeHold.hold(airship, current.attitude())` (was `arrivalOrientation()` - which
    would now spin the hull round at its mooring). The crossing's `relocate(emergenceOrigin,
    arrivalOrientation, …)` already takes an attitude and `HullJump` already carries the turn to crew
    and clients (the plotter relies on it).
  - `completeWarp` → `Berths.moored`; `abort` → release the reservation; any sequence or cruise start →
    `Berths.castOff`.
  - `dryRun` names the berth it would be given, or the refusal - `/aerowarptics warp dryrun`.
  - `CrossingHandoff` carries the assignment (berth id, arrival attitude); `finishCrossing` turns the
    rebuilt hull to it with a relocate in place, deep in the throat. **Risk:** Dimensional Sable's
    rebuild takes no attitude; if the in-place turn misbehaves in game, cross-dimension warps to a
    station fall back to refusing with `BERTH_OBSTRUCTED`-style wording until fixed. Called out, not hidden.
- `WarpFailure`: append `NO_FREE_BERTH`, `BERTH_TOO_SMALL`, `BERTH_FORBIDDEN`, `BERTH_OBSTRUCTED`
  (appended - ordinals are the wire), all in `penalised()`'s free list.
- `SafeArrival.find`: a candidate whose hull box overlaps an enabled berth's box in that level is
  skipped (cheap box test against the registry, before any block is read).

### Flotillas (`flotilla/`)

- A fold at a station with **Rift: One** is refused at the Fleet Board with a `CouplerNotice`, in the
  panel (never the action bar), and by the flagship's drive with `FLOTILLA_NOT_READY`.
- **A rift each:** the flagship's `beginWarp` asks `BerthAssign` for the whole fleet. It plans its own
  flight with no `SafeArrival.Companion`s; `FleetMuster.flagshipPlanned` gains a berthed form that puts
  each escort's `Release` at its berth's centre and carries the arrival attitude and approach; the
  escort's `beginEscortWarp` uses `planInto` instead of `planTo`. Every berth is reserved at the
  flagship's commit. A shaken escort (Wake Echo) keeps its berth.
- `FoldOrder`/`Release`/the drive's fold NBT grow the attitude and approach; `AWNetwork.VERSION` bumps.

### Holds (`hold/`)

`HoldPurpose.BERTHED` (`holdsShip()` true), owner key = berth id, on a lease renewed by `Berths`' tick,
released on cast-off. `HoldUpkeep`'s "flying" test stays FLIGHT/PLOTTED. `berth.keepMooredLoaded`
(default on) is the switch. `ChunkLoadingChokePointTest` must stay green: no loading call in `berth/`.

### Everything a new block touches (pattern: the Spatial Wave Detector, the Rift Bond, the ward's panel)

- `registry/`: `AWBlocks`, `AWBlockEntities`, `AWItems`, `AWCreativeTab`; `AWConfig` section `berth`
  (enabled, linkReach, maxPerAnchor, max length/width/height, captureSpeed, keepMooredLoaded,
  reservation margin; client: `berthGuide`).
- `network/`: `ServerboundConfigureBerthPacket`, `ClientboundBerthPanelPacket` (carries every server
  figure and a `BerthNotice` for refusals said in the panel), `ServerboundCastOffPacket`; the drive
  console packet gains "moored at"; `WarpQuote`/chart row gains berths free/total. Bounded reads,
  `enqueueWork`, version bump.
- `client/`: `BerthScreen` + `BerthLayout` (pure, 320-wide, in `ScreenLayoutTest`), `BerthGuides` (in
  `AfterShaders.EFFECTS`, three-line preamble), console row, chart row, `FasteningText`-style line.
- Assets, **generated**: `tools/berth_model.py` → texture, block models (three lamp states), blockstate;
  `geo_item_models.py` is not involved (not GeckoLib) - a plain item model. Loot table, pickaxe and
  stone-tool tags, recipe.
- Player-facing: lang (`en_us.json`, every failure/notice/gui key), JEI info page, a Ponder scene
  (`ponder_schematics.py`, `ponder_lang.py`), two handbook pages (named keys), advancements via
  `tools/advancements.py` ("Made Fast": moor at a berth; "Harbour Master": own a station that berths a
  stranger's ship), `AWCriteria`, Display Link source (lambda holder, `AWDisplaySourcesTest`), `Trace.BERTH`.
- Docs: `docs/berth-plan.md` (this plan, in the repo's own voice, as the ward and wellhead have),
  `AGENTS.md` (layout line, script table, invariants below, current state), `README.md`, `CHANGELOG.md`
  under 1.4.0, `docs/mod-description.md`, `docs/text-inventory.md`. The GitBook wiki is drafted and
  **asked about first**, not pushed.

### Invariants to write into AGENTS.md

- A berth is asked through `Berths`, after the wards, and taken only once the flight is certain.
- A flight has two attitudes; hold `flight.attitude()`, never `arrivalOrientation()`, before the crossing.
- A moored ship is held by the server tick from the record, never by the block.
- A berth is not a clearance check; and it is not a `Fastening` (it makes no joint) - the drive casts
  off itself.
- A berth's fit is measured in the vessel's own frame, never a world box.
- A fleet at a station is assigned as one decision and reserved at the flagship's commit.

---

## Phases (each ends green: `./gradlew build`, with `JAVA_HOME` from memory `jdk-path`)

0. **Plan in the repo** - `docs/berth-plan.md`.
1. **Pure core + registry** - `Berth`, `BerthBox`, `BerthFit`, `BerthAssign`, `BerthAccess`,
   `BerthMooring`, `BerthSignal`, `BerthRegistry`. Tests: `BerthFitTest` (sweep sizes × headings, as
   `LargeHullTest`), `BerthAssignTest` (table, every refusal, fleet all-or-nothing), `BerthAccessTest`,
   `BerthBoxTest` (handedness round trip, lane), `BerthRegistryTest` (save/load, reservation lapse).
2. **The block** - block, BE, item, config, generated model, loot, tags, recipe, lang; registers and
   links to an anchor; comparator. `ResourceIntegrityTest`, `LangCoverageTest`, `RecipeBalanceTest`,
   `CapabilityCoverageTest` stay green.
3. **Turned flights** - `WarpFlight` two headings/attitudes, `SafeArrival.exactly(…, orientation)`,
   `planInto`. Tests: `TurnedFlightTest` (exit rift stands in the lane, hull ends on the mark at the
   attitude, untuned plan is bit-for-bit the old one), `HullJumpTest` case for a quarter turn.
4. **Drive wiring** - `planBerth`, reserve/release/moored, failures, dry run, trace, search keeps out
   of berth boxes. `BerthWiringTest` reads the source (asked after wards, reserved after the last
   abort, `attitude()` held, cast-off on every sequence start), in the manner of `WardWiringTest`;
   break each guard once to see it fail.
5. **Mooring** - `Berths` tick: clamp, `BERTHED` hold, tractor, cast-off paths, console row.
   `BerthMooringTest` flies a simulated hull to its mark (as `DockAlignTest`), and holds one against a
   simulated sink.
6. **Panel and guide** - screen, packets, camera, in-world guide, lane self-test, chart row.
   `ScreenLayoutTest`, `RelicTextTest`-style measuring of every string, `AfterShadersTest`.
7. **Flotillas** - fleet assignment, berthed releases, Rift: One refusal. `CouplerTextTest`,
   a `FleetBerthTest`.
8. **Cross-dimension** - handoff + in-place turn.
9. **Teaching** - Ponder, handbook, JEI, advancements, Display Link, docs, AGENTS.md.

## Verification

- **Unit:** the tests above; the whole suite (currently 1679) green; guards broken once each.
- **In game, through the Minecraft MCP** (recipes in memory `in-game-testing-via-mcp`; flat creative
  world, server config under `config/`): build a three-slip station round an anchor by command; warp a
  Creative-drive skiff from each of four compass headings and confirm it ends on the mark, bow along
  the facing, moored and still (`get_entities`/logs with `debug.trace` on); fill the slips and confirm
  `NO_FREE_BERTH` at the mooring; an oversized hull → `BERTH_TOO_SMALL`; a girder in the lane →
  `BERTH_OBSTRUCTED` and the panel's "Lane fouled"; a second fake player against Owner/Network
  reserves; leave the area and confirm an uncrewed moored ship stays loaded, then restart and confirm
  it is still moored; cast off four ways; fly a ship in slowly and watch the tractor; a two-ship
  flotilla into two berths; a crossing into the Nether to a station with Dimensional Sable present.
- **Cannot be judged by tests, said plainly:** how the turn reads from the deck inside the corridor
  wash, whether a clamped deck is comfortable to walk on, the guide at noon and under a shader pack,
  and the model. A screenshot every few seconds is not watching a scene.
- Uncommitted work is large and the repo is public: nothing is committed unless asked, and then by
  pathspec with the diff checked (memory `public-repo-sanitize`).
