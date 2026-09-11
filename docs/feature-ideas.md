# Feature ideas

Things this mod could do next, judged against what it already is rather than against what other mods
have. Nothing here is committed to; it is a menu.

Two rules were applied throughout. **An idea has to attach to a system that already exists** — a
feature that needs three new subsystems before it does anything is a different mod. And **it has to
survive the mod's own invariants**: redstone is the only way to launch, a course is an anchor or a fix
and never both, a refusal happens before anything moves, a failed warp never damages the ship, only
the end that dialled a gate pays, and a crossing is a change of side the gate saw for itself. Where an
idea strains one of those, it says so.

Effort is XS (an afternoon) to L (a project). Risk is about design, not difficulty. Numbers are
stable identifiers rather than an order — a section is where an idea belongs, and the number is
how the rest of the document refers to it.

---

## Where the mod is now

This menu was rewritten for **1.3.0**, against a mod that has grown three systems the previous list
could not have known about. What that list said next is preserved in git history; almost none of it is
reprised here, because the newest surfaces are the ones with the most unclaimed room on them.

What shipped, and what it opens:

- **The Rift Modulator** dresses a drive's rift in a pilot's chosen colours and one of five themes —
  Standard, Clockwork, Arcane, Ember, Starlight — spending a trickle of essence only while that drive
  warps. It is this mod's first customisation surface, and it dresses **one machine**. Every other
  rift in the mod still wears its tier colours.
- **The Rift Portal is a real block now**, not a client effect: it lights the room, holds an
  irregular ring's exact shape, has its own opening and closing animation, and is there the moment the
  chunk loads. A gate's aperture is a thing the world can see and stand in, which it was not before.
- **The Rift Beacon** calls a bound airship down to where its holder is pointing, carrying which ship
  it answers to in a `beacon_binding` data component. Recall is a pipeline that exists and has exactly
  one caller.
- **Every machine can be read by a Create Display Link**, and `instability` — the Singularity's
  scattered exit — is implemented at last. The two big debts the previous menu led with are paid.

The through-line for most of what follows: the mod learned to *decorate*, to *stand a portal in the
world*, and to *call a ship home*, and each of those is a verb with one noun attached to it so far.

Two things have not moved, and are stated once here so the ideas below need not repeat them. Rift
Essence is still one grade in one container at one value — a currency with no denominations — so an
essence **sink** is worth more to the mod than another source. And the Rift Scars still generate with
nothing to find in them but the tear.

---

| # | Idea | Kind | Effort | Risk |
| --- | --- | --- | --- | --- |
| 1 | Gate livery | Modulator reach | M | Upkeep model |
| 2 | Livery card | Item | S | — |
| 3 | Earned themes | Progression | S | Gating cosmetics |
| 4 | Reactive livery | Modulator behaviour | S | — |
| 5 | Tame portal | Block | S | — |
| 6 | Portal shutter | Gate behaviour | S | — |
| 7 | Homing beacon | Beacon mode | S | — |
| 8 | Muster check | Beacon / crew | S | — |
| 9 | Fleet roster | Screens | M | — |
| 10 | Fissure shard | Item | S | Economy |
| 11 | Standing derelict | Worldgen | M | — |
| 12 | Survey atlas | Item | M | — |
| 13 | Deep survey | Probe mode | M | — |
| 14 | Readiness relay | Block | S | — |
| 15 | Trawling the throat | Block | M | Balance, not mechanism |
| 16 | The Riftwright's Codex | Research system | L | Parity, grind |

**If you only do three:** the gate livery (1), the tame portal (5) and the homing beacon (7). The
first spends the theme engine you already wrote on the one rift surface it cannot yet reach; the
second turns that same engine into the essence sink the mod most lacks; the third is the recall every
player asks for and the smallest change on the list.

**If you would rather ship four small things:** the livery card (2), earned themes (3), the muster
check (8) and the readiness relay (14). None needs a new system, all four are things a player meets in
their first fleet, and together they make the modulator, the beacon and the console feel like parts of
one machine rather than three deliveries.

**And if you want the one nobody else has:** trawling the throat (15). It is the sole entry carried
over from the previous menu, because nothing built since has replaced its niche — every other idea
here improves something the mod already does, and that one makes the journey itself a place you get
something out of, which it still is not.

