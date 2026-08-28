# Changelog

## 1.3.0

### Changed

- Rift Portal pane now has its own opening and closing animation, and dims while opening or closing.
- Rift Essence now has an ongoing upkeep cost while a gate holds a connection open, not just a dial
  cost. Base rate plus a per-block rate, charged every second at the dialling end only. Config
  `gateUpkeepCost`/`gateUpkeepCostPerBlock`; set both to 0 to disable.
- Rift Portal pane is now semi-transparent, like a nether portal.
- A Rift Gate's opening is now a real block (**Rift Portal**) instead of a client-rendered effect. It
  lights the room, holds an irregular ring's exact shape, and shows up as soon as the chunk loads.
  Behaviour of crossing is unchanged.
- Rift Drive remodelled as a tesseract, all five tiers.
- The Singularity's `instability` config value is now actually applied to warp exits.

### Added

- **Rift Modulator**: a module bolted beside a Rift Drive that lets its pilot choose the rift's core
  and rim colour (sixteen swatches each - left click sets the core, right click sets the rim - blended
  across the aperture's face rather than one flat tint), its theme and how strongly it all reads
  (25%-200%), applied to the aperture, the corridor wash and the exit shockwave in place of the drive's
  tier colours. Runs on a trickle of Rift Essence, spent only while the linked drive is actually
  warping; an unfuelled or unlinked module changes nothing, and a core and rim left equal draws exactly
  as an undecorated drive does. Takes its facing from the drive it is placed beside. Config
  `modulatorUpkeepCost`/`modulatorUpkeepInterval`.
- **Five rift themes, each with its own geometry, opening and break.** A theme is not a recolour: it
  changes what stands around the aperture for its whole life, how it winds up before the hole exists,
  and how the pane comes apart.
  - **Standard** — unchanged. Struck glass, tumbling shards, no furniture.
  - **Clockwork** — a toothed ring gear with three counter-rotating satellites meshing outside it, and
    a working clock face across the aperture with hour and minute hands at honest relative rates. Winds
    up under load, then ten iris blades sweep aside around the centre.
  - **Arcane** — a band of twenty-four written glyphs circling the rim, each flickering on its own
    clock, with a seven-pointed figure inscribed on the aperture's face. Opens by drawing its circle,
    charging it, and discharging inward; the pane ignites where it stands rather than moving.
  - **Ember** — a mouth. Eighteen fangs bite inward over the rim and breathe, and seven eyes watch from
    outside it, blinking on their own timers with slit pupils that follow you as you move. The pane
    catches, chars, lifts and darkens like burning paper.
  - **Starlight** — three true three-dimensional orbits tilted out of the aperture's plane, carrying
    ring bands and four transiting planets that genuinely pass behind the rift and are hidden by it.
    The pane disperses on a slow spiral, twinkling.

  All of it is drawn in the colours the pilot picked, untinted — a hell portal in pale blue is
  entirely possible.
- Every machine (Rift Drive, Rift Gate, Rift Probe, Spatial Siphon, Rift Chute, Rift Modulator) can now
  be read by a Create Display Link - a link on a Modulator reads the drive it is dressing.
- A Ponder scene and an advancement (**House Colours**) for the Rift Modulator.
- Three new Ponder scenes (Rift Fissure discovery, sealing one, Rift Beacon recall) and five new
  advancements for 1.2.0's gear. **[W]** over the Handbook now opens the Ponder chapters.
- Lightning now sparks off a rift: off the torn rim of the aperture, across the corridor behind it,
  and occasionally a strike reaches all the way down to the ground. Drawn as geometry through the same
  additive shader pass the fire and cracks already use, not as particles. A strike leaves a soft,
  shimmering mark on whatever block it hits, fading out over a few seconds. Config `riftLightning`.

### Fixed

- Rift Gates built on an airship or land vehicle now work. A block's position aboard a Sable hull is
  plot-local, not a world position; the gate's reach check and crossing detection both assumed world
  coordinates, so the dial panel never opened and a formed connection never noticed anyone crossing.
  Both now convert through the hull's pose, including facing for a hull at any heading.
- Fixed a directional bug in the above: crossing from the ground onto a hull worked, but not the
  reverse. A traveller standing on a hull is tracked by it for unrelated reasons (so a hull crossing a
  gate carries its passengers as one unit); that tracking was also incorrectly excluding a traveller
  from being seen crossing a gate mounted on that same hull.
- Removed the idle-disconnect timer. A Rift Gate no longer hangs up for lack of use — only for the
  real requirements (essence, rotation at the dialling end) not being met.

---

## 1.2.0

Adds the Rift Beacon, Rift Fissures, and Rift Infused Goggles. Astrolabe tables can now be built at
three different sizes (1×1, 2×2 or 3×3), machines are drawn properly in your hands, and a server
crash on well-explored worlds is fixed.

### Added

- **Rift Beacon** — a hand-held summons. Bind it to a Rift Drive while you're aboard, then aim at
  anywhere in sight and right-click, and that ship warps to you. You don't need to be on it, or
  anywhere near it. The spot it's called to gets a beam of light telling you where it will be placed.
  Everything else about the jump works as usual: the drive still needs speed, charge, range, and
  somewhere the hull actually fits.
- **Rift Fissures** — tears in space that were already there. They generate out in the world, in
  ruins, and they leak Rift Essence. Set a Spatial Siphon beside one and it draws from it steadily,
  no warping required.
- **Rift Infused Goggles** — like Create's engineer's goggles, but with the added benefit of seeing
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

- **Rift Gate & Rift Gate Frame** — build a ring of frame in any size and shape, drop a controller in it, and dial another gate to open a walk-through (or drive-through) doorway between them. Works on foot, on mobs, and on Aeronautics land vehicles. Ring size is the size limit — nothing bigger than the opening can pass. Only the dialling gate needs essence and rotation; the far end just has to exist. Gates are named, owned, and access-controlled like Warp Anchors. Same-dimension only.
- **Rift Probe** — scan a direction and distance from the ship without having ever been there, and get back a top-down survey of what's out there before committing to a course. Costs Rift Essence; longer scans cost more. The wait is real world generation happening on the server, not a fake progress bar.
- **Astrolabe Cartography Table** — a 3x3 multiblock that shows a live relief map of the ship's surroundings and a chart of every reachable Warp Anchor, complete with distance, cost, and a terrain preview of each destination.
- **Spatial Siphon & Rift Essence** — bolt one to the hull and it collects Rift Essence (a real, pipeable fluid) each time you warp. Essence now fuels gates and probes.
- **Creative Rift Drive** — no recipe, no cost, no cooldown, no range limit. For testing and creative worlds.
- **Rift Fissures, Rift Scars and Rift Infused Goggles** — ruins now generate in the overworld where somebody's gate went wrong: a broken ring, a sunken vault, or a survey mast. Each has a Rift Fissure in it — a tear that never closed, still leaking Rift Essence. Fissures are completely invisible, have no hit box and cannot be broken; the only way to see one is to wear Rift Infused Goggles, which are Create's goggles with a rift-ground lens and work as engineer's goggles for every machine besides. Stand a Spatial Siphon within a few blocks of a fissure and it drains into it, shrinking as it empties and sealing over for good when it is spent. One fissure holds several times what a single vessel does.
- **Navigator's Handbook** — an in-game guide book with its own screen. Craft it from a book, a brass sheet and an amethyst shard, right-click, and it opens as a book: two pages at a time, leather boards and parchment, a ribbon per chapter along the top, arrows at the corners, and a contents page you can click. Nine chapters walk the whole mod step by step — fitting a drive, marking destinations, launching, the probe, gates, essence and cargo, fissures, and what to do when nothing happens — and each of the eighteen pages carries an animated diagram. Pages turn with an actual page turn: the leaf squashes into the spine, casts a shadow on the sheet under it, and opens out the other side.
- **Ponder scenes** for the Spatial Siphon, Warp Anchor, Astrolabe, Rift Probe, Rift Drive, and Rift Gate (two scenes), split across two Ponder chapters.
- **13 advancements**, including two custom triggers: one for completing a warp (tracks distance, drive tier, and whether the destination was a probe scan) and one for travelling through a gate (on foot vs. as a vehicle).
- **JEI support** — every block/item gets an info page covering setup gotchas a recipe alone won't explain. Fully optional; the mod works fine without JEI installed.

### Changed

- **Block items are now drawn from the blocks themselves.** The Rift Drives, Warp Anchor, Spatial Siphon and Rift Chute had flat sprites standing in for them in the inventory; they are now rendered from the same GeckoLib model, skin and idle animation the block uses in the world, so they turn in your hand and in item frames and can no longer drift out of step with the machine. The Astrolabe's item is its panel's real model rather than a sprite of one. The nine placeholder icons are gone.

- **Destination selection moved off the Rift Drive.** The drive's screen is now a diagnostics panel — speed, stress, charge, and a checklist of what's blocking the next jump. Courses are set at an Astrolabe, and a redstone signal is now the only way to launch.
- **All five screens rebuilt** with a consistent layout, shared visual style, and animated bars/readouts.
- **Aperture visuals reworked.** Rifts now shatter open like broken glass instead of irising, with actual debris drifting through the tunnel and a light visible at the far end. Rift Gates close the same way in reverse — shards pull back in and seal shut, rather than the hole just vanishing.
- **Passengers stay aboard during a warp**, seated or not, even through long jumps.
- Machines are now detected reliably as part of the ship, instead of occasionally being missed depending on build history.
- **Gate stress cost lowered** — a 5x5 gate no longer costs about 3 crushing wheels' worth of stress; it's roughly a quarter of that per block now.
- **Recipes rebalanced across the board**, most notably the Singularity Drive, which previously required four complete Mk III drives and five Singularity Cores for one item. That's fixed.
- **Rift Probe UI reworded** to plain language — "Direction"/"Distance" instead of "Bearing"/"Range", "Scan ahead" instead of "Throw sounding", and a "Send ship here" button that now clearly shows when a course is set.
- Setting a course from the Probe now gives clear feedback — a chat message, an updated Probe panel, and a course marker on the Astrolabe's chart.

### Fixed

- **Long-distance warps could kick players off the ship and make the airship appear to vanish.** This was actually four separate bugs:
  - Very large single-tick jumps could break Sable's collision handling entirely, leaving players standing on nothing.
  - The client wasn't always told the ship had moved, so players could visually lag behind or fall through the deck.
  - A drive could silently fail mid-warp with no error logged, which made earlier attempts at fixing this look like they weren't working.
  - A stalled-warp cleanup routine could tow an already-arrived ship back to its departure point.
- **Rift Gates didn't actually work when dialled** — a passive receiving gate could block the connection from ever completing.
- **Gate crossings were sometimes missed**, especially for players, due to how movement was being tracked.
- **Rift Essence couldn't be piped out of a Rift Gate** — the fluid capability was never registered, even though the gate stored and displayed essence correctly.
- **Closing gate apertures could vanish instantly** instead of playing their closing animation, on gates that had been open a very long time.
- **Rift fire/glow effects were invisible from one side** of the aperture.
- **Map/chart colors were wrong** — grass rendered blue-green and water rendered orange due to a byte-order mixup.
- **Gate settling timers ran one tick longer than intended.**

### Removed

- **Flight Lever** — replaced by chart-based course setting and redstone-triggered launches.

---

Also included: a large expansion of the automated test suite (9 → 30 test classes, 268 tests total) covering gate handedness, aperture shatter geometry, screen layout, recipe balance, and translation coverage, to catch this kind of regression earlier next time.
