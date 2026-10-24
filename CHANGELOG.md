# Changelog

## 1.4.0

### Added

- **Trains through Rift Gates.** Create trains can now run through a Rift Gate to another gate, as they do through a Nether portal.
  - Pick a gate on the dial and press **Set Route**, or shift-click **Dial**. Every train that goes in comes out of that gate. Setting a route costs nothing and doesn't open the gate.
  - Run straight track into the bottom of the gate's opening. Track comes out of the far gate, two blocks of it, for you to build on.
  - A train that reaches a shut gate waits there. The gate dials for it, paying the dial cost and needing rotation as a dial does, and shuts again once the last carriage is through. A train that finds the gate already open goes straight through.
  - Scheduled trains find a path through gates. A gate on a train's path dials as the train comes, so it is usually open when the train gets there.
  - Trains run between gates in different dimensions too. People and vehicles still can't follow them there.
  - A redstone signal on the controller holds trains back.
  - A gate shown with **Trains** in its list is in another dimension and can only be a route.
  - Riders pass through the Rift Corridor, a few seconds of track in a violet void. Anyone left standing in it is put back at the gate they came through.
  - A gate won't be hung up, or routed elsewhere, while a train is part of the way through.
  - New advancement: *Mind the Gap*.
  - Settings are under `gates.rail` in the server config.

- **Rift Sunderer.** A new single-use machine that tears a Rift Fissure open where you want one, so a Rift Wellhead can be built at home.
  - Build a ring of Rift Gate Frame as for a gate, with an opening from 3 by 3 to 7 by 7, and set the Sunderer in the ring where the controller would go.
  - Fill it with 4 buckets of Rift Essence, turn it at 128 RPM or more, and give it a redstone signal.
  - A point opens in the ring and searches for a far side. It finds none and becomes a black seed that bends the world around it. A third of the frame lifts out, orbits the seed and is swallowed block by block.
  - The seed implodes, the Sunderer is used up, and a fissure is left in the opening. Everyone can see the new fissure for 10 seconds; after that, only Rift Goggles show it.
  - Keep it turning and keep the rest of the ring whole. Stopped early, it misfires, puts the frame back and gives half the essence back. Stopped later, or broken, it ruptures and blows a crater.
  - Stand out of the opening while it works: it pulls things in, hurts what stands in it, and destroys loose items.
  - A torn fissure gives a Spatial Siphon nothing and leaves no Scar Heart. It closes after 20 minutes unless a Rift Wellhead is built round it.
  - It won't fire aboard a ship, within 48 blocks of another fissure, near a Warp Anchor, inside a Rift Ward's field or in unsettled ground.
  - Four new advancements, one of them hidden.

- **Berth.** A new block that marks out a parking slip at a Warp Anchor, so a ship can warp straight into a station.
  - Place it within 128 blocks of an anchor you own. Click it to set the slip's size, facing and position; the camera lifts and the slip is drawn in the world while you do.
  - A ship that warps to that anchor is given the smallest free berth it fits. It is turned to the berth's facing, comes out of its rift down the berth's lane, and stops in the slip.
  - The berth then holds it: kept still, kept level and kept loaded, for as long as you like.
  - If no berth is free, none is big enough, none is open to you, or one is blocked, the warp is refused before it starts and says which.
  - A ship needs two clear blocks all round, so a slip takes a ship four blocks smaller than itself each way.
  - A ship flown slowly into an empty slip by hand is caught and moored the same way. Crouch while it is being drawn in to break free.
  - To leave, start the ship's drive. **Cast Off** on the berth's panel, or a redstone pulse into the berth, also lets go.
  - Each berth can be kept for anyone, for a network, or for its owner only, and ships can come in bow first or backwards.
  - A flotilla folding to a station gets a berth for every ship or is refused. A fleet with Rift set to One cannot be berthed.
  - A comparator reads 15 while a ship is moored, 8 while one is being drawn in and 4 while one is on its way.
  - An anchor with a berth takes arrivals in its berths only. Other arrivals nearby keep out of every slip.

- **Rift Bond.** A new block for ships built in several sections that only fly alongside each other.
  - Place one on every section. Click a placed bond with more in your hand to tune them to it, then place those on the other sections.
  - A warp then takes every bonded section through the rift, in the same arrangement.
  - It is not a joint. It holds nothing together in normal flight.
  - Sections more than 256 blocks apart are left behind (`bondReach` in the server config).
- **Free Cruise.** A new Rift Drive mode, switched on the console. The ship flies where its bow points and you steer as normal.
  - Redstone strength is the throttle, so Simulated's Throttle Lever works. Cut the signal or press **Disengage** to stop.
  - Uses charge for every block flown, and stops when charge or range runs out.
  - Slows down for terrain and unloaded land ahead.
  - Top speed is 30, 45, 60 or 90 blocks a second, from Mk I to Singularity.
- **Rift Coupler.** A new drive module that lets several airships warp together as a flotilla.
  - Set one ship's coupler to Flagship and the others to Escort. The flagship holds the course.
  - From an escort, take station with a flagship nearby. The flagship's owner accepts; their own ships join straight away.
  - On the flagship's Fleet Board, drag each escort into a formation slot and press **Sound Stations**. Scroll a slot to change its height, right-click to reset it. **Stand Down** ends the muster.
  - An escort holding its slot with a full, turning drive comes into harmony after a few seconds. That's what counts as ready.
  - **Fold** sends the fleet at once, and every ship arrives in its slot. Hold Fold to leave the ships that aren't ready behind. Drift out of your slot and you arrive a few seconds late and off to one side.
  - Set **Rift** to **One** and the whole flotilla folds through a single large rift.
  - A flagship leads one escort on a Mk I, then two, four, and eight on a Singularity. Creative is unlimited.
  - Every drive needs its own power and pays its own charge, a fleet costs more than the same ships flying alone, and the flagship's coupler spends Rift Essence per escort during a fold.
  - A Rift Beacon bound to a flagship calls the whole flotilla, unless you turn that off on the Fleet Board.
  - Escorts fly the flagship's rift colours. A Display Link reads a coupler's role and roster.
