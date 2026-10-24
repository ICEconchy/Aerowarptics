# AeroWarptics debug test bench.
# Run standing where you want the middle of the pad:  /function aerowarptics_test:build
# Everything is placed RELATIVE to your feet (~), so your facing does not matter.
#
# Pad is 31x31, centred on you. Walking surface is your feet level (~0); the floor
# blocks are one below (~-1). Stations sit on the floor. A legend prints at the end.
#
# What cannot be scripted: an actual airship. A warp needs the Rift Drive to be part
# of an assembled Create: Aeronautics sub-level, which only the assembler can make.
# Glue the drive to a hull yourself, then use the near Astrolabe + the far anchor.

# ---- ground -------------------------------------------------------------------
# Clear the volume first, then lay a fresh floor, so re-running is idempotent.
fill ~-15 ~0 ~-15 ~17 ~9 ~17 minecraft:air replace
fill ~-15 ~-1 ~-15 ~17 ~-1 ~17 minecraft:smooth_stone

# ---- station 1: Rift Drive on live rotation (west edge) ------------------------
# Creative Motor -> Rift Drive (creative tier). Scroll the motor to set its RPM.
# A Rift Modulator is bolted beside the drive; a lever stands in for the redstone
# rising edge that launches a course (only does anything once the drive is on a hull).
setblock ~-12 ~0 ~0 create:creative_motor[facing=east]
setblock ~-11 ~0 ~0 aerowarptics:rift_drive_creative[facing=west]
setblock ~-11 ~0 ~1 aerowarptics:rift_modulator[facing=north]
setblock ~-11 ~0 ~-1 minecraft:smooth_stone
setblock ~-11 ~1 ~-1 minecraft:lever[face=floor,facing=south]

# ---- station 2: Astrolabe chart table (north) ---------------------------------
# A 3x3 of cells. Each placement runs onPlace/tryForm, so the last one forms the
# table automatically (FORMED becomes true; the block lights up). If it ever does
# not, break and replace any one cell by hand.
fill ~-1 ~0 ~-13 ~1 ~0 ~-11 aerowarptics:astrolabe

# ---- station 3: near Warp Anchor (centre-east) --------------------------------
# For setting/quoting courses at the Astrolabe. Too close to warp to (min 128).
setblock ~10 ~0 ~0 aerowarptics:warp_anchor[facing=west]

# ---- station 4: Rift Gate ring (east) -----------------------------------------
# A closed ring standing in the X-Y plane at z=+8, enclosing a 3x3 opening
# (area 9: within MIN_AREA 4 .. MAX_AREA 225, aspect 1 <= MAX_ASPECT 4).
# Perimeter is frame; one bottom-centre block is the thinking controller.
fill ~6 ~0 ~8 ~10 ~0 ~8 aerowarptics:rift_gate_frame
fill ~6 ~4 ~8 ~10 ~4 ~8 aerowarptics:rift_gate_frame
fill ~6 ~1 ~8 ~6 ~3 ~8 aerowarptics:rift_gate_frame
fill ~10 ~1 ~8 ~10 ~3 ~8 aerowarptics:rift_gate_frame
fill ~7 ~1 ~8 ~9 ~3 ~8 minecraft:air
setblock ~8 ~0 ~8 aerowarptics:rift_gate[facing=south]

# ---- station 5: Rift Fissure + Spatial Siphon (south-east) ---------------------
# The fissure is invisible and intangible without Rift Infused Goggles on. The
# siphon next to it drains it into Rift Essence over time.
setblock ~12 ~0 ~12 aerowarptics:rift_fissure
setblock ~12 ~0 ~13 aerowarptics:spatial_siphon

# ---- station 6: Rift Probe (south) --------------------------------------------
setblock ~0 ~0 ~12 aerowarptics:rift_probe

# ---- station 7: Rift Chute over a depot (south-west) --------------------------
# Belt/depot-fed. Drop items on the depot to watch the chute swallow them.
setblock ~-12 ~0 ~12 create:depot
setblock ~-12 ~1 ~12 aerowarptics:rift_chute

