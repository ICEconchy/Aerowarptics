# Feature ideas

Things this mod could do next, judged against what it already is rather than against what other mods
have. Nothing here is committed to; it is a menu.

Two rules were applied throughout. **An idea has to attach to a system that already exists** — a
feature that needs three new subsystems before it does anything is a different mod. And **it has to
survive the mod's own invariants**: redstone is the only way to launch, a course is an anchor or a fix
and never both, a refusal happens before anything moves, and a failed warp never damages the ship.
Where an idea strains one of those, it says so.

Effort is XS (an afternoon) to L (a project). Risk is about design, not difficulty. Numbers are
stable identifiers rather than an order — a section is where an idea belongs, and the number is
how the rest of the document refers to it.

| # | Idea | Kind | Effort | Risk |
| --- | --- | --- | --- | --- |
| 1 | Essence Cell | Item | S | — |
| 2 | Stabilised Essence | Item + recipes | M | — |
| 3 | Rift Condenser | Block | M | Undercuts travel |
| 4 | Filed Chart | Item | M | — |
| 5 | Deep Survey | Probe mode | M | — |
| 6 | Anchor networks that do something | Screens | S | — |
| 7 | Gate shutters | Gate behaviour | S | — |
| 8 | Gate Keys | Item | S | — |
| 9 | One-way gates | Gate behaviour | XS | — |
| 10 | Crossing filters | Gate behaviour | M | — |
| 11 | Heat Sink | Block | M | — |
| 12 | Drive Governor | Block | S | — |
| 13 | Muster Bell | Block | S | — |
| 14 | Divining Rod | Item | S | — |
| 15 | Deep Fissures | Worldgen | S | — |
| 16 | Fissures that drift | Fissure behaviour | M | Punitive |
| 17 | The Singularity's unstable exit | Warp | S–M | — |
| 18 | Cross-dimension warp | Warp | L | Blocked upstream |
| 19 | Ponder and advancements for the new gear | Teaching | M | **Done** |
| 20 | Arrival preview | Warp | M | Search cost |
| 21 | Derelicts and salvage | Worldgen | M | — |
| 22 | Reading a ring through the goggles | Gate behaviour | S | — |
| 23 | Dialling on redstone | Gate behaviour | S | — |
| 24 | Flight plans | Warp | M | Strains the launch rule |
| 25 | Berths | Block | M | — |
| 26 | Flight recorder | Screens | S | — |
| 27 | Chutes for fluids | Block | S | — |
| 28 | Warped stone | Blocks | S | — |
| 29 | Trawling the throat | Block | M | Balance, not mechanism |

**If you only do three:** the Filed Chart (4), the Heat Sink (11) and the Singularity's unstable exit
(17). The first makes discovery worth something to somebody else, the second gives an airship a reason
to have engineering around the drive rather than just a drive, and the third is a feature you have
already written the config knob and the documentation for.

**If you would rather ship four small things than one large one:** one-way gates (9), reading a ring
through the goggles (22), the flight recorder (26) and a building set (28). None needs a new system,
all four are things a player meets in their first week, and the last is the only entry here that gives
a full tank of essence somewhere to go.

**And if you want the one nobody else has:** trawling the throat (29). Every other idea on this list
improves something the mod already does. That one makes the journey itself a place you get something
out of, which at the moment it is not.

---

## The essence economy

Rift Essence has two sources and three sinks and nothing in between. You warp or drain a fissure to get
it; gates, probes and chutes spend it. It is one grade, in one container, at one value — so it is a
currency with no denominations.

### 1. Essence Cell

**Pitch.** A one-stack item holding several buckets, filled from a tank or a siphon and emptied into a
gate.

**Why it fits.** This is already a friction a player meets. A dialling gate spends thousands of
millibuckets and the only portable container in the mod is a one-bucket bucket, so standing up a gate
at a mine means either plumbing a tank out to it or twelve round trips. `AWDataComponents` exists now
for the Rift Beacon, so a fluid component on a stack has somewhere to live.

**Touches.** New item, a fluid handler capability on the stack, `AWCapabilities`, a recipe, a handbook
line.

### 2. Stabilised Essence

**Pitch.** Essence put through a Create mixer with something dear comes out as a denser grade, worth
several buckets of the raw stuff, used by the Singularity tier and by gates above a certain size.

