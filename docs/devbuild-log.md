# AeroWarptics 1.4.0 devbuild 2 - Berths, Rift Ward, Rift Wellhead, networks and gate passages

Follows `aerowarptics-1.4.0-devbuild1`. Compared against that jar, everything below is new. Version is still
1.4.0.

**This is a development build.** Most of it has been unit-tested and some of it played in a test world, but none
of it has been played for long. **Back up your world**, and please report anything odd. For bug reports, run
`/aerowarptics trace on`, reproduce the problem, then send `latest.log`.

## New

- **Berth.** A block that marks out a parking slip at a Warp Anchor, so a ship can warp straight into a station.
  - Place it within 128 blocks of an anchor you own. Click it to set the slip's size, facing and position; the
    camera lifts and the slip is drawn in the world while you do.
  - A ship warping to that anchor is given the smallest free berth it fits, turned to face the berth's way, and
    brought down the berth's lane into the slip. The berth then holds it still, level and loaded until it leaves.
  - A ship needs two clear blocks all round, so a slip takes a ship four blocks smaller than itself each way.
  - If no berth is free, big enough, open to you or clear, the warp is refused before it starts and says why.
  - Fly a ship slowly into an empty slip and it is caught and moored the same way. Crouch while it is drawn in
    to break free.
  - To leave, start the drive, press **Cast Off** on the berth's panel, or send a redstone pulse into the berth.
  - Each berth can be open to anyone, a network or only you, and ships can come in bow first or backwards.
  - A flotilla gets a berth for every ship or is refused. A fleet with Rift set to One can't be berthed.
  - A comparator reads 15 while a ship is moored, 8 while one is drawn in, 4 while one is on its way.
- **Rift Bond.** For ships built in several sections that only fly alongside each other. Put one on every
  section, tuned alike (click a placed bond with more in your hand), and a warp takes every section through in the
  same arrangement. It's not a joint and holds nothing together in normal flight. `bondReach` (256) in the server
  config sets how far apart sections can be.
- **Rift Ward.** A third old machine, placed whole like the Cradle and Plotter, built with a Relic Armillary.
  It holds a field over the ground around it, and any ship warp or Rift Plotter exit aimed into the field comes
  out at its edge instead.
  - Choose who it turns on the **Who** tab: everyone, everyone except named networks, or only named networks.
    Your own ward lets you through unless you tick **Turn me too**.
  - The pilot isn't warned. If there's no room anywhere around the edge, the warp is refused with
    "Destination is warded".
  - Probe and Plotter scans of the ground inside a field come back scrambled for anyone it would turn.
  - Turn it at 32 RPM and pipe in Rift Essence. It stores up to 32,000 mB of charge; holding the field and
    turning rifts both cost charge. Better drives are turned less far, and Rift Storms shrink every field.
  - Comparator, redstone pulse on a turn, Display Link, `/aerowarptics ward list` and `remove` for operators.
    Server config section `ward`.
- **Rift Wellhead.** Farm Rift Essence from a fissure you've found.
  - Build a hollow 3x3x3, 5x5x5 or 7x7x7 cube around the fissure: Rift Casing on edges and corners, Warp Glass
    on faces, and a **Rift Collar** in the middle of one side. Seat a Warp, Folded or Pearlescent Warp Crystal in
    the collar for the small, medium or large frame.
  - Feed blocks in at the top (cobblestone, deepslate, end stone, obsidian, amethyst) and pipe essence out of the
    other faces. Turn the collar with a shaft and run Spatial Stabilisers around it to hold the strain down.
  - Right-click for its panel. A comparator reads the strain, and a redstone pulse scrams it.
  - **If the strain reaches the top it melts down**: the fissure is destroyed and it leaves a crater 16, 28 or 48
    blocks in radius, a Rift Wound and 20 minutes of unsettled ground. `wellheadMeltdownBreaksBlocks` turns off
    the crater. `/aerowarptics wellhead preview i|ii|iii` shows the scene without touching the world.
  - The Rift Fissure is now in the creative tab for building and testing.
- **Networks.** Named groups of players who share Warp Anchors and Rift Gates.
  - Anchors and gates have a third access setting, **Network**. Type a name in the Network box to start one.
  - **Members** opens the network's panel, where the owner adds, removes, renames or disbands.
  - The Astrolabe chart and gate dial have a filter for networks.
  - Operators: `/aerowarptics network`. Server config: `allowAnchorNetworks`, `maxNetworksPerPlayer`,
    `maxNetworkMembers`.
