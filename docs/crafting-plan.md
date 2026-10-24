# Crafting plan

A crafting layer that makes building this mod's items mean something: machines, rituals and processes
that ask the player to *do* what the mod is about — warp, rift, fold, seal, weather a storm — rather
than mine more of the same. Each phase is shippable on its own and leaves the drive ladder
consistent.

Written on 2026-09-27 against 1.3.1 (unreleased). The design was checked against the code it hangs off,
Create 6.0.10's API and the invariants in AGENTS.md, then stress-tested by a review pass whose findings
are folded in. Line numbers are as of that date and will drift. Where this document says "decision",
see the list at the end.

**Status: built, 2026-09-28 - every phase, unit-tested (1094 tests), not yet seen in game.**

| Phase | What | Effort | Status |
| --- | --- | --- | --- |
| 0 | Retire fissure ore | S | Done |
| 1 | Foundations: test upgrades, shared siphon draught, Classic pack, Rift Core assembly, Rift Pearl, Rift Casing | M | Done |
| 2 | Rifting and the Seal | M | Done |
| 3 | Folding | M–L | Done |
| 4 | The Collapse | L | Done |
| 5 | Storm work | M | Done |

Where the build settled something this plan left open, or differs from it:

- **The Rift Pearl, thrown, hops what it hits, never its thrower.** Like a chorus fruit, through the chorus
  fruit's own teleport event, within eight blocks (`RiftHop`, shared with rifting). Carrying the thrower would
  have to fire the ender pearl's own teleport event, which claim mods listen for by that type, or skip it and
  walk through protected borders. (Built first as a harmless flare; the user asked for the hop.)
- **The Spatial Stabiliser takes one Gyroscopic Mechanism** where it took two Electron Tubes.
- **The Mk I is built with Rift Casing** like every other machine that took a Brass Casing, so its tree has
  warp dust in it from the casing as well as the crystals; it still has no essence and no rift.
- **The collapse takes its offerings off the depots all at once, as it begins,** so a ritual never finds half
  its offerings gone; a streak of sparks from each depot draws them in through spin-up and opening. Depots are
  any Create Depot within `collapseDepotReach` on the cradle's own level, up to eight.
- **The Singularity costs 656 by effort**, not 610: the Scar Heart and the essence are priced (30, and 0.004 a
  millibucket), which the plan's figure left out. The last step is ×2.26, inside the band.
- **The Ship's Compass has its own dial and needle** - thirty-two hand-drawn frames (2026-10-08), numbered as
  vanilla numbers its compass, where it used to be vanilla's dial under a violet badge - pointed at the ship by a
  position the server keeps on the stack - loaded or not, the hold ledger's last sighting.
- **The Stormglass's four readings are four textures,** switched by an item property; see AGENTS.md on why the
  property is quarters and not the ordinal.
- **Some of the new item icons are still placeholders** in the mod's palette, drawn once by a throwaway script,
  waiting for the same hand as the rest: the Opened Rift Lens, Scar Chart, Incomplete Rift Core and Relic
  Armillary. The Rift Pearl has its real art (2026-09-30), and so do the Folded Warp Crystal, Scar Heart and
  the Stormglasses (2026-10-06) - animated strips, the Stormglasses at 32x32 a frame - and the Ship's Compass
  (2026-10-08).
