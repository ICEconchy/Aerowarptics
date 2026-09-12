# Changelog

## 1.3.1

### Added

- **Seven more rift themes, and they're all stolen.** Affectionately, and with the names filed just
  far enough off to be deniable. The Rift Modulator's theme button now cycles twelve instead of five -
  and since that's a lot of clicking, **shift-click cycles it backwards**.
  - **Starblocks** - hyperspace. The stars sit still, then all at once draw out into long white lines
    that converge on one point, and that point opens into a mottled, swirling tunnel of blue and white.
    Ride through it and you're *inside* the tunnel: the swirl turning around you and starlines rushing
    past on every side.
  - **Bedrock** - the jump drive. Three rings outside the rim spin up, counter-turning, and open a
    genuine singularity - black, with a gold event-horizon halo hugging it and light bent round it in
    long stretched arcs. The pane gets wound into the dark, and the ship leaves nothing but a slow dark
    ripple. Inside it's black, with gold ripples rolling past and the halo waiting at the far end.
  - **Boldly Gone** - warp. The lens stretches out along the heading while stars streak past as bright
    forward lines, and the instant it engages there's a white flash and a blue ring blown outward from
    the rim. Inside, you're in the bubble: warped hoops of warp field rolling past and long lines of
    starlight.
  - **Ludicrous Speed** - it starts out as Starblocks. Then the starlines keep accelerating, fatten into
    blinding bands of colour, and *weave* - warp, then weft - into a proper eleven-thread tartan.
    Inside, the whole tunnel is plaid.
  - **Eventful Horizon** - the gravity drive. A spiked core spins up inside three rings on three axes
    and tears open a dark red wormhole: the rings, now enormous, turning violently round it, over a grid
    of spacetime twisted into the middle and jolting to a new shape every couple of ticks. The pane
    doesn't break so much as get *pulled in*, stretched thin and shuddering. It crackles, too, in cold
    arc-light.
  - **Vworp** - the time vortex. Currents of orange and blue spiral in, a clock dial turns backwards
    round the rim, and a police box tumbles end over end in the middle of it all. Inside is the vortex
    itself - and the box, tumbling towards you from far down the tube.
  - **Improbability** - never twice the same. Every seven ticks the rift becomes something else - a
    sperm whale, a bowl of petunias, a knitted doll, a cube, a cup of tea - and it's never only one of
    them: the same thing in two more universes, faintly, turned and shifted and another colour. It picks
    its colours from where it is, so the two ends of one jump won't match.

  These seven bring their **own colours** - hyperspace is blue and white whatever your swatch says,
  which is most of what makes it look like hyperspace. The Modulator's intensity still works on them.
  And they're still *only* a look: not one of them changes a single thing about how a warp runs, or how
  long any of it takes.

- New theme flourishes on the Modulator's panel to match - streaks, gold halos, lenses, tartan
  checks, spinning gyroscopes, police boxes, and for Improbability a mote that rolls for which of the
  others it's going to be.
- **Warp Crystals, and somewhere to get them.** The drive recipes used to ask for levitite, which
  Aeronautics never actually lets you hold. Now there's a proper chain: **Warp Crystal Ore** grows -
  slowly - in the natural stone around a Rift Fissure, and that's the only place it grows. Crush the
  raw crystal into **Warp Dust** (or mix dust from two amethyst and an ender pearl, if you'd rather not
  go fissure-hunting), haunt the dust into a **Warp Shard**, press the shard into a **Warp Crystal**, and
  superheat a crystal with a shulker shell and two amethyst for a **Pearlescent Warp Crystal**. How fast
  a fissure grows ore, how far, and how much in total are all in the server config.
- **Your ship stays where you left it.** A ship carrying a Rift Drive now keeps itself - and the ground
  under it - loaded, and remembers that across a restart. Warp to an anchor out in the wilderness, log
  off, come back tomorrow, and it's still there. The catch: every drive-carrying ship is now a chunk
  loader. Server admins can turn it off with `keepAirshipsLoaded`.
