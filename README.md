# AeroWarptics

A Create: Aeronautics addon that lets an airship fold space.

Build a **Rift Drive** into a vessel, spin it with Create's rotational force, register **Warp Anchors**
at the places you want to reach, and the whole ship — hull, machinery, cargo and crew — travels through
a temporary rift to the far end.

* Minecraft 1.21.1 / NeoForge 21.1.233
* Create 6.0.10
* Create: Aeronautics 1.3.0 (Sable 2.0.3 + Simulated 1.3.0 + Aeronautics 1.3.0)
* GeckoLib 4.8.4

---

## What an "airship" actually is here

Create: Aeronautics does not carry its own ship object. An assembled vessel is a **Sable
`SubLevel`**: an independent plot of blocks living in reserved chunks of the parent `ServerLevel`,
driven around by Sable's physics pipeline. Aeronautics contributes the balloons, propellers and
levitite on top of it; Simulated contributes the assembler and the controls.

Everything in this addon follows from that:

| Question | Sable API used |
| --- | --- |
| Which airship is this machine on? | `BlockEntitySubLevelActor.sable$tick(ServerSubLevel)` |
| Where is the airship? | `SubLevel.logicalPose()` (`Pose3dc`) |
| Ship space ⇄ world space | `Pose3dc.transformPosition` / `transformPositionInverse` |
| How big / heavy is it? | `LevelPlot.getBoundingBox()`, `ServerSubLevel.getMassTracker()` |
| Move it | `PhysicsPipeline.teleport(body, position, orientation)` |
| Fly it | `PhysicsPipeline.resetVelocity` + `addLinearAndAngularVelocity`, re-commanded each tick |
| Per-ship persistent state | `ServerSubLevel.get/setUserDataTag()` |
| Carry passengers with it | `SubLevelHelper.push/popEntityLocal` |
| Draw effects at a ship-mounted block | `Sable.HELPER.projectOutOfSubLevel` |

A Rift Drive never scans the world looking for its ship. Sable calls `sable$tick` on it once per tick
*because* it is part of an airship, and that call is the answer.

---

## The machines

### Rift Drive

A Create kinetic machine in four tiers. It accepts a shaft on the axis it faces, draws stress, and
needs a minimum RPM before it will charge at all.

| Tier | Min / optimal RPM | Stress | Range | Charge time | Cooldown | Efficiency |
| --- | --- | --- | --- | --- | --- | --- |
| Mk I | 64 / 128 | 16 su | 4,000 | 120 s | 120 s | 1.0× |
| Mk II | 96 / 192 | 24 su | 24,000 | 90 s | 90 s | 1.6× |
| Mk III | 128 / 256 | 32 su | 120,000 | 70 s | 70 s | 2.4× |
| Singularity | 192 / 256 | 48 su | 2,000,000 | 60 s | 300 s | 3.2×, unstable exit |

Every number above is a config default, not a constant.

### Warp Anchor

A destination. Place one, right-click to name it, choose public or private, group it under a network
label, switch it on or off. The record is kept in a server-wide `SavedData` file, so an anchor in an
unloaded chunk is still a valid destination — which is the point of long-range travel.

An anchor that gets assembled *into* an airship withdraws itself: its position would be a coordinate
inside Sable's plot grid, which nobody can navigate to.

---

## The warp

A warp is flown, not cut to. The ship makes a run at an aperture, crosses it, runs the corridor, and
comes out of a second aperture at the far end still moving.

```
        APPROACH                  CORRIDOR                     EMERGE
 ┌────┐                       ╔══════════════╗                         ┌─────┐
 │ship│──▶──▶──▶ ((entry))    ║ ▶▶▶ ship ▶▶▶ ║    ((exit)) ──▶──▶ ship │ rest│
 └────┘   opens off the bow   ╚══════════════╝    opens at the anchor  └─────┘
                                 y ≈ 900, above everything built
```

1. **Charging** — rotational force accumulates charge. Below the tier's minimum RPM the drive bleeds
   charge instead.
2. **Destination** — the pilot picks an anchor in the Rift Navigation console.
3. **Validation** — the server re-derives everything: which airship, which anchor, the distance, the
   cost, whether the player may command this drive.
4. **Lock** — the drive claims its airship. A second drive on the same ship is refused.
5. **Stabilising** — rings align, the destination locks, sparks converge on the core.
6. **Planning** — the *whole* journey is worked out before anything opens: where the ship will come
   out, whether that volume and its run-out are clear, and whether the corridor is free. A warp with
   nowhere to land is refused while the ship is still safely at its mooring.
7. **The run** — an aperture tears open ahead of the bow and the server takes the helm, driving the
   hull at it. The pilot's controls, the ship's own thrust and gravity are all overridden until it
   comes to rest.
8. **The corridor** — the hull crosses the threshold and comes out in a warp lane high above the
   world, running it for a few seconds. As it enters, the far aperture tears open at the destination,
   so anyone waiting there watches a rift form and *then* a ship come through it.
9. **Emergence** — the hull appears behind the far aperture already moving and coasts out under its
   own momentum, settling into the place the planner picked.
10. **Cooldown** — the drive vents and recovers.

The two threshold crossings are the only teleports. Everything else is genuine velocity, so
passengers, cargo and attached machinery are carried by the same Sable code that carries them in
normal flight.

### Which way is forward

Nothing in Sable or Aeronautics marks a bow, so the pilot marks it, on the drive, in the airship's own
frame. The setting is one of four quarter turns - **Forward**, **Right**, **Back**, **Left** - measured
from the Rift Drive's own front face.

