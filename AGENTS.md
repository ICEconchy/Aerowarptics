# AeroWarptics — agent context

Context for any AI agent working in this repository. Read this before changing anything.

Claude Code reads `CLAUDE.md`; this file is the vendor-neutral copy and `CLAUDE.md` points at it.
Keep them from diverging by editing this file only.

---

## What this is

A **Create: Aeronautics addon** that lets an airship fold space. A Rift Drive built into a vessel,
spun by Create's rotational force, flies the whole ship — hull, machinery, cargo, crew — through a
temporary rift to a Warp Anchor somewhere else.

| | |
| --- | --- |
| Mod id | `aerowarptics` |
| Version | `1.4.0` (unreleased) |
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.233 |
| Java | 21 |
| Package root | `uk.co.iceconchy.aerowarptics` |
| Hard deps | Create 6.0.10, Create: Aeronautics 1.3.0 (Sable 2.x + Simulated 1.3.0), GeckoLib 4.8.0 |
| Soft deps | JEI 19.x (client only); Dimensional Sable 1.0.5+ (server only, for cross-dimension warps); Curios 9.x (Rift Goggles in a head slot) |

---

## The one thing to understand first

**Create: Aeronautics has no ship object.** An assembled airship is a **Sable `SubLevel`** — an
independent plot of blocks living in reserved chunks of the parent `ServerLevel`, driven by Sable's
physics pipeline. Aeronautics supplies balloons and propellers on top; Simulated supplies the
assembler.

Everything in this mod follows from that:

- A machine never scans the world for "its ship". Sable calls `sable$tick(ServerSubLevel)` on it once
  per tick **because** it is part of an airship, and that call is the answer.
- Ship space and world space are different frames. `Pose3dc.transformPosition` /
  `transformPositionInverse` convert. Anything positional must say which frame it is in.
- A block aboard a ship has coordinates **inside the plot grid** — huge, meaningless world numbers.
  A log line reading `(20481023, 123, 20489219)` is a plot coordinate, not corruption.
- Land vehicles are sub-levels too (a wheel mount is a `BlockEntitySubLevelActor`), which is why Rift
  Gates carry cars using the same `Airship.relocate` a warp uses.
- A gate's own controller can be aboard a hull, not just something crossing one. `RiftGateBlockEntity`
  crosses the plot/world boundary explicitly wherever it touches a position or a direction - see
  `worldCatchment`, `toLocalPoint`/`toWorldPoint`, `toLocalDirection`/`toWorldDirection` and `worldYaw`.
  Miss one of those and the gate compiles and looks fine on the ground, then silently never notices
  anything once it is moved onto a ship - which is exactly the bug that shipped before this existed.

`Airship` (in `airship/`) is this mod's wrapper over a `ServerSubLevel`. Prefer it to touching Sable
directly.

---

## Layout

```
uk/co/iceconchy/aerowarptics/
  AeroWarptics.java   mod entry point
  AWConfig.java       every tunable; server + client specs
  airship/            Airship wrapper over Sable, per-ship persistent warp data, what is one vessel
                      (VesselChain, pure), and what ties a ship down (Fastening is the pure rule,
                      Fastenings finds them)
  drive/              Rift Drive block entity, tiers, state machine  ← largest class here
                      plus Drive Extenders: DriveStructure (pure rules), DriveAssembly (level side),
                      and DriveSignal (pure), what a comparator reads
  warp/               flight planning, cost, validation, arrival search, crew handling, and CorridorTime
                      (pure), how long a warp lasts
  anchor/             Warp Anchors and the server-wide SavedData registry
  warpnet/            networks: named groups of players who share anchors and gates. The registry is world
                      data; NetworkAccess/NetworkRules/NetworkFilter are pure, NetworkOrders is every order and
                      who may give it, WarpNetworks follows a change through to the machines, and the
                      /aerowarptics network commands. (`network/` was taken: it is the packets.)
  hold/               everything that keeps chunks or ships loaded - Holds is the only code that may -
                      the lease ledger, the upkeep that lets go of what nobody renews, and /aerowarptics holds
  astrolabe/          3x3 chart table, terrain survey, vanilla-matching map palette
  probe/              Rift Probe: scans unvisited ground, here or in another dimension the server lists
                      (ProbeDimensions), and produces a "fix". ProbeAim (pure) is where a sounding lands
  gate/               Rift Gates: free-form ring portals, crossing detection, and the passage a player
                      goes through one as - GatePassages is the server's ledger of who is in one;
                      GatePassage (its timing), PassageClock, PassageFrame (what a style's shader is
                      handed) and GatePassageStyle (the list of styles) are pure
  fissure/            Rift Fissures, the goggles that reveal them, and the drain policy
  flotilla/           Rift Couplers: flotillas, the muster, and the fold several ships go through
                      together. The registry is world data; Formation/Readiness/FlotillaRules are pure
  guide/              the Navigator's Handbook: what it says, and the item that opens it
  siphon/             Spatial Siphon: one shared draught per warp (SiphonPool), rift rain (RainLedger)
  rifting/            the crafting layer's fan process (RiftingType, RiftingRecipe) and the Rift Brazier
  fold/               FoldingRecipe (a Create processing recipe with an essence ingredient, run by the Collapse
                      Cradle) and the Ship's Compass, bound at a drive
  collapse/           Collapse Cradle - a 3x3x3 master and its 26 parts (CradleStructure, pure), posed by CradleWorks
                      (pure) - CollapseRecipe, CollapseRules/Instability/Ritual/Choreography (pure), CradleFold
                      (pure), CollapseFailure, CradleRing (pure: which depots and stabilisers it counts, and the
                      motes drawn in from them), and its panel: CollapseCradleMenu and the CradlePanel it sends
  plotter/            Rift Plotter - the cradle's 3x3x3 sibling (PlotterStructure borrows CradleStructure) that tears a
                      rift beside itself and opens the far end round a typed target, for shipping or war. Plot, Firing,
                      PlotGeometry, PlotCost, PlotRules, DockAlign and PlotterWorks are pure; PlotterSurvey(s) build and
                      load the table's 3D model
  ward/               Rift Ward - the third relic (WardStructure borrows CradleStructure): holds a field and turns a
                      stranger's rift aside to its rim. Wards is the one place a rift asks and a ward is paid; the
                      registry is world data; WardField, WardAccess, WardCharge, WardRules, WardChoreography and
                      WardWorks are pure; WardScans scrambles a stranger's survey; /aerowarptics ward
  wellhead/           Rift Wellhead - a frame of casing and glass built round a found fissure and formed by a Rift
                      Collar, which digests fed blocks into essence and melts down if it is not kept steady.
                      WellheadStructure, WellheadReactor, WellheadSignal, CraterPlan, MeltdownChoreography and ScarZone
                      are pure; WellheadAssembly forms and unforms; AgitatingRecipe is what it digests; Meltdowns and
                      ScarZones are each level's world data and tick with it. See docs/rift-wellhead-plan.md
  sunderer/           Rift Sunderer - a single-use heart for a ring of Rift Gate Frame that dials nowhere and tears a
                      conjured fissure where the aperture should have been, sometimes after a hand reaches through.
                      SundererRing, SundererSequence, SundererChoreography, CrackWeb and RiftHandPose are pure;
                      SundererSurvey is every refusal; SundererBreach is the one place a fissure is made. See
                      docs/rift-sunderer-plan.md
  pack/               the Classic Recipes data pack's finder (PackSource.FEATURE, off unless turned on)
  pearl/              the Rift Pearl and its thrown entity, which chorus-hops what it hits (rifting/RiftHop)
  stabiliser/         Spatial Stabiliser, a drive module that cushions a hull's motion; the maths is pure
  berth/              The Berth: a bollard that marks out a slip at a Warp Anchor's station. Berths is the one place
                      a warp asks for a slip and one is taken; BerthMoorings holds every moored ship from the
                      server's tick; the registry is world data; Berth, BerthBox, BerthFit, BerthAssign, BerthAccess,
                      BerthMooring and BerthRules are pure. See docs/berth-plan.md
  bond/               Rift Bond: a block on each loose section of a ship, tuned alike, that makes them one vessel
                      for a warp and nothing more. BondLedger (pure) is who shares a bond; RiftBonds is what
                      Airship asks
  cooling/            Cooling Vent, a kinetic drive module that shortens the cooldown after a warp, and the ice
                      that does a little of the same. DriveCooling (the arithmetic) and VentPlume (the steam) are pure
  detector/           Spatial Wave Detector: redstone pulse when something arrives through a rift nearby
  weather/            Rift Storms: the clock, what they do to a drive, /weather rift_storm, the forecast
                      (StormForecast, pure) and the Stormglass that reads it and calls a storm
  mixin/              client mixins - only the Rift Storm's sky, clouds, daylight and rain level
  network/            all packets (see conventions below)
  registry/           blocks, items, block entities, capabilities, creative tab
  advancement/        custom criteria triggers
  compat/             Ponder scenes, the JEI plugin, the Display Link sources, the method-handle
                      bridge to Dimensional Sable (the only code that touches it), and the probes that
                      find what Simulated's machines tie a ship to (the only code that names Simulated),
                      and compat/curios (the only code that names Curios, behind CuriosBridge)
  client/             renderers, effects and the screens - the Collapse Cradle's panel the only one with slots, the Rift
                      Plotter's table the only one drawn in three dimensions (HologramMesh, HologramPick, OrbitCamera are pure)
  command/            /aerowarptics warp dryrun|clearance, /aerowarptics trace, and wiring for holds
  trace/              the diagnostic trace: Trace (server), ClientTrace, the session header
  util/               AWLang
```

---

## Commands

**`JAVA_HOME` is not set in this environment.** Every Gradle invocation needs it:

```bash
export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2" && ./gradlew build
```

Without it `./gradlew` prints a JAVA_HOME error and **still exits 0** — do not read a bare exit code
as success. Check the actual output.

| Task | Command |
| --- | --- |
| Build, test, deploy | `./gradlew build` |
| Tests only | `./gradlew test` |
| One test class | `./gradlew test --tests "uk.co.iceconchy.aerowarptics.ScreenLayoutTest"` |
| From scratch | `./gradlew clean build` |

`build` is finalized by `deployToInstance`, which copies the jar into the PrismLauncher instance
named by `deploy_dir` and deletes older `aerowarptics-*.jar` files there. It
fails loudly rather than fatally if the game is running and holding a lock.

**Dependencies are not on Maven.** They live in `Depends/` as jars, with Create's and Aeronautics'
jar-in-jar libraries unpacked into `Depends/lib/`. Both are on the compile and dev-runtime classpath.
`Depends/` is gitignored; the README's "Building" section says what goes in it.
Do not try to resolve them from a repository. The one exception is Curios: its API jar is
`compileOnly` from `maven.theillusivec4.top` (scoped to its group), version `curios_version`.

---

## Environment

- **Windows.** The shell is Git Bash (POSIX) via the Bash tool, and PowerShell is also available.
  They take different syntax — do not mix them.
- **Do not hand the user `grep` commands.** They have said so explicitly. Use PowerShell
  `Select-String` in anything they are meant to run themselves. Your own tool calls are unaffected.
- **Long heredocs fail.** `<<'PY'` blocks past a few dozen lines error with "unexpected EOF". Write
  the script to the scratchpad and execute the file.
- Prefer absolute paths in Bash; `cd` persists between calls and has caused broken relative paths.

---

## Generated resources — do not hand-edit

The resource sets below are generated by scripts in `tools/`. Editing the output directly will be silently
overwritten, and the tests will catch the drift but only after you have wasted the work.

| Script | Generates |
| --- | --- |
| `python tools/ponder_schematics.py` | `assets/aerowarptics/ponder/*.nbt` |
| `python tools/ponder_lang.py` | the `aerowarptics.ponder.*` lang entries |
| `python tools/advancements.py` | `data/aerowarptics/advancement/*.json` + their lang entries |
| `python tools/geo_item_models.py` | every block's `models/item/*.json` |
| `python tools/rift_scar_structure.py` | `data/aerowarptics/structure/rift_scar/*.nbt`, each with its cache barrel |
| `python tools/rift_drive_void.py` | the two sheets a Rift Storm lays over the Rift Drive, `rift_drive_void.png` and `rift_drive_void_rim.png`, read off the artist's drive model |
| `python tools/cooling_vent_model.py` | the Cooling Vent's block model and blockstate (its textures are the artist's, drawn to the model's numbers) |
| `python tools/berth_model.py` | the Berth's four block models (one a lamp state) and its blockstate |
| `python tools/wave_detector_model.py` | the Spatial Wave Detector's two block models and its blockstate |

Change the script, re-run it, re-run the tests.

### The artist's models and textures

**Every machine's model and texture is now an artist's**, drawn in Blockbench and delivered in two drops: the
Rift Drive, Warp Anchor, Rift Chute and Rift Brazier on 2026-10-14, and everything else on 2026-10-21 - the
Astrolabe (one model per table size: `astrolabe_1x`, `astrolabe_2x`, `astrolabe`), the Rift Coupler, Rift
Modulator, Spatial Siphon, the three relics (Collapse Cradle, Rift Plotter, Rift Ward, each with its glow mask),
the Rift Probe and Spatial Stabiliser (now shaped block models, their hit boxes box for box the models), the
Berth, Cooling Vent, Drive Extender, Rift Bond, Spatial Wave Detector, the Rift Collar's and Wellhead port's own
faces, the gate's frame and end faces, Scarred Stone, Warp Glass, the Rift Portal's three strips, both particles,
and the crafting layer's last icons. Edit them in Blockbench and drop them in; nothing regenerates them. The
scripts that once drew them were deleted on the day each was replaced, and the three that survive write only
block models and blockstates.

Per-face sheets reuse texels on purpose, so `geoUvBoxesFitAndDoNotOverlap` holds their faces to the sheet but not
to each other; the anchor's ring is exempt even from that (`OFF_SHEET_BY_THE_ARTIST`). The code reads the
artist's bone names, and each machine's rig test holds the model to the arithmetic that poses it:

- **Drive** - the rift is `cube`, the dial `needle` (`RiftDriveRenderer`). `DriveClearanceTest` holds the rift, at
  the largest any clip draws it and turned any way, inside the ring - by about a tenth of a pixel. After changing
  the drive's model, re-run `rift_drive_void.py`.
- **Anchor** - the status light is `bulb_lit`. **Coupler** and **Modulator** - the tinted crystal is `gem`.
  **Siphon** - the fill is `essence`, pivoted at its foot as before.
- **Collapse Cradle** - posed by `CradleWorks`: `sun`, `piston_0..3`, the three rings (turning about
  `CradleWorks.CENTRE`, y=31) and the `lock` ring on the roof. The artist's cradle has no arms, iris or face
  gears, so their arithmetic and tests went with the old model; the core now rises from over the heart on its own.
  `CradleRigTest` measures each ring off the model and holds it, turned any way, clear of the ring round it.
- **Rift Plotter** - built to `PlotGeometry`'s lens (y=30) and focus (8px along the beam). The rings stand on
  their edge inside the `ring` bone; `ring_middle` (carrying `ring_inner`) spins about the beam and `ball` tumbles.
  Their pivots were moved onto the beam (`ring_middle` to (0.1, 36, -5.9), `ball` to (0.09, 28, -5.93)) - neither
  bone has a turn of its own, so nothing moves at rest; a re-export from the artist needs the same two numbers,
  and `PlotterRigTest` says so if it does not get them.
- **Rift Ward** - `totem_2..4` lift, turn and drag as before; `ring_outer` and `ring_middle` turn with the field,
  against each other. The core is at 26px, `WardStructure.CORE_HEIGHT`.
- **Astrolabe** - `AstrolabeModel` picks the model by `size()`; a formed cell draws the particle-only
  `astrolabe_formed`, and a lone cell the artist's one-block instrument (`models/block/astrolabe.json`).
  A one-block table's map floats at `AstrolabeProjections.SINGLE_PROJECTION_HEIGHT` (21/16), a pixel over
  its compass needle; the larger tables keep theirs at a block.

