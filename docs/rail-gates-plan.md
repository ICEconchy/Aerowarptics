# Rail gates: Create trains through Rift Gates

## Status (2026-10-18)

Built, unit-tested, and run in game through the Minecraft MCP (flat creative world, one player, one train).
- **Seen working in game:**
  - track run into an open gate and a shut one becoming a lane;
  - the corridor, the far stub and the apron;
  - sockets keeping the track through a hang-up;
  - a scheduled train held at a shut, unloaded gate, the gate dialling for it, the train going through, and the gate
    shutting itself after the linger;
  - the same train back the other way, the far gate dialling without changing its route;
  - a scheduled train never stopped, because the gate dialled ahead;
  - a rider carried through and given *Mind the Gap*;
  - a route into the Nether set with the dial's **Set Route** (listed as **Trains**), the lane re-routed, and a rider
    carried there;
  - the redstone hold, holding a train and letting it go;
  - a server stopped with a train in the corridor, the train arriving after the restart and the gate shutting
    itself;
  - the dial's Rails line.
- **Not tried in game:**
  - the corridor rescue for a *train* - the stall itself was found in game and the rules under it are tested, but
    the push that sends one on has not been watched;
  - a hang-up refused mid-transit;
  - a carriage too long for the corridor;
  - several lanes in one gate;
  - a gate aboard a ship refusing a lane;
  - how the corridor and its starlines look while riding (only stills were taken, none in the corridor).
  - the Ponder scene, which has been read but not watched.

### Where the build differs from this plan

- **No Phase 0 branch.** The spike's questions were answered by building it and running it in game.
- **Dialling ahead goes by where a scheduled train is heading, not by its path.**
  - Create's navigation path records turns, and a plain line into a gate has none, so reading it never fired.
  - There is no `NavigationMixin` and no braking. A train within `preDialDistance` heading into a lane's track asks
    the gate, which is usually open when it arrives.
- **Corridors are held block-ticking chunk by chunk, and every piece is joined into Create's graph as it is laid**
  (`RailWorks.joinGraph`). Held every other chunk, half a corridor never ran its graph tick, and no train found a
  path.
- **A socket asks the lane registry whether it is still needed, not the ring.** The pane's ring walk stops at the
  air beside a socket, so every socket took itself away a minute or two after its gate shut, taking the lane with it.
- **`WorldDimensionsMixin`** leaves the corridor out of vanilla's count of dimensions. Without it, every world with
  the mod said "Worlds using Experimental Settings are not supported" each time it was opened.
- **The far end is held three chunks round while it is laid**, so the stub and the track it joins are loaded when
  `layFar` runs. The reason first written here - that a station falls out of the graph "until its own tick
  re-attaches it", so the hold had to be wide enough to tick it - **was wrong, and the fix of 2026-10-25 replaces
  it.** A station never re-attaches on a tick; see that entry below. The wide hold is still wanted for laying, so
  it stays.
- **Shift-click Dial** sets the route without dialling, as well as **Set Route** for a gate in another dimension.
- **The Ponder scene was built on 2026-10-25**, after the rest: a third gate scene, *Trains through a Rift
  Gate*, on the Rift Gate and its frame, with its own schematic (`rift_gate_rail`) - the ring laid out as the
  other two gate scenes lay it, and straight track on a low bed running into the bottom row of the opening.
  It shows no train, as Create's own train scenes show none: a Ponder level has no track graph to bind a
  carriage to. Ten lines cover the route, the row, the far apron, the self-dial and who pays, dialling ahead,
  the gate shutting behind the last carriage, and the redstone hold. **Not watched in game.**
- **Built beyond the plan:** the corridor's sky, a violet void with the free cruise's starlines while riding
  (`client/fx/CorridorView`).