- **Rift Wellhead.** A way to farm Rift Essence from a fissure you have found. Build a hollow cube of Rift Casing (edges and corners) and Warp Glass (faces) with the fissure in the exact middle, three, five or seven blocks along an edge, and set a **Rift Collar** in the middle of one side.
  - Put a Warp Crystal in the collar for the small frame, a Folded one for the medium, a Pearlescent one for the large. The goggles say what is wrong if it will not form.
  - Feed it blocks through the top (cobblestone, deepslate, end stone, obsidian, amethyst) and pipe essence out of the middle of any other face. Rougher blocks strain it more.
  - Turn the collar with a shaft and switch on Spatial Stabilisers round it to hold the strain down. A well under more strain makes essence faster.
  - A comparator reads the strain; 15 means critical. A redstone pulse scrams it: what it was digesting is lost and the hatch shuts for a while.
  - Right-click a formed wellhead for its panel: the strain, where it is heading, what the rotation and the stabilisers are giving, and buttons to scram it or shut the hatch.
  - At critical it sounds an alarm. Let the strain reach the top and it melts down, over about 45 seconds. The frame blows out. The tear swells into a black ball with a funnel climbing into the sky, strips the ground towards it and pulls in everything loose, to a quickening heartbeat. Then it goes silent, and detonates. The fissure is destroyed, with no Scar Heart, and it takes a crater 16, 28 or 48 blocks in radius.
  - What is left is a bowl lined in Scarred Stone with pieces of ground hanging over it, and a **Rift Wound** where the fissure was. The wound does nothing and cannot be broken; it marks the place for good. It is a black, twitching crack that bleeds red, under a thread of dark from the sky.
  - The ground around the crater stays unsettled for 20 minutes. It hurts and hops whatever stands in it, less as it fades, and no wellhead can be built there. Rift Goggles show its edge.
  - `wellheadMeltdownBreaksBlocks` turns the crater off. Land-protection mods that block explosions block it too.
  - A Display Link reads its state, strain, heading, essence and gullet. JEI lists what it digests. There is a Ponder scene, two handbook pages and three advancements.
  - Operators can watch the scene with nothing in the world changed using `/aerowarptics wellhead preview i`, `ii` or `iii`. `/aerowarptics wellhead meltdown` sends the nearest wellhead over for real, and `/aerowarptics wellhead strain` sets its strain.
  - The Rift Fissure is now in the creative tab, so one can be set down for a build or a test. It cannot be crafted or picked up, and still needs Rift Infused Goggles to see.
  - `wellheadCarveBudget` limits how many blocks a meltdown takes each tick. Lower it if the server stutters at the detonation.
  - A fissure in a wellhead no longer works for rifting, and siphons take nothing from it.
- **Rifts show through Warp Glass.** A rift or fissure behind Warp Glass is no longer hidden by it. Warp Glass seen through water or stained glass is not drawn.
- **Spatial Wave Detector.** A new redstone block that pulses when something warps in nearby, either a ship or anything out of a Rift Gate. The closer the arrival, the stronger the signal. Scroll the box on any side to set the range; `detectorMaxRange` caps it. Works aboard a ship.
- **Spatial Stabiliser.** A new drive module. Give it a redstone signal and it smooths out sudden climbs and turns. A second one on the same ship does nothing.
- **Cooling Vent.** A new drive module. Place it against a Rift Drive and turn it with a shaft, and the drive cools down in half the time after a warp. It blows steam when the ship arrives.
  - Vents stack. Each one halves what the last one left, up to four: a half, a quarter, an eighth, a sixteenth.
  - The louvres face away from what you place it on and the shaft goes in at the back. Placed straight onto a turning drive, the drive turns it. Use a wrench to turn it round and give it its own shaft.
  - Needs 32 RPM and uses stress like any Create machine.
  - **Ice works too.** Any kind of ice against the drive takes a tenth off the cooldown, with no shaft and no stress. It isn't used up.
  - Four coolers count on one drive, vents first. Extra ones do nothing, and the goggles tell you which.
  - What counts is whatever is cooling the drive when the ship arrives.
- **Drive Extenders.** Build a complete cube (2x2x2 to 7x7x7) around a Rift Drive to make it bigger. Any face of the cube takes shafts and modules, and any block opens the console. Range doesn't change.
- **Warp Crystals.** Mix two amethyst and an ender pearl into **Warp Dust** (heated).
  - Haunt dust into a **Warp Shard**, then press the shard into a **Warp Crystal**.
- **A new way to build drives.** Each tier now asks you to do something new, not just gather more.
  - **Mk I** is built from the workshop, as before, with Rift Casing in place of Brass Casing.
  - **Mk II** needs Opened Rift Lenses, which you make by rifting.
  - **Mk III** needs Folded Warp Crystal, which you fold at the Collapse Cradle.
  - **Singularity** needs a Singularity Core from the Collapse Cradle, and Pearlescent Warp Crystal, also folded there.
  - Gate controllers, Rift Chutes, Rift Probes and Rift Couplers also need Opened Rift Lenses now.
  - Turn on the **Classic Recipes** data pack when you create a world (or with `/datapack enable`) to keep the old recipes.
- **Rifting.** A new Encased Fan process. Blow items through a standing rift: a Rift Fissure, a lit Rift Brazier, or an open Rift Gate's pane.
  - Rift Lens becomes an **Opened Rift Lens**, Glass becomes **Warp Glass**, and Warp Dust becomes a Warp Shard.
  - Creatures caught in the stream take a short hop, like eating chorus fruit. Not on ships. `riftingHops` turns this off.
