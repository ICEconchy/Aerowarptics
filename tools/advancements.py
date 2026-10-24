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


def fissure_closed(**conditions):
    return {"trigger": f"{MOD}:fissure_closed", "conditions": conditions}


def ship_summoned(**conditions):
    return {"trigger": f"{MOD}:ship_summoned", "conditions": conditions}


def flotilla_fold(**conditions):
    return {"trigger": f"{MOD}:flotilla_fold", "conditions": conditions}


def fold_completed(**conditions):
    return {"trigger": f"{MOD}:fold_completed", "conditions": conditions}


def collapse_finished(**conditions):
    return {"trigger": f"{MOD}:collapse_finished", "conditions": conditions}


def storm_called(**conditions):
    return {"trigger": f"{MOD}:storm_called", "conditions": conditions}


def train_through_gate(**conditions):
    return {"trigger": f"{MOD}:train_through_gate", "conditions": conditions}


def plot_struck(**conditions):
    return {"trigger": f"{MOD}:plot_struck", "conditions": conditions}


def ward_held(**conditions):
    return {"trigger": f"{MOD}:ward_held", "conditions": conditions}


def wellhead(**conditions):
    return {"trigger": f"{MOD}:wellhead", "conditions": conditions}


def sunderer(**conditions):
    return {"trigger": f"{MOD}:sunderer", "conditions": conditions}