- **The Collapse Cradle was redesigned as forgotten technology** (the user's choices, 2026-09-29): a 3x3x3
  machine placed whole from one item and refusing to move; weathered rift-stone and verdigris bronze patched with
  the player's brass; crafted as before plus a **Relic Armillary**, found in a cache barrel in every rift scar
  ruin (0.4 a barrel); and fully mechanical - gears, pistons, jointed arms that hand the core up to the rift,
  an iris in the crown, and three rings that tumble round the hole and lock flat in the hush, all posed from
  the ritual's clock (`CradleWorks`). The hole and the rift shrank to fit inside the innermost ring
  (`HOLE_FULL` 0.85 to 0.45, `APERTURE_WIDE` 1.35 to 0.55) and the orbit moved outside the pillars (2.4 to 3.0).
- **The Fold Crucible is gone; the Collapse Cradle folds** (the user's choice, 2026-09-29). Phase 3 below is
  history. Folding is the cradle's lesser work: the arms hold up to sixteen, the cup a second ingredient, and
  each craft burns Rift Essence (100 mB a Folded Warp Crystal, 250 a Pearlescent, 50 a Ship's Compass) in a
  few seconds' animation, at `collapseFoldMinimumSpeed` or faster. No warp length is asked for any more, so
  the Mk III's act is a fold at a cradle - and so a found Relic Armillary - rather than a long jump; nothing
  folded goes into the cradle, which `ProgressionActTest` holds. The Ship's Compass comes out unbound and is
  bound by sneak-using it on a drive. The cradle also gained a panel (slots, tank, shaft, stabilisers, a
  heading dial and a Begin button beside the redstone pulse) and a failing look when a collapse heads for
  trouble.
  See README, "The cradle is forgotten technology".

---

## Where the crafting is now

- 28 recipe files, every one a bench craft or a stock Create step. **The mod has no recipe types of
  its own**, and **Rift Essence is in no recipe** — gates, probes, chutes, the Modulator and coupler
  folds spend it, but nothing is *made* from it.
- **You can build a Singularity without ever having warped.** Each tier is "one of the tier below plus
  more of the same" (RecipeBalanceTest holds each step to ×1.7–3.0). Tiers differ in quantity, not in
  what the player had to go and do.
- The warp shard is made by Create's haunting — the signature material is made by soul fire.
- Bounding numbers:
  - **Fissures** hold 6–18 buckets and drain at 12 mB/t (about a minute). They also turn nearby
    stone into Warp Crystal Ore (`maybeSeedOre`). That goes, by decision 5: fissures will never touch
    stone.
  - **Scars** come about one per 512 blocks across most overland biomes, with 1–2 fissures and no
    loot.
  - **A siphon** catches ~120–270 mB per jump, but *each siphon rolls separately*.
  - **Storms** come every 6–20 days and last 5–10 minutes. The server keeps only a raging flag and a
    calm countdown; lightning is client-only.
  - **Gate upkeep** is 10 + area mB/s.
- **Reach is not range.** `WarpCost.Formula.reach(mass)` exists because "the long-haul tiers never
  reach their range at all". On the shipped cost settings a Mk III's full charge buys ~51 km at 42,000
  mass, not 120 km.
- Hooks: `completeWarp` (RiftDriveBlockEntity ~3129–3171) harvests the siphons aboard, skips cruises,
  and runs for escorts, beacon summons and crossings. `RiftFissureBlockEntity.close()` (~274–285) is
  the seal and drops nothing. `RiftStorm.start(server, duration)` is reached only from `/weather`. The
  mod posts no events of its own.

---

## The idea in one line

**Each new drive is made from things that were carried to the edge of the last one's reach.**

The redesign asks for a different *act* at each step — **build**, then **open**, then **reach**, then
**collapse** — and every act is one of the mod's own verbs (warping, rifting, folding, sealing,
weathering), not more mining.

## Design rules

The load-bearing decisions, each short enough for a handbook page.

1. **The next drive is made from what the last one carried.** Crystal gains grades only by being
   folded. That takes one jump of at least half the previous tier's *reach* — what a full charge buys
   a reference hull — clamped to what the world allows.
2. **Anything that tears a rift is built with a lens that has seen one.** Drives from Mk II up, gate
   controllers, chutes, probes and couplers take an *opened* lens. The Mk I is blind — the first rift
   is the hardest.
3. **The Singularity is not built. It is collapsed — out of a scar.** Its core comes from a ritual that
   needs a Scar Heart, which only a sealed fissure yields.
4. **A ritual is an engineering ceremony.** It starts on a redstone edge and is refused with a named
   reason *before anything is spent*. It is visible from across the yard, and **failure hands something
   back**.
5. **Access, never numbers.** Nothing made changes a warp's tuned values (feature-ideas.md's rule for
   research). Grades unlock recipes; they never make a drive faster.
6. **Every gate has a second road.**
   - **Opened lenses:** a brazier's essence stands in for a found fissure.
   - **Storms:** they gate nothing.
   - **The Scar Heart** comes only from found scars (decision 3, which a Sunderer's tear does not change). Its second road is geography plus a
     tool: scars are common, and a **Scar Chart** leads to the nearest unvisited one. A world with none
     turns on the **Classic recipes** pack.
7. **Every process is a datapack recipe type with a JEI category.** Pack makers can hang their own
   progression on folding, rifting and collapse — impact in packs the mod never sees.

## Three sources of rift-stuff

| Source | What it is | What it gives | Machines and rituals |
| --- | --- | --- | --- |
| **The Throat** | the corridor a warp flies | grade, by distance folded | Fold Crucible |
| **The Scar** | a fissure torn and never closed | essence, a free standing rift, the Scar Heart | rifting at a fissure, the Seal, the Scar Chart |
| **The Storm** | the sky coming apart | rift rain, instability, the Stormglass | siphon rain, the Stormglass |

Storms gate nothing. They are rare, and only a Stormglass — which a storm must make first — can call
one. So they **modulate**, as they already do for warps (shorter cooldown, scattered exit): a storm makes
rituals faster and less stable, and rains essence into open vessels.

---

## The processes

Six processes. Five are the mod's own; one is stock Create steps put to work.

### 1. Rifting — a fan process that needs a standing rift

**What.** A new Create fan-processing type beside washing, smoking, blasting and haunting. Items blown
through a **standing rift** are rifted. It carries rule 2 literally: the thing a lens "sees" is a rift.

| Catalyst | Cost | Where it puts the process |
| --- | --- | --- |
| A **Rift Fissure** | free while it stays open | at a found scar |
| A **Rift Brazier**, lit by redstone | ~2 mB/s while lit | anywhere, once you have essence |
| An open gate's **Rift Portal** | the gate's own upkeep (~19 mB/s for 3×3) | on a depot or belt *behind* the pane |

The fissure row is what makes it matter. Today a fissure is drained in a minute and gone. Now it is
the best industrial site in the world, a free rifting catalyst for as long as it stays open, against
the essence and the Scar Heart it gives when sealed. Found ruins become places people build factories
around. Because that makes a fissure valuable, **a fissure must refuse to move.** The fissure's own
tick comment says a hull assembled around a tear takes it along. Register a
`BlockMovementChecks` refusal, as AstrolabeBlock ~244 does, and confirm Simulated's assembler honours
it.

A gate carries any entity through its pane, dropped items included (RiftGateBlockEntity ~993). Items
blown *through* the pane are posted to the far gate — the gate doing its job.

**Recipes** (`aerowarptics:rifting`):
- Rift Lens → **Opened Rift Lens**: the key one.
- Glass → **Warp Glass**: the mod's first building block.
- Warp Dust → Warp Shard: a second road. Haunting stays, so the Mk I never needs essence.

**Entities** in the stream take a short hop, like chorus fruit. It is server-side only, never an
entity carried by a ship or a Create contraption, and has a config switch.

**Code** (checked against Create 6.0.10):
- Register with `DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, MODID)`. It must be
  registered, because `FanProcessing` saves the type's key on the item entity.
- `isValidAt` checks for a fissure, a portal or a lit brazier.
- Priority goes between haunting (300) and smoking (200).
- `process(ItemStack, Level)` is not told which catalyst was passed, so the brazier burns by time,
  only while powered.
- The brazier needs a part-height collision shape, like a campfire's, or the air current stops at
  it.
- JEI: extend `ProcessingViaFanCategory.MultiOutput`, as haunting's category does.
- **A pool of Rift Essence is deliberately not a catalyst.** It would be one line, since fluids are
  passable, but a placed bucket would be a free catalyst forever and can be scooped back up.

### 2. Folding — the Fold Crucible

**What.** A sealed vessel bolted anywhere aboard a hull, the siphon's sibling. When the ship completes
a warp, the crucible's contents are **folded**. Recipes (`aerowarptics:folding`) ask for **one jump**
of at least a length, never a total. A total would reward bouncing between two anchors, which the
siphon's design already refuses.

**Threshold** = `fraction × min(tier formula.reach(referenceMass), range.maximumWarpDistance, level border size)`.

`referenceMass` is config, defaulting to 42,000, a small ship. On shipped defaults:

| Recipe | Fold needed | Opens |
| --- | --- | --- |
| Warp Crystal → **Folded Warp Crystal** | one jump ≥ ½ Mk II reach = **12 km** | Mk III |
| Folded Warp Crystal + Shulker Shell → **Pearlescent Warp Crystal** | one jump ≥ ½ Mk III reach ≈ **25.5 km** | Singularity; replaces superheated mixing |
| Compass → **Ship's Compass** | any completed warp | points at the ship it was folded aboard |

- **Config rescales everything.** Change a tier's cost, efficiency or range, or the border, and the
  recipes follow.
- **Mass is the pilot's problem, on purpose.** A 12 km Mk II jump costs 52% of a charge at 42,000
  mass and 90% at 203,000, and is impossible above ~245,000. The long fold is often flown by a light
  **courier** rather than the flagship. That is one more reason to build a second ship, and a trade
  ("rift courier") worth having on a server.
- **A dimension crossing never folds.** It is priced as `crossDimensionDistance` (1,000 by default).
  JEI and the handbook say so.
- **The Ship's Compass** is the smallest example of the rule that makes this worth doing: an item
  whose *function* depends on where it was made. It binds as `RiftBeaconBinding` does, and shares the
  beacon's one limit after a cross-dimension rebuild.
- **Crucible.** Four input slots, matched shapeless like a basin, and one output slot. Several aboard
  fold separately; the gate is the jump, not the quantity. Optional recipe conditions: `storm` (one
  raged at completion) and `fleet` (a shared-rift fold).

**Hook.** In `completeWarp`, inside the siphon's `if (!cruised)`:
- Build a `FoldContext` **before** `clearFold()` (~3149) and the distance reset (~3161). It holds the
  committed distance, tier, drive plot position and ship label, storm raging, and shared fold.
- Call `FoldCrucibleBlockEntity.fold(airship, context)`, found through `airship.machines(...)` as
  siphons are.
- **Wrap it in try/catch and trace it.** It reads datapack recipes in the warp's final steps, and an
  exception there would escape before the claim release, cooldown and holds.
- A cruise, an aborted warp and an INTERRUPTED warp credit nothing. The last is traced as "fold denied:
  interrupted", because the known large-hull stall will hit exactly these long jumps.
- Contents are block-entity NBT, so they survive a crossing, as the chute's `Tray` does.

### 3. The Seal and the Scar Chart

**Scar Heart.** When a fissure empties and seals, it gives one **Scar Heart**, whatever it held.
- In `close()`, after `AWCriteria.fissureClosed`, spawn it as an `ItemEntity` with
  `setUnlimitedLifetime()`. `Block.popResource` would drop nothing with `doTileDrops` off, and a tear
  often seals unwatched.
- The fissure must not get a loot table; ResourceIntegrityTest forbids one.
- On the mast ruin the heart lands on the mast head, eight blocks up. A small climb, kept.

**The decision it creates.** Leave a fissure open as a free rifting catalyst for as long as you like,
or drain it once for 6–18 buckets and a heart. Most will "use it, then close it", which is the story of
a scar. The fissure never touches the ground around it (decision 5), so the choice is between those two
alone.

**Scar Chart.** Scars are only ever found, so finding one needs a tool.
- **Recipe:** a bench craft of an Empty Map, a Rift Lens and an Amethyst Shard, with no essence, so
  it is open to the player with no drive.
- **Use:** opened, it becomes an explorer map to the nearest **unvisited** scar. It works as vanilla's
  treasure maps do: `findNearestMapStructure` over `#aerowarptics:on_scar_maps`, skipping known
  structures, located from structure placement without loading chunks.

### 4. The Collapse — the Singularity Core ritual

The centrepiece.

**Fiction.** A Singularity is a rift folded shut on itself — a hole with no far side. The collapse has
to fall *into* something, and only the heart of a wound that has already closed can hold it. (If the
Codex, feature-ideas #16, is ever built, this is P.'s last experiment, and the standing derelict is
what it left.)

**The site.** A **Collapse Cradle**:
- a kinetic block with a shaft from below;
- slots for the Rift Core and the Scar Heart;
- an essence tank on a pipe.

Around it stand three kinds of block:
- **Eight Create Depots** in a ring hold the offerings. The ritual is visible — each offering lifts off
  and is drawn in — and automatable, since Mechanical Arms load depots.
- **Spatial Stabilisers**, switched on by redstone, damp the collapse. It is their second job, the
  same verb they do aboard ship.
- **A Rift Modulator** beside the cradle dresses the ritual's rift.

**Rotation is the Singularity's own.** It reads that tier's config:
- below the minimum (128 RPM), the cradle refuses;
- below optimal (256 RPM), it raises instability;
- at optimal it draws 24 su/RPM, 6,144 su.

If you cannot turn the cradle, you cannot fly the drive it makes.

**Sequence (~60 s, started by a redstone rising edge):**
1. **Pre-flight.** Named `CollapseFailure` refusals, with nothing spent:
   - no core, or no Scar Heart ("nothing for it to fall into");
   - the offerings match no recipe;
   - the tank cannot pay for the whole ritual;
   - rotation below minimum;
   - aboard a ship.
2. **Spin-up.** The core lifts and the stabilisers light.
3. **Opening.** A rift opens above the cradle under a holder id it renews, as a fissure's client rift
   is held. Offerings are drawn in and essence drains.
4. **Winding.** Instability accumulates from live conditions.
5. **Collapse.** The rift falls in on itself (the theme's own closing, a flash, the exit shockwave).
   `SpatialWaveDetectorBlockEntity.announce` pulses detectors, so an arm can collect the result.

*Redone as a set piece (2026-09-28, the user's choices):* a **landmark** - a column of light 320 blocks
into the sky, the day dimmed for 160 blocks round, the boom heard at 128. The rift folds into a **black
hole** at 40%: a ball of darkness with the offerings in orbit and a disc of light round it, space bent past
its edge, lightning to the stabilisers, a Scar Heart beating faster; a silent **hush** for the last 5%,
when everything falls into a point; then a flash, a boom and a ring of air. The player **watches** - setup
still decides - but how unsteady it is shows throughout, from where instability is *heading*. It **pulls**
loose things and players in as it winds and **knocks back** everything at the end, and a flung or spat
collapse **hurts** whoever is too close (less behind cover; never blocks, items or armour stands). See
`CollapseChoreography`, `CollapseSpectacle`, and `collapsePullRange` / `collapseBlastRange` /
`collapseBlastDamage`.

**Instability** (`CollapseInstability`, pure, shaped and tested like `StabiliserCushion`):
- **Rises** with a raging storm (which also shortens the ritual, cutting both ways as it does for
  warps), rotation below optimal, and a tank running short.
- **Falls** with the number of active stabilisers, down to a floor, and with their **balance**: the
  length of the sum of their offsets from the cradle, over the count. A ring of opposites cancels, and
  a lopsided cage wobbles — the stabiliser's "they do not stack" lesson again: symmetry, not quantity.

**Outcomes** (bands in config):

| Final instability | Result |
| --- | --- |
| low | the Singularity Core, on the cradle |
| middling | the core, **flung** up to N blocks — it "declines to be precise", the Singularity's vice at its birth; it glows, and the goggles find it |
| high | **spits**: the Rift Core and every offering thrown out whole around the cradle; the **heart and the essence are spent** |

**Why the heart is the stake, not the offerings.** The offerings are the grind: pearlescent from a long
Mk III fold, end crystals and echo shards. The heart is the adventure: find another scar, which the Scar
Chart makes a trip rather than a search. Draining that scar refunds more essence than the ritual burned.
So failure costs a journey, never the long work — and a journey is the mod's thesis.

**Chunks.** The cradle holds its ground through `Holds.ground` and the expiring `aerowarptics:reach`
ticket for the ritual's length.
- **Unload pauses it.** A ritual whose chunk unloads anyway pauses; a player logging out must never
  spend a heart.
- **A grace window on resume.** Create reports zero speed for the first ticks after a reload, and a
  level with no players stops ticking block entities after 300 ticks, so pauses will be routine.

**Recipe** (`aerowarptics:collapse`), in Create's `ProcessingRecipeParams` shape. Slots are by position
(0 = core, 1 = heart), so the producer test can see the heart:

```json
{"type":"aerowarptics:collapse",
 "ingredients":[{"item":"aerowarptics:rift_core"},{"item":"aerowarptics:scar_heart"},
   {"item":"aerowarptics:pearlescent_warp_crystal"}, "…one entry per item…",
   {"type":"neoforge:single","amount":4000,"fluid":"aerowarptics:rift_essence"}],
 "results":[{"id":"aerowarptics:singularity_core"}],
 "processing_time":1200, "drive_tier":"singularity"}
```

Built in: Rift Core + Scar Heart + 4 Pearlescent + 2 End Crystals + 2 Echo Shards + 4,000 mB. That is
today's ingredients, plus a heart and a full siphon (~15–30 jumps, or one found fissure).

### 5. Storm work — rift rain and the Stormglass

**Rift rain.** The storm's rain "is not water" (README). A Spatial Siphon under open sky in a Rift
Storm fills with it: a third job for a glass vessel left out in the rain.
- **Rate:** ~1 mB/s, tripled above cloud height (a config Y, since the server cannot read the
  client's cloud height).
- **Shared, never stacked.** Rain is a per-chunk budget shared between the siphons in the chunk, and
  per ship aboard, exactly as a fissure shares its drain ("a ring of siphons fills no faster than
  one").
- **Aboard a ship,** sky is tested in plot space ("is it under its own deck?"), and altitude uses the
  siphon's *world* Y through the ship's pose.

It gives players without a drive a road to essence, and a reason to fly into the storm.

**The Stormglass** — FitzRoy's storm glass, rift edition.
- **Making one.** A spout fills a Glass Bottle with 500 mB to make an **Unset Stormglass**. It sets
  only under open sky in a Rift Storm: held by a player standing out in it for half a minute
  (`inventoryTick`), or dropped (`onEntityItemUpdate`). Made by a storm, it knows storms.
- **Read it.**
  - *Clear:* no storm within a day, or the calm is not yet rolled.
  - *Gathering:* the next storm is within a day.
  - *Raging.*
  - *Still:* natural storms are off or weather is frozen, so none will come.

  The reading is honest because the server knows the calm. It rides the storm packet and is resent
  whenever the reading changes, not only when a storm begins or ends (bump `AWNetwork.VERSION`).
- **Break it** to call a storm through `RiftStorm.start()`, the first non-operator way. A storm is
  server-wide and changes every pilot's warps, so calling one is a public act.
  - **Refused** while a storm rages (`start()` would reset it, shortening a raging storm), while weather
    is frozen (a called storm would never end), and within a minimum calm since the last storm ended.
  - **Forced off** when natural storms are off: the operator chose no storms.
  - `riftStorm.callable` defaults on (decision 4).

"The only thing that can call a storm is something a storm has made."

### 6. Stock Create steps, put to work

- **Rift Core by sequenced assembly.** On a Precision Mechanism: deploy Electron Tube, deploy Warp
  Crystal, deploy Brass Sheet, press, two loops, through a transitional **Incomplete Rift Core**. The
  same materials as today's mixing, as a production line, with lottery scrap in Create's style.
  Simulated's gyroscopic mechanism is the precedent in this stack.
- **Rift Pearl** (spout: Ender Pearl + 250 mB; emptying gives 200 mB back — the loss is a sink). It is
  essence in item form, so it can enter bench and mechanical-crafter recipes that cannot take fluids.
  It is also a denomination to trade, and thrown it hops what it hits like a chorus fruit. Consumed by
  the **Rift Beacon** (one pearl replaces a brass sheet — a thing that summons should carry a pearl)
  and the **Collapse Cradle**.
- **Rift Casing** (item application: Warp Dust on a Brass Casing). The addon's own casing, used by every
  machine that takes a brass casing, and a building block alongside Warp Glass.
- **Gyroscopic Mechanism** (Simulated) replaces the Spatial Stabiliser's electron tubes; the block
  already wears a gyroscope.

---

## The ladder, reshaped

| Tier | Act | Gate | Where it comes from |
| --- | --- | --- | --- |
| **Mk I** | build | nothing new: plain lenses, a Rift Core off an assembly line | ground and factory only, with warp dust from the mixer (amethyst + ender pearl); **no essence and no rift in its tree** (tested) |
| **Mk II** | open | Opened Rift Lenses | rifting at a brazier (essence from a siphon on Mk I jumps) or at a found fissure |
| **Mk III** | reach | Folded Warp Crystal ×6 | a crucible aboard for one jump ≥ 12 km |
| **Singularity** | collapse | Singularity Core; Pearlescent ×8 | the Cradle with a Scar Heart; one Mk III jump ≥ ~25.5 km |

Rift-openers take an opened lens: gate controller, chute, probe, coupler. Things that receive, listen,
see or dress a rift keep the plain lens: anchor, detector, goggles, siphon, modulator, beacon.

Existing worlds keep their built drives and their stacks. Only *future* crafts change, and any world
can turn on the Classic pack.

## Recipe by recipe

| Item | Today | Proposed |
| --- | --- | --- |
| Rift Core | heated mixing | sequenced assembly, two loops, via Incomplete Rift Core |
| Opened Rift Lens · *new* | — | rifting a Rift Lens |
| Warp Glass · *new block* | — | rifting Glass |
| Warp Shard | haunting | haunting; rifting as a second road |
| Folded Warp Crystal · *new* | — | folding: one jump ≥ ½ Mk II reach |
| Pearlescent Warp Crystal | superheated mixing | folding Folded Warp Crystal + Shulker Shell: one jump ≥ ½ Mk III reach |
| Singularity Core | mechanical crafting | collapse (above) |
| Scar Heart · *new* | — | a sealing fissure |
| Scar Chart · *new* | — | bench: Empty Map, Rift Lens, Amethyst Shard |
| Rift Pearl · *new* | — | filling Ender Pearl + 250 mB; emptying → 200 mB |
| Unset Stormglass / Stormglass · *new* | — | filling Glass Bottle + 500 mB; sets in a storm |
| Ship's Compass · *new* | — | folding a Compass, any completed warp |
| Rift Casing · *new block* | — | item application: Warp Dust on Brass Casing |
| Rift Brazier · *new block* | — | bench: Rift Casing, Brass Sheets, Warp Crystal (needs no opened lens) |
| Fold Crucible · *new block* | — | bench: Rift Casing, Glass, Warp Crystals (Mk I era) |
| Collapse Cradle · *new block* | — | mechanical crafting: Rift Casing, Precision Mechanisms, Stabiliser Rings, Opened Lenses, Rift Pearls |
| Mk II / Mk III / Singularity | as today | lenses → opened; Mk III's crystals → folded; the Singularity's pearlescent and core by the new roads |
| Gate controller, Chute, Probe, Coupler | plain lens | opened lens |
| Rift Beacon | brass sheet on top | a Rift Pearl on top |
| Spatial Stabiliser | electron tubes | Gyroscopic Mechanism |
| Machines taking a Brass Casing | Brass Casing | Rift Casing |

**Balance.** With Rift Casing at 5 and the new processes priced at 0, the ladder stays in band: Mk I
68.5 → Mk II 145.5 (×2.12) → Mk III 290.5 (×2.00) → Singularity 610 (×2.10). Pearlescent keeps its
fixed effort price; priced through its fold recipe, twelve in the tree could push the last step past
×3.0. Fixed prices prove nothing about the acts, so **ProgressionActTest carries those.**

---

## What it touches

| Package | New code |
| --- | --- |
| `registry/` | `AWRecipeTypes` (an `IRecipeTypeInfo` enum, like Create's `AllRecipeTypes`), `AWFanTypes`; new blocks, items, block entities, capabilities |
| `rifting/` | `RiftingType` (`FanProcessingType`), `RiftingRecipe` (`StandardProcessingRecipe`), `RiftBrazierBlock` and its block entity |
| `fold/` | `FoldCrucibleBlock` and its block entity, `FoldingRecipe` with its params, `FoldRules` (pure; takes a `WarpCost.Formula`, never reads config), `FoldContext` |
| `collapse/` | `CollapseCradleBlock` and its block entity, `CollapseRecipe` with its params, `CollapseRules` and `CollapseInstability` (pure), `CollapseFailure` |
| `fissure/` | the heart in `close()`, the movement refusal, `ScarChartItem`, the `#aerowarptics:on_scar_maps` structure tag; `maybeSeedOre` and its `Seeded` count removed |
| `weather/` | `StormForecast` (pure), `StormglassItem`, a saved last-ended time, the non-operator start path |
| `siphon/` | one shared draught per jump; rift rain |
| `pack/` | the **Classic recipes** pack via `AddPackFindersEvent`, **`PackSource.FEATURE`** (opt-in; `BUILT_IN` would switch on in every world) |
| `compat/` | three JEI categories (`CreateRecipeCategory.Builder`, `ProcessingViaFanCategory.MultiOutput`), Display Link sources for the three machines |
| `drive/` | the fold call in `completeWarp` |
| `network/` | a forecast state on the storm packet; a fixed-size per-tier reach table + border for JEI, resent on config reload |
| `advancement/` | triggers `fold_completed`, `collapse_finished`, `storm_called` |

**Reuse:**
- `Airship.machines` (`airship/Airship.java:919`) and `SpatialSiphonBlockEntity.harvest` (`:198`) — the
  crucible's lookup and its per-hull pass, trace line included.
- `WarpCost.Formula.reach` (`warp/WarpCost.java:89`).
- The live border as `RiftProbeBlockEntity` ~306 reads it.
- `RiftBeaconBinding`.
- `StabiliserCushion` — the shape of the pure instability class.
- The fissure's client-held rift.
- `SpatialWaveDetectorBlockEntity.announce`.
- `Holds.ground` and `RiftStorm.start`.
- `BlockMovementChecks` (as AstrolabeBlock ~244).
- `AWCriteria` and the generators in `tools/`.
- From Create: `StandardProcessingRecipe`, a `ProcessingRecipeParams` subclass (as
  `ItemApplicationRecipeParams`), a depot's held item.

**Resources:**
- `recipe/<block>.json` at top level plus a loot table for every new block (ResourceIntegrityTest).
- GeckoLib models for the crucible, cradle and brazier, through `tools/` scripts.
- Hand-drawn icons for the new items: opened lens, folded crystal, heart, chart, pearl, both
  stormglasses, compass, incomplete core.
- Lang, Ponder and advancements through their generators.

## Invariants this has to keep

- **Redstone starts the cradle and lights the brazier.** A refusal comes before any spend, with a
  named reason and a `Trace.log` line with the numbers; every fold credited or denied is traced too.
- **`AWConfig` owns every tunable**; the client never reads it. Thresholds and the forecast arrive by
  bounded, fixed-size packets.
- **Only `Holds` loads chunks.**
- **GeckoLib machines use `DrawnAboard`.** All three — the crucible, the brazier *and* the cradle,
  which refuses to run aboard but can be placed there — call `DrawnAboard.redraw` and
  `DrawnAboard.offScreen`, override `getRenderBoundingBox`, and have item renderers in `AWClientSetup`.
- **Pure classes never name a `DeferredHolder`** or read config.
- **Nothing new is keyed by ship UUID.** The compass binds by plot position, as the beacon does.
- **Handbook chapters stay even;** Ponder text stays generated.

## Changes to existing behaviour

These alter what players already have, and each is a changelog line:

0. **Fissure ore is retired** (decision 5).
   - **Code and config:** `maybeSeedOre`, the `Seeded` NBT count, and the four `fissure.*` ore keys
     in `AWConfig`.
   - **Registry:** Warp Crystal Ore (block and item) and Raw Warp Crystal in `AWBlocks`, `AWItems`
     and `AWCreativeTab`.
   - **Data:** `warp_dust_from_crushing.json`, the ore's loot table, and its entries in the
     `mineable/pickaxe` and `needs_stone_tool` tags.
   - **Assets:** the blockstate, models and textures, and the lang names.
   - **Ponder:** the "Where Warp Crystals Come From" scene (`FissureScenes.crystals`, its entries in
     `AWPonderScenes` and `AWPonderTags`, `warp_crystal_ore.nbt`, and its lines in
     `tools/ponder_schematics.py` and `tools/ponder_lang.py`).
   - **Docs:** the README's two Ponder mentions (~1679, ~1704) and the 1.3.1 CHANGELOG lines (26, 71).
   - **Tests:** `PonderSceneryTest`, and `raw_warp_crystal` in RecipeBalanceTest's price table.
   - **Consequences:**
     - Warp dust comes only from the mixer, so every crystal costs half an ender pearl.
     - The retirement fixes the output-key collision between warp dust's two recipes.
     - **The ore arrived in 1.3.1, which is unreleased.** Retired before 1.3.1 ships, no released
       world has it and nothing needs migrating. If 1.3.1 ships first, map the old ids with NeoForge
       21.1's registry aliases (check `DeferredRegister.addAlias`), or keep a hidden legacy block for
       one release.
1. **Siphons share one draught per jump.** One per-jump capture is split across the siphons aboard;
   more siphons add room, not yield, as a fissure's drain already works. The per-siphon roll's own
   comment allowed it only because "there is no rate to balance yet". This makes essence a real price.
2. **Fissures cannot be carried off** by an assembled hull.
3. **Pearlescent and the Singularity Core** move off their Create recipes.
4. **The Rift Storm keeps a last-ended time,** and its packet carries a forecast state.

## Tests

- **RecipeBalanceTest**, fixed first, since Phase 1's first file would otherwise break every test in
  it:
  - read `ingredient` (sequenced assembly) and Create's fluid entries;
  - count step extras per loop;
  - skip `create:emptying`, whose item-first results key it as a cycle;
  - key outputs to a **list**, since the output-keyed map already hides warp dust's second recipe;
  - price the new raw inputs: ender pearl, glass bottle, compass, map, scar heart, gyroscopic
    mechanism and the fluid;
  - walk the Classic pack too, and run the old ladder assertions on it.
- **ProgressionActTest** (new): the design as a guard.
  - An **OR** over recipes: the Mk I has a route with no essence, opened lens, fold or collapse.
  - An **AND**: Mk II needs an opened lens; Mk III needs a product of folding at ≥ ½ Mk II reach; the
    Singularity needs a collapse product and a fold at ≥ ½ Mk III reach.
- **IngredientProducerTest** (new): every ingredient has a producer — a recipe of any type, a loot
  table, or a declared world source (the seal, the storm). Other-mod ids are checked
  against the jars in `Depends/`, skipped when absent as the Dimensional Sable test is. The levitite
  ids that once shipped were another mod's.
- **FoldRulesTest:** thresholds follow `reach(referenceMass)`, clamp to the maximum warp distance and
  border, and each named tier can afford its own threshold at the reference mass. A single jump
  counts, never a total. A source-read check puts the fold call inside `if (!cruised)`, before the
  resets.
- **CollapseRules/InstabilityTest:**
  - a balanced ring cancels and a lopsided one does not;
  - a storm raises instability and shortens the ritual;
  - the outcome bands;
  - no heart is a refusal, and every refusal comes before any spend;
  - a spit returns the core and offerings and spends only the heart and the essence;
  - an unload pauses, a pause never resolves, and the resume grace window holds.
- **StormForecastTest** (the four readings, and a calm of 0 reading clear) and the call rules:
  refused while raging, frozen or inside the minimum calm, and forced off when natural storms are off.
- **SiphonPoolTest:** yield per jump is the same whatever the number of siphons.
- **RiftFissureTest:**
  - a seal spawns exactly one heart, with an unlimited lifetime;
  - the fissure refuses movement;
  - the "rolled rather than stored" comments still hold;
  - **a fissure changes no block but its own.** This is a source-read guard in the codebase's style:
    `RiftFissureBlockEntity` sets no block except removing itself, so seeding cannot quietly come back.
- **Extend:** the hand-kept lists in ResourceIntegrityTest, the enum families in LangCoverageTest
  (`CollapseFailure`), RenderBoundsTest, DrawnAboardTest, CapabilityCoverageTest.

## Build order

Each phase ships alone and leaves the ladder consistent.

0. **Retire fissure ore.** It stands alone, and is best done **before 1.3.1 is released**, so no
   migration is needed (see "Changes to existing behaviour" 0). Update anything else that still
   describes the ore chain, the wiki's pages included.
1. **Foundations.**
   - The RecipeBalanceTest fixes, IngredientProducerTest and ProgressionActTest, with its Mk I guard.
   - The shared siphon draught.
   - The Classic pack, set to `FEATURE`.
   - The Rift Core by sequenced assembly; the Rift Pearl, and the beacon taking one; Rift Casing; the
     gyroscopic stabiliser; an empty `AWRecipeTypes`.
2. **Rifting and the Seal.**
   - The fan type, recipe type and JEI; the brazier.
   - The Opened Lens and Warp Glass, with the rift-openers switched to opened lenses.
   - The Scar Heart, the Scar Chart, and fissures refusing to move.
3. **Folding.**
   - The crucible and its recipe type; `FoldRules` on real reach; the reach packet and JEI.
   - The Folded Crystal; pearlescent moves to folding; the Ship's Compass; the Mk III takes folded
     crystal.
4. **The Collapse.** The cradle, its rules, instability, recipe type, JEI, Ponder and client rift; the
   Singularity Core moves.
5. **Storm work.** The storm-cycle changes (last-ended time, the four readings, update on change),
   siphon rain, the Stormglass. Storms gate nothing, so this can slip without harm.

Teaching ships with each phase — the mod's "standing cost":
- a handbook chapter, *Rift craft*: rules, then rifting, folding and the collapse;
- a Ponder scene per machine;
- JEI info pages;
- advancements: *Seen a Rift*, *Crossed the Line*, *Heart of a Scar*, *It Declines to Be Precise*,
  *Stormcaller*;
- CHANGELOG lines and the README's "Crafting" section.

## Verification

- **Tests.** `./gradlew test`, with `JAVA_HOME` set as AGENTS.md's "Commands" describes. Read the
  output, not the exit code.
- **In game, per phase,** with `/aerowarptics trace on`, reading `latest.log`:
  - **Ore retired:** a fissure left loaded for an hour beside natural stone changes nothing round it,
    and the mixer still makes warp dust.
  - **Rifting:**
    - lenses convert through a brazier (only while powered) and through a fissure;
    - a player in the stream hops, and a rider on a ship does not;
    - a depot behind a gate's pane rifts, and items blown through arrive at the far gate
      unprocessed;
    - a hull assembled round a fissure leaves it behind.
  - **Folding:**
    - a light Mk II's crucible converts at 12 km and not at 11 km;
    - a light Mk III makes pearlescent at ~25.5 km;
    - cruises, aborts and crossings credit nothing;
    - a small border lowers JEI's number;
    - contents survive a crossing.
  - **Collapse:**
    - a balanced ring settles, a lopsided one flings, and a storm speeds it up and destabilises it;
    - no heart refuses;
    - a spit scatters the core and offerings intact;
    - logging out pauses it, and resuming does not spike;
    - detectors pulse at the end.
  - **Storm:**
    - ten siphons on a deck fill no faster than one;
    - a deck above the clouds fills faster than the ground;
    - a held glass sets;
    - a broken glass calls a storm, and is refused while one rages or inside the calm;
    - the glass reads *still* with weather frozen.

## Considered and set aside

- **Seasoned drives** (a warp-count gate). Folding is the physical version. A per-ship log in
  `AirshipWarpData`, which already crosses dimensions, is a good later idea.
- **A Resonance Bench** (exact-RPM ring tuning). A machine for one ingredient; the cradle already
  demands the Singularity's real power.
- **Twin-lens chute pairing.** Chutes already pair by panel.
- **Theme plates and earned themes.** Feature-ideas #3.
- **Throat trawl flotsam.** The debris is generic; this is feature-ideas #15, and `RiftDebris` is
  server-computable.
- **A storm vane and stormstruck crystal.** Lightning is client-only.
- **Essence grades.** The pearl is the denomination.
- **A Primed Rift Core.** Opened lenses already make Mk II need essence.
- **The `crossed` fold condition.** Nothing records a crossing at completion; it would need a saved
  flag for an optional feature.
- **Lens wear, and quality that changes stats.** A maintenance tax, and a breach of rule 5.

## Decisions (2026-09-27)

1. **The reshaped ladder is the default.** The **Classic recipes** pack restores today's recipes per
   world — opt-in, `PackSource.FEATURE`. Not a config switch: recipes load before a world's server
   config, and there is no common config.
2. **Built in order.** Recorded first, then built phase by phase on 2026-09-28; see Status at the top.
3. **Scars stay found, never made.** No code places a fissure; a failed Collapse spits its inputs back
   and spends only the heart and the essence.
   *Amended by the owner, 2026-10-16:* a **Rift Sunderer** may tear a fissure at home - a single-use heart for a
   ring of gate frame, `docs/rift-sunderer-plan.md`. What it tears is *conjured*: it seals with **no Scar Heart**,
   gives a siphon nothing, and gutters shut unless a Rift Wellhead holds it. So the heart, and with it the
   Singularity, still comes only from a found scar; what is opened up is the wellhead, at home.
4. **Players may call storms, on by default.** The call is refused while a storm rages, while weather
   is frozen and inside a minimum calm, and forced off when natural storms are off. A review pass
   suggested default-off, because `/weather` is operator-only; the author's choice stands, with
   `riftStorm.callable` for operators who disagree.
5. **Fissures never touch stone.** Ore seeding is removed, and Warp Crystal Ore and Raw Warp Crystal
   are retired; warp dust comes only from the mixer. (The code did seed ore — `maybeSeedOre`, on by
   default, taught by a Ponder scene — so this is a change, not a description.) An optional second
   dust road, rifting Amethyst → Warp Dust, would ease the ender-pearl demand after the first warp.
   It is *not* adopted, because the mixer was chosen as the single source.
