# AeroWarptics rail-gate bench: three gates round a shared hub, four lanes, and the yards for
# two trains.
# Run standing where you want GATE A:  /function aerowarptics_test:rails
# Everything is placed RELATIVE to your feet (~), so your facing does not matter.
# Run it on clear ground - it reaches 42 blocks west of you and 266 east, 6 either side.
# Walking surface is your feet level (~0); floor blocks are one below (~-1), as on the pad.
#
# Looking down, north is -Z (up the page). You are @ where GATE A goes.
#
#   z-3   . . . . . the motors and the hub's lever stand on this line . . . . .
#   z-2   ====track====[A]...................[HUB]  gap  ====track====>
#   z-1   ====track====[A]...................[HUB]  gap  ====track====>
#   z 0      walkway    A   no track in this column   HUB      walkway
#   z+1      <====track====  gap  [HUB]...................[B]====track====
#   z+2      <====track====  gap  [HUB]...................[B]====track====
#
#         -40      @0                       +112                    +224      +264
#
# A lane is track run into a gate's opening, bound through a corridor in the Rift Corridor
# dimension to whichever gate the near one is routed to. A gate takes ONE LANE PER COLUMN of its
# opening, by itself, as soon as track runs into that column's bottom cell - that is
# RiftGateBlockEntity.noticeTracks, every 20 ticks. So two parallel tracks into one gate are two
# lanes, and nothing here has to say so.
#
# Both origin gates are routed to the HUB, which is the whole point of the layout: two routes
# wanting one gate is the only way to reach RailGateLogic.Call.WAIT / DESTINATION_BUSY, and a
# two-gate bench cannot get there. The HUB itself needs no route - a gate asked to open for a
# train is handed its target directly (RailTransit calls gate.dialForTrain), so only gates with
# track run into them need one.
#
# Each ring is 7 wide by 5 tall across Z with a 5x3 opening - area 15, inside MIN_AREA 4 and
# MAX_AREA 225, aspect 5/3 well inside MAX_ASPECT 4. The bottom row sits in the floor at ~-1 so
# the opening's bottom row is at walking level, which is where a lane has to enter: the lowest
# open cell of its column, or RailLaneMap.Refusal.NOT_BOTTOM_ROW. Columns run across = z + 2, so
# column 0 is z-2 and column 4 is z+2. Column 2 (z 0) is left trackless on purpose - it stays a
# clear walk-through, so the same gate serves the pedestrian path too.
#
# ---- the routes are scripted, and this is how -----------------------------------------
# Three commands per gate, and the ORDER IS THE TRICK. Do not reorder them.
#   1. The ring is laid MINUS its top-middle block, plus the controller. tryForm runs as the
#      controller goes down, the flood fill escapes up through the gap and busts MAX_AREA, so the
#      gate does not form - and unform() never touches gateId, so it stays null. It has to be a
#      MID-EDGE block: the fill is orthogonal within the plane, so a missing corner is still
#      diagonal from every interior cell and the ring would form anyway.
#   2. data merge block writes GateId, Destination and the tank. read() takes GateId and
#      Destination straight off the tag.
#   3. The top-middle frame block goes in. Frame blocks fire nothing, so the gate forms on its own
#      revalidate within 20 ticks, and registerSelf uses the id already set instead of minting a
#      random one. noticeTracks lays the lanes on the pass after that - about two seconds from now.
# If a dial says there is no route, the merge did not take: set the routes by hand at the dials
# (pick the HUB from A and from B, press Set Route) and the rest of the bench is unaffected.
#
# ---- one bench per world --------------------------------------------------------------
# The gate ids here are fixed, which is what lets the routes be written. registerSelf re-registers
# an id it already knows with existing.withShape(shape), and RiftGate.withShape keeps the OLD
# controller position - so a second bench in the same world would leave all three records pointing
# at the first bench's controllers. Tear this one down with aerowarptics_test:rails_clear before
# building another. Re-running in the SAME spot is fine: the clear below unforms the old rings
# first, which unregisters them.
#
# ---- what no command can do -----------------------------------------------------------
# Four things, once per world, all in Create's own screens.
#   1. Place the four Train Stations from the barrel. TWO clicks each, and the first one decides
#      everything: right-click the marked track while LOOKING AWAY FROM THE GATES.
#      TrackTargetingBlockItem takes the station's direction from Player.getLookAngle, not from
#      the face you clicked. Then click anywhere within 16 blocks to set the block down - it does
#      not have to be beside the track. Create binds a station into its own edge-point registry
#      as it is placed, which setblock cannot.
#   2. Name them WEST, HUB EAST, EAST and HUB WEST - what the legend's schedules assume.
#   3. Make the bogeys, then build a train where the station's own assembly ghost shows. There is
#      NO bogey item - "Create new bogeys by using Train Casing on Tracks", says Create's ponder,
#      and clicking the track again cycles the design. So: Train Casing on the track twice over,
#      then the carriage built over both bogeys in Train Casing, Train Controls, and a Blaze
#      Burner on the side the Controls faces. Then Assemble.
#   4. Write the schedules (WEST then HUB EAST, cyclic; EAST then HUB WEST, cyclic) and right-click
#      each burner with one.
#
# "Mind that a Station can only be approached from the indicated direction" - Create's own ponder.
# So on a there-and-back schedule the two stations must face OPPOSITE ways, which here means each
# of them looking AWAY from the gates: WEST west, HUB EAST east, HUB WEST west, EAST east. Get one
# backwards and the train crosses the gate, fails to dock and sits there - which reads exactly like
# a gate bug and is not one. Each station spot sits mid-run so there is track either side of it for
# Create to assemble a train on, whichever way round it ends up.