- **Stations stayed off the graph entirely until 2026-10-25, so no schedule ever worked through a gate.** Reported
  as "trains can't see stations linked via a gate"; it was worse than that - `Points { create:station: [] }` on the
  one merged graph, the *near* station included, from the moment the first lane was laid. The merge itself was
  always right (one graph, 37 nodes, both dimensions, and Create carries points across a merge correctly). The
  fault is that `TrackPropagator.onRailAdded` tears the network down first and `TrackEdgePoint.invalidate` resolves
  each point's block entity out of whichever level is being propagated, ignoring the point's own
  `blockEntityDimension` - so joining the corridor's end with the corridor's level strips every station in the
  player's world and resets none of them, and `TrackTargetingBehaviour.tick` never builds them again. `RailPoints`
  finds each one in its own level and invalidates it there. A Create bug this mod is the first thing to hit,
  because it is the first thing to build a cross-dimensional graph outside a Nether portal; worth reporting
  upstream, and worth keeping the workaround either way. Also fixed in passing: the apron was laid with a full
  update and never joined to the graph at all, so a station built on it had nothing to attach to.
- **The stalled-train rescue did not work until 2026-10-31, and a corridor now has a door.** Players still found
  trains stuck in the corridor after the push was built, and the reason was underneath it: nothing held a
  corridor's chunks once its gate stopped working, so fifteen seconds after the linger the carriage was no longer
  being ticked and no speed set on it could move it. The warden now holds each occupied corridor's ground itself.
  With that fixed, two more cases went: a derailed train is lifted back on and driven out instead of being left
  (there is no player who can reach it to pick it up), and anything else loose in a corridor is put out - never a
  carriage, never a rider, because a train goes out whole or not at all. And because the person who could drive a
  stopped train out was the one person who could not get to it, a gate with a lane is now a door: right-click the
  cell the rails go into, open or shut, and you step in beside the track at that gate's end; right-click either
  mouth and you are back where you clicked. Three minutes by default (`visitTicks`), counted down on the view.
- **A corridor now sends a stalled train on (2026-10-25), which the plan had no answer for.** Found in game on the
  rail test bench, driving by hand: a rider knocked off the controls mid-crossing leaves the train with no driver
  and no navigation, `Train.tickPassiveSlowdown` stops it, and it stands in the corridor for good - nobody can
  reach it, nobody may build to it, and the lane stays in transit so the gate cannot even be hung up. The save
  showed the carriage at x=12.6 of a 160-block corridor, `Speed: 0.0`, `TargetSpeed: 1.05`, schedule paused for
  manual controls. `CorridorWarden.tickTrains` now pushes anything standing still past `rescueGraceTicks` on out
  the way it was going, by setting `manualTick` and a speed every tick as Create's own controls do. The plan had
  expected this to want "an operator's rescue command" (unknown 6); it wants no command, because a corridor that
  strands a train is the bug. A derailed one is still nobody's to mend here, and is traced and left.

## Context

Players want Rift Gates to join Create rail networks: track laid into one gate comes out of the gate it is
dialled to, and trains, passengers and schedules travel through it, much as Create's trains use a Nether
portal. The destination is picked at the gate's dial instead of by where a Nether portal happens to land.

The obstacle is in Create. Bytecode for Create 6.0.10 shows it only carries a train through a portal whose
two ends are in **different dimensions**:
- `TrackEdge.interDimensional` decides whether an edge is zero length.
- A `Carriage` keeps one entity per dimension.

A same-dimension link would just be a long straight track through the terrain. Rift Gates, meanwhile, are
same-dimension only.

**Owner's decisions (2026-10-18):**
1. **Rift corridor.** The mod adds a small void dimension, `aerowarptics:rift_corridor`. A train goes from gate A
   (in any dimension) along a short straight corridor track in that dimension and out of gate B. Both hops cross
   a dimension, so Create's own code carries the carriages, passengers and schedule pathfinding.
2. **The gate opens for the train.** A train that reaches a closed gate waits at the pane as it would at a red
   signal. The gate then dials itself, paying the dial cost and needing rotation, and closes once the last
   carriage is through.
3. **One route per gate.** Every track lane into a gate follows the gate's dial destination. Re-dialling elsewhere
   re-routes all of that gate's track.
