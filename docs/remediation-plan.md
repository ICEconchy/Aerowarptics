# Remediation plan

Actions for the findings in the codebase audit, ordered so that each phase is independently
shippable and nothing later depends on anything earlier being perfect.

Severity is about what a player experiences, not how interesting the code is.

The **Status** column was re-checked against the tree on 2026-08-28. Two items are done; the rest are
as written. A second scan on the same date added items 10 to 15, which are set out after phase 4.

| # | Item | Severity | Effort | Phase | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | 256-anchor packet crash | High | S | 0 | **Done** |
| 9 | Repo hygiene and licensing | Blocking release | S | 0 | Open |
| 3 | Astrolabe holds a removed sub-level | Low | XS | 1 | Open |
| 4 | Client state survives a dimension change | Low | S | 1 | Open — wider than written, see 14 |
| 5 | Paired constants with nothing binding them | Low | XS | 1 | Open |
| 6 | `DestinationSurvey` record array equality | Low (latent) | XS | 1 | Open |
| 7 | `instability` is a dead config knob | Low | S–M | 2 | **Done** — implemented |
| 2 | `INTERRUPTED` stall on large hulls | Medium | L | 3 | Open |
| 8 | Two god classes | Maintainability | L | 4 | Open |
| 10 | An airship's name overruns the packet codec | High | S | 0 | Open |
| 11 | Two registries load unhardened records | Medium | S | 1 | Open |
| 12 | `indexName` steals a colliding name | Low | XS | 1 | Open |
| 13 | A cross-dimension warp moves before it plans | Low (latent) | S | 1 | Open |
| 14 | Three more classes keep client state across a dimension change | Low | — | 1 | Open |
| 15 | Open-ended upper version bounds | Low | XS | 0 | Open |

---

## Phase 0 — before anything is published

### 1. Cap the destination and gate lists

**Problem.** `WarpValidator.quoteAll` is uncapped; the packet encodes with `ByteBufCodecs.list(256)`,
and `writeCount` throws `EncoderException` above the limit. Server-side failure, every Astrolabe
open, once a world has 256 visible anchors. `maxAnchorsPerPlayer` defaults to unlimited.

**Complication found while planning.** `AstrolabeChartScreen` does *not* sort — it renders
`data.quotes()` in registry order. So truncating without sorting first would drop an arbitrary
subset, which is worse than the crash because it is silent.

**Approach.**

1. Add `WarpQuote.MAX_ON_CHART = 256` as the single source of the number, and have the packet's
   codec read it rather than repeating the literal in `encode` and `decode`.
2. In `quoteAll`, sort before truncating: usable anchors first, then by ascending distance. Nearest
   reachable destinations are the ones a pilot wants; the tail of a 400-anchor list is not.
3. Extract the sort-and-truncate step as a pure static method taking `List<WarpQuote>` so it can be
   unit-tested without a `ServerLevel`.
4. Add `int totalAvailable` to the chart packet and have the header say `showing nearest 256 of 412`
   when it truncated. Silent truncation is the failure mode being replaced, so it must not be the
   replacement.
5. Same treatment for `RiftGateRegistry.dialableFrom` and `ClientboundGateDialPacket`.

**Verify.** New test: build 400 synthetic quotes, assert the capped list is exactly 256, that every
usable quote sorts ahead of every unusable one, that distances ascend within each group, and that
the cap constant matches the codec limit. The last assertion is the one that stops this regressing.

**Risk.** Low. Nothing outside these two paths reads the lists.

### 9. Repo hygiene and licensing

Carried over from the release audit; blocks a public repo rather than a working mod.

**Done.** `Depends/` was purged from the whole history and is now gitignored, with the README
listing what to download into it. The licence is CC BY-NC-SA 4.0 (`LICENSE`). `deploy_dir` is blank
in the committed `gradle.properties` and set per machine in `~/.gradle/gradle.properties`.

---

## Phase 1 — small, independent correctness fixes

Each of these is a single-file change with a test. They can be done in any order, or in one sitting.

### 3. Release the removed sub-level in `AstrolabeBlockEntity`

`RiftDriveBlockEntity` nulls `airshipSubLevel` when it sees `isRemoved()`; the Astrolabe checks the
same condition but keeps the reference, pinning a whole disassembled plot for as long as the block
entity is loaded.

Mirror the drive's null-out. Better, since both classes now carry the identical cache-with-grace-
window pattern including a duplicated `AIRSHIP_GRACE_TICKS`: extract it into a small
`AirshipHandle` helper holding the cached reference, the tick counter and the resolve/invalidate
rule, and have both block entities own one. That also closes half of item 5.

**Verify.** The helper is plain enough to unit-test: resolve, tick past the grace window, mark
removed, assert the reference is dropped.

