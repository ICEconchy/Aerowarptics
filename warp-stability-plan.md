# Warp stability plan — desync, ghosting, and the "yeet"

> **Status (implemented).** Phases 0–5 are in the codebase: `WarpTrace` diagnostics and the
> `/aerowarptics warp dryrun` command (0); `LaunchClearance` and the `NO_CLEAR_LAUNCH` refusal (1);
> the aperture/throat clearance folded into `SafeArrival` and `LaunchClearance` (2); the widened
> arrival ticket, `ArrivalTicket.holdCorridor`, and the `ARRIVAL_NOT_LOADED` commit precondition (3);
> the `Airship.clampSpeed` ceiling and the reported-velocity abort (4); and the server-side
> re-broadcast plus the shared jump threshold (5). New tests: `LaunchClearanceTest`,
> `SafeArrivalTest`, `ArrivalTicketTest`, `AirshipVelocityTest`, and extensions to `WarpTraceTest` and
> `FoldCrossingTest`. Phase 6 is not code here: its acceptance criteria were folded into
> `docs/remediation-plan.md` item 2, as this plan directs. The prose below is kept as the design
> record.

A plan to execute later. It targets four things a player actually experiences during a warp, and the
two clearance holes that cause most of them:

1. **Desynchronisation** — the client and server disagree about where the hull, or a passenger, is.
2. **Client-side ghosting / disappearance** — the airship stops being drawn, or the crew falls
   through the deck.
3. **The physics "yeet"** — the hull (or a passenger) accumulates an enormous velocity and is
   launched thousands of blocks.
4. **Arrival into unloaded chunks** — the hull materialises where the surrounding world is not
   loaded, and Sable removes the sub-level ("kills the airship") because there is nothing under it.

And the structural gap behind most of 1–3:

5. **The launch path is never proven clear.** The arrival end is searched exhaustively
   (`SafeArrival`); the departure end — the entry aperture, the run at it, and the corridor run flown
   in real world space behind it — is not checked at all. A hull that flies into terrain while the
   drive is commanding its velocity is exactly what makes Sable's solver eject it, which is the same
   event as the yeet.

This document is a plan, not a change. Nothing here is implemented yet. It is written so a later
session can pick up any one phase and ship it on its own. It assumes the reader has read
[`AGENTS.md`](../AGENTS.md) and understands that an airship *is* a Sable `SubLevel` and that ship
space and world space are different frames.

The existing [`remediation-plan.md`](remediation-plan.md) covers a different, overlapping set of
findings (notably item 2, the `INTERRUPTED` stall on large hulls). Where the two meet it is called
out; this plan does not restate that work.

---

## How a warp moves the hull today

The reference the rest of the plan hangs off. Line numbers are a starting point, not a promise.

A warp is **flown, not cut to**. `WarpFlight` (`warp/WarpFlight.java`) runs a five-stage state
machine, and `RiftDriveBlockEntity.tickFlight` (`drive/RiftDriveBlockEntity.java:827`) drives it one
tick at a time by commanding the hull's velocity through `Airship.driveVelocity`
(`airship/Airship.java:303`):

| Stage | What moves | Where |
| --- | --- | --- |
| `APPROACH` | Hull flown at the entry rift, easing to passage speed | Real world, over the departure point |
| `TRANSIT` | Hull drawn through the entry aperture | Real world, still over the departure point |
| `CORRIDOR` | Hull keeps flying straight, at passage speed | **Real world, out ahead of the departure point** — hidden inside the entry throat, not teleported anywhere |
| `BREACH` | Hull flies out of the exit aperture | Real world, at the destination |
| `EMERGE` | Hull coasts to rest | Real world, at the destination |

The **only** discontinuity is a single teleport at `EXIT_CORRIDOR`
(`drive/RiftDriveBlockEntity.java:876`), which calls `Airship.relocate` (`airship/Airship.java:238`)
to carry the hull, its block entities and its passengers from the corridor to `emergeStart` behind
the exit aperture, keeping momentum in full (`retainedVelocity = 1.0`).

Three systems already exist to keep the crew and the clients consistent across that teleport, and
each was written in response to a real bug:

- **`Airship.relocate` → `tell`** (`airship/Airship.java:229`) sends every player a
  `connection.teleport`, resetting what their own client thinks it is doing.
- **`FoldCrossings`** (`client/FoldCrossings.java`) collapses the client's interpolated pose delta so
  a client does not draw the jump as a four-thousand-block sweep and then have Sable abandon the
  hull's collision as "enormous".
- **`WarpPassengers`** (`warp/WarpPassengers.java`) and **`CrewManifest`** (`warp/CrewManifest.java`)
  take back borrowed momentum and put stray passengers back in their seats — the manifest doing it
  from the *server* tick so it survives the drive going quiet.

Arrival is chosen by **`SafeArrival.find`** (`warp/SafeArrival.java:73`), which sweeps the hull's
footprint backward along the emerge bearing and proves the whole run-out volume clear with
`ObstructionScan` (`warp/ObstructionScan.java`, exhaustive, air-section-skipping). **`ArrivalTicket`**
(`warp/ArrivalTicket.java`) force-loads the arrival chunks and the drive's own chunk for 600 ticks so
neither streams in at the teleport.

The gaps this plan closes are: the departure path has none of `SafeArrival`'s scrutiny; the exit
aperture and both throats are sized but their volumes are not proven clear; there is no velocity
sanity guard anywhere; and chunk residency ends after 600 ticks with nothing checking a loader is
present to keep the landed hull alive.

---

## Failure taxonomy — symptom to mechanism

Fixing a symptom without naming its mechanism is how the earlier passenger fixes "looked like they
had done nothing." Each class below names the mechanism the phases act on.

### A. Desynchronisation
- **A1 — Pose sync ordering.** Sable's pose stream and the mod's `ClientboundFoldCrossedPacket`
  (`network/ClientboundFoldCrossedPacket.java`) are independent streams with no ordering. Handled by
  the `FoldCrossings` watch window, but the window is time-bounded (`WATCH_TICKS = 200`) and the jump
  detector is distance-bounded (`JUMP_BLOCKS = 128`).
- **A2 — Passenger frame.** A passenger is reconciled in ship space (`WarpPassengers.reseat`); a
  missed tick or a rider that was never on the manifest reads as a player standing somewhere the
  server does not expect.
- **A3 — Interrupted flight.** On reload the drive drops the flight in `read`
  (`drive/RiftDriveBlockEntity.java:1467`) and recovers on the next tick; between the drop and the
  recovery the hull, the clients and the crew can each hold a different position. (Overlaps
  remediation item 2.)

### B. Ghosting / disappearance
- **B1 — Collision-bounds blow-up.** The mechanism `FoldCrossings` exists for: a client that
  interpolates the teleport builds a collision volume long enough for Sable to log *"Enormous local
  sub-level collision bounds, quitting"* and abandon the hull — undrawn hull, crew through the deck.
- **B2 — Notice never reached this client.** The fold-crossed notice is sent to the crew and to
  players tracking either endpoint at the moment of crossing
  (`drive/RiftDriveBlockEntity.java:1341`). A client that starts tracking the hull *during* the watch
  window, or after a dimension change (which calls `FoldCrossings.clear`), never gets the collapse.

### C. The physics "yeet"
- **C1 — Penetration ejection (the big one).** If the hull is commanded into solid blocks while
  `driveVelocity` holds it there — on the departure path that is never checked, or at an arrival that
  raced a block update — Sable's rigid-body solver resolves the penetration by ejecting the sub-level
  at high speed. This is the launch-thousands-of-blocks report, and it is why C is mostly solved by
  phases 1–2.
- **C2 — Frame-mismatched velocity command.** `tickEmerge` (`warp/WarpFlight.java:457`) commands
  `remaining = arrivalOrigin − airship.position()` scaled by `emergeFraction`. Both terms are
  world-space today, so `remaining` is a sane distance — but `driveVelocity` multiplies by
  `PHYSICS_TICKS_PER_SECOND = 20` (`airship/Airship.java:330`). Any frame or unit slip here becomes a
  20× over-command, and there is no clamp to catch it.