- **Rift Storms.** Rare weather - days apart, a few minutes long - in which space is already coming
  apart. The sky and clouds darken to a bruised violet and the day dims, the sun showing through as a
  smudge. Glowing violet rain replaces ordinary rain - in every biome, deserts included - fizzing where
  it lands. Bolts of rift fire split the sky, each one flashing the sky and ground purple before a low
  roll of thunder, and curtains of rift light hang overhead, faint by day and bright after dark. While
  one lasts, every Rift Drive **cools down in half the time** after a warp, but
  every drive is also as **unstable as a Singularity**: any warp might throw your ship off course. The
  Singularity itself is no worse than it already was, and a Creative drive is unaffected. Storms happen
  under an open sky, so not in the Nether or the End. Operators can call one with
  `/weather rift_storm [duration]`, and `/weather clear` ends it. How rare, how long, how much they
  shorten a cooldown, and whether they happen on their own at all are in the server config under
  `riftStorm`. If the look is too much, `riftStormWeather` in the client config turns off the dark sky,
  rain and aurora; the flashes also respect vanilla's *Hide Lightning Flashes*.
- **Choose how high ships arrive.** A Warp Anchor's panel has an arrival-height slider under its name
  and network, and the Rift Probe has one under its distance slider - so a harbour anchor can bring a
  ship in low enough to step off, and a mountaintop one can keep a big hull well clear of the peaks.
  Drag for a rough height, scroll over the slider for a block at a time. The probe also tells you where
  the ship will arrive from - *Arrives from y 81* - and a ship only ever comes in higher than that,
  if something's in the way, never lower. Anchors and probes start at the old six blocks
  (`arrivalGroundBuffer`), so nothing already built moves; `maxArrivalHeight` in the server config caps
  how high players can set it.
- **Two debug commands for server operators.** `/aerowarptics warp dryrun` runs the whole pre-flight
  against the course you've set and tells you what it thinks, without moving anything.
  `/aerowarptics warp clearance` toggles a live wireframe of the departure path round your ship - amber
  for the hull's own sweep, which is what gets checked, teal for where the rift's opening reaches, red
  for anything in the way.

### Changed

- **Every drive is craftable in survival again.** The Mk III and Singularity recipes used Create's
  refined radiance and shadow steel, which Create 6 can no longer make. They take an **End Crystal**
  and an **Echo Shard** now, and everything that asked for levitite takes Warp Crystal instead.
- **Only what's right in front of the bow can stop a launch.** The drive used to insist on proving the
  entire corridor clear before it would go, and that cost more the bigger the ship was - so the biggest
  ships were refused every time, often with nothing near them. Now it checks the path the bow is about
  to fly into, which is what actually puts a ship into a hillside, and that check costs the same
  whatever the size. The old whole-corridor check is still there as `requireClearLaunch` for servers
  that want it.
- The hull holds still inside the rift during the corridor instead of flying on through terrain nobody
  can see. `corridorDrift` puts that back if you want it.
- A launch that couldn't be checked all the way now says so - *"The launch path could not be checked
  in full"* - instead of claiming the way is blocked.

### Fixed

- **The whole server froze for over a minute when you put a Rift Drive on an airship** - any airship,
  any size - and afterwards blocks wouldn't break, chests wouldn't open, and nothing in a menu would
  apply. The drive's keep-loaded claim was force-loading the entire patch of space Sable reserves for a
  ship rather than the ship itself, and asking every chunk of it to tick. It now holds only the ground
  under the hull, and none of it ticks.
- **Worlds with a Rift Drive in them never finished saving on exit.** Quitting sat on "Saving worlds"
  for as long as you'd let it - half an hour, in one case. To keep your ship loaded, the drive was also
  force-loading the ship's own chunks, which belong to Sable rather than to the normal world, and when
  the game shut down it waited on those chunks to unload - which they never do. The drive now only
  holds the real ground under the ship, which keeps the ship loaded just the same. Worlds already
  affected fix themselves the next time they load; the log says `Dropped N forced chunk claim(s) inside
  airship plot space` when it happens.
