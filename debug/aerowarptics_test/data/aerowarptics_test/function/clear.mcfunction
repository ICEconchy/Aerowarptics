# Tear the bench down. Run from the SAME spot you built it, standing where you stood.
# Clears the pad and the far-anchor pad back to air. Does not touch anything you added
# beyond the original footprint.
fill ~-15 ~-1 ~-15 ~17 ~9 ~17 minecraft:air replace
fill ~198 ~-1 ~-2 ~202 ~3 ~2 minecraft:air replace
tellraw @s {"text":"[AeroWarptics] test bench cleared.","color":"aqua"}
