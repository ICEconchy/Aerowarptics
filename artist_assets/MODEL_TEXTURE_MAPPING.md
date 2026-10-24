# Model to Texture Mapping Guide

This document shows how each block/item model references its textures.

## How Models Reference Textures

Each model JSON file contains a `textures` section that maps texture slots to PNG files:

```json
{
  "textures": {
    "all": "aerowarptics:block/my_texture"
  }
}
```

The format is: `modname:category/texture_name`
- `aerowarptics` = mod namespace
- `block` or `item` = texture category
- `texture_name` = the PNG filename (without extension)

---

## Block Models

### Cube Blocks (Simple Textured Cubes)

| Model File | Texture Reference | Texture File |
|------------|-------------------|--------------|
| warp_glass.json | aerowarptics:block/warp_glass | warp_glass.png |
| rift_casing.json | aerowarptics:block/rift_casing | rift_casing.png |
| spatial_siphon.json | aerowarptics:block/spatial_siphon | spatial_siphon.png |
| rift_coupler.json | aerowarptics:block/rift_coupler | rift_coupler.png |
| drive_extender.json | aerowarptics:block/drive_extender | drive_extender.png |

### Complex Block Models

| Model File | Texture Reference(s) | Texture File(s) | Type |
|------------|----------------------|-----------------|------|
| rift_drive_mk_i.json | aerowarptics:block/rift_drive_mk_i | rift_drive_mk_i.png | Particle texture |
| rift_drive_mk_ii.json | aerowarptics:block/rift_drive_mk_ii | rift_drive_mk_ii.png | Particle texture |
| rift_drive_mk_iii.json | aerowarptics:block/rift_drive_mk_iii | rift_drive_mk_iii.png | Particle texture |
| rift_drive_singularity.json | aerowarptics:block/rift_drive_singularity | rift_drive_singularity.png | Particle texture |
| rift_drive_creative.json | aerowarptics:block/rift_drive_creative | rift_drive_creative.png | Particle texture |
| rift_chute.json | aerowarptics:block/rift_chute | rift_chute.png | Block texture |
| astrolabe.json | aerowarptics:block/astrolabe | astrolabe.png | Particle texture |
| rift_gate.json | aerowarptics:block/rift_gate | rift_gate.png | Gate texture |
| rift_portal_ns.json | aerowarptics:block/rift_portal | rift_portal.png | Portal animation* |
| rift_portal_ew.json | aerowarptics:block/rift_portal | rift_portal.png | Portal animation* |
| rift_gate_frame.json | aerowarptics:block/rift_gate_frame | rift_gate_frame.png | Frame texture |
| rift_modulator.json | aerowarptics:block/rift_modulator | rift_modulator.png | Modulator texture |
| rift_probe.json | aerowarptics:block/rift_probe | rift_probe_*.png | Multi-texture |
| spatial_stabiliser.json | aerowarptics:block/spatial_stabiliser_* | spatial_stabiliser_*.png | Multi-texture |
| spatial_wave_detector.json | aerowarptics:block/spatial_wave_detector_* | spatial_wave_detector_*.png | Multi-texture |
| rift_essence.json | aerowarptics:block/rift_essence_* | rift_essence_*.png | Flowing liquid |
| collapse_cradle*.json | aerowarptics:block/collapse_cradle* | collapse_cradle*.png | Multi-part structure |
| rift_plotter*.json | aerowarptics:block/rift_plotter* | rift_plotter*.png | Multi-part structure |
| rift_brazier.json | aerowarptics:block/rift_brazier | rift_brazier.png | Block texture |
| cooling_vent.json | aerowarptics:block/cooling_vent_* | cooling_vent_*.png | Multi-texture |

*Animated textures - see the .mcmeta files for animation details

---

## Item Models

### Simple Generated Items

These use `minecraft:item/generated` parent with layer textures:

| Model File | Texture Layers | Texture Files |
|------------|----------------|---------------|
| warp_dust.json | layer0 | warp_dust.png |
| warp_shard.json | layer0 | warp_shard.png |
| rift_lens.json | layer0 | rift_lens.png |
| opened_rift_lens.json | layer0 | opened_rift_lens.png |
| rift_core.json | layer0 | rift_core.png (animated*) |
| incomplete_rift_core.json | layer0 | incomplete_rift_core.png |
| singularity_core.json | layer0 | singularity_core.png (animated*) |
| rift_essence_bucket.json | layer0 | rift_essence_bucket.png |
| rift_beacon.json | layer0 | rift_beacon.png |
| rift_pearl.json | layer0 | rift_pearl.png |
| scar_chart.json | layer0 | scar_chart.png |
| scar_heart.json | layer0 | scar_heart.png (animated*) |
| stabiliser_ring.json | layer0 | stabiliser_ring.png |
| relic_armillary.json | layer0 | relic_armillary.png |

### Multi-Layer Items

| Model File | Texture Layers | Texture Files |
|------------|----------------|---------------|
| handbook.json | layer0 | handbook.png |
| rift_goggles.json | layer0 | rift_goggles.png |
| warp_crystal.json | layer0 | warp_crystal.png |
| pearlescent_warp_crystal.json | layer0 | pearlescent_warp_crystal.png |
| folded_warp_crystal.json | layer0 | folded_warp_crystal.png (animated*) |
| stormglass*.json | layer0 | stormglass*.png (some animated*) |

### **SPECIAL: Ship Compass** ⭐ VANILLA TEXTURE

| Model File | Texture Layers | Texture Files | Status |
|------------|----------------|---------------|--------|
| ship_compass.json | layer0: **minecraft:item/compass_16** | **NEEDS REPLACEMENT** | **HIGH PRIORITY** |
| | layer1: aerowarptics:item/ship_compass_badge | ship_compass_badge.png | ✓ Done |
| ship_compass_00-31.json | (rotation variants, all use ship_compass.json as parent) | Same as above | **HIGH PRIORITY** |

**Action Required**: Replace `minecraft:item/compass_16` with a custom compass base texture.

---

## Texture Animation Files (.mcmeta)

The following textures have animation metadata (animation framerate, looping, etc.):

**Block Textures**:
- rift_portal.png.mcmeta
- rift_portal_closing.png.mcmeta
- rift_portal_opening.png.mcmeta

**Item Textures**:
- rift_core.png.mcmeta
- singularity_core.png.mcmeta
- stormglass.png.mcmeta
- stormglass_gathering.png.mcmeta
- stormglass_raging.png.mcmeta
- unset_stormglass.png.mcmeta
- scar_heart.png.mcmeta
- folded_warp_crystal.png.mcmeta

**Do not modify .mcmeta files** - these control the animation properties and should remain as-is.

---

## Texture Resolution Guide

All textures should be created at:
- **Base resolution**: 16x16 pixels (Minecraft standard)
- **Can be larger** if needed (e.g., 32x32, 64x64), but will display at 16x16 unless the mod's rendering code specifically handles higher resolutions
- **Format**: PNG with alpha transparency support

---

## Notes for Artist

1. **Texture Naming**: Keep existing texture filenames - they're referenced by the models
2. **Color Consistency**: Review existing textures to match the visual style
3. **Transparency**: PNG alpha channel is fully supported for special effects
4. **Animations**: Don't modify .mcmeta files; animation parameters are already set
5. **Priority**: Focus on Ship Compass first, then general texture polish

---

## Quick Reference: Files That Need Redrawing

Look for any textures where the corresponding model uses vanilla Minecraft textures:

- ✓ `ship_compass_badge.png` - Already custom (reference for style)
- **✗ Ship Compass base** - Currently uses vanilla compass (NEEDS REPLACEMENT)
- ✓ All other block/item textures - Already custom

Everything else is already custom and can be enhanced/polished as needed.
