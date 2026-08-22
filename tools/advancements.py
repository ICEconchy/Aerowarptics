#!/usr/bin/env python3
"""
Builds the advancement tree under data/aerowarptics/advancement/ and its language entries.

The tree is written here rather than as eleven hand-kept JSON files because every entry needs the
same five things said the same way, and because the titles live in a different file from the
advancements that use them. Split across two formats by hand, they drift; declared once, they
cannot.

    python tools/advancements.py

`AdvancementTest` checks the result against the language file and against the triggers the mod
actually registers, so a renamed trigger or a missing title fails the build rather than shipping as
an advancement nobody can earn.
"""

import json
import os

MOD = "aerowarptics"
ADVANCEMENTS = os.path.join("src", "main", "resources", "data", MOD, "advancement")
LANG = os.path.join("src", "main", "resources", "assets", MOD, "lang", "en_us.json")
LANG_PREFIX = f"advancements.{MOD}."
BACKGROUND = f"{MOD}:textures/block/rift_gate_frame.png"


def has_item(*items):
    """The vanilla "it is in your inventory" trigger, which covers crafting it or being given it."""
    return {
        "trigger": "minecraft:inventory_changed",
        "conditions": {"items": [{"items": list(items) if len(items) > 1 else items[0]}]},
    }


def warped(**conditions):
    return {"trigger": f"{MOD}:warp_completed", "conditions": conditions}


def gate_travel(**conditions):
    return {"trigger": f"{MOD}:gate_travel", "conditions": conditions}


DRIVES = [f"{MOD}:rift_drive_mk_i", f"{MOD}:rift_drive_mk_ii",
          f"{MOD}:rift_drive_mk_iii", f"{MOD}:rift_drive_singularity"]

# id -> (parent, icon, frame, title, description, {criterion: trigger})
TREE = {
    "root": (
        None, f"{MOD}:rift_core", "task",
        "Folding Space",
        "Craft a Rift Core, and stop treating the distance between two places as a fact",
        {"rift_core": has_item(f"{MOD}:rift_core")},
    ),
    "warp_anchor": (
        "root", f"{MOD}:warp_anchor", "task",
        "You Are Here",
        "Obtain a Warp Anchor. An airship can only be sent somewhere that has been marked",
        {"warp_anchor": has_item(f"{MOD}:warp_anchor")},
    ),
    "astrolabe": (
        "warp_anchor", f"{MOD}:astrolabe", "task",
        "Dead Reckoning",
        "Obtain an Astrolabe, and see the ground before committing a hull to it",
        {"astrolabe": has_item(f"{MOD}:astrolabe")},
    ),
    "rift_drive": (
        "root", f"{MOD}:rift_drive_mk_i", "task",
        "Engine of Elsewhere",
        "Build a Rift Drive. It moves the ship it is built into, and nothing it is merely near",
        {"rift_drive": has_item(*DRIVES)},
    ),
    "first_warp": (
        "rift_drive", f"{MOD}:rift_drive_mk_ii", "task",
        "Somewhere Else",
        "Ride an airship through a rift and arrive in one piece",
        {"warped": warped()},
    ),
    "long_haul": (
        "first_warp", f"{MOD}:rift_drive_mk_iii", "goal",
        "The Long Way Round",
        "Cross two thousand blocks in a single warp",
        {"warped_far": warped(distance={"min": 2000.0})},
    ),
    "spatial_siphon": (
        "first_warp", f"{MOD}:spatial_siphon", "task",
        "Wringing It Out",
        "Obtain a Spatial Siphon, and keep whatever a rift sheds on the way through",
        {"spatial_siphon": has_item(f"{MOD}:spatial_siphon")},
    ),
    "singularity": (
        "long_haul", f"{MOD}:rift_drive_singularity", "challenge",
        "Point of No Return",
        "Build a Singularity drive, and buy its reach with an unsteady arrival",
        {"singularity": has_item(f"{MOD}:rift_drive_singularity")},
    ),
    "rift_chute": (
        "spatial_siphon", f"{MOD}:rift_chute", "task",
        "Cargo Without a Dock",
        "Obtain a Rift Chute, and stop trying to line a moving ship up with a stationary belt",
        {"rift_chute": has_item(f"{MOD}:rift_chute")},
    ),
    "rift_probe": (
        "spatial_siphon", f"{MOD}:rift_probe", "task",
        "Uncharted",
        "Obtain a Rift Probe, and stop being limited to places you have already walked",
        {"rift_probe": has_item(f"{MOD}:rift_probe")},
    ),
    "blind_jump": (
        "rift_probe", f"{MOD}:rift_probe", "challenge",
        "Nobody Has Been Here",
        "Warp to a fix a Rift Probe found, rather than to an anchor somebody placed",
        {"blind": warped(fix=True)},
    ),
    "rift_gate": (
        "root", f"{MOD}:rift_gate", "task",
        "Open the Door",
        "Build a Rift Gate controller. On its own it goes nowhere - there has to be another",
        {"rift_gate": has_item(f"{MOD}:rift_gate")},
    ),
    "gate_traveller": (
        "rift_gate", f"{MOD}:rift_gate_frame", "task",
        "Step Through",
        "Walk into a dialled Rift Gate and come out somewhere else",
        {"on_foot": gate_travel(vehicle=False)},
    ),
    "gate_haulier": (
        "gate_traveller", f"{MOD}:rift_gate", "goal",
        "Freight Forward",
        "Take a vehicle through a Rift Gate. Both ends have to be wide enough for it",
        {"aboard": gate_travel(vehicle=True)},
    ),
}


def build(name, entry):
    parent, icon, frame, title, description, criteria = entry
    display = {
        "icon": {"count": 1, "id": icon},
        "title": {"translate": f"{LANG_PREFIX}{name}.title"},
        "description": {"translate": f"{LANG_PREFIX}{name}.description"},
        "frame": frame,
        "show_toast": True,
        "announce_to_chat": True,
        "hidden": False,
    }
    if parent is None:
        display["background"] = BACKGROUND

    advancement = {
        "display": display,
        "criteria": criteria,
        # Every criterion required. None of these have alternative routes to them.
        "requirements": [[key] for key in criteria],
        "sends_telemetry_event": False,
    }
    if parent is not None:
        advancement["parent"] = f"{MOD}:{parent}"
    return advancement


def main():
    os.makedirs(ADVANCEMENTS, exist_ok=True)
    for name, entry in TREE.items():
        path = os.path.join(ADVANCEMENTS, f"{name}.json")
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(build(name, entry), handle, indent=2, ensure_ascii=False)
            handle.write("\n")

    lang = json.load(open(LANG, encoding="utf-8"))
    lang = {k: v for k, v in lang.items() if not k.startswith(LANG_PREFIX)}
    for name, entry in TREE.items():
        lang[f"{LANG_PREFIX}{name}.title"] = entry[3]
        lang[f"{LANG_PREFIX}{name}.description"] = entry[4]
    with open(LANG, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(dict(sorted(lang.items())), handle, indent=2, ensure_ascii=False)
        handle.write("\n")

    print(f"wrote {len(TREE)} advancements into {ADVANCEMENTS}")
    for name, entry in TREE.items():
        print(f"  {name:16} <- {entry[0] or '(root)'}")


if __name__ == "__main__":
    main()
