# Rift Sunderer - implementation plan

**Status, 2026-10-17: every phase built and unit-tested, the scene redone (below), and everything this plan asks
for seen in a real run except sound.** On 2026-10-16 a tear, a rupture, a refusal, guttering and the hand in
preview worked. On 2026-10-17, after the redo: a misfire (the burst third put back, 2000 mB handed back), the hand's
take of a real frame block and the block carried back in its fingers, a rupture mid-orbit flinging the orbiting
blocks, and a tier I wellhead round a conjured tear (*Home Fires*, the guttering clock stopped, and restarted by a
broken frame). The sounds resolve but were not heard, and nothing was judged in motion - stills only (AGENTS.md,
"Current state").

| Phase | What | Status |
| --- | --- | --- |
| 0 | Decision 3 amended, this plan | Done |
| 1 | The conjured fissure: stored reservoir, no siphon, no heart, guttering | Done |
| 2 | Pure model: ring, sequence, choreography, crack web, hand pose | Done |
| 3 | Server: block, block entity, tank, kinetics, survey, breach, commands | Done |
| 4 | Client: cue, scene, sounds, crack web | Done |
| 5 | The hand: pose, renderer, the take | Done |
| 6 | Rupture | Done |
| 7 | Recipe, model, advancements, handbook, Ponder, JEI, lang, changelog, AGENTS.md | Done, except the wiki |

## The scene's second pass (2026-10-17)

The owner saw the first scene and sent it back: "just a 2d plane, not visually appealing at all". It was a flat
membrane across the opening with cracks drawn on it. Their decisions for the second pass:

1. **The ring unravels.** The third of the frame that bursts lifts out, tumbles and orbits a black seed growing in
   the middle of the opening; the world bends round the seed; the blocks are swallowed one by one; the seed
   implodes and the fissure is born.
2. **Those blocks really move.** They leave the world as it fires and are drawn moving; a misfire puts them back.
3. **About 35 s**, about 44 s with the hand.
4. **The newborn tear is visible to everyone for about 10 s**, then goggles-only.

What was built: `Unravelling` (each burst block's path, pure), a rewritten `SundererChoreography` (point, search,
lens, seed, pulse, disc, orbit), `SundererSequence` straining 500 ticks with the hand at 260, the burst blocks
lifted in `fire` and put back by `putBack`, `RiftFissureBlockEntity.reveal`, and a new `SundererSpectacle` with no
flat surface at all: starlines, a `GravityLens`, a black seed, real block models, a dust disc on the shard pass, and
the hand out of the seed's edge. `CrackWeb` was deleted. Two new sounds: `sunderer_grind`, `sunderer_swallow`.

Seen in game the same day: every beat frame by frame through the preview, and a real run to a fissure seen without
goggles. The dust disc was first pale and lost against the sky, and was made dark. A real run cannot be stepped
frame by frame: Create's rotation stops under `/tick freeze`.

## Where the build differs from this plan

- **It is shorter.** Reaching 8 s, straining 14 s, the breach 3 s: 25 s, or 33 s with the hand (`SundererSequence`).
- **The membrane bows towards the Sunderer's face**, out at whoever set it, and **the hand comes out on the side the
  watching player is on**, turning to them if they are within eight blocks. Each client sees it look at them.
- **The cracks are pale on black, not dark.** A black membrane hides dark cracks; solid pale violet reads by day,
  and the glow is laid over it.
- **No generated model and no hand skin.** The block is a vanilla `orientable` model on the frame's sheet with a
  placeholder front (`rift_sunderer_front.png`), and the hand is drawn in flat colours by `RiftHandRenderer`.
  `tools/rift_sunderer_model.py` and `tools/rift_hand_texture.py` were not written.
- **`tolerance()` needed no clamp**: it already clamped its share at nought.
- **The rupture's crater is carved in its one tick** (`SundererBreach.carve`), small enough not to need the
  meltdown's budget. Land protection is the meltdown's own check, made public as `WellheadMeltdown.permitted`.