# ---- supply barrels (next to you) ---------------------------------------------
# Barrel 1: components, tools, and one of every drive tier.
setblock ~2 ~0 ~-2 minecraft:barrel[facing=up]
data merge block ~2 ~0 ~-2 {Items:[{Slot:0b,id:"aerowarptics:handbook",count:1},{Slot:1b,id:"aerowarptics:rift_goggles",count:1},{Slot:2b,id:"aerowarptics:rift_beacon",count:1},{Slot:3b,id:"aerowarptics:rift_core",count:16},{Slot:4b,id:"aerowarptics:rift_lens",count:16},{Slot:5b,id:"aerowarptics:stabiliser_ring",count:16},{Slot:6b,id:"aerowarptics:singularity_core",count:4},{Slot:7b,id:"aerowarptics:rift_essence_bucket",count:1},{Slot:9b,id:"aerowarptics:rift_drive_mk_i",count:1},{Slot:10b,id:"aerowarptics:rift_drive_mk_ii",count:1},{Slot:11b,id:"aerowarptics:rift_drive_mk_iii",count:1},{Slot:12b,id:"aerowarptics:rift_drive_singularity",count:1},{Slot:13b,id:"aerowarptics:rift_drive_creative",count:1}]}

# Barrel 2: the machine block-items, so you can place your own layouts.
setblock ~3 ~0 ~-2 minecraft:barrel[facing=up]
data merge block ~3 ~0 ~-2 {Items:[{Slot:0b,id:"aerowarptics:warp_anchor",count:8},{Slot:1b,id:"aerowarptics:astrolabe",count:18},{Slot:2b,id:"aerowarptics:rift_gate",count:4},{Slot:3b,id:"aerowarptics:rift_gate_frame",count:64},{Slot:4b,id:"aerowarptics:rift_probe",count:4},{Slot:5b,id:"aerowarptics:spatial_siphon",count:4},{Slot:6b,id:"aerowarptics:rift_chute",count:4},{Slot:7b,id:"aerowarptics:rift_modulator",count:8}]}

# ---- labels -------------------------------------------------------------------
setblock ~-13 ~0 ~0 minecraft:oak_sign[rotation=12]{is_waxed:1b,front_text:{messages:['"Rift Drive"','"+ motor"','"+ modulator"','"scroll motor RPM"']}}
setblock ~0 ~0 ~-10 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"Astrolabe"','"3x3 chart table"','"set courses here"','""']}}
setblock ~9 ~0 ~1 minecraft:oak_sign[rotation=4]{is_waxed:1b,front_text:{messages:['"Warp Anchor"','"(near)"','"course target"','"< 128: no warp"']}}
setblock ~6 ~0 ~7 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"Rift Gate"','"3x3 opening"','"pair to dial"','""']}}
setblock ~11 ~0 ~12 minecraft:oak_sign[rotation=4]{is_waxed:1b,front_text:{messages:['"Fissure +"','"Siphon"','"wear goggles"','"to see it"']}}
setblock ~0 ~0 ~11 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"Rift Probe"','"scan ahead"','"for a fix"','""']}}
setblock ~-12 ~0 ~11 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"Rift Chute"','"+ depot"','"drop items on"','"the depot"']}}
setblock ~2 ~0 ~-3 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"Supply"','"barrels"','"kit + machines"','""']}}

# ---- far Warp Anchor: a real warp target, 200 blocks east (>= 128 min) ---------
fill ~198 ~-1 ~-2 ~202 ~-1 ~2 minecraft:smooth_stone
fill ~198 ~0 ~-2 ~202 ~3 ~2 minecraft:air replace
setblock ~200 ~0 ~0 aerowarptics:warp_anchor[facing=west]
setblock ~200 ~0 ~1 minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['"Far anchor"','"200 blocks"','"real warp target"','""']}}

# ---- legend -------------------------------------------------------------------
tellraw @s {"text":"[AeroWarptics] test bench built.","color":"aqua"}
tellraw @s {"text":"W: Rift Drive on a live Creative Motor (scroll to set RPM).","color":"gray"}
tellraw @s {"text":"N: Astrolabe 3x3 (auto-forms).  E: near Warp Anchor + Rift Gate ring.","color":"gray"}
tellraw @s {"text":"SE: Fissure+Siphon (goggles!).  S: Probe.  SW: Chute+depot.","color":"gray"}
tellraw @s {"text":"By you: 2 supply barrels.  200 blocks east: far anchor for a real warp.","color":"gray"}
tellraw @s {"text":"Airships are not scripted: glue the drive to a hull, then warp to the far anchor.","color":"yellow"}
