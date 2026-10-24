# Quick Start Guide for Artist

Welcome! This folder contains all the textures and models for the AeroWarptics Minecraft mod that need to be redrawn or enhanced.

## What's In This Folder

```
artist_assets/
├── textures/
│   ├── blocks/          # 49 block textures
│   └── items/           # 25 item textures  
├── models/
│   ├── blocks/          # 37 block model definitions
│   └── items/           # 80 item model definitions
├── README.md            # Complete overview (START HERE)
├── PRIORITY_ITEMS.md    # What needs to be done first
├── MODEL_TEXTURE_MAPPING.md  # Which models use which textures
└── QUICKSTART.md        # This file
```

## 3-Step Getting Started

### 1. Understand the Project
Read `README.md` to understand:
- What AeroWarptics is (Minecraft mod with warp/teleportation)
- Texture specifications (16x16 pixels, PNG format)
- Animation system (how `.mcmeta` files work)
- Priority list for what to work on first

### 2. Identify What Needs Redrawing
Check `PRIORITY_ITEMS.md` for:
- **HIGH PRIORITY**: Ship Compass (uses vanilla compass, needs complete redesign)
- **SECONDARY**: Blocks that could use custom models or enhanced textures

### 3. Reference the Mapping
Use `MODEL_TEXTURE_MAPPING.md` to:
- Understand how each model file references its texture
- See which textures go with which models
- Identify animated textures vs static textures

---

## Key Facts

### Ship Compass - HIGH PRIORITY ⭐
- **Current state**: Uses vanilla Minecraft compass texture
- **What's done**: Badge overlay (`ship_compass_badge.png`) - reference this for style
- **What you need to create**: Custom compass base texture
- **Format**: 16x16 PNG (or larger if needed)
- **Special requirement**: Works with 32 rotation states (compass_00.png through compass_31.png)
- **Impact**: High - this is the most visible change needed

### Everything Else
- Most textures are already custom created
- Can be enhanced/polished for better quality
- Models are already defined
- No urgent changes needed unless you want to improve them

---

## Technical Notes

### Minecraft Texture Standards
- **Size**: 16x16 pixels (Minecraft standard)
- **Color**: RGB + Alpha transparency
- **Format**: PNG only
- **Style**: Blocky, pixel-art aesthetic

### How Models Work
Models (JSON files) are like instruction sheets:
```json
{
  "textures": {
    "all": "aerowarptics:block/my_texture"
  }
}
```
This tells Minecraft: "Use the file `textures/blocks/my_texture.png` for this block"

**You don't need to modify the JSON files** - just replace/update the PNG texture files.

### Animated Textures
Some textures animate in-game (e.g., rift_portal.png). The animation is controlled by `.mcmeta` files.
- **Don't modify `.mcmeta` files** - animation settings are already configured
- Just update the PNG file
- The animation will automatically apply

---

## File Organization

When you're done redrawing:
1. Replace PNG files in their original locations
2. Keep the same filenames (models reference them by name)
3. Keep .mcmeta files unchanged
4. Return the entire folder to the dev team

### Example Workflow
```
1. Open: artist_assets/textures/items/ship_compass_badge.png (reference)
2. Create: New compass base texture
3. Save as: artist_assets/textures/items/ship_compass_base.png
4. Update models to reference it (dev team will do this)
```

---

## Style Reference

### Existing Custom Textures
Browse the textures to understand the mod's visual style:
- Look at `rift_drive_mk_i.png` - shows the sci-fi/magical aesthetic
- Check `astrolabe.png` - shows the ancient compass style
- Review `stormglass.png` - shows glowing/dynamic elements
- Study `ship_compass_badge.png` - your badge overlay to match

### Color Palette
- Cosmic blues and purples (rifts/warps)
- Metallic silvers and grays (machinery)
- Magical glows and effects (enchantment)
- Accents in gold/brass (decorative elements)

---

## Questions & Clarifications

### "What resolution should I work at?"
- **Minimum**: 16x16 pixels (Minecraft standard)
- **Recommended**: Start at 16x16, can scale up if you need more detail
- The mod will display at 16x16 by default

### "Do I need to understand JSON?"
- **No** - Just update the PNG files
- Models are already configured
- Dev team handles the code integration

### "What about the ship compass?
- **Layer 0**: Custom compass base (YOU create this)
- **Layer 1**: Badge overlay (ALREADY exists, reference it)
- They composite together in-game

### "Can I improve other textures?"
- **Absolutely!** If you want to enhance any texture, go ahead
- The ones in this folder are the priority list
- But quality improvements are always welcome

---

## Next Steps

1. **Read** `README.md` thoroughly (10 min read)
2. **Review** `PRIORITY_ITEMS.md` to understand priorities
3. **Browse** the texture files to understand the style
4. **Start** with the Ship Compass (highest priority)
5. **Reference** `MODEL_TEXTURE_MAPPING.md` when unsure which textures go with which models

---

## Delivery Format

When you're done:
1. Return the entire `artist_assets/` folder with updated PNG files
2. Keep all filenames the same
3. Keep the folder structure intact
4. Don't modify .json or .mcmeta files (dev team handles those)

That's it! The dev team will integrate your textures back into the mod.

---

## Support

- Reference `MODEL_TEXTURE_MAPPING.md` for any "which texture goes where?" questions
- Check `README.md` for detailed technical info
- Look at existing textures in the folders for style guidance

Good luck! Looking forward to seeing your artwork in the mod! 🎨