- **C3 — Retained momentum stacking.** `relocate` restores a fraction of pre-warp velocity
  (`airship/Airship.java:263`); a failure or double-fire that adds velocity without a matching
  `resetVelocity` accumulates.

### D. Arrival into unloaded chunks
- **D1 — Ticket lapse.** `ArrivalTicket` holds for 600 ticks. If, when it lapses, no player and no
  chunk loader keeps the landing zone loaded, the world chunks under the hull unload and Sable can
  remove the sub-level. Nothing checks a loader is present before committing the warp.
- **D2 — Footprint vs ticket radius.** The ticket radius (`ArrivalTicket.radiusFor`) is derived from
  `hullSpan`; the emerge run-out and the exit aperture can reach beyond it, so the leading part of
  the arrival can momentarily sit in a chunk that is not held.

### E/F/G. Unproven flight path (departure and arrival)
- **E — Departure path unchecked.** No obstruction test covers the entry aperture volume, the
  `APPROACH` run at it, or the `CORRIDOR` run — which is flown in real world space out ahead of the
  departure point, for `corridorTicks × transitSpeed` blocks. Terrain there is flown straight into.
- **F — Arrival aperture/throat unchecked.** `SafeArrival` proves the swept hull footprint clear but
  does not separately prove the exit aperture disc, the exit throat depth, or the aperture *opening*
  (radius `riftRadius`) is clear of terrain — so a block can intrude on the aperture the hull flies
  out through.
- **G — Blocks streaming into the path.** Even a proven-clear path can have blocks stream in or
  update during the several-second flight if the path is not chunk-resident, so a block "pops through
  the corridor" into the moving hull.

---

## The plan, in phases

Ordered so each phase is independently shippable and nothing later needs anything earlier to be
perfect. Phase 0 is instrumentation and should go first because every other phase needs to be able
to *see* what it changed. Phases 1–3 remove the causes; phases 4–5 are the belt-and-braces guards
that catch whatever the causes missed.

### Phase 0 — See the failures before touching them

**Why first.** These bugs are intermittent and most are silent. `WarpTrace` (`warp/WarpTrace.java`)
already traces stage transitions, teleports and aborts; extend it so a *reproduction* leaves a
complete record rather than a guess.

**Approach.**

1. Add a `warp.diagnostics` server config flag (default off) gating verbose tracing. Everything
   below is behind it, so production logs stay quiet.
2. Trace the hull's **commanded** velocity and Sable's **reported** velocity
   (`Airship.velocity()`) every flight tick. A yeet shows up as the reported figure diverging from
   the commanded one — the single clearest signature of C1/C2, and today invisible.
3. Trace, at each teleport, the client-visible move size against `FoldCrossings.JUMP_BLOCKS`, and
   whether a fold-crossed notice was sent and to how many players (B2).
4. Trace, at arrival, the loaded/held state of the landing chunks and whether a chunk loader is
   present (D1).
5. Add a `/aerowarptics warp dryrun` debug command (permission level 2) that runs `WarpFlight.plan`
   plus the new departure and arrival clearance checks against the current course and prints the
   verdict **without** moving anything. This is the reproduction harness for phases 1–2.

**Verify.** No behavioural change to assert; add a `WarpTraceTest`-style check that the new trace
lines format without a running game (keep the formatting logic Minecraft-free, per the testing
conventions in `AGENTS.md`).

**Risk.** Negligible; additive and gated.

---

### Phase 1 — Prove the departure path clear (fixes E, most of C1)

**Problem.** The hull is flown at the entry rift, through it, and then straight ahead down the
corridor — all in real world space over the departure point — with no check that any of that volume
is clear. Fly into a hillside and Sable ejects the hull (C1) or the flight desyncs (A). This is the
single largest hole.

**Approach.**

1. Add `LaunchClearance` in `warp/`, the departure-side mirror of `SafeArrival`, and keep it
   Minecraft-free the same way (`ObstructionScan` + a `Solid` predicate) so it is unit-testable.