### 4. Clear client state on a dimension change

`FoldCrossings.sweep` and `RiftEffectManager.onClientTick` clear only when `level == null`.

Cache the last seen `ClientLevel` dimension key in each and clear when it differs, or clear both from
a single `EntityJoinLevelEvent`/level-change hook so the rule lives in one place. Prefer the shared
hook — two classes independently remembering which dimension they were in is how they drift apart.

**Verify.** Hard to unit-test without a client. Accept manual verification: warp, then step through a
nether portal mid-effect, and confirm no rift is drawn on the other side.

### 5. Bind the paired constants

- `RiftGateBlockEntity.CLOSE_TICKS` and `RiftEffectManager.GATE_CLOSE_TICKS` must agree or the seal
  animation desyncs from the state machine. Make one the source and have the other reference it — the
  gameplay side should own it, and the renderer should read it.
- `AIRSHIP_GRACE_TICKS`, duplicated in the drive and the Astrolabe, is absorbed by item 3's helper.

No test needed if there is genuinely one constant. If a shared reference is impossible across the
client/common split, then a test asserting equality is the fallback — but that is the worse outcome.

### 6. Fix `DestinationSurvey` equality

It is a record with `byte[] colours, byte[] relief`, so the generated `equals`/`hashCode` use array
identity. Nothing compares surveys today, so this is a trap rather than a bug.

Add explicit `equals`, `hashCode` and `toString` using `Arrays.equals`/`Arrays.hashCode`. Keep it a
record — the accessors and the constructor are fine, only the three generated methods are wrong.

**Verify.** Test that a survey equals itself after a `STREAM_CODEC` round trip. That is the exact
case that would surprise somebody later, and it fails today.

---

## Phase 2 — a product decision, then work

### 7. `instability`: implement or delete

The knob exists on every tier (Singularity `0.15`), is exposed in the server config with the comment
*"Chance per warp that the drive scatters its arrival point"*, and is described in the README as the
Singularity's *unstable exit*. **Nothing reads it.**

Two honest options:

**Implement it (recommended).** It is already promised in the README, it is the only thing that makes
the Singularity a distinct kind of jump rather than a bigger one, and it is roughly thirty lines:
roll against `tier.instability()` when the flight is planned, and on a hit offset the arrival search's
starting point by a bounded random amount before `SafeArrival` runs — so a scattered exit is still a
*safe* exit, just not the one you aimed at. Report it to the crew so an unexplained displacement does
not read as a bug.

**Delete it.** Remove from `RiftDriveTier.Defaults`, `AWConfig`, the README table and the changelog's
Singularity line. Cheaper, and defensible if the Singularity's range and cooldown are considered
enough differentiation.

**Do not leave it as it is.** A config setting that silently does nothing is worse than either.

**Verify (if implementing).** Test that instability `0.0` never scatters, that `1.0` always does,
that the scattered point still passes the arrival-clearance check, and that the offset is bounded.

---

## Phase 3 — the hard one

### 2. The `INTERRUPTED` stall on large hulls

Still unfixed. Survivable — the ship stays at the destination and `CrewManifest` places the crew —
but the drive stops ticking at the crossing and logs `INTERRUPTED`.

**A hypothesis worth testing first, because it changes the fix.** The observed log line was
`INTERRUPTED at (20481023, 123, 20489219) stage=BREACH after 0t`. Those coordinates are inside the
airship's plot, and `after 0t` means `sequenceTicks` was zero — the block entity had *just been
constructed and read from NBT*. That is not a chunk unload, which is why the chunk ticket did not
help. It looks like Sable's plot relocation round-trips the block entity through NBT as part of the
move, and `read()` deliberately drops any running flight:

```java
} else if (state.isSequenceRunning()) {
    // A warp cannot survive a reload: drop back to a safe state rather than resuming blind.
    ...
    flight = null;
```

If that is right, the crossing *inherently* serialises the drive mid-flight, and no amount of keeping
chunks loaded will prevent it. The flight has to survive an NBT round trip.

**Step 1 — confirm or kill the hypothesis.** Add temporary logging in `read()` and `saveAdditional()`
recording the position, stage and `sequenceTicks`, and whether the block entity instance identity
changed. Do one large-hull warp. This is diagnosis, not a fix, and it should be a throwaway commit.

**Step 2a — if confirmed: make the flight serialisable.**

1. Give `WarpFlight` `save(CompoundTag)` / `load(CompoundTag)` covering the stage, tick counter, the
   planned entry and exit apertures, the corridor endpoints and the arrival pose.
2. Write it in `saveAdditional`; in `read`, restore it instead of nulling it.
3. Keep `interruptedOnLoad` as the fallback for a flight that *cannot* be restored (version mismatch,
   missing destination) so a genuinely broken flight still fails loudly rather than resuming blind.