- **Gate passages.** Going through a Rift Gate now takes about three seconds, and you see the journey. You can't
  be hurt, pushed or seen while in it.
  - Each player picks a style with the **Passage** button on any gate's dial: Kawoosh, Timey Wimey, Punch It,
    Energise, Full of Stars, Down the Pipe or Quiet.
  - Mobs, items and empty vehicles still cross instantly. `gatePassageTicks` set to 0 makes gates instant again.
- **Rift Probes can scan other dimensions.** Pick one in the box at the top right of the probe's panel. Needs
  cross-dimension warps turned on; `probeDimensions` lists which dimensions are allowed.
- **Rifts show through Warp Glass.**
- New handbook chapters for the Berth and the Rift Ward, and wellhead pages. Five new advancements. A Ponder scene
  for the Rift Wellhead.

## Changed

- **Rift Gates and Rift Plotters have owners.** Whoever places one owns it. Gates built before this have no
  owner and anyone can still edit them; plotters placed before this count as a stranger's to every Rift Ward.
- **The Rift Plotter's table has Face, Dock and Fire tabs.** You can turn the near rift to any facing and tip it,
  and set the dock helper's **Pull radius** and **Pull depth**. On the Rift tab, "Facing" and "Tilt" are now
  "Bearing" and "Elevation".
- **Relics take rotation only through the middle of each side of their bottom layer**, and pass none on. A
  Cradle or Plotter fed from a corner before will need its shaft moved.
- Collapse Cradle: motes of light drift in from every linked Depot and switched-on Stabiliser, so you can see what
  it's using (`cradleLinks` in the client config). Goggles on a nearby stabiliser say it's linked to the cradle.
- **Ships made of several parts are sized as one ship.** The rift and the room checked at each end cover every part
  a warp carries.
- The warp dry run now says what's in the way when it finds no safe arrival point.
- **The mod's sounds are half as loud.** `effectVolume` in the client config now covers all of them; set it to 2
  for the old loudness.
- A flotilla escort reads "Course too close" or "Course too far" instead of "Out of range".
- `probeEssenceBuffer` in the server config can cap how much essence a probe holds. 0, the default, keeps it
  unlimited.
- New item art for the Folded and Pearlescent Warp Crystals, Scar Heart, Stormglasses and Ship's Compass, and
  touch-ups to the Rift Core, Rift Beacon, Rift Infused Goggles and Stabiliser Ring. (Credit: RWBlackbird)

## Fixed

- Rifts, fissures, collapses, plotter and ward effects, beacons and holograms look right with a shader pack
  (Iris). The glow was missing and the dark throat washed out.
- Rift Storm rain no longer falls through airships. It splashes on the deck and sounds muffled underneath.
- A Rift Gate whose ring was broken and rebuilt keeps its name, access and network. A Private gate can be dialled
  by its owner.
- Machines aboard a ship keep turning after a cross-dimension warp.
- The Collapse Cradle's panel shows its figures in full, and the Scar Heart's animation no longer skips a frame.
- On an escort's Coupler panel, the charge bar no longer covers the *Drive Charge* label.

## Not finished yet

- A formed Rift Wellhead still looks like its casing and glass. The collar, ports, Scarred Stone and Rift Bond use
  placeholder textures.
- The Berth has no Ponder scene or advancements yet, the drive console has no Cast Off button, and the chart
  doesn't show how many berths a station has free.

## Please test

- **Berths:** warping into a station with several berths, a full station, a berth kept for a network, stern-first
  berths, berths that don't face a compass direction, a flotilla into berths, and leaving a ship moored, going
  away and coming back.
- **Rift Bonds:** ships in two or more sections through a warp, especially odd shapes and sections at angles.
- **Rift Wards:** a ward turning other players' ships and plotter shots, the three modes with networks, and
  running out of charge.
- **Rift Wellheads:** all three sizes, keeping one steady, and a meltdown. **Try a meltdown in a copy of your
  world**, and tell us if the server stutters at the detonation (`wellheadCarveBudget` lowers the load).
- **Networks** with several players: joining, leaving, removing, and what happens to anchors and gates afterwards.
- **Gate passages** in every style, on foot, mounted and in vehicles, and how they look on your graphics card.
- **Probe scans of the Nether and End**, with cross-dimension warps turned on.
- Anything that looks wrong next to a shader pack or Fabulous graphics.
- Multiplayer in general. Almost all testing so far has been one player.

Full details are in the changelog.