4. **Rail routes may cross dimensions.** Players, mobs and vehicles going through on foot still keep to one dimension.

**My own calls** (not asked; easy to change):
- **Tolls.** A train that finds the gate already open, with upkeep being paid, passes free. The train that opens it
  pays the dial cost. This matches today's rule that only the dialler pays.
- **Redstone hold.** A redstone signal on the controller stops the gate dialling itself for trains, so a player's
  hang-up is not undone by the next train.
- **Gates aboard airships refuse lanes.** Create track inside a Sable plot is untested ground.
- **The far stub.** Like Create at a Nether portal, the far gate gets a portal stub plus `apronLength` straight
  blocks, laid automatically. Players should build from the apron, because a curve drawn straight off the stub is
  lost when the gate re-routes.

## How it works

Each lane is one straight, flat track running into the bottom row of the gate's opening. A lane is made of these
pieces:

```
[player track]→[A pane/socket] ⇄ [west mouth track]—corridor (≥160 blocks)—[east mouth track] ⇄ [B pane/socket]→[stub + apron]
   world A                          rift_corridor dimension                                      world B
```

- **Laying the track.** `PortalTrackProvider` is registered on three blocks:
  - the pane (`RiftPortalBlock`);
  - a new **`RiftSocketBlock`**, which is invisible, has no collision and stands in lane cells while the gate is
    closed;
  - a new **`CorridorMouthBlock`**, which sits beyond each corridor end.

  When a player lays track into a gate, Create calls the provider. The provider allocates a corridor slot, places
  the mouth block, and returns the corridor's west end as the exit. Create then places and binds the west mouth
  track itself.
- **Laying the far end.** The corridor and the far end are laid at `connect()`, when both gates are resolved and
  loaded; the 40 ticks of dialling cover the work. This lays the corridor track, the east mouth, and a portal stub
  at B. The stub is on the side the train is heading, in the lane mapped by across-fraction as
  `GateTraversal.map` maps it.
- **Sockets keep the track alive.** Create's `TrackBlock.updateShape` turns a portal track to AIR when the block
  behind it stops being a portal. So `setAperture` swaps pane and socket in lane cells and never writes AIR there.
- **Holding a train** takes three mixins on Create:
  - `@ModifyArg(index = 5)` on the single `TravellingPoint.travel` call in `Carriage.travel`. This wraps Create's
    portal listener, so a carriage stops at a gate that is not OPEN. It never stops a carriage that already
    straddles that portal, so a train is never cut in half.
  - Two `@WrapOperation`s in `Train.tick`:
    - one on the first `Navigation.cancelNavigation()` call;
    - one on `TrainStatus.endOfTrack()`.

    Together these keep a held train's schedule and path intact, and show our reason in place of "end of track".
    Without them the train would cancel its navigation and search for a new path every tick.
- **Re-routing.** On a re-dial to C, `RailWorks` destroys B's stubs and lays new ones at C. Destroying a stub
  takes its bound east mouth with it. A's track, the west mouth and the corridor all stay. A re-dial is refused
  while a train is in the corridor.

## Phase 0: spike (throwaway branch, in game through the Minecraft MCP)

This proves the approach before anything is built on it.
1. Baseline: a train crosses a vanilla Nether portal with Aeronautics and Sable loaded.
2. The datapack dimension: `data/aerowarptics/dimension_type/rift_corridor.json` and
   `dimension/rift_corridor.json`, a flat generator with no layers and the void biome. Check it loads in a new
   world and in an existing one, and that Sable does not object.
3. A hard-coded provider on `RIFT_PORTAL`, plus a debug command that lays a corridor and a stub at a second gate
   in the same dimension.
4. A scheduled three-carriage train with a seated player, station to station through the corridor. Check for:
   - no `doublePortal` at 160 blocks;
   - the player re-seated without falling;
   - the gate not trying to move the carriage itself.
