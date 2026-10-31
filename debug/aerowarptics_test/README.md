# AeroWarptics test bench (datapack)

A reproducible debugging arena for the mod. Drop it into a world, run one command, and
get every machine laid out, labelled, powered where it makes sense, and stocked — plus
two targeted bug repros. This is meant to be the base of our in-game debugging going
forward, so it lives in the repo next to the mod.

## Install

1. Copy the whole `aerowarptics_test` folder into your test world's datapack folder:
   ```
   <instance>/saves/<world>/datapacks/aerowarptics_test/
   ```
   (The folder that contains `pack.mcmeta` must sit directly under `datapacks/`.)
2. In game: `/reload`
3. Stand where you want the middle of the pad, then:
   ```
   /function aerowarptics_test:build
   ```

Everything is placed **relative to your feet**, so your facing does not matter and you
can build it anywhere. Re-running rebuilds cleanly over the top.

## Functions

| Function | What it does |
| --- | --- |
| `aerowarptics_test:build` | Builds the 31×31 pad and every station, plus a far anchor 200 blocks east. |
| `aerowarptics_test:kit` | Gives one of everything straight into your inventory. |
| `aerowarptics_test:clear` | Removes the pad and far-anchor pad (run from the build spot). |
| `aerowarptics_test:stress_anchors` | Places 272 Warp Anchors 300 east — repro for the >256-anchor `EncoderException`. |
| `aerowarptics_test:rails` | Builds the rail-gate bench: three gates round a shared hub, four lanes, routes already written. Run it on clear ground, away from the pad. |
| `aerowarptics_test:rails_essence` | Refills all three rail-gate tanks and touches nothing else (run from the `rails` spot). |
| `aerowarptics_test:rails_clear` | Tears the rail-gate bench down and frees its three gate ids (run from the `rails` spot). |

## Map (looking down; N is −Z, you are `@`)

```
                    N  (−Z)
        [Astrolabe 3x3]
                    |
 [Drive+motor]      @  [barrels]      [Warp Anchor near] ---> 200 blocks E: [Far anchor]
      (W)         (you)                     (E)
                                       [Rift Gate ring]
 [Chute+depot]    [Probe]              [Fissure+Siphon]
                    S  (+Z)                  (SE)
```

## Stations and what each is for

- **Rift Drive on live rotation** (west) — a `create:creative_motor` spinning a
  creative-tier `rift_drive`, with a `rift_modulator` beside it and a lever. Scroll the
  motor to set RPM; watch the drive take stress and the modulator's lens react. The drive
  will **not** warp here — it has to be part of an assembled hull first (see below).
- **Astrolabe** (north) — a 3×3 chart table that auto-forms on placement. Set and quote
  courses here. Permission checks live here too.
- **Warp Anchor, near** (east) — a course target close enough to select but inside the
  128-block minimum, so it is good for course-setting/quoting, not for an actual jump.
- **Rift Gate ring** (east) — a closed ring with a 3×3 opening (area 9; the code accepts
  4–225 with aspect ≤4). Pair it with a second gate to dial a connection.
- **Rift Fissure + Spatial Siphon** (south-east) — the fissure is invisible and intangible
  unless you are **wearing Rift Infused Goggles** (in barrel 1). The siphon drains it.
- **Rift Probe** (south) — scan ahead to produce a fix (an anchorless course).
- **Rift Chute + depot** (south-west) — drop items on the depot to watch the chute take them.
- **Supply barrels** (by you) — barrel 1: handbook, goggles, beacon, cores/lenses/rings,
  one of every drive tier. Barrel 2: the machine block-items to build your own layouts.

## The one thing you must do by hand: an airship

A warp cannot be scripted, because a Rift Drive only flies when it is part of an assembled
Create: Aeronautics sub-level — and only the assembler can make one. To test an end-to-end
warp / the `INTERRUPTED` stall:

1. Take a `rift_drive` from the barrel and build a small hull around it.
2. Assemble it into an airship (Aeronautics assembler + controller).
3. Set a course to the **far anchor** (200 east, ≥128 min) at the Astrolabe.
4. Trigger the drive with a redstone rising edge.

## The rail-gate bench: `rails`

Trains through gates need a few hundred blocks of yard, so this is its own function rather than
part of the pad. Stand on clear ground well away from the pad — it reaches 42 blocks west of you
and 266 east, 6 either side — and run:

```
/function aerowarptics_test:rails
```

You stand where **GATE A** goes. Three gates, every ring across Z with a 5×3 opening, each sunk so
the opening's bottom row is at walking level:

```
  z−3   . . . . . the motors and the hub's lever stand on this line . . . . .
  z−2   ====track====[A]...................[HUB]  gap  ====track====>
  z−1   ====track====[A]...................[HUB]  gap  ====track====>
  z 0      walkway    A   no track in this column   HUB      walkway
  z+1      <====track====  gap  [HUB]...................[B]====track====
  z+2      <====track====  gap  [HUB]...................[B]====track====

        −40      @0                       +112                    +224      +264
```

| gate | x | lanes in | routed to |
| --- | --- | --- | --- |
| A | 0 | columns 0 and 1 (z−2, z−1), from the west | the HUB |
| HUB | +112 | none | nothing — it needs none |
| B | +224 | columns 3 and 4 (z+1, z+2), from the east | the HUB |

**A gate takes one lane per column of its opening, by itself.** `noticeTracks` walks every column
every 20 ticks and makes a lane of any track running into that column's bottom cell, so two
parallel tracks into one gate are two lanes and nothing in the function has to say so. Column 2 is
left trackless on purpose: it stays a clear walk-through, so the same ring serves the pedestrian
path as well.

**The routes are already written.** The function gives each gate a fixed `GateId` and writes
`Destination` straight into the block entity, so the lanes lay themselves about two seconds after
the command finishes — no dialling, no clicking. The trick is the order, three commands per gate:
the ring goes down a block short, the id and the route are merged into a gate that has not formed
yet, and only then is the ring closed. A gate mints a random id the first time anything asks for
one and `tryForm` asks as the controller is placed, so closing the ring first would quietly
register three random ids whose routes point at nothing. The block held back must be a **mid-edge**
one, not a corner: the flood fill is orthogonal within the plane, so a missing corner is still
diagonal from every interior cell and the ring would form anyway.
`everyRingIsClosedOnlyAfterItsIdIsWritten` in `TestBenchTest` is what holds that order in place.

If a dial says there is no route, the merge did not take: set the routes by hand at the two origin
dials (pick the HUB, press **Set Route**) and the rest of the bench is unaffected.

**Why 112 blocks apart.** `RailPreDial` fires on distance, a heading dot product, and the track
being ahead — never on the train being *on* that lane, as its own comment admits. Within one route
that costs nothing, because all of a gate's lanes share its route and the second ask finds the gate
already engaged. Across two routes it is not free: a train running one route would dial the other
route's gate for nothing, at 430 mB a time. 112 keeps every point of either approach outside
`preDialDistance` (96) of the other route's stub. Close that gap and you reproduce the cross-talk.

**The ground left empty.** A lane's far end is the mod's to lay: a stub out of the far gate's
matching column, then `apronLength` (2) blocks of apron. The column mapping between two gates of
the same width and the same orientation is the identity, so A's lanes come out of the HUB at
**+113 … +115** on z−2 and z−1, and B's at **+109 … +111** on z+1 and z+2. Do not build there.

### One bench per world

The gate ids are fixed, which is what lets the routes be written — and it means a second bench in
the same world would collide with the first. `registerSelf` re-registers an id it already knows
with `existing.withShape(shape)`, and `RiftGate.withShape` keeps the **old** controller position,
so all three records would end up pointing at the first bench's controllers. Run
`aerowarptics_test:rails_clear` before building another. Re-running in the same spot is fine: the
clear at the top of `rails` unforms the old rings first, which unregisters them.

### The four things no command can do

Down from five — the routes used to be on this list. These are all Create's own screens:

1. **Place the four Train Stations** from the barrel. **Two clicks each**, and the first one
   decides everything: right-click the marked track **while looking away from the gates**, then
   click anywhere within 16 blocks to set the block down. `TrackTargetingBlockItem.useOn` takes
   the station's direction from `Player.getLookAngle`, not from the face you clicked, and the
   block itself need not be beside the track at all — only `closerThan(target, 16.0)`, or it
   refuses with `track_target.too_far`. Create binds a station into its own edge-point registry
   as it is placed, which `setblock` cannot.
2. **Name them** `WEST`, `HUB EAST`, `EAST` and `HUB WEST` — what the legend's schedules assume.
3. **Make the bogeys, then build a train** where the station's own assembly ghost shows. There is
   **no bogey item** — "Create new bogeys by using Train Casing on Tracks", says Create's own
   ponder, and clicking the track again cycles the design. So: Train Casing on the track twice,
   then the carriage built over both bogeys in Train Casing, Train Controls, and a Blaze Burner
   **on the side the Controls faces**. Then **Assemble**. The barrel stocks no bogey for this
   reason; it carries 32 Train Casing instead, which does both jobs.