2. The volume to prove clear is the hull swept **forward** along the world bow from its current
   pose, through the entry plane, and on for the full corridor reach —
   `transitRun + corridorTicks × transitSpeed` (the same figure `WarpFlight.entryThroatDepth` is
   sized from, `warp/WarpFlight.java:306`). Reuse `SafeArrival.sweep` with a positive distance rather
   than duplicating it.
3. Exclude the hull's own footprint and any sub-level it is docked to from "solid" (reuse
   `SafeArrival.intersectsAnotherAirship`'s self-exclusion), so a ship does not read its own deck as
   an obstruction.
4. Call it in `beginWarp` (`drive/RiftDriveBlockEntity.java:703`), right beside the existing
   `WarpFlight.plan`/`SafeArrival` result check, **before** the rift opens and before any velocity is
   commanded. On failure, `abort` with a new `WarpFailure.NO_CLEAR_LAUNCH` — refuse at the mooring,
   never mid-corridor, exactly as the arrival check already does.
5. Decide the launch-blocked policy explicitly: a moored ship with a wall ahead should be told to
   turn (the bow is pilot-set, `DriveHeading`), so the failure message names the bearing. This is a
   pure refusal — the airship is never damaged (per the failure conventions in `AGENTS.md`).

**Verify.** `LaunchClearanceTest`: a hull with clear air ahead passes; a one-block wall at the entry
plane, at mid-corridor, and at the far end of the corridor reach each fail (the same
floor/wall/thickness cases `ObstructionScan`'s own doc calls out). Assert the swept volume length
equals the entry throat reach.

**Risk.** Medium. Adds a new refusal path players will hit — deliberately. Tune the corridor reach
against real tiers so ordinary launches are not refused; the dry-run command from phase 0 is how.

---

### Phase 2 — Prove the arrival aperture and both throats clear (fixes F, rest of C1)

**Problem.** `SafeArrival` proves the swept hull footprint clear but says nothing about the exit
aperture disc, the throat depths, or the aperture opening the hull flies out through. A block on the
exit plane "interferes with the ship entering the aperture" from the far side.

**Approach.**

1. Extend `SafeArrival.find` (or add a sibling checked alongside it) to also prove clear:
   - the **exit aperture disc**: a cylinder of radius `riftRadius` (`WarpFlight.plan`,
     `warp/WarpFlight.java:154`) centred on `exitCentre`, one hull-thickness deep, so the opening is
     not inside terrain;
   - the **exit throat volume** back to `emergeStart` — this is where the hull sits at `BREACH` — at
     the aperture radius, not just the hull's AABB, so a block cannot protrude into the throat the
     player sees.
2. Because arrival placement is chosen by search, fold these into the per-candidate `isClear` test
   (`warp/SafeArrival.java:154`) so a candidate that clears the hull but not its aperture is rejected
   and the search continues, rather than committing and failing later.
3. Apply the **same** aperture/throat clearance to the *entry* side inside `LaunchClearance` from
   phase 1 — the entry aperture opening and its throat are equally real on the departure side. This
   is the symmetric "same checks on the entrance aperture and corridor" the goal calls for.
4. Confirm the swept region already covers the full `BREACH`→`EMERGE` coast (it does today:
   `emergeDistance` in `WarpFlight.plan`, `warp/WarpFlight.java:169`); assert it in a test so a later
   change to the emerge sizing cannot silently uncover it.

**Verify.** Extend `SafeArrivalTest`/`ObstructionScan` tests: a candidate clear for the hull but with
a block on the exit plane is rejected; the aperture cylinder radius matches `riftRadius`; the entry
and exit clearance volumes are congruent for a symmetric course.

**Risk.** Medium. Tighter arrival criteria mean the search rejects more candidates and may climb
higher or refuse more often. `ArrivalSearch` already prefers "come in higher" to "come in beside it",
so this compounds in the intended direction; verify large hulls still find a berth near a
valley-floor anchor.

---

### Phase 3 — Keep the landing zone (and the corridor) resident (fixes D, G)

**Problem.** Chunks are held for 600 ticks and then let go. If nothing else keeps the landing zone
loaded the hull can be unloaded and removed by Sable (D1); and the corridor path (phase 1) must be
resident for its whole flight or blocks stream in and pop through the hull (G).

**Approach.**

1. **Widen the arrival ticket to the whole footprint.** `ArrivalTicket.radiusFor`
   (`warp/ArrivalTicket.java:83`) is derived from `hullSpan`; base it instead on the swept arrival
   volume from phase 2 (hull + emerge run + exit throat) so no part of the arrival is ever briefly
   unheld (D2).
2. **Add a corridor/launch ticket.** Mirror `ArrivalTicket.hold` for the departure path proven in
   phase 1, force-loaded from `beginWarp` for the flight's duration, released by its own lifespan.
   This makes the phase-1 obstruction proof *stay* true for the whole flight (G).
3. **Require a durable landing before committing.** Before the warp fires, check the landing zone is
   either within a player's view distance or covered by a chunk loader (a forceload, or a
   `WarpAnchor`-driven loader — see the open question below). If not, refuse with a new
   `WarpFailure.ARRIVAL_NOT_LOADED` **or** hold the zone with a longer, self-expiring "settling"
   ticket that outlives the coast and hands off to whatever keeps it loaded afterwards. Prefer the
   refusal: it makes "the airship must arrive where chunks are loaded" a checked precondition rather
   than a hope.