def turned_aside(**conditions):
    return {"trigger": f"{MOD}:turned_aside", "conditions": conditions}


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
    "handbook": (
        "root", f"{MOD}:handbook", "task",
        "Ship's Papers",
        "Craft the Navigator's Handbook, and read how the rest of this is meant to go together",
        {"handbook": has_item(f"{MOD}:handbook")},
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
    "rift_modulator": (
        "rift_drive", f"{MOD}:rift_modulator", "task",
        "House Colours",
        "Obtain a Rift Modulator, and stop flying whatever colour your tier happened to come in",
        {"rift_modulator": has_item(f"{MOD}:rift_modulator")},
    ),
    "rift_coupler": (
        "rift_drive", f"{MOD}:rift_coupler", "task",
        "Signal Book",
        "Obtain a Rift Coupler, and stop folding space on your own",
        {"rift_coupler": has_item(f"{MOD}:rift_coupler")},
    ),
    "line_abreast": (
        "rift_coupler", f"{MOD}:rift_coupler", "task",
        "Line Abreast",
        "Fold with a flotilla: more than one ship through the same wake",
        {"folded": flotilla_fold()},
    ),
    "fleet_in_being": (
        "line_abreast", f"{MOD}:rift_coupler", "goal",
        "Fleet in Being",
        "Fold with as many escorts as your flagship's drive can lead, and bring every one of them out",
        {"full": flotilla_fold(full=True)},
    ),
    "nobody_left_behind": (
        "line_abreast", f"{MOD}:rift_coupler", "challenge",
        "Nobody Left Behind",
        "Fold a flotilla of three ships or more with every one of them in its slot at the far end",
        {"clean": flotilla_fold(ships={"min": 3}, clean=True)},
    ),
    "blind_jump": (
        "rift_probe", f"{MOD}:rift_probe", "challenge",
        "Nobody Has Been Here",
        "Warp to a fix a Rift Probe found, rather than to an anchor somebody placed",
        {"blind": warped(fix=True)},
    ),
    "rift_goggles": (
        "root", f"{MOD}:rift_goggles", "task",
        "Second Sight",
        "Craft Rift Infused Goggles. There is something in this world that nothing else will show you",
        {"rift_goggles": has_item(f"{MOD}:rift_goggles")},
    ),
    "fissure_closed": (
        "rift_goggles", f"{MOD}:spatial_siphon", "goal",
        "Sealed Over",
        "Empty a Rift Fissure with a Spatial Siphon, and be there when it closes for good",
        {"closed": fissure_closed()},
    ),
    "wellhead_formed": (
        "rift_goggles", f"{MOD}:rift_collar", "goal",
        "Tapped the Wound",
        "Build a frame round a Rift Fissure and set a Rift Collar in its side, so the tear can be made to pay",
        {"formed": wellhead(event="formed")},
    ),
    "wellhead_scrammed": (
        "wellhead_formed", "minecraft:lever", "goal",
        "Not Today Either",
        "Scram a Rift Wellhead that had gone critical, and throw away what it was digesting to keep the rest",
        {"scrammed": wellhead(event="scrammed")},
    ),
    "wellhead_meltdown": (
        "wellhead_formed", f"{MOD}:scarred_stone", "challenge",
        "It Was Like That When I Found It",
        "Be standing near enough to a Rift Wellhead's meltdown to see it, and still be standing afterwards",
        {"meltdown": wellhead(event="meltdown")},
    ),
    "where_it_went_wrong": (
        "wellhead_formed", f"{MOD}:rift_sunderer", "goal",
        "Where It Went Wrong",
        "Set a Rift Sunderer in a ring of gate frame and let it tear a fissure where the aperture should have been",
        {"torn": sunderer(event="torn")},
    ),
    "home_fires": (
        "where_it_went_wrong", f"{MOD}:rift_collar", "goal",
        "Home Fires",
        "Build a Rift Wellhead round a fissure you tore yourself, before it gutters shut",
        {"held": sunderer(event="held")},
    ),
    "too_thin": (
        "where_it_went_wrong", f"{MOD}:scarred_stone", "task",
        "Too Thin",
        "Be near a Rift Sunderer when it ruptures",
        {"ruptured": sunderer(event="ruptured")},
    ),
    "something_waved_back": (
        "where_it_went_wrong", "minecraft:sculk_shrieker", "challenge",
        "Something Waved Back",
        "Be there when something on the far side of a sundering reaches through",
        {"waved": sunderer(event="waved")},
    ),
    "rift_beacon": (
        "first_warp", f"{MOD}:rift_beacon", "task",
        "Whistle For It",
        "Obtain a Rift Beacon, and stop having to be aboard a ship to command it",
        {"rift_beacon": has_item(f"{MOD}:rift_beacon")},
    ),
    "ship_summoned": (
        "rift_beacon", f"{MOD}:rift_beacon", "goal",
        "Come About",
        "Call a bound airship down to the ground you are pointing at",
        {"summoned": ship_summoned()},
    ),
    "seen_a_rift": (
        "rift_drive", f"{MOD}:opened_rift_lens", "task",
        "Seen a Rift",
        "Blow a Rift Lens through a standing rift with a fan. Everything that tears one of its own is built with a lens that has seen one",
        {"opened_lens": has_item(f"{MOD}:opened_rift_lens")},
    ),
    "crossed_the_line": (
        "first_warp", f"{MOD}:folded_warp_crystal", "goal",
        "Crossed the Line",
        "Fold something at a Collapse Cradle, burning essence to make it more than it was",
        {"folded": fold_completed()},
    ),
    "heart_of_a_scar": (
        "fissure_closed", f"{MOD}:scar_heart", "goal",
        "Heart of a Scar",
        "Take the Scar Heart a sealed fissure leaves behind: the one thing a Singularity can fall into",
        {"scar_heart": has_item(f"{MOD}:scar_heart")},
    ),
    "declines_to_be_precise": (
        "heart_of_a_scar", f"{MOD}:singularity_core", "challenge",
        "It Declines to Be Precise",
        "Collapse a Singularity Core so unsteadily that it flings itself clear of the cradle",
        {"flung": collapse_finished(outcome="flung")},
    ),
    "over_the_horizon": (
        "crossed_the_line", f"{MOD}:rift_plotter", "goal",
        "Over the Horizon",
        "Send something through a Rift Plotter's rift and out over its target, wherever in the world that is",
        {"struck": plot_struck()},
    ),
    "not_today": (
        "over_the_horizon", f"{MOD}:rift_ward", "goal",
        "Not Today",
        "Stand by a Rift Ward as it turns a stranger's rift aside from the ground it holds",
        {"held": ward_held()},
    ),
    "shown_the_door": (
        "first_warp", f"{MOD}:rift_ward", "task",
        "Shown the Door",
        "Warp at somebody's base and come out at the edge of it instead, courtesy of a Rift Ward",
        {"turned": turned_aside()},
    ),
    "stormcaller": (
        "root", f"{MOD}:stormglass", "goal",
        "Stormcaller",
        "Break a Stormglass and call a Rift Storm down on everybody",
        {"called": storm_called()},
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
    "mind_the_gap": (
        "gate_traveller", "create:track", "goal",
        "Mind the Gap",
        "Ride a train through a Rift Gate. Run track into the gate and it comes out of the one the gate is routed to",
        {"rode": train_through_gate()},
    ),
}


# Advancements nobody should be told about until they have happened to them.
HIDDEN = {"something_waved_back"}


def build(name, entry):
    parent, icon, frame, title, description, criteria = entry
    display = {
        "icon": {"count": 1, "id": icon},
        "title": {"translate": f"{LANG_PREFIX}{name}.title"},
        "description": {"translate": f"{LANG_PREFIX}{name}.description"},
        "frame": frame,
        "show_toast": True,
        "announce_to_chat": True,
        "hidden": name in HIDDEN,
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