**And if you want a system to build the rest of the menu around:** the Riftwright's Codex (16) — the
research loop set out in its own section below. It turns the mod's discovery ethos into a loop the
player drives, lands its payoff on the unread corridor, quietly gives essence its largest sink, and
makes a dozen of the small ideas above into branches worth unlocking. It is the one L-sized entry
here, and the only one that is less a feature than a frame for the others.

---

## Liveries — the Modulator's reach

The Modulator is the newest system and the most obviously half-finished, not because it is unpolished
but because it does one thing to one block. It knows how to read a rift in any colours and any of five
themes; it is pointed at a single drive. Everything in this section is that engine, aimed somewhere
else.

### 1. Gate livery

**Pitch.** Let a Rift Modulator dress an adjacent Rift Gate's portal the way it dresses a drive.

**Why it fits.** This is the best-shaped idea on the list, because every piece of it already ships and
only the wiring between them is missing. The Rift Portal is a real block that holds a ring's exact
shape; the modulator already renders sixteen swatches and five full themes onto a rift's face; and a
gate is a rift a player walks up to and looks at for as long as it stays open, which is a far better
stage for a livery than a drive whose aperture exists for a few seconds mid-jump. Right now a gate is
the one rift in the mod that cannot be anything but its plain self.

**The one real question** is upkeep. A modulator on a drive spends essence only while that drive
warps, which is a bounded, occasional cost. A gate holds its portal open continuously, so the same
"charge while active" rule becomes a permanent drain, and this mod has a stated view that only the end
that dialled a gate pays. Cleanest is to charge the livery once, at dial, folded into the dial cost —
the decoration travels with the connection and lapses when it closes, and no new standing cost is
invented.

**Touches.** The modulator's link resolution — it finds a drive today; teach it to also find a gate
frame it sits against — the portal's renderer, and the gate's dial cost. Its facing already comes from
the block it is placed beside, which a gate frame supplies as readily as a drive.

**Risk.** The upkeep model, above. Get that decision made first; the rendering is a solved problem.

### 2. Livery card

**Pitch.** Stamp a modulator's colours, theme and strength onto a card item, and apply that card to
another modulator to reproduce them exactly.

**Why it fits.** House colours are only a house's if a fleet can share them, and today a livery lives
in one modulator's saved state and nowhere else — so a second ship in the same colours means eyeballing
sixteen swatches twice. `AWDataComponents` already carries a binding on the beacon, so a stack that
remembers a livery has both precedent and a home. It also makes a livery a thing a server player can
gift, sell, or hand a new recruit, which is the first time any of this mod's customisation has been
tradeable.

**Touches.** A new item with a livery data component, a stamp-and-apply pair on the modulator screen,
a handbook line. No gameplay rule moves — a card is a convenience over a screen that already exists.

### 3. Earned themes

**Pitch.** Not every theme is available from the first modulator. Some are unlocked — by sealing a
fissure, reaching a drive tier, or the advancement that already marks the milestone.

**Why it fits.** Five themes handed over at once is five recolours; five themes you arrive at one at a
time is a reason to keep playing after the first. The advancement tree already fires on the events
that would gate them (`fissure_closed`, `ship_summoned`, tier milestones), so the unlock signal exists
and only needs somewhere to land. It also gives the fissure system, which currently ends the moment
you seal it, a lasting reward — the Ember theme for the ruin you closed reads better than a number.

**Risk.** Gating cosmetics annoys players who wanted them, so keep the gates generous and early, make
Standard and at least one colourway free, and never lock a livery a player has already applied. This
is a carrot, not a fence.

**Touches.** A small per-world or per-player unlock set, a lock state on the modulator's theme
picker, and a hook on the advancements that already fire.

### 4. Reactive livery

**Pitch.** A modulator that holds more than one livery and picks between them on a redstone signal or a
Display Link reading — a hull that flies its ordinary colours until an alarm turns it red.

**Why it fits.** It pairs the two customisation surfaces the mod grew in the same version: the
modulator that sets a look, and the Display Link that now reads every machine. Nothing about launch
changes — a livery is decoration, and choosing it from a signal touches no course, no cost and no
crossing. It is the kind of thing a base with a sorting system builds without being asked.