4. Confirm the drive-chunk hold (`ArrivalTicket.holdDrive`, `warp/ArrivalTicket.java:60`) already
   covers the interruption case; this phase does not change it, but note it interacts with
   remediation item 2 (the `INTERRUPTED` stall) — that stall is *why* the drive-chunk hold exists.

**Verify.** Unit-test `radiusFor` against a swept volume rather than a bare span; test the
loader/view-distance precondition as a pure predicate over (has-loader, in-view-distance,
config-policy). Runtime verification (a hull surviving an unattended arrival) needs a game and should
be documented as a manual check, per the testing conventions.

**Risk.** Medium. Force-loading more, and longer, is a server cost — the very drain
`ArrivalTicket`'s own doc warns about. Keep every new ticket self-expiring and radius-capped
(`Mth.clamp`, as today). The commit precondition may refuse warps to unattended anchors; that is the
intended trade for not losing ships, but make it a configurable policy.

---

### Phase 4 — A velocity sanity guard (belt-and-braces for C)

**Problem.** Even with phases 1–3, a solver glitch, a frame slip in `tickEmerge` (C2), or retained
momentum stacking (C3) could still command or produce an extreme velocity. There is no backstop.

**Approach.**

1. Add a clamp in `Airship.driveVelocity` (`airship/Airship.java:303`): a commanded speed above a
   configurable ceiling (well above the fastest legitimate passage speed, comfortably below anything
   that reads as a yeet) is clamped and, under `warp.diagnostics`, traced. This alone caps C2 at
   source.
2. Add a per-tick **reported**-velocity check in `tickFlight` (`drive/RiftDriveBlockEntity.java:827`):
   if `Airship.velocity()` exceeds the ceiling while the drive is flying, `abort` with
   `WarpFailure.RELOCATION_FAILED` and `restoreOrigin` — catch the ejection and put the hull back
   rather than letting it travel. This is the direct defence against C1's residue and is only
   reachable if phases 1–2 missed something.
3. Audit `tickEmerge` for the frame assumption behind C2: assert (in code, cheaply) that
   `arrivalOrigin` and `airship.position()` are both world-space, and add a test that a plausible
   `remaining` never produces a command above the ceiling.
4. Confirm `relocate`'s `resetVelocity` (`airship/Airship.java:254`) precedes every velocity add on
   every path, so C3 cannot stack; add a test that two back-to-back relocates do not accumulate.

**Verify.** `AirshipVelocityTest` (extract the clamp as a pure function): commanded speeds above and
below the ceiling; the emerge command for a range of `remaining` distances stays bounded; the
reported-velocity abort triggers at the threshold.

**Risk.** Low, if the ceiling is set from real traced passage speeds (phase 0). Too low a ceiling
would clamp a legitimate fast tier and stall a warp; derive it from tier `warpTicks`/`transitSpeed`,
not a guessed constant.

---

