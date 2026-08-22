# Changelog

## 1.1.0

Major update. Adds Rift Gates, the Rift Probe, the Astrolabe, the Spatial Siphon, a full UI rebuild, Ponder/JEI/advancement support, and fixes a serious bug where long-distance warps could throw players off the ship or corrupt the airship's sub-level.

### Added

- **Rift Gate & Rift Gate Frame** — build a ring of frame in any size and shape, drop a controller in it, and dial another gate to open a walk-through (or drive-through) doorway between them. Works on foot, on mobs, and on Aeronautics land vehicles. Ring size is the size limit — nothing bigger than the opening can pass. Only the dialling gate needs essence and rotation; the far end just has to exist. Gates are named, owned, and access-controlled like Warp Anchors. Same-dimension only.
- **Rift Probe** — scan a direction and distance from the ship without having ever been there, and get back a top-down survey of what's out there before committing to a course. Costs Rift Essence; longer scans cost more. The wait is real world generation happening on the server, not a fake progress bar.
- **Astrolabe Cartography Table** — a 3x3 multiblock that shows a live relief map of the ship's surroundings and a chart of every reachable Warp Anchor, complete with distance, cost, and a terrain preview of each destination.
- **Spatial Siphon & Rift Essence** — bolt one to the hull and it collects Rift Essence (a real, pipeable fluid) each time you warp. Essence now fuels gates and probes.
- **Creative Rift Drive** — no recipe, no cost, no cooldown, no range limit. For testing and creative worlds.
- **Navigator's Handbook** — an in-game guide book with its own screen. Craft it from a book, a brass sheet and an amethyst shard, right-click, and it opens as a book: two pages at a time, leather boards and parchment, a ribbon per chapter along the top, arrows at the corners, and a contents page you can click. Eight chapters walk the whole mod step by step — fitting a drive, marking destinations, launching, the probe, gates, essence and cargo, and what to do when nothing happens — and each of the sixteen pages carries an animated diagram. Pages turn with an actual page turn: the leaf squashes into the spine, casts a shadow on the sheet under it, and opens out the other side.
- **Ponder scenes** for the Spatial Siphon, Warp Anchor, Astrolabe, Rift Probe, Rift Drive, and Rift Gate (two scenes), split across two Ponder chapters.
- **13 advancements**, including two custom triggers: one for completing a warp (tracks distance, drive tier, and whether the destination was a probe scan) and one for travelling through a gate (on foot vs. as a vehicle).
- **JEI support** — every block/item gets an info page covering setup gotchas a recipe alone won't explain. Fully optional; the mod works fine without JEI installed.

### Changed

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