**Why it fits.** The mod already makes two components in Create machines rather than on a grid — the
Rift Lens is compacted, the Rift Core is mixed hot — so there is precedent and a place to put it. It
also gives the top of the drive ladder something to consume that is not just *more* of the same, and
gives a siphon farm a reason to keep running after the first gate is up.

**Touches.** A second fluid or an item, mixing recipes, `RecipeBalanceTest`'s effort table, the gate's
cost calculation.

### 3. Rift Condenser

**Pitch.** A machine that turns rotation and time into a trickle of essence.

**Why it fits.** It answers "I want a gate but I do not want to fly anywhere", which is a real player,
and it gives Create's power network a direct line into this mod's currency.

**Risk.** It also cuts against the premise, stated in the README and on the siphon itself: *you warp to
gather it; you spend it to reach further*. A condenser that pays better than travelling makes the
travelling pointless, and one that pays worse is a trap for the player who builds it. If it is built at
all it wants to be visibly a poor deal — a stopgap for a base with no ship, not an alternative to
having one.

### 27. Chutes for fluids

**Pitch.** A paired chute that moves fluid instead of items.

**Why it fits.** The item chute is built and works. A ship that can send cargo home still cannot send
essence home, which is the thing it is most likely to have a tank of — and after a fissure, the thing
it is most likely to have too much of. Pairing, access, the per-unit cost and the aperture are all
already there; only the payload changes.

**Touches.** A mode on the existing chute or a second block, a tank and a fluid capability, the chute
panel.

### 28. Warped stone

**Pitch.** A small building set made with essence: bricks, tiles, a lamp, a pillar.

**Why it fits.** The mod adds fifteen or so items and not one of them is for building with, while the
Rift Scars have already settled what its architecture looks like — so the palette is designed and
unused. It is also the cheapest essence sink there is, and the one the mod most needs: a player with a
full tank and a finished gate currently has nowhere to put any of it.

**Touches.** Blocks, models, a filling or mixing recipe that drinks essence, loot tables. The texture
generator already knows how to draw this family.

---

## Charts and discovery

### 4. Filed Chart

**Pitch.** A fix a probe found, written onto an item: name it, frame it, hand it to another player,
slot it into an Astrolabe to set a course from it.

**Why it fits.** This is the best-shaped idea on the list because it is entirely additive to rules that
already hold. A `WarpCourse` is an anchor or a fix and never both — a chart *is* a fix, in item form, so
nothing about the drive, the validator or the state machine changes. And it fixes a real gap: at the
moment a fix lives on the probe that found it, so a discovery cannot be shared, sold, stored, or kept
for later. On a server that turns exploration into something with an economy around it.

**Touches.** New item with a data component, the Astrolabe's screen and packet, the probe's panel (a
"file this" button), `AWDataComponents`.

### 5. Deep Survey

**Pitch.** A second probe mode that reports what is *under* the ground at the far end — the depth to
bedrock, whether there is a large cave void, roughly how much ore is in the band — rather than the
shape of the surface.

**Why it fits.** The probe's whole apparatus already exists: it tears a rift, holds ground open, reads
it and brings a picture back, honestly reporting when the world had not finished generating. Reading a
different thing out of the same held chunks is a mode, not a machine. It also gives the probe a job
after you have chosen where to go, which it currently does not have.

**Touches.** `DestinationSurvey` gains a second sampling pass, the probe's screen gains a mode toggle
and a second readout, the survey texture gains a palette.

### 20. Arrival preview

**Pitch.** Before the lever is pulled, show where the hull will actually come out: its footprint, at
the pose the arrival search picked, drawn over the chart.

**Why it fits.** Everything needed already runs. `ArrivalSearch` climbs the anchor's column and only
then steps sideways in widening rings; `ObstructionScan` proves each candidate clear exhaustively; and
`TerrainHologram` already draws a lit relief above a table. The one thing missing is that all of it
happens at launch and is thrown away. Showing it first turns "somewhere near the anchor, probably"
into a decision, and it is the best answer this mod could give to *why has my ship parked in a tree*.

**Touches.** Run the search when a course is set, carry the chosen pose on the chart packet, draw it
with the hologram that already exists.

**Risk.** The search is not cheap and this would run it on course-set rather than launch. It wants
caching, and it wants running again at launch regardless — the world moves between the two, and the
preview is advice rather than a promise.

### 21. Derelicts and salvage

