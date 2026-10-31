# Fills all three rail-gate tanks back up, and touches nothing else - so a train part way through
# a test, or an afternoon of schedules, does not end with a gate going dark.
# Run from the same spot as aerowarptics_test:rails - GATE A's.
#
# This writes each tank rather than adding to it, and 8000 mB is exactly a Rift Gate's capacity
# (RiftGateBlockEntity.CAPACITY), so a full gate is left full and a dry one filled.
# Do not raise the figure: the tank would be loaded holding more than it can hold.
#
# A 15-cell opening costs more to run than the old 3x3 did: 430 a dial and 25 a second to hold,
# against 358 and 19. A full tank is roughly ten crossings, so expect to run this more often than
# the two-gate bench needed it.
data merge block ~0 ~1 ~-3 {Tank:{Fluid:{id:"aerowarptics:rift_essence",amount:8000}}}
data merge block ~112 ~1 ~-3 {Tank:{Fluid:{id:"aerowarptics:rift_essence",amount:8000}}}
data merge block ~224 ~1 ~-3 {Tank:{Fluid:{id:"aerowarptics:rift_essence",amount:8000}}}
tellraw @s {"text":"[AeroWarptics] all three gates topped up to 8 buckets.","color":"aqua"}