- **The scar zone a rupture leaves** goes through a new `ScarZones.leave(level, centre, radius, ticks, what)`.
- **The collar's panel says nothing new.** A conjured tear shows its clock on the goggles before it is held, and
  nothing while held, like every harnessed tear.
- **A fourth advancement**, *Home Fires*, for holding a tear you made. *Something Waved Back* is hidden, which
  `tools/advancements.py` now supports (`HIDDEN`).

---

## Context

Fissures are found, never made, so the Rift Wellhead (the essence reactor) can only be built at a rift scar far
from home. The owner wants a survival way to put a fissure in a base without a placeable fissure item. It should
be **a machine that tears reality open, single use**, built from what the scar ruins already show. The ring ruin
is a broken circle of Rift Gate Frame with a fissure hanging where the aperture should have been;
`tools/rift_scar_structure.py:141` describes it as "The gate they were building, and the hole where it went
wrong."

**The Sunderer lets a player do that on purpose:** a gate ring with a single-use heart that dials *nowhere*. The
aperture finds no far side, strains, cracks and collapses into a fissure, leaving the player the same broken ring
they found in the wild. On rare occasions something on the other side reaches out first.

This overturns written policy, by the owner's call (2026-10-16). Amend these and record the date:
- `docs/crafting-plan.md` decision 3;
- `AGENTS.md:1051-1055` ("No machine, recipe or ritual may place one").

**The owner's decisions (2026-10-16):**
1. **A conjured fissure seals with no Scar Heart.** The Singularity still needs a found scar.
2. **It gutters unless harnessed.** It is unsettled for 20 min, then seals itself unless a Wellhead frame holds
   it. A broken frame restarts the clock.
3. **A failed tear ruptures.** A crater of radius ~5 lined with Scarred Stone, a few minutes of scar zone,
   creatures flung, and the meltdown's land-protection check.
4. **It is a machine in a gate-frame ring, single use**, with a rare eldritch hand as an easter egg.

**My calls, open to change:**
- The tear shatters about a third of the frame, so the ring ends up looking like the ruin.
- The hand appears 1 time in 50, and takes one frame block back with it.
- Names: *Rift Sunderer*, package `sunderer/`.


---

## 1. The machine

### Build
- **Rift Gate Frame**, the existing block (8 per craft), stood on end in a closed ring, exactly as for a gate.
- **The Rift Sunderer** takes the place one frame block would, where a gate's controller would go.
  - It is a kinetic block with **one shaft, on its back, looking out**. That is the wellhead's lesson: a
    second shaft relays rotation.
  - It has a 4000 mB **Rift Essence** tank, fed by pipe.
- **The opening is found by `RiftGateStructure.find`**, the gate's own flood fill, with frame-or-sunderer as the
  wall and air as open space.
  - It must be **at least 3×3 and fit inside 7×7**.
  - A Sunderer is not frame, so a gate never forms round one and a ring with a gate controller in it gives
    `NO_RING`.
- **Recipe:**

  ```
   L
  PCP
   S
  ```

  L is an Opened Rift Lens (crafting rule 2: what tears a rift has seen one), P a Rift Pearl, C Brass Casing,
  S a Shaft. That is the gate controller's recipe, but with pearls in place of frame and brass in place of
  copper.

### Charging and firing
- **Charging:** fill the tank and spin the shaft at **128 RPM or more**. The stress impact is in config.
- **Light:** the Sunderer's own block light rises as it charges (a `CHARGE` state, 0-3, mapped to light
  0/4/8/12). It is real light in the room, changed only a few times.
- **Feedback:**
  - Goggles show the charge, the speed, the ring's size, or the first refusal.
  - A comparator reads the charge.
- **Firing** is a redstone rising edge (crafting rule 4). The survey runs first, and nothing is spent on a
  refusal.