The files are Bedrock geometry 1.21 rather than GeckoLib's 1.12.0, so GeckoLib logs "Unsupported geometry json
version" for those at startup and ignores their rotated face UVs (`uv_rotation`). Nothing seen in game has looked
wrong for it; a re-export as a GeckoLib model without rotated UVs would silence it. The Coupler's idle clip had a
track for a `funnel` the coupler does not have (copied from the siphon's); it was taken out.

### Connected textures

The Rift Casing, Warp Glass, Rift Gate frame and a formed Wellhead's casing join up with the blocks beside them,
through the mod's own model loader, `aerowarptics:connected` (`client/model/ConnectedGeometry`,
`ConnectedBakedModel`, `ConnectedLayout`) - no CTM mod needed. The tiles are the artist's, in OptiFine's layouts,
one file per tile under `textures/block/ctm/<set>/`: 47 for the gate frame (`"method": "full"`), 5 for the others
(`"method": "compact"`, a face drawn as four quarters). A model's `connect_with` lists what it joins - Warp Glass
joins a formed Wellhead's glass, and the Wellhead's casing joins only itself. `ConnectedLayoutTest` reads the
shipped tiles back to hold them to the order `ConnectedLayout.TILES` assumes. A new set is a folder of tiles and a
model with the loader; nothing else.

The flat item icons in `textures/item/` (and the Rift Beacon's item model) are the other way round:
hand-drawn art, with no script behind them - the Rift Pearl, Warp Crystal, Warp Shard and Warp Dust included
(redrawn 2026-09-30), and the Folded Warp Crystal, Scar Heart, Pearlescent Warp Crystal and the Stormglasses
and their readings (drawn 2026-10-06, with the Rift Core, Rift Beacon, Rift Goggles and Stabiliser Ring touched
up the same day; the Folded Warp Crystal, Scar Chart, Rift Essence Bucket and Singularity Core redrawn 2026-10-14,
the Folded Warp Crystal now a still 16x16). The Scar Heart, Rift Core and every Stormglass but the still reading are
animated strips with a `.mcmeta` beside them, and the Stormglasses are 32x32 a frame where everything else is
16x16 - both are the art, not something to tidy. The Ship's Compass is hand-drawn too (2026-10-08): thirty-two
frames, `ship_compass_00` to `_31`, one a needle position and numbered as vanilla numbers its own compass, since
the angle is vanilla's `CompassItemPropertyFunction`. It was vanilla's dial under a violet badge; the badge is
gone, and `ResourceIntegrityTest` holds each frame's model to its own frame. The rest of the crafting layer's
icons (Opened Rift Lens, the Incomplete Rift Core, the Relic Armillary, the Rift Fissure) are the artist's too
(2026-10-21). The Rift Sunderer's front, `rift_sunderer_front.png` (2026-10-16), is still a placeholder: the old gate
frame's sheet with a cracked black heart, so it no longer matches the artist's frame round it. The Rift Casing's
texture and Rift Essence's still and flowing strips are the artist's (2026-10-14). Scripts once drew the handbook, goggles, beacon and pearl icons; they
were deleted when the art replaced them, so nothing can paint over it. No script in `tools/` writes any of
these icons, and none should start to. Edit those files directly.

---

## Testing

207 test classes, **1856 tests, all passing** (one skipped unless Dimensional Sable's jar is in `Depends/`, and `PassageShaderTest`'s seven where no OpenGL context can be made). The suite is the main safety net and is unusually load-
bearing here, because most failure modes in this mod are *silent* — a panel four pixels off, a
translation key that renders raw, a recipe that quietly asks for four complete drives.

Conventions worth preserving:

- **Logic classes are kept free of Minecraft so they can be tested directly.** `AWLayout`,
  `AWLayouts`, `AWAnim`, `AWDraw`, `AWBookStyle`, `GuideDiagrams`, `GuideBook` and `DisplayReadout`
  contain no `GuiGraphics`, no `Font`, nothing needing a game. That is deliberate and is what makes
  `ScreenLayoutTest` and `GuidebookTest` possible. Do not import Minecraft into them. `AWDraw` is the
  trick that keeps the drawing testable: one method that puts a coloured rectangle somewhere, which
  the screen satisfies with `graphics::fill` and a test satisfies with a recorder.
- **Tests target the failure that would not throw.** Geometry invariants, tiling with no gaps or
  overlaps, handedness on a round trip, coverage of lang keys and capabilities.
- Guards are verified by breaking them. `CapabilityCoverageTest` was checked by removing the
  registration and watching it fail — "a guard that has never failed is a guard nobody has checked."

Add a test with any behavioural change. If a change cannot be tested without a running game, say so
plainly rather than asserting something weaker and calling it covered.

---

## Non-obvious invariants

These have each cost a real bug. Breaking one usually produces something that draws or runs, wrongly.

- **A GeckoLib block's item is drawn by code, not by a sprite.** Its `models/item/*.json` declares
  `builtin/entity`, which means "nothing here draws this" - so an item whose renderer is not
  registered in `AWClientSetup` is not a missing texture, it is an empty slot. `GeoBlockItem` carries
  the geometry, skin and idle animation; the client registers one renderer for the lot.
- **A Rift Fissure's hit box depends on who is looking.** `getShape` is handed the looking entity in
  its collision context, and returns nothing at all unless that entity is wearing Rift Infused
  Goggles. Without that, an invisible block still draws a selection outline, which gives it away to
  everybody. The collision shape is empty for everyone, deliberately: a wall that exists for some
  players and not others desynchronises.
- **The handbook's text keys are named, never positional** — deliberately, as a reaction to the
  line below it. A page's lines are keyed by the page and their own name, so a sentence can be moved,
  or another dropped in above it, without touching a single translation.
- **Ponder text keys are positional.** `text_1`, `text_2`… are assigned in the order `.text(...)` is
  called, so inserting a sentence mid-scene renumbers everything after it. Ponder also resolves text
  through `I18n` with **no fallback** to the storyboard string — a missing entry shows the player a
  raw key. Both are why the lang entries are generated, and why `PonderTextTest` and
  `LangCoverageTest` exist.
- **`MapColor.calculateRGBColor` returns ABGR**, not ARGB — the order vanilla's map *texture* wants.
  Reading it as ARGB swaps red and blue: grass comes out blue-green, water orange.
- **Catnip's `BoxElement` draws its border outside the bounds it is given.** A window `width` across
  has `width - 8` of usable interior. `AWLayout` encodes this; the screens must not re-derive it.
- **Screens must fit 320 virtual pixels wide.** Minecraft's Auto GUI scale guarantees at least
  320x240 and never promises more. `AWLayout.anchor` handles the rarer case where even that fails, by
  pinning to the top-left rather than clipping both edges symmetrically.
- **Interpolated ages are floats.** A "held forever" value in the hundreds of millions cannot
  distinguish one tick from the next, which once made a 20-tick close animation vanish entirely. Hold
  values are deliberately ~10 million, not `Integer.MAX_VALUE`.
- **A gate crossing is a change of side between two sightings the gate took itself** — never inferred
  from an entity's previous position, which depends on when in the tick that entity happened to move.
- **A gate passage sets nothing on a player that is saved.** A player who crosses is put at the far gate at
  once and is then *in passage* for `gatePassageTicks`: unhurt, untargeted, unmoved, unseen. All of that is
  `GatePassages` answering events from a ledger (`EntityInvulnerabilityCheckEvent`, `LivingChangeTargetEvent`,
  `LivingKnockBackEvent`), never `setInvulnerable`, `setNoGravity` or a game mode - each of those is written
  to the player's NBT, and a server that stopped mid-passage would come back with it still set and nothing
  that knew to clear it. The one flag it does set, invisible, a player does not save. Gravity is left alone
  on purpose (a hovering player is kicked for flying after 80 ticks). It runs on the server tick, not a
  gate's, so a gate that hangs up or unloads cannot leave anybody held. `GatePassageWiringTest` reads the
  ledger's source for every one of these.
- **A gate ignores anyone in passage before it notes which side they are on.** A held traveller stands in
  the far gate's own catchment. `moveEntities` asks `GatePassages.inPassage` and `moveVehicles` asks
  `holding` *before* `stepped`; asked after, the gate remembers a side for them and the first step they take
  on being let go is a crossing straight back. A crewed vehicle is held with `driveVelocity(ZERO)` every
  tick and driven off at arrival; an uncrewed one, a mob and an item still cross in the tick, so both paths
  through each mover are live.
- **A train goes through a gate by two real dimension changes, never one same-dimension "portal".** Create carries
  a train through a portal only when its ends are in different dimensions (`TrackEdge.interDimensional`; a carriage
  keeps one entity per dimension). So a rail lane (`rail/`) is the player's track bound into a short corridor in
  `aerowarptics:rift_corridor`, and the corridor's east end bound to a stub laid out of the routed gate - both
  bindings written exactly as Create's own `connectToPortal` writes them. `RiftPortalTracks` registers the pane,
  the `RiftSocketBlock` and the `CorridorMouthBlock` with `PortalTrackProvider.REGISTRY`. The corridor is kept out
  of everything else that lists dimensions (`ProbeDimensions.offered`, `CrossDimensionWarp.joined`; it has no sky,
  so no storm). `docs/rail-gates-plan.md` is the design record. `RailWiringTest` reads the hook-up off the source.
- **A lane's cell is never left empty.** Create turns a portal-shaped track to air the moment the block it points
  into stops being a registered portal - and takes its bound partner with it. So `setAperture` swaps pane for
  socket in every cell `RailLaneRegistry.laneCells` names, and a ring that comes apart empties its cells *before*
  `RailWorks` takes its lanes up, or Create's next tick finds the portal still there and asks for the lane again.
  Every bound track this mod takes up is `unbind`-ed first: removed bound, it destroys its partner, loading that
  partner's chunk to do it.
- **A track joins Create's graph from a scheduled tick, which only runs in a block-ticking chunk.** A region ticket
  of radius one block-ticks only its centre, so `RailWorks.holdCorridor` puts one on every chunk of a corridor, and
  `joinGraph` calls `TrackPropagator.onRailAdded` as each piece is laid. Held every other chunk, half a corridor was
  never in a graph and schedules found no path through it (seen in game, 2026-10-18).
- **A train is held at a gate by answering Create's portal listener, never by cutting track.** `CarriageMixin`
  wraps the listener `Carriage.travel` passes to `TravellingPoint.travel`, found by the call and not by Create's
  lambda's synthetic name. `RailTransit.holds` lets through anything already crossing - straddling the pane, or
  present in the corridor - whatever the gate is doing, and reads which way a carriage crosses off where it is,
  because Create hands the two nodes over in opposite orders forwards and backwards. A held train would cancel its
  navigation and search again every tick; `TrainMixin` skips the first `cancelNavigation` and the `endOfTrack` report
  on a tick the gates held it. Everything there fails open (`RailGates`). The gate dials for a waiting train from
  the server tick, through `dialForTrain`, which pays as a dial does and never changes the gate's route; a
  connection a train struck (`railOpened`) shuts itself when `RailTransit.busy` has been false for `lingerTicks`.
- **Create reads driving keys straight from GLFW,** so a train cannot be driven by hand through the Minecraft MCP.
  Test with a schedule and a Blaze Burner conductor: the burner goes on the side each Controls block *faces*.
- **A passage's timing is `GatePassage`, one function of the clock and of when the server said it was
  over - and the veil is not a style's to leave out.** The client is told a forecast and then, separately,
  that the traveller has arrived; a late server is waited for in white and an early word starts the flare
  there and then, and every curve is the same either side of the word (`GatePassageTest`, a hundred samples
  a tick). The overlay is a GUI layer registered above all - a mod's layer is drawn with the HUD hidden,
  and after any shader pack - and `LevelChangeReset` clears it, or a player is left behind a veil with their
  keys doing nothing.
- **While a passage's veil is whole, a style shows nothing of the world - at any shape of screen.** The
  traveller is already at the far gate, held, so the far side must not show before the flare is whole. A
  `GatePassageStyle` decides what the veil is made of and where it thins first - Energise dissolves a grain
  at a time, Down the Pipe opens an iris - never whether there is one: with `Veil` at 1 every pixel's alpha
  is 1, with it at 0 every pixel's is 0, and no pixel is covered again once it has cleared. A wipe or a hole
  sized in screen heights stops short of an ultrawide's corners; size it from `length(vec2(Aspect, 1.0))`.
  `PassageShaderTest` draws every style at four shapes of view and reads the alpha of every pixel.
- **A shader is compiled by a real driver in the tests, and looked at before it is believed.**
  `PassageShaderTest` makes a hidden OpenGL 3.2 core context and compiles, links and draws every
  `shaders/core/*` program; it is skipped, not failed, where no context can be made. Nothing else can so much
  as parse one, and a shader that fails to compile in game is one log line and a fallback. It cannot say a
  style looks right: run it with `PASSAGE_SHEETS` set to a folder (and `--rerun`, since an environment
  variable does not make Gradle run a test again) and it writes a contact sheet of every style there. Four of
  the six were visibly wrong on their first sheet. Do not use `near`, `far`, `half`, `input`, `output`,
  `filter` or `sample` as GLSL names - reserved somewhere - and never call `smoothstep` with its edges the
  wrong way round, which is undefined.
- **To add a passage style:** a constant in `GatePassageStyle` (before `QUIET`; its name is what a config
  file holds, so never rename one), `shaders/core/passage_<name>.json` and `.fsh` on the shared
  `passage.vsh`, and `gate.passage.<name>` and `.about` in the lang file. Nothing else: `AWClientSetup`
  registers every style's shader, `GatePassagePainter` draws it and falls back to Quiet if it did not load,
  and the dial's button steps through the enum. A style's JSON declares only the uniforms its fragment program
  reads; all of them come from `PassageFrame`.
- **A uniform is named three times, and a name in one place and not another fails silently.** Given in
  `PassageFrame.uniforms`, declared in the style's JSON, read in its `.fsh`: `safeGetUniform` hands back a
  dummy for a name the JSON lacks, and the JSON's default is drawn for one nobody sets. `ResourceIntegrityTest`
  holds each list inside the next, and every core shader's programs to its JSON.
- **The dial's Passage button is the player's, not the gate's.** It writes `gatePassage` in that player's
  client config and sends nothing, so it is never greyed out on somebody else's gate. It plays nothing
  either: it once showed the style chosen full screen behind the panel, and the owner asked for that to go
  (2026-10-04), so the tooltip's line about the style is all that describes it - do not add a preview back.
  The dial's readings panel gave up the room for the button: `ConsoleTextTest` holds the longest refusal to
  what is left.
- **Who may use a destination is asked through `Membership`, never read off its `access` alone.**
  `WarpAnchor.isVisibleTo` and `RiftGate.isVisibleTo` take one (the `WarpNetworkRegistry`, in the game) and
  both defer to `NetworkAccess.permits`, which is tested as a table. A new place that lists or accepts a
  destination has to ask the same way: a check written as `access == PUBLIC || owner` compiles, works on
  every anchor made before networks, and shuts a network's members out of their own anchors. Network access
  with no network, or a network that has gone, is Private - it must fail shut, and `withoutNetwork()` is how
  a machine comes off one. `WarpAnchorAccess.NETWORK` is declared last because the modes go over the wire by
  ordinal; `next(hasNetwork, allowed)` steps over a mode the server forbids rather than stopping at it.
- **A machine knows its network by id, and an anchor's block is what has to be told.** An anchor's block
  re-asserts its record whenever its chunk loads, so a network kept by name would be un-renamed by the first
  anchor to wake, and one taken off a network only in the registry would put itself back. `WarpNetworks.detach`
  changes the records, tells every loaded anchor (`leaveNetwork`), and leaves the unloaded to
  `NetworkRules.mayStay` in `syncToRegistry`. A gate's settings live only in its record - which is thrown away
  when its ring breaks, so the controller keeps its owner, name, access and network (`kept*`) and gives them
  back in `registerSelf`. Lose that and a mended gate comes back unnamed and **public**.
- **A list's network filter is applied on the server, before the cut.** `NetworkFilter.options` is built from
  everything the player may see, `applied` falls back to no filter when the one asked for is no longer on
  offer, and only then is the list priced and passed to `PacketLists.cap`. Filter after the cut and a network
  whose anchors are all far away shows empty on exactly the servers big enough to need the filter. Both list
  screens send their filter with every action, because every action is answered with the list again, and
  drop an answer about any other filter for a second after the button is pressed.
- **A network's refusals are said where the player is looking.** The members panel and a gate's dial stay
  open, so theirs ride the panel packet as a `NetworkNotice`, as a coupler's do; an anchor's Save closes its
  panel, so its refusal goes to the action bar. `NetworkTextTest` holds every notice key to a translation,
  because the source scan cannot see keys built on the server.
- **A `WarpCourse` is an anchor or a fix, never both and never neither.** Enforced in the
  constructor. A probe's fix has no anchor at the far end and never will.
- **A fix is not necessarily in the drive's own dimension.** A Rift Probe can sound another, and the
  course carries which (`WarpCourse.dimension`; `null` is "the drive's own", which is every fix saved
  before that and a Rift Beacon's). The same three numbers are a different place in each, and a fix flown
  in the wrong one does not fail - it arrives over ground nobody looked at. Ask the course, never the
  block: `WarpValidator.validateFix` and `fixDistance` take the course (across dimensions the distance is
  `crossDimensionDistance`, an anchor's rule), and a committed destination's level is the drive's
  `arrivalLevel()`, never `serverLevel`. A probe keeps the dimension of its reading beside the reading
  (`soundingDimension`) rather than reading it off the dial, and only ever aims at what
  `ProbeDimensions.offered` returns now - the panel's list is as old as the panel. A new dimension-wide
  rule for crossings has to go in `CrossDimensionWarp.joined` too, or the probe offers scans of somewhere
  every drive then refuses. `ProbeDimensionTest` holds the course, the aim and the packets.
- **Redstone is the only way to launch.** Courses are set at an Astrolabe (where permission is
  checked); a rising edge on the drive replays that decision and never makes a new one. A drive in
  `DriveMode.FREE_CRUISE` has no course: switching the mode at the console is the checked moment
  (`cruiseAuthor`), a signal rising from 0 engages, and signal strength is the throttle. The console's
  Disengage only ever brings a cruise to a stop.
- **A free cruise is real flight, not a fold.** There is no `WarpFlight`, rift or teleport. The drive
  replaces only the hull's horizontal velocity each tick (`Airship.cruiseVelocity`) and hands back its
  vertical speed and rotation, which is what lets the pilot steer - never use `driveVelocity` for it,
  which resets both. It stays inside the ordinary `WARPING` state, so `isSequenceRunning()` still covers
  it; `CruisePhase` on the drive says which part. Speed is capped by `CruiseSensor`, which reads only
  chunks already loaded and treats unloaded ground as a wall - `SafeArrival.clearance` loads what it
  reads and must never be called at cruise speed. The starlines keep a window down the heading clear
  (`HyperspaceField`, proven in `HyperspaceFieldTest`), and `AirshipWarpData` heartbeats so a long
  cruise is not released as stale.
- **A drive's `clearanceChecks` switch skips the checks, it does not ignore their answer.** Off (set at
  the console, saved per drive), `beginWarp` never calls `LaunchClearance.guard` or the corridor proof
  and `WarpFlight.plan(..., searchArrival = false)` places the hull with `SafeArrival.straightIn` - the
  search's own first candidate, no block read. A new check added to the pre-flight has to honour the
  switch too, or a pilot who turned "the clearance system" off finds one piece of it still refusing.
  `jumpToCruise` skips its bow guard; `CruiseSensor` is flight, not a check, and still runs.
  **The one pre-flight check the switch does not skip is `SHIP_FASTENED`** (`RiftDriveBlockEntity.fastened`),
  on purpose: the switch is about terrain, and a ship tied to the ground cannot fly its run at the rift at
  all. Do not "fix" it into honouring the switch.
- **Size a warp from `FlightGeometry`, never from `worldBounds()` extents.** A world box's X extent
  depends on the compass, so anything sized from it — aperture, margins, chunk claims — made whether a
  large ship could warp depend on which way it faced. And claim ground **along runs**
  (`ArrivalTicket.centresAlong`), not in one square about a point: a square has to be as wide as a run
  is long, gets capped, and the old capped one missed where a long hull reappears. `LargeHullTest`
  sweeps both across sizes and headings.
- **A warp is sized from the whole vessel, in the hull's own frame: `Airship.vesselShipBounds()`.** That is
  the hull's plot grown round every one of its `parts()`, and it is what `WarpFlight.geometry` and the
  arrival search's placement measure; `vesselCentre` is what the rifts are placed against and the run at the
  rift is measured from, and `LaunchClearance` sweeps `vesselWorldBounds()`. Sized from the hull's plot
  alone, a ship built in sections got a rift the size of the section its drive was in and had only that
  section's landing looked at. Bearing contraptions are still left out - a propeller's box is its whole
  sweep, which is what sank `assemblyBounds()` as a footprint. With no parts every one of these is exactly
  what it was. `VesselBoxTest` holds the arithmetic; none of it has been flown.
- **A Rift Bond is a link in the vessel chain and not a joint.** Sections that only fly alongside each other
  have nothing for Sable to walk, so `Airship.joinedTo` asks `RiftBonds` about every Rift Bond it finds in a
  plot's chunks: everything else loaded in the same level, aboard a ship, tuned to the same bond and within
  `bonds.bondReach`. From there it is an ordinary part - carried, weighed, held, searched by `Fastenings`,
  excused by the clearance checks. It needs no `Fastenings` probe because it ties nothing down. The ledger
  is in memory and filled by each block entity as it loads (`BondLedger`, pure); a section that is not
  loaded is not found, and is left behind.
- **Find a drive from a position with `DriveAssembly.driveAt`, never `instanceof RiftDriveBlockEntity`.**
  A formed Drive Extender is its drive to anything outside the frame - modules, the console, the beacon.
  A neighbour search written the old way compiles, works beside a lone drive, and silently ignores
  every face of an extended one. The drive's own module search walks `DriveStructure.surroundings`, not
  its six neighbours, for the same reason. An extender only counts while the drive's frame covers it:
  the pointer is checked both ways.
- **A block entity's `getRenderBoundingBox()` does nothing for a GeckoLib renderer on its own.** NeoForge
  21.1 culls a block entity renderer by `renderer.getRenderBoundingBox(be)`, defaulting to the one block;
  the block entity method is Create's hook and GeckoLib never reads it. A model that reaches past its
  block vanishes when that block leaves the screen - an extended drive did, with seven blocks of model in
  view. The renderer must override it and return the block entity's box. `RenderBoundsTest` checks.
- **Flip an extender's `FORMED` with `KineticBlockEntity.switchToBlockState`, never a bare `setBlock`.**
  A formed extender is a kinetic relay carrying power to its drive, and whether it has shafts is its
  state. Create only re-links a kinetic block on a state change it counts as kinetically different,
  which `DriveExtenderBlock.areStatesKineticallyEquivalent` extends to `FORMED`; a bare setBlock leaves a
  freshly formed frame connected to nothing, or a broken one still turning on a source it has left.
- **An Astrolabe table has to survive arriving in pieces.** Placing and breaking are not the only ways a
  cell appears. Sable's assembler writes blocks straight into the plot with `onPlace` switched off
  (`captureBlockSnapshots`), and a Create contraption sets blocks down before loading their NBT. Both
  defences are needed. `AstrolabeBlock.onCommonSetup` registers a Create attached-check so a table
  moves whole: Simulated's assembler only takes glued or attached blocks, and a table's sides attach to
  nothing. And each cell runs `AstrolabeBlock.checkTable` on its first lazy tick after any load, which
  breaks up a table with a hole in it. A formed cell whose origin is missing draws nothing at all - not
  a missing texture - which is what a half-carried table looked like on the deck.
- **A hull that jumps is moved by `Airship.relocate` and nothing else.** A client draws a sub-level a
  tick and a half in the past, interpolated between buffered poses, and a teleport lands in that buffer
  as just another pose - so left alone it draws the hull half-way across (in chunks it does not have:
  the ship "vanishes") and carries whoever stands on it the same distance in one tick (they are
  "launched"). `relocate` therefore moves everything aboard with the entity's own `setPos` (Sable's
  push/pop raw write leaves the entity filed under its old level section), and sends every tracking
  client a `HullJump`; `FoldCrossings` carries that client's buffered poses and, for the crew, its own
  player through the same transform. A new way to teleport a hull that bypasses `relocate` brings both
  bugs back. `HullJumpTest` holds the arithmetic.
- **A warp moves the whole vessel - the hull and its `Airship.parts()` - and refuses what it cannot move.**
  `parts()` is Sable's connected chain (a Docking Connector's partner, a Swivel Bearing's plate, a Spring's
  pair). `relocate` teleports every part through the hull's own `HullJump`, and `driveVelocity` /
  `cruiseVelocity` command every part the same speed; `effectiveMass` and the drive's holds cover them too.
  Moved alone, the hull left its parts behind still joined to it, and the joint dragged them together across
  the whole jump - the bug behind this was a Docking Connector locked to the *ground*, which the drive could
  not fly away from at all. Sable cannot list the joints on a body, so `Fastenings` asks each thing that
  makes one by name (Simulated's machines only in `compat/simulated/SimulatedTies`, each probe fenced
  against a `LinkageError`); anything joined to the ground or to a ship outside the chain refuses with
  `WarpFailure.SHIP_FASTENED`. Ropes are the exception to "everything joined is in a plot": a strand's
  middle is world-space points, so `SimulatedTies.ropedTo` puts whatever a rope ends on into the chain and
  `CarriedRopes` lifts every all-aboard strand out of the physics world before `relocate` teleports, moves
  its points through the `HullJump`, and adds it back (`ClientRopes` moves the client's buffered points so it
  does not streak). Only a rope to the ground holds; a crossing refuses any rope (`DIMENSION_ROPED`), since
  Dimensional Sable moves neither the strand nor what hangs on it. A new thing that joins sub-levels needs a probe there, or it is the Docking Connector
  bug again. A new teleport path must move `parts()` as `relocate` does, and must dedupe them against the
  rest of what it moves - see the Rift Gate's `carried` set.