4. **Write the schedules** — `WEST` then `HUB EAST`, cyclic; `EAST` then `HUB WEST`, cyclic — and
   right-click each burner with one.

### Which way each station faces, and why it matters

> Mind that a Station can only be approached from the indicated direction

— Create's own ponder text. That direction is whichever way you were looking when you targeted
the track, so on a there-and-back schedule the two stations on a route must face **opposite**
ways. Here that means each one looking **away from the gates**:

| station | x | on | look |
| --- | --- | --- | --- |
| `WEST` | −20 | z−1 | west |
| `HUB EAST` | +138 | z−1 | east |
| `HUB WEST` | +86 | z+1 | west |
| `EAST` | +244 | z+1 | east |

Get one backwards and the train will cross the gate perfectly well, fail to dock, and sit there
— which reads exactly like a gate bug and is not one. Each spot sits mid-run so there is track
either side of it for Create to assemble a train on, whichever way round it ends up.

### What each route exercises

- **Both schedules at once** is the headline, and the reason there are three gates: both routes want
  the HUB, so the second train reaches `RailGateLogic.Call.WAIT` with `DESTINATION_BUSY` and asks
  again every `retryTicks` (40). A two-gate bench cannot reach that arm at all.
- **Two lanes per origin** puts two trains through one gate pair at once, each in its own corridor
  slot, 256 apart. The dial's **Rails** line should read `2 in, 2 out` at A and at B, and
  `4 incoming` at the HUB — which is also how you confirm the written routes took.
- **The hub's lever** holds trains at a gate that is otherwise ready, which is `RAIL_HELD`. A signal
  on a controller does not power a gate; it stops it dialling for trains.
- **Driving an outer lane by hand** (z−2 and z+2 have no station on purpose) is the only way to the
  stop-dead path: a hand-driven train has no `navigation.destination`, so `RailPreDial` never fires
  for it. It must also be you at the keyboard — Create reads driving keys straight from GLFW, so
  nothing driving the game through an MCP can hold W.
- **`preDialDistance = 0`** under `[gates.rail]` in the world's
  `serverconfig/aerowarptics-server.toml` makes a scheduled train stop at the pane and wait instead
  of crossing at speed.
- **Breaking a shaft** in one of the gaps stalls a gate under a waiting train.
- `/aerowarptics trace on` logs every rail decision under `rail`, with the numbers behind each
  refusal. To look at a corridor itself:
  `/execute in aerowarptics:rift_corridor run tp @s 80 64 8` is slot 0, and each slot after it is
  256 further down Z.

`HOLD_TOO_LONG` is **not** reachable here, and no config switch gets to it:
`CorridorFit.requiredLength` treats `corridorLength` as a floor rather than a cap, so every
corridor is always long enough for any train Create can assemble.

## Notes for maintainers

- Block/item IDs and block-states here are verified against the mod source and the Create
  6.0.10 jar, but the pack itself has not been run in a live world — if a `setblock`
  errors, check it against the current registry first.
- `rails.mcfunction` writes block entities by hand, and the keys were read off the bytecode
  rather than guessed: `{ScrollValue:n}` is `ScrollValueBehaviour`'s own key,
  `{Tank:{Fluid:{id,amount}}}` is what NeoForge's `FluidTank.writeToNBT` writes round
  `FluidStack`'s codec, and `GateId` and `Destination` are taken straight off the tag by
  `RiftGateBlockEntity.read`. If any of them stops taking, the fallbacks are all in reach —
  scroll the motor up, fill the gate from the bucket in the barrel, set the routes at the dial.
- The three gate ids in `rails.mcfunction` are fixed on purpose: that is what lets one gate's
  `Destination` name another without a player ever opening a dial. It is also why
  `rails_clear` exists — breaking a ring is what unregisters an id, and two benches in one
  world would otherwise fight over the same three.
- Nothing in `rails.mcfunction` writes Create's railway data. A station's binding to a track
  lives in Create's own edge-point registry as well as the block entity, so a hand-written one
  would half exist; placing the station from the item is what the pack asks for instead. It is
  two clicks, not one — target the track, then place the block within 16 blocks of it — and the
  station's approach direction comes from the player's look angle at the moment they target it,
  which is read off `TrackTargetingBlockItem.useOn` in the Create 6.0.10 jar rather than guessed.
- `pack_format` is `48` (Minecraft 1.21.1). Bump it if the target MC version changes.
- Keep this in sync with the registry: a new machine or drive tier means a new line in
  `build.mcfunction` and `kit.mcfunction`.
```
