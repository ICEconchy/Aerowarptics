#!/usr/bin/env python3
"""
Writes the item models for this mod's blocks.

    python tools/geo_item_models.py

Two kinds, and which kind a block gets is decided by how the block is drawn in the world:

  * A block with a real baked model is held as that model. An Astrolabe cell is a brass panel and the
    panel has geometry, so the item is the panel.
  * A block drawn entirely by GeckoLib has no baked model to hold, so its item declares
    `builtin/entity` - the vanilla marker meaning "code draws this" - and `GeoBlockItemRenderer`
    draws it from the same geometry, skin and idle animation the block uses. What is left in the JSON
    is the particle texture and the display transforms, and those are the reason this is generated:
    eight files needing one identical block of transforms is eight chances to mistype one, and the
    symptom is a machine sitting at the wrong angle in one slot out of eight.

The transforms are vanilla's own `block/block` values, so these sit in a hotbar exactly like every
other block in the game.
"""

import json
import os

OUT = os.path.join("src", "main", "resources", "assets", "aerowarptics", "models", "item")

# Straight out of assets/minecraft/models/block/block.json.
DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                              "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                             "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                              "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0],
                             "scale": [0.4, 0.4, 0.4]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}

# Drawn by GeckoLib: item name -> the block texture its break particles and its model use.
GEO_RENDERED = {
    "rift_drive_mk_i": "rift_drive_mk_i",
    "rift_drive_mk_ii": "rift_drive_mk_ii",
    "rift_drive_mk_iii": "rift_drive_mk_iii",
    "rift_drive_singularity": "rift_drive_singularity",
    "rift_drive_creative": "rift_drive_creative",
    "warp_anchor": "warp_anchor",
    "spatial_siphon": "spatial_siphon",
    "rift_chute": "rift_chute",
    "rift_modulator": "rift_modulator",
}

# Drawn from a baked model: item name -> the block model it is held as.
MODELLED = {
    "astrolabe": "aerowarptics:block/astrolabe",
    "rift_probe": "aerowarptics:block/rift_probe",
    "rift_gate": "aerowarptics:block/rift_gate",
    "rift_gate_frame": "aerowarptics:block/rift_gate_frame",
}


def write(name, model):
    path = os.path.join(OUT, name + ".json")
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(model, handle, indent=2)
        handle.write("\n")
    return path


def main():
    os.makedirs(OUT, exist_ok=True)
    print("item models:")

    for name, texture in sorted(GEO_RENDERED.items()):
        write(name, {
            "parent": "builtin/entity",
            "gui_light": "side",
            "textures": {"particle": "aerowarptics:block/" + texture},
            "display": DISPLAY,
        })
        print(f"    {name+'.json':30} drawn by GeckoLib")

    for name, model in sorted(MODELLED.items()):
        write(name, {"parent": model})
        print(f"    {name+'.json':30} {model}")


if __name__ == "__main__":
    main()
