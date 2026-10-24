# Reproduce the known bug: more than 256 visible Warp Anchors throws EncoderException
# when any player opens an Astrolabe (quoteAll is uncapped against a list(256) codec).
# See docs/remediation-plan.md, issue 1.
#
# Lays a 17x16 = 272 anchor grid on a pad 300 blocks east, clear of the main bench.
# After running, open the Astrolabe on the bench and watch the disconnect / server log.
fill ~298 ~-1 ~-9 ~318 ~-1 ~8 minecraft:smooth_stone
fill ~300 ~0 ~-8 ~316 ~0 ~7 aerowarptics:warp_anchor
tellraw @s {"text":"[AeroWarptics] 272 Warp Anchors placed 300 east. Now open an Astrolabe.","color":"red"}

# To remove them again (run from the same spot):
#   fill ~298 ~-1 ~-9 ~318 ~2 ~8 minecraft:air replace