**Pitch.** A wrecked hull hanging where somebody's warp went wrong, and chests in the Rift Scars,
which at present contain nothing at all.

**Why it fits.** The scar structures already exist, already generate and are already written by a
script, so a second set is a generator function and three JSON files. It also answers something the
fissure feature raised and left: a scar is a ruin with a story in it and nothing to find but the tear.
A derelict is the one piece of worldgen that could justify a *damaged* component — a Rift Core you
have to re-forge rather than one you pick up working, which is a better first sight of the mod than a
recipe list.

**Touches.** A second structure script in `tools/`, a loot table, a structure set, and optionally one
cracked-component item.

### 6. Anchor networks that do something

**Pitch.** Anchors already carry a network label. Let the chart filter and group by it, and let a
player collapse a network to one line.

**Why it fits.** The data is there and the screen ignores it. This is the cheapest genuine quality of
life item on the list, and it gets better the more anchors a world has — which is exactly the direction
a long-lived world goes.

**Touches.** `AstrolabeChartScreen`, the chart packet, `AWLayouts`, `ScreenLayoutTest`.

---

## Rift Gates

### 7. Gate shutters

**Pitch.** A gate that holds its connection but closes its aperture on a redstone signal, and opens it
again when the signal goes.

**Why it fits.** It is the drive's own philosophy applied to a doorway: the mod already believes that
committing something to a journey is a deliberate act with a lever behind it. A base with a permanent
gate to a mine currently has a permanent hole in its wall; this makes the hole a door. It also composes
with everything Create can automate.

**Touches.** `RiftGateBlockEntity`'s state machine (a fourth state, or a flag on `OPEN`), the dial
panel, the aperture's client effects.

### 8. Gate Keys

**Pitch.** An item that lets its holder dial a private gate they do not own, without transferring
ownership.

**Why it fits.** Access is already a real, enforced concept shared between anchors and gates through
`WarpAnchorAccess`. Today it is binary — yours or everyone's — which on a server means a base gate is
either open to strangers or unusable by your friends. A key is the missing middle, and it is a data
component and a permission check rather than a new system.

**Touches.** New item, `RiftGateRegistry`'s access check, the dial panel's refusal text.

### 9. One-way gates

**Pitch.** A toggle: this gate sends, or receives, or both.

**Why it fits.** A drop gate at the bottom of a mine that things go *into* and never out of is a
one-line change to a check that already runs, and it makes automated cargo routes possible without
anything else being built.

**Touches.** A flag on the gate, its panel, `GateWatch`'s crossing rule.

### 10. Crossing filters

**Pitch.** A gate that passes only players, only vehicles, or only items — or charges essence per
crossing rather than per dial.

**Why it fits.** `GateTraversal` already classifies what is crossing, because it has to treat a player,
a mob and a sub-level differently. A filter reads a decision that is already being made.

**Risk.** Small but real: "only the end that dialled pays" is a stated rule with a good reason behind
it, and a per-crossing toll inverts it. Worth doing as a filter first and leaving tolls alone.

### 22. Reading a ring through the goggles

**Pitch.** With the goggles on, a ring of Rift Gate Frame shows the opening it has found — and, when
it has found none, where the ring leaks.

**Why it fits.** "The ring is not closed around a clear opening" is the single most common thing to
get wrong when building a gate, and today the only thing that will tell you is the controller, after
you have built the whole thing and fed it. The flood fill that finds an opening already exists in
`RiftGateStructure`, and the goggles are already this mod's answer to *show me what I cannot see*.
This is that answer applied to the one place where a player is definitely stuck.

**Touches.** A query on `RiftGateStructure` that does not need a controller, and an overlay in
`client/fx`. The fill is cheap enough to run client-side while a player holds frame.

### 23. Dialling on redstone

**Pitch.** A gate that takes its destination from signal strength, or steps to the next gate on its
list with each pulse.

**Why it fits.** It pairs with shutters (7): between them, a gate becomes a thing Create can drive,
which is what any player with a sorting system will want within an hour of building the second one.
Nothing about permission changes — the list a signal steps through is the list that player was already
allowed to dial.

**Touches.** Comparator input on the gate, its dial panel, `RiftGateRegistry`'s reachable list.

---

## Ships and drives

### 11. Heat Sink

**Pitch.** A block that shortens a drive's cooldown for each one built near it, up to a cap — so a ship
that wants to jump often is a ship with an engine room.