**Refusals** (`SundererFailure`, in this order, sent to the action bar and the goggles):
1. `ABOARD_SHIP`
2. `NO_RING`
3. `RING_TOO_SMALL`
4. `RING_TOO_LARGE`
5. `NOT_CHARGED`
6. `TOO_SLOW`
7. `IN_SCAR_ZONE`: a scar zone or a running meltdown.
8. `NEAR_TEAR`: a fissure, a Rift Wound, or another sundering within 48 blocks.
9. `NEAR_ANCHOR`: a Warp Anchor within 32. In the fiction, an anchor pins space.
10. `IN_WARD_FIELD`
11. `DIMENSION`: a config list, empty by default.

### The sequence (~35 s; ~43 s with the hand)
All tank essence is spent at the edge. The sequence is saved in the block entity's NBT, so it resumes after a
restart, and `Holds.holdGround` keeps its chunk loaded.

**1. Reaching (0-8 s), a misfire if it stalls.**
- The aperture opens in the ring as a gate's does: violet, filling the frame's shape (`RiftEffectManager.hold`
  sized to the `RiftGateShape`).
- Then it **finds no far side**. Where a gate shows a destination, the membrane goes flat, matte black, and still.
- The hum drops in pitch.
- **If rotation stalls here, it misfires:** the aperture closes and the Sunderer survives, but half the essence
  is lost (crafting rule 4: failure hands something back).

**2. Straining (8-22 s), committed.**
- **The membrane bows** towards whichever side the Sunderer faces.
- **A web of cracks** spreads inward from the frame across the membrane:
  - The cracks are dark, solid fractures (`RIFT_MEMBRANE`), branching as they grow and seeded so every client
    sees the same web (`CrackWeb`, pure).
  - Violet glints run along them (`RIFT_FIRE`), with a snap as each branch forks.
- **The frame** lets off block-crack particles and a pulse of violet light.
- **Light and sky:** `RiftStormSky.lend` darkens sky and fog within 32 blocks, up to 60%. The Sunderer's own
  light climbs to 12, so the room is lit from the ring alone.
- **Sound:** a groaning loop that rises in pitch, plus crack snaps.
- **The pull, the hazard:**
  - Living things within 6 blocks of the plane are drawn gently towards the centre.
  - Loose items are pulled in and destroyed.
  - Standing in the opening costs void damage and a `RiftHop`.

**3. The Hand (rare, 8 s, inside Straining).** The server rolls `handChance` (0.02) at the edge and saves it.
1. The membrane bulges at one point and splits into a slit.
2. **Fingers come first:** too long, four joints each, matte black with a faint violet rim.
3. Then a palm, then a forearm to the elbow.
4. The fingers splay and sweep slowly. If a player is within 8 blocks, the palm **turns to face them and holds
   still for a beat**.
5. The fingers curl round one frame block, chosen by seed.
6. As the hand withdraws, **that block is taken through with it**. It is removed, through the land-protection
   check, and drops nothing.

**Sound:** everything else ducks, and a slowed, deep creak and breath are layered from vanilla warden and sculk
sounds. The slit seals with one sharp crack.

**Advancement:** *Something Waved Back*, hidden, granted to everyone within 32 blocks.

**4. Breach (3 s).**
1. Every sound ducks.
2. The cracks race to the centre and the membrane implodes to a point.
3. A white-violet flash and a ring of dust go out along the ground.
4. **The Sunderer shatters** (it is removed and drops nothing), and **about a third of the frame bursts**
   (seeded, removed, no drops).
5. Left behind: a broken ring with **a fissure hanging at the opening's centre block**, the ruin rebuilt.

Advancement: *Where It Went Wrong*.

**Rupture (failure after commitment).** Rotation stalls for more than 2 s during Straining, or a frame block is
broken mid-sequence. Then:
- The membrane blows outward.
- A crater of radius 5, lined with Scarred Stone, is carved through `permitted()`.
- `ScarZones.leave` covers radius 7 for 5 minutes.
- Living things within 10 blocks are hurt and hopped outward.
- The Sunderer, the essence and the whole frame are lost, and there is no fissure.

Advancement: *Too Thin*.