5. The socket: neighbour updates must leave the track standing. Then check that AIR does kill it.
6. Holding: a mixin that always holds, then the `cancelNavigation` wrap. The train must wait with its schedule
   intact and go on when let go.
7. An unloaded far side. Then an empty dimension: does its gate stop ticking? This decides whether a wake is needed.
8. A player who dismounts inside the corridor.

**Go or no-go:** steps 2, 4 and 6 must work. If any of them fails, stop and bring it back to the owner.

## Phase 1: foundations

**Pure classes** (no Minecraft, tested directly):
- `rail/CorridorSlots`: slot to origin (x 0, z = slot × 64, y 64), allocation with a free list, and staying inside
  the world border.
- `rail/CorridorFit`: the required length, `max(config, Create maxAssemblyLength + 32)`, and whether a carriage of
  a given span fits.
- `rail/RailLaneMap`: from a near cell and approach side to the far cell, exit side, stub and apron. It uses
  `GateTraversal.spanSign` and across-fractions, takes the lowest open cell in the mapped column, and detects
  collisions.
- `rail/RailLaneState`: LAYING, READY, FAR_PENDING, BROKEN.

**Level side:**
- `rail/RailCorridor`: the dimension key and level, plus `isCorridor`.
- `gate/RiftSocketBlock` and `rail/CorridorMouthBlock`: no item, no loot, unbreakable mouth.

**Keep the corridor out of everything else:**
- `ProbeDimensions.offered` (even when configured with `"*"`)
- `CrossDimensionWarp.joined`
- `RiftStorm` (`weather/RiftStorm.java:104`)
- the Sunderer's dimension list

**Gate geometry:** `RiftGateBlockEntity.isOpen` (`gate/RiftGateBlockEntity.java:315`) and
`RiftPortalBlock.reachesFrame` count a socket as pane.

**Other:** add `Trace.RAIL`. Register the blocks in `AWBlocks`, with blockstates and empty models.

**Tests:**
- `CorridorSlotsTest`, `CorridorFitTest`, `RailLaneMapTest` (parallel and right-angled gates, a round trip,
  irregular masks).
- `ProbeDimensionTest`, with the corridor excluded.
- `ResourceIntegrityTest`: the dimension JSON parses, and the new blocks have no item, recipe or loot.

## Phase 2: lanes

- **`rail/RiftPortalTracks`**: the three providers, registered in `FMLCommonSetupEvent.enqueueWork` the way
  `AstrolabeBlock.onCommonSetup` registers its Create hook.
  - **Refusals:** no route, a gate aboard a ship, not the bottom row, too many lanes, far cell taken, rails
    disabled.
  - **Each refusal is told and traced.** Create destroys the player's track and drops it as an item, so every
    refusal also sends our own action-bar reason and a `Trace.log` line with the numbers.
- **`rail/RailLane`** (record) and **`rail/RailLaneRegistry`**: SavedData on the overworld, indexed by gate, by
  track position per dimension, and by the far gate's stub cells.
- **`rail/RailWorks`**: lays and tears down the corridor, mouths and barrier floor, and the stub and apron. It
  writes bindings the way Create does: `asPortal` shape, `HAS_BE`, then `TrackBlockEntity.bind` both ways. Its jobs
  hold their chunks through `Holds.ground` with a new `aerowarptics:rail_works` ticket, and act only once the
  chunks are loaded. `ChunkLoadingChokePointTest` must still pass.
- **Edits to `RiftGateBlockEntity`:**
  - `setAperture` (l.339): sockets go in lane cells.
  - `connect` (l.515): lays the far stubs.
  - `revalidate` (l.776): heals sockets, checks each lane's binding, and notices track laid up to a closed gate.
  - `unform` and `remove`: tear down every lane explicitly. Today's `UPDATE_KNOWN_SHAPE` skips `updateShape`, so a
    closed gate's track would otherwise die only on some later neighbour update.
  - `moveEntities` (l.1082): skips `CarriageContraptionEntity`.