- **Big ships refused to launch with nothing in the way.** Three separate limits all grew with the
  ship and all reported "blocked": a cap on how much ground could be loaded for the check, a budget that
  open water and plants ate through without ever finding anything solid, and a corridor that stretched
  with the length of the hull.
- **Big ships could vanish on arrival** while their crew arrived safely. The far end of the ship was
  let go the moment the arrival's hold lapsed, and Sable removed the rest of it.
- **Warp stability.** A ship can't be fired off at an absurd speed during a jump any more - there's a
  hard ceiling (`maxCommandedSpeed`) and the jump aborts if it's broken. Everyone watching is told the
  ship *jumped* rather than flew, so it no longer ghosts across the sky between the two ends, and the
  ground at both ends is held loaded for the whole jump.
- **Server crash when a plot was released out from under one of this mod's blocks.** Sable wipes a
  plot's chunks to `void_air` in one go, but the block entities inside get one more tick each - and
  anything they do in that tick that touches the world asks Sable to change a block in a plot that no
  longer exists, which takes the server thread down with an
  `UnsupportedOperationException: Cannot change blocks in nonexistent plot holder`. Caught in the
  wild on a **Rift Fissure**, which is the easiest one to hit by accident: assemble a hull around a
  tear, take the hull apart, and the tear's first tick afterwards would crash the game. Every ticking
  block in the mod now stops dead when its block has gone rather than carrying on regardless.
- **A ship built from more than one sub-level could never warp at all.** Anything Sable had joined to
  the hull - a tender, a gondola, a turret on its own plot - was folded into the volume the launch
  check sweeps, and then read by that same check as *another airship parked in the way*. "Blocked",
  on every bearing, at every heading, under clear sky. A vessel's own attached plots now count as
  part of the vessel.
- **Propellers made the corridor far bigger than the ship.** Clearance was measured from the whole
  assembly, and Create sizes a spinning contraption's box to enclose the blades' full rotation - so a
  pair of propellers widened the path that had to be proven clear by the span of the disc, on every
  side, down the whole length of the run, and scaled the rift aperture to match. The launch check,
  the arrival search and the clearance overlay all measure the hull itself now.
- **The launch check refused for things beside the ship.** It used to widen the path by the width of
  the rift's opening plus `arrivalClearance`, so grass, shoreline and hillside the hull was never going
  to touch counted as being in the way. It now checks only the path the hull itself flies through -
  the **amber** box in `/aerowarptics warp clearance`. The **teal** box is still drawn, so you can see
  how far the rift's opening reaches, but nothing inside it stops a launch any more.
- **The ground a moored ship sits on counted as an obstruction.** A ship resting on the ground settles
  a hair into it, and the check took that to mean the whole layer underneath was inside the ship's
  path - so the ground was marked red for the full length of the run, and so was the row of blocks
  against the far side of the path. Only blocks that actually reach into the path count now; blocks
  merely touching it don't. The arrival search reads blocks the same way, so it's a block less fussy
  at its edges too - it still keeps its `arrivalClearance` gap round the ship.
- **The clearance overlay's red blocks match the check.** They used to mark everything in the teal box;
  now they mark only what would actually stop the launch. `/aerowarptics warp dryrun` also prints the
  hull's exact box now, and no longer lists the aperture margin against the corridor, since the
  corridor doesn't use it.

## 1.3.0

### Added

- **Rift Modulator** - the fun one. Bolt it on next to a Rift Drive and you get to decorate the rift it
  tears open. Pick a core colour and a rim colour (sixteen swatches each - left-click for the core,
  right-click for the rim), crank the intensity anywhere from a whisper to *far too much* (25–200%),
  and choose a theme. It all follows through from the aperture to the corridor to the shockwave you
  arrive on. Costs a slow trickle of Rift Essence, and only while the drive's actually warping - leave
  it unfuelled, unlinked, or with matching colours and nobody's the wiser, it just looks like a plain
  old drive. Faces whichever way the drive does.
  