- **Rift Brazier.** Give it a redstone signal and Rift Essence, and it burns with rift fire: violet flames with dark hearts, drawn like a rift. Burns 2 mB a second while lit. It looks like a Blaze Burner the rift has got into, and its head turns to watch you.
- **Collapse Cradle.** Makes the Singularity Core from a Rift Core, a Scar Heart, offerings on Create Depots around it, and 4 buckets of Rift Essence.
  - It's an old machine, rebuilt: three blocks across and three high, placed in one go and centred where you aim. It needs clear room, can't go on a ship, and can't be moved. Breaking any part of it picks up the whole machine.
  - Building one takes a **Relic Armillary**, which can't be crafted. Look in the barrels in rift scar ruins.
  - Right-click it to open its panel: its arms, cup and tray, the essence tank, the shaft's speed, the stabilisers around it, a dial for how steady a collapse is heading, and a **Begin** button that says what it will make and cost, or why it won't start.
  - Turn it at a Singularity's speed (128 RPM minimum, 256 best) and press Begin, or give it a redstone pulse at any side. A shaft turns it through the middle of any side of its bottom layer or from under its middle, and no rotation passes through the machine; pipes and funnels connect to any block of that layer. It takes about a minute.
  - Everything on it moves. Gears and pistons work with the shaft. Four arms lift the Rift Core up to the rift. The iris in the crown opens to let the column of light out and slams shut at the end. Three rings tumble round the black hole and lock flat in the last seconds.
  - Switched-on Spatial Stabilisers around it keep it steady, but only if they're spread evenly. Storms make it faster and less steady.
  - It links to eight Depots and keeps to them. The first eight you place are the ones it uses; a ninth gets nothing until you break one of those.
  - You can see what it's using. Motes of light drift slowly into the machine from every linked Depot and every switched-on Stabiliser around it. A depot too far away, a ninth depot, or a stabiliser that's switched off has none. They fade while a collapse runs. `cradleLinks` in the client config turns them off.
  - Goggles on a stabiliser near a cradle say it's linked to it, or steadying a collapse, instead of complaining that it isn't beside a Rift Drive.
  - Steady, the core comes back down into the arms. Less steady, it's flung a few blocks away and glows. Very unsteady, it throws back the Rift Core and every offering, and only the heart and essence are used up.
  - It's a show you can see from a long way off. The offerings lift off their depots and orbit a rift, which folds into a black hole with a ring of light turning round it and space bending past its edge. A column of light climbs into the sky and the day goes dark around it. Lightning reaches for the stabilisers and the Scar Heart beats faster. In the last seconds it goes quiet and everything falls into a point, then there's a flash, a boom heard over 100 blocks away, and a shockwave along the ground.
  - You can see how steady it is while it runs: an unsteady ritual wobbles, flickers, tips towards the side without stabilisers and throws lightning at the ground.
  - The machine looks like it's failing when a collapse is heading for trouble. It shakes, the rings rock, the gears slip, the pistons misfire, the arms tremble and the iris rattles. Sparks fly, smoke vents from the base, and the carvings flicker and turn red.
  - Stand back. As it winds it slowly pulls in loose items, mobs and players (never faster than you can walk away), and the collapse knocks everything nearby back. A flung or spat collapse also hurts anyone too close, less behind cover. It never breaks blocks or destroys items. `collapsePullRange`, `collapseBlastRange` and `collapseBlastDamage` in the server config change or turn off each of these.
  - It won't start aboard a ship, and it tells you why on its panel and the goggles.
- **Folding.** The Collapse Cradle also folds items, for Rift Essence. Put up to 16 in its arms, fill the tank, turn it at 64 RPM or faster and press Begin. It takes a few seconds.
  - Warp Crystal becomes **Folded Warp Crystal**, 100 mB each.
  - Folded Warp Crystal and a Shulker Shell in the cup become **Pearlescent Warp Crystal**, 250 mB each.
  - A Compass becomes a **Ship's Compass**, 50 mB. Sneak and use it on a Rift Drive to point it at that ship.
  - `collapseFoldMinimumSpeed` in the server config sets the speed.
- **Rift Plotter.** Tears a rift beside itself and opens the other end anywhere in the same world, around a target you choose. Whatever goes through the near rift comes out of the far one. Use it to ship things or as a weapon.
  - Another old machine, three blocks across and three high, placed in one go like the Collapse Cradle. It can't go on a ship or be moved. It's a field generator: a dish on a turntable faces the rift, with an armillary ball spinning at its focus. Building one takes a Relic Armillary and Folded Warp Crystals.
  - Right-click it to open its table. Type the target's X and Z and press **Survey**: the ground around it (5 chunks out) loads and rises as a 3D model you can turn and zoom. Click the model to move the target onto a block.
  - On the **Exit** tab, set how far from the target the far rift opens, on which bearing, and how high. It faces the target unless you untick that and aim it yourself, including diving at it. Click the model or drag the ring to move it. **Exit speed** sets how fast things come out: their own speed, a set speed, or stopped dead for deliveries.
  - The **Entry** tab folds the table into a sidebar and flies the camera out beside the machine. A hologram of the near rift stands where it will open and moves as you change it. The sidebar's **Rift** tab sets where it is: which way from the machine (including straight up or down), how far out, and how wide. The dish turns to point at it. Drag to look around, scroll to zoom.
  - On the **Face** tab, choose which way the near rift faces. It faces straight away from the machine unless you untick that, and then you can turn it to any compass facing and tip it up or down without moving it. The dock is behind the way it faces, so it turns too.
  - On the **Dock** and **Fire** tabs, choose how it fires. Mix these however you like:
    - **Dock helper**: a ship that slows down behind the entry is gently lined up, levelled and turned end-on, then launched straight through once the rift is open. Crouch aboard to break free. While it's on, an outline in the air shows where to wait: the space a ship is caught in, the spot it's held at, and arrows pointing through. It's drawn thick enough to see at midday, and faintly through terrain. It's amber while waiting, brighter while holding a ship, and teal once the ship is lined up. `plotterDockGuide` in the client config turns it off.
    - **Pull radius** and **Pull depth**: how far out from the rift and how far back from it the dock helper catches ships. Left on Auto, the dock is sized to suit the rift and is 32 blocks deep. Set them yourself for a narrow dock that leaves passing ships alone, or a wide one that's easy to fly into. The outline shows the size you set. `plotterMaxDockRadius` (48) and `plotterMaxDockDepth` (96) in the server config limit them, and `plotterDockReach` sets the Auto depth.
    - **Fire when a ship docks**: a lined-up ship sets it off by itself after a short wait. The rift closes again behind it.
    - **Fire on redstone**: a pulse fires it. With a lane, the lane stays open while the signal is on.
    - **Hold open as a lane**: once open, the rifts stay open, paying essence every second (1% of the opening cost), until you stand it down or it runs dry.
    - **Open for**: how long a normal strike stands open, up to 60 seconds.
  - Press **Engage** on the table or the sidebar, or use any of the triggers above. Turn it at 64 RPM or faster: a shaft works into the middle of any side of its bottom layer or from under its middle, and no rotation passes through the machine; pipes connect to any block of that layer.
  - The exit can be as close to the plotter as you like, so it works across a base as well as across the world.
  - There's no range limit. It costs Rift Essence: 500 mB, plus 250 mB per 1,000 blocks to the exit, plus 40 mB per block of radius. It draws the essence as it charges, faster the faster it turns, so pipe more in for a long shot.
  - Airships go through with their crew and anything docked to them. Players, mobs, arrows, TNT and cannon shells go through too (`plotterCarriesEntities` turns that off). Ships wider than the rift are turned back.
  - A ship that comes out stays loaded for 30 seconds, so an empty one keeps flying at the target.
  - The far rift stays shut until something is actually on its way through the entry. It tears open a moment before a ship, player or shot arrives, and heals a few seconds after the last one, while the entry stays open. A lane waiting for freight shows nothing at the far end.
  - Engaging is a show: a beam gathers from the armillary ball to where the rift will tear, a star of light swells at its end with rings closing in and arcs jumping to it, and a hum climbs with the charge. Cracks spread across the disc while it waits on the far end, then the star bursts with a flash, a boom and a shockwave, and tethers of light hold the rim while it stands open.
  - If the far end is buried in the ground or never loads, it refuses and keeps the essence it has drawn for next time.
  - Everything is in the server config's `plotter` section, including `plotterEnabled` to turn plotters off.
