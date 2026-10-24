# AeroWarptics test bench (datapack)

A reproducible debugging arena for the mod. Drop it into a world, run one command, and
get every machine laid out, labelled, powered where it makes sense, and stocked — plus
two targeted bug repros. This is meant to be the base of our in-game debugging going
forward, so it lives in the repo next to the mod.

## Install

1. Copy the whole `aerowarptics_test` folder into your test world's datapack folder:
   ```
   <instance>/saves/<world>/datapacks/aerowarptics_test/
   ```
   (The folder that contains `pack.mcmeta` must sit directly under `datapacks/`.)
2. In game: `/reload`
3. Stand where you want the middle of the pad, then:
   ```
   /function aerowarptics_test:build
   ```

Everything is placed **relative to your feet**, so your facing does not matter and you
can build it anywhere. Re-running rebuilds cleanly over the top.

## Functions

| Function | What it does |
| --- | --- |
| `aerowarptics_test:build` | Builds the 31×31 pad and every station, plus a far anchor 200 blocks east. |
| `aerowarptics_test:kit` | Gives one of everything straight into your inventory. |
| `aerowarptics_test:clear` | Removes the pad and far-anchor pad (run from the build spot). |
| `aerowarptics_test:stress_anchors` | Places 272 Warp Anchors 300 east — repro for the >256-anchor `EncoderException`. |

## Map (looking down; N is −Z, you are `@`)

```
                    N  (−Z)
        [Astrolabe 3x3]
                    |
 [Drive+motor]      @  [barrels]      [Warp Anchor near] ---> 200 blocks E: [Far anchor]
      (W)         (you)                     (E)
                                       [Rift Gate ring]
 [Chute+depot]    [Probe]              [Fissure+Siphon]
                    S  (+Z)                  (SE)
```

## Stations and what each is for

- **Rift Drive on live rotation** (west) — a `create:creative_motor` spinning a
  creative-tier `rift_drive`, with a `rift_modulator` beside it and a lever. Scroll the
  motor to set RPM; watch the drive take stress and the modulator's lens react. The drive
  will **not** warp here — it has to be part of an assembled hull first (see below).
- **Astrolabe** (north) — a 3×3 chart table that auto-forms on placement. Set and quote
  courses here. Permission checks live here too.
- **Warp Anchor, near** (east) — a course target close enough to select but inside the
  128-block minimum, so it is good for course-setting/quoting, not for an actual jump.
- **Rift Gate ring** (east) — a closed ring with a 3×3 opening (area 9; the code accepts
  4–225 with aspect ≤4). Pair it with a second gate to dial a connection.
- **Rift Fissure + Spatial Siphon** (south-east) — the fissure is invisible and intangible
  unless you are **wearing Rift Infused Goggles** (in barrel 1). The siphon drains it.
- **Rift Probe** (south) — scan ahead to produce a fix (an anchorless course).
- **Rift Chute + depot** (south-west) — drop items on the depot to watch the chute take them.
- **Supply barrels** (by you) — barrel 1: handbook, goggles, beacon, cores/lenses/rings,
  one of every drive tier. Barrel 2: the machine block-items to build your own layouts.

## The one thing you must do by hand: an airship

A warp cannot be scripted, because a Rift Drive only flies when it is part of an assembled
Create: Aeronautics sub-level — and only the assembler can make one. To test an end-to-end
warp / the `INTERRUPTED` stall:

1. Take a `rift_drive` from the barrel and build a small hull around it.
2. Assemble it into an airship (Aeronautics assembler + controller).
3. Set a course to the **far anchor** (200 east, ≥128 min) at the Astrolabe.
4. Trigger the drive with a redstone rising edge.

## Notes for maintainers

- Block/item IDs and block-states here are verified against the mod source and the Create
  6.0.10 jar, but the pack itself has not been run in a live world — if a `setblock`
  errors, check it against the current registry first.
- `pack_format` is `48` (Minecraft 1.21.1). Bump it if the target MC version changes.
- Keep this in sync with the registry: a new machine or drive tier means a new line in
  `build.mcfunction` and `kit.mcfunction`.
```