- **Five rift themes.** And these aren't paint jobs
  - **Standard** - the one you already know. The world getting shattered, no fuss.
  - **Clockwork** - a great toothed gear with little satellites spinning against it and an honest-to-god
    working clock face across the middle, hands and all. Cranks up under load, then irises open like a
    camera shutter.
  - **Arcane** - two dozen runes around the rim on their own flickering schedules, a
    seven-pointed star scratched across the face.
  - **Ember** - a mouth, frankly. Eighteen fangs biting inward and breathing, seven eyes peering in
    from outside and *following you around the room*. 
  - **Starlight** - three real tilted orbits leaning out of the rift's plane, with planets that swing
    round behind it and vanish for a moment. Drifts apart on a slow twinkling spiral.

  And all of it wears whatever colours you picked, no strings attached. Want a hellmouth in baby blue?
  Go right ahead.
- Every machine - Drive, Gate, Probe, Siphon, Chute, Modulator - now talks to a Create Display Link.
  Point one at a Modulator and it reads the drive it's dressing up.
- New Ponder scenes and advancements to go with all this - including **House Colours** for the
  Modulator - plus some catch-up scenes and advancements for last version's beacons and fissures. Tap
  **[W]** over the Handbook and it drops you straight into the Ponder chapters now.
- Rifts throw lightning. Off the torn edge of the aperture, crackling down the corridor behind it, and
  every so often a bolt reaches all the way to the ground and leaves a faint shimmer on whatever it
  hits for a few seconds. Not particles - actual drawn lightning, sharing the same glow as the fire and
  cracks. Toggle it with `riftLightning` if it's too much.
- **Theme Buttons.** When selecting a theme, they show a little particle effect in the gui.

### Changed

- A gate's opening is a **real block** now - the **Rift Portal** - instead of something your graphics
  card was faking. So it lights the room, holds the exact shape of whatever weird ring you built, and
  is just *there* the instant the chunk loads. Walking through feels the same as ever.
- The portal pane got some polish: it's see-through like a nether portal now, and fades in and out
  properly when a gate opens or closes.
- Holding a gate open **costs essence over time** now, not just to dial. A base rate plus a bit per
  block, ticked every second, and only the end that dialled pays. Config `gateUpkeepCost` /
  `gateUpkeepCostPerBlock`, both to zero if you'd rather it stayed free.
- Rift Drives are tesseracts now. All five tiers. They look great, trust me.
- The Singularity's `instability` setting actually does something to your warp exit now, which it
  embarrassingly wasn't before.

### Fixed

- **Gates on ships finally work.** Build one on an airship or a land vehicle and it just... didn't,
  before - the dial panel wouldn't open and even a live connection never spotted anyone stepping
  through. Turns out a block's address on a moving hull isn't its world address, and both checks were
  assuming otherwise. They do the maths through the hull now, whichever way it's pointed.
- ...and a sneaky follow-on to that: you could cross *onto* a ship through a gate but not back off it.
  A hull keeps track of who's standing on it (that's how it carries passengers through a gate as one
  lump), and that same bookkeeping was quietly deciding those people couldn't be crossing a gate on
  their own hull. Fixed.
- Killed the idle-disconnect timer. Gates don't hang up on you just for standing around any more -
  only when they genuinely can't keep going (no essence, no rotation at the dialling end).
- Fixed a jdk issue where the java noise was using a jdk only algorithm
- Warp has been stabilised, should work more often correctly.
- Siphon tanks now have transparency
- Gui's have been tweaked and should now show correctly on Gui scale: Auto

---

## 1.2.0

Adds the Rift Beacon, Rift Fissures, and Rift Infused Goggles. Astrolabe tables can now be built at
three different sizes (1×1, 2×2 or 3×3), machines are drawn properly in your hands, and a server
crash on well-explored worlds is fixed.

### Added

- **Rift Beacon** - a hand-held summons. Bind it to a Rift Drive while you're aboard, then aim at
  anywhere in sight and right-click, and that ship warps to you. You don't need to be on it, or
  anywhere near it. The spot it's called to gets a beam of light telling you where it will be placed.
  Everything else about the jump works as usual: the drive still needs speed, charge, range, and
  somewhere the hull actually fits.