### Phase 5 — Make the anti-desync guards robust (fixes A, B)

**Problem.** The three existing guards work in the common case but have edges: `FoldCrossings` can
time out or miss a client (A1, B2), and the jump detector's 128-block threshold misses a short
same-dimension hop.

**Approach.**

1. **Re-broadcast the fold-crossed notice on late tracking (B2).** When a client begins tracking a
   sub-level that crossed within the last `MANIFEST_GRACE_TICKS`, send it the notice so a player who
   arrives at either endpoint mid-window still gets the collapse. Server-side, keyed off the same
   record the manifest already keeps.
2. **Make the client collapse self-sufficient (B1/A1).** Today the collapse only runs for hulls the
   client was *told* to watch. Have `FoldCrossings.sweep` additionally collapse any AeroWarptics
   sub-level whose one-tick pose delta exceeds `JUMP_BLOCKS` even without a notice — the notice
   becomes an optimisation, not a requirement. Guard it to AeroWarptics-driven warps so it never
   fights Sable's ordinary fast flight.
3. **Handle the dimension-change case (B2).** `FoldCrossings.clear` drops watches on disconnect; a
   cross-dimension warp (`CrossDimensionWarp`) moves the player between levels. Confirm the collapse
   still runs in the destination level, and that remediation items 4/14 (client state surviving a
   dimension change) do not leave a stale watch.
4. **Close the passenger frame edges (A2).** Confirm `WarpPassengers.manifest` (the union of
   `passengers()` and `crew()`, `warp/WarpPassengers.java:180`) covers riders on the emerge run, and
   that `settle` runs on every terminal path (`completeWarp`, `abort`, `recoverFromInterruption`).
   Add a test that a rider dismounted at `EMERGE` is reseated — the exact hole
   `WarpPassengers.recoverable` was widened to cover (`warp/WarpPassengers.java:89`).

**Verify.** Extend `FoldCrossingsTest`: a sub-level move above/below `JUMP_BLOCKS` with and without a
notice; a watch does not survive a `clear`. Keep `isJump`/`watched`/`watchTicks` as the test seams
they already are.

**Risk.** Medium on B2 item 2: collapsing without a notice must be scoped tightly to warp jumps, or
it could paper over a genuine Sable desync and hide a different bug. Gate it on the sub-level being
one this mod is currently warping if that state is reachable client-side; otherwise on the
`JUMP_BLOCKS` discontinuity alone, which nothing but a crossing produces.

---

### Phase 6 — Reconcile interruption recovery (ties A3 to remediation item 2)

**Problem.** A warp dropped by a reload leaves the hull wherever the crossing left it, recovers the
crew but not the hull's position (`recoverFromInterruption`, `drive/RiftDriveBlockEntity.java:1365`),
and depends on `releaseStaleClaim` → `restoreOrigin` to put a stranded hull back. The window between
drop and recovery is a genuine multi-actor desync (A3).

**Approach.** This is the same root cause as remediation item 2 (the `INTERRUPTED` stall). Do not
duplicate it; instead, when that item is taken up, add these acceptance criteria to it:
1. After recovery, the hull, every client, and every crew member agree on one position — either the
   origin (restored) or the destination (completed), never a third place.
2. `forgetOrigin` (`drive/RiftDriveBlockEntity.java:894`) is only reached once the crossing is
   past the point of no return, so recovery can always choose origin-or-destination unambiguously.
3. The corridor/launch ticket from phase 3 covers the reload window so the hull is not unloaded
   mid-recovery.

**Verify.** Whatever harness item 2 gains; assert the single-position invariant.

**Risk.** Inherited from item 2 (large, L-effort). Kept out of this plan's critical path — phases 1–5
stand without it.

---

## New surfaces this introduces

**Config (`AWConfig`, server spec):**
- `warp.diagnostics` — verbose tracing gate (phase 0).
- `warp.launchCorridorReach` or reuse of the derived entry-throat reach — the departure path length
  proven clear (phase 1).
- `warp.maxCommandedSpeed` — the velocity ceiling (phase 4), or derive per-tier and expose only a
  multiplier.