- **`RailWiringTest`** (reads the source):
  - registration happens in setup;
  - `setAperture` never writes AIR into a lane cell;
  - sockets count in `isOpen` and `reachesFrame`;
  - every refusal traces;
  - the carriage exemption is there.

## Phase 3: holding trains and letting them through

- **Mixins:** `mixin/rail/CarriageMixin` and `mixin/rail/TrainMixin`, added to the common array of
  `aerowarptics.mixins.json`. They call static hooks in `rail/RailGates`. `MixinTargetTest` learns `@ModifyArg`
  (checking the argument type at the index) and `@WrapOperation` with an ordinal.
- **`rail/RailGateLogic`** (pure): the steps are request, self-dial, admit, transit, clear, linger, close. It also
  handles:
  - payment, by the rule above;
  - rate-limited retries when the far end is busy or the gate is short of essence or rotation;
  - the redstone hold;
  - no auto-close if a player opened the gate.
- **`rail/RailTransit`** (runs on the server tick): which trains are held this tick, and which trains are in each
  corridor slot (from `Carriage.getPositionInDimension`). It prunes trains that Create no longer lists, and is
  rebuilt on server start.
- **Gate edits:**
  - **Dialling for a train:** `tryDial` gains a rail purpose (no player, other dimensions allowed).
  - **Refusals while a train is in the corridor:** a player's Hang Up or re-dial is refused with
    `RAIL_TRAIN_IN_TRANSIT`. Automatic hang-ups (power, essence) still happen, and trains already admitted finish
    their crossing.
  - **Arrivals:** they call `SpatialWaveDetectorBlockEntity.announce`.
- **Carriage length:** a carriage longer than `CorridorFit` allows is held at A with `RAIL_CARRIAGE_TOO_LONG`.
- **`rail/CorridorWarden`:** a player off the train in the corridor for longer than `rescueGraceTicks` is put back
  in front of gate A. Breaking and placing blocks in the corridor is cancelled.
- **The wake** (only if Phase 0 step 7 needs it): extend `HoldRules.wakeChunk` / `AirshipResidency.wake` to count
  rail requests and transits, so a gate in an empty dimension still ticks.
- **`RailGateLogicTest`:**
  - each train pays once at most;
  - losing power mid-transit strands nobody;
  - a re-route during transit is refused;
  - the redstone hold is honoured;
  - a gate a player opened is never auto-closed.

## Phase 4: routes across dimensions and the dial

- **Cross-dimension routes:** add a `farLevel()` helper. `renewTicket` (l.812), `hangUp` (l.526) and
  `tickTraversal` (l.842) currently resolve the far gate in their own level. `tickTraversal` moves no people when
  the far end is in another dimension, and a player who walks in is told `destination_rail_only`.
- **Route list:** `RiftGateRegistry.routableFrom` lists gates in other dimensions, marked trains-only.
- **Packets:**
  - `ServerboundGatePacket` gains `SET_ROUTE`, appended to the end.
  - `ClientboundGateDialPacket` gains a bounded rail summary: lanes, ready, waiting and in-transit counts, and the
    last rail failure. Route entries gain trains-only and dimension.
  - Bump `AWNetwork.VERSION`.
- **`RiftGateDialScreen`:** one readings line for the rails, and other-dimension routes in the list. It must still
  fit 320 px, and `ConsoleTextTest` must still hold the longest refusal.

## Phase 5: polish