**Touches.** The modulator holds a short list rather than one livery, a comparator or link input
selects the index, its screen gains a slot per entry. Folds naturally into the livery card (2) — a
reactive modulator is one that swaps between stamped cards.

---

## The Rift Portal as a block

Making the aperture a real block was quiet plumbing with a large surface area behind it: a lit,
shaped, animated pane is now a thing the mod can place for reasons other than "a gate is open here."

### 5. Tame portal

**Pitch.** A crafted, inert Rift Portal pane that goes nowhere — it lights a room, shows a chosen
livery, and is there to be built with.

**Why it fits.** This is the essence sink the mod most needs and the cheapest one it could build,
because the hard part — a semi-transparent, self-lit, theme-coloured pane that tiles to a frame — is
the block that already ships, minus the gate behind it. The mod adds fifteen-odd items and not one is
for building, while a player with a full essence tank and a finished gate currently has nowhere to put
any of it. A wall of tame rift, in the colours off idea 2's card, is somewhere.

**Touches.** A block that reuses the portal renderer with the crossing logic removed, a filling or
mixing recipe that drinks essence, the livery data component so it can be coloured. The texture and
model paths are the portal's own.

**Risk.** None to the mechanism. The only care is that it must read as decoration, not as a gate —
a different frame, or a clearly inert idle animation, so nobody walks into a wall expecting a trip.

### 6. Portal shutter

**Pitch.** A gate that holds its connection but closes its portal on a redstone signal, and opens it
again when the signal drops.

**Why it fits.** The mod already believes committing something to a journey is a deliberate act with a
lever behind it; a base with a permanent gate to a mine currently has a permanent hole in its wall,
and this makes the hole a door. Now that the aperture is a real block with its own opening and closing
animation, a shutter is a fourth gate state driving an animation that already exists, rather than a new
effect to author. It composes with everything Create can automate.

**Touches.** A state on `RiftGateBlockEntity` (a flag on `OPEN`, or a fourth state), the dial panel, and
the portal block's shown/hidden switch — which is the same transition the gate plays when it closes for
real, stopped short of tearing the connection down.

---

## The Rift Beacon

Recall shipped with exactly one behaviour: call the bound ship to where you point. It is a pipeline
with room for more callers and more destinations, and the binding that drives it is already a data
component other things can read.

### 7. Homing beacon

**Pitch.** A beacon mode that recalls the ship to its bound **anchor** instead of to the holder's feet
— "go home", not "come here".

**Why it fits.** It is the recall players reach for first: a beacon that brings the ship to *you* is
for a rescue, but the everyday want is a button that parks the ship where it lives. The arrival at an
anchor is already the most-validated destination the mod has, so the target is the only thing that
changes — the recall pipeline, the cost, the effects all stand. It also sidesteps the failure that a
"come here" recall can hit, of dropping a hull onto ground the arrival search would never have picked.

**Touches.** The beacon binding gains an optional anchor alongside the ship, the recall's target reads
it, the beacon's tooltip and screen say which of the two modes it is in. A course is still an anchor or
a fix — this is a recall, not a course, and does not touch that rule.

### 8. Muster check

**Pitch.** Before a beacon fires — or before a drive launches — it names who is aboard and warns
anyone standing off the hull who is about to be left behind.

**Why it fits.** `CrewManifest` already does the hard half: it knows who is on the hull and puts them
back in their seats mid-fold, on a grace window the config already exposes. Surfacing that a moment
*before* departure rather than only during it turns a piece of safety machinery into a thing a captain
reads. The beacon is the natural place to hang it, because pressing recall is exactly the moment you
want to know the away team is aboard.

**Touches.** A read of `CrewManifest` from the beacon and the drive console, a chat line or goggle
overlay, no change to the manifest itself. Nothing moves that would not have moved anyway — this only
tells you first.

### 9. Fleet roster

**Pitch.** A board that lists the ships a player has bound beacons to, and where each one currently is.

**Why it fits.** A `beacon_binding` names a ship, and once a player has more than two, "which beacon is
which, and where did I leave the second one" is a real question with no answer in the mod. The data is
all recorded; nothing reads it in aggregate. This gets better the larger a fleet grows, which is the
direction a long-lived server goes, and it is the natural screen the beacon has been missing.

