#!/usr/bin/env python3
"""
Writes the Berth's four block models and its blockstate. Its textures are the artist's (2026-10-21).

    python tools/berth_model.py

A Berth is a bollard at the edge of a slip: a full cube with a face, which looks back at whoever set it
down, and three plain sides. The face carries the one thing a harbour is read by - a lamp that says what
is in the slip - so there are four faces and four models, one for each thing a berth can be doing, and
the blockstate turns each to the four ways a berth can face. Generated together, like the Wave
Detector's, so the state names here and in BerthState cannot drift apart: a state this file does not
name is a berth drawn as the missing-model cube.

The faces are berth_front_<state>.png, one per state, each with its own lamp; the sides, top and bottom
are shared. This script once painted them too; the artist's replaced them, and nothing here writes a
texture any more.
"""

import json
import os

ASSETS = os.path.join("src", "main", "resources", "assets", "aerowarptics")
MODELS = os.path.join(ASSETS, "models", "block")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")

NAME = "berth"

# What a berth can be doing, exactly as BerthState spells it, each with its own face.
STATES = ("empty", "reserved", "drawing", "moored")

# Blockstate y rotations for a model whose face is drawn on its north side.
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")


def model_name(state):
    """The empty berth is the block's own model, which is the one its item is held as."""
    return NAME if state == "empty" else f"{NAME}_{state}"


def main():
    for state in STATES:
        write_json(os.path.join(MODELS, model_name(state) + ".json"), {
            "parent": "minecraft:block/orientable_with_bottom",
            "textures": {
                "top": f"aerowarptics:block/{NAME}_top",
                "bottom": f"aerowarptics:block/{NAME}_bottom",
                "side": f"aerowarptics:block/{NAME}_side",
                "front": f"aerowarptics:block/{NAME}_front_{state}",
            },
        })
        print(f"  models/block/{model_name(state)}.json")

    variants = {}
    for facing, turn in FACINGS.items():
        for state in STATES:
            variant = {"model": f"aerowarptics:block/{model_name(state)}"}
            if turn:
                variant["y"] = turn
            variants[f"facing={facing},state={state}"] = variant
    write_json(os.path.join(BLOCKSTATES, NAME + ".json"), {"variants": variants})
    print(f"  blockstates/{NAME}.json  {len(variants)} variants")


if __name__ == "__main__":
    main()
