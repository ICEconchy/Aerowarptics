# AeroWarptics Texture & Model Redraw Guide

This folder contains all textures and block models that need to be redrawn for the AeroWarptics mod.

## Project Overview

AeroWarptics is a Minecraft mod that adds advanced warp/teleportation mechanics, including:
- Warp drives and anchors for dimensional travel
- Rift portals and essence systems
- Ship compass navigation
- Various specialized utility blocks

## What Needs to Be Done

The mod currently uses a mix of:
1. **Custom textures** - Already created, no changes needed
2. **Vanilla Minecraft textures** - These need custom replacements

### Items Using Vanilla Textures (PRIORITY)

The following items currently reference vanilla Minecraft textures and **must be redesigned**:

#### Ship Compass (`ship_compass_*.json`)
- **Current base texture**: `minecraft:item/compass` (vanilla compass)
- **What needs to be created**: 
  - Custom compass base texture to replace vanilla compass
  - Should maintain the rotating functionality (32 rotation states: compass_00 through compass_31)
  - The badge overlay (`ship_compass_badge.png`) is already custom
  - All 32 rotation variants need corresponding textures
- **Files involved**: 
  - Base model: `models/items/ship_compass.json`
  - Rotation variants: `models/items/ship_compass_00.json` through `ship_compass_31.json`
  - Custom badge: `textures/items/ship_compass_badge.png` (already done)

### Blocks Using Vanilla Model Parents (SECONDARY)

These blocks use vanilla Minecraft block model parents but have custom textures. They may need custom models if the visual doesn't match expectations:

#### Cube-Based Blocks
- **warp_glass** - Uses `minecraft:block/cube_all` (simple textured cube)
- **rift_casing** - Uses `minecraft:block/cube_all`
- **spatial_siphon** - Uses `minecraft:block/cube_all`

#### Complex Blocks with Custom Models
- **Rift Portal** (EW/NS variants) - Portal-style blocks
- **Rift Essence** - Fluid-like visual
- **Collapse Cradle** - Multi-part structure
- **Rift Plotter** - Multi-part structure
- **Rift Brazier** - Decorative block

## Folder Structure

```
artist_assets/
├── textures/
│   ├── blocks/        # All block textures (PNG files)
│   └── items/         # All item textures (PNG files)
├── models/
│   ├── blocks/        # Block model definitions (JSON files)
│   └── items/         # Item model definitions (JSON files)
└── README.md          # This file
```

## Texture Specifications

### Block Textures
- **Resolution**: 16x16 pixels (standard Minecraft textures)
- **Format**: PNG with transparency support
- **Location**: `textures/blocks/`

### Item Textures
- **Resolution**: 16x16 pixels
- **Format**: PNG with transparency support
- **Location**: `textures/items/`
- **Animated Textures**: Some items have `.mcmeta` files that define animation parameters

### Animated Textures (Notice the .mcmeta files)
The following textures have animation metadata:
- `rift_core.png` - Glowing core effect
- `singularity_core.png` - Pulsing singularity
- `stormglass.png` / `stormglass_*.png` - Storm glass variations
- `scar_heart.png` - Pulsing heart
- `folded_warp_crystal.png` - Crystal animation
- `rift_portal*.png` - Portal animation
- Block portals also have `.mcmeta` files for animation

Check the `.mcmeta` files for framerate and animation loop settings.

## Model Files Explanation

### Model Format (JSON)
Each model file references:
1. **parent** - The base model template (e.g., `minecraft:block/cube_all`, `minecraft:item/generated`)
2. **textures** - The texture references (maps texture slots to PNG files)
3. **elements** or **overrides** - Custom geometry or variants

### Examples

#### Simple Cube Block (warp_glass.json)
```json
{
  "parent": "minecraft:block/cube_all",
  "render_type": "minecraft:translucent",
  "textures": {
    "all": "aerowarptics:block/warp_glass"
  }
}
```
This uses a simple cube with the same texture on all sides.

#### Complex Item (ship_compass.json)
```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/compass_16",
    "layer1": "aerowarptics:item/ship_compass_badge"
  },
  "overrides": [...]
}
```
This layers two textures and has rotation overrides.

## Priority List for Artist

### HIGH PRIORITY (Block Warp)
1. **Ship Compass** - Must replace vanilla compass texture with a custom design
   - 32 rotation states needed
   - Custom compass design that fits the mod aesthetic
   - Will overlay with the badge texture

### MEDIUM PRIORITY (Can use vanilla if needed temporarily)
2. Glass block (`warp_glass`)
3. Casing blocks (`rift_casing`)
4. Metal/mechanical blocks

### NICE TO HAVE
- Custom models for complex structures (cradle, plotter, brazier)
- Decorative elements and polish

## Technical Notes for Integration

- Textures use **Minecraft's 16x16 pixel standard** (can be higher res, but will be downscaled)
- Models reference textures via `aerowarptics:category/name` format
- Animation is controlled via `.mcmeta` files (JSON format, not touched by artist)
- All textures should maintain **Minecraft's aesthetic** (blocky, pixel-art style)
- Transparency is fully supported for special effects

## Next Steps

1. Review all textures and models in this folder
2. Start with the **Ship Compass** redesign (highest priority)
3. Create custom textures matching the mod's aesthetic
4. Replace corresponding PNG files in this folder
5. Return the completed folder to the dev team

For any questions about specific textures or models, refer to the model JSON files which show exactly how each texture is used.