- **Scar Hearts.** An emptied Rift Fissure leaves a Scar Heart behind when it seals.
- **Rift scar caches.** Every rift scar ruin now has a barrel of old supplies, and some hold a Relic Armillary.
- **Scar Chart.** Crafted from a Map, a Rift Lens and an Amethyst Shard. Use it to turn it into a map to the nearest rift scar nobody has charted.
- **Rift Casing.** Put Warp Dust on a Brass Casing, by hand or with a Deployer. The mod's machines are now built with it.
- **Rift Pearl.** Fill an Ender Pearl with 250 mB of Rift Essence using a Spout. An Item Drain gets 200 mB back. Throw it at a mob or player to teleport them a short way at random, like eating a Chorus Fruit (`riftPearlHops` in the server config turns this off). The Rift Beacon now uses one.
- **Rift rain.** A Spatial Siphon under open sky collects Rift Essence during a Rift Storm, 1 mB a second (3 above the clouds). Siphons in the same chunk, or on the same ship, share it.
- **Stormglass.** Fill a Glass Bottle with 500 mB of Rift Essence using a Spout, then leave it out in a Rift Storm for 30 seconds to set.
  - It shows whether a storm is coming: clear, gathering (within a day), raging, or still (storms are off).
  - Use it to break it and start a Rift Storm. It won't work during a storm, with the weather frozen, within a day of the last storm, or if natural storms are off. `riftStorm.callable` turns this off.
- New advancements: *Seen a Rift*, *Crossed the Line*, *Heart of a Scar*, *It Declines to Be Precise*, *Stormcaller* and *Over the Horizon*.
- **Rift Ward.** A third old machine. It holds a field over the ground around it, and a rift aimed into that field comes out at the field's edge instead.
  - Three blocks across and three high, placed in one go like the Collapse Cradle and the Rift Plotter. It can't go on a ship or be moved. It's a cracked stone totem standing in a roofless machine, clamped and chained in brass. Building one needs a Relic Armillary.
  - It turns aside **ship warps** (anchors, probe fixes, beacon summons and whole flotillas) and **Rift Plotter exits**. Rift Gates and ships flying in under their own power are not affected.
  - Right-click it for its panel. The camera lifts out over the ward so you can see the whole field, and the edge of the field is drawn on the ground as you move the radius slider. Drag to look around, scroll to zoom.
  - On the **Who** tab, choose whom it turns:
    - **Turns everyone**: nobody is let through.
    - **Lets networks through**: everyone is turned except members of the networks you name.
    - **Turns networks only**: only members of the networks you name are turned.
    - Type a network's name and press **Add**, up to eight. It can be any network on the server, not only yours.
    - Your own ward lets you through in every mode. Tick **Turn me too** to be turned like anyone else, which is how to test it on your own.
  - The pilot is not warned. The chart and console show nothing; the ship comes out at the edge. If there is no room anywhere around the edge, the warp is refused before it starts with "Destination is warded".
  - When it turns a ship aside, everyone nearby sees the rift caught over the base and dragged across the sky to the edge, the day dims, and the ward tolls. The crew feel the corridor wrench sideways.
  - A **Rift Probe** scan, or Rift Plotter table, shows interference in place of the ground inside the field to anyone the ward would turn.
  - You can tell it's working: while the field is up, the totem's pieces hang apart, turn and glow, the machine hums, and it lights the ground. Switched off it is shut, still and dark. Out of charge it hangs open but still, flickering.
  - Around a working ward, motes drift upward, dust creeps in towards it, and the air over it darkens.
  - Turn it at 32 RPM or more through the middle of any side of its bottom layer or from under its middle, and pipe in Rift Essence. It stores up to 32,000 mB as charge and runs on that, even while nobody is nearby.
  - Holding the field costs charge all the time, more for a wider field (16 to 96 blocks by default). Each rift it turns costs more, and a heavier ship costs more still. Out of charge, the field drops and stays down until it's a quarter full again.
  - A ward that can't afford to turn a rift lets it through and drops.
  - A **Rift Storm** shrinks every field to 60%. Better drives are turned less far out: a Singularity lands where it aimed anywhere in the outer half of a field.
  - A comparator reads its charge, it gives a redstone pulse when it turns something aside, and a Display Link can read it.
  - Operators: `/aerowarptics ward list` shows every ward, and `/aerowarptics ward remove <id>` takes one down.
  - Everything is in the server config under `ward`, including switching wards off.