- **Rift Fissures** - tears in space that were already there. They generate out in the world, in
  ruins, and they leak Rift Essence. Set a Spatial Siphon beside one and it draws from it steadily,
  no warping required.
- **Rift Infused Goggles** - like Create's engineer's goggles, but with the added benefit of seeing
  Rift Fissures.

### Changed

- **Machines are drawn as machines in your inventory and your hand.** Drives, anchors, siphons,
  chutes and astrolabe panels use their real models instead of placeholder icons.
- **Buckets of Rift Essence now look like it.**
- **The Creative Rift Drive no longer has a minimum warp distance**, so it can move a hull across the
  room. Every other tier still has one.
- **Astrolabe Cartography Tables can be built at three different sizes.** A single block is a working
  table, so you can chart something the day you craft one. Bigger tables survey proportionally more
  ground.

### Fixed

- **Fixed a server crash on well-explored worlds.** Opening an Astrolabe, or a Rift Gate's dial list,
  could take the server down once enough anchors or gates existed to see. Long lists are now trimmed
  to the nearest ones, and the screen tells you how many it's showing out of how many there are.
- **The Astrolabe's projected map sits on its table again.** It could end up floating off to one side,
  hanging at an angle, and stuttering while the ship was under way. All three are fixed, and the
  terrain now stays put on the ground it was surveyed from rather than being towed along behind the
  hull.

---

## 1.1.0

Major update. Adds Rift Gates, the Rift Probe, the Astrolabe, the Spatial Siphon, a full UI rebuild, Ponder/JEI/advancement support, and fixes a serious bug where long-distance warps could throw players off the ship or corrupt the airship's sub-level.

### Added

- **Rift Gate & Rift Gate Frame** - build a ring of frame in any size and shape, drop a controller in it, and dial another gate to open a walk-through (or drive-through) doorway between them. Works on foot, on mobs, and on Aeronautics land vehicles. Ring size is the size limit - nothing bigger than the opening can pass. Only the dialling gate needs essence and rotation; the far end just has to exist. Gates are named, owned, and access-controlled like Warp Anchors. Same-dimension only.
- **Rift Probe** - scan a direction and distance from the ship without having ever been there, and get back a top-down survey of what's out there before committing to a course. Costs Rift Essence; longer scans cost more. The wait is real world generation happening on the server, not a fake progress bar.
- **Astrolabe Cartography Table** - a 3x3 multiblock that shows a live relief map of the ship's surroundings and a chart of every reachable Warp Anchor, complete with distance, cost, and a terrain preview of each destination.
- **Spatial Siphon & Rift Essence** - bolt one to the hull and it collects Rift Essence (a real, pipeable fluid) each time you warp. Essence now fuels gates and probes.
- **Creative Rift Drive** - no recipe, no cost, no cooldown, no range limit. For testing and creative worlds.
- **Rift Fissures, Rift Scars and Rift Infused Goggles** - ruins now generate in the overworld where somebody's gate went wrong: a broken ring, a sunken vault, or a survey mast. Each has a Rift Fissure in it - a tear that never closed, still leaking Rift Essence. Fissures are completely invisible, have no hit box and cannot be broken; the only way to see one is to wear Rift Infused Goggles, which are Create's goggles with a rift-ground lens and work as engineer's goggles for every machine besides. Stand a Spatial Siphon within a few blocks of a fissure and it drains into it, shrinking as it empties and sealing over for good when it is spent. One fissure holds several times what a single vessel does.
- **Navigator's Handbook** - an in-game guide book with its own screen. Craft it from a book, a brass sheet and an amethyst shard, right-click, and it opens as a book: two pages at a time, leather boards and parchment, a ribbon per chapter along the top, arrows at the corners, and a contents page you can click. Nine chapters walk the whole mod step by step - fitting a drive, marking destinations, launching, the probe, gates, essence and cargo, fissures, and what to do when nothing happens - and each of the eighteen pages carries an animated diagram. Pages turn with an actual page turn: the leaf squashes into the spine, casts a shadow on the sheet under it, and opens out the other side.
- **Ponder scenes** for the Spatial Siphon, Warp Anchor, Astrolabe, Rift Probe, Rift Drive, and Rift Gate (two scenes), split across two Ponder chapters.
- **13 advancements**, including two custom triggers: one for completing a warp (tracks distance, drive tier, and whether the destination was a probe scan) and one for travelling through a gate (on foot vs. as a vehicle).
- **JEI support** - every block/item gets an info page covering setup gotchas a recipe alone won't explain. Fully optional; the mod works fine without JEI installed.