**Why it fits.** This is the biggest hole in the mod as a *building* game. Every other machine here is
one block that does its whole job, so an airship needs a drive, a table and a lever and nothing else;
Create's own appeal is that a machine is an arrangement of parts. Cooldown is the right knob to attach
that to because it is already per-tier, already configurable, and shortening it is a benefit that
cannot break anything — a fast drive is not a dangerous drive.

**Touches.** New block, a proximity scan on the drive (read from the plot's own chunks, not Sable's
actor list — see the invariant), the console's readout, config.

### 12. Drive Governor

**Pitch.** A block that watches a drive and emits a redstone signal when every requirement is met.

**Why it fits.** It looks like it breaks the launch rule and does not, which is what makes it a good
piece: redstone remains the only way to launch, and a governor is simply a redstone source, exactly as
Create Simulated's Throttle Lever already is. It also makes the console's checklist mean something to a
machine as well as to a person, and it is the natural other half of an automated cargo run.

**Touches.** New block, a read-only view of the drive's requirement list, redstone output.

### 13. Muster Bell

**Pitch.** A block that reports who is aboard and where they are, and warns anyone standing off the
hull before a jump.

**Why it fits.** `CrewManifest` already does the hard half — it knows who is on the hull and puts them
back in their seats mid-fold. Surfacing that before departure rather than only during it turns a piece
of safety machinery into a thing a captain uses.

**Touches.** A block reading `CrewManifest`, one screen or a goggle tooltip, a chat warning.

### 24. Flight plans

**Pitch.** A route of several anchors, set once at the table, run one leg per redstone edge.

**Why it fits.** It looks like it weakens the launch rule and does not — provided each leg still needs
its own edge. A plan is a list of decisions made at the chart table, which is where permission is
checked, and a signal still replays a decision rather than making one. That is the whole test, and a
plan passes it. What it buys is a cargo run with nobody at either end, which is currently impossible
past the first hop.

**Touches.** The drive holds a list rather than one `WarpCourse`, the Astrolabe gains a plan editor,
the console says which leg is next.

**Risk.** A plan that advanced itself on a timer would fail the test above and should not be built.
The edge has to stay.

### 25. Berths

**Pitch.** A mooring marker that says not only where a ship arrives but which way round it sits.

**Why it fits.** Arrival finds a clear volume near the anchor and puts the hull in it, which is exactly
right for a wilderness and quite wrong for a hangar somebody built to fit. A berth is an anchor with a
facing and a footprint, and the arrival search already thinks in precisely those terms — it is
computing a pose, it simply has no preference about the last part of it.

**Touches.** A facing and an optional footprint on `WarpAnchorBlockEntity`, a preference in
`ArrivalSearch`, a control on the anchor's screen.

### 26. Flight recorder

**Pitch.** The console remembers the last few jumps: from where, to where, what it cost, and why one
was refused.

**Why it fits.** `WarpTrace` already records all of that in detail — to the server log, behind a config
flag, which makes it a diagnostic for whoever wrote the mod rather than for whoever is flying the ship.
The same facts on the panel answer "did that cost more than last time" and "what stopped it", which is
the question the console exists to answer.

**Touches.** A short ring buffer on the drive, the console packet, one more page on its screen.

---

## Fissures

### 14. Divining Rod

**Pitch.** A held instrument that points towards the nearest unclosed fissure within a few chunks and
spends essence each time it is asked.

**Why it fits.** The goggles are the *seeing* half of that feature and there is no *finding* half — you
have to already be standing in the ruin. A rod pairs with them without replacing them: it tells you
there is one somewhere north, and the goggles are still what make it real when you get there.

**Touches.** New item, a chunk-local search for fissure block entities, a client indicator.

### 15. Deep Fissures

**Pitch.** A rarer, larger scar with a much bigger reservoir and more than one tear in it.

**Why it fits.** The structure is already a weighted pool of three variants, so a fourth rare one is a
generator function and a pool entry. It gives the fissure system somewhere to go after the first one is
drained, which it currently does not have.

**Touches.** `tools/rift_scar_structure.py`, the pool JSON, config for the reservoir band.

### 16. Fissures that drift

**Pitch.** A fissure nobody drains slowly widens, and a wide one starts shedding rift debris or
spawning something.

**Why it fits.** It gives a found fissure a clock, which turns "I will come back with a siphon" into a
decision rather than a bookmark.