- Rift Plotters now remember who placed them. A plotter placed before this update counts as a stranger's to every Rift Ward.
- A new *Rift Ward* chapter in the Navigator's Handbook, and two new advancements.
- A new *Rift Craft* chapter in the Navigator's Handbook, and Ponder scenes for the Rift Brazier and the Collapse Cradle, collapsing and folding.
- A new *Rift Plotter* chapter in the Navigator's Handbook.
- Display Links can read the Rift Brazier and Collapse Cradle.
- **Ten new rift themes** for the Rift Modulator, 15 in total. Shift-click cycles backwards.
  - **Starblocks** - a hyperspace tunnel.
  - **Bedrock** - a black singularity with a gold halo.
  - **Boldly Gone** - a warp bubble with a blue flash.
  - **Ludicrous Speed** - hyperspace that turns into tartan.
  - **Eventful Horizon** - a red wormhole with spinning rings.
  - **Vworp** - a time vortex with a tumbling police box.
  - **Improbability** - changes into something new every few ticks.
  - **Ender** - the End's starfield, with enderman eyes that stare back.
  - **Sketch** - a cartoon rift, drawn by a pencil and scribbled out afterwards.
  - **Mostly Dangerous** - a black storm cloud that bends the view around it. `riftLensing` in the client config turns the bending off, and it switches itself off with Fabulous graphics or a shader pack.

  All but Sketch use their own colours, so the swatch grid greys out for them. Intensity still works on all of them. Themes only change the look.
- The theme buttons have new animated icons.
- **Clearance checks switch.** A new console button. When it's off, the drive skips the launch and landing checks and arrives exactly on target. It's saved per drive and shows in gold on the console and goggles.
- **Arrival height.** Warp Anchors and Rift Probes have an arrival-height slider. Scroll over it to move one block at a time. The default is still 6 blocks. If something is in the way, the ship arrives higher, never lower.
- **Hold altitude.** A new checkbox on the Astrolabe chart. The ship arrives at the height it's flying at instead of the anchor's height. It's ignored on cross-dimension warps.
- **Rift Storms.** Rare violet storms with purple rain and rift lightning. Tears open in the sky, a vortex turns overhead, shards streak across it and the clouds flicker.
  - During one, drive cooldowns are halved, but every drive is as unstable as a Singularity. Creative drives are unaffected.
  - Rift Drives grow patches of void while a storm lasts, so you can see which ones it's changing.
  - The rain is vanilla's rain, tinted purple. A resource pack that changes the rain changes it too.
  - Storms don't happen in the Nether or the End.
  - `/weather rift_storm [duration]` starts one, and `riftStormWeather` in the client config turns the visuals off.
- **Things work when nobody is there.** The mod loads what it needs while something is in use, and lets go afterwards.
  - The Astrolabe shows a map of an anchor nobody is near.
  - A Rift Beacon can call a ship that isn't loaded. It says *"Reaching for..."* until the ship answers.
  - A Rift Chute keeps its far end loaded while items are going into it.
  - A Rift Gate keeps both ends loaded while it's connected, even when an end is aboard a ship.
  - `/aerowarptics holds` lists what's being held. Operators can release a ship, or use `holds reach` to bring back a ship lost before this update.
  - New `loading` settings: `astrolabeLoadsPreviews`, `beaconReachesShips`, `summonReachTimeoutTicks`, `chutesHoldPartners` and `residencyRoamLimit`.