# ---- ground ---------------------------------------------------------------------------
# Cleared then floored in three sections, so re-running rebuilds cleanly. One fill over the whole
# footprint would be 28,119 blocks - under the 32,768 limit, but not by enough to lean on. An
# assembled train is an entity by then and survives this; stations, stock and anything you built
# do not.
fill ~-42 ~0 ~-6 ~40 ~6 ~6 minecraft:air replace
fill ~-42 ~-1 ~-6 ~40 ~-1 ~6 minecraft:smooth_stone
fill ~41 ~0 ~-6 ~183 ~6 ~6 minecraft:air replace
fill ~41 ~-1 ~-6 ~183 ~-1 ~6 minecraft:smooth_stone
fill ~184 ~0 ~-6 ~266 ~6 ~6 minecraft:air replace
fill ~184 ~-1 ~-6 ~266 ~-1 ~6 minecraft:smooth_stone

# ---- gate A: the ring at x=0, routed to the HUB ---------------------------------------
# Perimeter is frame; the interior is the opening. The controller replaces the middle of the near
# column, where a shaft reaches it without crossing the opening, and faces along the ring's normal
# because that is the axis a Rift Gate takes rotation on. The top row is laid in two halves: the
# middle block is held back until the id is written.
fill ~0 ~-1 ~-3 ~0 ~-1 ~3 aerowarptics:rift_gate_frame
fill ~0 ~3 ~-3 ~0 ~3 ~-1 aerowarptics:rift_gate_frame
fill ~0 ~3 ~1 ~0 ~3 ~3 aerowarptics:rift_gate_frame
fill ~0 ~0 ~-3 ~0 ~2 ~-3 aerowarptics:rift_gate_frame
fill ~0 ~0 ~3 ~0 ~2 ~3 aerowarptics:rift_gate_frame
fill ~0 ~0 ~-2 ~0 ~2 ~2 minecraft:air
setblock ~0 ~1 ~-3 aerowarptics:rift_gate[facing=east]
data merge block ~0 ~1 ~-3 {GateId:[I;1,0,0,1],Destination:[I;1,0,0,2],Tank:{Fluid:{id:"aerowarptics:rift_essence",amount:8000}}}
setblock ~0 ~3 ~0 aerowarptics:rift_gate_frame

# ---- the HUB: the ring at x=112, the far end of all four lanes ------------------------
# No Destination: no track runs into it, and it needs none to be dialled for a train.
fill ~112 ~-1 ~-3 ~112 ~-1 ~3 aerowarptics:rift_gate_frame
fill ~112 ~3 ~-3 ~112 ~3 ~-1 aerowarptics:rift_gate_frame
fill ~112 ~3 ~1 ~112 ~3 ~3 aerowarptics:rift_gate_frame
fill ~112 ~0 ~-3 ~112 ~2 ~-3 aerowarptics:rift_gate_frame
fill ~112 ~0 ~3 ~112 ~2 ~3 aerowarptics:rift_gate_frame
fill ~112 ~0 ~-2 ~112 ~2 ~2 minecraft:air
setblock ~112 ~1 ~-3 aerowarptics:rift_gate[facing=east]
data merge block ~112 ~1 ~-3 {GateId:[I;1,0,0,2],Tank:{Fluid:{id:"aerowarptics:rift_essence",amount:8000}}}
setblock ~112 ~3 ~0 aerowarptics:rift_gate_frame

