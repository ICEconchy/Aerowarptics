# Rift Wellhead - implementation plan

**Status, 2026-10-09: every phase built and unit-tested (1652 tests) except the generated model. Only the unformed
frame has been seen in game.**

| Phase | What | Status |
| --- | --- | --- |
| 1 | Frame: structure, forming, parts and ports, fissure harness, refusals | Done, except the generated model |
| 2 | Reactor: recipe type, hatch, digestion, strain, damping, tank, integrity, bleed-dry, scram, comparator, goggles | Done |
| 3 | Panel, Display Link, JEI | Done |
| 4 | Cinematic meltdown | Done |
| 5 | Exclusion zone | Done |
| 6 | Teaching: handbook, Ponder, advancements, wiki | Done, except the wiki |

## The meltdown's second pass (2026-10-09)

The first meltdown was watched once, on a small frame, and sent back: lacklustre, small, and almost none of
its effects seen. What was left was a shallow dirt hole with loose Scarred Stone on the grass. The owner's
decisions, and what was built for them:

- **Crater radius 16 / 28 / 48** (was 6 / 10 / 16), **about 45 seconds** (was 30). The config keys were renamed
  (`craterRadius`, `wellheadMeltdownLength`) so a world that already held the old values takes the new ones.
- **Why so little was seen:** everything scaled with a crater of six, and almost all of it was additive light,
  which at noon is nearly nothing. The scene is now drawn dark first - a black tear up to nineteen blocks in
  radius, a funnel of broken dark rings climbing to the clouds, a dark ring thrown out at each heartbeat, and
  a ball of dark that runs out with the ground gone behind it - with the light laid over as trim.
- **The build-up:** the whole frame goes in two seconds; the ground strips towards the tear from the surface
  down, trees first; a heartbeat quickens from one every three seconds to one every eight ticks; the pull
  reaches three radii and takes dropped items for good.
- **The detonation:** a white over the whole view for those near, the dark front, a ring of air that throws and
  hurts each creature as it reaches it, a boom that reaches each player when it would have, and the rim thrown
  outward as real blocks.
- **The aftermath:** a bowl lined in Scarred Stone, veins of it running out from the rim, blobs of ground left
  hanging over the hole, nothing loose on the grass, and a **Rift Wound** standing where the fissure was for
  good - a dull closed tear under a thin column of light.
- **`/aerowarptics wellhead preview i|ii|iii`** plays the scene dry, in front of whoever asks.

Not seen in game. The cost of relighting a radius-48 crater is not measured; `wellheadCarveBudget` is the dial.

Where the build settled something this plan left open, or differs from it:

- **The frame keeps the look of its blocks for now.** Parts are drawn as the casing and glass they stand for,
  and the collar and ports wear borrowed textures, until `tools/rift_wellhead_model.py` exists.
- **Damping is in proportion to strain**, not a flat rate as the sketch below has it. A flat rate gives a well
  nowhere to settle but nought; in proportion, a kept well settles at a strain its cage decides, and a thinner
  cage settles higher and pays better - which is the reason to run one hot. See `WellheadReactor`.
- **The gullet is measured in the essence it will come to**, not in items (128 / 512 / 2,048 mB by tier).
- **A crystal comes out, or is swapped, only when the well is cold** - nothing in it and no strain on it.
- **`FISSURE_TAKEN`** is a refusal the plan did not list: another collar already holds that tear.
- **The panel is plain vanilla drawing**, laid out by `WellheadLayout`, not the house style of the other screens.
- **The corrupted shell turns ground to Scarred Stone and rains it back; it does not swap blocks' places**, as
  the plan had it. A swap moves somebody's chest as readily as a stone.
- **Loose ground that flies is capped at 140 pieces**; past that, ground in the swell is swallowed where it lies.
- **Running effects are the alarm, the hops and the harm.** The posed ribs and cracking panes wait on the model.
- **Advancements are for whoever is standing by**, not whoever did it: nobody in particular forms a frame or
  melts one down. *Tapped the Wound*, *Not Today Either* (a scram at critical), *It Was Like That When I Found It*.
- **A scar zone refuses a new wellhead and stops rift rain in a siphon.** It does not stop a siphon's warp draught.

## Context

Rift Essence has no sustained, automatic source: a warp gives one draught, a fissure gives 6-18 buckets
once, rift rain only falls in a storm. The Rift Wellhead is the farm: a frame built round a **found**
fissure that agitates it with fed blocks, turns them into essence, and has to be held steady the whole
time. It is run like a reactor - yield rises with strain, strain feeds on itself, and a neglected one
melts down.

