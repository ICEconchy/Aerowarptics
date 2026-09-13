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
| Version | `1.3.1` (unreleased) |
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.233 |
| Java | 21 |
| Package root | `uk.co.iceconchy.aerowarptics` |
| Hard deps | Create 6.0.10, Create: Aeronautics 1.3.0 (Sable 2.x + Simulated 1.3.0), GeckoLib 4.8.0 |
| Soft dep | JEI 19.x (client only) |

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
  airship/            Airship wrapper over Sable, per-ship persistent warp data
  drive/              Rift Drive block entity, tiers, state machine  ← largest class here
  warp/               flight planning, cost, validation, arrival search, crew handling
  anchor/             Warp Anchors and the server-wide SavedData registry
  astrolabe/          3x3 chart table, terrain survey, vanilla-matching map palette
  probe/              Rift Probe: scans unvisited ground, produces a "fix"
  gate/               Rift Gates: free-form ring portals, crossing detection
  fissure/            Rift Fissures, the goggles that reveal them, and the drain policy
  guide/              the Navigator's Handbook: what it says, and the item that opens it
  siphon/             Spatial Siphon, collects Rift Essence from completed warps
  weather/            Rift Storms: the clock, what they do to a drive, and /weather rift_storm
  mixin/              client mixins - only the Rift Storm's sky, clouds, daylight and rain level
  network/            all packets (see conventions below)
  registry/           blocks, items, block entities, capabilities, creative tab
  advancement/        custom criteria triggers
  compat/             Ponder scenes, the JEI plugin, and the Display Link sources
  client/             renderers, effects, and the eight screens
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
Do not try to resolve them from a repository.

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
| `python tools/probe_textures.py` | the Rift Probe's block textures |
| `python tools/handbook_texture.py` | the Navigator's Handbook's item texture |
| `python tools/goggles_texture.py` | the Rift Infused Goggles' item texture |
| `python tools/geo_item_models.py` | every block's `models/item/*.json` |
| `python tools/rift_scar_structure.py` | `data/aerowarptics/structure/rift_scar/*.nbt` |
| `python tools/chute_model.py` | the Rift Chute's geo, animation and textures |
| `python tools/rift_drive_model.py` | the Rift Drive's geo, animations and all five tier textures |
| `python tools/rift_portal_texture.py` | the Rift Portal's three textures - idle, opening, closing - and their `.mcmeta` |
| `python tools/rift_rain_texture.py` | the Rift Storm's rain texture |

`chute_model.py` and `rift_drive_model.py` generate a block model, its animations and its texture sheet
together, for the same reason: a GeckoLib cube's UV rectangle is derived from its *size*, so the
geometry and the texture are one decision written twice. Generated, they cannot drift, and the
resource-integrity test can prove the UV boxes do not overlap. `rift_drive_model.py` also refuses to
write a drive whose gears would turn through anything, which `DriveClearanceTest` then re-derives from
the shipped file.

`rift_portal_texture.py` writes three strips off one field: the calm idle shimmer, and an opening and
a closing strip that multiply it by a seamless breathing pulse - see `RiftPortalStage` for why that is
a looping motif rather than a literal progress bar tied to a real gate's timing.

Change the script, re-run it, re-run the tests.

---

## Testing

65 test classes, **607 tests, all passing**. The suite is the main safety net and is unusually load-
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
- **A `WarpCourse` is an anchor or a fix, never both and never neither.** Enforced in the
  constructor. A probe's fix has no anchor at the far end and never will.
- **Redstone is the only way to launch.** Courses are set at an Astrolabe (where permission is
  checked); a rising edge on the drive replays that decision and never makes a new one.
- **Rift glow is additive, so nothing drawn in it can be dark.** `RIFT_FIRE` adds light; a black
  stroke there adds nothing and is simply absent. Darkness in a rift lives in exactly two places: the
  opaque membrane (face and bore) and the translucent shard pass. A singularity is therefore a black
  *face* from the palette with gold light round it, never a black disc in the glow. Glow colours are
  also clamped at 1 in `glowVertex`: the buffer stores each channel as a byte, and before the clamp a
  `lit` of 1.6 wrapped round to a dim colour instead of saturating.

- **A block entity gets at least one tick after its block has gone.** Sable does not tear a plot
  down a block at a time: releasing one wipes its chunks to `void_air` while the block entities stay
  in the ticking list. Anything that touches the level from that tick — a `sendData`, a `setBlock`, a
  `removeBlock` — asks Sable to change a block in a plot with no holder, and it throws
  `UnsupportedOperationException: Cannot change blocks in nonexistent plot holder` **on the server
  thread**. Every ticking block entity here therefore opens with `if (Airship.orphaned(this)) return;`
  **before** `super.tick()` — before, because Create runs `initialize()`, `lazyTick()` and every
  behaviour from inside `super.tick()`, and those touch the level too. Machines that resolve an
  `Airship` first were already safe by accident, via `isActive()`; the Rift Fissure was not, because it
  is a worldgen feature with no airship to ask about, and it is the one that crashed.