# ---- gate B: the ring at x=224, routed to the HUB -------------------------------------
fill ~224 ~-1 ~-3 ~224 ~-1 ~3 aerowarptics:rift_gate_frame
fill ~224 ~3 ~-3 ~224 ~3 ~-1 aerowarptics:rift_gate_frame
fill ~224 ~3 ~1 ~224 ~3 ~3 aerowarptics:rift_gate_frame
fill ~224 ~0 ~-3 ~224 ~2 ~-3 aerowarptics:rift_gate_frame
fill ~224 ~0 ~3 ~224 ~2 ~3 aerowarptics:rift_gate_frame
fill ~224 ~0 ~-2 ~224 ~2 ~2 minecraft:air
setblock ~224 ~1 ~-3 aerowarptics:rift_gate[facing=east]
data merge block ~224 ~1 ~-3 {GateId:[I;1,0,0,3],Destination:[I;1,0,0,2],Tank:{Fluid:{id:"aerowarptics:rift_essence",amount:8000}}}
setblock ~224 ~3 ~0 aerowarptics:rift_gate_frame

# ---- rotation -------------------------------------------------------------------------
# A gate needs gateMinimumRpm (32) to hold a connection open, and a Creative Motor makes
# DEFAULT_SPEED (16), so each motor is set to 64 and only then joined to its gate by a shaft - the
# network forms once, at the right speed. All three stand on the z-3 line, clear of every lane, at
# walking level, where you can reach one to test a gate losing its rotation.
setblock ~2 ~1 ~-3 create:creative_motor[facing=west]
data merge block ~2 ~1 ~-3 {ScrollValue:64}
setblock ~1 ~1 ~-3 create:shaft[axis=x]
setblock ~114 ~1 ~-3 create:creative_motor[facing=west]
data merge block ~114 ~1 ~-3 {ScrollValue:64}
setblock ~113 ~1 ~-3 create:shaft[axis=x]
setblock ~226 ~1 ~-3 create:creative_motor[facing=west]
data merge block ~226 ~1 ~-3 {ScrollValue:64}
setblock ~225 ~1 ~-3 create:shaft[axis=x]

# ---- the hub's hold -------------------------------------------------------------------
# A signal on a gate's controller does not power it - it holds trains back, which is
# RailGateLogic.Call.HELD and GateFailure.RAIL_HELD. The lever hangs on the HUB's controller
# because the hub is where trains queue. If it drops off, stand a block beside the controller and
# put a lever on that instead; anything that gives the controller a signal will do.
setblock ~112 ~1 ~-4 minecraft:lever[face=wall,facing=north]

# ---- track ----------------------------------------------------------------------------
# Straight along X, which is the only shape that can run into a gate whose normal is X
# (RiftPortalTracks.runsInto). Four runs, two per route, one block apart so a Train Station placed
# on the inner lane has the trackless middle column to stand in.
#
# The gaps are not oversights. A lane's far end is the mod's ground: a stub out of the far gate's
# matching column and apronLength (2) blocks of apron on from it. The column mapping between two
# gates of the same width and the same orientation is the identity, so A's columns 0 and 1 come
# out of the HUB's columns 0 and 1 - at x +113 (stub) and +114, +115 (apron) on z-2 and z-1. B's
# columns 3 and 4 come out heading west, at +111 and +110, +109 on z+1 and z+2. Leave them empty.
# (apronLength 2 is the default; a server that changed it leaves a gap, which is a block or two of
# track to fill in by hand.)
fill ~-40 ~0 ~-2 ~-1 ~0 ~-2 create:track[shape=xo]
fill ~-40 ~0 ~-1 ~-1 ~0 ~-1 create:track[shape=xo]
fill ~116 ~0 ~-2 ~160 ~0 ~-2 create:track[shape=xo]
fill ~116 ~0 ~-1 ~160 ~0 ~-1 create:track[shape=xo]
fill ~225 ~0 ~1 ~264 ~0 ~1 create:track[shape=xo]
fill ~225 ~0 ~2 ~264 ~0 ~2 create:track[shape=xo]
fill ~64 ~0 ~1 ~108 ~0 ~1 create:track[shape=xo]
fill ~64 ~0 ~2 ~108 ~0 ~2 create:track[shape=xo]