**Touches.** A block or a beacon screen mode, a query over bound ships and their sub-level positions,
`AWLayouts` and `ScreenLayoutTest`. Reuses the terrain hologram or the chart's list rather than drawing
anything new.

---

## Fissures and the scar

Sealing a fissure ends it and gives nothing back; a scar generates and holds nothing to find. Both are
systems that stop one step early, and the portal-as-a-block makes the second cheaper to finish than it
was.

### 10. Fissure shard

**Pitch.** Sealing a fissure drops a shard — a material used to build with (idea 5's recipe) or to
unlock a theme (idea 3).

**Why it fits.** A fissure is currently a chore with an intangible reward: you close it because a raw
tear in the world is untidy, not because closing it gives you anything. A shard makes the seal pay,
and it feeds two other ideas on this list rather than inventing a use of its own — it is the material
the tame portal wants and the token an earned theme could cost. It closes the loop the fissure feature
opened and left hanging.

**Touches.** A drop on the seal event, a new item, and whichever of ideas 3 and 5 consume it. The seal
already fires a Ponder-worthy event; this hangs an item on it. If the Riftwright's Codex (16) is built,
sealing a scar is also a research reading, and a shard is the physical half of the same act.

**Risk.** Economy, mildly. A shard has to be worth sealing for but not worth farming fissures over —
one per seal, not scaling with reservoir size, so the reward is the ruin closed and not the essence in
it.

### 11. Standing derelict

**Pitch.** A rare scar that generates with an open, leaking Rift Portal and a wrecked hull hanging
near it — someone else's warp, gone wrong, left where it fell.

**Why it fits.** The scar structures already generate from a weighted pool written by a script, so a
rare fourth variant is a generator function and a pool entry. What makes it newly cheap is the portal
block: a scar can now *contain a portal*, standing open and going nowhere, without a client effect
faking it — which is the single most evocative thing a ruin in this mod could hold. It also answers
what the fissure feature raised and dropped: a scar is a ruin with a story, and this is the first one
that tells any of it. A derelict is the one place a *damaged* component belongs — a cracked Rift Core
you re-forge rather than one you pick up working.

**Touches.** A second structure script in `tools/`, a pool entry, a loot table, an inert portal placed
in the structure, and optionally one cracked-component item. No new mechanism — a derelict is scenery
and loot arranged around blocks that already exist. It is also where the Riftwright's Codex (16) is
found: the derelict is the research system's first page, and the two want building together.

---

## Charts, probes, and the throat

The discovery side of the mod is the least changed by 1.3.0, so these lean less on the new surfaces and
more on gaps that were always there. Two finish the probe's own apparatus; one is the corridor, still
untouched.

### 12. Survey atlas

**Pitch.** A book that binds several of a probe's fixes together — name them, order them, carry them,
slot one into an Astrolabe to set a course from it.

**Why it fits.** A fix a probe found lives on that probe and nowhere else, so a discovery cannot be
shared, stored or kept for later, and a player who surveys widely has no way to hold what they found. A
`WarpCourse` is an anchor or a fix and never both — an atlas is a book of fixes, in item form, so
nothing about the drive, the validator or the state machine changes. It is entirely additive to rules
that already hold, and on a server it turns exploration into something with a record and an economy
around it.

**Touches.** A book item with a data component holding a list of fixes, the Astrolabe's screen and
packet to read one out, the probe's panel to write one in, `AWDataComponents`.

### 13. Deep survey

**Pitch.** A second probe mode that reports what is *under* the ground at the far end — depth to
bedrock, whether there is a large cave void, roughly how much ore sits in the band — rather than the
shape of the surface.

**Why it fits.** The probe's whole apparatus already exists: it tears a rift, holds ground open, reads
it and brings a picture back, and it honestly reports when the world had not finished generating.
Reading a different thing out of the same held chunks is a mode, not a machine. It also gives the probe
a job after you have chosen where to go — surface relief tells you where to land, and this tells you
whether the spot is worth landing on.

**Touches.** `DestinationSurvey` gains a second sampling pass, the probe's screen gains a mode toggle
and a second readout, the survey texture gains a palette for depth rather than colour.

### 14. Readiness relay

**Pitch.** A block that reads a drive's launch checklist through the Display Link stack and emits
redstone when every requirement is met.

**Why it fits.** It looks like it breaks the launch rule and does not, which is what makes it worth
building: redstone stays the only way to launch, and a relay is simply a redstone source that happens
to know when a launch would succeed — exactly as Create Simulated's own levers are sources. What makes
it newly natural is that the Display Link can now read the drive, so the checklist the console shows a
person is already a thing a machine can subscribe to. It makes an automated cargo run possible, and it
makes the console's list of green ticks mean something to more than a reader.

**Touches.** A block reading the drive's requirement view over the Display Link path that already
exists, a redstone output, the console's readout. No new read of ship state — this consumes one the mod
already computes.

### 15. Trawling the throat

**Pitch.** A frame bolted to the hull that catches what goes past during a warp.

**Why it fits.** It is the most interesting unused surface the mod has, and the one entry here carried
over unchanged from the previous menu because nothing built since has taken its place. The corridor's
contents are cut deterministically from the rift's own seed, so the server can work out exactly what a
hull passed through with no extra traffic and no new state — the same property that lets two players in
one corridor see the same debris go by. Today the journey is scenery between two places; a trawl makes
the journey the point, and what it brings back is something no amount of mining will get you.

**Touches.** A block, a yield derived from the rift's seed and the distance flown, and the corridor's
existing client effects to show it working.

**Risk.** The yield table is a balance decision rather than a mechanism, and balance decisions age
badly. Start it at what a siphon earns for the same jump and price everything else against that.

---

## Research — reading the rift

*The Riftwright's Codex.* The one large system on this list, and the one the rest of the menu can hang
off. It gets a section of its own rather than a table row's worth of prose because it is not really a
feature — it is a frame that turns a dozen of the features above into things worth reaching.

The mod's founding complaint is that none of this is discoverable, and it answers that complaint three
times over — the Navigator's Handbook, the Ponder scenes, the JEI pages, the advancement tree. Every one
of them teaches *forward*: here is the thing, here is how it is made, here is what to do next. Not one of
them records what *you* found. That is the gap, and it is a better fit here than in most mods, because
this is a mod about going to unmapped places and reading what is there. Research is that verb turned on
the rift itself instead of on the ground beyond it.

It also, at last, lands a payoff on the corridor. The throat is the one system a player passes through
and takes nothing from; trawling (15) claims the *matter* that goes by, and research claims the *reading*.
The two are siblings, not rivals — a trawl is a net, the Aetherograph is a camera, and a hull can carry
both down the same throat and come home with one of each.

### The loop

Four steps: three new blocks and a book.

**1. Capture — the Aetherograph.** An instrument that records whatever rift event happens in its
presence. Bolted to a hull it catches the warp — the corridor it passed, how far, what turned by in the
dark. Set beside a fissure it catches the drain; beside a gate, the crossings. What it produces is an
**aether plate**: an exposed, undeveloped reading, held as a stack item.

The elegant part is the one that makes trawling cheap, too. The corridor is cut deterministically from
the rift's own seed, so the Aetherograph never streams or stores a picture of it — from the seed and the
distance flown, the server can re-derive exactly what a hull passed through. A plate is therefore a short
tag naming *which* rift and *how far*, and developing it later reconstructs the rest. No new traffic, no
per-tick state, and two ships down the same throat expose the same plate.

**2. Develop — the Riftwright's Bench.** A workbench that turns exposed plates into **findings**.
Developing costs essence and a little time, and because essence is one undenominated grade with too few
sinks, that cost is the point: research is the largest, most willing essence sink the mod could add. The
Bench takes rotation like the rest of this mod's machines, so a proper research corner is a lit, turning,
faintly obsessive place rather than a menu with a button.

A finding is not a point on a bar. It is a specific, named thing *understood* — *the corridor is
inhabited*, *a fresh scar's gauge lies*, *the Singularity declines to be precise* — resolved when enough
plates of the right kind have been developed. You do not grind a number upward; you accumulate
observations until one of them clicks into a conclusion.

**3. Record — the Codex.** Each finding writes itself into the **Riftwright's Codex**, a book that is the
companion and the opposite of the Handbook. The Handbook is instruction in a neutral, plain voice. The
Codex is discovery in a voice with a great deal of personality — described in its own right below,
because the voice is half of what this idea is.

**4. Spend — what a finding unlocks.** Findings gate *apparatus and knowledge*, never tuned numbers. This
is the load-bearing rule of the whole design: a research bonus that quietly lowered your warp cost would
fork the mod's carefully tuned, config-owned values per player and break the parity two pilots rely on.
Research does not make your drive better. It makes new drives, modes and behaviours *buildable* — the
deep survey (13), the trawl frame (15), a gate shutter (6), an earned theme (3) — each a recipe or a mode
that stays locked until the finding that explains it is written in the Codex. Some findings unlock
nothing but themselves: an entry that is its own reward, the way an advancement is, only better written.

### Whose Codex it was

The Codex does not start blank. You find it — half-filled, in the hand of someone no longer using it — in
a **standing derelict** (11): a wrecked hull hung in a Rift Scar where a warp went wrong. The early
entries are theirs. They number the things in the throat, grind their own glass to read a scar, object
to the walk from a Singularity's careless arrival, and sign every entry **— P.** The book ends
mid-observation, on the jump that stranded them, in the wreck you are standing in.

Continuing it is the research system. From the moment you take it up, the entries are yours — and the mod
gets a spine of quiet lore with no dialogue and no NPC, because the only character who ever speaks is a
dead navigator you are slowly catching up to. Who P. was, and whether the last jump was an accident, is
left where it belongs: implied, never spelt out. (The name is the author's to fix — *P.* is a
placeholder with room to grow into one.)

This is why the derelict and the Codex want building in the same breath: one is where the story starts,
the other is how it goes on.

### The branches

A finding belongs to a branch, and a branch is one of the mod's own systems read closely. Nothing here is
a new subsystem — each is understanding wrung out of something you already do.

| Branch | Read from | A finding or two | Unlocks toward |
| --- | --- | --- | --- |
| **The Throat** | warps, distance flown | the corridor is inhabited; the ring gates keep true time | the trawl frame (15), corridor readouts |
| **The Scar** | fissures drained, scars sealed | a fresh gauge lies; a scar remembers its fold | earned themes (3), the fissure shard (10), deep fissures |
| **The Ground** | probe surveys, arrivals | what the relief hides; what lies beneath it | the deep survey (13), the survey atlas (12) |
| **The Door** | gate crossings | a crossing is a change of side; what a ring will hold | the gate shutter (6), one-way and warded portals |
| **The Drive** | jumps by tier, the Singularity | it declines to be precise; the price of an unsteady exit | Singularity refinements, the readiness relay (14) |

The tree is deliberately shallow and wide — five branches a few findings deep, not a hundred-node
lattice. A player should be able to see the whole of what is left to learn, because a research system a
player cannot see the edge of is a chore, and this mod does not build chores.

### The voice

The instruments speak plainly, on purpose — exactly as the Probe's screen says *Scan ahead* rather than
*throw a sounding*. The Codex is the one thing in the mod allowed airs, because it is a person and the
instruments are not. It is precise, a little vain about its own coinages, tender about the craft, and
honest to a fault about what it does not know — the same honesty the Probe already has when it admits the
world had not finished generating.

> **On the throat.** *They teach you the fold as an equation and a lever, and not one of them warns you
> the throat is inhabited. Not by anything living — by leavings. Things that fell in and never came out,
> turning in the dark at the pace the rift keeps. I have begun numbering them. My apprentice thinks it a
> waste of good chalk. My apprentice has never watched a door of the world shut behind a dining chair.*

> **On the Singularity.** *It does not miss. It declines to be precise, which is a different vice and a
> prouder one — aim it at the harbour and it sets you within a mile of the harbour and dares you to
> object to the walk. I have objected to the walk. It was unmoved.*

> **On a fresh scar.** *The pressure in a new one is a lie. It reads full because it has not finished
> emptying. Do not size your reservoir to the first number the gauge gives you. I did. I shall not
> again.*

> **The last entry.** *Distance nine, then — further than the bench swore the ring would hold. If the
> plate develops clean you will know it worked, because you are reading this somewhere I did not mean to
> send it. Continue the numbering. The throat is worth the ledger even when the harbour is not. — P.*
>
> *Here the hand changes.*

That is the register: someone who loved the work, was better at observing it than at surviving it, and
left you the good notes and the fatal one in the same book.

### Touches

- **Blocks.** The Aetherograph (reads rift events near it) and the Riftwright's Bench (develops plates,
  takes rotation and essence). Both are GeoBlock machines like the rest — mind the base-pivot rotation
  invariant if either turns.
