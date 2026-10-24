#!/usr/bin/env python3
"""
Writes the Spatial Wave Detector's two block models and its blockstate. Its textures are the artist's
(2026-10-21); this script once painted them and no longer writes any.

    python tools/wave_detector_model.py

A full cube, unlike the stabiliser, because the detector's range is set with Create's value box on
its sides and that box sits on the face of a full block - a waisted model would leave it floating in
the air beside the drum.

Two looks, quiet and pulsing, each with its own side and top texture. The block state carries the signal
strength (POWER, 0-15), and
every strength above nought shares the lit model - the block's light level is what grades with it,
see AWBlocks.SPATIAL_WAVE_DETECTOR. So the blockstate lists sixteen variants over two models.
"""

import json
import os

ASSETS = os.path.join("src", "main", "resources", "assets", "aerowarptics")
MODELS = os.path.join(ASSETS, "models", "block")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")

NAME = "spatial_wave_detector"


def model(lit):
    suffix = "_on" if lit else ""
    return {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "side": f"aerowarptics:block/{NAME}_side{suffix}",
            "top": f"aerowarptics:block/{NAME}_top{suffix}",
            "bottom": f"aerowarptics:block/{NAME}_bottom",
        },
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")


def main():
    write_json(os.path.join(MODELS, NAME + ".json"), model(False))
    write_json(os.path.join(MODELS, NAME + "_on.json"), model(True))
    print(f"  models/block/{NAME}.json, {NAME}_on.json")

    variants = {}
    for power in range(16):
        variants[f"power={power}"] = {"model": f"aerowarptics:block/{NAME}{'_on' if power else ''}"}
    write_json(os.path.join(BLOCKSTATES, NAME + ".json"), {"variants": variants})
    print(f"  blockstates/{NAME}.json")


if __name__ == "__main__":
    main()
