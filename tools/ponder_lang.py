#!/usr/bin/env python3
"""
Writes the Ponder scene entries into assets/aerowarptics/lang/en_us.json.

Ponder resolves scene text through the language file and nothing else: outside its editing mode,
`PonderLocalization.getSpecific` calls straight into `I18n.get`, with no fallback to the string
written in the storyboard. A missing entry is therefore not a missing translation, it is a raw
`aerowarptics.ponder.rift_gate.text_3` shown to the player.

Worse, the keys are positional. `text_1`, `text_2` and so on are handed out in the order the
`.text(...)` calls run, so inserting a sentence in the middle of a scene silently renumbers every
sentence after it. Keeping the two in step by hand is a losing game, so this reads the storyboards
and writes the entries:

    python tools/ponder_lang.py

`PonderTextTest` runs the same parse and fails the build when the language file has drifted, which
is what stops a scene edit shipping without a regenerate.
"""

import collections
import json
import os
import re

SCENE_DIR = os.path.join("src", "main", "java", "uk", "co", "iceconchy", "aerowarptics",
                         "compat", "ponder", "scene")
LANG = os.path.join("src", "main", "resources", "assets", "aerowarptics", "lang", "en_us.json")
PREFIX = "aerowarptics.ponder."

# A Java string literal with no escapes in it, which is all these storyboards use.
TITLE = re.compile(r'\bscene\.title\(\s*"([^"\\]+)"\s*,\s*"([^"\\]+)"\s*\)')
TEXT = re.compile(r'\.text\(\s*"([^"\\]+)"\s*\)')


def scene_entries(root="."):
    """Every ponder lang key the storyboards imply, in the order they are handed out."""
    entries = collections.OrderedDict()
    directory = os.path.join(root, SCENE_DIR)
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".java"):
            continue
        source = open(os.path.join(directory, name), encoding="utf-8").read()
        scene = None
        index = 0
        # One pass in file order: a title opens a scene, and the text calls after it belong to it.
        for match in re.finditer(f"({TITLE.pattern})|({TEXT.pattern})", source):
            if match.group(2) is not None:
                scene, header = match.group(2), match.group(3)
                index = 0
                entries[f"{PREFIX}{scene}.header"] = header
            else:
                if scene is None:
                    raise SystemExit(f"{name}: text before any scene.title(...)")
                index += 1
                entries[f"{PREFIX}{scene}.text_{index}"] = match.group(5)
    return entries


def main():
    entries = scene_entries()
    lang = json.load(open(LANG, encoding="utf-8"))
    lang = {k: v for k, v in lang.items() if not k.startswith(PREFIX)}
    lang.update(entries)

    with open(LANG, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(dict(sorted(lang.items())), handle, indent=2, ensure_ascii=False)
        handle.write("\n")

    scenes = sorted({k[len(PREFIX):].rsplit(".", 1)[0] for k in entries})
    print(f"wrote {len(entries)} ponder entries across {len(scenes)} scenes into {LANG}")
    for scene in scenes:
        lines = sum(1 for k in entries if k.startswith(f"{PREFIX}{scene}.text_"))
        print(f"  {scene:22} {lines} lines")


if __name__ == "__main__":
    main()