- `warp.requireLoadedArrival` — policy: refuse vs. settling-ticket when no loader is present
  (phase 3).

Every one lives in `AWConfig`, never as a constant in code, per `AGENTS.md`. Client presentation
constants stay client-side.

**`WarpFailure` values (with lang keys, surfaced as named reasons):**
- `NO_CLEAR_LAUNCH` — the departure path is blocked (phase 1).
- `ARRIVAL_NOT_LOADED` — the landing zone has no loader and policy refuses (phase 3, optional).

Add each with a translation key and cover it in `LangCoverageTest`, as the enum conventions require.

**New classes:** `LaunchClearance` (phase 1), kept Minecraft-free for direct testing.

---

## Test plan at a glance

The suite is the safety net here — most of these failures are silent, so each phase adds a test that
targets *the failure that would not throw*, per `AGENTS.md`:

| Phase | New/extended test | The silent failure it guards |
| --- | --- | --- |
| 0 | `WarpTrace` formatting | A trace line that crashes only when diagnostics are on |
| 1 | `LaunchClearanceTest` | A thin wall in the corridor the old (absent) check ignored |
| 2 | `SafeArrivalTest` extension | A block on the exit aperture the AABB sweep missed |
| 3 | `ArrivalTicket` radius/predicate | A footprint edge in an unheld chunk |
| 4 | `AirshipVelocityTest` | A frame slip that becomes a 20× over-command |
| 5 | `FoldCrossingsTest` extension | A short hop below the jump threshold; a missed client |
| 6 | (with remediation item 2) | Hull, client and crew disagreeing after a reload |

Runtime-only checks (a hull surviving an unattended arrival; a real long warp not ghosting) cannot be
unit-tested without a running game; document them as manual acceptance steps rather than asserting
something weaker, as the conventions demand.

---

## Suggested sequencing

```
Phase 0  (instrumentation)         ── do first; unblocks reproduction of everything
   │
   ├── Phase 1  (departure clearance)  ─┐
   ├── Phase 2  (arrival aperture)      ├─ the causes; biggest wins for C and the yeet
   └── Phase 3  (chunk residency)      ─┘
   │
   ├── Phase 4  (velocity guard)        ─ belt-and-braces for C
   └── Phase 5  (anti-desync robustness)─ A and B
   │
Phase 6  (interruption)  ── fold into remediation item 2; not on the critical path
```

Phases 1–3 are the ones that make the yeet, the ghosting and the "arrives into terrain / unloaded
chunks" reports stop happening. Phases 4–5 catch the residue. Phase 0 makes all of it visible, and
Phase 6 is deferred to the existing stall work.

---

## Open questions to resolve before executing

These change the shape of a phase and should be answered against the real Sable/Aeronautics APIs and
a running instance, not guessed:

1. **What exactly makes Sable "kill" a sub-level?** The plan assumes an unloaded landing zone lets
   Sable remove the hull (D1). Confirm the precise condition (chunk unload? physics-world eviction?
   mass tracker invalidation?) — it decides whether phase 3 needs a forceload, a follow ticket, or a
   commit-time refusal. Phase 0's arrival trace is designed to capture this.
2. **Is there a first-class chunk loader to hand?** Does a `WarpAnchor` (or the base mod) already
   keep its area loaded? If so, phase 3's precondition is "is there an anchor-driven loader", which is
   cleaner than a bare forceload.
3. **How fast is a legitimate top-tier passage, really?** Phase 4's ceiling must sit above it. Take
   the figure from a traced Singularity-tier warp (the `toPhysicsVelocity` doc already records
   sampled figures at `airship/Airship.java:330`), not from a constant.
4. **Can the client know a sub-level is mid-warp?** Phase 5's notice-free collapse is safest if it
   can be scoped to warp jumps. If that state is not synced, scope it on the `JUMP_BLOCKS`
   discontinuity alone and accept the wider net.
5. **Does the departure check need to account for the hull turning?** The bow is pilot-set and the
   ship can be commanded to bank; the plan assumes a straight corridor along the world bow (matching
   `WarpFlight`'s own model). Confirm no stage rotates the hull enough to leave the proven volume.