- **Dialling ahead:** `NavigationMixin`, injected before the single `approachTargetSpeed` call. The gate dials
  ahead and the train brakes rather than stopping dead (Create's own hold sets speed to 0 at once).
- **The corridor's look:** a sky of hyperspace starlines (`HyperspaceField`).
- **Docs and extras:**
  - a Ponder scene, a handbook page and an advancement (*Mind the Gap*), each through its `tools/` generator;
  - a JEI note and a changelog line;
  - an AGENTS.md invariants entry ("a lane cell never holds AIR", "a train is held through `RailGates`, never by
    cutting track");
  - the "gates stay in their own dimension" line in `docs/mod-description.md`.

## Config (`AWConfig`, server, `gates.rail.*`)

| Key | Default |
| --- | --- |
| `enabled` | true |
| `allowCrossDimension` | true |
| `corridorLength` | 0 (auto, at least Create `maxAssemblyLength` + 32) |
| `lingerTicks` | 60 |
| `retryTicks` | 40 |
| `maxLanesPerGate` | 8 |
| `apronLength` | 2 |
| `rescueGraceTicks` | 60 |
| `preDialDistance` | 96 |

Slot spacing stays a code constant: changing it would orphan every existing corridor.

## Lang

- **New `GateFailure` values**, appended because they go over the wire by ordinal, each as `gate.failure.<name>`:
  `rail_no_route`, `rail_aboard_ship`, `rail_far_blocked`, `rail_not_bottom_row`, `rail_too_many_lanes`,
  `rail_far_lane_taken`, `rail_train_in_transit`, `rail_carriage_too_long`, `rail_lane_building`, `rail_disabled`,
  `destination_rail_only`.
- **Dial and status strings:** `gate.rail.*` for the dial, and `rail.held` for the train status line.
- **The dimension's name.**

`LangCoverageTest` and `ConsoleTextTest` already loop over `GateFailure.values()`.

## Critical files

- `src/main/java/uk/co/iceconchy/aerowarptics/gate/RiftGateBlockEntity.java`: aperture, connect, revalidate, unform,
  movers, tryDial, farLevel.
- `gate/RiftPortalBlock.java`, `gate/RiftGateRegistry.java`, `gate/GateFailure.java`.
- `network/ServerboundGatePacket.java`, `network/ClientboundGateDialPacket.java`, `network/AWNetwork.java`.
- `client/screen/RiftGateDialScreen.java`.
- `hold/Holds.java` (ticket type) and `hold/HoldRules.java` (wake).
- `probe/ProbeDimensions.java`, `compat` Dimensional Sable `CrossDimensionWarp`, `weather/RiftStorm.java`.
- `src/main/resources/aerowarptics.mixins.json`, plus `src/test/.../MixinTargetTest.java`.
- The new `rail/` package and `mixin/rail/`.

## Verification

- **Unit tests:** run `./gradlew build` with `JAVA_HOME` set from the jdk-path memory, and read the output rather
  than trusting the exit code. The suite must stay green, with the new tests above added.
- **In game, through the Minecraft MCP**, with `/aerowarptics trace on`:
  1. The corridor dimension exists in a new world and an old one.
  2. Track laid into an open gate builds a corridor, a far stub and an apron; track laid to a closed gate is noticed.
  3. After a hang-up the socket stands, and the track survives neighbour updates.
  4. A scheduled train at a closed gate waits, the gate dials itself, essence is taken, the train goes through, and
     the gate closes after the linger.
  5. A seated player is carried through without falling, and the gate does not move them a second time.
  6. A train waits for want of essence or rotation, when the far end is busy, and under the redstone hold.
  7. A re-dial to C moves the stub and keeps the apron; a re-dial mid-transit is refused.
  8. A route to the Nether carries trains; a player on foot gets "trains only".
  9. An unloaded far side, an empty dimension, and the wake.
  10. Breaking A's track tears the lane down and frees the slot; a broken stub is laid again on the next dial.
  11. A restart mid-transit.
  12. A carriage that is too long is refused at A.
  13. A dismounted player is rescued from the corridor.
  14. A gate aboard a ship refuses lanes.
  15. `profile_ticks` with five lanes running.

## Biggest unknowns (what Phase 0 is for)

1. A datapack dimension in existing worlds, and with Sable loaded.
2. Whether Create checks the portal neighbour anywhere besides `updateShape`.
3. How the hard stop behaves with navigation kept.
4. A seated player reaching the corridor before the carriage entity exists there.
5. Gates in empty dimensions not ticking.
6. A ring broken mid-transit, which may derail a train in the corridor and need an operator's rescue command.
