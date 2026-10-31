# Tear the rail-gate bench down. Run from the SAME spot you built it, standing where GATE A was.
# Clears the three yards back to air, floor included. Does not touch anything you added beyond
# the original footprint.
#
# This is not only tidiness. The bench writes fixed gate ids, and breaking a ring is what makes
# unform() call registry.remove(gateId) - which is what frees those three ids to be used by a
# bench built somewhere else in the same world. Run this before you build another one.
# An assembled train is an entity and survives this: disassemble it first, or it will be left
# standing on no track.
fill ~-42 ~-1 ~-6 ~40 ~6 ~6 minecraft:air replace
fill ~41 ~-1 ~-6 ~183 ~6 ~6 minecraft:air replace
fill ~184 ~-1 ~-6 ~266 ~6 ~6 minecraft:air replace
tellraw @s {"text":"[AeroWarptics] rail-gate bench cleared, and its three gate ids are free again.","color":"aqua"}