4. Re-claim the arrival ticket on resume — the ticket does not survive either.

**Step 2b — if not confirmed**, the block entity is being genuinely unloaded or destroyed, and the
question becomes why the `WARP_DRIVE` ticket did not hold it. Different fix, so do not plan it yet.

**Verify.** `WarpFlight` round-trip test: save at each stage, load, assert every field survives and
the stage machine continues from the same point. Then a manual large-hull warp with
`debug.traceWarps` on, confirming the trace runs to `EMERGE` with no `INTERRUPTED` line.

**Acceptance criteria carried over from the warp-stability plan (its phase 6, folded in here).**
The stability plan does not duplicate this item; it adds these to it, because the drop-and-recover
window is a genuine multi-actor desync (its failure class A3) and this is where it is closed:

1. **One position after recovery.** Once recovery finishes, the hull, every client and every crew
   member agree on a single position — either the origin (restored) or the destination (completed),
   never a third place. Assert this single-position invariant in whatever harness this item gains.
2. **`forgetOrigin` only past the point of no return.** `forgetOrigin`
   (`drive/RiftDriveBlockEntity.java`) must be reached only once the crossing is committed, so a
   recovery can always choose origin-or-destination unambiguously. It already sits immediately after
   the `relocate` at `EXIT_CORRIDOR`; keep that ordering when the persistence contract changes.
3. **The corridor stays resident across the reload.** The corridor/launch ticket added by the
   stability plan's phase 3 (`ArrivalTicket.holdCorridor`, alongside `holdDrive`) covers the reload
   window, so the hull is not unloaded mid-recovery. Re-claim it on resume together with the arrival
   ticket in step 4 above.

**Risk.** Highest of anything here. It changes the warp state machine's persistence contract, and a
half-restored flight is worse than an aborted one — hence keeping the loud-failure path in step 3.

---

## Phase 4 — optional

### 8. Split the two god classes

`RiftDriveBlockEntity` (1,512 lines) and `RiftEffectManager` (1,396). Both are cohesive rather than
tangled, so this is genuinely optional and should be done **after** item 2, not before — refactoring
the drive while its persistence contract is about to change would mean doing the work twice.

If done, the natural seams are:

- **Drive** — lift the flight state machine into its own class (it already has `WarpFlight` to hang
  off), and the packet-building into a `DriveSync` helper. The block entity keeps the Sable
  integration, the kinetics and the redstone.
- **Effects** — the two dozen `draw*` methods are already static and self-contained; they move to a
  `RiftRenderer` wholesale, leaving `RiftEffectManager` owning only the `ACTIVE` list and its
  lifecycle.

Do not attempt both in one change.

---

## Sequencing note

Phases 0 and 1 are safe to do in a single session and would leave the mod publishable. Phase 2 needs
a decision from you before any code is written. Phase 3 should start with the diagnostic commit and
stop there until the hypothesis is confirmed — the fix branches on the answer.

---

# Second pass — 2026-08-28

A fresh scan of the tree, made with the suite green at 447 tests. Items 1 and 7 above were found
already done and are marked so in the table. Items 2, 3, 4, 5, 6 and 9 were each re-confirmed as
still live at the lines they describe.

What follows is new. Nothing here was reachable from the first audit's framing, which is worth
saying: three of the six are the *same* mistake as an item already fixed, made a second time
somewhere the first fix did not look.

## 10. An airship's name overruns the packet codec

**Problem.** Item 1's failure mode, in a place item 1 did not reach — an unbounded value that
something else owns, written through a bounded codec.

`ServerboundAstrolabePacket.sendChart` passes `airship.name()` straight into the chart packet, which
encodes it with `writeUtf(name, 64)`; `ClientboundDriveConsolePacket.of` does the same for the drive
console. `FriendlyByteBuf.writeUtf` throws `EncoderException` above its limit rather than truncating,
so a ship whose name is longer than 64 characters makes the server throw every time anybody opens an
Astrolabe or a Rift Drive console on it.

**Why this is reachable and not theoretical.** `Airship.name()` is `SubLevel.getName()`, which is
Sable's field, not ours, and `SubLevel.setName(String)` is unbounded. Simulated sets it two ways and
neither is capped: `NameplateChangeNamePacket` uses `ByteBufCodecs.STRING_UTF8`, which permits 32,767
characters, and `NamePlatePeripheral.setName` hands the same call to a ComputerCraft script. A player
with a computer can therefore name a hull long enough to break every one of this mod's panels, from
an unmodified client.

**Approach.** Clamp at the boundary, in both senses. The two call sites should truncate to the
codec's own limit before handing the string over, and that limit should be a named constant rather
than the literal `64` repeated at the encode and the decode. A neighbour's field width is not a thing
to trust; the rule is that anything crossing into a packet is bounded by the packet, at the point it
crosses.