- **Items.** The aether plate (a data-component stack naming a rift and a distance) and the Codex (a
  one-per-inventory book carrying a per-player found/continued state). `AWDataComponents` already carries
  a binding, so both have precedent.
- **Data.** A findings set (per player or per world — see the risk), a small server registry mapping
  findings to unlocked recipes, and recipe-unlock hooks of the kind the advancement tree already fires.
- **Config.** Develop cost, plate yields per event kind, whether research is per-player or world-shared,
  and a master switch to turn the gate off for packs that do not want one on their recipes.
- **Teaching.** A Codex chapter in the Handbook explaining the loop — plainly — a Ponder scene for the
  Bench, and an advancement for taking up the found Codex. The generators for all three already exist.
- **Tests.** The seed→plate→finding round trip (a plate re-derives the same corridor); the
  findings-to-recipe map covers every gated recipe; and — the load-bearing one — no finding unlocks a
  *value*, only a recipe or a mode. That last test is the guard that stops research quietly becoming a
  stat tree.

### Risks

- **Parity, and the shape of the whole thing.** If findings gate recipes and research is per-player, two
  players on one server have different craftable sets — a legitimate stance (vanilla gates recipes behind
  advancements) but a stance, so it must be a config choice with a world-shared option. Decide this
  first; the data model hangs off it.