### Balance
- **Cost of one tear:**
  - the Sunderer (an Opened Rift Lens, two Rift Pearls, brass);
  - 4 buckets of essence;
  - a third of a ring of frame;
  - the rotation for about 35 s.
- **Payoff:** a fissure that is worth nothing on its own (see §2) and only becomes a reactor once a Wellhead
  harnesses it.
- **Removing the frame:** the ring's remains stand where a Wellhead's shell must go, so the player clears the
  ruin and builds the well.

---

## 2. The conjured fissure

It is the same `RIFT_FISSURE` block. Its block entity gains `conjured` and `unsettledUntil` (NBT keys
`Conjured` and `Unsettled`).
- **Its reservoir is stored, not rolled:** `fissureEssence`, 3000 mB. A found tear still rolls it from its
  position.
- **It never drains into siphons,** so nothing can loop "sunder, drain, repeat" for a profit.
- **It seals with no heart.** `close()` guards the heart with `!conjured`.
- **It gutters:** while unharnessed and past `unsettledUntil`, it calls `beginSealing()`. `release()` (the
  frame breaking) restarts the clock, and harnessing it clears the clock.
- **Its tear trembles more as guttering nears.** The client jitters its openness.
- **Goggles show** "Conjured - unsettled mm:ss" or "Held by a frame". The collar panel adds "Conjured tear -
  thin-walled".
- **While unsettled it is a free rifting catalyst** for 20 minutes. A brazier burning that long costs 2.4
  buckets, so the window is paid for.
- **The reactor balances it with no new rule:**
  - `RiftCollarBlockEntity.tolerance()` reads `original`, so 3000 mB sits at the minimum tolerance of 0.8 and
    runs hotter than a found tear. Clamp the share at 0, since 3000 is below `reservoirLeast`.
  - TEARING bleeds 20 mB/s, so a conjured well run hot bleeds dry in about 2.5 minutes and seals with no heart.
  - A meltdown at home still makes its full crater.

---

## 3. Code

New package **`sunderer/`**:

| Class | Kind | Job |
| --- | --- | --- |
| `RiftSundererBlock` | block | `KineticBlock`, horizontal facing (shaft on the back), `CHARGE` 0-3 → light, comparator, refuses movement mid-sequence |
| `RiftSundererBlockEntity` | BE | `KineticBlockEntity`, essence tank capability, the sequence's driver and saved state, goggle info, `DrawnAboard` rules if rendered |
| `SundererRing` | pure | wraps `RiftGateStructure.find`: size limits, centre block, which third of the frame shatters, the hand's grip block (all seeded) |
| `SundererFailure` | enum | refusals, `StringRepresentable`, `sunderer.failure.<name>` |
| `SundererSequence` | pure | IDLE → REACHING → STRAINING (→ HAND) → BREACH → DONE, plus MISFIRE and RUPTURE. Takes `Conditions` (speed held, ring intact) each tick. A pause never resolves (the `CollapseRitual` shape). |
| `SundererChoreography` | pure | curves: aperture, blackening, bow, crack growth, darkness, block light, hum pitch, hand beat, implosion, flash. No jumps at boundaries. |
| `CrackWeb` | pure | seeded branching cracks inside the opening's mask; they reach the centre by the end of Straining |
| `RiftHandPose` | pure | joint angles over the hand beat: reach, splay, sweep, turn-to-viewer, curl, withdraw |
| `SundererSurvey` | level | refusals. Reuses `Holds.inPlot`, `ScarZones.within`, `Meltdowns.over`, the anchor registry and `Wards`. Reads only loaded chunks. |
| `SundererBreach` | level | **the only placer of a fissure**: sets it and calls `conjure()` in the same tick; shatters the frame; removes the Sunderer; the hand's take; the rupture crater (`CraterPlan` + `WellheadMeltdown.mayTake` + `permitted`, Scarred Stone, `ScarZones.leave`, hops) |
| `SundererCommands` | command | `/aerowarptics sunderer preview <pos> [hand]` (a dry scene, as the wellhead's preview) and `survey` |
| `network/ClientboundSundererCuePacket` | packet | origin, `RiftGateShape`, facing, elapsed, length, seed, hand flag, grip block, viewer target, dry. Sent every 20 ticks and on every beat change, so latecomers join. Bump `AWNetwork.VERSION`. |
| `client/fx/SundererSpectacle` | client | aperture, membrane, crack web, glints, sky lend, shake (one `add` per event, never per tick), sound loop (multiplies its own volume by `TrimmedSound.level()`), breach and rupture scenes |
| `client/fx/RiftHandRenderer` | client | code-built segmented mesh posed by `RiftHandPose`, solid pass first, rim on `RIFT_FIRE` |

### What changes elsewhere
- **`fissure/RiftFissureBlockEntity.java`:**
  - the conjured fields;
  - `conjure(reservoir, unsettledTicks)`;
  - the `close()` heart guard;
  - the gutter in the tick;
  - `release()` restarts the clock;
  - `drainInto` skips siphons when conjured;
  - the goggles lines.
- **`client/ClientRuntime.java:381-418`:** the conjured tremble.
- **`wellhead/RiftCollarBlockEntity`:** the panel line and the `tolerance()` clamp.
- **Registries:** `AWBlocks`, `AWItems`, `AWBlockEntities`, `AWCapabilities` (the tank; `CapabilityCoverageTest`
  will ask), and the creative tab.
- **`AWSounds` + `sounds.json`** (built from vanilla audio): `sunderer_reach`, `sunderer_strain` (loop),
  `sunderer_crack`, `sunderer_hand_creak`, `sunderer_hand_breath`, `sunderer_breach`, `sunderer_misfire`,
  `sunderer_rupture`. They are not in `WarpSoundSwitch`.
- **`AWConfig`:** a new `sunderer` section:

  | Key | Default |
  | --- | --- |
  | `enabled` | |
  | `chargeEssence` | 4000 |
  | `minimumSpeed` | 128 |
  | `stressImpact` | |
  | `minOpening` / `maxOpening` | 3 / 7 |
  | `shatterShare` | 0.3 |
  | `handChance` | 0.02 |
  | `spacing` | 48 |
  | `anchorSpacing` | 32 |
  | `fissureEssence` | 3000 |
  | `unsettledMinutes` | 20 |
  | `ruptureCrater` | 5 |
  | `ruptureZoneMinutes` | 5 |
  | `forbiddenDimensions` | empty |

- **Generated assets:** `tools/rift_sunderer_model.py` writes the block's textures, model and blockstate (a
  placeholder for the artist). `tools/rift_hand_texture.py` writes the hand's 32px skin. Add both to AGENTS.md's
  table.
- **Recipe:** `recipe/rift_sunderer.json`, with `RecipeBalanceTest` and `IngredientProducerTest` pricing it.
- **`AWCriteria` + `python tools/advancements.py`:** Where It Went Wrong, Too Thin, Something Waved Back
  (hidden), and Held (harness a conjured tear).
- **Guides:** a handbook page, a Ponder scene (through `tools/ponder_schematics.py` and `tools/ponder_lang.py`),
  and a JEI info entry. It is a single-use machine, not a process, so it is not a recipe type; note the exception
  to crafting rule 7.