### Changed

- **Block items are now drawn from the blocks themselves.** The Rift Drives, Warp Anchor, Spatial Siphon and Rift Chute had flat sprites standing in for them in the inventory; they are now rendered from the same GeckoLib model, skin and idle animation the block uses in the world, so they turn in your hand and in item frames and can no longer drift out of step with the machine. The Astrolabe's item is its panel's real model rather than a sprite of one. The nine placeholder icons are gone.

- **Destination selection moved off the Rift Drive.** The drive's screen is now a diagnostics panel - speed, stress, charge, and a checklist of what's blocking the next jump. Courses are set at an Astrolabe, and a redstone signal is now the only way to launch.
- **All five screens rebuilt** with a consistent layout, shared visual style, and animated bars/readouts.
- **Aperture visuals reworked.** Rifts now shatter open like broken glass instead of irising, with actual debris drifting through the tunnel and a light visible at the far end. Rift Gates close the same way in reverse - shards pull back in and seal shut, rather than the hole just vanishing.
- **Passengers stay aboard during a warp**, seated or not, even through long jumps.
- Machines are now detected reliably as part of the ship, instead of occasionally being missed depending on build history.
- **Gate stress cost lowered** - a 5x5 gate no longer costs about 3 crushing wheels' worth of stress; it's roughly a quarter of that per block now.
- **Recipes rebalanced across the board**, most notably the Singularity Drive, which previously required four complete Mk III drives and five Singularity Cores for one item. That's fixed.
- **Rift Probe UI reworded** to plain language - "Direction"/"Distance" instead of "Bearing"/"Range", "Scan ahead" instead of "Throw sounding", and a "Send ship here" button that now clearly shows when a course is set.
- Setting a course from the Probe now gives clear feedback - a chat message, an updated Probe panel, and a course marker on the Astrolabe's chart.

### Fixed

- **Long-distance warps could kick players off the ship and make the airship appear to vanish.** This was actually four separate bugs:
  - Very large single-tick jumps could break Sable's collision handling entirely, leaving players standing on nothing.
  - The client wasn't always told the ship had moved, so players could visually lag behind or fall through the deck.
  - A drive could silently fail mid-warp with no error logged, which made earlier attempts at fixing this look like they weren't working.
  - A stalled-warp cleanup routine could tow an already-arrived ship back to its departure point.
- **Rift Gates didn't actually work when dialled** - a passive receiving gate could block the connection from ever completing.
- **Gate crossings were sometimes missed**, especially for players, due to how movement was being tracked.
- **Rift Essence couldn't be piped out of a Rift Gate** - the fluid capability was never registered, even though the gate stored and displayed essence correctly.
- **Closing gate apertures could vanish instantly** instead of playing their closing animation, on gates that had been open a very long time.
- **Rift fire/glow effects were invisible from one side** of the aperture.
- **Map/chart colors were wrong** - grass rendered blue-green and water rendered orange due to a byte-order mixup.
- **Gate settling timers ran one tick longer than intended.**

### Removed

- **Flight Lever** - replaced by chart-based course setting and redstone-triggered launches.

---

Also included: a large expansion of the automated test suite (9 → 30 test classes, 268 tests total) covering gate handedness, aperture shatter geometry, screen layout, recipe balance, and translation coverage, to catch this kind of regression earlier next time.