**Verify.** Encode a chart packet and a console packet built from a 200-character name and assert
they round trip rather than throw. That test fails today.

## 11. Two registries load unhardened records

**Problem.** `WarpAnchorRegistry.load` is careful: `WarpAnchor.load` returns `null` for anything
malformed, a duplicate id is dropped rather than left to corrupt the index, and the count of what was
skipped is logged and the file re-marked dirty. That care was plainly bought with a real bug.

Its two siblings never received it. `RiftGateRegistry.load` and `RiftChuteRegistry.load` both put
whatever comes back into `byId` with no null check and no duplicate guard, and `RiftGate.load` and
`RiftChute.load` both open with `tag.getUUID("Id")`, which throws on a missing or wrong-typed tag
instead of returning null. One corrupt gate record and the `SavedData` constructor throws during
world load.

**Approach.** Give `RiftGate.load` and `RiftChute.load` the same nullable contract `WarpAnchor.load`
has, and give both registries the same skip-and-log loop. Three near-identical loaders is the actual
smell — if a shared helper can carry the "read a list of records, drop what will not parse, say how
many" shape without dragging the three record types into a common interface, prefer that, because the
next registry will otherwise be the fourth to miss the lesson.

**Verify.** Load a registry from a tag containing one good record, one with no `Id`, and one
duplicating the first, and assert exactly one survives and nothing throws.

## 12. `indexName` steals a colliding name

**Problem.** `WarpAnchorRegistry.indexName` uses `putIfAbsent`, and carries a comment explaining that
a name collision surviving from an older save should lose its index entry but keep its record, so the
anchor stays reachable by id and can be renamed to recover. `RiftGateRegistry.indexName` and
`RiftChuteRegistry.indexName` use a plain `put`, which does the opposite.

Registering a gate whose name another gate already holds hijacks the index. Removing the hijacker
then calls `byName.remove(key, itsOwnId)`, which succeeds, and the original — still present, still
named — is unreachable by name from then on. `rename` is not the way in, because it checks; `register`
is, because it does not.

**Approach.** One line each, matching the anchor. Item 11 touches the same two files, so do them
together.

**Verify.** Register two gates with one name, remove the second, assert the first is still found by
that name.

## 13. A cross-dimension warp moves before it plans

**Problem.** `beginWarp` calls `CrossDimensionWarp.transfer` — the irreversible step — and only
afterwards calls `WarpFlight.plan`, which can still return null and abort with `NO_SAFE_ARRIVAL`. A
warp that fails at that point has already moved the hull into the destination dimension, which
contradicts the invariant that a warp is refused before anything moves.

Unreachable today, because no handler is registered and `transfer` refuses with
`DIMENSION_UNSUPPORTED` before touching anything. It matters anyway: this ordering is the contract a
downstream handler would inherit, and the whole point of the extension point is that somebody else
writes that half.

**Approach.** Plan first, transfer second. The arrival search needs the destination level but not the
airship's presence in it, so the two can be swapped without the plan losing anything. While there,
`CrossDimensionWarp.handler` is a plain static field written by `setHandler` and read from the server
thread — make it `volatile`, or state in the javadoc that registration must happen during mod setup.

**Verify.** A stub handler that records whether it was called, plus a destination with no safe
arrival: assert the handler was never reached.

## 14. Three more classes keep client state across a dimension change

**Problem.** Item 4 names `FoldCrossings.sweep` and `RiftEffectManager.onClientTick` as clearing only
when `level == null`. `RiftShimmer.onClientTick`, `SummonBeacons.onClientTick` and
`AstrolabeProjections.onRenderLevel` do exactly the same thing.

**Approach.** No change to item 4's recommendation — it strengthens it. Five classes independently
remembering which dimension they were in is five chances to drift, so the shared level-change hook
item 4 already prefers is now the only sensible option, and the per-class dimension key should not be
attempted.

**Verify.** As item 4: manual. Warp, step through a nether portal mid-effect, confirm nothing is drawn
on the far side.

## 15. Open-ended upper version bounds

**Problem.** `neoforge.mods.toml` bounds Create at `[6.0.10,)`, aeronautics and simulated at
`[1.3.0,)` and GeckoLib at `[4.8.0,)`. Only `sable` is capped, at `[2.0.0,3.0.0)`. Create's API breaks
between majors, so Create 7 would load this mod and then fail at classload with a stack trace naming
us rather than a dependency message naming the real problem.

**Approach.** Cap each at the next major, as sable already is. Also note that `AGENTS.md` records
GeckoLib 4.8.0 while the jar in `Depends/` is 4.8.4 — harmless, but the table is meant to be the
answer to "what is this built against".