- **Nothing loads a chunk except `hold/Holds`, and a ship is held with Sable's ticket, never by forcing its
  ground.** Sable keeps a sub-level only while *every* column under its box (grown a block) is in
  block-ticking range - ticket level 32 or below - and unloads a crewless one the moment it is not; a
  region ticket's outer ring is only 33. Residency used to force the ground a ship was over, renewed every
  twenty seconds, so a ship a warp had just put down (a beacon's summon above all, nobody aboard) sat on
  ground that was not enough and was filed away by Sable - and a big hull never came back, because no
  player's simulation distance covers all of it. `Holds.holdShip` puts Sable's own force-load ticket on the
  ship instead, which holds the ground under it wherever it goes; the drive takes a `FLIGHT` hold for every
  running sequence and a `RESIDENCY` hold after (`tickHolds`). Every ship hold is on a lease in `HoldLedger`
  (overworld game time, so a restart neither spends nor refreshes it) and `HoldUpkeep` lets go of whatever
  is not renewed and sweeps for our tickets the ledger does not know. Sable's ticket does not count as a
  forced chunk, and a level with no players and none stops ticking block entities after 300 ticks - so one
  non-ticking wake chunk per level (`AirshipResidency.wake`) sits under a held ship. Everything else - a
  preview, a beacon's reach, a chute's partner, a gate's far end - is an expiring region ticket through
  `Holds.ground`, which refuses plot space: a machine aboard a ship that wants its far end loaded reaches for
  the *ship* (`Holds.reachShip`, via the ship locator in the ledger). `ChunkLoadingChokePointTest` fails on a
  loading call anywhere else. Removing the mod is safe: Sable logs and drops a ticket type it does not know,
  and NeoForge drops forced chunks of a missing controller. **Read a Sable ticket only through
  `SableTickets`**: Sable 2.0.5 (shipped with Aeronautics 1.3.2) made `SubLevelLoadingTicket` a record, so
  `getType()`/`getKey()` became `type()`/`key()`, and the direct call built against 2.0.3 crashed every 2.0.5
  world on its first tick. Every other ticket call is wrapped to catch `LinkageError` and stand ship holds
  down (`Holds.incompatible`) rather than stop the server.
- **A warp holds the hull's attitude after every physics substep, not once a tick.** Sable calls the
  drive (`sable$tick`) once, then runs two substeps in which lift blocks push on the hull in proportion to
  its speed at their own positions; at warp speed that twists an unbalanced hull, and zeroing the spin per
  tick never undoes the turn already made - hulls came out of the far rift tilted. `AttitudeHold` listens
  for Sable's `ForgeSablePostPhysicsTickEvent` and teleports a held hull back to its attitude, keeping its
  linear velocity. The flight renews the hold each tick and releases it on completion or abort; a new way
  for a drive to fly a hull must do the same, or its hull will lean.
- **"Stop" has to reach Sable's borrowed motion, not just delta movement.** Sable keeps what a deck
  lent a creature in a vector of its own (`LivingEntityMovementExtension.sable$getInheritedVelocity`)
  and keeps adding it while they are off the deck, losing one percent a tick. `setDeltaMovement(ZERO)`
  and the game's motion packet never touch it, which is how "calmed" crew went on flying at passage
  speed. Go through `BorrowedMotion` and `WarpPassengers.calm` (which sends `ClientboundSettlePacket`);
  lend the hull's current velocity, not zero, to anyone put back on a deck that is still moving.
- **A rendered machine calls `DrawnAboard.redraw` from `onLoad`, and answers `shouldRenderOffScreen` with
  `DrawnAboard.offScreen`.** A GeckoLib machine is drawn only if a build of its chunk section filed it, and
  a warp left machines unfiled two ways: a block entity the client creates from an update packet, after its
  section was built, marks nothing to rebuild; and the off-screen list is rewritten by a section build even
  after it is cancelled, so a stale build can empty it - and Sable never culls a ship's sections, so aboard
  one that list protects nothing. Forget either and a new machine draws on the ground and vanishes after a
  warp, still ticking and still read by the goggles. Create's own machines are immune because Flywheel
  draws them. `DrawnAboardTest` reads both off the source.
- **A Rift Storm's rain and sky are drawn after the clouds, against the depth the solid world had before
  them.** `CloudlessDepth` copies the depth at `AFTER_BLOCK_ENTITIES` and swaps it in at `AFTER_WEATHER`,
  then puts the full depth back. Tested against the full depth, the clouds hid the rain round a ship at cloud
  height, and an aurora drawn at `AFTER_SKY` was painted over by them. The tears, vortex, aurora and streaks
  hang just inside the far plane (`SkyFrame`) so the depth test passes only on sky, each at its own share of
  the distance - tears furthest, streaks nearest - and are drawn furthest first, because a tear's End surface
  writes depth. Not under a shader pack, nor Fabulous graphics, whose layers are merged by depth: there the
  sky is drawn at `AFTER_SKY` under the clouds, the cloud flashes' glow is not drawn at all, and the rain
  writes depth (`RIFT_RAIN_LAYERED`), as vanilla's does, or it sits behind every hill.
- **The storm's rain is vanilla's `rain.png`, recoloured as it loads - never a copy of it.** Vanilla's rain is
  one blue at many transparencies and a vertex tint can only multiply, so `RiftRainTexture` (a `SimpleTexture`,
  so a resource reload rebuilds it) keeps the mask and replaces the colour through `RiftRainTint`. It is
  registered on first draw under `aerowarptics:dynamic/rift_rain`, which has no file: ask the texture manager for
  that name before it is registered and it caches a missing texture instead. Do not ship Mojang's file.
- **The storm's rain asks the ships for itself.** A ship is not in the level's heightmap - its blocks are in a
  plot - and vanilla's rain stops on a deck only because Sable wraps the heightmap reads inside
  `LevelRenderer.renderSnowAndRain` and `tickRain`. `RiftStormRain` is built like those two but is not them,
  so its drops, its splashes and its under-a-roof sound each pair their heightmap read with `RainCovers.over`
  (`RainCover` is the walk, pure). A new read there without one falls through every deck, and nothing fails
  but `RainCoverTest`, which counts them. The walk starts half a block down and closes on the surface instead
  of answering in whole blocks as Sable does: a walk in whole blocks from the top of a level hull's box reads
  the face between two blocks every time, and rounding finds a one-block deck or misses it. Drops go by the
  hull's `renderPose()`, splashes by its `logicalPose()`. Not seen in game.
- **A drive's storm void is a threshold, not a transparency.** `StormCorruptionLayer` draws through
  `RenderType.dragonExplosionAlpha`, whose shader keeps a texel only where texture alpha is at least vertex
  alpha; the void sheets' alpha is the order the void takes each texel, so the vertex alpha grows the patches.
  A cutout type cannot stand in for it - 1.21.1's cutout shaders test alpha before the vertex colour. The sheets
  never hold a full 255 (the threshold with no storm is exactly one), and the threshold is never zero (every
  empty texel would pass and paint the drive black). `DriveVoidTextureTest` and `StormCorruptionTest` hold both.
- **Machines aboard a hull are read from the plot's own chunks**, not Sable's actor list — membership
  of that list depends on a block having *changed* since the plot was created, so a machine placed on
  an already-assembled hull may never appear in it.
- **What is one vessel is asked of `Airship` (`assemblySubLevels`, `parts`, `vessel`), never of
  `SubLevelHelper.getConnectedChain` directly.** Sable's walk reads that same actor list, so a Swivel
  Bearing missing from it leaves its turret out of the chain - and a turret out of the chain is "another
  ship" inside the hull's own box, which refuses every launch with no block to name. `Airship.chain()`
  also asks every machine standing in each plot's chunks (`VesselChain` is the walk, pure and tested), and
  never finds less than Sable. The trace's `parts=` prints both counts; a bow guard fouled by a hull names
  it. Not confirmed in game that the actor list is what dropped the reported turret.
- **A drive's comparator reading is `DriveSignal`, and fourteen means exactly `acceptsDestination()`.** A
  launch circuit waits for fourteen, so a reading that showed it a tick early throws a lever at a drive that
  refuses it; `DriveSignalTest` holds the two together. The switch has no default, so a new state will not
  compile until it is given a reading. The drive tells comparators on a change (`tickComparators`), at every
  cell of its frame, and a formed extender answers for its drive - a new way for the reading to change needs
  nothing, but a new block that stands in for the drive needs both overrides.
- **A warp's time in the corridor is the drive's `corridorTicks()`, never `tier.warpTicks()`.** The tier's
  figure is only the base: `flight.warpTimeScaling` (off by default) lengthens it with the committed
  distance through `CorridorTime`. Anything that plans a flight, sizes a fleet's passage or prices a fold's
  upkeep from the tier's bare figure is right until a server turns scaling on. A corridor longer than
  `ArrivalTicket`'s lifespan renews its landing and corridor claims as it goes (`renewCorridorHolds`).
- **A new warp sound has to be named in `WarpSoundSwitch`.** The client's `warpSounds` switch drops sounds by
  name on their way into the sound engine, because the server plays most of them. One left off the list
  goes on playing for a player who turned them off, and nothing fails; `WarpSoundTest` only checks that
  every name on the list is a real sound.
- **How loud a sound of the mod's is, is `SoundMix`'s to say, not its caller's.** `WarpSoundFilter` wraps
  every `aerowarptics:` sound in a `TrimmedSound` on its way into the engine - server-played ones included,
  which `effectVolume` never used to reach - at half what was asked times the setting (players found them
  too loud, 2026-10-08). Do not multiply by `EFFECT_VOLUME` at a call site: that is twice. A volume past one
  is reach, not loudness (the engine clamps it), so the wrapper plays it at one's gain and gives the carry
  back as attenuation distance; merely scaled, a sound asked for at three is as loud as ever and heard a
  third as far. Two things it cannot do: a tickable loop is left unwrapped (wrapped, the engine stops
  ticking it) and multiplies its own volume by `TrimmedSound.level()`; and a *vanilla* sound the mod plays
  (a gate's beacon, the collapse's thunder) is not in the namespace, so it is only as quiet as its call
  site makes it. `SoundMixTest` holds the arithmetic and reads both rules off the source. Not heard in game.
- **A rift's transit cue must be able to open the rift by itself.** The far aperture's open cue goes out as
  the corridor begins, to players near the *far* end, while the crew are still at the entry, thousands of
  blocks out of range. So the crew first hear of it from the `RIFT_TRANSIT` sent as they cross, which is
  why that cue carries the throat, goes to the crew by name (a long hull's stern sits deeper in the
  throat than the tracking range reaches) and opens the aperture if the client does not have one standing.
  Before that, crew only saw the far rift on warps short enough to be in range. A transit also extends
  the aperture's hold (`ActiveRift.passage`), because the open cue's hold was only a forecast and a
  lagging server once closed it around a hull still inside. A far-end cue goes to `arrivalLevel()`, not
  the drive's level, which before a cross-dimension crossing is the wrong world. `RiftPassageTest` holds
  the client side of this.
- **An arrival is announced, never searched for.** Anything new that brings something out of a rift -
  another kind of warp, a new way through a gate - has to call `SpatialWaveDetectorBlockEntity.announce`
  with the world box of what arrived, as `RiftDriveBlockEntity.onEmerged` and both of
  `RiftGateBlockEntity`'s movers do. Forget it and the detectors compile, sit loaded, and simply never
  hear that kind of arrival. A player, or a crewed vehicle, a gate puts into a passage has not come out
  yet: the gate says nothing, and `GatePassages.arrive` announces and awards the advancement when they do.
- **`Map.Entry.setValue` returns the old value.** Two countdowns in this codebase have run a tick
  long because of it.
- **GeckoLib turns a block model about the middle of its *base*, not its centre.**
  `GeoBlockRenderer.preRender` translates to `(0.5, 0, 0.5)` and `rotateBlock` turns about that
  point. Harmless for the four horizontal facings, which only spin about the vertical axis through
  it, and wrong for `UP`/`DOWN` on any bottom-anchored model: a quarter turn about the floor drops
  half the model under the ground and pushes the rest into the next block. `RiftDriveRenderer`
  overrides `rotateBlock` to lift the pivot half a block first. `RiftModulatorRenderer` copies the
  same override for the same reason - it also takes all six facings and also stands on its own base.
  Everything else here is either horizontal-only (the Warp Anchor) or does not turn at all.
- **A warp into another dimension rebuilds the hull; it does not move it, and this mod does not do the
  rebuild.** Dimensional Sable does, called at the crossing through `DimensionalSableBridge` (off by
  default: `allowCrossDimensionWarp`, and nothing happens without the mod). It is reached by method
  handle, never imported - keep it that way, or the build needs a jar for a feature most servers leave
  off. The rebuilt ship has a **new UUID, no user data, and every block at a new plot position**, and
  Dimensional Sable only fixes the block-entity fields on its own list. Anything this mod remembers by
  ship UUID or in plot/ship space must be carried in the drive's `CrossingHandoff` and repaired in
  `finishCrossing`, the way the warp claim, `WarpPassengers.rebase` and `CrewManifest.crossed` are. A new
  such memory that is missed loads fine and points thousands of blocks from the deck.
- **Dimensional Sable is asked nothing it will throw on.** It builds every copy before removing any
  original and checks neither the far grid's free slots nor the far dimension's section count, so
  `CrossDimensionWarp.preflight` does, at the mooring and again at the crossing; and when it throws anyway,
  `cross` removes every destination sub-level that was not there before the call. Loosen either and a
  failed crossing leaves a ship in two dimensions.
- **Sable forgets ships by grid slot, not by dimension - `DimensionChangeTracking` works round it.** A
  player who changes dimension is only noticed as gone on the old level's next tracking tick, which then
  sends a stop-tracking packet naming each ship there by plot slot. The client is already in the new
  dimension, and both grids fill from slot (0, 0), so that packet deletes the new dimension's ship in
  the same slot: the ship is fine on the server and invisible to the player ("sub-level movement packet
  for a non-existent sub-level" in the client log). `DimensionChangeTracking` takes the player off the
  old level's tracking lists as the change completes. Do not remove it for looking redundant; it is the
  whole reason a cross-dimension warp arrives visible.
- **A crossing stops Create machines at random, and `CrossDimensionWarp.relinkLater` starts them again.**
  Dimensional Sable rebuilds each block entity from its saved NBT, and a Create machine's `Source` is a plot
  position in the dimension it has left. Create's `validateKinetics` runs on a machine's first tick, finds
  nothing there and removes its source; whether a generator's own first tick then starts it again depends on
  which ticked first. Seen in game (2026-10-10): the drive read 0 rpm beside a turning motor after one
  crossing and refused to launch, and the Cooling Vent beside it lost its rotation on the way back.
  `finishCrossing` queues a pass that calls `attachKinetics()` on every machine aboard left with no speed and
  no source. Anything else that rebuilds a hull needs the same. Run in a game on 2026-10-11: a crossing
  stopped one machine of eight and the pass started it again.
- **Under a shader pack the mod's world effects are drawn after the pack's frame, not in it - `AfterShaders`.**
  A pack replaces the program behind every render type drawn during the level, and each does it differently:
  Bliss turns the beacon beam's additive blend into an ordinary one, discards every beam vertex that is not
  fully opaque, and sun-lights position-colour geometry, so a rift lost its glow and its throat went pale (seen
  in game, 2026-10-11). So every effect at the translucent or particle stage starts with
  `if (AfterShaders.defer(event)) return;`, takes its buffers from `AfterShaders.buffers()`, and is listed in
  `AfterShaders.EFFECTS`; `GameRendererMixin` replays them straight after `LevelRenderer.renderLevel`, before
  the depth is cleared for the hand. Three things each cost a failed attempt: Iris announces the same stages
  during its **shadow pass**, with the sun's matrices (`GravityLens.shadowPass`); the game's buffer source is
  still Iris's deferred one at that moment, so the replay draws into its own; and the matrices to put back are
  the ones `RenderSystem` held at the stage, not the event's. The world's depth is copied at the particle stage
  and blitted back first. With no pack none of it runs. A new effect at those stages that skips any of the
  three lines is either drawn inside the pack's frame or not at all - `AfterShadersTest` reads the source.
  Seen right under Bliss, Complementary Reimagined, BSL and Photon: a fissure and a plotter's rift, hidden by a
  wall in front and by the held item. Not seen under a pack: a ship's warp from its deck, the storm's sky
  (which does not go through this), or a pack that renders smaller than the window.
- **A drive's `CrossingHandoff` is the only thing that carries a flight through a drive reload.** The
  drive aboard a crossing hull is saved with it and comes back as a new block entity at a new position;
  it writes `CrossingHandoff` only while being saved for that, and the copy resumes in `finishCrossing`,
  waiting up to `CROSSING_WAIT_TICKS` for Sable to say which ship it is on. Every other reload
  mid-sequence is still `INTERRUPTED`. The old instance sets `handedOff` and must do nothing further - an
  abort from it would be about a ship that is fine.
- **Nothing but mixins may live in `mixin/`.** Mixin reserves the package: an ordinary class placed
  there cannot be loaded at all. The hooks the mixins call live in `RiftStormSky`. Every mixin must be
  listed in `aerowarptics.mixins.json` or it compiles and silently never applies, and `defaultRequire`
  is 1, so a target that stops matching is a crash at launch - `MixinTargetTest` checks both against
  the game's bytecode before that. The mod had no mixins until the Rift Storm, which needed them only
  because NeoForge has no event for the sky, cloud or daylight colour. The one common (server-side) mixin,
  `network.ServerGamePacketListenerImplMixin`, takes vanilla's "moved too quickly" exemption away from a
  move of over a kilometre in a tick (`PlayerMoveGuard`): around a warp's jump a client can work out its
  deck position against a stale pose of the ship, Sable passes a position that lands outside its plot grid
  through unconverted, and the server - which Sable exempts from the speed check for anyone on a ship -
  swept the player's collision box across twenty million blocks and froze for most of a minute. Prefer an
  event wherever one
  exists.
- **Ink goes on the glass pass, never the fire pass.** `RIFT_FIRE` is additive, and additive light cannot
  draw dark: a graphite stroke on it is simply not there. A theme whose `ThemeLook.surface` is `PAPER`
  (Sketch) is skipped by every light pass in `RiftEffectManager` and drawn by `RiftFurniture.inked` on the
  translucent `RIFT_SHARD` pass instead. A new light-pass helper that forgets to skip paper draws white
  fire round a pencil drawing. The one exception is the pencil drawing it (`RiftFurniture.pencil`), which
  is solid and goes on the membrane's pass through `RiftEffectManager.solidWorld`.
- **A sketch is drawn inside the opening, because a hull can arrive at the end of it.** `transit` jumps a
  rift straight to open, so everything `SketchPlan.opening` draws - outline, colour, inner line, shine -
  has to be finished by `openTicks`, and the face is revealed *behind the crayon* (`SketchPlan.front`)
  rather than grown as a circle, so an interrupted drawing still leaves a complete hole. Anything added to
  the opening has to come out of that same second; anything longer belongs in the acting.
- **A sketch has one pencil, so its strokes never overlap, and the gaps between them are travel.** The
  pencil's point is wherever `SketchPencil` says, which is one pure function of the clock and the few
  moments the rift knows about - not a state machine - so a hull arriving mid-twirl or a close landing
  mid-duck is just the same function a tick later. Shorten a gap between two strokes and the pencil
  teleports across the page; `SketchPencilTest` samples a hundred times a tick to catch exactly that.
- **The lens copies the frame after every face and throat, never before.** `GravityLens` bends the view round a
  Mostly Dangerous rift by redrawing a disc out of a copy of the frame. The face and throat are what hide a
  hull; a copy taken before they are drawn - the tidier-looking order - still holds that hull, and the lens
  draws it bent round the outside of the rift. `RiftPassOrderTest` reads the order off the source. It is the
  mod's first custom shader (`shaders/core/gravity_lens.*`; the gate passages' `passage_*` are the others), takes only the copy's colour because a frame's alpha
  is not a transparency, and stands down under Fabulous graphics (the face is drawn into a different layer
  from the world, so no one copy holds both), while a shader pack is running (Iris's API, by reflection) or
  with `riftLensing` off - `RiftFurniture.bentLight` draws a ring of bent light instead. A failed shader load is
  logged, not thrown, so `ResourceIntegrityTest` is what notices a broken one.
- **A cloud is drawn dark, and before the light over it.** `Surface.NEBULA`'s puffs go on `RIFT_SHARD` as a
  batch of their own at the start of the fire pass: additive light cannot draw dark, and lightning in the
  same translucent batch as the cloud is dimmed by it. The light passes skip fire, haze and the seal spark
  for a nebula; its light is `RiftFurniture.nebula`, on its own clock from the first tick to the last rather
  than scaled by the hole, because the cloud crackles before there is a hole and its trails outlive it.
- **`RIFT_END_SKY` is plain position, and fed by `solidVertex` on purpose.** The Ender face and bore go
  through the same solid writer as the membrane, which also writes a colour; `BufferBuilder` silently
  skips an element its format lacks, so that is safe. It is not safe the other way round - give the type
  any element `solidVertex` does not write (texture coordinates, next to its two textures, are the
  tempting one) and it is the `Missing elements in vertex` crash. `RiftVertexFormatTest` pins the format.
  It is not vanilla's `RenderType.endPortal()` because that culls back faces, and a rift is watched from
  both ends. It must also be its own batch after the membrane's: two custom types share one buffer, and
  asking for the second ends the first.
- **A flotilla is keyed on coupler ids, never on ships or positions.** A cross-dimension crossing gives a
  hull a new UUID and moves every block to a new plot position, so a flotilla remembered by either loses
  its escorts at the first crossing. Each Rift Coupler carries an id in its own block entity, which the
  rebuild copies, and re-announces itself from wherever it wakes up. That is also why a coupler whose block
  has gone is only *marked* gone: Sable's assembler and a crossing both take a block away and put an
  identical one somewhere else, which is indistinguishable from a pickaxe until the copy reports in. See
  `FlotillaRegistry.markGone`; `FlotillaUpkeep` forgets what never comes back.
- **An escort in a fold is flown by its own drive, and comes out exactly where the flagship's search put
  it.** Every ship pays its own charge, needs its own rotation and tears its own rift; what the fold
  changes is when the rift opens and where the hull lands. The flagship's arrival search proves the whole
  formation clear in one pass (`SafeArrival.Companion`), so an escort must never search again on its own
  account - a second search would move it out of formation into ground nobody looked at. That is what
  `WarpFlight.planTo` is for, and why slots are kept apart when they are set rather than when they arrive.
- **A coupler's refusals are said in its panel, never the action bar.** Its screens dim the whole game
  behind them, the action bar included, so a refusal said there is never seen: a slot marker dragged where
  it may not go simply jumped back. `ServerboundCouplerPacket.handle` turns every order's answer into a
  `CouplerNotice`, which rides the panel packet to the info line. A new order that can be refused has to
  answer the same way; `CouplerTextTest` holds every notice key to a translation, because the source scan
  that finds the mod's other missing keys cannot see keys built on the server.
- **A fleet going through one rift moves as one rigid thing.** With the Fleet Board's Rift set to One
  (`Flotilla.sharedRift`), every ship's flight is planned on the whole formation's run - `FleetPassage`: the
  flagship's bearing, corridor and run-out, a passage long enough to swallow the formation, and a run at the
  rift flown by dead reckoning (`WarpFlight.keepStep`) from one start tick. Planned on their own runs, a
  column's rear ship flies into the one in front, which has stopped just inside the throat they share. The
  two apertures are one ellipse each, drawn only - each flight still flies its own planes. Every cue about
  them is sent once, by the first ship there (`FoldLog.firstTo`), and each end closes behind the *last* ship
  through it (`throughEntry`, `outOfExit`, `leave`), never the first. A new cue about a fold's rift has to go
  through the same gate, or clients stand one aperture on another in the same place. `FleetPassageTest` holds
  the geometry.
- **A Rift Plotter's crossing is a line between two sightings it took itself, and its entry works from either face.**
  The gate's rule, extended: a shell covers more than a catchment in a tick, so `PlotGeometry.crossing` asks whether
  the segment between the plotter's last two sightings met the disc inside its rim, never whether something changed
  side (walking round the rim is not going through). A backwards crossing is mapped through the entry turned half round
  its own up, so everything leaves the exit's front. What crosses is carried by the one rotation between the two
  frames (`PlotGeometry.turn`); both are built from a yaw and pitch the same way round, which is what keeps it a
  rotation and not a mirror - build a frame any other way (from the world's up, say) and a disc facing straight up has
  no up at all. Vehicles go by `Airship.relocate` with their whole vessel and are then held under `HoldPurpose.PLOTTED`
  on a lease, or Sable files away an uncrewed hull put down where nobody is. Fit is measured off the hull's own box
  turned by its orientation (`PlotGeometry.needs`), never its world box. `PlotGeometryTest` holds all of it.
- **Where a plotter's entry is and which way it faces are two things, and both go through `PlotGeometry.frame`.**
  `Plot.inputYaw`/`inputPitch`/`inputReach` say where it is thrown from the lens; `Plot.facingYaw()`/`facingPitch()`
  say which way it faces - the same line until a player turns it (`inputTurned`, with `faceYaw`/`facePitch` in world
  terms), and off is what every plot saved before entries turned loads as. Ask the plot for the facing, never
  `inputYaw` - that compiles, is right for every entry nobody has turned, and puts the rift, the dock or the hologram
  square to the machine for the rest. The dish and the ball are the exception: they point at where the entry *is*
  (`aim.tick`, `PlotGeometry.focus`). A turned entry is still built from a yaw and a pitch; turn the outward frame
  about one of its own axes instead and its up lies over, and every hull comes out of the exit rolled.
  `PlotGeometryTest.aLevelHullGoesThroughATurnedEntryTheRightWayUp` holds it.
- **A plotter's dock is the size its `Firing` says, asked through the machine.** `dockRadius()` and `dockDepth()` on
  the block entity are the only two questions - the server catches by them and the client draws by them.
  `Firing.AUTO` (either measurement, independently) is the dock every plotter had before docks were sized:
  `DockAlign.zoneRadius` of its rift, and the server's `plotterDockReach`, which is sent in the client tag because a
  client never reads the server config. A missing number in a save reads as nought, which would clamp every standing
  plotter's dock to the smallest there is, so `Firing.load` reads a missing size as `AUTO`. A caught hull is held by
  `DockAlign.keepDepth`, not the dock's depth: its mark is half its length behind the rift, which for a long hull or
  a shallow dock is out of the dock's back. `PlotterDockWiringTest` reads the hook-up off the source.
- **The plotter's sidebar is four tabs of six rows, and that is all there is.** Six rows is what a 240-pixel-high
  screen holds between the tabs and the status lines, and four four-letter words is what 144 pixels holds. A new
  entry setting needs a free row on the tab it belongs to (Rift is full) - `HologramTest` and `RelicTextTest` say no
  to anything else.
- **The plotter's camera is a client-only marker, and it is always given back.** Setting the entry flies the camera
  out (`PlotterCameraRig`) by making a never-added `Marker` the camera entity, with the view forced to first person
  and the HUD hidden. Every way out - closing the table, `leaveField`, `LevelChangeReset`, the camera entity being
  taken by anything else - calls `stop`, which puts the camera, the view and the HUD back as they were. A new way
  to leave the table has to stop it too, or the player is left looking at their plotter from outside with no HUD.
- **The dock steers, never teleports, and never traps.** A caught hull is flown with `Airship.steerVelocity` each tick
  (`DockAlign`: proportional and capped), and pushed through with it - the crossing that follows is what moves it.
  The dock is behind the way the entry *faces*, so it turns with a turned entry.
  A crouching crew member always breaks it free (`brokeFree`), and nothing faster than `plotterDockCaptureSpeed` is
  caught. `DockAlignTest` flies a simulated hull in.
- **The plotter's lens is the model's pivot.** The entry is thrown from `PlotGeometry.lens`, and
  `rift_plotter_model.py`'s `LENS_Y` is where the dish pivots, and `FOCUS_Z` where the ball floats (the beam's start,
  `PlotGeometry.focus`); `PlotterRigTest` reads both back out of the shipped geometry. The dish faces +z, GeckoLib's
  and Minecraft's yaw of nought. `setRotX` is the pitch the entry lies at (`inputPitch`) as it stands, but `setRotY`
  is the yaw it lies on **negated**
  (`PlotterWorks.turntableYaw`): GeckoLib turns a bone right-handed about +y, carrying +z east, and Minecraft's yaw
  carries a look west. Given the yaw as it stood, the dish faced the entry's mirror image across the north-south
  line, right only due north or south. `PlotterWorksTest.theDishFacesTheEntryItIsAimedAt` and the script's
  `check_dish_faces_its_aim` hold the sign; any other GeckoLib bone turned to a game yaw needs the same care.
- **A plotter's exit stands only while traffic is coming.** At the tear only the entry opens. Each tick it stands,
  `traverse` asks everything near it how long until it crosses (`PlotGeometry.ticksToCross`, from its movement since
  the last sighting - a player's server-side motion is stale) and the exit is shown once anything is due inside
  `EXIT_LEAD_TICKS`, or the dock is pushing a ship through, or something arrived unseen; it heals `EXIT_LINGER_TICKS`
  after the last. It is kept up on clients by `RIFT_HOLD` cues (appended to the stage enum - never insert), which
  renew a standing aperture or open one for a newcomer; never re-send `RIFT_OPEN` for a standing aperture, which
  would draw a second one over it.
- **The plotter's world effects are drawn with `LightStrokes`, in four layers, to be seen by day.** Through (no
  depth, faint), shade (dark edging), body (alpha-blended strokes) and glow (additive fire). Never GL lines or
  additive-only light for anything a player has to find at noon - that is how the first dock guide vanished.
  `LightStrokes.glowVertex` is registered in `RiftVertexFormatTest`. The strike's timing is `PlotterChoreography`
  (pure, tested for joins between states); `PlotterSpectacle` and `PlotterGuides` only draw.
- **Every string a relic screen draws is measured against its room.** `RelicTextTest` checks the plotter's table,
  its 144-pixel sidebar (the narrowest screen's), the goggles, and the cradle's three-line status box, wrapped as the
  screen wraps it, against the font's real glyph advances. Shorten the string, never widen the room past the layout
  tests. A tooltip handed to vanilla as one component is never wrapped - pass `font.split(...)` lines.
- **A plotter spends only at the tear, and refuses before it draws.** `PlotRules.preflight` is asked before a
  millibucket is drawn; the far end's ground can only be read once it is loaded, so `PlotRules.farEnd` is asked then,
  with the price asked again (the real ground moves the exit). Refused there, the charge stays in the machine. The
  table's model is loaded through `PlotterSurveys` (which goes through `Holds`) and only its own chunks; the far end
  through `Holds.ground` on the plotter's own expiring ticket. A strike does not survive an unload.
- **A fold is the Collapse Cradle's lesser work, and nothing folded is needed to build the cradle.** What the
  arms hold is folded - with the cup, where a recipe asks for a second thing - for Rift Essence, up to sixteen
  crafts a run (`CradleFold`), refused by a pre-flight as a collapse is and paid for as it begins. The Mk III is
  built from a folded crystal, so the cradle, and so a found Relic Armillary, is on the road to it;
  `ProgressionActTest.theCradleNeedsNothingFolded` keeps that from becoming a circle nobody can enter. Folding
  once happened aboard, on a long enough jump, in a Fold Crucible; it is gone, and nothing about a warp folds
  anything now. A Ship's Compass comes out unbound and is bound by sneak-using it on a drive, since a cradle is
  never aboard.
- **The cradle's panel starts only what a redstone pulse would, and says only what that would do.**
  `CollapseCradleBlockEntity.panel` is built from the same plans (`collapsePlan`, `foldPlan`) as `begin`, so
  the panel never promises a start the machine refuses. The Begin button's packet names a cradle and is honoured only
  for the menu that player has open on it, still in reach - the open menu is the permission, as a chest's is.
  The cradle's slots are shut while it works, and the tray only ever gives up; an automation handler that can
  put things in the tray lets a pipe fill it with anything.
- **A collapse spends nothing until every refusal has been asked, and a paused one never resolves.** The
  cradle's pre-flight (`CollapseRules.preflight`) runs before it touches a depot or the tank, and the only
  stake however a ritual ends is the heart and the essence - a spit throws the Rift Core and every offering
  back out whole. `CollapseRitual` holds, neither advancing nor winding, while the cradle turns below the
  minimum and for a grace window after a load (Create reports no speed for the first ticks); an unloaded
  cradle simply does not tick. `CollapseRulesTest` walks it; a new way to end a ritual must keep both.
- **A collapse's scene is `CollapseChoreography`, and its forces are too.** The client (`CollapseSpectacle`,
  the cradle's renderer) and the server read the same curves, so the pull a player feels is the one drawn and
  the blast knocks back what the ring of air is seen to reach. The server pulls everything but players; each
  client pulls its own player. The blast goes before anything is thrown out, breaks no block, hurts nothing
  that is not alive, and never an armour stand. What the scene shows of instability is its *heading*
  (`CollapseRitual.heading`), which for steady conditions is where the ritual ends. A curve that jumps at a
  beat is a piece that teleports on screen - `CollapseChoreographyTest` holds every one.
- **The Collapse Cradle is one master and twenty-six parts, placed whole and never moved.** The master, in the
  middle of the floor, is the machine: block entity, shaft, renderer. A part
  (`CollapseCradlePartBlock`) knows its master only by its place in the footprint, three numbers in its block
  state (`CradleStructure`), and passes everything to it - clicks, a wrench, goggles (`IProxyHoveringInformation`),
  redstone (the master reads a signal at any of its 27 blocks), pipes and funnels (block capabilities on the part, but see the floor below),
  a Display Link (`DisplaySource.BY_BLOCK`). Something new the cradle answers has to be passed on by the part too,
  or it works on the one block nobody can reach. Breaking any part breaks the whole; a part whose master is gone
  removes itself a tick later, never at once, and the master raises missing parts on placing and on load. Every
  block refuses `BlockMovementChecks`, pushes and a ship's deck, which is what keeps it from ever arriving in
  pieces - loosen that and it needs the Astrolabe's defences. A collapse's point is *inside* it, so anything it
  throws comes out of the crown (`CollapseChoreography.SPOUT`) and blast cover is measured from where each ray
  meets the machine, never from the point, or the machine is its own cover.
- **A relic's floor has block entities, because Create's pipes and funnels look for one.** Both the cradle
  and the plotter stand on eight `RelicBaseBlock`s round the master in their bottom layer (the body above is inert
  parts). `FluidPropagator.hasFluidCapability` and a funnel's capability lookup both return nothing where there is no
  block entity, whatever capability the block registers - so an all-inert body took no pipe anywhere, and the master,
  boxed in, took none either. A master set down before the floor was bases swaps its own old parts for bases on load
  (a part replaced by a base does not take the machine down). Anything new a relic answers from outside has to be
  answerable by a base.
- **A relic is turned from its sides or from under its middle and passes no rotation on - so no block of it has two
  shafts.** A Create block passes rotation between any two faces it has shafts on, consumer or not, and links to any
  block whose shaft faces its own. The floor was once one relay with shafts on every face but up, and a shaft into one
  side of a plotter turned whatever stood against any other (reported 2026-10-07). Now only the base in the middle of
  each side of the floor has a shaft, on its outward face, and the master has one on its underside (the user put a
  shaft there and expected it to work, 2026-10-14) - `CradleStructure.shaftTowards`, `inputs` - each the end of its own
  line. The master's `getSpeed()` is overridden to the speed of the input turning fastest (`RelicFloor.feeder`, chosen by `RelicFeed` on *theoretical* speed, so an overstressed line stalls the
  machine rather than handing the load on and getting it straight back), and its load (`RelicMaster.relicImpact`) is
  counted on that one input (`RelicFloor.load`). Create caches a block's stress, so anything that changes the feeder or the load calls
  `RelicFloor.changed` and each input re-tells its network on its next tick - never inside `onSpeedChanged`, which
  Create calls while walking the network's members. A master's `getTheoreticalSpeed()` is only the line under it, and
  `RelicMaster.lineSpeed()` that line's real speed. Give any block of a relic a second shaft and it is a gearbox again: `CradleStructureTest`
  counts them. Links saved under the old rule are let go of once on load (`RelicFloor.dropStrayLinks`). Seen in game
  2026-10-14: all three take a motor from below, the load lands on that line, and a ward fed from a side and from below
  kept the two as separate networks.
- **The cradle is posed, never animated, and nothing reaches in among its rings.** Every moving bone is set each
  frame from `CradleWorks` on the ritual's clock; a keyframed loop would drift off the beat it shows. The arms
  only ever work below the rings (the core is handed up to `HAND_OFF` and the rift takes it the rest of the
  way), the rings tumble only while the arms are folded, brake flat in the hush, and the arms come home only once
  they lie flat. `CradleWorksTest.nothingReachesInAmongTheRings` walks the arms against the rings' bands and
  sweeps; `CradleRigTest` holds the model's joints and rings to the numbers that pose them. The hole and the
  rift are sized to fit inside the innermost ring - make either larger and it cuts through the armillary.
  A fold works the arms up to `HAND_OFF` and holds them there, so its rings never tumble - only the outer one
  whirls. A failing collapse (`CradleWorks`, "failing", from the heading) shakes the machine by less than its
  clearances: the rings rock under two degrees and the arms tremble by hundredths of a radian, which
  `aFailingMachineStillKeepsItsArmsOutOfItsRings` walks at its worst. Nothing new begins while the arms are
  still coming home after a collapse (`settling`), or they snap from wherever they were into its first pose.
- **Who is in a cradle's ring is asked of `CradleRing`, by everything that asks.** Three things do: the cradle,
  drawing its offerings and counting its cage (`stabiliserOffsets`); a Spatial Stabiliser, for its goggles
  (`CollapseCradleBlockEntity.steadiedFrom` - `ATTENDING` or `STEADYING` in place of the fault of having no
  drive); and each client, drawing the machine's pull on whatever it counts (`CradleLinks`) and a collapse's
  lightning to its stabilisers. A walk of its own in any one of them compiles, looks right with four depots close
  in, and then shows a link the ritual ignores or calls a stabiliser linked from further than the cradle looks.
  `CradleRingTest` holds the rule and the motes' curves; `CradleRingWiringTest` reads the hook-up off the source.
- **A cradle's depots are remembered; its stabilisers are found.** It links to eight depots and a linked one keeps
  its place until it goes (`CradleRing.linked`, from `lookAtRing` once a second and before anything asks what is
  on the depots). "The nearest eight", the first rule, let every depot set down nearer than the furthest take a
  link off another - to the player, each new depot linked, long past the eighth. So `linkedDepots` is saved
  (which eight of more is the order they were set down in) and sent (a client cannot work it out), and a client
  must draw from `cradle.linkedDepots()`, never look for depots itself. It is not refreshed while any corner of
  the square is unloaded, or a depot loses its place to a chunk that was not there. Stabilisers have no memory -
  every switched-on one in reach counts - so the client walks for those, by `stabiliserReach()`, which the
  cradle's packet carries; never the server's config.
- **The cradle's pull is a force, not a display - and so is anything else the relic shows of itself.** The owner
  sent the first version back (2026-10-06): threads of light with pulses, rings round each block and a dark
  edging were "too noticeable" and "a hologram looking thing" on technology that should feel ancient. What is
  drawn now is only what the pull moves: a few slow motes in the carvings' own two colours, body and glow, with no
  shade layer, no ghost through terrain, no ring, no line. Do not give it an outline to make it read better at
  noon; that is the Rift Plotter's manner, whose guides are instructions. Fades while a collapse runs, answers to
  `cradleLinks`. Only the first version has been seen in game.
- **A Rift Ward is asked through `Wards`, by everything that brings a rift out somewhere it chose.**
  `Wards.turnAside` says where a rift aimed at a place will really come out; the caller plans on that, and only
  then `Wards.commit`s. `RiftDriveBlockEntity.planPastWards` does it for a ship (after any scatter, and again on
  the fallback when a scattered spot is unsafe) and `RiftPlotterBlockEntity.wardedExit` for a plotter. A new kind
  of warp that plans its own arrival and does not ask lands strangers in the middle of a base, and nothing
  fails. An escort never asks: it comes out where the flagship's search put it, and that search asked with the
  whole formation's reach. A free cruise, a Rift Gate and a Rift Pearl are not asked at all, on purpose - flying
  in is not a rift, and a gate needs a ring built at both ends. `WardWiringTest` reads the hook-up off the source.
- **Asking a ward changes nothing, and a ward is paid only once the flight is certain.** `turnAside` is asked
  for arrivals that are then refused for other reasons, so it neither spends a charge nor drops a field;
  `commit` does both, and in `beginWarp` it sits after the last `abort`. A ward that could not pay is not in the
  way at all - the rift goes where it aimed - and drops its field at the same `commit`.
- **A ward is not a clearance check.** A drive with `clearanceChecks` off is turned like any other, as
  `SHIP_FASTENED` still refuses one. Do not "fix" it into honouring the switch.
- **Nothing a pilot reads before launching knows of a ward.** The owner chose a deflection over a refusal so
  that a ward gives nothing away: no chart greys a warded anchor, `WarpValidator` and `WarpRules` do not import
  it, and the one refusal, `DESTINATION_WARDED`, is only for a field with no room anywhere round its rim. A
  scan of anyone it would turn *is* scrambled (`WardScans`), which gives a ward away to whoever scouts first - meant.
- **Whom a ward turns is its `WardMode` and nothing else - `WardAccess`, asked as `RiftWard.passes`.** Block
  all, allow the networks it names, or block only the networks it names; its owner passes in every mode unless
  `selfTest` ("Turn me too") is set, which is how one player tries their own ward. There is **no** operator
  exception and **no** anchor exception: the first version had both, and the owner threw them out (2026-10-07)
  for a rule a player can read off the panel. Do not add either back. A rift with no author (a drive with no
  `initiator`, a plotter with no owner) is in no network - turned by the first two modes, passed by the third.
  `WardWiringTest.nothingButTheModeDecidesWhoIsTurned` reads `Wards` for `WarpAnchor` and `hasPermissions`.
- **A ward's three radii are three numbers.** Set (what upkeep is paid on), standing (thinned by a storm; what
  a plotter and a scan meet), met (less the drive tier's `ward.penetration`; what a drive meets). A turned rift
  is kept its clearance - the hull's reach, or a formation's - from *every* field, not only the one that turned
  it: `WardField.settle` grows them all by it. Sized from `FlightGeometry`, never a world box.
- **No ward loads a chunk, and an unloaded one runs on its banked charge.** Its record in `WardRegistry` has
  its charge and when it was counted; `WardCharge.after` takes the upkeep since off whenever something asks.
  The block writes its record every second and on every change, and on loading takes its charge *from* the
  record (`adopt`) - the record is what a rift was charged against while the chunk was away. A record whose
  ground is loaded with no ward on it is forgotten (`Wards.live`); a block that finds its id recorded somewhere
  else is a copy and takes a new one.
- **A ward names networks by id, up to eight, and they need not be its owner's.** Shutting out a rival's
  network is half of what the third mode is for, so `NetworkRules.mayStay` does not apply to a ward and a member
  leaving a network changes no ward. Only a disbanding does: `WarpNetworks.detach` calls `Wards.forget` when
  `ownedBy` is null, which strikes the id from the record and calls `forgetNetwork` on a loaded block, and an
  unloaded block drops ids that no longer exist in `adopt`. The panel sends names, not ids
  (`ServerboundConfigureWardPacket`), and a name no network has refuses the whole edit - nothing is founded from
  a ward. A save from before modes (one `NetworkId`) loads as allowing that network, or as blocking everyone.
- **A turned ship's exit is not opened early.** `ENTER_CORRIDOR` normally opens the far aperture at once, as
  warning; for a turned ship it marks the fold's log and sends nothing, so nothing stands at the rim before the
  tear is dragged there, and the transit cue opens it as the hull crosses. An escort flying its own rift still
  opens its own early - a known rough edge, not fixed.
- **A deflection's scene is `WardChoreography`, on the client's clock.** One cue (`ClientboundWardCuePacket`, its
  own packet - it describes a track and a machine, not an aperture) as the hull enters its corridor, and nothing
  after. Every curve is continuous across every beat (`WardChoreographyTest`, a hundred samples a tick). Nothing
  in it pushes, pulls or hurts. The toll, the break and the drone are the ward's own sounds and are **not** in
  `WarpSoundSwitch`; the wrench the crew hear is.
- **The ward's totem is posed, piece by piece, and its x is mirrored.** `totem_2`..`totem_4` each hang from
  nothing and turn about the axis (a piece parented to another would be carried round by it); `WardWorks` gives
  each its lift, turn and drag. GeckoLib mirrors x as it bakes a model, so `shearX` is negated - given the sign
  as it stood the totem leans away from the rift it is catching along one axis. `rift_ward_model.py`'s
  `check_the_totem_goes_at_its_pull` and `WardRigTest` hold the sign; the script also checks every pose clear of
  the clamp ring, the chains and the pillars. Look at a relic's model before shipping it: the first ward was
  never looked at and came back. An offline render - every texel put through `bone_matrix` - is cheap.
- **A ward says it is on four ways, and all four read `enabled` and `standing`.** The glow layer
  (`WardWorks.glow`: off is dark, down gutters), the totem's motion (`WardWorks.Motion`: on opens it, standing
  turns it), the drone (`WardEffects.Hum`), and block light (`RiftWardBlock.LIT`, set by the block entity when
  `standing` changes - a plain `setBlock`, since the master has no shaft). The blockstate has to name both `lit`
  states. Round a standing ward `WardEffects` moves things the wrong way - motes up, dust in, a dark stain over
  the open top - and still draws no line, ring or outline: a force, not a display, like the cradle.
- **The ward's panel takes the camera, and every way out gives it back.** `RiftWardScreen` is a sidebar
  (`WardLayout`) that starts `PlotterCameraRig` in `init` and stops it in `removed`; it dims and blurs nothing,
  because the rim drawn while it is open (`WardEffects.rim`) is how a radius is set. A new way to leave the panel
  that does not go through `removed` leaves the player looking down at their ward with no HUD.
- **A Berth is asked through `Berths`, after the wards, and taken only once the flight is certain.** An anchor
  with a switched-on berth is a *station* and gives out berths and nothing else. `planPastWards` asks the wards,
  and a rift they let through at the anchor itself (`stationAt` - a scattered target is not it) is planned by
  `planBerth`: `Berths.offers` ranks the berths (`BerthAssign`: smallest that fits, then priority, then distance),
  `WarpFlight.planInto` plans the flight, `Berths.proof` proves the slip and its lane, and the next berth is tried
  when one is fouled. All of that changes nothing. `Berths.reserve` sits beside `Wards.commit`, after the last
  `abort`; `abort` gives the berth back and `completeWarp` moors the ship. A new kind of warp that plans its own
  arrival at an anchor and does not ask puts a ship down loose in the middle of a station, and nothing fails.
  The reservation is by berth id, kept on the drive and saved - a crossing changes the ship's own id on the way -
  and lapses by itself. `BerthWiringTest` reads every one of these off the source.
- **A berth is not a clearance check, and it is not a fastening.** A drive with `clearanceChecks` off is still
  given a berth; only `Berths.proof` is skipped. And a berth makes no joint - it holds a ship by commanding its
  velocity - so `Fastenings` knows nothing of it and it never refuses a launch: instead every way a sequence
  starts (`startWarp`, `followFold`, `startCruise`) calls `castOff` before it transitions. A new way to start one
  that forgets flies its run at the rift against a berth still pulling the hull back.
- **A flight has two attitudes and two bearings; ask `flight.attitude()`, never `arrivalOrientation()`, before
  the crossing.** A flight into a berth leaves at the attitude it had (`departureOrientation`) and is turned at
  the crossing, in the throat, by the one `relocate` every flight makes - or, after a rebuild in another
  dimension, by a second one in `finishCrossing`, since Dimensional Sable takes no attitude. `AttitudeHold` held
  at the arrival attitude from the start spins the hull round at its mooring. The far end is `WarpFlight.farEnd`,
  which turns the origin-to-centre offset with the hull and, with no turn, turns nothing: every other flight
  comes out to the last bit as it did (`TurnedFlightTest`). Seen in game: a quarter turn, crew aboard, on the mark.
- **A moored ship is held level, from the record, on the server's tick.** `BerthMoorings` steers every held ship
  to its mark with `Airship.steerVelocity` and holds its attitude with `AttitudeHold` each tick, and renews its
  `HoldPurpose.BERTHED` hold - never the berth's block, which ticks only while its chunk is loaded and would drop
  a ship kept loaded where nobody is. Level, because no hull floats quite level and one measured as it flew is a
  hair wider than it is (`BerthMooring.attitude`). Where it is held is worked out once and kept (`Station`), or a
  hull that rolled would be held a degree over; `Berths.changed` forgets it when the slip moves. Not there is
  not gone: a ship loads a moment after the ground it is over, so both "its ship has gone" paths wait
  `ABSENT_TICKS` with the ground loaded.
- **A berth's fit and its proof are the slip's own shape, never an upright box.** `BerthFit` reads the vessel's
  own box, turned to the attitude it will sit at, along the slip's axes with the arrival clearance on every side;
  `Berths.proof` reads the same swept shape through `SafeArrival.clearanceWithin`. So a ship that fits sweeps
  nothing outside its slip and lane, and a slip at forty-five degrees is not refused for its own quay. An
  ordinary arrival search skips any candidate inside a slip (`Berths.inASlip`).
- **A fleet at a station is one decision.** `BerthAssign.fleet` berths the largest ship first, all or none;
  `planFleetBerths` strikes a fouled berth off and asks again for the whole fleet; every slip is reserved at the
  flagship's commit; the muster hands each escort its berth (`berthFold`) and the escort plans with `planInto`.
  A fleet through one rift is refused - a rigid formation cannot be put down in slips.
- **A drive's cooldown is fixed as the warp completes, and everything that shortens it is asked there.**
  `completeWarp` takes the tier's figure through `RiftStorm.cooldownTicks` and then `cool`, which counts the
  turning Cooling Vents and the ice round the frame (`coolers()`, `DriveCooling`) and sets the drive's
  countdown, its progress bar and the hull's own `cooldownUntil` from the one result. Nothing shortens a
  cooldown already running - the console counts it down in seconds, and a figure that moved under it would
  make that a guess. At most `cooling.mostCoolers` count, vents before ice, so swapping ice for a vent never
  cools slower. A vent is counted only if it is turning (`isTurning`, the server's config) and agrees the
  drive is its own; whether one counts is sent to the client as a `VentStatus`, never worked out there.
  A new thing that cools a drive goes through `DriveCooling.fit`, or it stacks past the limit.
  `DriveCoolingWiringTest` reads the hook-up off the source.
- **A siphon never rolls for itself.** One draught per warp, poured into the vessels aboard in turn
  (`SiphonPool`), and rift rain is a per-chunk or per-ship budget per second (`RainLedger`). More siphons
  add room, not yield - the fissure's rule. Essence is a price now; a per-vessel roll would make it free.
- **A fissure is found, or torn by a Rift Sunderer - and nothing else makes one.** The owner allowed the
  Sunderer on 2026-10-16, overturning `docs/crafting-plan.md` decision 3 for it alone. `SundererBreach.tear` is the
  one place a fissure is set (`SundererWiringTest` walks the source for `RIFT_FISSURE.get().defaultBlockState()`),
  and what it sets is made **conjured** in the same method, before its first tick can roll it a reservoir from its
  position. A conjured tear holds `sundererFissureEssence` and nothing it rolled, **hands nothing to a siphon** (or
  tearing one and draining it is a loop that pays), **seals with no Scar Heart** (the Singularity stays gated on a
  found scar), and **gutters shut** `sundererUnsettledMinutes` after it is torn unless a wellhead holds it - a
  frame letting go restarts its clock for a whole window. Its small reservoir is also what a wellhead reads for
  `tolerance()`, so a home reactor runs at the hottest a fissure can, and bleeds dry fast if it is run torn.
  `AWItems.RIFT_FISSURE` stays creative-only for setting up a build or a test (the owner asked, 2026-10-09):
  no recipe, no loot table, and a fissure drops nothing; `ResourceIntegrityTest` holds both absent.
- **A fissure never changes a block but its own, and refuses to move.** It once seeded Warp Crystal Ore; that
  is retired, and `RiftFissureTest` reads the block entity for any `setBlock`. Sealing spawns one Scar Heart
  with an unlimited lifetime - never a loot table. Its `BlockMovementChecks` refusal is what keeps a found
  scar where it was found, since an open fissure is a free rifting catalyst.
- **A fissure is marked harnessed by `WellheadAssembly` and nothing else, and a harnessed one is the well's.**
  `RiftFissureBlock.HARNESSED` is in the state because the fan (`RiftingType.isCatalyst`) and the outline are
  handed a state and nothing more: a harnessed tear rifts nothing, drains into no siphon, and is seen and
  targeted by everyone. The fissure still sets no block but its own - the wellhead flips the state - and has two
  ends, `close()` (a heart) and `consume()` (a meltdown: none). `RiftFissureTest` holds all of it.
- **A block of a wellhead's frame finds its machine through the fissure.** A part carries its offset from the
  tear (three numbers, 0..6 for -3..3) and the tear carries its collar's position, so a frame of any size with
  its collar in any side needs no more state than a three-cube. The collar must be let go (`setRadius(0)`)
  before its parts are swapped back, and told its radius before they are set down, or each part breaks the
  frame open or takes itself for an orphan; `WellheadWiringTest` reads both orders off the source. What a part
  was is a rule of where it stands (`WellheadStructure.cell`), never stored.
- **Warp Glass is on the tripwire layer, not the translucent one - so a rift shows through it.** A rift's
  membrane is drawn at `AFTER_TRANSLUCENT_BLOCKS` against the depth translucent blocks have already written, so
  Warp Glass in front of a tear hid it (seen in game, 2026-10-09). The tripwire layer is drawn after that pass,
  so the glass blends over the tear instead; the owner asked for this everywhere, not only in a wellhead's
  frame, whose glass parts use the same model. The price, accepted: Warp Glass seen *through* water or stained
  glass is not drawn, as vanilla tripwire is not. Do not "tidy" `warp_glass.json` back to `translucent`. Water
  and stained glass still hide a rift. Seen in game: the face showed through and the rings did not, because the
  light pass (`AFTER_PARTICLES`) comes after the glass has written depth. So a rift marked `underGlass` - a
  fissure, each tick it is held, and nothing else - has `RiftEffectManager.drawLight` run `early`, straight after
  its face and before the tripwire layer. Every other rift's light is still after the particles and is still
  lost behind Warp Glass; moving them all would change how every rift looks. The early pass is not yet seen.
- **A wellhead has one shaft, on the collar, looking out.** The relics' gearbox bug again: a second shaft
  anywhere in the frame passes rotation through it. Ports have block entities only because Create's pipes and
  funnels will not join a block without one, and are registered by block, not block entity type, because they
  hold no tank for `CapabilityCoverageTest` to find.
- **A well settles or runs away, and which is `WellheadReactor`'s arithmetic, not a threshold.** Pressure rises
  with the square of strain and damping only with strain itself, so there is a lower meeting where a kept well
  settles and an upper one past which it cannot come back. Changing `wellheadPressure`, `wellheadFeedback`,
  `wellheadYieldGain` or `wellheadDamping` moves both; `WellheadReactorTest` runs wells for minutes to prove a
  kept one settles calm, a thin cage settles higher and pays better, and none holds without rotation. Fifteen
  on a comparator means exactly critical (`WellheadSignal`), the bargain `DriveSignal` keeps with fourteen.
- **A meltdown is a record in the level's data, not anything its collar does.** The frame is the first thing it
  destroys, so `Meltdowns` (a `SavedData` per level, ticked from `LevelTickEvent`) plays it out: it holds its own
  ground through `Holds.holdGround`, waits while that ground is unloaded, and resumes after a restart from its
  saved count. When is `MeltdownChoreography` (pure, read by both sides, every curve continuous - a hundred
  samples a tick in `MeltdownChoreographyTest`); what is taken is `CraterPlan`. The ground only goes inside the
  detonation beat, a shell a tick, set with `UPDATE_KNOWN_SHAPE` and the crater's wall told once at the end
  (`settle`) - tell the neighbours as each block goes and a large crater is tens of thousands of updates. Every
  block it takes is offered to `ExplosionEvent.Detonate` first, which is all the hearing a land-protection mod
  gets. It never reads an unloaded position, never touches plot space, and takes no block entity as debris.
- **What has to be found in a meltdown is drawn dark, and the light is trim.** The first scene was nearly all
  additive light at a third the size, and the owner saw almost nothing of it at noon (2026-10-09). Additive
  light cannot draw dark and barely shows against a bright sky - the plotter's first dock guide went the same
  way. So the tear, the funnel, the heartbeat's rings and the front of the ground's going are solid, on
  `RIFT_MEMBRANE`, and `RIFT_FIRE` is laid over them. A new piece of the scene that matters goes on the solid
  pass first. `/aerowarptics wellhead preview i|ii|iii` plays the scene dry - no block changed, nothing moved
  or hurt, no zone, no wound - which is how it is looked at; with the trace on the client says what it was told
  and whether it drew a frame.
- **The largest crater is nearly half a million blocks, and nothing about it is done in one tick.**
  `CraterPlan` packs an offset into an `int` (seven bits a side, so 63 is the furthest a crater reaches) and
  keeps a radius's shells until the last meltdown ends. The ground goes by `wellheadCarveBudget` a tick; what
  the scene's pace asks for past it is carried into the fallout, and the lining and the settling of the wall
  take their turn under the same budget. `carve` eases out so the great outer shells go slowest. The hold is
  `Holds.holdGround(level, pos, chunkRadius)` out to 1.35 radii - held by the tear's one chunk, a large crater
  skipped everything past it. Relighting that much ground has not been measured; the budget is the dial.
- **A Rift Wound looks wrong on purpose, and is not drawn as a rift.** The owner asked for "corrupted, evil and
  not meant to be" (2026-10-09), after a first version that was a dull closed aperture under a column of light -
  which read as a tidy, dimmer rift. So `WellheadSpectacle.drawWound` draws it alone: a crooked black crack on
  the solid pass that holds a shape for three ticks and is then another with nothing between, threads that
  reach out and are elsewhere next beat, an arterial-red ghost of its last shape slipped to one side, and a
  thread of dark from the sky where a landmark would have a beam. Smoke sinks into it and embers are drawn in.
  Do not smooth its motion, give it the mod's violet, light it, or route it through `RiftEffectManager.hold` -
  each makes it one of the things it is meant not to be. Drawn whatever `wellheadSpectacle` says. Not seen in game.
- **A Rift Wound is left by a meltdown and by nothing else, and is spared by being unbreakable.** It stands
  where the fissure was, for good: no item, recipe or loot, no collision, no light, visible to all. `mayTake` spares
  it for its destroy speed, not its name. The frame's survey looks for a fissure, so no wellhead forms round
  one. The crater's ground left hanging (`CraterPlan.islands`) is rolled from a seed the record saves, so a
  resumed meltdown leaves the blobs it meant to.
- **A Sunderer spends nothing on a refusal and everything at the edge.** `fire` asks `SundererSurvey.full` - aboard a
  ship, the ring and its size, the charge, the speed, then what is round about (scar zones, other tears within
  `sundererSpacing`, anchors within `sundererAnchorSpacing`, a standing ward's field, the dimension list) - and
  drains the tank only after it answers. Round-about checks read only chunks already loaded; an unloaded one in
  reach is taken as clear. Before it commits (the reaching), a stopped shaft or a broken ring **misfires** and
  hands half back; after, a ring broken **ruptures** at once and a shaft stopped for `STALL_LIMIT` ticks does too.
  The breach is past stopping. The rupture's crater, the burst frame and the hand's take each go through
  `WellheadMeltdown.permitted` (land protection, the meltdown's own check, now shared) and never touch plot space.
- **A sundering is drawn from a seed, and two things in it are each client's own.** Which third of the ring
  bursts, how each of those blocks unravels (`Unravelling`), the order the seed swallows them and which block a hand
  takes are all rolled from the seed the cue carries, so every client draws the same scene and a latecomer joins it
  half-way. The hand comes out on the side the watching player stands and turns its palm to them if they are
  within eight blocks - everybody watching is looked at - and every sound is played by the client on the scene's
  clock. A tear's last few ticks are the client's alone: the Sunderer that would have told them is gone at the
  moment the seed lets go.
- **Nothing flat is drawn across a Sunderer's opening (the owner, 2026-10-17).** The first scene was a membrane with
  cracks on it, sent back as "just a 2d plane". Now the opening stays open: a point that searches with starlines,
  the black seed it becomes (a ball with a violet limb, on the solid pass), a `GravityLens` round it that bends the
  standing frame, and the burst third of the frame as real block models (`renderSingleBlock`) lifting, orbiting
  over a dark dust disc (the shard pass, translucent, so it reads by day) and swallowed one a beat of the seed.
  Under a shader pack there is no lens; a dark halo stands in. `SundererWiringTest` fails if a membrane comes back.
- **The burst third of a Sunderer's frame leaves the world as it fires.** Lifted through land protection after
  the survey and the drain; the ring that must stay whole from then on is what still stands; a misfire, or a
  Sunderer broken before it commits, puts them back into air only. The clients drew them in place until their
  turn, so neither the lifting nor the putting back is seen to jump.
- **Create's rotation stops under `/tick freeze` and `tick step`.** A real sundering stepped frame by frame reads
  speed 0 and stalls: it misfires while reaching and ruptures after (seen 2026-10-17). Look at the scene frozen
  through `/aerowarptics sunderer preview [hand]`, which needs no shaft, and run real ones unfrozen. A reaching
  Sunderer now rides out `REACH_STALL_LIMIT` ticks of no speed, as a straining one rides out `STALL_LIMIT`.
- **The hand is posed by code and drawn by code.** `RiftHandPose` is a skeleton in the plane's own frame, carried
  round with the arm by the shortest turn from rest - never worked out from "up", which flipped the palm over in a
  tick when the arm passed upright (caught by `RiftHandPoseTest.noJointJumps`). Its elbow never leaves the slit;
  the forearm stretches. `RiftHandRenderer` draws tubes in flat colours with no texture, so there is nothing to
  paint over - change the colours there. It is pale on purpose: black, its first colour, vanished against the black
  it comes out of (seen in game, 2026-10-16). It comes out of the seed's edge in the ring's plane, on the side of
  the block it takes; what of the arm is still inside the seed, the seed's own depth hides.
- **A shake added every tick settles at seven times what is added.** `WarpScreenShake` decays by 0.86 a tick
  and caps at 1.5, so `add(0.1)` each tick is not a tenth of a shake, it is 0.7 of one, held. The meltdown's
  first figures pinned the view to the cap for the whole swell and again through the detonation, and the owner
  found it too disorientating (2026-10-09). It now adds 0.02 of `MeltdownChoreography.shake` a tick, a tenth
  at each heartbeat and 0.6 once at the detonation, all scaled by the client's `wellheadShake`. Work out the
  settled figure before adding a per-tick shake anywhere.
- **A client runs a meltdown on its own clock and is put right once a second.** `ClientboundMeltdownCuePacket`
  says how far through the scene is, not what to do next, so a latecomer joins half-way; `WellheadSpectacle`
  keeps the start tick and draws from the camera, since there is no block entity left to draw from. A new thing
  the scene shows goes in the choreography, never as a one-off packet, or a latecomer never sees it. The player
  is pulled by their own client; the server pulls everything else.
- **A scar zone is a record too, and loads nothing.** `ScarZones` bears once a second on what is living in
  ground that happens to be loaded; `ScarZone` (pure) is how it fades. Players near one are told once a second
  and forget it when the telling stops. Without Rift Goggles it draws no edge - motes, a dim sky and a ticking.
- **A wellhead's panel works nothing out, and an answer to an order never opens it.** Every figure on it is the
  server's config at work, so `ClientboundWellheadPanelPacket` carries them all. Only the packet sent for a
  click on the machine has `open` set; the refresh and the orders are answered with it clear, or a panel
  closed a moment ago would reopen itself. Orders are honoured for a formed collar within reach and nothing else.
- **An item property is clamped to 0..1.** `ItemProperties.register` takes a `ClampedItemPropertyFunction`,
  so a Stormglass's reading is its ordinal over four, and the model's overrides are 0.25/0.5/0.75. Return the
  ordinal and every reading past the first draws as the last.
- **The old ladder is a data pack, not a switch.** Recipes load before a world's server config and there is
  no common config, so `datapacks/classic_recipes` overrides the changed recipes by path, registered with
  `PackSource.FEATURE` so it is offered and left off. `BUILT_IN` would switch it on everywhere.
  `RecipeBalanceTest` prices both books.
- **Naming a `DeferredHolder` loads its registry class, and that needs a bootstrapped Minecraft.**
  Writing `AWBlockEntities.RIFT_DRIVE` anywhere that a test will reach loads `AWBlockEntities`, whose
  static initialiser calls `DeferredRegister.register` and dies with "Not bootstrapped". The Display
  Link sources hold `() -> AWBlockEntities.X.get()` rather than the holder itself for exactly this
  reason: the lambda body is not loaded until it runs, which keeps the whole source list testable.

---

## Conventions

**Packets.** Every payload is a record with a `Type`, a `StreamCodec`, and a static `handle` that
calls `context.enqueueWork(...)`. Never touch game state directly from a decode. Every `readUtf` is
length-bounded and every array read is range-checked before allocating — a decode must not let a
sender make the receiver allocate arbitrarily. `AWNetwork.VERSION` is bumped when the wire changes.

**Config.** Everything tunable lives in `AWConfig`, never as a constant in the code. Server config is
in `aerowarptics-server.toml`, client-only presentation in `aerowarptics-client.toml`. Client code
must not read server-only values directly — the packet carries them. The server file is a NeoForge
*server* config, so it is per world (`saves/<world>/serverconfig/`); a copy in `config/` is never read.
Tell players that, not "the config folder".

**Failure.** Refusals are enum values (`WarpFailure`, `GateFailure`) with translation keys, surfaced
to the player as a named reason. A warp that cannot proceed is refused *before* anything moves. The
airship is never damaged or deleted by a failed warp. A drive's `reject()` tells nobody - it only records
`lastFailure` for the console - so anything that asks a drive from somewhere other than its console (the
beacon, the Fleet Board) has to send the refusal itself. The beacon assumed otherwise from 1.3.0 until
1.3.1, and every summon the drive turned down was silent.

**Trace.** Every refusal, and every decision that is silent to the player, writes a line through
`Trace.log` (server) or `ClientTrace.log` (client) - with the numbers that decided it, not just its name,
because most reasons have more than one cause. A drive refusal goes through `reject()`, which already
attaches `RiftDriveBlockEntity.traceState()`; a new path that returns a failure without `reject()` or
`abort()` must trace itself. Anything asked on a timer (a chart refresh, an access check) uses
`Trace.changed` so it logs once per change, not once per ask. Build expensive arguments only behind
`Trace.enabled()`. Client code never reads `debug.trace`; `ClientboundTracePacket` tells it. The one
switch is `debug.trace` in the per-world server config, also `/aerowarptics trace on|off|report`.

**Comments explain why, not what.** This codebase has a distinctive and consistent voice: prose
sentences, British spelling, explaining the reasoning and often the bug that motivated the code.
Match it. Do not add `// increment the counter` noise, and do not strip the existing explanations —
they are the design record.

**Prose style.** Em dashes and hyphens both appear; British spellings throughout (`colour`,
`normalised`, `behaviour`). Player-facing strings favour plain language over the mod's own jargon —
the Probe's UI says "Scan ahead", not "Throw sounding", deliberately.

---

## Current state

- **The unreleased version is 1.4.0.** It was called 1.3.1 until 2026-10-12, when the owner renamed it for its
  size: commits headed "1.3.1" on `main` are part of it, and the changelog has no 1.3.1 section. Anything newer than
  those commits is whatever `git status` shows.
- **A first pass in a running game was made on 2026-10-10**, one player, driven through the Minecraft MCP
  server in a flat creative world. It supersedes "not yet seen in game" below for exactly these, and for
  nothing else. *Worked:* the cradle (panel, Begin, a fold of eight, a full collapse that spat and threw
  everything back); the plotter (table, survey, tear, a player carried, exit opened on approach and healed);
  the ward (charging, panel, a plotter's survey scrambled, a plotter's exit and a Mk I ship's warp both
  turned to the rim and paid for; a Creative drive passes, as its penetration says); a tier I wellhead
  (forming, panel, digesting from a hopper, the dry preview, a real meltdown, crater, Rift Wound, scar zone,
  refusal to form again inside the zone); gates (dial, a player's passage in Down the Pipe, held three
  seconds and let go); networks (founded from an anchor, members panel, a refusal said in the panel, the
  chart's filter); ship warps by Creative and Mk I drives with crew aboard, a Cooling Vent halving a
  cooldown, a crossing into the Nether and back, a Rift Storm's sky and rain, and rifting at a lit brazier.
  *Not tried:* sound, flotillas, large or jointed hulls, a vehicle through a gate, wellhead tiers II and III,
  and how any model or scene reads - a screenshot every few seconds is not watching one.
- **A second pass on 2026-10-11** used the MCP server's later tools - a restart that installs a fresh jar, a
  fake second player, capability and loot probes, packet capture. *Worked:* the fixes from the first pass
  (the cradle panel's figures, the Scar Heart's strip, rotation after a crossing); a ship saved and loaded
  across a restart; the residency hold (`keepAirshipsLoaded` on: an uncrewed ship stayed loaded in a Nether
  with nobody in it, its drive ticking; off, Sable files it away within a minute, as the option says); a Rift
  Beacon's summon of that uncrewed ship from 264 blocks (bound by component - the sneak-click bind was not
  tried); a free cruise (mode switched at the console, engaged and stopped by redstone, crew kept aboard); a
  second player through a gate (both players told of the passage, damage refused in it and taken after, a
  traveller who moved put back as it ended); a second player added to a network from the members panel; the
  operator commands refused to a player without permission; `chests/rift_scar_cache` giving a Relic Armillary
  0.378 of the time over a thousand rolls; and every block entity of the mod's costing under a tenth of a
  percent of a 3 ms tick. *Seen and left:* automation may put anything in the cradle's arms and cup and take
  only from the tray, so a funnel can jam it with dirt; a pipe may draw essence back out of a cradle; the
  crew are sent `ClientboundSettlePacket` twice a tick while a hull emerges.
- **A third pass the same day** went down what the first two had not tried. *Worked:* a two-ship flotilla
  (station taken, Sound Stations, harmony, a fold with Rift set to One, both ships landing in formation), and
  the same flotilla folded by redstone into the End; a 585-block uncrewed hull summoned by beacon twice with
  no `INTERRUPTED`, the first time from unloaded; an uncrewed vehicle through a gate, and one caught, lined
  up and launched by a plotter's dock; wellhead tiers II and III with scram, comparator, port rules and an
  unattended runaway; a settled collapse making a Singularity Core; Singularity, Mk II and Mk III drives; a
  launch refused for a wall ahead; a vent and two ice together (2430 of 6000 ticks); siphon, fissure sealing
  to a Scar Heart, goggles, wave detector, modulator panel, chute pair, probe scan, pearl hop, stormglass,
  handbook; network removal, rename and disband; and no rotation passed through a relic. *Fixed:* the
  escort panel's charge bar overdrawing "Drive Charge" (built, not yet looked at). The full list of what is
  and is not checked was written up for the owner as a Claude doc, "AeroWarptics in-game test status".
- **A stability pass before the dev build, 2026-10-14**, on the jar as it stood. *Worked:* every recipe loaded
  (53, Create's and Simulated's ingredients resolved); `validate_mod` clean; each new block placed and mined by a
  survival player dropping exactly itself; the three relics placed whole and broken from a part, a base and the
  master - one item each, nothing left standing; TNT on a cradle and on a formed tier I wellhead dropping exactly
  what it destroyed, the collar's seated crystal included; relics wiped by `/fill` leaving their registries; every
  block entity of the mod together under 0.3% of a 3.5 ms tick; all of them, a formed wellhead and a linked berth
  across a save and reload. The one bug found is the berth fit in the Berth bullet below. Recipe prices were read
  against `RecipeBalanceTest`'s model and left as they were; that test now also holds the relics between the Mk I
  and the Singularity, a berth under its anchor and a coupler under half a Mk I.
- **The crafting layer (`docs/crafting-plan.md`, phases 0-5) is built and unit-tested, not yet seen in
  game** - rifting, the Seal, folding, the Collapse and storm work, with fissure ore retired.
- **Networks (`warpnet/`) are built and unit-tested, not yet seen in game** - the third access mode, the
  members panel, the list filters, gate ownership and the operator's commands. The four screens that
  changed can only be judged by opening them.
- **Gate passages are built and unit-tested, not yet seen in game.** The timing, the countdown and the
  wiring are tested, and all six shaded styles compile, hold the veil, and have been looked at as contact
  sheets. Whether a held hull sits still, the three protection events, hiding a traveller from other players,
  the dial's button, and how any style reads in motion and full screen can only be judged by going through a
  gate.
- **The Rift Wellhead (`docs/rift-wellhead-plan.md`) is built except for its model, and unit-tested; only the
  unformed frame has been seen in game.** The frame, the reactor, the panel, the Display Link source, JEI, the
  meltdown and its scene, the exclusion zone, a Ponder scene, two handbook pages and three advancements are in.
  **Not built:** the generated GeckoLib model - a formed frame still looks like its casing and glass, and the
  collar, the ports and Scarred Stone wear placeholder textures. The panel is plain vanilla drawing, not the
  house style of the other screens. The meltdown, the zone, the panel and every sound can only be judged by
  running one: `/aerowarptics wellhead meltdown` sends the nearest formed well over. The wiki is not updated.
- **The Rift Ward (`docs/rift-ward-plan.md`, every phase) is built and unit-tested, not yet seen in game.**
  The arithmetic, the registry, the wiring and the scene's timing are tested. Whether a field turns a real ship,
  how the scene reads from the ground and from a deck, the weight's poses, the panel and the rim guide can only be
  judged in a running game - and it stands on the plotter, the networks and the relic floor, which have not been
  confirmed running either.
- **The Berth (`docs/berth-plan.md`, phases 0-8) is built and unit-tested, and partly seen in game** (2026-10-12,
  one player, flat creative world, through the MCP server). *Worked:* placing a berth by hand and its link to the
  placer's anchor; the dry run naming the berth, and naming `berth_too_small` with the numbers; a Creative-drive
  warp that turned a nine-block hull a quarter turn into the slip, crew aboard, to within three hundredths of a
  block of its mark; the clamp holding it still and level for two hundred ticks; the `berthed` hold; cast off by
  redstone; the tractor catching and mooring a ship lying in an empty slip; the panel (camera, slip and lane
  guide, all three tabs, Save). *Found and fixed there:* a hull floating a seventh of a degree over was refused
  a slip built to fit it (berths now moor level), and a cast-off ship was taken for gone on the first tick after
  a restart and caught again (both "gone" paths now wait - and a restart after that fix left it lying cast off,
  as it should). *A pre-release pass on 2026-10-14* found a hull tilted two ways at once (0.6 degrees of pitch and
  a trace of roll) refused a slip it fitted exactly - its bow, levelled in the world and brought back into the hull,
  was a hair off its own axis, so it was turned into the slip that hair askew (`BerthMooring.attitude` now snaps
  the bow to the ship's nearest level end). After the fix the same uncrewed hull warped in, moored level, stayed
  held and loaded with the player eight kilometres away, kept its mooring across a save and reload, and was cast
  off by redstone. *Not tried:* a full or forbidden
  station, a fouled lane, stern first, a slip that is not square to the compass, a second player and networks,
  a fleet into berths, a crossing into another dimension, a shader pack, and how the turn reads from the deck. *Not built:* see the note at the top of the plan. The
  wiki is not updated.
- **The Rift Bond and whole-vessel sizing are built and unit-tested, not yet seen in game.** The block, its
  tuning, the ledger and the box arithmetic are tested. Whether bonded sections really go through together,
  how a sectioned ship's larger rift and landing behave, and the block's look can only be judged in a running
  game. It has a JEI page but no Ponder scene, handbook page or advancement, and its texture is a placeholder.
  A flotilla escort built in sections is still measured by its own hull only (`SafeArrival.Companion`,
  `exactly`), as are a free cruise's sensor, gates and the plotter.
- Remote is `https://github.com/ICEconchy/Aerowarptics.git`.
- Licence is CC BY-NC-SA 4.0 (`LICENSE`, `mod_license` in `gradle.properties`).
- `Depends/` is **gitignored** and was purged from history before the repo went public. The README's
  "Building" section lists the jars to put there. Never commit a third-party jar again.
- Nothing machine-specific belongs in a committed file. `deploy_dir` is blank in `gradle.properties`
  and set per machine in `~/.gradle/gradle.properties`; write paths as `$HOME/...`, not a user name.

- **The Rift Sunderer was built on 2026-10-16** (`docs/rift-sunderer-plan.md`): the machine, the conjured fissure,
  the scene and the hand, the rupture, the advancements, a handbook spread, a Ponder scene and JEI's page. It is
  unit-tested, and the same day was tried in game through the Minecraft MCP (flat world, one player). *Worked:* a
  5x5 ring found and surveyed, refused until charged, fired by redstone, the violet membrane filling the ring
  exactly, going black, pale cracks spreading and meeting (readable at noon), the breach bursting 7 of 23 frame
  blocks and leaving a conjured 3000 mB fissure with *Where It Went Wrong*; a ring broken mid-straining ruptured
  (crater lined with Scarred Stone, the whole ring gone, *Too Thin*); a charged Sunderer 20 blocks from that tear
  refused with `near_tear` and kept its charge; the unheld tear guttered after 20 minutes and left no heart; the
  hand in `/aerowarptics sunderer preview hand`, frame by frame (it was black and invisible on the membrane, and was
  made pale). *Not tried:* a misfire, the hand's take of a real block, a wellhead round a conjured tear, sound,
  and how any of it moves - stills only.

- **Rail gates were built on 2026-10-18** (`docs/rail-gates-plan.md`, which has a status section and where the build
  differs): Create trains through Rift Gates by way of the Rift Corridor dimension.
  - *Seen in game* (flat world, one player, one scheduled train with a Blaze Burner conductor):
    - track run into an open gate and a shut one becoming a lane;
    - the corridor, stub and apron;
    - sockets holding the track through a hang-up;
    - the train held at a shut, unloaded gate and let through, with the gate shut again after the linger;
    - the trip back, the far gate dialling without changing its route;
    - dialling ahead;
    - a rider (*Mind the Gap*);
    - a route into the Nether set with **Set Route**;
    - the redstone hold;
    - a restart with a train in the corridor.
  - *Found and fixed there:*
    - half a corridor never in Create's graph;
    - every socket removing itself;
    - dial-ahead never firing;
    - every world calling itself experimental;
    - the Trains tag not fitting.
  - *Not tried:* the rescue, a refused hang-up, a too-long carriage, several lanes, a gate aboard a ship, and the
    corridor as seen while riding. Create stations on track whose graph a lane changes sit out of the graph until their
    own chunk ticks; the far end's hold covers the near ones. Never `/ride` a carriage when testing: Create crashes at
    the portal on a passenger with no seat.

- **The Sunderer's scene was redone on 2026-10-17** at the owner's word: the ring unravels round a black seed, and
  the newborn tear is seen by everyone for ten seconds. *Seen in game:* the point and its starlines, the lens bending
  the frame, the lift, the orbit, the swallowing and the implosion, frame by frame through the preview; a real run
  of 35 s ending in a fissure seen without goggles, which faded to goggles-only after ten seconds. *Then, the same
  day, real runs (stills only, the tick rate slowed to 2/s for the hand and the rupture - which Create turns
  through, unlike a freeze):* a **misfire**, the motor taken away 6.5 s in: after `REACH_STALL_LIMIT` ticks the
  burst 7 were back, the ring whole (23 of 23), the Sunderer standing, 2000 mB in its tank, and nothing left drawn;
  **the hand in a real run** (`sundererHandChance` set to 1.0 and back), three times: out of the seed with its
  fingers splayed, its arm reaching to the standing frame and closing round a block, the take at tick 556 removing
  that block (*Something Waved Back* at 420), and the block in its fingers going back to the seed, partly behind an
  orbiting block; **a rupture mid-orbit**, a standing block broken 22 s in: the three still orbiting flung up and
  out and breaking into grit, the ring gone, a crater lined with Scarred Stone, a scar zone of 7, nothing dropped;
  **a tier I wellhead round a conjured tear**: it formed, *Home Fires* and *Tapped the Wound* fired, the tear's
  clock stopped (`Unsettled` -1), and breaking a casing restarted it for a whole window (break + 24000). Every
  Sunderer sound is a vanilla event that resolves, and none was reported missing; *how any of it sounds is
  unheard*, and nothing of how it moves was judged - each screenshot costs about five seconds of game.
  The client trace "has drawn no frame by tick 41" was a false alarm on every run (it waited for the seed, which
  is born at tick 120) and now means the pass reached the scene.

### Known issues

See **`docs/remediation-plan.md`** for the full audit findings and a phased fix plan. The two that
matter most:

1. **More than 256 visible Warp Anchors throws `EncoderException`** when any player opens an
   Astrolabe. `quoteAll` is uncapped against a `list(256)` codec. Reachable in normal play.
2. **The `INTERRUPTED` stall on large hulls.** The drive stops ticking at the crossing. Survivable —
   the ship arrives, `CrewManifest` places the crew — but real. Likeliest cause: Sable unloading the hull
   at the crossing because the ground under it was not block-ticking. Now doubly addressed, neither yet
   confirmed in game: the arrival ticket reaches where a long hull reappears, and every running sequence
   holds its ship with Sable's own ticket (`FLIGHT`), which Sable honours wherever the hull is put. The
   older hypothesis — Sable round-tripping the block entity through NBT on every relocate — is the
   fallback. See the remediation plan.

The per-tier `instability` value is read when the rift opens (`RiftDriveBlockEntity.beginWarp`, via
`RiftStorm.instability`, which adds the Singularity's figure to the other drives during a Rift Storm).

---

## Documentation map

| Where | What |
| --- | --- |
| `README.md` | The design record. Long, detailed, explains *why* every mechanism is as it is. |
| `CHANGELOG.md` | Player-facing release notes, one section per version. Only what changed in game, in short plain sentences - no internals, no flavour text. |
| `docs/mod-description.md` | CurseForge / Modrinth page copy. |
| `docs/remediation-plan.md` | Audit findings and the phased fix plan. |
| `docs/feature-ideas.md` | Candidate features, weighed against the mod's own invariants. A menu, not a plan. |
| GitBook wiki | Player and server-admin docs, 17 pages. Draft change request, **not yet merged**. |
| In game | The Navigator's Handbook (`guide/GuideBook.java`) - the step-by-step walkthrough, which is the one thing Ponder, JEI and the advancement tree each cannot be. |

The README is the source of truth for design intent; the wiki is the source of truth for how a player
uses it. Keep player-facing wording consistent between the wiki, the lang file and the JEI pages.