- **Lang.**
- **Docs:**
  - `docs/rift-sunderer-plan.md`.
  - `crafting-plan.md` decision 3, amended.
  - `AGENTS.md`: Layout; the fissure invariant rewritten ("found, or torn by a Sunderer; only `SundererBreach`
    places one; a conjured tear never yields a heart and gutters unless harnessed"); the generated-assets table.
  - Javadocs at `ScarChartItem.java:33` and `AWItems.java:86, 236`.
  - `CHANGELOG.md`.

---

## 4. Build order (each phase ships alone)
0. Docs and the decision amendments.
1. The conjured fissure's state, with its tests.
2. The pure model (`SundererRing`, `SundererSequence`, `SundererChoreography`, `CrackWeb`, `RiftHandPose`), with
   its tests.
3. Server: block, block entity, tank, kinetics, survey, breach, commands. It works with no visuals.
4. Client: cue, spectacle, sounds, crack web.
5. The hand: pose, renderer, skin, the take.
6. Rupture.
7. Recipe, generated model, advancements, handbook, Ponder, lang, changelog, AGENTS.md.

## 5. Tests
- **`SundererRingTest`:**
  - rings of 3×3 to 7×7 pass, and 2×2 and 8×8 are refused;
  - a ring with a gap fails;
  - a Sunderer anywhere in the ring, corners included, finds the opening;
  - the centre is an interior block;
  - the shatter set is about a third, seeded, and never the whole ring;
  - the grip block is a frame block.
- **`SundererSequenceTest`:**
  - a held run ends in BREACH;
  - a stall in Reaching misfires and refunds half;
  - a stall over 2 s in Straining ruptures;
  - a broken ring mid-sequence ruptures;
  - a paused run never resolves;
  - the hand adds its beat only when rolled.
- **`SundererChoreographyTest`:** 100 samples a tick, no jump at any boundary, with and without the hand.
- **`CrackWebTest`:**
  - same seed, same web;
  - every crack stays inside the mask;
  - the web reaches the centre at the end of Straining.
- **`RiftHandPoseTest`:**
  - continuity;
  - the fingertips never pass behind the membrane plane before withdrawal;
  - the grip pose reaches the grip block.
- **`RiftFissureTest`, amended:**
  - a conjured seal gives no heart;
  - found tears roll their reservoir and conjured ones store it;
  - "changes no block but its own" still holds;
  - a conjured tear does not drain into siphons;
  - it gutters only while unharnessed.
- **`SundererWiringTest`:**
  - only `SundererBreach.java` names `RIFT_FISSURE.get().defaultBlockState()`, walking `src/main/java` as
    `WellheadWiringTest.aWoundIsLeftByAMeltdownAndByNothingElse` does;
  - `conjure()` is called in the same method as the `setBlock`.
- **Existing tests that must keep passing:**
  - `ResourceIntegrityTest`: still no fissure recipe or loot table;
  - `CapabilityCoverageTest`;
  - `LangCoverageTest` and a failure-text test;
  - the `sounds.json` coverage.

## 6. Verification
- **Tests:**
  ```bash
  export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2" && ./gradlew test
  ```
  Read the output, not the exit code.
- **In game, through the Minecraft MCP:**
  1. Build a 5×5 ring of frame with a Sunderer, a creative motor and an essence tank.
  2. Fire it, with `/aerowarptics trace on`.
  3. Check the refusals: a ring of 2×2, a Warp Anchor nearby, too little charge.
  4. Stall a run in Reaching (a misfire), then one in Straining (a rupture: check the crater and the zone).
  5. Run `/aerowarptics sunderer preview <pos> hand` at noon and at night. The cracks and the hand must read in
     daylight.
  6. Harness the tear with a tier I Wellhead after clearing the ring.
  7. Break the frame and check the clock restarts. Use `/time add` to see it gutter with no heart.
- **Not testable without the game:** how the cracks, the darkening and the hand look. Expect the owner to send
  the hand's look back at least once.

## Risks to check at build time
- **A fissure's first tick rolls its reservoir when it is -1.** `conjure()` must run in the same tick as
  `setBlock`.
- **`Meltdowns` keeps its crater carving private.** The rupture may need a small shared helper pulled out rather
  than a second copy.
- **`RiftGateShape` may not have a packet codec yet.** Check what the gate's sync uses.
- **The hand is drawn from the spectacle against the camera, not as a block-entity renderer.** The Sunderer is
  gone at breach, and a renderer culled to one block would vanish (the `RenderBoundsTest` lesson).
- **The hand's take is a block change outside the fissure.** It goes through `permitted()`, and is skipped if
  refused.