# ---- the parts barrel, by the WEST station spot ---------------------------------------
setblock ~-38 ~0 ~4 minecraft:barrel[facing=up]
data merge block ~-38 ~0 ~4 {Items:[{Slot:0b,id:"create:track_station",count:4},{Slot:1b,id:"create:railway_casing",count:32},{Slot:2b,id:"create:controls",count:2},{Slot:3b,id:"create:blaze_burner",count:2},{Slot:4b,id:"create:schedule",count:2},{Slot:5b,id:"create:track",count:64},{Slot:6b,id:"create:wrench",count:1},{Slot:9b,id:"aerowarptics:rift_essence_bucket",count:3},{Slot:10b,id:"aerowarptics:rift_goggles",count:1},{Slot:11b,id:"aerowarptics:handbook",count:1},{Slot:12b,id:"aerowarptics:rift_gate",count:4},{Slot:13b,id:"aerowarptics:rift_gate_frame",count:64}]}

# ---- markers --------------------------------------------------------------------------
setblock ~-38 ~0 ~5 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"Parts barrel"','"stations, casing"','"controls, burners"','"schedules, track"']}}
setblock ~-20 ~0 ~-4 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"WEST station"','"target z-1 track"','"LOOKING WEST, then"','"place block <=16"']}}
setblock ~-32 ~0 ~-4 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"Make bogeys with"','"Train Casing ON"','"THE TRACK, then"','"build + Assemble"']}}
setblock ~0 ~0 ~4 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"GATE A"','"routed to the HUB"','"2 lanes in:"','"z-2 and z-1"']}}
setblock ~112 ~0 ~4 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"HUB GATE"','"far end of all 4"','"lanes. No route"','"of its own."']}}
setblock ~114 ~0 ~-5 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"Motors: 64 rpm"','"min is 32. Break a"','"shaft to stall it."','"Lever = hold."']}}
setblock ~116 ~0 ~-4 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"A lanes come out"','"here. +113 to +115"','"is the mod stub"','"and apron - leave it"']}}
setblock ~108 ~0 ~4 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"B lanes come out"','"here. +109 to +111"','"is the mod stub"','"and apron - leave it"']}}
setblock ~138 ~0 ~-4 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"HUB EAST station"','"target z-1 track"','"LOOKING EAST, then"','"place block <=16"']}}
setblock ~86 ~0 ~4 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"HUB WEST station"','"target z+1 track"','"LOOKING WEST, then"','"place block <=16"']}}
setblock ~224 ~0 ~4 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"GATE B"','"routed to the HUB"','"2 lanes in:"','"z+1 and z+2"']}}
setblock ~244 ~0 ~4 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"EAST station"','"target z+1 track"','"LOOKING EAST, then"','"place block <=16"']}}

# ---- legend ---------------------------------------------------------------------------
tellraw @s {"text":"[AeroWarptics] rail-gate bench built: three gates, four lanes, two routes into one hub.","color":"aqua"}
tellraw @s {"text":"GATE A at x+0 and GATE B at x+224 are both routed to the HUB at x+112, 112 apart so neither route pre-dials the other's gate.","color":"gray"}
tellraw @s {"text":"Routes are already written, so the lanes lay themselves. Give it two seconds, then a dial's Rails line should read 2 in, 2 out at A and B, and 4 incoming at the HUB.","color":"gray"}
tellraw @s {"text":"Four things no command can do, once per world:","color":"yellow"}
tellraw @s {"text":"1. Place the four Train Stations: TWO clicks each. Right-click the marked track LOOKING AWAY FROM THE GATES - the look angle, not the clicked face, is what sets the direction trains may approach from. Then click within 16 blocks to put the block down.","color":"gray"}
tellraw @s {"text":"2. Name them WEST, HUB EAST, EAST and HUB WEST.","color":"gray"}
tellraw @s {"text":"3. There is no bogey item: use Train Casing ON THE TRACK to create each bogey, clicking again to cycle the design. Then build the carriage over both in Train Casing, add Train Controls and a Blaze Burner on the side the Controls faces, and Assemble.","color":"gray"}
tellraw @s {"text":"4. Two schedules: WEST then HUB EAST cyclic, and EAST then HUB WEST cyclic. Run both at once and the second train hits DESTINATION_BUSY at the hub - the arm a two-gate bench cannot reach.","color":"gray"}
tellraw @s {"text":"A station can only be approached from the direction you were looking when you targeted its track, so the two on a route must face opposite ways: WEST west, HUB EAST east, HUB WEST west, EAST east. One backwards and the train crosses, fails to dock and stalls - which looks like a gate bug and is not.","color":"yellow"}
tellraw @s {"text":"The outer lane of each pair (z-2, z+2) has no station on purpose: drive it by hand to reach the stop-dead path, which a scheduled train never takes.","color":"gray"}
tellraw @s {"text":"/aerowarptics trace on logs every rail decision. aerowarptics_test:rails_essence tops the tanks up; rails_clear tears it down, which the fixed gate ids need before you build another.","color":"gray"}