Decisions already made by the owner:

- A meltdown **damages terrain** ("corrupting the very essence of reality"). This overrides the old
  "fissures never touch stone" decision for the wellhead only.
- A harnessed fissure does **no rifting**.
- It **can** be automated to run hot indefinitely.
- A meltdown **destroys the fissure** - no Scar Heart, the site is gone.
- The crater is followed by a **lingering exclusion zone**.
- It has a **control panel** as well as redstone, comparator and goggles.
- The meltdown must be **cinematic**.

On approval, copy this plan to `docs/rift-wellhead-plan.md` (as `docs/rift-ward-plan.md` was).

---

## 1. The build

A hollow cube, odd size, with the fissure in the exact centre cell.

| Cell | Block |
| --- | --- |
| Edges and corners | Rift Casing (existing) |
| Faces | Warp Glass (existing) |
| Centre of one **side** face | **Rift Collar** (new) - the master: shaft, crystal seat, panel |
| Interior | air, and the one fissure |

- The collar's distance to the fissure straight behind it fixes the size (1, 2 or 3 blocks = 3, 5, 7
  cube), so there is no search and no ambiguity.
- The collar forms the frame itself (periodic check, as `DriveAssembly.check` does), and refuses with a
  named `WellheadFailure`: `NO_FISSURE`, `FRAME_INCOMPLETE` (names the first wrong cell),
  `INTERIOR_BLOCKED`, `SECOND_FISSURE`, `CRYSTAL_TOO_WEAK`, `FISSURE_SEALING`, `ABOARD_SHIP`,
  `IN_SCAR_ZONE`. Each is traced with its numbers.
- On forming, every shell block becomes an invisible `rift_wellhead_part` and the collar's renderer draws
  one model the size of the frame. What a part was is a rule of its position (edge = casing, face =
  glass), so unforming puts the right blocks back with nothing stored.
