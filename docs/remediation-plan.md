# Remediation plan

Actions for the findings in the codebase audit, ordered so that each phase is independently
shippable and nothing later depends on anything earlier being perfect.

Severity is about what a player experiences, not how interesting the code is.

| # | Item | Severity | Effort | Phase |
| --- | --- | --- | --- | --- |
| 1 | 256-anchor packet crash | High | S | 0 |
| 9 | Repo hygiene and licensing | Blocking release | S | 0 |
| 3 | Astrolabe holds a removed sub-level | Low | XS | 1 |
| 4 | Client state survives a dimension change | Low | S | 1 |
| 5 | Paired constants with nothing binding them | Low | XS | 1 |
| 6 | `DestinationSurvey` record array equality | Low (latent) | XS | 1 |
| 7 | `instability` is a dead config knob | Low | S–M | 2 |
| 2 | `INTERRUPTED` stall on large hulls | Medium | L | 3 |
| 8 | Two god classes | Maintainability | L | 4 |

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

1. `git rm -r --cached Depends/`, add it to `.gitignore`, and document in the README how to populate
   it. 13 third-party jars and 105 MB of `.git` currently redistribute Create, Sable, Aeronautics,
   GeckoLib, Flywheel, Veil, Ponder and Registrate from a public remote. **History rewrite is a
   separate decision** — removing them going forward does not remove them from past commits.
2. Decide the licence. `All Rights Reserved` with no `LICENSE` file is the MDK default rather than a
   choice, and it blocks modpack inclusion. Add a real `LICENSE` once decided.
3. Move `deploy_dir` out of the committed `gradle.properties` into `~/.gradle/gradle.properties`.
4. Commit. Everything from the last several sessions is still uncommitted.

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