- **Ships stay loaded.** A ship with a Rift Drive keeps itself loaded, even after a restart. Turn it off with `keepAirshipsLoaded`.
- **Cross-dimension warps (experimental, off by default).** Needs [Dimensional Sable](https://modrinth.com/mod/dimensional-sable) on the server and `permissions.allowCrossDimensionWarp` turned on. Back up your world before trying it.
  - Drives can warp to anchors in the Nether, the End and other dimensions.
  - Propellers and other bearing contraptions are rebuilt on arrival. Pistons, pulleys and gantries arrive as blocks.
  - Ships too tall for the destination are refused.
  - A crossing costs the same as a 1000-block jump.
  - **Rift Probes can scan other dimensions.** Click the box at the top right of the probe's panel and pick one. The scan starts from where a Nether portal under your ship would come out, and the distance is measured in the dimension you're scanning.
    - Changing the dimension clears the last scan.
    - A scan of the Nether shows the bedrock roof, and the ship arrives above it.
    - `probeDimensions` in the server config lists which dimensions probes can scan. It's the Overworld, Nether and End by default. Add a mod's dimension by its id, or `"*"` for all of them.
    - Without cross-dimension warps turned on, the box just shows the dimension the probe is in.
  - Gates still only work in their own dimension.
- **Debug commands for operators.**
  - `/aerowarptics warp dryrun` runs the pre-flight checks without moving the ship, including power, charge and cost.
  - `/aerowarptics warp clearance` shows the launch path: amber for the hull, teal for the rift's reach, red for anything in the way.
  - `/aerowarptics trace on` logs why the mod does what it does, for bug reports: reproduce the problem, then send `latest.log`. Every refusal is logged with its numbers, including silent ones. It replaces `debug.traceWarps`, now `debug.trace`, and covers the whole mod.
- **Comparators read Rift Drives.** The signal says what the drive is doing:
  - 0 when it isn't turning, 1 while it cools down or clears a fault.
  - 2 to 13 as it charges, in twelve even steps. 14 when it's fully charged.
  - 15 from the moment a course is locked until the ship has arrived.
  - It works against any block of an extended drive.
- **Ship Range.** The drive console now shows how far the drive can throw the ship it's on, under the drive's own Max Range. A heavy ship reaches less far than its drive could, and this is the number anchors are judged against. The Astrolabe chart's header shows both as well.
- **Spin-up sound.** A drive winding up to open a rift now sounds like it: a hum that rises as the spin builds, and climbs slowly if the shaft is slow.
- **Warp sounds and the warp's field-of-view stretch can be turned off**, with `warpSounds` and `warpFov` in the client config. A few other machines borrow the warp sounds and go quiet with them.
- **Warps can take longer the further they go.** Off by default. Set `warpTimeScaling` in the server config's `flight` section to `LINEAR`, `SQUARE_ROOT` or `LOGARITHMIC`; `warpTimeMultiplier` sets how much longer, and `warpTimeLimitTicks` caps it.
- **Networks.** A network is a named group of players who share Warp Anchors and Rift Gates.
  - Anchors and gates have a third access setting, **Network**, between Public and Private. Only players in the network can travel there.
  - Type a name in an anchor's or gate's **Network** box to start a network. You own it. Type the name of a network you're in to put the machine on it.
  - **Members** opens the network's panel. The owner adds players by name (they must be online), removes them, renames the network or disbands it.
  - Any member can put their own anchors and gates on the network. If you leave or are removed, yours come off it, and any that were set to Network become Private.
  - The Astrolabe chart and the gate dial list have a filter above the list. Click it to step through networks, or scroll over it. Each row shows its network, and so does the chart's detail panel.
  - On servers with more than 256 anchors or gates, filtering to a network shows that network's destinations even if they aren't among the nearest 256.
  - Operators can use `/aerowarptics network` to list, create, rename and disband networks, add or remove players (including offline ones), and change a network's owner.
  - `allowAnchorNetworks`, `maxNetworksPerPlayer` (16) and `maxNetworkMembers` (64) are in the server config.
- **Gate passages.** Going through a Rift Gate now takes about three seconds, and you see the journey.
  - You are moved to the far gate straight away, so it loads while you travel. Until you arrive you can't be hurt, targeted, pushed or seen, and you can't move.
  - It works on foot, on a mount, and aboard a vehicle. A vehicle with a crew waits at the far gate until they arrive, then drives off at the speed it went in.
  - Mobs, items and empty vehicles still cross instantly.
  - Each player chooses how it looks. Press the **Passage** button on any gate's dial panel to step through the styles; shift-click steps back. Hover the button to read what the chosen style looks like. It's your own setting and works on gates you don't own.
  - **Kawoosh** (the default): a twisting blue and violet tunnel that ends in a flash of light.
  - **Timey Wimey**: a spiral of storm cloud that shifts between red and blue, with lightning.
  - **Punch It**: the stars stretch into lines, then a blue-white tunnel.
  - **Energise**: a column of light and rising gold and blue sparkles. You fade in at the far side a speck at a time.
  - **Full of Stars**: two planes of streaming colour either side of a bright line.
  - **Down the Pipe**: a green pipe in big square pixels, with an iris closing and opening.
  - **Quiet**: a plain fade with no motion.
  - `gatePassageTicks` in the server config's `gates` section sets the length (60). Set it to 0 to make gates instant again.
  - The void and `/kill` still work on a player in a passage.
- **Three new Ponder scenes:** Drive Extenders, Spatial Stabiliser and Cooling Vent. Rift Chutes, Drive Extenders, Stabilisers and the warp crystal line are now listed in the **Warp Travel** chapter.
- **Rift Probe essence buffer.** `probeEssenceBuffer` in the server config's `probe` section sets how much Rift Essence a probe holds. It is 0 by default, which leaves it unlimited as before. A probe always has room to pay for its longest scan.
- **Rift Goggles in a Curios slot.** With [Curios](https://www.curseforge.com/minecraft/mc-mods/curios) installed, Rift Infused Goggles can be worn in the head slot there, leaving the helmet slot free. They show fissures and read machines there just as they do on the head. Curios is optional.

### Changed

- **New art.** The Rift Drive, Warp Anchor, Rift Chute and Rift Brazier have new models and textures, and Rift Essence, its bucket, the Rift Casing, the Scar Chart, the Singularity Core and the Folded Warp Crystal have new textures.
  - The drive is a brass frame round a stone housing, with a ring turning through it and the rift spinning in the middle. The needle on its lid still points where the drive was told, and the rift still grows as the drive charges.
  - The anchor's lantern shows its state: it blinks while idle, flashes while locked or warping, and goes dark while cooling down. The light is in the anchor's status colour.
  - The brazier is a basin of essence in an open cage, and its fire burns up out of it. It no longer shows how much essence it holds; the Display Link and the tooltip still do.
- **More new art.** Every other machine has new models and textures too: the Astrolabe, Rift Coupler, Rift Modulator, Spatial Siphon, Rift Probe, Spatial Stabiliser, Berth, Cooling Vent, Drive Extender, Rift Bond, Spatial Wave Detector, the Collapse Cradle, Rift Plotter and Rift Ward, the gate's frame, the Rift Collar and Wellhead port, Scarred Stone, Warp Glass, the Rift Portal and the rift particles, along with the last crafting icons.
  - An Astrolabe table looks right at every size. It used to be one table squeezed down.
  - The Rift Probe and Spatial Stabiliser are shaped blocks now, and their outlines follow the new shapes.
  - The Collapse Cradle has no arms, iris or gears any more. The Rift Core hangs over the Scar Heart and rises into the rings on its own.
  - The Rift Plotter's rings spin about the beam as it charges, and the ball at their middle tumbles.
  - The Rift Ward's two rings turn round the totem while its field stands.
- **Connected textures.** Walls of Rift Casing, Warp Glass or Rift Gate frame, and a formed Wellhead's casing, join up into one surface. No other mod is needed.
- **Ships made of several parts are sized as one ship.** The rift, the run through it and the room checked at both ends now cover every part a warp carries, not only the part with the drive.
- The warp dry run now says what is in the way when it finds no safe arrival point: a block, or another ship.
- **The mod's sounds are half as loud.** Drives, rifts, gates, flotillas, the Collapse Cradle, the Rift Plotter and the Rift Ward were all at or near full volume, and several play at once. They carry as far as before.
  - `effectVolume` in the client config now turns all of them up or down, including the ones a drive or a machine plays for everybody nearby. It used to reach only a few. Set it to 2 for the old loudness.
- **Rift Gates have owners.** Whoever places the controller owns the gate, and only they (or an operator) can rename it or change its access. Gates built before this update have no owner and anyone can still edit them.
- The Warp Anchor's Network box now does something. Text already typed in it becomes a network owned by the anchor's owner.
- **Siphons share one catch per warp.** More Spatial Siphons on a ship give you more room, not more essence.
- **Rift Fissures can't be moved.** A ship built around one leaves it behind.
- The Rift Core is now made on a sequenced assembly line (Deployers and a Press), with the same materials as before.
- Pearlescent Warp Crystal and the Singularity Core now come from the Collapse Cradle.
- The Spatial Stabiliser uses a Gyroscopic Mechanism instead of Electron Tubes.
- The standard rift now cracks like glass before it breaks open. Rift Chutes and Fissures open the same way.
- **Rift Drives need much less power** and all run on plain cogs.

  | Tier | Min / full RPM | Stress at full RPM |
  | --- | --- | --- |
  | Mk I | 32 / 64 | 512 su |
  | Mk II | 64 / 128 | 1,536 su |
  | Mk III | 128 / 256 | 3,072 su |
  | Singularity | 128 / 256 | 6,144 su |

  Existing configs keep the old values. To use the new ones, update `minimumRpm`, `optimalRpm` and `stressImpact` in `aerowarptics-server.toml`, or delete the file.
- **Clearance checks are much less fussy.** The launch check now only looks at the path in front of the bow, and no longer counts propellers, terrain beside the ship or the ground it's parked on - so big ships aren't refused with nothing in the way. The overlay and `dryrun` show what's actually checked. `requireClearLaunch` brings back the old full-corridor check.
- The Rift Drive console fits on every screen size and GUI scale, with no cut-off text, and has a second row of buttons.
- Rift Drives take a shaft on any face. On existing ships, shafts already pointing at a drive's side will now connect.
- Mk III and Singularity drives can be crafted again. They use an End Crystal and an Echo Shard. Recipes that used levitite now use Warp Crystal.
- The ship holds still inside the rift during a warp. `corridorDrift` brings back the old behaviour.
- If a launch or landing check can't finish, the message says so instead of reporting a blockage.
- Rift size no longer depends on which way the ship faces. Long ships facing east or west get narrower rifts.
- The destination loads while the drive spins up. Goggles show *"Waiting for the landing to load"*, and the drive stops waiting after 30 seconds. `maxArrivalChunks` caps how much it loads.
- `keepAirshipsLoaded` holds the ship itself rather than the ground under it, however big the ship is. An unmanned ship that drifts more than `residencyRoamLimit` blocks from where it was left is let go until it stops.
- Docked ships, turrets on Swivel Bearings and other attached parts now go through a warp or a Rift Gate with the ship, instead of being left behind. The warp costs their mass too.
- New item art for the Navigator's Handbook, Rift Beacon, Rift Core, Singularity Core, Rift Lens, Stabiliser Ring, Rift Infused Goggles and Bucket of Rift Essence. The two cores are animated, and the Rift Beacon is a 3D model in the hand. (Credit: RWBlackbird)

### Fixed

- A Rift Gate whose ring was broken and rebuilt no longer comes back unnamed and public. It keeps its name, access and network.
- A Private Rift Gate can be dialled by its owner. Before, only operators could dial one.
- The server no longer freezes when a Rift Drive is put on a ship, or for up to a minute during a long warp. Worlds with a drive in them now finish saving on exit.
- Worlds load again with Sable 2.0.5, the version Create Aeronautics 1.3.2 ships with. They crashed as they loaded.
- Mobs on seats aboard a ship no longer disappear, or lose health, when the world is reloaded. It happened most to ships a Rift Drive keeps loaded. Mobs already lost come back to their seats. The fault is in Sable, which puts them back in the wrong place; this works around it.
- Ships no longer vanish: on arrival, when warped or summoned into ground nobody is near, or at the moment of a warp or a Rift Gate crossing.
- This mod's machines no longer turn invisible on a ship after a warp, and Astrolabe tables no longer turn invisible when a ship is assembled. Broken tables fix themselves when loaded.
- Ships over about 200 blocks long can land again, and no longer end their warps as "interrupted".
- Ships with attached parts, like tenders, gondolas or turrets, can warp again.
- A turret or other part on an assembled Swivel Bearing is no longer mistaken for another ship in the way of its own ship's launch.
- A ship that's tied down can no longer warp, cruise or go through a Rift Gate. Before, it stayed put, was teleported anyway, and was dragged back once the tie let go. The message says what's holding it and where, and the drive console shows *Ship is tied down*.
  - Ties that count: a locked Docking Connector, a rope, a Sticker, a Swivel Bearing on the ground, a Physics Staff lock, springs, plungers, Merging Glue that's still merging, and Sable physics being paused.
  - A rope only counts if its other end is on the ground. A rope between two parts of the ship, or down to a loose block hanging from it, goes through the warp with the ship, and whatever is on the end comes too. A ship with a rope on it can't cross into another dimension.
- Ships no longer appear to fly across the sky during a jump, and a jump stops if the ship would be thrown at an extreme speed.
- Ships come out of a warp at the same angle they went in, instead of tilted. Turn this off with `holdAttitude`.
- Players are no longer thrown off a ship that warps, including when jumping or falling on deck, or when the ship stops.
- The crew now see the far rift on every warp, not just short ones. It waits for a ship still coming out of it on a lagging server, and on a cross-dimension warp it opens in the right dimension.
- Rift Drives aboard a held ship keep working in a dimension with no players in it.
- A Rift Gate aboard a ship that has flown out of range can be dialled again. The first dial says the ship is being brought in; dial again a moment later.
- Taking apart a hull built around a Rift Fissure no longer crashes the server.
- Creative Rift Drives no longer run out of charge. Their warps and cruises cost nothing, and their range, charge and cruise distance show as Infinite.
- Rift Storm rain and aurora are no longer hidden by clouds, show through windows, and no longer disappear in front of hills and ships with Fabulous graphics.
- Rift Storm rain no longer falls through airships and other vehicles. It stops and splashes on the deck, as normal rain does, and sounds muffled under one.
- A Rift Beacon now tells you when the drive refuses a summon, such as *Destination too close*. Before, it did nothing and said nothing.
- A Rift Beacon aimed at a ship's deck now summons to that spot. Before, it was refused as too far away.
- Rifts look right with a shader pack (Iris). Before, the glow was missing and the dark throat was washed out. Fissures, collapses, meltdowns, plotter and ward effects, beacons and holograms are drawn the same way.
  - A shader pack's own bloom, fog and clouds don't affect them.
- Machines aboard a ship keep turning after a cross-dimension warp. Before, some stopped, including the Rift Drive, which then refused to launch.
- The Collapse Cradle's panel shows its stabiliser count, essence and speed in full. They were cut off.
- The Rift Drive console's **Close** button works on an idle drive. It was greyed out.
- On an escort's Rift Coupler panel, the charge bar no longer covers the *Drive Charge* label.
- The Scar Heart's animation no longer skips a frame.

## 1.3.0

### Added

- **Rift Modulator.** Place it next to a Rift Drive to style its rift. Left-click a swatch for the core colour and right-click for the rim. Set the intensity from 25% to 200% and pick a theme. It uses a little Rift Essence, only while warping.
- **Five rift themes:** Standard, Clockwork, Arcane, Ember and Starlight. They all use your chosen colours.
- Every machine now works with Create's Display Link. A Modulator shows its drive's info.
- New Ponder scenes and advancements, including **House Colours**. Press **[W]** over the Handbook to open its Ponder chapters.
- Rifts throw lightning. `riftLightning` turns it off.
- Theme buttons show a particle preview.

### Changed

- A gate's opening is now a real block, the **Rift Portal**. It gives off light and fills any ring shape.
- Portals are see-through and fade in and out.
- An open gate uses essence over time, and only the gate that dialled pays. Set `gateUpkeepCost` and `gateUpkeepCostPerBlock` to 0 to make gates free again.
- Rift Drives have a new tesseract model.
- The Singularity's `instability` setting now actually changes where a warp comes out.

### Fixed

- Gates now work on ships.
- You can now leave a ship through a gate, not just board one.
- Gates no longer disconnect when nobody is using them.
- Fixed a Java compatibility issue in noise generation.
- Warps succeed more reliably.
- Siphon tanks are now see-through.
- GUIs now display correctly on Auto GUI scale.

## 1.2.0

### Added

- **Rift Beacon.** Bind it to a Rift Drive, then right-click a spot you can see to call that ship there. A beam marks where it will arrive. The usual warp requirements still apply.
- **Rift Fissures.** Tears in space found in ruins. They leak Rift Essence, which a nearby Spatial Siphon collects.
- **Rift Infused Goggles.** Engineer's goggles that also show Rift Fissures.

### Changed

- Machines show their real models in your inventory and hand.
- Rift Essence buckets have a new texture.
- The Creative Rift Drive has no minimum warp distance.
- Astrolabe tables can be 1x1, 2x2 or 3x3. Bigger tables map more ground.

### Fixed

- Opening an Astrolabe or a gate's dial list no longer crashes servers with lots of anchors or gates. Long lists show the nearest entries.
- The Astrolabe's map no longer floats, tilts or stutters on a moving ship.

## 1.1.0

### Added

- **Rift Gate and Rift Gate Frame.** Build a frame ring of any shape, add a controller, and dial another gate to open a doorway.
  - Players, mobs and land vehicles can go through.
  - Only the gate that dials needs essence and rotation.
  - Same dimension only.
- **Rift Probe.** Scan unexplored land in a chosen direction and distance. Costs Rift Essence.
- **Astrolabe Cartography Table.** A 3x3 multiblock with a live map and every reachable Warp Anchor, including distance, cost and a terrain preview.
- **Spatial Siphon and Rift Essence.** The siphon collects Rift Essence, a pipeable fluid, on every warp. Essence powers gates and probes.
- **Creative Rift Drive.** No recipe, cost, cooldown or range limit.
- **Rift Fissures, Rift Scars and Rift Infused Goggles.** Overworld ruins contain invisible fissures that leak Rift Essence. Goggles reveal them. A nearby siphon drains a fissure until it closes.
- **Navigator's Handbook.** An in-game guide with nine illustrated chapters. Craft it from a book, a brass sheet and an amethyst shard.
- Ponder scenes for the Siphon, Anchor, Astrolabe, Probe, Drive and Gate.
- 13 advancements.
- JEI info pages for every block and item.

### Changed

- Machine items use their block models and animations.
- Destinations are set at an Astrolabe. The Rift Drive screen now only shows status. Warps launch with a redstone signal.
- Every screen was redesigned.
- Rifts shatter open like glass. Gates close the same way in reverse.
- Passengers stay aboard during warps.
- Machines on a ship are always detected.
- Rift Gates use much less stress.
- Recipes were rebalanced. The Singularity Drive is much cheaper.
- The Rift Probe uses plainer wording.
- Setting a course from the Probe shows a chat message and marks it on the Astrolabe.

### Fixed

- Long warps no longer throw players off the ship or make the airship vanish.
- Dialled gates now connect.
- Gates now reliably notice things passing through.
- Rift Essence can be piped out of gates.
- Gates that were open a long time now play their closing animation.
- Rift effects are visible from both sides.
- Map colours are correct.

### Removed

- **Flight Lever.** Set a course at an Astrolabe and launch with redstone instead.