- Part state carries its offset from the centre (three ints, -3..3), the cradle's scheme.
- Face-centre parts are **ports** with block entities (Create's pipes and funnels need one - the relic
  floor's lesson): the top is the feed hatch, the bottom and the other three sides are taps.
- The collar has exactly one shaft, on its outward face. No other block has any (the relic gearbox bug).
- Every block refuses `BlockMovementChecks`, pistons and a ship's deck.

### Tiers

| Tier | Frame | Crystal seated in the collar | Digestion, calm to hot | Gullet | Tank | Stabilisers for full damping | Crater |
| --- | --- | --- | --- | --- | --- | --- | --- |
| I | 3 | Warp Crystal | 0.5-1.4 mB/s | 16 items | 4 B | 2 | 6 |
| II | 5 | Folded Warp Crystal | 1.5-4 mB/s | 32 | 16 B | 4 | 10 |
| III | 7 | Pearlescent Warp Crystal | 3-8 mB/s | 64 | 64 B | 8 | 16 |

All figures are starting values in `AWConfig`. The crystal is seated by using it on the collar and can
only be taken back when the well is cold.

---

## 2. The reactor

One pure class, `WellheadReactor`, stepped once a second. State: `matter`, `strain` (0-1), `integrity`
(0-1), tank.

```
digestion = tierBase * (1 + yieldGain * strain)            while matter > 0
essence  += digestion                                      into the tank
pressure  = digestion * feedStrain * storm * (1 + feedback * strain)
          + overflow                                       essence that found the tank full
damping   = tierDamping * speedShare * cage * integrity
strain   += (pressure - damping) * dt
```

- **Positive feedback** is the reactor: above a point, strain raises its own pressure faster than a
  fixed cage can damp it. `WellheadReactorTest` proves there is a stable band and a runaway band.
- `speedShare`: 0 below the tier's minimum RPM, 1 at optimal. Stress is charged per RPM (`AWStress`).
- `cage`: switched-on Spatial Stabilisers in reach, counted against the tier's need and weighted by
  balance. Reuse `CradleRing.stabilisers` and `CollapseInstability.imbalance`
  (`collapse/CradleRing.java`, `collapse/CollapseInstability.java`); a stabiliser's goggles say
  `STEADYING` for a wellhead as they do for a cradle.
- **A full tank is a failure mode**: digestion does not stop, the lost essence becomes pressure. A
  blocked outlet pipe is the coolant leak.
- A raging Rift Storm multiplies pressure and yield.
- The fissure's rolled reservoir sets tolerance (a big fissure runs hotter before tearing).
- Grace after a load (Create reports zero speed for the first ticks): `wellheadResumeGrace`, the
  cradle's `COLLAPSE_RESUME_GRACE` pattern. An unloaded well does not tick.

### Bands

| Strain | State | What happens |
| --- | --- | --- |
| < 0.35 | Calm | Integrity slowly mends. |
| < 0.65 | Straining | Best safe yield. Ribs flex, nearby creatures rift-hop (`rifting/RiftHop`). |
| < 0.90 | Tearing | Integrity falls (so damping falls - a second feedback), the fissure bleeds reservoir, creatures in reach are hurt, panes crack. |
| >= 0.90 | Critical | Klaxon, comparator 15, sky dims. Still recoverable. |
| 1.0 | Meltdown | Irreversible. See section 4. |

Two ways a site dies, deliberately different:

- **Bled dry**: reservoir reaches zero in the tearing band - the fissure seals as now, drops its Scar
  Heart, the frame unforms intact.
- **Meltdown**: no heart, no fissure, no frame, a crater.

### Feed

Recipe type `aerowarptics:agitating` (a Create `StandardProcessingRecipe` with params, shaped like
`rifting/RiftingRecipe` and `collapse/CollapseRecipeParams`): one item in, essence mB out, plus a
`strain` number. The hatch accepts only items with a recipe, only with room, never during a lockout.

| Feed | Essence | Strain per mB |
| --- | --- | --- |
| Cobblestone, stone | 4 mB | 1.0 |
| Deepslate | 6 | 1.0 |
| End stone | 12 | 0.7 |
| Obsidian | 30 | 0.6 |
| Amethyst block | 40 | 0.4 |

Nothing made from essence has a recipe (Rift Pearl, Warp Glass, stormglasses); a test holds that.

### Controls

- **Comparator** off the collar or any part: `WellheadSignal` (pure, like `drive/DriveSignal`), strain
  as 0-14, 15 only at critical or beyond.
- **Redstone rising edge** on any block of it: **SCRAM** - the gullet is dumped (lost), the hatch locks
  for `wellheadScramLockout` seconds. Does nothing once a meltdown has begun.
- **Breaking the frame while hot** is a breach: the gullet becomes strain at once, damping is zero.
- **Panel** (no slots, so a plain screen on two packets, as the ward's and coupler's are):
  strain gauge with its bands, the **heading** (where strain settles under present conditions), yield,
  gullet, tank, a damping breakdown (rotation, cage count and balance, integrity), state, a SCRAM
  button and a hatch open/shut toggle. Fits 320 px via `AWLayout`; the server honours an order only from
  a player in reach of a standing collar.
- **Goggles** through every part (`IProxyHoveringInformation`), and a **Display Link** source.

---

## 3. Changes to the fissure

`fissure/RiftFissureBlock` gains a `HARNESSED` state; the block entity gains `harness(pos)`,
`release()`, `bleed(mB)` and `consume()` (removed with no heart, traced).

- `drainInto` hands nothing to siphons while harnessed.
- `RiftingType.isCatalyst` (`rifting/RiftingType.java:63`) answers false for a harnessed fissure.
- A harnessed tear is drawn for everyone, goggles or not, its openness following strain.
- The fissure's own source still sets no block but its own - the wellhead flips the state, so
  `RiftFissureTest`'s source guard stays true. AGENTS.md's fissure invariant is reworded.

---

## 4. The meltdown

It cannot live in the collar: the frame is destroyed partway through. It is a record in world data,
`Meltdowns` (a `SavedData` per level), ticked from the level tick, holding its ground through
`Holds.holdGround` for its length (only `Holds` may load - `ChunkLoadingChokePointTest`). Walking away
does not stop it; a server restart resumes it.

Timing is `MeltdownChoreography` - pure, read by server and client alike, every curve continuous across
every beat (tested at a hundred samples a tick, as `CollapseChoreographyTest` does). About 30 seconds,
`wellheadMeltdownTicks`.

| Beat | Share | Seen and heard | Server |
| --- | --- | --- | --- |
| **Breach** | 0-15% | The klaxon chokes off. Panes blow out one at a time, the tank vents in jets, lightning walks from the tear to every stabiliser. The glow goes violet to white. | Frame parts removed pane by pane; tank contents lost. |
| **Swell** | 15-55% | The tear grows out of the frame. Ribs peel back and are drawn in. Ground lifts off in layers and spirals up into it - things fall *up*. Space bends round it (`GravityLens`), the day dims for hundreds of blocks (`RiftStormSky.lend`), a tear opens in the sky overhead. | Blocks in a growing shell become falling-block debris pulled to the core and swallowed (budgeted, capped). Everything loose is pulled in (`CollapseChoreography.pulled`); each client pulls its own player. |
| **Hush** | 55-62% | Every sound cuts. The debris hangs still in the air. The tear shrinks to one white point. | Pull stops. |
| **Detonation** | 62-70% | White flash, then a sphere of unmaking runs outward from the point - the crater is seen being carved shell by shell. Boom carried far, ring of air, camera jolt. | Crater carved centre-out over the beat; blast knockback and harm with cover (the cradle's `blast`, `CollapseCradleBlockEntity.java:1102`, lifted to a shared helper); fissure `consume()`d; detectors pulsed (`SpatialWaveDetectorBlockEntity.announce`). |
| **Fallout** | 70-100% | Debris rains back, some of it Scarred Stone. Motes drift up from the crater. The sky comes back slowly. | Outer shell corrupted; the exclusion zone is recorded. |

Clients are driven by `ClientboundMeltdownCuePacket` (centre, tier, radius, start tick, length), resent
each second to players in `wellheadSpectacleRange` so a latecomer joins mid-scene. `WellheadSpectacle`
(client, modelled on `client/fx/CollapseSpectacle.java`) draws it from the camera, not the block entity.
It answers to `riftDistortion`, `riftLensing`, `riftLightning`, `screenShake`, Hide Lightning Flashes
and a new client `wellheadSpectacle` switch.

### Terrain

- **Crater** (radius by tier): blocks swallowed, no drops.
- **Corrupted shell** (to 1.5x radius): a random share is thrown as debris, swapped with another block's
  place, or turned to **Scarred Stone** (new decorative block).
- Skipped: unbreakable blocks, a `#aerowarptics:meltdown_immune` tag, anything in plot space (a ship is
  never damaged - the mod's rule), unloaded positions.
- The block list is offered to NeoForge's explosion-detonate event before it is applied, so protection
  mods that filter explosions can filter this.
- `wellheadMeltdownBreaksBlocks` (default on) for servers that disagree.

### Exclusion zone

`ScarZones` world data (shaped like `ward/WardRegistry`): centre, radius, tier, expiry. Default 20
minutes, strength fading to nothing.

- Living things inside are hurt (new damage type in `AWDamageTypes`) and rift-hopped at random, hardest
  at the centre. Loaded entities only; a zone loads nothing.
- No wellhead forms inside one; siphons inside catch nothing.
- Client: rising motes, a dimmed sky, a crackle that quickens toward the centre. Rift Goggles show the
  edge. No line or ring without them.

---

## 5. Code

New package `wellhead/`:

| Class | Kind |
| --- | --- |
| `WellheadStructure` | pure - the shell, which cell is what, ports, the one shaft |
| `WellheadReactor`, `WellheadSignal`, `WellheadWorks` | pure - the step, the comparator, the model's pose |
| `MeltdownChoreography`, `CraterPlan` | pure - the scene's curves; which blocks go, in what order |
| `WellheadFailure`, `WellheadState`, `WellheadTier` | enums |
| `WellheadAssembly` | level side of forming and unforming (as `drive/DriveAssembly`) |
| `RiftCollarBlock` / `BlockEntity`, `RiftWellheadPartBlock`, `RiftWellheadPortBlock` / `BlockEntity` | blocks |
| `AgitatingRecipe` (+ params) | recipe type, registered in `registry/AWRecipeTypes` |
| `Meltdowns`, `ScarZones` | world data and their ticks |
| `WellheadCommands` | `/aerowarptics wellhead strain <n>` and `meltdown`, operator only, for testing the scene |

Elsewhere: `registry/` (blocks, items, block entities, capabilities, stress, sounds, damage type),
`AWConfig` (server `wellhead*` keys, client `wellheadSpectacle`), `network/` (panel, order and cue
packets; bump `AWNetwork.VERSION`), `client/` (renderer with `getRenderBoundingBox` override, glow
layer, screen and `WellheadLayout`, `WellheadSpectacle`, zone effects), `compat/` (JEI category, Display
Link source, Ponder scenes), `advancement/AWCriteria`, `guide/GuideBook`.

Generated, never hand-edited:

- `tools/rift_wellhead_model.py` - three geos (one a tier), texture sheet, glow mask, the collar's loose
  block model, part and port blockstates. Posed by `WellheadWorks`, never keyframed.
- `tools/ponder_schematics.py`, `tools/ponder_lang.py`, `tools/advancements.py` extended.

Resources: `recipe/rift_collar.json` (mechanical crafting: Rift Casing, Opened Rift Lens, Stabiliser
Rings, Precision Mechanism, Rift Pearl), agitating recipes, loot tables, lang, sounds.

---

## 6. Build order

Each phase ships alone.

1. **Frame.** Structure, forming, parts and ports, fissure harness, static model, refusals.
2. **Reactor.** Recipe type, hatch, digestion, strain, damping, tank, integrity, bleed-dry, SCRAM,
   comparator, goggles, running effects.
3. **Panel.** Screen, packets, Display Link, JEI.
4. **Meltdown.** `Meltdowns`, choreography, crater, cue packet, `WellheadSpectacle`, sounds, test
   commands.
5. **Exclusion zone.** `ScarZones`, harm and hops, client effects, goggles edge.
6. **Teaching.** Handbook chapter, Ponder, advancements, CHANGELOG, README, AGENTS.md (layout,
   invariants, test count), wiki note, `docs/crafting-plan.md` cross-reference.

---

## 7. Tests

New:

- `WellheadStructureTest` - shell cell counts (26 / 98 / 218), every cell classified once, exactly one
  shaft, ports at face centres, round trip of part offsets.
- `WellheadReactorTest` - a stable band and a runaway band exist per tier; a full tank raises strain; no
  rotation means no damping; a lopsided cage damps less; a storm raises both; grace holds after a load;
  an unloaded well never advances.
- `WellheadSignalTest` - 15 means exactly critical-or-worse.
- `MeltdownChoreographyTest` - no curve jumps at a beat; the crater is finished inside the detonation.
- `CraterPlanTest` - centre-out order, radius by tier, immune and plot positions skipped, budget per tick.
- `ScarZoneTest` - strength fades to zero at expiry; a zone forgets itself.
- `WellheadWiringTest` (source-read) - scram does nothing in a meltdown; the meltdown loads only through
  `Holds`; refusals come before any state changes.
- `WellheadLayoutTest`, and the panel's strings against their room.

Extended: `RiftFissureTest` (harnessed never drains, never a catalyst, `consume` leaves no heart),
`ResourceIntegrityTest`, `LangCoverageTest`, `RenderBoundsTest`, `DrawnAboardTest`,
`CapabilityCoverageTest`, `RecipeCodecTest`, `RecipeBalanceTest` (price the collar),
`IngredientProducerTest`, `PonderSceneryTest`, `PonderTextTest`.

---

## 8. Verification

Tests, with the JDK path from memory (`jdk-path`), reading the output and not the exit code:

```bash
./gradlew test
```

In game, with `/aerowarptics trace on`:

- **Frame:** each size forms round a scar's fissure and names the missing block when it does not; the
  twin-fissure ruin refuses a larger frame; breaking a cold frame gives every block back.
- **Reactor:** cobblestone on a funnel fills a tank; cutting the shaft sends strain up; a comparator
  circuit holds it in the straining band unattended; a blocked outlet pipe raises strain; SCRAM drops
  it and locks the hatch; leaving and returning does not spike it.
- **Bleed-dry:** held in the tearing band, the fissure seals and leaves a heart.
- **Meltdown:** `/aerowarptics wellhead meltdown` at each tier - the five beats read in order, the
  crater matches the tier, a player arriving mid-scene sees it, a restart mid-scene resumes it, a ship
  parked beside it is untouched.
- **Zone:** hurts and hops inside, fades, ends; no wellhead forms in it.

Not testable without a game, and to be said so in the changelog notes: how the scene reads, the model's
poses, the panel, sound levels.

---

## Risks to check at build time

- That Rift Casing and Warp Glass carry no data a part would lose (assumed plain blocks).
- Whether `ResourceIntegrityTest` demands a recipe for Scarred Stone and the part blocks, or takes them
  on its exemption list as the cradle's parts are.
- Whether `GravityLens` can be driven away from a rift aperture, or the meltdown needs its fallback ring.
- Carving up to ~17,000 blocks for a Tier III crater: budget per tick and suppress neighbour updates
  until the shell is done.
- Multiplayer: a meltdown beside someone else's base. The explosion event and the config switch are the
  only protection; say so in the wiki.