- **Never put a vanilla chunk ticket on plot coordinates.** A ship's plot chunks are Sable's: it serves
  them itself and parks its own `PlotChunkHolder`s in vanilla's `ChunkMap`. Sable cancels the
  four-argument `ServerChunkCache.addRegionTicket` inside plot space, but NeoForge's forced chunks
  (`TicketController.forceChunk`) use the five-argument overload, which it does not. Residency once
  forced a hull's plot chunks that way, and every world with a drive in it hung on "Saving worlds"
  forever: on shutdown vanilla drops all tickets and waits for the chunks to unload, and
  `PlotChunkHolder.isReadyForSaving()` is hard-coded `false`, so the unload re-queues itself
  endlessly. Releasing such a ticket at runtime does the same. Hold a ship by the **world** ground under
  it - Sable loads the sub-level with that chunk. `AirshipResidency` refuses plot chunks and strips old
  ones at load. (`ArrivalTicket.holdDrive` and a gate or probe ticket placed aboard a ship use the
  four-argument form, so Sable silently cancels them - harmless, but they hold nothing.)
- **Machines aboard a hull are read from the plot's own chunks**, not Sable's actor list — membership
  of that list depends on a block having *changed* since the plot was created, so a machine placed on
  an already-assembled hull may never appear in it.
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
- **Nothing but mixins may live in `mixin/`.** Mixin reserves the package: an ordinary class placed
  there cannot be loaded at all. The hooks the mixins call live in `RiftStormSky`. Every mixin must be
  listed in `aerowarptics.mixins.json` or it compiles and silently never applies, and `defaultRequire`
  is 1, so a target that stops matching is a crash at launch - `MixinTargetTest` checks both against
  the game's bytecode before that. The mod had no mixins until the Rift Storm, which needed them only
  because NeoForge has no event for the sky, cloud or daylight colour; prefer an event wherever one
  exists.
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
must not read server-only values directly — the packet carries them.

**Failure.** Refusals are enum values (`WarpFailure`, `GateFailure`) with translation keys, surfaced
to the player as a named reason. A warp that cannot proceed is refused *before* anything moves. The
airship is never damaged or deleted by a failed warp.

**Comments explain why, not what.** This codebase has a distinctive and consistent voice: prose
sentences, British spelling, explaining the reasoning and often the bug that motivated the code.
Match it. Do not add `// increment the counter` noise, and do not strip the existing explanations —
they are the design record.

**Prose style.** Em dashes and hyphens both appear; British spellings throughout (`colour`,
`normalised`, `behaviour`). Player-facing strings favour plain language over the mod's own jargon —
the Probe's UI says "Scan ahead", not "Throw sounding", deliberately.

---

## Current state

- **1.3.1 is committed and pushed** to `main`, unreleased. Anything newer is whatever `git status` shows.
- Remote is `https://github.com/ICEconchy/Aerowarptics.git`.
- Licence is CC BY-NC-SA 4.0 (`LICENSE`, `mod_license` in `gradle.properties`).
- `Depends/` is **gitignored** and was purged from history before the repo went public. The README's
  "Building" section lists the jars to put there. Never commit a third-party jar again.
- Nothing machine-specific belongs in a committed file. `deploy_dir` is blank in `gradle.properties`
  and set per machine in `~/.gradle/gradle.properties`; write paths as `$HOME/...`, not a user name.

### Known issues

See **`docs/remediation-plan.md`** for the full audit findings and a phased fix plan. The two that
matter most:

1. **More than 256 visible Warp Anchors throws `EncoderException`** when any player opens an
   Astrolabe. `quoteAll` is uncapped against a `list(256)` codec. Reachable in normal play.
2. **The `INTERRUPTED` stall on large hulls.** The drive stops ticking at the crossing. Survivable —
   the ship arrives, `CrewManifest` places the crew — but real. Current hypothesis: Sable's plot
   relocation round-trips the block entity through NBT as part of the crossing, and `read()`
   deliberately drops any running flight. Unconfirmed; the plan starts with a diagnostic step.

The per-tier `instability` value is read when the rift opens (`RiftDriveBlockEntity.beginWarp`, via
`RiftStorm.instability`, which adds the Singularity's figure to the other drives during a Rift Storm).

---

## Documentation map

| Where | What |
| --- | --- |
| `README.md` | The design record. Long, detailed, explains *why* every mechanism is as it is. |
| `CHANGELOG.md` | Player-facing release notes for 1.3.0. |
| `docs/mod-description.md` | CurseForge / Modrinth page copy. |
| `docs/remediation-plan.md` | Audit findings and the phased fix plan. |
| `docs/feature-ideas.md` | Candidate features, weighed against the mod's own invariants. A menu, not a plan. |
| GitBook wiki | Player and server-admin docs, 17 pages. Draft change request, **not yet merged**. |
| In game | The Navigator's Handbook (`guide/GuideBook.java`) - the step-by-step walkthrough, which is the one thing Ponder, JEI and the advancement tree each cannot be. |

The README is the source of truth for design intent; the wiki is the source of truth for how a player
uses it. Keep player-facing wording consistent between the wiki, the lang file and the JEI pages.