- **Grind.** A finding must come from *playing the mod* — warping, draining, probing — and never from
  feeding a machine the same plate a hundred times. Diminish repeat plates of one kind, so the road to the
  next finding is to go somewhere new, which is the mod's whole thesis anyway.
- **A stat tree by the back door.** The urge to let research shave a little off a cooldown is strong and
  wrong: it forks the tuned values and the parity. Keep the "unlocks access, not numbers" line bright and
  let the test enforce it.
- **A wandering voice.** One diegetic author, one register, British throughout, and never breaking to
  explain a mechanic — the moment the Codex says "right-click to develop," the spell is gone. Mechanics
  live in the Handbook; the Codex only ever observes. `GuidebookTest` will catch whether the words fit the
  paper; nothing but discipline catches the tone.

### Why it is worth the L

Every small idea on this menu is better with a reason to reach it, and research is that reason wearing a
personality. It hands the corridor a payoff, hands essence its largest sink, hands the derelict a reason
to exist and a voice to carry, and turns a dozen S-sized unlocks into a tree a player can see the edge of
and want to finish. It is the one entry here that is less a feature than a frame for the rest — and the
only one that leaves the player a character to catch up to.

---

## The two that are still upstream of us

### Cross-dimension warp

Warp to an anchor in another dimension. The anchors record their dimension, the whole pipeline is
dimension-agnostic, and the socket is already cut — register a `CrossDimensionWarp.Handler`, set
`allowCrossDimensionWarp`, and everything downstream works unchanged. It stays blocked for the same
honest reason as before: Sable binds a sub-level to one `ServerLevel` and offers no way to hand a plot,
its chunks and its tickets to another. Rebuilding the ship block by block in the target dimension is
the thing an addon should not do. Until Sable offers the move, the right behaviour is to keep refusing
clearly, which is what happens today. Listed here so the next reader does not re-plan it before
checking whether Sable has grown the hook.

---

## A standing cost

Every item on this list carries a handbook page as part of its price, not as a follow-up. That is
cheaper than it sounds — a page is a few lines in `GuideBook` and their translations — and
`GuidebookTest` will say plainly if the words do not fit on the paper or a chapter has come out an odd
number of pages long. Ideas that also want a Ponder scene or an advancement should say so where they
are described; the tooling for both (`tools/ponder_schematics.py`, `tools/advancements.py`, and the
tests that cover their shape) is in place and proven.