**Risk.** It is the one idea here that makes the world worse if you ignore it, and this mod has been
careful not to punish — a failed warp does not damage the ship, a refusal happens before anything moves,
and dangerous failures are off by default. If it is built it wants the same treatment: off by default,
and loud about what it is going to do before it does it.

---

## The corridor

The throat is the one system in this mod that a player passes through and takes nothing from. It is
already a *place* rather than a lit pipe — debris to pass, things tearing by, ring gates to measure
progress against — and all of it is cut deterministically from the rift's own seed, so both ends
already agree on what is in there without a byte being sent about it.

### 29. Trawling the throat

**Pitch.** A frame bolted to the hull that catches what goes past during a warp.

**Why it fits.** It is the most interesting unused surface the mod has. Because the corridor's contents
are derived from the seed rather than streamed, the server can work out exactly what a hull passed
through with no extra traffic and no new state — the same property that lets two players in one
corridor see the same debris go by. Today the journey is scenery between two places. A trawl makes the
journey the point: a longer jump passes more, and what it brings back is something no amount of mining
will get you.

**Touches.** A block, a yield derived from the rift's seed and the distance flown, and the corridor's
existing client effects to show it working.

**Risk.** The yield table is a balance decision rather than a mechanism, and balance decisions are the
ones that age badly. Start it at what a siphon earns for the same jump and price everything else
against that.

---

## The two big ones

### 17. The Singularity's unstable exit

**Pitch.** Implement the per-tier `instability` value: the top drive arrives scattered from where it
aimed, by a radius that scales with the number.

**Why it fits.** This is not really a new feature; it is an unfinished one. The config knob exists, the
advancement is already written around it — *"buy its reach with an unsteady arrival"* — and the mod
description promises it. Nothing reads it. That gap is documented in `AGENTS.md` and sits in the
remediation plan as item 7.

**Touches.** `ArrivalSearch` or `SafeArrival` takes an offset before searching, the console shows the
figure, the handbook's gates chapter gains a line. The arrival search still has to prove the scattered
spot clear, so this cannot put a hull inside a mountain.

### 18. Cross-dimension warp

**Pitch.** Warp to an anchor in another dimension.

**Why it fits.** It is the obvious next question a player asks, the anchors already record their
dimension, and the whole pipeline — cost, validation, the state machine, the effects — is already
dimension-agnostic. The mod has deliberately left the socket for it: register a
`CrossDimensionWarp.Handler`, set `allowCrossDimensionWarp`, and everything downstream works unchanged.

**Risk.** Blocked upstream, and honestly so. Sable binds a sub-level to one `ServerLevel` and exposes no
way to hand a plot, its chunks and its tickets to another. Rebuilding the ship block by block in the
target dimension is the thing an addon should not do. Until Sable offers it, the right move is to keep
refusing clearly — which is what happens today.

---

## The debt that reads as a feature

### 19. Ponder and advancements for the new gear

**Done.** Eleven Ponder scenes and nineteen advancements as of this writing: two scenes for the
fissure, one for the beacon, the Handbook listed under both chapters rather than given a scene of its
own, and two new triggers (`fissure_closed`, `ship_summoned`) for the events with no vanilla
equivalent. The rest of this entry is kept for the reasoning.

**Pitch.** Ponder scenes and advancement branches for the Rift Fissure, the Rift Infused Goggles, the
Rift Beacon and the Navigator's Handbook.

**Why it fits.** The mod's stated position is that none of this is discoverable and three systems exist
to fix that. The last few features shipped with only one and a half of them: the handbook covers
fissures and goggles, JEI has pages, but there are no Ponder scenes and no advancements, so the
progression tree stops before the newest third of the mod.

**Touches.** `tools/ponder_schematics.py`, a storyboard in `compat/ponder/scene/`,
`tools/ponder_lang.py`, `tools/advancements.py`, plus a trigger in `advancement/` for closing a
fissure. `PonderTextTest` and `AdvancementTest` already cover the shape of both.

---

## A standing cost

Every item on this list carries a handbook page as part of its price, not as a follow-up. That is
cheaper than it sounds — a page is a few lines in `GuideBook` and their translations — and
`GuidebookTest` will say plainly if the words do not fit on the paper or a chapter has come out an odd
number of pages long. Ideas that also want a Ponder scene or an advancement are marked as such above.