Relative, not absolute, and that is the whole point. A compass bearing would be a lie the moment the
vessel came about: set it to north before a long turn and the rift would tear itself open across the
beam. Because the setting lives in ship space, it is only turned into a world bearing at the instant a
flight is planned, using the hull's current pose. Aim it at the nose once and it stays aimed at the
nose, whatever heading the ship is on and however far it has been sailed since.

The drive wears a **needle** on its crown, balanced across the upright gimbal, which points at the
current setting. It swings a quarter turn each time the setting is cycled, so the bearing can be read
off the machine from the deck without opening anything. The needle and the flight planner are driven
from the same value, and a test holds them to it: if the needle points somewhere the ship would not
actually go, the build fails.

A drive taking power from a *vertical* shaft has no front face to measure from, so the hull's own north
stands in as the reference. The four settings still cover all four ways round and the needle still
reads the same rule, so it remains something a pilot can aim - it just cannot be read off the block's
face.

### Cost

```
cost = baseWarpCost + distance × distanceMultiplier + (mass ÷ massReference) × sizeMultiplier
       ────────────────────────────────────────────────────────────────────────────────────
                                    tier costEfficiency
```

`mass` is Sable's own figure for the airship. If the mass tracker has not been built yet — briefly
after assembly — the bounding-box volume stands in.

### Safe arrival

The airship is never dropped onto the anchor block. The search starts from a **clearance point**: the
height at which the hull's underside would sit a configured buffer above the anchor, worked out from
the ship's own footprint rotated into the orientation it will arrive with. A skiff clears the anchor
by a few blocks; a two-hundred-block dreadnought starts a hundred blocks up, because that is where its
keel has to be for its deck not to be inside the mountain.

From there the search **climbs the anchor's column**, and only once every height in that column is
ruled out does it start stepping sideways in widening rings.

```
   blocked column?  ↑ ↑ ↑        then, and only then, rings:   ↑   ↑ ↑ ↑   ↑
   come in higher.  │ │ │                                      │   │ │ │   │
                    ● ● ●                                      ●   ● ● ●   ●
                      ▲ anchor                                   r=1   r=2
```

The volume tested at each candidate is not just the hull: it is the hull swept backwards along the
approach, covering the whole run-out from the exit aperture. If nothing fits, the warp is refused
before it starts and the ship stays where it is.

### Failure

The default consequences of a failed warp are: the warp stops, some charge is lost, the drive sits in
its fault state for a while. **The airship is never damaged or deleted.** `failure.dangerousFailures`
adds a shove and nothing more.

---

## Using it

1. Assemble an airship with Create: Aeronautics as usual.
2. Mount a **Rift Drive** on it and drive it with a shaft at or above its minimum RPM.
3. Place **Warp Anchors** wherever you want to travel to and name them.
4. Right-click the Rift Drive to open **Rift Navigation**.
5. Set the **Bow** control in the top right - `Forward`, `Right`, `Back` or `Left`, measured from the
   drive's own front face. Watch the needle on top of the machine and stop when it points at the nose.
   The compass point in brackets is only a readout of where that is pointing right now.
6. Pick a destination — the panel shows distance, cost, your charge, the drive's range and whether
   the destination is safe.
7. **Initiate Warp**, and stay aboard — the corridor is only visible from the deck.

Wearing Create's goggles (or Aeronautics' Aviator's Goggles) shows the drive's tier, state, charge and
RPM requirement, plus its stress impact in Create's usual format.

---

## Configuration

`config/aerowarptics-server.toml` — range, cost formula, arrival search and clearance buffer, the
flight through the rift (aperture stand-off and size, approach speed, corridor altitude and speed,
run-out length), permissions, failure behaviour, and a block of settings per drive tier. Corridor
duration and run-out time come from the tier, so a Singularity drive crosses faster than a Mk I.

`config/aerowarptics-client.toml` — particle density, rift distortion, corridor effects, screen shake,
effect volume.

---

## Building

```bash
./gradlew build
```

The mods this builds against live in `Depends/`. Create and the Aeronautics bundle ship several of
their libraries as jar-in-jar artifacts; those are unpacked into `Depends/lib/` so javac can see them.
Both directories are on the compile classpath and on the dev runtime classpath.

```bash
./gradlew test
```

---

## Limitations

**Cross-dimension warping is not implemented.** Sable binds a sub-level to one `ServerLevel`: its
blocks live in that level's plot grid, its rigid body belongs to that level's physics system, and
`PhysicsPipeline.teleport` takes a position and an orientation but no destination level. Sable 2.0.3
exposes no way to hand a sub-level, its plot chunks and its loading tickets to another level, and
rebuilding the ship block by block in the target dimension is exactly what an addon should not do.

Rather than fake it by teleporting players, the addon refuses the warp with a clear reason and leaves
a real extension point: register a `CrossDimensionWarp.Handler` and set `allowCrossDimensionWarp`, and
the rest of the pipeline — anchors, cost, validation, the state machine, the effects — already works
across dimensions unchanged.

**Airship ownership.** Neither Sable nor Simulated tracks who owns a vessel, so authority over a drive
is proximity plus presence: you must be within reach of it and, by default, standing on the ship it is
about to move. Anchors have real owners, and private anchors are enforced.

**A warp does not resume across a restart.** If the server stops mid-sequence, the drive comes back in
its fault state and the ship's claim is released. A hull that was out in the corridor is put back where
it set off from: the departure pose is written onto the airship itself, not onto the drive, so it
survives independently of the machine that started the journey.

**Textures and models are procedurally generated.** The geometry and its texture sheet come out of one
generator script, so box UVs cannot drift apart and the resource-integrity test can prove they do not
overlap. The machines read correctly — a plinth, a drum, two gimbal rings that overhang the block, a lit
core — but an artist would still want to replace the sheets by hand.
