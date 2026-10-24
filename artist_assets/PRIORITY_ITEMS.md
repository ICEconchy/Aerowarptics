# Priority Items - Vanilla Textures to Replace

## Critical: Ship Compass System

### Overview
The Ship Compass currently uses Minecraft's vanilla compass texture as its base. This needs a complete redesign to fit the AeroWarptics aesthetic.

### Files to Modify

**Item Model (Base)**: `models/items/ship_compass.json`
- Uses: `minecraft:item/compass_16` (vanilla compass texture)
- Needs: Custom compass design

**Rotation Variants** (32 total):
- `models/items/ship_compass_00.json` through `ship_compass_31.json`
- Each references a corresponding vanilla compass rotation state
- All will use the new custom compass base

**Badge Overlay** (ALREADY DONE):
- `textures/items/ship_compass_badge.png` - Custom badge overlay already created
- This overlays on top of the compass base texture

### Technical Details

The compass system works as follows:
1. Layer 0: Custom compass base texture (needs to be created)
   - Should replace the vanilla compass aesthetic
   - Must work with 32 rotation states (0-31)
   - Size: 16x16 pixels

2. Layer 1: Badge overlay (already custom)
   - `ship_compass_badge.png` - decorative badge
   - Composited on top of the base compass

### Design Notes

- **Style**: Should match AeroWarptics' sci-fi/magical aesthetic
- **Color scheme**: Fits with the mod's rift/warp theme
- **Functionality**: Maintains the rotating compass needle visual
- **Compatibility**: Must work with the existing badge system

### What the Artist Receives

When you look at `ship_compass_badge.png`, you'll see the style of overlay that's already been created. The compass base should complement this design.

---

## Secondary: Vanilla Model Parent Replacements

These blocks use vanilla Minecraft model parents but could benefit from custom models:

| Block | Current Parent | Status | Notes |
|-------|----------------|--------|-------|
| warp_glass | `minecraft:block/cube_all` | Custom texture ✓ | Simple cube, texture exists |
| rift_casing | `minecraft:block/cube_all` | Custom texture ✓ | Simple cube, texture exists |
| spatial_siphon | `minecraft:block/cube_all` | Custom texture ✓ | Simple cube, texture exists |
| rift_coupler | `minecraft:block/cube_all` | Custom texture ✓ | Custom texture done |
| drive_extender | `minecraft:block/cube_all` | Custom texture ✓ | Custom texture done |

**Note**: The above blocks technically work as-is since they have custom textures. Custom models would only be needed if the geometry needs to be more complex than a simple cube.

---

## Summary

**Must Do:**
- [ ] Ship Compass base texture (high priority)
- [ ] Ensure all 32 rotation variants work correctly

**Nice to Have:**
- [ ] Custom models for complex blocks
- [ ] Enhanced visual effects for key structures

**Testing:**
- Test compass rotations in-game
- Verify badge overlay composites correctly
- Check transparency and shading
