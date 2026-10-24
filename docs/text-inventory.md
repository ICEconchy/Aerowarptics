# Text inventory

Every piece of player-facing text in the mod: where it is stored, where the player sees it, and what each
line is for. Written as a working list for rewriting the text. Generated from the language file, so the
"Current text" column is a snapshot - the language file is the truth.

## Where the text is stored

| What | File | Amount |
|---|---|---|
| Almost everything shown in game | `src/main/resources/assets/aerowarptics/lang/en_us.json` | 1,392 lines, all listed below |
| Ponder captions (source for the `ponder.*` lang lines) | `src/main/java/uk/co/iceconchy/aerowarptics/compat/ponder/scene/*.java` | 11 files, 20 scenes |
| Advancement names and descriptions (source for the `advancements.*` lang lines) | `tools/advancements.py` | 32 advancements |
| Config option descriptions | `src/main/java/uk/co/iceconchy/aerowarptics/AWConfig.java`, every `.comment("...")` | 240 comments |
| Admin command replies | `command/AWCommands.java`, `hold/HoldCommands.java`, `ward/WardCommands.java`, `warpnet/NetworkCommands.java`, and the dry-run report in `drive/RiftDriveBlockEntity.java` (from line 2726) | about 60 hardcoded strings |
| Mod list description | `gradle.properties`, `mod_description` | 1 sentence |
| Outside the game | `README.md`, `CHANGELOG.md`, `docs/mod-description.md`, the GitBook wiki | - |

The config comments, command replies and the mod list description are **not** in the language file and are
not listed line by line here. Config comments are written into the generated `.toml` config files for
server owners; command replies are seen only by operators running `/aerowarptics ...`.

## Before changing a line

1. **Two groups are generated.** `advancements.*` and `aerowarptics.ponder.*` are written into the language
   file by scripts. Change the source (see the table above) and re-run the script, or the edit is overwritten.
2. **Keep the placeholders.** `%s`, `%1$s`, `%2$s` are filled in by the game. Each must still appear, and
   numbered ones keep their numbers. `LangFormatTest` checks this.
3. **Many short lines have a width limit.** Labels, buttons and status lines are measured in pixels by
   `ConsoleTextTest`, `CouplerTextTest`, `RelicTextTest`, `NetworkTextTest` and `GuidebookTest`. A longer
   replacement can fail the build; the failure message names the line and how many pixels over it is.
4. **Do not rename keys**, only the text to the right of the colon.
5. **Run `./gradlew test` when done.** It catches a broken placeholder, an over-long label, or a Ponder or
   advancement line that no longer matches its source.
6. `AGENTS.md` asks that wording stay consistent between the language file, the JEI pages and the wiki, so
   a renamed term needs changing in all three.

## Reading the tables

- **Line** is the line number in `en_us.json`.
- **Used in** is the Java class that asks for the key, where one names it directly. Blank means the key is
  built from a name at run time (a state, a failure reason, a block id) or read from a data file.
- Four keys have no reference in the code that I could find and may be unused: `gui.rift_gate.essence_short`,
  `gui.rift_navigation.cooldown`, `gui.rift_navigation.status`, `gui.rift_navigation.title`.

## 1. Long-form writing

These five groups hold nearly all of the sentences and paragraphs. If the complaint is tone, start here.

### Advancements (64)

**Seen:** the advancement toast and the AeroWarptics tab of the advancement screen. Each has a `title` (the name) and a `description` (one line saying what to do).

**Edit in:** `tools/advancements.py` (the `entry[3]` / `entry[4]` strings), then run `python tools/advancements.py`. That script overwrites these lang lines, so a change made only in `en_us.json` is lost the next time it runs.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 2 | `advancements.aerowarptics.astrolabe.description` | Obtain an Astrolabe, and see the ground before committing a hull to it | The line under the name: what earns it |  |
| 3 | `advancements.aerowarptics.astrolabe.title` | Dead Reckoning | Advancement name, on the toast and in the advancement screen |  |
| 4 | `advancements.aerowarptics.blind_jump.description` | Warp to a fix a Rift Probe found, rather than to an anchor somebody placed | The line under the name: what earns it |  |
| 5 | `advancements.aerowarptics.blind_jump.title` | Nobody Has Been Here | Advancement name, on the toast and in the advancement screen |  |
| 6 | `advancements.aerowarptics.crossed_the_line.description` | Fold something at a Collapse Cradle, burning essence to make it more than it was | The line under the name: what earns it |  |
| 7 | `advancements.aerowarptics.crossed_the_line.title` | Crossed the Line | Advancement name, on the toast and in the advancement screen |  |
| 8 | `advancements.aerowarptics.declines_to_be_precise.description` | Collapse a Singularity Core so unsteadily that it flings itself clear of the cradle | The line under the name: what earns it |  |
| 9 | `advancements.aerowarptics.declines_to_be_precise.title` | It Declines to Be Precise | Advancement name, on the toast and in the advancement screen |  |
| 10 | `advancements.aerowarptics.first_warp.description` | Ride an airship through a rift and arrive in one piece | The line under the name: what earns it |  |
| 11 | `advancements.aerowarptics.first_warp.title` | Somewhere Else | Advancement name, on the toast and in the advancement screen |  |
| 12 | `advancements.aerowarptics.fissure_closed.description` | Empty a Rift Fissure with a Spatial Siphon, and be there when it closes for good | The line under the name: what earns it |  |
| 13 | `advancements.aerowarptics.fissure_closed.title` | Sealed Over | Advancement name, on the toast and in the advancement screen |  |
| 14 | `advancements.aerowarptics.fleet_in_being.description` | Fold with as many escorts as your flagship's drive can lead, and bring every one of them out | The line under the name: what earns it |  |
| 15 | `advancements.aerowarptics.fleet_in_being.title` | Fleet in Being | Advancement name, on the toast and in the advancement screen |  |
| 16 | `advancements.aerowarptics.gate_haulier.description` | Take a vehicle through a Rift Gate. Both ends have to be wide enough for it | The line under the name: what earns it |  |
| 17 | `advancements.aerowarptics.gate_haulier.title` | Freight Forward | Advancement name, on the toast and in the advancement screen |  |
| 18 | `advancements.aerowarptics.gate_traveller.description` | Walk into a dialled Rift Gate and come out somewhere else | The line under the name: what earns it |  |
| 19 | `advancements.aerowarptics.gate_traveller.title` | Step Through | Advancement name, on the toast and in the advancement screen |  |
| 20 | `advancements.aerowarptics.handbook.description` | Craft the Navigator's Handbook, and read how the rest of this is meant to go together | The line under the name: what earns it |  |
| 21 | `advancements.aerowarptics.handbook.title` | Ship's Papers | Advancement name, on the toast and in the advancement screen |  |
| 22 | `advancements.aerowarptics.heart_of_a_scar.description` | Take the Scar Heart a sealed fissure leaves behind: the one thing a Singularity can fall into | The line under the name: what earns it |  |
| 23 | `advancements.aerowarptics.heart_of_a_scar.title` | Heart of a Scar | Advancement name, on the toast and in the advancement screen |  |
| 24 | `advancements.aerowarptics.line_abreast.description` | Fold with a flotilla: more than one ship through the same wake | The line under the name: what earns it |  |
| 25 | `advancements.aerowarptics.line_abreast.title` | Line Abreast | Advancement name, on the toast and in the advancement screen |  |
| 26 | `advancements.aerowarptics.long_haul.description` | Cross two thousand blocks in a single warp | The line under the name: what earns it |  |
| 27 | `advancements.aerowarptics.long_haul.title` | The Long Way Round | Advancement name, on the toast and in the advancement screen |  |
| 28 | `advancements.aerowarptics.nobody_left_behind.description` | Fold a flotilla of three ships or more with every one of them in its slot at the far end | The line under the name: what earns it |  |
| 29 | `advancements.aerowarptics.nobody_left_behind.title` | Nobody Left Behind | Advancement name, on the toast and in the advancement screen |  |
| 30 | `advancements.aerowarptics.not_today.description` | Stand by a Rift Ward as it turns a stranger's rift aside from the ground it holds | The line under the name: what earns it |  |
| 31 | `advancements.aerowarptics.not_today.title` | Not Today | Advancement name, on the toast and in the advancement screen |  |
| 32 | `advancements.aerowarptics.over_the_horizon.description` | Send something through a Rift Plotter's rift and out over its target, wherever in the world that is | The line under the name: what earns it |  |
| 33 | `advancements.aerowarptics.over_the_horizon.title` | Over the Horizon | Advancement name, on the toast and in the advancement screen |  |
| 34 | `advancements.aerowarptics.rift_beacon.description` | Obtain a Rift Beacon, and stop having to be aboard a ship to command it | The line under the name: what earns it |  |
| 35 | `advancements.aerowarptics.rift_beacon.title` | Whistle For It | Advancement name, on the toast and in the advancement screen |  |
| 36 | `advancements.aerowarptics.rift_chute.description` | Obtain a Rift Chute, and stop trying to line a moving ship up with a stationary belt | The line under the name: what earns it |  |
| 37 | `advancements.aerowarptics.rift_chute.title` | Cargo Without a Dock | Advancement name, on the toast and in the advancement screen |  |
| 38 | `advancements.aerowarptics.rift_coupler.description` | Obtain a Rift Coupler, and stop folding space on your own | The line under the name: what earns it |  |
| 39 | `advancements.aerowarptics.rift_coupler.title` | Signal Book | Advancement name, on the toast and in the advancement screen |  |
| 40 | `advancements.aerowarptics.rift_drive.description` | Build a Rift Drive. It moves the ship it is built into, and nothing it is merely near | The line under the name: what earns it |  |
| 41 | `advancements.aerowarptics.rift_drive.title` | Engine of Elsewhere | Advancement name, on the toast and in the advancement screen |  |
| 42 | `advancements.aerowarptics.rift_gate.description` | Build a Rift Gate controller. On its own it goes nowhere - there has to be another | The line under the name: what earns it |  |
| 43 | `advancements.aerowarptics.rift_gate.title` | Open the Door | Advancement name, on the toast and in the advancement screen |  |
| 44 | `advancements.aerowarptics.rift_goggles.description` | Craft Rift Infused Goggles. There is something in this world that nothing else will show you | The line under the name: what earns it |  |
| 45 | `advancements.aerowarptics.rift_goggles.title` | Second Sight | Advancement name, on the toast and in the advancement screen |  |
| 46 | `advancements.aerowarptics.rift_modulator.description` | Obtain a Rift Modulator, and stop flying whatever colour your tier happened to come in | The line under the name: what earns it |  |
| 47 | `advancements.aerowarptics.rift_modulator.title` | House Colours | Advancement name, on the toast and in the advancement screen |  |
| 48 | `advancements.aerowarptics.rift_probe.description` | Obtain a Rift Probe, and stop being limited to places you have already walked | The line under the name: what earns it |  |
| 49 | `advancements.aerowarptics.rift_probe.title` | Uncharted | Advancement name, on the toast and in the advancement screen |  |
| 50 | `advancements.aerowarptics.root.description` | Craft a Rift Core, and stop treating the distance between two places as a fact | The line under the name: what earns it |  |
| 51 | `advancements.aerowarptics.root.title` | Folding Space | Advancement name, on the toast and in the advancement screen |  |
| 52 | `advancements.aerowarptics.seen_a_rift.description` | Blow a Rift Lens through a standing rift with a fan. Everything that tears one of its own is built with a lens that has seen one | The line under the name: what earns it |  |
| 53 | `advancements.aerowarptics.seen_a_rift.title` | Seen a Rift | Advancement name, on the toast and in the advancement screen |  |
| 54 | `advancements.aerowarptics.ship_summoned.description` | Call a bound airship down to the ground you are pointing at | The line under the name: what earns it |  |
| 55 | `advancements.aerowarptics.ship_summoned.title` | Come About | Advancement name, on the toast and in the advancement screen |  |
| 56 | `advancements.aerowarptics.shown_the_door.description` | Warp at somebody's base and come out at the edge of it instead, courtesy of a Rift Ward | The line under the name: what earns it |  |
| 57 | `advancements.aerowarptics.shown_the_door.title` | Shown the Door | Advancement name, on the toast and in the advancement screen |  |
| 58 | `advancements.aerowarptics.singularity.description` | Build a Singularity drive, and buy its reach with an unsteady arrival | The line under the name: what earns it |  |
| 59 | `advancements.aerowarptics.singularity.title` | Point of No Return | Advancement name, on the toast and in the advancement screen |  |
| 60 | `advancements.aerowarptics.spatial_siphon.description` | Obtain a Spatial Siphon, and keep whatever a rift sheds on the way through | The line under the name: what earns it |  |
| 61 | `advancements.aerowarptics.spatial_siphon.title` | Wringing It Out | Advancement name, on the toast and in the advancement screen |  |
| 62 | `advancements.aerowarptics.stormcaller.description` | Break a Stormglass and call a Rift Storm down on everybody | The line under the name: what earns it |  |
| 63 | `advancements.aerowarptics.stormcaller.title` | Stormcaller | Advancement name, on the toast and in the advancement screen |  |
| 64 | `advancements.aerowarptics.warp_anchor.description` | Obtain a Warp Anchor. An airship can only be sent somewhere that has been marked | The line under the name: what earns it |  |
| 65 | `advancements.aerowarptics.warp_anchor.title` | You Are Here | Advancement name, on the toast and in the advancement screen |  |

### The Navigator's Handbook (164)

**Seen:** the in-game book (item `Navigator's Handbook`). A key is `guide.page.<page>.<line>`; the page list and the order of lines on each page are in `guide/GuideBook.java`.

**Edit in:** `en_us.json` directly. **Limit:** `GuidebookTest` fails if a page's lines no longer fit on the page, or a title is wider than the page. Keep replacements about the same length, or shorter.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 725 | `aerowarptics.guide.chapter.charts` | Anchors and Charts | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 726 | `aerowarptics.guide.chapter.contents` | Contents | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 727 | `aerowarptics.guide.chapter.craft` | Rift Craft | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 728 | `aerowarptics.guide.chapter.drive` | The Rift Drive | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 729 | `aerowarptics.guide.chapter.essence` | Essence and Cargo | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 730 | `aerowarptics.guide.chapter.fissures` | Fissures | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 731 | `aerowarptics.guide.chapter.flotilla` | Flotillas | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 732 | `aerowarptics.guide.chapter.gates` | Rift Gates | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 733 | `aerowarptics.guide.chapter.help` | If Nothing Happens | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 734 | `aerowarptics.guide.chapter.launch` | Launching | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 735 | `aerowarptics.guide.chapter.plotter` | The Rift Plotter | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 736 | `aerowarptics.guide.chapter.probe` | Into the Unknown | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 737 | `aerowarptics.guide.chapter.ward` | The Rift Ward | Chapter name: on the ribbon, in the contents list and in the page footer |  |
| 738 | `aerowarptics.guide.page.anchors.loaded` | An anchor in an unloaded chunk is still a destination. | Page `anchors`: an aside, set apart as a note |  |
| 739 | `aerowarptics.guide.page.anchors.name` | Name it, set access, a network and arrival height. | Page `anchors`: a numbered step (the number is added by the book) |  |
| 740 | `aerowarptics.guide.page.anchors.place` | Place a Warp Anchor where you want to arrive. | Page `anchors`: a numbered step (the number is added by the book) |  |
| 741 | `aerowarptics.guide.page.anchors.title` | Marking a Destination | Heading of the page `anchors` (chapter `charts`) |  |
| 742 | `aerowarptics.guide.page.anchors.why` | A ship can only be sent somewhere that has been marked. | Page `anchors`: body text |  |
| 743 | `aerowarptics.guide.page.chart.course` | Clicking sets the course. Nothing moves yet. | Page `chart`: an aside, set apart as a note |  |
| 744 | `aerowarptics.guide.page.chart.lay` | Lay nine Astrolabe blocks in a three-by-three square. | Page `chart`: a numbered step (the number is added by the book) |  |
| 745 | `aerowarptics.guide.page.chart.pick` | Right-click the table and pick a destination. | Page `chart`: a numbered step (the number is added by the book) |  |
| 746 | `aerowarptics.guide.page.chart.shows` | It gives distance, cost, reach, and the ground you would land on. | Page `chart`: body text |  |
| 747 | `aerowarptics.guide.page.chart.title` | The Chart Table | Heading of the page `chart` (chapter `charts`) |  |
| 748 | `aerowarptics.guide.page.chute.cost` | Each item sent spends a little essence. | Page `chute`: an aside, set apart as a note |  |
| 749 | `aerowarptics.guide.page.chute.feed` | Feed it like any Create machine. | Page `chute`: a numbered step (the number is added by the book) |  |
| 750 | `aerowarptics.guide.page.chute.pair` | Place two chutes and pair one with the other. | Page `chute`: a numbered step (the number is added by the book) |  |
| 751 | `aerowarptics.guide.page.chute.title` | The Rift Chute | Heading of the page `chute` (chapter `essence`) |  |
| 752 | `aerowarptics.guide.page.chute.what` | A chute sends items to another chute, so a moving ship need not line up with a belt. | Page `chute`: body text |  |
| 753 | `aerowarptics.guide.page.closing.draws` | It draws essence steadily, and the rift shrinks as it does. | Page `closing`: body text |  |
| 754 | `aerowarptics.guide.page.closing.place` | Stand a Spatial Siphon within a few blocks of the fissure. | Page `closing`: a numbered step (the number is added by the book) |  |
| 755 | `aerowarptics.guide.page.closing.spent` | A fissure holds more than one siphon does. Pipe the essence away as it fills. | Page `closing`: an aside, set apart as a note |  |
| 756 | `aerowarptics.guide.page.closing.title` | Closing One | Heading of the page `closing` (chapter `fissures`) |  |
| 757 | `aerowarptics.guide.page.contents.title` | Contents | Heading of the page `contents` (chapter `contents`) |  |
| 758 | `aerowarptics.guide.page.cover.aim` | A Rift Drive built into an airship opens a rift, and the whole vessel flies through it - hull, cargo and crew. | Page `cover`: body text |  |
| 759 | `aerowarptics.guide.page.cover.read` | Turn a page with the arrows below, or jump to a chapter with the ribbons above. | Page `cover`: an aside, set apart as a note |  |
| 760 | `aerowarptics.guide.page.cover.title` | Folding Space | Heading of the page `cover` (chapter `contents`) |  |
| 761 | `aerowarptics.guide.page.cover.what` | You stay on the deck for all of it. | Page `cover`: body text |  |
| 762 | `aerowarptics.guide.page.craft_collapse.cage` | Switch on stabilisers around it, evenly spaced. | Page `craft_collapse`: a numbered step (the number is added by the book) |  |
| 763 | `aerowarptics.guide.page.craft_collapse.cradle` | Its rings can only be found in scar ruins. Set it down, load a Rift Core and a Scar Heart, pipe in essence. | Page `craft_collapse`: a numbered step (the number is added by the book) |  |
| 764 | `aerowarptics.guide.page.craft_collapse.ring` | Stand offerings on depots around it, turn it hard, and give it a redstone pulse. | Page `craft_collapse`: a numbered step (the number is added by the book) |  |
| 765 | `aerowarptics.guide.page.craft_collapse.stake` | If unsteady, it flings or spits the core and hurts anyone close. Only the Scar Heart is lost. | Page `craft_collapse`: an aside, set apart as a note |  |
| 766 | `aerowarptics.guide.page.craft_collapse.title` | The Collapse | Heading of the page `craft_collapse` (chapter `craft`) |  |
| 767 | `aerowarptics.guide.page.craft_folding.arms` | Put up to sixteen items in its arms. For a Pearlescent Warp Crystal, also put one Shulker Shell per crystal in its cup. | Page `craft_folding`: a numbered step (the number is added by the book) |  |
| 768 | `aerowarptics.guide.page.craft_folding.begin` | Turn it, fill its tank, and press Begin on its panel. | Page `craft_folding`: a numbered step (the number is added by the book) |  |
| 769 | `aerowarptics.guide.page.craft_folding.compass` | A Ship's Compass comes out unbound. Sneak and use it on a Rift Drive to bind it. | Page `craft_folding`: an aside, set apart as a note |  |
| 770 | `aerowarptics.guide.page.craft_folding.title` | Folding | Heading of the page `craft_folding` (chapter `craft`) |  |
| 771 | `aerowarptics.guide.page.craft_folding.what` | As well as collapsing, the Collapse Cradle folds items for Rift Essence. | Page `craft_folding`: body text |  |
| 772 | `aerowarptics.guide.page.craft_rifting.brazier` | A powered Rift Brazier burns Rift Essence every second. | Page `craft_rifting`: an aside, set apart as a note |  |
| 773 | `aerowarptics.guide.page.craft_rifting.catalyst` | A Rift Fissure is free. A lit brazier or an open gate's pane also works. | Page `craft_rifting`: a numbered step (the number is added by the book) |  |
| 774 | `aerowarptics.guide.page.craft_rifting.fan` | Point an Encased Fan through an open rift, and put the items in its stream. | Page `craft_rifting`: a numbered step (the number is added by the book) |  |
| 775 | `aerowarptics.guide.page.craft_rifting.title` | Rifting | Heading of the page `craft_rifting` (chapter `craft`) |  |
| 776 | `aerowarptics.guide.page.craft_rifting.what` | Blow items through an open rift with an Encased Fan, the same way you would wash them. | Page `craft_rifting`: body text |  |
| 777 | `aerowarptics.guide.page.craft_rules.acts` | Each drive tier asks for a different step: build, open, reach, collapse. | Page `craft_rules`: body text |  |
| 778 | `aerowarptics.guide.page.craft_rules.classic` | Turn on the Classic Recipes data pack in a world to use the old recipes. | Page `craft_rules`: an aside, set apart as a note |  |
| 779 | `aerowarptics.guide.page.craft_rules.idea` | Each new drive is made from things carried to the edge of the previous drive's reach. | Page `craft_rules`: body text |  |
| 780 | `aerowarptics.guide.page.craft_rules.ladder` | A Mk I is built. A Mk II needs Opened Rift Lenses, a Mk III needs a Folded Warp Crystal, and a Singularity needs a core collapsed from a Scar Heart. | Page `craft_rules`: body text |  |
| 781 | `aerowarptics.guide.page.craft_rules.title` | Made by Doing | Heading of the page `craft_rules` (chapter `craft`) |  |
| 782 | `aerowarptics.guide.page.craft_scars.chart` | Open a Scar Chart to get a map to a scar nobody has charted yet. | Page `craft_scars`: a numbered step (the number is added by the book) |  |
| 783 | `aerowarptics.guide.page.craft_scars.choice` | An open fissure can be used for rifting for free, forever. Seal it only when you need the Scar Heart. | Page `craft_scars`: an aside, set apart as a note |  |
| 784 | `aerowarptics.guide.page.craft_scars.heart` | A fissure that has been emptied seals, and leaves a Scar Heart behind. | Page `craft_scars`: body text |  |
| 785 | `aerowarptics.guide.page.craft_scars.seal` | Empty the tear with a siphon, and take the heart. | Page `craft_scars`: a numbered step (the number is added by the book) |  |
| 786 | `aerowarptics.guide.page.craft_scars.title` | Hearts and Charts | Heading of the page `craft_scars` (chapter `craft`) |  |
| 787 | `aerowarptics.guide.page.craft_storms.call` | Break a Stormglass to call a Rift Storm down on everyone. | Page `craft_storms`: an aside, set apart as a note |  |
| 788 | `aerowarptics.guide.page.craft_storms.glass` | Fill a glass bottle with Rift Essence and leave it out in a storm to set. | Page `craft_storms`: a numbered step (the number is added by the book) |  |
| 789 | `aerowarptics.guide.page.craft_storms.rain` | A Spatial Siphon under open sky collects rift rain in a storm, and more above the clouds. | Page `craft_storms`: body text |  |
| 790 | `aerowarptics.guide.page.craft_storms.read` | A set Stormglass shows whether a storm is coming. | Page `craft_storms`: body text |  |
| 791 | `aerowarptics.guide.page.craft_storms.title` | Storm Work | Heading of the page `craft_storms` (chapter `craft`) |  |
| 792 | `aerowarptics.guide.page.drive_bow.bow` | Right-click the drive and set the Bow. | Page `drive_bow`: a numbered step (the number is added by the book) |  |
| 793 | `aerowarptics.guide.page.drive_bow.compass` | The bracketed compass direction is only a readout. | Page `drive_bow`: an aside, set apart as a note |  |
| 794 | `aerowarptics.guide.page.drive_bow.needle` | Turn it until the needle on top points at the nose. | Page `drive_bow`: a numbered step (the number is added by the book) |  |
| 795 | `aerowarptics.guide.page.drive_bow.title` | Which Way Is Forward | Heading of the page `drive_bow` (chapter `drive`) |  |
| 796 | `aerowarptics.guide.page.drive_bow.why` | A drive cannot tell which end of the hull is the front. | Page `drive_bow`: body text |  |
| 797 | `aerowarptics.guide.page.drive_fit.assemble` | Assemble an airship as you always would. | Page `drive_fit`: a numbered step (the number is added by the book) |  |
| 798 | `aerowarptics.guide.page.drive_fit.mount` | Mount a Rift Drive anywhere aboard it. | Page `drive_fit`: a numbered step (the number is added by the book) |  |
| 799 | `aerowarptics.guide.page.drive_fit.spin` | Drive its shaft at or above the minimum RPM. | Page `drive_fit`: a numbered step (the number is added by the book) |  |
| 800 | `aerowarptics.guide.page.drive_fit.tiers` | A drive only moves the ship it is built into, never one it is parked beside. | Page `drive_fit`: an aside, set apart as a note |  |
| 801 | `aerowarptics.guide.page.drive_fit.title` | Fitting a Drive | Heading of the page `drive_fit` (chapter `drive`) |  |
| 802 | `aerowarptics.guide.page.faults.common` | Usually: too slow, still charging, or no course set. | Page `faults`: an aside, set apart as a note |  |
| 803 | `aerowarptics.guide.page.faults.order` | The list follows the order the server checks. The first unticked line is the problem. | Page `faults`: body text |  |
| 804 | `aerowarptics.guide.page.faults.role` | The console is a diagnostic panel, not a control. It answers one question: why hasn't the ship moved? | Page `faults`: body text |  |
| 805 | `aerowarptics.guide.page.faults.title` | Reading the Console | Heading of the page `faults` (chapter `help`) |  |
| 806 | `aerowarptics.guide.page.fissures.hidden` | Without goggles, you could walk through one unaware. | Page `fissures`: body text |  |
| 807 | `aerowarptics.guide.page.fissures.ruins` | Look for a broken gate ring, a sunken vault, or a survey mast. | Page `fissures`: an aside, set apart as a note |  |
| 808 | `aerowarptics.guide.page.fissures.title` | Rifts Already There | Heading of the page `fissures` (chapter `fissures`) |  |
| 809 | `aerowarptics.guide.page.fissures.wear` | Wear Rift Infused Goggles to see one at all. | Page `fissures`: a numbered step (the number is added by the book) |  |
| 810 | `aerowarptics.guide.page.fissures.what` | Some rifts were never opened. They hang in old ruins, leaking essence. | Page `fissures`: body text |  |
| 811 | `aerowarptics.guide.page.flotilla_fold.fold` | When every row is ready, press Fold. | Page `flotilla_fold`: a numbered step (the number is added by the book) |  |
| 812 | `aerowarptics.guide.page.flotilla_fold.loose` | An escort that drifts in the countdown arrives late and off to one side. | Page `flotilla_fold`: an aside, set apart as a note |  |
| 813 | `aerowarptics.guide.page.flotilla_fold.rift` | Rift: One sends the whole fleet through one large rift. | Page `flotilla_fold`: an aside, set apart as a note |  |
| 814 | `aerowarptics.guide.page.flotilla_fold.slots` | On the flagship's Fleet Board, drag each escort to a slot, then Sound Stations. | Page `flotilla_fold`: a numbered step (the number is added by the book) |  |
| 815 | `aerowarptics.guide.page.flotilla_fold.sound` | Escorts hold their slots, charged and turning, until they match the flagship's hum. | Page `flotilla_fold`: a numbered step (the number is added by the book) |  |
| 816 | `aerowarptics.guide.page.flotilla_fold.title` | The Flotilla Fold | Heading of the page `flotilla_fold` (chapter `flotilla`) |  |
| 817 | `aerowarptics.guide.page.flotilla_join.owner` | If the flagship belongs to someone else, its owner must accept. Your own ships join straight away. | Page `flotilla_join`: an aside, set apart as a note |  |
| 818 | `aerowarptics.guide.page.flotilla_join.roles` | Set one ship's coupler to Flagship, and the others' to Escort. | Page `flotilla_join`: a numbered step (the number is added by the book) |  |
| 819 | `aerowarptics.guide.page.flotilla_join.station` | At each escort's coupler, pick the flagship and press Take Station. | Page `flotilla_join`: a numbered step (the number is added by the book) |  |
| 820 | `aerowarptics.guide.page.flotilla_join.title` | Joining a Flotilla | Heading of the page `flotilla_join` (chapter `flotilla`) |  |
| 821 | `aerowarptics.guide.page.flotilla_join.what` | A Rift Coupler fitted to a Rift Drive lets several ships fold as one. | Page `flotilla_join`: body text |  |
| 822 | `aerowarptics.guide.page.gate_dial.cross` | Anything that crosses the gate's plane comes out of the far gate: players, mobs, riders and land vehicles. | Page `gate_dial`: body text |  |
| 823 | `aerowarptics.guide.page.gate_dial.dial` | Name the gate, then dial another in the same dimension. | Page `gate_dial`: a numbered step (the number is added by the book) |  |
| 824 | `aerowarptics.guide.page.gate_dial.pays` | Only the gate that dialled pays. The far gate needs nothing but to exist. | Page `gate_dial`: an aside, set apart as a note |  |
| 825 | `aerowarptics.guide.page.gate_dial.title` | Dialling Another | Heading of the page `gate_dial` (chapter `gates`) |  |
| 826 | `aerowarptics.guide.page.gate_ring.controller` | Put a Rift Gate anywhere in the ring. | Page `gate_ring`: a numbered step (the number is added by the book) |  |
| 827 | `aerowarptics.guide.page.gate_ring.feed` | Feed it a shaft and some Rift Essence. | Page `gate_ring`: a numbered step (the number is added by the book) |  |
| 828 | `aerowarptics.guide.page.gate_ring.ring` | Lay a closed ring of Rift Gate Frame on end. | Page `gate_ring`: a numbered step (the number is added by the book) |  |
| 829 | `aerowarptics.guide.page.gate_ring.size` | The opening is the size limit. Build a wider ring to let larger things through. | Page `gate_ring`: an aside, set apart as a note |  |
| 830 | `aerowarptics.guide.page.gate_ring.title` | Building the Ring | Heading of the page `gate_ring` (chapter `gates`) |  |
| 831 | `aerowarptics.guide.page.journey.flown` | The drive propels the ship through the rift. It comes out still moving. | Page `journey`: body text |  |
| 832 | `aerowarptics.guide.page.journey.safe` | If a warp cannot be made safely, it is refused before anything moves. | Page `journey`: an aside, set apart as a note |  |
| 833 | `aerowarptics.guide.page.journey.stay` | Stay on the deck. Anyone who falls off is put back in their seat. | Page `journey`: body text |  |
| 834 | `aerowarptics.guide.page.journey.title` | The Journey | Heading of the page `journey` (chapter `launch`) |  |
| 835 | `aerowarptics.guide.page.modulator_link.fallback` | If it runs out of essence or is removed, the rift goes back to the drive's own tier colours. | Page `modulator_link`: an aside, set apart as a note |  |
| 836 | `aerowarptics.guide.page.modulator_link.fuel` | Feed it a little Rift Essence. It uses a trickle only while the drive is warping. | Page `modulator_link`: a numbered step (the number is added by the book) |  |
| 837 | `aerowarptics.guide.page.modulator_link.place` | Place one against any face of a Rift Drive. | Page `modulator_link`: a numbered step (the number is added by the book) |  |
| 838 | `aerowarptics.guide.page.modulator_link.title` | The Rift Modulator | Heading of the page `modulator_link` (chapter `essence`) |  |
| 839 | `aerowarptics.guide.page.modulator_link.what` | A Rift Modulator lets the pilot of a Rift Drive customise the rift it opens: its colour and its look. | Page `modulator_link`: body text |  |
| 840 | `aerowarptics.guide.page.modulator_look.board` | A Display Link pointed at the Modulator reads the drive it is linked to, the same as one pointed at the drive itself. | Page `modulator_look`: an aside, set apart as a note |  |
| 841 | `aerowarptics.guide.page.modulator_look.choose` | Right-click for sixteen colours (left-click sets the core, right-click sets the rim), a button that cycles through fifteen themes (shift-click to go back), and a slider for how strong the effect is. | Page `modulator_look`: body text |  |
| 842 | `aerowarptics.guide.page.modulator_look.title` | Customising the Rift | Heading of the page `modulator_look` (chapter `essence`) |  |
| 843 | `aerowarptics.guide.page.modulator_look.where` | It changes everything about the rift: the opening, the corridor, and the exit shockwave. | Page `modulator_look`: body text |  |
| 844 | `aerowarptics.guide.page.more.advancements` | Thirty advancements double as the progression, from a first Rift Core onwards. | Page `more`: body text |  |
| 845 | `aerowarptics.guide.page.more.goggles` | Create's goggles show tier, state, charge and stress. | Page `more`: body text |  |
| 846 | `aerowarptics.guide.page.more.jei` | JEI carries an information page on every item. | Page `more`: body text |  |
| 847 | `aerowarptics.guide.page.more.ponder` | Hold [W] over any item from this mod for a Ponder scene. | Page `more`: body text |  |
| 848 | `aerowarptics.guide.page.more.title` | Where to Read More | Heading of the page `more` (chapter `help`) |  |
| 849 | `aerowarptics.guide.page.more.wiki` | This book is the walkthrough. JEI, Ponder and the goggles give the detail. | Page `more`: an aside, set apart as a note |  |
| 850 | `aerowarptics.guide.page.plotter_aim.exit` | Set the exit around the target: how far out, which way it faces, how high, and how fast things leave. | Page `plotter_aim`: a numbered step (the number is added by the book) |  |
| 851 | `aerowarptics.guide.page.plotter_aim.pay` | There is no range limit, but distance costs essence. Pipe it in as the plotter charges. | Page `plotter_aim`: an aside, set apart as a note |  |
| 852 | `aerowarptics.guide.page.plotter_aim.target` | Type the target's X and Z on the Target tab and press Survey. The ground there appears as a 3D model. | Page `plotter_aim`: a numbered step (the number is added by the book) |  |
| 853 | `aerowarptics.guide.page.plotter_aim.title` | Plotting a Shot | Heading of the page `plotter_aim` (chapter `plotter`) |  |
| 854 | `aerowarptics.guide.page.plotter_aim.what` | A relic machine that opens a rift beside itself and another at any target in this world. | Page `plotter_aim`: body text |  |
| 855 | `aerowarptics.guide.page.plotter_fire.engage` | Turn it at 64 RPM or more and press Engage, or set it to fire on redstone, when a ship docks, or to hold the rift open as a lane. | Page `plotter_fire`: a numbered step (the number is added by the book) |  |
| 856 | `aerowarptics.guide.page.plotter_fire.entry` | Open the Entry tab. The camera moves out to show where the entry rift will open. | Page `plotter_fire`: a numbered step (the number is added by the book) |  |
| 857 | `aerowarptics.guide.page.plotter_fire.seen` | The far rift opens only as something comes through. | Page `plotter_fire`: an aside, set apart as a note |  |
| 858 | `aerowarptics.guide.page.plotter_fire.through` | Park a ship behind the entry, inside the amber outline. The dock helper lines it up and launches it through. | Page `plotter_fire`: body text |  |
| 859 | `aerowarptics.guide.page.plotter_fire.title` | Firing | Heading of the page `plotter_fire` (chapter `plotter`) |  |
| 860 | `aerowarptics.guide.page.probe_fix.essence` | Scanning spends Rift Essence, which is what a Spatial Siphon is for. | Page `probe_fix`: an aside, set apart as a note |  |
| 861 | `aerowarptics.guide.page.probe_fix.fix` | A finished scan leaves a fix: a location known only from the scan, with no anchor at the far end. | Page `probe_fix`: body text |  |
| 862 | `aerowarptics.guide.page.probe_fix.set` | Set your course to the fix at the Astrolabe table, as you would for an anchor. | Page `probe_fix`: a numbered step (the number is added by the book) |  |
| 863 | `aerowarptics.guide.page.probe_fix.title` | Warping to a Fix | Heading of the page `probe_fix` (chapter `probe`) |  |
| 864 | `aerowarptics.guide.page.probe_scan.partial` | The bar is the world being generated. A partial map says so. | Page `probe_scan`: an aside, set apart as a note |  |
| 865 | `aerowarptics.guide.page.probe_scan.point` | Aim the dial, set a distance and arrival height. | Page `probe_scan`: a numbered step (the number is added by the book) |  |
| 866 | `aerowarptics.guide.page.probe_scan.scan` | Press Scan ahead to bring back a survey of that ground. | Page `probe_scan`: a numbered step (the number is added by the book) |  |
| 867 | `aerowarptics.guide.page.probe_scan.title` | The Rift Probe | Heading of the page `probe_scan` (chapter `probe`) |  |
| 868 | `aerowarptics.guide.page.probe_scan.why` | Anchors only reach where somebody has walked. | Page `probe_scan`: body text |  |
| 869 | `aerowarptics.guide.page.siphon.fluid` | Essence is a real fluid. Create's pipes can move it, and gates and probes use it up. | Page `siphon`: an aside, set apart as a note |  |
| 870 | `aerowarptics.guide.page.siphon.odds` | A longer jump gives a better chance of a larger catch, but nothing is guaranteed. | Page `siphon`: body text |  |
| 871 | `aerowarptics.guide.page.siphon.title` | The Spatial Siphon | Heading of the page `siphon` (chapter `essence`) |  |
| 872 | `aerowarptics.guide.page.siphon.what` | Bolt one to the hull. Each time the ship comes out of a rift, it catches some Rift Essence. | Page `siphon`: body text |  |
| 873 | `aerowarptics.guide.page.ward_field.charge` | It banks a charge and the field runs on that, loaded or not. A wider field costs more. | Page `ward_field`: an aside, set apart as a note |  |
| 874 | `aerowarptics.guide.page.ward_field.feed` | Run a shaft into the middle of any side, or up under its middle, at 32 RPM or more, and pipe in Rift Essence. | Page `ward_field`: a numbered step (the number is added by the book) |  |
| 875 | `aerowarptics.guide.page.ward_field.place` | Set it down on the ground with three clear blocks each way. It cannot be moved or carried. | Page `ward_field`: a numbered step (the number is added by the book) |  |
| 876 | `aerowarptics.guide.page.ward_field.title` | Holding a Field | Heading of the page `ward_field` (chapter `ward`) |  |
| 877 | `aerowarptics.guide.page.ward_field.what` | A relic machine that holds a field over the ground round it and turns rifts aside. | Page `ward_field`: body text |  |
| 878 | `aerowarptics.guide.page.ward_hold.panel` | Click it: the camera lifts to show the field. Set its radius and switch it on. | Page `ward_hold`: a numbered step (the number is added by the book) |  |
| 879 | `aerowarptics.guide.page.ward_hold.through` | On the Who tab choose whom it turns: everyone, all but some networks, or only some. | Page `ward_hold`: body text |  |
| 880 | `aerowarptics.guide.page.ward_hold.title` | What It Turns | Heading of the page `ward_hold` (chapter `ward`) |  |
| 881 | `aerowarptics.guide.page.ward_hold.turned` | Whoever it turns comes out at the field's edge instead, by ship or by plotted rift. | Page `ward_hold`: body text |  |
| 882 | `aerowarptics.guide.page.ward_hold.worn` | Each rift turned costs charge. A Rift Storm thins the field. Better drives push further in. | Page `ward_hold`: an aside, set apart as a note |  |
| 883 | `aerowarptics.guide.page.wiring.button` | No launch button: a signal only repeats a set course. | Page `wiring`: an aside, set apart as a note |  |
| 884 | `aerowarptics.guide.page.wiring.check` | Open the console: every line ticked but the last. | Page `wiring`: a numbered step (the number is added by the book) |  |
| 885 | `aerowarptics.guide.page.wiring.signal` | Give the drive a redstone pulse. A lever will do. | Page `wiring`: a numbered step (the number is added by the book) |  |
| 886 | `aerowarptics.guide.page.wiring.split` | Choose at the table. Launch with redstone. Diagnose at the drive. | Page `wiring`: body text |  |
| 887 | `aerowarptics.guide.page.wiring.title` | Three Jobs, Three Places | Heading of the page `wiring` (chapter `launch`) |  |
| 888 | `aerowarptics.guide.tip` | Right-click to read | Tooltip on the handbook item | HandbookItem |

### Ponder scenes (148)

**Seen:** Create's Ponder (hold W on an item). `header` is the scene title; `text_1`, `text_2`... are the captions in the order they appear, each tied to one moment of the animation.

**Edit in:** the `.text("...")` and `scene.title(...)` calls in `compat/ponder/scene/*.java`, then run `python tools/ponder_lang.py`. The lang lines are generated from the Java; `PonderTextTest` fails if the two differ. Text must not contain `"` or `\`.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 984 | `aerowarptics.ponder.astrolabe.header` | Charting a Course | Scene title (MachineScenes.java) |  |
| 985 | `aerowarptics.ponder.astrolabe.text_1` | Astrolabe blocks are laid flat, in a square up to three by three | Caption 1 of the scene, in the order shown |  |
| 986 | `aerowarptics.ponder.astrolabe.text_2` | Complete the square and the table forms itself | Caption 2 of the scene, in the order shown |  |
| 987 | `aerowarptics.ponder.astrolabe.text_3` | Right-click for the chart: every anchor within reach, drawn over a survey of the ground around it | Caption 3 of the scene, in the order shown |  |
| 988 | `aerowarptics.ponder.astrolabe.text_4` | A table can ride aboard the ship, so a course can be set without landing first | Caption 4 of the scene, in the order shown |  |
| 989 | `aerowarptics.ponder.collapse_cradle.header` | The Collapse | Scene title (CraftScenes.java) |  |
| 990 | `aerowarptics.ponder.collapse_cradle.text_1` | A Collapse Cradle collapses a Singularity Core out of a Rift Core and a Scar Heart | Caption 1 of the scene, in the order shown |  |
| 991 | `aerowarptics.ponder.collapse_cradle.text_10` | Steady, the core settles back into the arms. Unsteady, it is flung clear. Very unsteady, it spits the core and offerings back out | Caption 10 of the scene, in the order shown |  |
| 992 | `aerowarptics.ponder.collapse_cradle.text_11` | The collapse knocks back everything near it, and an unsteady one hurts whoever stands too close | Caption 11 of the scene, in the order shown |  |
| 993 | `aerowarptics.ponder.collapse_cradle.text_12` | However it ends, only the Scar Heart and the essence are used up | Caption 12 of the scene, in the order shown |  |
| 994 | `aerowarptics.ponder.collapse_cradle.text_2` | It is an old machine, rebuilt: three blocks each way, set down whole where you aim, and too heavy to move | Caption 2 of the scene, in the order shown |  |
| 995 | `aerowarptics.ponder.collapse_cradle.text_3` | Its rings, the Relic Armillary, can only be found: look in the barrels in rift scar ruins | Caption 3 of the scene, in the order shown |  |
| 996 | `aerowarptics.ponder.collapse_cradle.text_4` | The Scar Heart comes only from a sealed Rift Fissure. Without one, the core has nothing to collapse into | Caption 4 of the scene, in the order shown |  |
| 997 | `aerowarptics.ponder.collapse_cradle.text_5` | The offerings stand on Create Depots round it. A Mechanical Arm can load them | Caption 5 of the scene, in the order shown |  |
| 998 | `aerowarptics.ponder.collapse_cradle.text_6` | Pipe Rift Essence into its bottom layer, and turn it with a shaft into the middle of any side of its floor or up under its middle, at a Singularity drive's own speed | Caption 6 of the scene, in the order shown |  |
| 999 | `aerowarptics.ponder.collapse_cradle.text_7` | Switched-on Spatial Stabilisers steady the collapse. Space them evenly: a lopsided cage wobbles | Caption 7 of the scene, in the order shown |  |
| 1000 | `aerowarptics.ponder.collapse_cradle.text_8` | A redstone pulse at any side begins it. Anything missing refuses it, before anything is spent | Caption 8 of the scene, in the order shown |  |
| 1001 | `aerowarptics.ponder.collapse_cradle.text_9` | The arms lift the core into the rings, the iris opens and the rift folds into a hole. Stand back: it drags loose objects in as it winds up | Caption 9 of the scene, in the order shown |  |
| 1002 | `aerowarptics.ponder.cooling_vent.header` | Cooling a Drive | Scene title (CoolingScenes.java) |  |
| 1003 | `aerowarptics.ponder.cooling_vent.text_1` | After every warp a Rift Drive has to cool down before it can charge for the next | Caption 1 of the scene, in the order shown |  |
| 1004 | `aerowarptics.ponder.cooling_vent.text_2` | A Cooling Vent shortens that. It goes against any face of the drive, with its louvres facing away and its shaft at the back | Caption 2 of the scene, in the order shown |  |
| 1005 | `aerowarptics.ponder.cooling_vent.text_3` | Set straight onto a drive that is turning, it is turned by the drive. Turn it with a wrench to feed it from a slower shaft of its own instead | Caption 3 of the scene, in the order shown |  |
| 1006 | `aerowarptics.ponder.cooling_vent.text_4` | When a warp completes, a turning vent halves the cooldown and blows the heat out as steam | Caption 4 of the scene, in the order shown |  |
| 1007 | `aerowarptics.ponder.cooling_vent.text_5` | Each further vent halves what the last one left | Caption 5 of the scene, in the order shown |  |
| 1008 | `aerowarptics.ponder.cooling_vent.text_6` | A block of ice against the drive cools it too, with no shaft at all, but it only takes a tenth off | Caption 6 of the scene, in the order shown |  |
| 1009 | `aerowarptics.ponder.cooling_vent.text_7` | Four coolers count on one drive, vents before ice. Any more do nothing, and the goggles show which | Caption 7 of the scene, in the order shown |  |
| 1010 | `aerowarptics.ponder.cooling_vent.text_8` | The cooldown is set as the ship arrives, by whatever is cooling the drive at that moment. A vent that has stopped turning by then does not count | Caption 8 of the scene, in the order shown |  |
| 1011 | `aerowarptics.ponder.cradle_folding.header` | Folding at the Cradle | Scene title (CraftScenes.java) |  |
| 1012 | `aerowarptics.ponder.cradle_folding.text_1` | A Collapse Cradle can also fold items: it is simpler than a collapse and takes a few seconds | Caption 1 of the scene, in the order shown |  |
| 1013 | `aerowarptics.ponder.cradle_folding.text_2` | Put a stack of up to sixteen in its arms. The folding ingredient, such as a Shulker Shell, goes in its cup | Caption 2 of the scene, in the order shown |  |
| 1014 | `aerowarptics.ponder.cradle_folding.text_3` | Every craft uses Rift Essence from its tank, and the shaft has to be turning | Caption 3 of the scene, in the order shown |  |
| 1015 | `aerowarptics.ponder.cradle_folding.text_4` | Press Begin on its panel, or give it a redstone pulse. The arms lift the stack up to a small rift, which folds it | Caption 4 of the scene, in the order shown |  |
| 1016 | `aerowarptics.ponder.cradle_folding.text_5` | The result comes back down into the arms, and waits in the tray for you or a funnel to collect it | Caption 5 of the scene, in the order shown |  |
| 1017 | `aerowarptics.ponder.cradle_folding.text_6` | A Warp Crystal folds into a Folded Warp Crystal. That plus a Shulker Shell folds into a Pearlescent Warp Crystal. A Compass folds into a Ship's Compass | Caption 6 of the scene, in the order shown |  |
| 1018 | `aerowarptics.ponder.cradle_folding.text_7` | A Ship's Compass comes out unbound. Sneak and use it on a Rift Drive to bind it to that ship | Caption 7 of the scene, in the order shown |  |
| 1019 | `aerowarptics.ponder.drive_extender.header` | Building a Drive Out | Scene title (DriveScenes.java) |  |
| 1020 | `aerowarptics.ponder.drive_extender.text_1` | A Rift Drive works on its own. Drive Extenders are an optional upgrade, never a requirement | Caption 1 of the scene, in the order shown |  |
| 1021 | `aerowarptics.ponder.drive_extender.text_2` | Build a complete cube of them, from two blocks along an edge up to seven, with the drive anywhere inside it - buried in the middle included | Caption 2 of the scene, in the order shown |  |
| 1022 | `aerowarptics.ponder.drive_extender.text_3` | Until the cube is finished, the extenders are inert casing. An incomplete frame does nothing and carries nothing | Caption 3 of the scene, in the order shown |  |
| 1023 | `aerowarptics.ponder.drive_extender.text_4` | Place the last block and the drive forms. What stands there is now one machine, drawn the size of its frame | Caption 4 of the scene, in the order shown |  |
| 1024 | `aerowarptics.ponder.drive_extender.text_5` | A shaft against any face of the cube turns it. Every extender passes the rotation through, so nothing needs to reach the drive itself | Caption 5 of the scene, in the order shown |  |
| 1025 | `aerowarptics.ponder.drive_extender.text_6` | Every block of the frame acts as the drive: right-click any of them for the console, and a module against any face links to it | Caption 6 of the scene, in the order shown |  |
| 1026 | `aerowarptics.ponder.drive_extender.text_7` | It does not fly any further for being bigger. What a larger frame gives you is room and faces to attach things to, not range | Caption 7 of the scene, in the order shown |  |
| 1027 | `aerowarptics.ponder.drive_extender.text_8` | Remove one block and the cube is no longer complete. The frame stops working, and the drive goes back to being a single block | Caption 8 of the scene, in the order shown |  |
| 1028 | `aerowarptics.ponder.rift_beacon.header` | Calling a Ship Down | Scene title (BeaconScenes.java) |  |
| 1029 | `aerowarptics.ponder.rift_beacon.text_1` | A Rift Beacon calls a ship you are not standing on, which a normal course cannot do | Caption 1 of the scene, in the order shown |  |
| 1030 | `aerowarptics.ponder.rift_beacon.text_2` | Sneak and right-click a Rift Drive to bind the beacon to it. A plain click would only open the console | Caption 2 of the scene, in the order shown |  |
| 1031 | `aerowarptics.ponder.rift_beacon.text_3` | Permission is checked here. A beacon cannot be bound to a ship you could not have commanded by hand | Caption 3 of the scene, in the order shown |  |
| 1032 | `aerowarptics.ponder.rift_beacon.text_4` | Then aim at anywhere within sight and right-click. The ship is called to whatever the crosshair is on | Caption 4 of the scene, in the order shown |  |
| 1033 | `aerowarptics.ponder.rift_beacon.text_5` | The drive still has to be ready: turning fast enough, charged, in range, and with room for the hull to arrive | Caption 5 of the scene, in the order shown |  |
| 1034 | `aerowarptics.ponder.rift_beacon.text_6` | If the warp is refused, the beacon says which of these failed, in the same words the drive's own console uses | Caption 6 of the scene, in the order shown |  |
| 1035 | `aerowarptics.ponder.rift_beacon.text_7` | If the drive is taken apart, the beacon points at nothing. Bind it again to whatever you build next | Caption 7 of the scene, in the order shown |  |
| 1036 | `aerowarptics.ponder.rift_brazier.header` | Rifting at an Open Rift | Scene title (CraftScenes.java) |  |
| 1037 | `aerowarptics.ponder.rift_brazier.text_1` | A Rift Brazier burns with rift fire while it has a redstone signal and essence | Caption 1 of the scene, in the order shown |  |
| 1038 | `aerowarptics.ponder.rift_brazier.text_2` | Give it a redstone signal. Lit, it burns two millibuckets of Rift Essence a second, whatever is passing | Caption 2 of the scene, in the order shown |  |
| 1039 | `aerowarptics.ponder.rift_brazier.text_3` | Blow items through it with an Encased Fan, the same way you would wash them | Caption 3 of the scene, in the order shown |  |
| 1040 | `aerowarptics.ponder.rift_brazier.text_4` | A Rift Lens comes out as an Opened Rift Lens. Every drive from the Mk II up is built with them | Caption 4 of the scene, in the order shown |  |
| 1041 | `aerowarptics.ponder.rift_brazier.text_5` | Any open rift will do: a Rift Fissure is free for as long as it stays open, and so is an open Rift Gate's pane | Caption 5 of the scene, in the order shown |  |
| 1042 | `aerowarptics.ponder.rift_chute.header` | Moving Cargo Across a Rift | Scene title (MachineScenes.java) |  |
| 1043 | `aerowarptics.ponder.rift_chute.text_1` | A Rift Chute hands items to another chute, however far apart they are - including one bolted to an airship | Caption 1 of the scene, in the order shown |  |
| 1044 | `aerowarptics.ponder.rift_chute.text_2` | Fit a funnel to any face and feed it from a belt. A chute is an inventory, so it does not sit directly on the belt | Caption 2 of the scene, in the order shown |  |
| 1045 | `aerowarptics.ponder.rift_chute.text_3` | Right-click a chute to name it and pair it with another. Items travel both ways along a pair | Caption 3 of the scene, in the order shown |  |
| 1046 | `aerowarptics.ponder.rift_chute.text_4` | Whichever end sends pays the Rift Essence, per item and only when one crosses. A receiving chute needs no essence of its own | Caption 4 of the scene, in the order shown |  |
| 1047 | `aerowarptics.ponder.rift_chute.text_5` | Nothing is ever lost. A full far end, an unloaded chunk or a ship mid-warp all make items wait in the chute instead | Caption 5 of the scene, in the order shown |  |
| 1048 | `aerowarptics.ponder.rift_coupler.header` | The Rift Coupler | Scene title (CouplerScenes.java) |  |
| 1049 | `aerowarptics.ponder.rift_coupler.text_1` | A Rift Coupler fitted to any face of a Rift Drive lets its ship warp together with others, as a flotilla | Caption 1 of the scene, in the order shown |  |
| 1050 | `aerowarptics.ponder.rift_coupler.text_2` | Right-click to set its role. One ship is the Flagship: its drive holds the course and commits the jump | Caption 2 of the scene, in the order shown |  |
| 1051 | `aerowarptics.ponder.rift_coupler.text_3` | The others are Escorts. At an escort's coupler, take station with a flagship within reach: its owner accepts, and your own ships join straight away | Caption 3 of the scene, in the order shown |  |
| 1052 | `aerowarptics.ponder.rift_coupler.text_4` | On the flagship's Fleet Board, drag each escort to a slot, then press Sound Stations. Every ship in the flotilla hears the horn | Caption 4 of the scene, in the order shown |  |
| 1053 | `aerowarptics.ponder.rift_coupler.text_5` | An escort holding its slot with a full, turning drive harmonises with the flagship. Listen for the hums settling on one note | Caption 5 of the scene, in the order shown |  |
| 1054 | `aerowarptics.ponder.rift_coupler.text_6` | Every drive still needs its own power and pays its own charge, and a fleet costs more than the same ships flying alone | Caption 6 of the scene, in the order shown |  |
| 1055 | `aerowarptics.ponder.rift_coupler.text_7` | When every escort is ready, press Fold. A ship that drifts out of its slot during the countdown is shaken loose, and arrives late and off to one side | Caption 7 of the scene, in the order shown |  |
| 1056 | `aerowarptics.ponder.rift_drive.header` | The Rift Drive | Scene title (DriveScenes.java) |  |
| 1057 | `aerowarptics.ponder.rift_drive.text_1` | A Rift Drive moves an entire airship to a Warp Anchor, hull and cargo and crew together | Caption 1 of the scene, in the order shown |  |
| 1058 | `aerowarptics.ponder.rift_drive.text_2` | It has to be built into the ship. A drive standing on the ground has nothing to move | Caption 2 of the scene, in the order shown |  |
| 1059 | `aerowarptics.ponder.rift_drive.text_3` | Rotational force spins it up. Below the drive's minimum speed it will not charge at all | Caption 3 of the scene, in the order shown |  |
| 1060 | `aerowarptics.ponder.rift_drive.text_4` | Charge builds while it turns. A jump spends a share of it - more for distance, more for a heavy ship | Caption 4 of the scene, in the order shown |  |
| 1061 | `aerowarptics.ponder.rift_drive.text_5` | Right-click for the console: pick an anchor, and the drive quotes the cost before committing | Caption 5 of the scene, in the order shown |  |
| 1062 | `aerowarptics.ponder.rift_drive.text_6` | Mk I to Singularity buy reach, speed and efficiency. The Singularity pays for its range with an unsteady arrival | Caption 6 of the scene, in the order shown |  |
| 1063 | `aerowarptics.ponder.rift_fissure.header` | Rifts Nobody Opened | Scene title (FissureScenes.java) |  |
| 1064 | `aerowarptics.ponder.rift_fissure.text_1` | Ruins generate where someone's gate went wrong: a broken ring, a sunken vault, a survey mast | Caption 1 of the scene, in the order shown |  |
| 1065 | `aerowarptics.ponder.rift_fissure.text_2` | A Rift Fissure hangs in the middle of one. It is a rift that never closed, still leaking Rift Essence | Caption 2 of the scene, in the order shown |  |
| 1066 | `aerowarptics.ponder.rift_fissure.text_3` | It is invisible. It has no model and no outline, and you cannot bump into it - you could stand in the ruin and never know | Caption 3 of the scene, in the order shown |  |
| 1067 | `aerowarptics.ponder.rift_fissure.text_4` | Wear Rift Infused Goggles. Only then is the fissure drawn, and only then can you aim at it | Caption 4 of the scene, in the order shown |  |
| 1068 | `aerowarptics.ponder.rift_fissure.text_5` | They are Create's goggles with a rift-ground lens, so they read every machine that the engineer's goggles do | Caption 5 of the scene, in the order shown |  |
| 1069 | `aerowarptics.ponder.rift_fissure.text_6` | A fissure cannot be broken. The only way to get rid of one is to empty it | Caption 6 of the scene, in the order shown |  |
| 1070 | `aerowarptics.ponder.rift_fissure_closing.header` | Emptying a Fissure | Scene title (FissureScenes.java) |  |
| 1071 | `aerowarptics.ponder.rift_fissure_closing.text_1` | The fissure stands here, visible only through the goggles | Caption 1 of the scene, in the order shown |  |
| 1072 | `aerowarptics.ponder.rift_fissure_closing.text_2` | Stand a Spatial Siphon within a few blocks of it. Nothing else is needed: no shaft, no redstone | Caption 2 of the scene, in the order shown |  |
| 1073 | `aerowarptics.ponder.rift_fissure_closing.text_3` | The fissure hands over its essence steadily, and shrinks as it empties | Caption 3 of the scene, in the order shown |  |
| 1074 | `aerowarptics.ponder.rift_fissure_closing.text_4` | A fissure holds several times what a single siphon does, so pipe the essence away or stand more than one siphon nearby | Caption 4 of the scene, in the order shown |  |
| 1075 | `aerowarptics.ponder.rift_fissure_closing.text_5` | More siphons do not fill any faster. The fissure's own rate is the limit, not the plumbing | Caption 5 of the scene, in the order shown |  |
| 1076 | `aerowarptics.ponder.rift_fissure_closing.text_6` | Once empty, it seals for good. It cannot be brought back | Caption 6 of the scene, in the order shown |  |
| 1077 | `aerowarptics.ponder.rift_gate.header` | Building a Rift Gate | Scene title (GateScenes.java) |  |
| 1078 | `aerowarptics.ponder.rift_gate.text_1` | Rift Gate Frame builds a ring standing upright, enclosing an empty opening | Caption 1 of the scene, in the order shown |  |
| 1079 | `aerowarptics.ponder.rift_gate.text_2` | The opening is the size limit. Whatever fits through it can travel - and nothing larger can | Caption 2 of the scene, in the order shown |  |
| 1080 | `aerowarptics.ponder.rift_gate.text_3` | Anything from four blocks of opening up to two hundred and twenty-five will form | Caption 3 of the scene, in the order shown |  |
| 1081 | `aerowarptics.ponder.rift_gate.text_4` | One block of the ring is a Rift Gate controller. It counts as frame, so it may sit anywhere in the circle | Caption 4 of the scene, in the order shown |  |
| 1082 | `aerowarptics.ponder.rift_gate.text_5` | A second gate has to be built somewhere else in the same world. A gate on its own connects to nothing | Caption 5 of the scene, in the order shown |  |
| 1083 | `aerowarptics.ponder.rift_gate_dialling.header` | Travelling by Rift Gate | Scene title (GateScenes.java) |  |
| 1084 | `aerowarptics.ponder.rift_gate_dialling.text_1` | Rotational force holds the gate's opening open. If the speed drops, the gate disconnects | Caption 1 of the scene, in the order shown |  |
| 1085 | `aerowarptics.ponder.rift_gate_dialling.text_2` | A wider opening is harder to hold and costs more to dial | Caption 2 of the scene, in the order shown |  |
| 1086 | `aerowarptics.ponder.rift_gate_dialling.text_3` | Rift Essence pays to dial, and keeps being used every second the doorway stays open | Caption 3 of the scene, in the order shown |  |
| 1087 | `aerowarptics.ponder.rift_gate_dialling.text_4` | Right-click the controller and choose which gate to connect to | Caption 4 of the scene, in the order shown |  |
| 1088 | `aerowarptics.ponder.rift_gate_dialling.text_5` | Only the dialling gate pays. The far end needs no rotation and no essence of its own | Caption 5 of the scene, in the order shown |  |
| 1089 | `aerowarptics.ponder.rift_gate_dialling.text_6` | Walk in, or drive in. A vehicle leaves the far gate in the direction that gate faces, keeping its speed | Caption 6 of the scene, in the order shown |  |
| 1090 | `aerowarptics.ponder.rift_gate_dialling.text_7` | Both ends must lie in the same world. A gate cannot reach into another dimension | Caption 7 of the scene, in the order shown |  |
| 1091 | `aerowarptics.ponder.rift_modulator.header` | The Rift Modulator | Scene title (ModulatorScenes.java) |  |
| 1092 | `aerowarptics.ponder.rift_modulator.text_1` | A Rift Modulator bolted beside a Rift Drive lets its pilot choose the colour of the rift it opens, and even how the rift opens | Caption 1 of the scene, in the order shown |  |
| 1093 | `aerowarptics.ponder.rift_modulator.text_2` | It reads whichever drive it touches, and takes its facing from that drive when it is placed | Caption 2 of the scene, in the order shown |  |
| 1094 | `aerowarptics.ponder.rift_modulator.text_3` | It uses a trickle of Rift Essence, and only while the drive is warping. An idle drive costs it nothing | Caption 3 of the scene, in the order shown |  |
| 1095 | `aerowarptics.ponder.rift_modulator.text_4` | Right-click for a choice of colours (left-click sets the core, right-click sets the rim), a matching theme, and a slider for how strong the effect is | Caption 4 of the scene, in the order shown |  |
| 1096 | `aerowarptics.ponder.rift_modulator.text_5` | If it runs out of essence or is removed, the rift goes back to the drive's own tier colours | Caption 5 of the scene, in the order shown |  |
| 1097 | `aerowarptics.ponder.rift_probe.header` | Finding Somewhere New | Scene title (MachineScenes.java) |  |
| 1098 | `aerowarptics.ponder.rift_probe.text_1` | Every other destination is somewhere a player has placed an anchor. A Rift Probe is how you reach ground nobody has visited | Caption 1 of the scene, in the order shown |  |
| 1099 | `aerowarptics.ponder.rift_probe.text_2` | Right-click it, pick a direction on the dial and a distance on the slider, then scan ahead | Caption 2 of the scene, in the order shown |  |
| 1100 | `aerowarptics.ponder.rift_probe.text_3` | Rift Essence pays for each scan, and scanning further costs more. A Spatial Siphon is how you collect it | Caption 3 of the scene, in the order shown |  |
| 1101 | `aerowarptics.ponder.rift_probe.text_4` | The wait is the world being generated. A scan aimed at unexplored ground has to generate that ground before it can map it | Caption 4 of the scene, in the order shown |  |
| 1102 | `aerowarptics.ponder.rift_probe.text_5` | What comes back is the same chart the Astrolabe draws for an anchor, so a blind jump is judged on the same evidence as any other | Caption 5 of the scene, in the order shown |  |
| 1103 | `aerowarptics.ponder.rift_probe.text_6` | Send the ship there and the Rift Drive flies it. There is no anchor at the far end yet, so bring one if you want to return | Caption 6 of the scene, in the order shown |  |
| 1104 | `aerowarptics.ponder.spatial_siphon.header` | Collecting Rift Essence | Scene title (MachineScenes.java) |  |
| 1105 | `aerowarptics.ponder.spatial_siphon.text_1` | A Spatial Siphon is built into an airship, not onto the ground | Caption 1 of the scene, in the order shown |  |
| 1106 | `aerowarptics.ponder.spatial_siphon.text_2` | Passing a hull through folded space sheds some Rift Essence. The siphons aboard catch it | Caption 2 of the scene, in the order shown |  |
| 1107 | `aerowarptics.ponder.spatial_siphon.text_3` | One catch per jump, shared between every siphon aboard. More siphons hold more, but do not catch more. Longer jumps give more | Caption 3 of the scene, in the order shown |  |
| 1108 | `aerowarptics.ponder.spatial_siphon.text_4` | Four buckets fit inside. Pipes can draw the essence off into storage | Caption 4 of the scene, in the order shown |  |
| 1109 | `aerowarptics.ponder.spatial_siphon.text_5` | Rift Essence is what dials a Rift Gate | Caption 5 of the scene, in the order shown |  |
| 1110 | `aerowarptics.ponder.spatial_stabiliser.header` | Steadying a Ship | Scene title (StabiliserScenes.java) |  |
| 1111 | `aerowarptics.ponder.spatial_stabiliser.text_1` | A Spatial Stabiliser is a Rift Drive module. It goes against any face of a drive that is built into an airship | Caption 1 of the scene, in the order shown |  |
| 1112 | `aerowarptics.ponder.spatial_stabiliser.text_2` | It has no screen and nothing to set. A redstone signal switches it on, and losing the signal switches it off | Caption 2 of the scene, in the order shown |  |
| 1113 | `aerowarptics.ponder.spatial_stabiliser.text_3` | Left alone, a hull does what its machinery tells it, as fast as the physics allows. Light the burners under a balloon and the ship leaps upward | Caption 3 of the scene, in the order shown |  |
| 1114 | `aerowarptics.ponder.spatial_stabiliser.text_4` | Switched on, it cancels most of each tick's change in motion. The same push now eases the ship upward instead | Caption 4 of the scene, in the order shown |  |
| 1115 | `aerowarptics.ponder.spatial_stabiliser.text_5` | The ship still goes wherever it is pushed. It just responds as though it were several times heavier | Caption 5 of the scene, in the order shown |  |
| 1116 | `aerowarptics.ponder.spatial_stabiliser.text_6` | A blow too hard to come from machinery, such as a hillside or a hard landing, passes through untouched, so the ship is never cushioned into the ground it just hit | Caption 6 of the scene, in the order shown |  |
| 1117 | `aerowarptics.ponder.spatial_stabiliser.text_7` | While its drive is flying a warp, it stands by. The flight was planned in full before the rift opened, and resisting it would only drag the ship off course | Caption 7 of the scene, in the order shown |  |
| 1118 | `aerowarptics.ponder.spatial_stabiliser.text_8` | A second stabiliser aboard makes no difference. One holds the ship and the rest stand by. The goggles show which is which | Caption 8 of the scene, in the order shown |  |
| 1119 | `aerowarptics.ponder.spatial_wave_detector.header` | Hearing an Arrival | Scene title (DetectorScenes.java) |  |
| 1120 | `aerowarptics.ponder.spatial_wave_detector.text_1` | A Spatial Wave Detector listens for anything arriving through a rift nearby | Caption 1 of the scene, in the order shown |  |
| 1121 | `aerowarptics.ponder.spatial_wave_detector.text_2` | Scroll on the box on any side to set how far it listens. The server may cap how far that can be | Caption 2 of the scene, in the order shown |  |
| 1122 | `aerowarptics.ponder.spatial_wave_detector.text_3` | When a ship finishes a warp, or anyone steps out of a Rift Gate within range, it gives off a pulse of redstone | Caption 3 of the scene, in the order shown |  |
| 1123 | `aerowarptics.ponder.spatial_wave_detector.text_4` | The closer the arrival, the stronger the signal. Right beside it gives 15, and the very edge of its range gives 1 | Caption 4 of the scene, in the order shown |  |
| 1124 | `aerowarptics.ponder.spatial_wave_detector.text_5` | A ship is measured from its nearest part, not its middle. If a long hull's bow comes out beside the detector, the ship counts as arriving beside it | Caption 5 of the scene, in the order shown |  |
| 1125 | `aerowarptics.ponder.spatial_wave_detector.text_6` | It only hears arrivals, never departures. Another arrival during a pulse restarts the pulse | Caption 6 of the scene, in the order shown |  |
| 1126 | `aerowarptics.ponder.spatial_wave_detector.text_7` | It works aboard an airship too, and hears its own ship arrive at full strength | Caption 7 of the scene, in the order shown |  |
| 1127 | `aerowarptics.ponder.warp_anchor.header` | Marking a Destination | Scene title (MachineScenes.java) |  |
| 1128 | `aerowarptics.ponder.warp_anchor.text_1` | A Warp Anchor is somewhere an airship can be sent | Caption 1 of the scene, in the order shown |  |
| 1129 | `aerowarptics.ponder.warp_anchor.text_2` | Right-click it to give it a name, and to say whether other players may travel to it | Caption 2 of the scene, in the order shown |  |
| 1130 | `aerowarptics.ponder.warp_anchor.text_3` | The world keeps the record, not the block. An anchor stays a valid destination even while its chunks are unloaded | Caption 3 of the scene, in the order shown |  |
| 1131 | `aerowarptics.ponder.warp_anchor.text_4` | Leave room around it. An arriving hull has to fit, and the drive will refuse an anchor whose space is blocked | Caption 4 of the scene, in the order shown |  |

### JEI information pages (37)

**Seen:** JEI, on an item's "Information" tab. One block of text per item; JEI wraps and paginates it, so length is free.

**Edit in:** `en_us.json` directly.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 903 | `aerowarptics.jei.info.astrolabe` | Laid flat in a square, these form a Cartography Table. One block on its own is already a working table; two by two and three by three chart progressively more ground, and a smaller table is absorbed when you build a bigger one around it. Right-click the finished table for a chart of every anchor within reach, drawn over a survey of the ground around it. It can ride aboard the ship. | JEI information page for `astrolabe` |  |
| 904 | `aerowarptics.jei.info.collapse_cradle` | An old machine, rebuilt: three blocks across and three high, placed whole and centred where you aim. Right-click it for its panel. It does two kinds of work, depending on what is in its arms. A stack in its arms, with its folding ingredient in the cup, is folded for Rift Essence, up to sixteen at once. A Rift Core in its arms, a Scar Heart in its cup and offerings on Create Depots around it make a collapse: turn it at a Singularity drive's speed, pipe in essence and start it. Switched-on Spatial Stabilisers around it steady a collapse, as long as they are evenly spaced and not all on one side. If the collapse is unsteady, the machine shakes, sparks and smokes, flings its core clear, and hurts whoever stands too close. If it is very unsteady, it spits the core and offerings back out and keeps only the Scar Heart and the essence. Pipes and funnels connect to any block of its bottom layer. A shaft turns it through the middle of any side of that layer or from under its middle, and no rotation passes through the machine. Start it from the panel or with a redstone pulse on any side. Building one requires a Relic Armillary, which can only be found. | JEI information page for `collapse_cradle` |  |
| 905 | `aerowarptics.jei.info.cooling_vent` | Placed against a Rift Drive and turned by a shaft, it halves the time the drive takes to cool down after a warp, and blows the heat out as steam when the ship arrives. Up to four coolers count on one drive, and each vent halves what the last one left. Its louvres face away from whatever you place it on and the shaft goes in at the back, so one set straight onto a turning drive is turned by it. A block of ice against the drive works too, with no shaft at all, but only takes a tenth off. | JEI information page for `cooling_vent` |  |
| 906 | `aerowarptics.jei.info.drive_extender` | Build a complete cube of these, from two to seven blocks along an edge, with a Rift Drive anywhere inside it. The finished drive is drawn the size of the cube, and every extender in it counts as part of the drive: a shaft against any face of the cube turns it, a Rift Modulator or Spatial Stabiliser placed against any face works as if it were beside the drive, and right-clicking any block opens the drive's console. It does not fly any further for being bigger. Until the cube is complete, extenders do nothing at all, not even carry rotation. | JEI information page for `drive_extender` |  |
| 907 | `aerowarptics.jei.info.folded_warp_crystal` | A Warp Crystal folded at a Collapse Cradle, for Rift Essence. The Mk III is built from it, so the third drive needs a cradle - and so a Relic Armillary from the ruins. | JEI information page for `folded_warp_crystal` |  |
| 908 | `aerowarptics.jei.info.handbook` | Every machine in this mod, in the order you would build them. Right-click to open it; the ribbons along the top are the chapters. | JEI information page for `handbook` |  |
| 909 | `aerowarptics.jei.info.opened_rift_lens` | A Rift Lens that has been through a rift. Blow a Rift Lens through an open rift with an Encased Fan: a Rift Fissure, a lit Rift Brazier, or an open Rift Gate's pane. Every machine that opens its own rift is built with one, from the Mk II drive up. | JEI information page for `opened_rift_lens` |  |
| 910 | `aerowarptics.jei.info.relic_armillary` | Three bronze rings, one inside the next: the heart of a Collapse Cradle, and the one part of it that nobody knows how to make. It can only be found, never crafted, in barrels in the ruins around rift scars, though not in every one. A Scar Chart leads to the nearest scar nobody has charted. | JEI information page for `relic_armillary` |  |
| 911 | `aerowarptics.jei.info.rift_beacon` | Bind a Rift Beacon by sneaking and right-clicking the Rift Drive of a ship you are standing on. After that, aim at anywhere within sight and right-click, and that ship warps there - you do not have to be aboard it, or anywhere near it. Permission is checked when you bind, so a bound beacon acts as a key: whoever holds it can call the ship. The warp still needs everything an ordinary warp needs: rotation, charge, range, and somewhere the hull fits. | JEI information page for `rift_beacon` |  |
| 912 | `aerowarptics.jei.info.rift_brazier` | Burns with rift fire while it has a redstone signal and essence, and rift fire counts as an open rift. Blow items through it with an Encased Fan to rift them, the same way you would wash them. Burns two millibuckets of Rift Essence a second while lit, whether or not anything is passing through. | JEI information page for `rift_brazier` |  |
| 913 | `aerowarptics.jei.info.rift_casing` | A Brass Casing with Warp Dust worked into it - by hand, or by a Deployer. Every machine in this mod that used to take a Brass Casing is built from one. | JEI information page for `rift_casing` |  |
| 914 | `aerowarptics.jei.info.rift_chute` | Pairs with another Rift Chute anywhere in the world, including one bolted to an airship. Items travel both ways along the pair. Fit a Create funnel to any face to fill it: a chute is an inventory, not a belt attachment. Whichever end sends pays the Rift Essence, charged per item, so a receiving chute needs no supply of its own. A full far end, an unloaded chunk or a ship mid-warp all make items wait in the chute instead of being lost. | JEI information page for `rift_chute` |  |
| 915 | `aerowarptics.jei.info.rift_core` | The machinery every Rift Drive is built around. | JEI information page for `rift_core` |  |
| 916 | `aerowarptics.jei.info.rift_coupler` | Bolted beside a Rift Drive, a Rift Coupler lets several ships warp together as a flotilla. Set one ship's coupler to Flagship and the others to Escort, then take station from each escort's screen: pick a flagship within reach, and its owner accepts (your own ships join without asking). On the flagship's Fleet Board, drag each escort's marker to give it a slot, then press Sound Stations. An escort that holds its slot with a full, turning drive harmonises with the flagship after a few seconds - listen for the hums settling into one note. When every row is ready, press Fold: every ship spins up, counts down, and follows the flagship through the rift, coming out in its slot. Hold Fold instead to go without the ships that are not ready; they are dropped from the flotilla. An escort that drifts out of its slot during the countdown is shaken loose and comes out a few seconds late, off to one side. Set Rift to One on the Fleet Board and the flagship opens one large rift for the whole flotilla instead, which flies in together, in formation, and comes out of one rift at the far end. Every drive still needs its own power and pays its own charge, and a flotilla costs more than the same ships flying alone. The flagship's coupler uses a little Rift Essence per escort during a fold. How many escorts a flagship may lead depends on its drive: one for a Mk I, two for a Mk II, four for a Mk III, and eight for a Singularity. A Display Link reads the coupler's flotilla, or its whole roster, one ship to a line. | JEI information page for `rift_coupler` |  |
| 917 | `aerowarptics.jei.info.rift_drive` | Built into an airship, a Rift Drive folds the whole hull to a Warp Anchor. Rotational force charges it; a jump spends a share of that charge, and costs more for distance and for weight. A drive standing on the ground has nothing to move. | JEI information page for `rift_drive` |  |
| 918 | `aerowarptics.jei.info.rift_essence` | What a rift sheds when a hull is folded through it. Caught by a Spatial Siphon aboard the ship, and spent dialling Rift Gates. | JEI information page for `rift_essence` |  |
| 919 | `aerowarptics.jei.info.rift_gate` | The one block of a Rift Gate that does the thinking. It counts as frame, so it can sit anywhere in the ring. Rift Essence pays to dial and to keep the doorway open, and rotational force holds it open - both only at the gate that dialled. If the tank runs dry or the shaft stalls, the gate disconnects. A second gate is needed in the same world to connect to. | JEI information page for `rift_gate` |  |
| 920 | `aerowarptics.jei.info.rift_gate_frame` | Builds the ring of a Rift Gate: standing upright, closed, and enclosing an empty opening of between four and two hundred and twenty-five blocks. That opening is the size limit for anything travelling through. | JEI information page for `rift_gate_frame` |  |
| 921 | `aerowarptics.jei.info.rift_goggles` | Create's goggles with a rift-ground lens. They read machines exactly as the engineer's goggles do, and they also show Rift Fissures: rifts in the world that are invisible to anyone not wearing them. | JEI information page for `rift_goggles` |  |
| 922 | `aerowarptics.jei.info.rift_lens` | Focuses a rift. Used by every machine here that has to aim one. | JEI information page for `rift_lens` |  |
| 923 | `aerowarptics.jei.info.rift_modulator` | Bolted beside a Rift Drive, a Rift Modulator lets its pilot choose the colour and theme of the rift the drive opens: the opening, the corridor, the exit shockwave, and even how the rift opens in the first place. Right-click for sixteen colours (left-click sets the core, right-click sets the rim) and a button that cycles through fifteen themes; shift-click the button to cycle backwards. Eight of the themes are nods to warp effects from other works, one is a portal you have almost certainly built, and one is drawn in pencil. Those eight and the Ender theme use their own colours instead of your swatch - hyperspace is blue and white whatever you pick - so the swatches are greyed out while one is chosen. Sketch keeps its paper and pencil look but is coloured in with your swatch. It uses a trickle of Rift Essence only while the linked drive is warping. If the tank runs dry, or the drive is removed, the rift goes back to the drive's own tier colours. A Display Link pointed at the Modulator reads the drive it is linked to, with the same eight readouts the drive itself offers. | JEI information page for `rift_modulator` |  |
| 924 | `aerowarptics.jei.info.rift_pearl` | 250 mB of Rift Essence in an ender pearl's shell, filled by a Spout. It is Rift Essence in item form, for recipes that cannot take a fluid. An Item Drain gets 200 mB back. Thrown, it teleports whatever mob or player it hits to somewhere within eight blocks, like eating a Chorus Fruit. It never affects bosses or anything aboard a ship. | JEI information page for `rift_pearl` |  |
| 925 | `aerowarptics.jei.info.rift_plotter` | The Collapse Cradle's sibling: a field generator three blocks across and three high, placed whole. A dish on a turntable faces the rift it holds, with an armillary ball spinning at its focus. It opens a rift beside itself and another around a target anywhere in the same world, for shipping or for war. Right-click it for its table: type the target's X and Z and press Survey, and the ground there appears as a model you can turn. Set the exit around the target: how far out, which way, how high, and how fast things leave it (their own speed, a set speed, or at rest). Open the Entry tab and the camera moves out beside the machine so you can set where the entry is, how wide, and - on the Face tab - which way it faces, with a hologram of it standing in the world. The Dock and Fire tabs set how it fires: a dock helper lines up a ship waiting behind the entry and launches it through, as far out and as far back as you set it to catch, and an outline in the air shows pilots where to wait; it can fire when a ship docks, on redstone, or hold the rift open as a lane that costs essence each second. It draws Rift Essence as it charges - there is no range limit, a distant target simply costs more - and must be turned at 64 RPM or more. Pipes connect to any block of its bottom layer. A shaft turns it through the middle of any side of that layer or from under its middle, and no rotation passes through the machine. Airships go through with their crew. Unless the server says otherwise, so do players, creatures, shells and TNT. A ship wider than the rift is turned back. Crouch aboard a docked ship to break free. The far rift stays shut until something is on its way through, then opens just before it arrives. Building one requires a Relic Armillary, which can only be found. | JEI information page for `rift_plotter` |  |
| 926 | `aerowarptics.jei.info.rift_probe` | Scans a direction and distance for ground nobody has stood on, and brings back the same map the Astrolabe draws of an anchor. It is the only way to reach somewhere unexplored. Rift Essence pays for each scan; a Rift Drive on the same ship flies the course it finds. | JEI information page for `rift_probe` |  |
| 927 | `aerowarptics.jei.info.rift_ward` | The third relic: a cracked stone totem bound in a roofless machine, three blocks across and three high, placed whole. It holds a field over the ground round it. A rift aimed into that field - a ship's arrival, a Rift Plotter's far end - comes out at the field's edge instead, and a scan of the ground under it comes back as interference. Its panel sets whom it turns: everyone, everyone but the networks you name, or only the networks you name. You are let through your own unless you tick Turn me too. Turn it at 32 RPM or more, with a shaft into the middle of any side of its bottom layer or from under its middle, and pipe in Rift Essence: it banks a charge, pays a little all the time for the field and a lot for each rift it turns, and its field drops when the charge runs out. While the field stands the totem's pieces hang apart, turn and glow, and the machine hums and gives off light. A Rift Storm thins the field, and a better drive is turned less far out. A comparator reads its charge; it gives a redstone pulse when it turns something aside. | JEI information page for `rift_ward` |  |
| 928 | `aerowarptics.jei.info.scar_chart` | Open it to turn it into a map to the nearest rift scar nobody has charted. A second chart leads somewhere else. | JEI information page for `scar_chart` |  |
| 929 | `aerowarptics.jei.info.scar_heart` | What a Rift Fissure leaves behind when it is emptied and seals. It can only be found, never crafted. A Singularity Core can only be collapsed using one. | JEI information page for `scar_heart` |  |
| 930 | `aerowarptics.jei.info.ship_compass` | A compass folded at a Collapse Cradle. It comes out unbound: sneak and use it on a Rift Drive to point it at that ship from then on, loaded or not. It can be bound again the same way. A cross-dimension warp rebuilds the ship, and the compass keeps pointing at where the ship was. | JEI information page for `ship_compass` |  |
| 931 | `aerowarptics.jei.info.singularity_core` | A core collapsed in on itself. Only the Singularity drive is built on one. | JEI information page for `singularity_core` |  |
| 932 | `aerowarptics.jei.info.spatial_siphon` | Carried aboard an airship, a siphon catches the Rift Essence that a completed warp sheds. Longer jumps give more, and every siphon aboard rolls for its own share. Holds four buckets, and pipes can draw the essence off. | JEI information page for `spatial_siphon` |  |
| 933 | `aerowarptics.jei.info.spatial_stabiliser` | Placed beside a Rift Drive on an airship, it softens sudden changes in the ship's motion while it has a redstone signal - light the burners and the ship eases upward instead of leaping. It stands by while the drive warps, and more than one aboard does not make it stronger. | JEI information page for `spatial_stabiliser` |  |
| 934 | `aerowarptics.jei.info.spatial_wave_detector` | Gives off a redstone pulse whenever something arrives through a rift nearby - a ship finishing a warp, or a player, mob or vehicle coming out of a Rift Gate. The closer the arrival, the stronger the signal, from 15 right beside it down to 1 at the edge of its range. Set the range by scrolling on the box on any side, with or without a wrench. It works aboard a ship too. | JEI information page for `spatial_wave_detector` |  |
| 935 | `aerowarptics.jei.info.stabiliser_ring` | Holds a warp corridor open while a hull is still inside it. | JEI information page for `stabiliser_ring` |  |
| 936 | `aerowarptics.jei.info.stormglass` | Made by a storm, it knows storms. Its crystals show one of four readings: clear, gathering (a storm within a day), raging, or still (none will come on its own). Break it to call a Rift Storm. This is refused while a storm rages, while the weather is frozen, too soon after the last storm, or where storms are turned off. | JEI information page for `stormglass` |  |
| 937 | `aerowarptics.jei.info.unset_stormglass` | A glass bottle filled with 500 mB of Rift Essence by a Spout. It only sets under open sky in a Rift Storm. It takes half a minute, either held by a player standing out in the storm or lying on the ground. | JEI information page for `unset_stormglass` |  |
| 938 | `aerowarptics.jei.info.warp_anchor` | Marks a place an airship can be sent. Right-click to name it and to choose who may travel there: everybody, only you, or the players in a network you put it on. The world keeps the record rather than the block, so an anchor stays a valid destination while its chunks are unloaded. Leave room for the arriving hull. | JEI information page for `warp_anchor` |  |
| 939 | `aerowarptics.jei.info.warp_glass` | Glass blown through an open rift with an Encased Fan. A building block that drops itself when broken. | JEI information page for `warp_glass` |  |

### Rift Gate passage styles (14)

**Seen:** the Passage button on the gate dial screen. The name is on the button; the `.about` line is the button's tooltip and is the only description of the animation the player gets.

**Edit in:** `en_us.json`. The style names are references to other works (Mario, Star Trek, 2001, Stargate, Star Wars, Doctor Who).

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 214 | `aerowarptics.gate.passage.down_the_pipe` | Down the Pipe | Name of a gate passage style (the animation a player sees going through) |  |
| 215 | `aerowarptics.gate.passage.down_the_pipe.about` | An iris shuts, a green pipe goes by in big square pixels, and an iris opens on the far side. | Tooltip describing that style - the only preview the player gets |  |
| 216 | `aerowarptics.gate.passage.energise` | Energise | Passage style name |  |
| 217 | `aerowarptics.gate.passage.energise.about` | No tunnel. A column of light and a shimmer of gold and blue motes that thickens to white, then thins into the far side. | Tooltip describing that style |  |
| 218 | `aerowarptics.gate.passage.full_of_stars` | Full of Stars | Passage style name |  |
| 219 | `aerowarptics.gate.passage.full_of_stars.about` | A corridor of light: two planes of every colour streaming out of a burning slit, turning as they come. | Tooltip describing that style |  |
| 220 | `aerowarptics.gate.passage.kawoosh` | Kawoosh | Passage style name |  |
| 221 | `aerowarptics.gate.passage.kawoosh.about` | The pane's light bursts over you, then a twisting conduit of blue and violet plasma to a flare at the far end. | Tooltip describing that style |  |
| 222 | `aerowarptics.gate.passage.punch_it` | Punch It | Passage style name |  |
| 223 | `aerowarptics.gate.passage.punch_it.about` | The stars hold for a breath, pull out into lines, and then there is only the blue-white lane rushing past. | Tooltip describing that style |  |
| 224 | `aerowarptics.gate.passage.quiet` | Quiet | Passage style name |  |
| 225 | `aerowarptics.gate.passage.quiet.about` | No tunnel and no motion. The view dims, holds, and brightens into the far side. | Tooltip describing that style |  |
| 226 | `aerowarptics.gate.passage.timey_wimey` | Timey Wimey | Passage style name |  |
| 227 | `aerowarptics.gate.passage.timey_wimey.about` | A spiral of storm cloud, ember red sliding to cold blue, turning hard with lightning down its length. | Tooltip describing that style |  |

## 2. Names

### Block, item and tier names (64)

**Seen:** inventory, tooltips, JEI, and anywhere a screen or sentence names the thing. Renaming one here does not change the sentences elsewhere that spell the name out (handbook, Ponder, JEI pages, advancements) - search for the old name after renaming.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 1213 | `aerowarptics.tier.creative` | Creative Rift Drive | Drive tier name used inside sentences and on screens (not the block name) |  |
| 1214 | `aerowarptics.tier.mk_i` | Rift Drive Mk I | Drive tier name, as above |  |
| 1215 | `aerowarptics.tier.mk_ii` | Rift Drive Mk II | Drive tier name, as above |  |
| 1216 | `aerowarptics.tier.mk_iii` | Rift Drive Mk III | Drive tier name, as above |  |
| 1217 | `aerowarptics.tier.singularity` | Singularity Rift Drive | Drive tier name, as above |  |
| 1269 | `block.aerowarptics.astrolabe` | Astrolabe Cartography Table | Name |  |
| 1270 | `block.aerowarptics.collapse_cradle` | Collapse Cradle | Name | CollapseCradleBlockEntity |
| 1271 | `block.aerowarptics.collapse_cradle_base` | Collapse Cradle | Name of an internal block of the 3x3x3 machine (seen in block-info overlays) |  |
| 1272 | `block.aerowarptics.collapse_cradle_part` | Collapse Cradle | Name of an internal block of the 3x3x3 machine |  |
| 1273 | `block.aerowarptics.cooling_vent` | Cooling Vent | Name |  |
| 1274 | `block.aerowarptics.drive_extender` | Drive Extender | Name |  |
| 1275 | `block.aerowarptics.rift_brazier` | Rift Brazier | Name |  |
| 1276 | `block.aerowarptics.rift_casing` | Rift Casing | Name |  |
| 1277 | `block.aerowarptics.rift_chute` | Rift Chute | Name |  |
| 1278 | `block.aerowarptics.rift_coupler` | Rift Coupler | Name |  |
| 1279 | `block.aerowarptics.rift_drive_creative` | Creative Rift Drive | Name |  |
| 1280 | `block.aerowarptics.rift_drive_mk_i` | Rift Drive Mk I | Name |  |
| 1281 | `block.aerowarptics.rift_drive_mk_ii` | Rift Drive Mk II | Name |  |
| 1282 | `block.aerowarptics.rift_drive_mk_iii` | Rift Drive Mk III | Name |  |
| 1283 | `block.aerowarptics.rift_drive_singularity` | Singularity Rift Drive | Name |  |
| 1284 | `block.aerowarptics.rift_essence` | Rift Essence | Name of the fluid when placed in the world |  |
| 1285 | `block.aerowarptics.rift_fissure` | Rift Fissure | Name |  |
| 1286 | `block.aerowarptics.rift_gate` | Rift Gate | Name |  |
| 1287 | `block.aerowarptics.rift_gate_frame` | Rift Gate Frame | Name |  |
| 1288 | `block.aerowarptics.rift_modulator` | Rift Modulator | Name |  |
| 1289 | `block.aerowarptics.rift_plotter` | Rift Plotter | Name |  |
| 1290 | `block.aerowarptics.rift_plotter_base` | Rift Plotter | Name of an internal block of the 3x3x3 machine |  |
| 1291 | `block.aerowarptics.rift_plotter_part` | Rift Plotter | Name of an internal block of the 3x3x3 machine |  |
| 1292 | `block.aerowarptics.rift_portal` | Rift Portal | Name of the glowing pane inside an open Rift Gate |  |
| 1293 | `block.aerowarptics.rift_probe` | Rift Probe | Name |  |
| 1294 | `block.aerowarptics.rift_ward` | Rift Ward | Name |  |
| 1295 | `block.aerowarptics.rift_ward_base` | Rift Ward | Name of an internal block of the 3x3x3 machine |  |
| 1296 | `block.aerowarptics.rift_ward_part` | Rift Ward | Name of an internal block of the 3x3x3 machine |  |
| 1297 | `block.aerowarptics.spatial_siphon` | Spatial Siphon | Name |  |
| 1298 | `block.aerowarptics.spatial_stabiliser` | Spatial Stabiliser | Name |  |
| 1299 | `block.aerowarptics.spatial_wave_detector` | Spatial Wave Detector | Name |  |
| 1300 | `block.aerowarptics.warp_anchor` | Warp Anchor | Name |  |
| 1301 | `block.aerowarptics.warp_glass` | Warp Glass | Name |  |
| 1368 | `entity.aerowarptics.rift_pearl` | Rift Pearl | Name |  |
| 1369 | `filled_map.aerowarptics.rift_scar` | Scar Chart | Name of the map a Scar Chart turns into | ScarChartItem |
| 1370 | `fluid_type.aerowarptics.rift_essence` | Rift Essence | Name |  |
| 1371 | `item.aerowarptics.folded_warp_crystal` | Folded Warp Crystal | Name |  |
| 1372 | `item.aerowarptics.handbook` | Navigator's Handbook | Name |  |
| 1373 | `item.aerowarptics.incomplete_rift_core` | Incomplete Rift Core | Name |  |
| 1374 | `item.aerowarptics.opened_rift_lens` | Opened Rift Lens | Name |  |
| 1375 | `item.aerowarptics.pearlescent_warp_crystal` | Pearlescent Warp Crystal | Name |  |
| 1376 | `item.aerowarptics.relic_armillary` | Relic Armillary | Name |  |
| 1377 | `item.aerowarptics.rift_beacon` | Rift Beacon | Name |  |
| 1378 | `item.aerowarptics.rift_core` | Rift Core | Name |  |
| 1379 | `item.aerowarptics.rift_essence_bucket` | Bucket of Rift Essence | Name |  |
| 1380 | `item.aerowarptics.rift_goggles` | Rift Infused Goggles | Name |  |
| 1381 | `item.aerowarptics.rift_lens` | Rift Lens | Name |  |
| 1382 | `item.aerowarptics.rift_pearl` | Rift Pearl | Name |  |
| 1383 | `item.aerowarptics.scar_chart` | Scar Chart | Name |  |
| 1384 | `item.aerowarptics.scar_heart` | Scar Heart | Name |  |
| 1385 | `item.aerowarptics.ship_compass` | Ship's Compass | Name |  |
| 1386 | `item.aerowarptics.singularity_core` | Singularity Core | Name |  |
| 1387 | `item.aerowarptics.stabiliser_ring` | Stabiliser Ring | Name |  |
| 1388 | `item.aerowarptics.stormglass` | Stormglass | Name |  |
| 1389 | `item.aerowarptics.unset_stormglass` | Unset Stormglass | Name |  |
| 1390 | `item.aerowarptics.warp_crystal` | Warp Crystal | Name |  |
| 1391 | `item.aerowarptics.warp_dust` | Warp Dust | Name |  |
| 1392 | `item.aerowarptics.warp_shard` | Warp Shard | Name |  |
| 1393 | `itemGroup.aerowarptics` | AeroWarptics | Name of the creative inventory tab | AWCreativeTab |

## 3. Machines: screens, goggles tooltips and messages

Mostly short labels, buttons and status lines. Many are held to a pixel width by a test (named in each section); a longer replacement can fail the build. `%s` / `%1$s` are slots the game fills with a number or name: keep every slot, and keep `%1$s`-style ones numbered as they are.

### Rift Drive console (72)

**Seen:** right-click a Rift Drive. `gui.rift_navigation.*` is an older key family used by the same console and by the Astrolabe chart. Drive states also show in goggles and on Display Links.

**Limit:** `ConsoleTextTest` measures every line against its panel.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 127 | `aerowarptics.drive.heading.back` | Back | Bow setting on the drive console: ship's front is the drive's back face |  |
| 128 | `aerowarptics.drive.heading.forward` | Forward | Bow setting: ship's front is the drive's front face |  |
| 129 | `aerowarptics.drive.heading.left` | Left | Bow setting: ship's front is to the drive's left |  |
| 130 | `aerowarptics.drive.heading.right` | Right | Bow setting: ship's front is to the drive's right |  |
| 131 | `aerowarptics.drive.mode.free_cruise` | Free Cruise | Drive mode name: fly the ship fast under manual steering |  |
| 132 | `aerowarptics.drive.mode.waypoint` | Waypoint | Drive mode name: jump to the set course |  |
| 133 | `aerowarptics.drive.state.arriving` | Arriving | Drive state shown on the console, goggles and displays: coming out of the rift |  |
| 134 | `aerowarptics.drive.state.charged` | Charged | Drive state: full, ready to warp |  |
| 135 | `aerowarptics.drive.state.charging` | Charging | Drive state: building charge |  |
| 136 | `aerowarptics.drive.state.cooldown` | Cooling Down | Drive state: recovering after a warp |  |
| 137 | `aerowarptics.drive.state.cruising` | Cruising | Drive state: flying in Free Cruise | RiftDriveBlockEntity, RiftDriveConsoleScreen |
| 138 | `aerowarptics.drive.state.destination_selected` | Destination Set | Drive state: a course has been chosen |  |
| 139 | `aerowarptics.drive.state.error` | Fault | Drive state: the last warp failed |  |
| 140 | `aerowarptics.drive.state.idle` | Idle | Drive state: doing nothing |  |
| 141 | `aerowarptics.drive.state.stabilizing` | Stabilising | Drive state: rift opening, just before the jump |  |
| 142 | `aerowarptics.drive.state.warping` | In Warp | Drive state: in the rift |  |
| 471 | `aerowarptics.gui.rift_drive.awaiting_landing` | Waiting for the landing to load | Console status: arrival chunks still loading | RiftDriveBlockEntity |
| 472 | `aerowarptics.gui.rift_drive.charge` | Charge: %s | Goggles line: charge percent | RiftDriveBlockEntity |
| 473 | `aerowarptics.gui.rift_drive.clearance_hint` | Before a warp, the drive checks that nothing stands in the ship's way as it sets off, and searches for clear ground to set it down on - refusing the jump when it cannot find any. Switch this off and it does neither: the ship flies straight out and comes in exactly at the course's target and height, into whatever is standing there. A free cruise still slows for terrain ahead. | Tooltip on the Clearance checks button | RiftDriveConsoleScreen |
| 474 | `aerowarptics.gui.rift_drive.clearance_off` | Clearance checks: Off | Console button: obstacle checks off | RiftDriveConsoleScreen |
| 475 | `aerowarptics.gui.rift_drive.clearance_off_line` | Clearance checks off | Goggles / console line reminding that checks are off | RiftDriveBlockEntity |
| 476 | `aerowarptics.gui.rift_drive.clearance_on` | Clearance checks: On | Console button: obstacle checks on | RiftDriveConsoleScreen |
| 477 | `aerowarptics.gui.rift_drive.cooling` | Cooling: vents %1$s, ice %2$s - %3$s of the cooldown | Goggles line: number of vents, number of ice blocks, resulting cooldown fraction | RiftDriveBlockEntity |
| 478 | `aerowarptics.gui.rift_drive.cruise_reach` | Free cruise: up to %s | Console line in Free Cruise: how far the current charge will fly | AWLang |
| 479 | `aerowarptics.gui.rift_drive.cruise_reach_infinite` | Free cruise: infinite range | Same line for a Creative drive | AWLang |
| 480 | `aerowarptics.gui.rift_drive.cruise_speed` | Cruising at %s blocks/s | Console line while cruising: speed | RiftDriveBlockEntity, RiftDriveConsoleScreen |
| 481 | `aerowarptics.gui.rift_drive.cruised` | Flown %s of %s | Console line while cruising: distance flown of the possible distance | AWLang |
| 482 | `aerowarptics.gui.rift_drive.cruised_infinite` | Flown %s, range infinite | Same line for a Creative drive | AWLang |
| 483 | `aerowarptics.gui.rift_drive.disengage` | Disengage | Console button: stop a Free Cruise | RiftDriveConsoleScreen |
| 484 | `aerowarptics.gui.rift_drive.dropping_out` | Dropping out of cruise | Console status: braking out of Free Cruise | RiftDriveBlockEntity, RiftDriveConsoleScreen |
| 485 | `aerowarptics.gui.rift_drive.frame` | Extended: %s x %s x %s | Goggles line when Drive Extenders are fitted: cube size | RiftDriveBlockEntity |
| 486 | `aerowarptics.gui.rift_drive.held_by` | Held by: %s | Goggles / dry-run line: what keeps the ship's chunks loaded (debug-flavoured) | RiftDriveConsoleScreen |
| 487 | `aerowarptics.gui.rift_drive.loading_ahead` | Waiting for the land ahead to load | Console status in Free Cruise: slowed, terrain ahead not loaded | RiftDriveBlockEntity, RiftDriveConsoleScreen |
| 488 | `aerowarptics.gui.rift_drive.mass` | Hull mass | Console label: ship's block count | RiftDriveConsoleScreen |
| 489 | `aerowarptics.gui.rift_drive.mode_hint` | Waypoint jumps to the course set at an Astrolabe or a Rift Probe. Free Cruise flies the ship itself, fast, wherever its bow points - steer it as you normally would. Redstone strength is the throttle: raise it to engage, cut it (or press Disengage) to brake to a stop. The charge drains with every block flown, and the ship slows for terrain ahead and for land that has not loaded yet. | Tooltip on the Mode button; explains both modes | RiftDriveConsoleScreen |
| 490 | `aerowarptics.gui.rift_drive.mode_line` | Mode: %s | Console button / line: current mode | RiftDriveBlockEntity |
| 491 | `aerowarptics.gui.rift_drive.req.charge` | Fully charged | Requirements checklist row: drive fully charged | RiftDriveConsoleScreen |
| 492 | `aerowarptics.gui.rift_drive.req.course` | Course set | Checklist row: a destination is set | RiftDriveConsoleScreen |
| 493 | `aerowarptics.gui.rift_drive.req.cruise_reach` | Charge enough to set off | Checklist row (Free Cruise): enough charge to start | RiftDriveConsoleScreen |
| 494 | `aerowarptics.gui.rift_drive.req.cruise_signal` | Awaiting redstone throttle | Checklist row (Free Cruise): waiting for a redstone signal | RiftDriveConsoleScreen |
| 495 | `aerowarptics.gui.rift_drive.req.fastened` | Ship is tied down | Checklist row: ship is attached to something (fails the check) | RiftDriveConsoleScreen |
| 496 | `aerowarptics.gui.rift_drive.req.reachable` | Destination in reach | Checklist row: destination within range | RiftDriveConsoleScreen |
| 497 | `aerowarptics.gui.rift_drive.req.rotation` | Rotational force | Checklist row: shaft turning fast enough | RiftDriveConsoleScreen |
| 498 | `aerowarptics.gui.rift_drive.req.signal` | Awaiting redstone | Checklist row: waiting for the redstone pulse that launches | RiftDriveConsoleScreen |
| 499 | `aerowarptics.gui.rift_drive.req.signal_disabled` | Redstone start disabled | Checklist row: server has turned redstone launching off | RiftDriveConsoleScreen |
| 500 | `aerowarptics.gui.rift_drive.req.vessel` | Aboard a vessel | Checklist row: drive is on an assembled airship | RiftDriveConsoleScreen |
| 501 | `aerowarptics.gui.rift_drive.required_rpm` | Requires %s RPM | Goggles line: minimum speed | RiftDriveBlockEntity |
| 502 | `aerowarptics.gui.rift_drive.requirements` | Requirements | Heading of the checklist | RiftDriveConsoleScreen |
| 503 | `aerowarptics.gui.rift_drive.ship_range` | Ship Range | Console label: how far this ship can warp at its weight | RiftDriveConsoleScreen |
| 504 | `aerowarptics.gui.rift_drive.ship_range_none` | Too heavy | Value of Ship Range when the ship is too heavy to warp at all | AstrolabeChartScreen, RiftDriveConsoleScreen |
| 505 | `aerowarptics.gui.rift_drive.speed` | Speed | Console label: shaft speed | RiftDriveConsoleScreen, RiftGateDialScreen |
| 506 | `aerowarptics.gui.rift_drive.spin` | Spinning up | Console status: the few seconds of spin-up before the rift opens | RiftDriveConsoleScreen |
| 507 | `aerowarptics.gui.rift_drive.stress` | Stress | Console label: stress the drive puts on the network | RiftDriveConsoleScreen |
| 508 | `aerowarptics.gui.rift_drive.terrain_ahead` | Terrain ahead - slowing | Console status in Free Cruise: slowing for an obstacle | RiftDriveBlockEntity, RiftDriveConsoleScreen |
| 509 | `aerowarptics.gui.rift_drive.title` | Rift Drive | Screen title | RiftDriveConsoleScreen |
| 561 | `aerowarptics.gui.rift_navigation.abort` | Abort | Console button: cancel a warp that has not jumped yet | RiftDriveConsoleScreen |
| 562 | `aerowarptics.gui.rift_navigation.bow` | Bow: %s (%s) | Console button: which way is the ship's front. %s = setting, (%s) = compass direction | RiftDriveConsoleScreen |
| 563 | `aerowarptics.gui.rift_navigation.bow_hint` | Which way the drive throws the ship, measured a quarter turn at a time from its own front face. It turns with the hull, so it stays true however the vessel is lying. The needle on the machine shows the same setting. | Tooltip on that button | RiftDriveConsoleScreen |
| 564 | `aerowarptics.gui.rift_navigation.charge` | Drive Charge | Console / chart label: charge bar | RiftDriveConsoleScreen, StationScreen |
| 565 | `aerowarptics.gui.rift_navigation.close` | Close | Close button | RiftDriveConsoleScreen |
| 566 | `aerowarptics.gui.rift_navigation.cooldown` | Cooldown %ss | No reference found in the code - may be unused |  |
| 567 | `aerowarptics.gui.rift_navigation.cooldown_short` | Cooldown | Console label: cooldown bar | RiftDriveConsoleScreen |
| 568 | `aerowarptics.gui.rift_navigation.cost` | Warp Cost | Console / chart label: charge this warp will use | AstrolabeChartScreen |
| 569 | `aerowarptics.gui.rift_navigation.distance` | Distance | Console / chart label: distance to the destination | AstrolabeChartScreen |
| 570 | `aerowarptics.gui.rift_navigation.no_anchors` | No anchors in range | Chart: no destinations in range | AstrolabeChartScreen |
| 571 | `aerowarptics.gui.rift_navigation.no_selection` | No destination selected | Console: no course set | AstrolabeChartScreen |
| 572 | `aerowarptics.gui.rift_navigation.none` | None | Generic "none" value on the console | AstrolabeChartScreen |
| 573 | `aerowarptics.gui.rift_navigation.range` | Max Range | Console / chart label: drive's maximum range | RiftDriveConsoleScreen |
| 574 | `aerowarptics.gui.rift_navigation.status` | Status | No reference found in the code - may be unused |  |
| 575 | `aerowarptics.gui.rift_navigation.title` | Rift Navigation | No reference found in the code - may be unused |  |
| 576 | `aerowarptics.gui.rift_navigation.unnamed_ship` | Unnamed vessel | Name shown for a ship nobody has named (console, escort screen, beacon) | AstrolabeChartScreen, CouplerScreen, FleetNotices |
| 949 | `aerowarptics.message.warp_engaged` | Rift stabilised. Stand by. | Action bar at the moment a warp is accepted and begins | ClientRuntime |

### Warp refusals and failures (48)

**Seen:** when a warp is refused or fails: in red on the action bar, as the console's status, on a Display Link's "Fault" reading, and as the Rift Beacon's reply. One line per reason. The `fastening` lines add detail to "The ship is tied down".

**Limit:** `ConsoleTextTest` holds the longest refusal to the console's width.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 1218 | `aerowarptics.warp.failure.airship_already_warping` | This airship is already warping | Warp refusal: another drive on the ship is already warping it |  |
| 1219 | `aerowarptics.warp.failure.airship_lost` | Airship lost during warp | Warp failure mid-flight: the ship disappeared (unloaded or disassembled) |  |
| 1220 | `aerowarptics.warp.failure.anchor_disabled` | Anchor is offline | Warp refusal: destination anchor is switched off |  |
| 1221 | `aerowarptics.warp.failure.anchor_forbidden` | Anchor is private | Warp refusal: you may not use that anchor |  |
| 1222 | `aerowarptics.warp.failure.anchor_missing` | Anchor not found | Warp refusal: anchor was removed |  |
| 1223 | `aerowarptics.warp.failure.anchor_unavailable` | Destination unavailable | Warp refusal: anchor is busy with another arrival |  |
| 1224 | `aerowarptics.warp.failure.arrival_not_loaded` | The landing zone is not kept loaded | Warp refusal: destination chunks could not be kept loaded |  |
| 1225 | `aerowarptics.warp.failure.arrival_unproven` | The landing could not be checked in full | Warp refusal: could not finish checking the arrival space for obstacles |  |
| 1226 | `aerowarptics.warp.failure.beacon_drive_missing` | The bound drive is gone or out of reach | Beacon refusal: its ship's drive was removed or cannot be loaded |  |
| 1227 | `aerowarptics.warp.failure.beacon_unbound` | This beacon is not bound to a ship | Beacon refusal: not bound |  |
| 1228 | `aerowarptics.warp.failure.cancelled` | Warp cancelled | Warp was aborted by the player |  |
| 1229 | `aerowarptics.warp.failure.destination_too_close` | Destination too close | Warp refusal: under the minimum distance |  |
| 1230 | `aerowarptics.warp.failure.destination_too_far` | Beyond drive range | Warp refusal: beyond this drive's range for this ship |  |
| 1231 | `aerowarptics.warp.failure.destination_warded` | Destination is warded: no room at its edge | Warp refusal: a Rift Ward covers the destination and there is no space at its edge |  |
| 1232 | `aerowarptics.warp.failure.dimension_no_room` | No room for this ship in that dimension | Cross-dimension refusal: no space found there |  |
| 1233 | `aerowarptics.warp.failure.dimension_too_tall` | This ship is too tall for that dimension | Cross-dimension refusal: ship taller than that dimension |  |
| 1234 | `aerowarptics.warp.failure.dimension_unsupported` | Other dimensions are out of reach | Cross-dimension refusal: needs the Dimensional Sable mod, or disabled |  |
| 1235 | `aerowarptics.warp.failure.drive_busy` | Drive unavailable | Warp refusal: drive is cooling down or mid-warp |  |
| 1236 | `aerowarptics.warp.failure.flotilla_not_ready` | The flotilla is not ready. See the Fleet Board | Warp refusal on a flagship: escorts not ready |  |
| 1237 | `aerowarptics.warp.failure.fold_called_off` | The flagship called off the fold | Shown to escorts when the flagship cancels |  |
| 1238 | `aerowarptics.warp.failure.insufficient_charge` | Insufficient charge | Warp refusal: not enough charge for this distance and weight |  |
| 1239 | `aerowarptics.warp.failure.insufficient_essence` | Not enough Rift Essence | Warp refusal: a fold needs essence in the coupler |  |
| 1240 | `aerowarptics.warp.failure.insufficient_power` | Insufficient rotational force | Warp refusal: shaft too slow |  |
| 1241 | `aerowarptics.warp.failure.interrupted` | Warp interrupted | Warp failure mid-flight: something stopped it (e.g. rotation lost) |  |
| 1242 | `aerowarptics.warp.failure.launch_unproven` | The launch path could not be checked in full | Warp refusal: could not finish checking the path in front of the ship |  |
| 1243 | `aerowarptics.warp.failure.no_airship` | Drive is not aboard an airship | Warp refusal: the drive is not on an assembled airship |  |
| 1244 | `aerowarptics.warp.failure.no_clear_launch` | The launch path ahead is blocked | Warp refusal: blocks in front of the ship |  |
| 1245 | `aerowarptics.warp.failure.no_safe_arrival` | No safe arrival point | Warp refusal: no clear space for the ship near the destination |  |
| 1246 | `aerowarptics.warp.failure.none` | Ready | Console status when nothing is wrong |  |
| 1247 | `aerowarptics.warp.failure.relocation_failed` | The physics engine refused to move the ship | Warp failure: Sable (the physics mod) would not move the ship |  |
| 1248 | `aerowarptics.warp.failure.ship_fastened` | The ship is tied down | Warp refusal: ship attached to the ground or another ship |  |
| 1249 | `aerowarptics.warp.failure.ship_out_of_reach` | The ship could not be reached where it was last seen | Beacon refusal: the ship could not be loaded where it was last saved |  |
| 1250 | `aerowarptics.warp.failure.unauthorised` | You are not authorised | Warp refusal: you lack permission to use this drive |  |
| 1251 | `aerowarptics.warp.fastening.at` | %s at %s, %s, %s | Detail added to "The ship is tied down": what and where. kind, x, y, z | FasteningText |
| 1252 | `aerowarptics.warp.fastening.end.carried` | %s, which can't go through a rift | Detail: the attached thing cannot be taken through a rift. %s = kind |  |
| 1253 | `aerowarptics.warp.fastening.end.ground` | %s, fastened to the ground | Detail: attached to the ground |  |
| 1254 | `aerowarptics.warp.fastening.end.other_ship` | %s, fastened to another ship | Detail: attached to another ship |  |
| 1255 | `aerowarptics.warp.fastening.kind.docking_connector` | Docking Connector | Name of an attachment type from Create Aeronautics / Simulated |  |
| 1256 | `aerowarptics.warp.fastening.kind.merging_glue` | Merging Glue | Attachment type name |  |
| 1257 | `aerowarptics.warp.fastening.kind.physics_paused` | Sable physics | Attachment type name: Sable's physics is paused |  |
| 1258 | `aerowarptics.warp.fastening.kind.physics_staff` | Physics Staff lock | Attachment type name |  |
| 1259 | `aerowarptics.warp.fastening.kind.plunger` | Plunger | Attachment type name |  |
| 1260 | `aerowarptics.warp.fastening.kind.rope` | Rope | Attachment type name |  |
| 1261 | `aerowarptics.warp.fastening.kind.spring` | Spring | Attachment type name |  |
| 1262 | `aerowarptics.warp.fastening.kind.sticker` | Sticker | Attachment type name |  |
| 1263 | `aerowarptics.warp.fastening.kind.swivel_bearing` | Swivel Bearing | Attachment type name |  |
| 1264 | `aerowarptics.warp.fastening.paused` | Sable physics is paused | Detail when the cause is paused physics rather than a block | FasteningText |
| 1265 | `aerowarptics.warp.fastening.reason` | %s: %s | Joins the refusal and its detail: "<refusal>: <detail>" | ClientRuntime |

### Astrolabe chart (23)

**Seen:** right-click an Astrolabe table (the map of destinations), plus its goggles tooltip.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 234 | `aerowarptics.gui.astrolabe.anchors` | anchors | Word after the count in the chart's header, e.g. "12 anchors" | AstrolabeChartScreen |
| 235 | `aerowarptics.gui.astrolabe.anchors_capped` | nearest %s of %s anchors | Chart header when the list is cut short. %s shown of %s total | AstrolabeChartScreen |
| 236 | `aerowarptics.gui.astrolabe.contours` | %s m contours | Chart legend: height difference between contour lines. %s = metres | AstrolabeChartScreen |
| 237 | `aerowarptics.gui.astrolabe.course` | Course: | Label in front of the current course on the chart's course strip (value drawn separately) | AstrolabeChartScreen |
| 238 | `aerowarptics.gui.astrolabe.course_named` | Course: %s | Goggles line on the Astrolabe: current course. %s = destination name | AstrolabeBlockEntity |
| 239 | `aerowarptics.gui.astrolabe.course_none` | none set | Value shown beside "Course:" when there is none | AstrolabeChartScreen |
| 240 | `aerowarptics.gui.astrolabe.course_set` | Course set | Confirmation after clicking a destination | AstrolabeChartScreen |
| 241 | `aerowarptics.gui.astrolabe.course_sounding` | Rift Probe scan | Name shown for a course that came from a Rift Probe scan instead of an anchor | AstrolabeChartScreen |
| 242 | `aerowarptics.gui.astrolabe.ground` | Ground | Label of the ground-survey panel beside the chart | AstrolabeChartScreen, RiftProbeScreen |
| 243 | `aerowarptics.gui.astrolabe.hold_altitude` | Hold altitude | Checkbox on the chart: arrive at the ship's current height | AstrolabeChartScreen |
| 244 | `aerowarptics.gui.astrolabe.hold_altitude_no_course` | Set a course first. | Tooltip on that checkbox when no course is set | AstrolabeChartScreen |
| 245 | `aerowarptics.gui.astrolabe.hold_altitude_other_dimension` | Ignored on this course: it leads to another dimension. | Tooltip on that checkbox when the course is in another dimension | AstrolabeChartScreen |
| 246 | `aerowarptics.gui.astrolabe.hold_altitude_tip` | Arrive at the height the ship is flying at when the rift opens, instead of the destination's own arrival height. Never lower than the destination itself. | Tooltip explaining the checkbox | AstrolabeChartScreen |
| 247 | `aerowarptics.gui.astrolabe.incomplete` | Incomplete table - lay the blocks out in a square | Goggles line / message when the Astrolabe blocks do not form a square | AstrolabeBlock, AstrolabeBlockEntity |
| 248 | `aerowarptics.gui.astrolabe.no_course` | No course set | Goggles line on the Astrolabe when no course is set | AstrolabeBlockEntity, FleetBoardScreen, RiftDriveConsoleScreen |
| 249 | `aerowarptics.gui.astrolabe.no_drive` | No Rift Drive on this vessel | Shown on the chart when the table's ship has no Rift Drive | AstrolabeBlockEntity, AstrolabeChartScreen |
| 250 | `aerowarptics.gui.astrolabe.no_selection` | Select a destination | Detail panel prompt before a destination is clicked | AstrolabeChartScreen |
| 251 | `aerowarptics.gui.astrolabe.range_of` | %s of %s | Distance to the destination against the drive's range. %s of %s | AstrolabeChartScreen |
| 252 | `aerowarptics.gui.astrolabe.reachable` | In range - click to set course | Hint under a selected destination that is within range | AstrolabeChartScreen |
| 253 | `aerowarptics.gui.astrolabe.scale` | %s m wide | Chart legend: how much ground the map covers. %s = metres | AstrolabeChartScreen, RiftProbeScreen |
| 254 | `aerowarptics.gui.astrolabe.surveying` | Surveying... | Shown while the ground survey of a destination loads | AstrolabeChartScreen |
| 255 | `aerowarptics.gui.astrolabe.title` | Astrolabe Chart | Screen title | AstrolabeChartScreen |
| 256 | `aerowarptics.gui.astrolabe.unsurveyed` | Unsurveyed | Shown where no ground survey exists for the destination | AstrolabeChartScreen |

### Warp Anchor (23)

**Seen:** right-click a Warp Anchor, its goggles tooltip, and the chart's detail panel for a selected anchor.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 66 | `aerowarptics.anchor.access.network` | Network | Access setting: only members of the anchor's network may warp to it |  |
| 67 | `aerowarptics.anchor.access.private` | Private | Access setting: only the owner may warp to it |  |
| 68 | `aerowarptics.anchor.access.public` | Public | Access setting: anyone may warp to it |  |
| 69 | `aerowarptics.anchor.status.cooldown` | Settling | Anchor state: a ship has just arrived and the anchor is recovering |  |
| 70 | `aerowarptics.anchor.status.destination_lock` | Locked | Anchor state: a drive has locked on and is about to warp here |  |
| 71 | `aerowarptics.anchor.status.error` | Fault | Anchor state: something is wrong with it |  |
| 72 | `aerowarptics.anchor.status.offline` | Offline | Anchor state: switched off, not a valid destination |  |
| 73 | `aerowarptics.anchor.status.online` | Online | Anchor state: normal, can be warped to |  |
| 74 | `aerowarptics.anchor.status.warp_active` | Receiving | Anchor state: a ship is on its way in right now |  |
| 715 | `aerowarptics.gui.warp_anchor.arrival_height` | Ships arrive above it | Anchor screen: label of the slider for how high ships arrive above it | WarpAnchorScreen |
| 716 | `aerowarptics.gui.warp_anchor.disabled` | Offline | Anchor screen: on/off button, off | WarpAnchorScreen |
| 717 | `aerowarptics.gui.warp_anchor.enabled` | Online | Anchor screen: on/off button, on | WarpAnchorScreen |
| 718 | `aerowarptics.gui.warp_anchor.name` | Name | Anchor screen: name field label | WarpAnchorScreen |
| 719 | `aerowarptics.gui.warp_anchor.network` | Network | Anchor screen: network name field label | AstrolabeChartScreen, NetworkScreen, RiftGateDialScreen |
| 720 | `aerowarptics.gui.warp_anchor.on_network` | Network: %s | Anchor goggles / chart detail: its network | WarpAnchorBlockEntity |
| 721 | `aerowarptics.gui.warp_anchor.owner` | Owner: %s | Anchor goggles / chart detail: its owner | NetworkScreen, WarpAnchorBlockEntity |
| 722 | `aerowarptics.gui.warp_anchor.save` | Save | Save button | WarpAnchorScreen |
| 723 | `aerowarptics.gui.warp_anchor.title` | Warp Anchor | Screen title | WarpAnchorScreen |
| 724 | `aerowarptics.gui.warp_anchor.unowned` | Unowned | Shown in place of an owner name when there is none | WarpAnchorScreen |
| 940 | `aerowarptics.message.anchor_limit_reached` | You have reached this server's Warp Anchor limit | Action bar when placing an anchor over the server's per-player limit | WarpAnchorBlockEntity |
| 941 | `aerowarptics.message.anchor_name_taken` | That anchor name is already in use | Reply when saving an anchor with a name already in use | ServerboundConfigureAnchorPacket |
| 942 | `aerowarptics.message.anchor_not_yours` | You do not own this anchor | Reply when editing someone else's anchor | ServerboundConfigureAnchorPacket |
| 943 | `aerowarptics.message.anchor_saved` | Anchor '%s' saved | Reply after saving an anchor. %s = name | ServerboundConfigureAnchorPacket |

### Rift Probe (43)

**Seen:** right-click a Rift Probe; states and results also show in goggles and on Display Links.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 654 | `aerowarptics.gui.rift_probe.aboard` | Scanning from this ship | Probe screen: it is on a ship, so a result can be flown to | RiftProbeScreen |
| 655 | `aerowarptics.gui.rift_probe.aim` | Aimed: %s, %s | Probe screen / goggles: target coordinates | RiftProbeBlockEntity |
| 656 | `aerowarptics.gui.rift_probe.aim_in` | Aimed: %s, %s in %s | Same, with the dimension when scanning another one | RiftProbeBlockEntity |
| 657 | `aerowarptics.gui.rift_probe.arrival_height` | Arrival height | Probe screen: slider label - height above ground to arrive at | RiftProbeScreen |
| 658 | `aerowarptics.gui.rift_probe.bearing` | Direction | Probe screen: label of the direction dial | RiftProbeScreen |
| 659 | `aerowarptics.gui.rift_probe.can_send` | Ready to send the ship | Probe screen: scan found somewhere the ship can arrive | RiftProbeScreen |
| 660 | `aerowarptics.gui.rift_probe.cannot_send` | Nowhere here to arrive | Probe screen: scan found nowhere to arrive | RiftProbeScreen |
| 661 | `aerowarptics.gui.rift_probe.cost` | Cost to scan | Probe screen: label of the essence cost | RiftProbeScreen |
| 662 | `aerowarptics.gui.rift_probe.course_set` | Ship is aimed here | Probe screen: confirmation after "Send ship here" | RiftProbeScreen |
| 663 | `aerowarptics.gui.rift_probe.coverage` | Mapped | Probe screen: label of the percent of the area mapped | RiftProbeScreen |
| 664 | `aerowarptics.gui.rift_probe.dimension` | Dimension to scan | Probe screen: label of the dimension selector | RiftProbeScreen |
| 665 | `aerowarptics.gui.rift_probe.dimension_locked` | This server only lets a probe scan the dimension it is in | Tooltip when the server forbids scanning other dimensions | RiftProbeScreen |
| 666 | `aerowarptics.gui.rift_probe.drive_missing` | No Rift Drive on this ship | Probe screen: on a ship with no drive | RiftProbeScreen |
| 667 | `aerowarptics.gui.rift_probe.essence` | Essence: %s mB | Probe goggles: tank level | RiftProbeBlockEntity |
| 668 | `aerowarptics.gui.rift_probe.grounded` | Scanning from here - no ship to send | Probe screen: not on a ship, results are view-only | RiftProbeScreen |
| 669 | `aerowarptics.gui.rift_probe.is_course` | The drive is aimed here | Probe screen: this scan is already the drive's course | RiftProbeScreen |
| 670 | `aerowarptics.gui.rift_probe.lowest_arrival` | Arrives from | Probe screen: label of the lowest height the ship could arrive at | RiftProbeScreen |
| 671 | `aerowarptics.gui.rift_probe.no_reading` | No scan yet | Probe goggles / displays: nothing scanned yet | RiftProbeScreen |
| 672 | `aerowarptics.gui.rift_probe.nothing_yet` | Pick a direction and distance, then scan | Probe screen: prompt in the empty map area | RiftProbeScreen |
| 673 | `aerowarptics.gui.rift_probe.range` | Distance | Probe screen: label of the distance slider | RiftProbeScreen |
| 674 | `aerowarptics.gui.rift_probe.reaching` | Scanning the far side | Probe screen status while a scan runs | RiftProbeScreen |
| 675 | `aerowarptics.gui.rift_probe.receiving` | Receiving map... | Probe screen: map data arriving | RiftProbeScreen |
| 676 | `aerowarptics.gui.rift_probe.set_course` | Send ship here | Probe screen: button that sets the drive's course to the scan | RiftProbeScreen |
| 677 | `aerowarptics.gui.rift_probe.sound` | Scan ahead | Probe screen: scan button | RiftProbeScreen |
| 678 | `aerowarptics.gui.rift_probe.sounding` | Scanning... | Scan button label while scanning | RiftProbeScreen |
| 679 | `aerowarptics.gui.rift_probe.title` | Rift Probe | Screen title | RiftProbeScreen |
| 944 | `aerowarptics.message.probe_course_set` | Course set: %s | Reply after "Send ship here" on the probe. %s = coordinates | ServerboundProbePacket |
| 945 | `aerowarptics.message.probe_interference` | Something scrambled part of that scan | Shown when a Rift Ward blanked part of a probe scan | RiftProbeBlockEntity |
| 1132 | `aerowarptics.probe.bearing.east` | East | Compass direction on the probe's dial and readouts |  |
| 1133 | `aerowarptics.probe.bearing.north` | North | Compass direction |  |
| 1134 | `aerowarptics.probe.bearing.north_east` | North-east | Compass direction |  |
| 1135 | `aerowarptics.probe.bearing.north_west` | North-west | Compass direction |  |
| 1136 | `aerowarptics.probe.bearing.south` | South | Compass direction |  |
| 1137 | `aerowarptics.probe.bearing.south_east` | South-east | Compass direction |  |
| 1138 | `aerowarptics.probe.bearing.south_west` | South-west | Compass direction |  |
| 1139 | `aerowarptics.probe.bearing.west` | West | Compass direction |  |
| 1140 | `aerowarptics.probe.state.complete` | Map ready | Probe state: scan finished |  |
| 1141 | `aerowarptics.probe.state.failed` | Nothing found | Probe state: scan found nothing usable |  |
| 1142 | `aerowarptics.probe.state.idle` | Ready | Probe state: not scanning |  |
| 1143 | `aerowarptics.probe.state.reaching` | Scanning | Probe state: scan running |  |
| 1144 | `aerowarptics.probe.verdict.clear` | Solid ground | Scan result: ground found across the whole area |  |
| 1145 | `aerowarptics.probe.verdict.no_ground` | Nothing solid | Scan result: void or open air only |  |
| 1146 | `aerowarptics.probe.verdict.partial` | Partly mapped | Scan result: only some of the area could be mapped |  |

### Rift Gate (38)

**Seen:** right-click a Rift Gate controller (the dial screen), its goggles tooltip, Display Links.

**Limit:** `ConsoleTextTest` also checks the dial screen's detail panel against the longest gate refusal.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 199 | `aerowarptics.gate.failure.destination_another_dimension` | That gate is in another dimension | Gate dial refusal, on the dial screen and Display Link "Fault" |  |
| 200 | `aerowarptics.gate.failure.destination_busy` | That gate is already connected elsewhere | Dial refusal: the other gate is connected to a third gate |  |
| 201 | `aerowarptics.gate.failure.destination_disabled` | That gate has been switched off | Dial refusal: the other gate is switched off |  |
| 202 | `aerowarptics.gate.failure.destination_is_self` | A gate cannot dial itself | Dial refusal: picked itself |  |
| 203 | `aerowarptics.gate.failure.destination_loading` | That gate's ship is being brought in - dial again in a moment | Dial refusal: the other gate is on a ship still being loaded |  |
| 204 | `aerowarptics.gate.failure.destination_missing` | That gate no longer exists | Dial refusal: the other gate was removed |  |
| 205 | `aerowarptics.gate.failure.destination_unformed` | That gate's ring is broken | Dial refusal: the other gate's frame is incomplete |  |
| 206 | `aerowarptics.gate.failure.fastened` | Fastened to something that cannot come through | Shown when something entering the gate is attached to something that cannot follow |  |
| 207 | `aerowarptics.gate.failure.insufficient_essence` | Not enough Rift Essence | Dial refusal: tank too low |  |
| 208 | `aerowarptics.gate.failure.insufficient_power` | Not turning fast enough | Dial refusal: shaft too slow |  |
| 209 | `aerowarptics.gate.failure.no_destination` | No gate chosen | Dial refusal: nothing selected |  |
| 210 | `aerowarptics.gate.failure.none` | *(empty)* | No fault. Deliberately empty - leave it empty |  |
| 211 | `aerowarptics.gate.failure.not_formed` | The ring is not closed around a clear opening | Dial refusal: this gate's frame is incomplete or its opening is blocked |  |
| 212 | `aerowarptics.gate.failure.too_large` | Too large for the opening | Shown when a vehicle is bigger than the opening |  |
| 213 | `aerowarptics.gate.failure.unauthorised` | You may not dial that gate | Dial refusal: the other gate is private or on a network you are not in |  |
| 228 | `aerowarptics.gate.state.closing` | Closing | Gate state on the dial screen, goggles and displays: connection shutting |  |
| 229 | `aerowarptics.gate.state.dialling` | Dialling | Gate state: connecting |  |
| 230 | `aerowarptics.gate.state.idle` | Standing by | Gate state: formed, not connected |  |
| 231 | `aerowarptics.gate.state.open` | Open | Gate state: connected |  |
| 232 | `aerowarptics.gate.state.unformed` | Ring incomplete | Gate state: frame incomplete |  |
| 513 | `aerowarptics.gui.rift_gate.access` | Access: %s | Dial screen: access button. %s = Public/Private/Network | RiftGateDialScreen |
| 514 | `aerowarptics.gui.rift_gate.cost` | Dial cost | Dial screen: label of the cost to connect | RiftGateDialScreen |
| 515 | `aerowarptics.gui.rift_gate.dial` | Dial | Dial screen: connect button | RiftGateDialScreen |
| 516 | `aerowarptics.gui.rift_gate.essence` | Essence: %s / %s mB | Gate goggles: tank level | RiftGateBlockEntity |
| 517 | `aerowarptics.gui.rift_gate.essence_short` | Essence | No reference found in the code - may be unused |  |
| 518 | `aerowarptics.gui.rift_gate.gates_capped` | Showing the nearest %s of %s gates in reach | Dial screen: list cut short | RiftGateDialScreen |
| 519 | `aerowarptics.gui.rift_gate.hang_up` | Disconnect | Dial screen: disconnect button | RiftGateDialScreen |
| 520 | `aerowarptics.gui.rift_gate.name` | Name | Dial screen: name field label | RiftGateDialScreen |
| 521 | `aerowarptics.gui.rift_gate.no_gates` | No gates within reach | Dial screen: empty list | RiftGateDialScreen |
| 522 | `aerowarptics.gui.rift_gate.opening` | Opening %s x %s | Dial screen / goggles: opening size | RiftGateBlockEntity, RiftGateDialScreen |
| 523 | `aerowarptics.gui.rift_gate.passage` | Passage: %s | Dial screen: passage-style button. %s = style name | RiftGateDialScreen |
| 524 | `aerowarptics.gui.rift_gate.passage.hint` | How going through a gate looks to you. Every player picks their own, on any gate. Shift-click to step back. | Tooltip on that button, shown above the style's own description | RiftGateDialScreen |
| 525 | `aerowarptics.gui.rift_gate.pick` | Choose a gate to dial | Dial screen: prompt before a gate is selected | RiftGateDialScreen |
| 526 | `aerowarptics.gui.rift_gate.ready` | Open - drive through | Dial screen / goggles status while connected | RiftGateDialScreen |
| 527 | `aerowarptics.gui.rift_gate.standing_by` | Ready to dial | Dial screen status: formed, not connected | RiftGateDialScreen |
| 528 | `aerowarptics.gui.rift_gate.title` | Rift Gate | Screen title | RiftGateDialScreen |
| 529 | `aerowarptics.gui.rift_gate.unformed_hint` | Close the ring around a clear opening | Dial screen / goggles: frame incomplete, what to do | RiftGateDialScreen |
| 530 | `aerowarptics.gui.rift_gate.upkeep` | Upkeep: %s mB every %ss | Dial screen: running cost while open. mB, seconds | RiftGateBlockEntity |

### Rift Chute (23)

**Seen:** right-click a Rift Chute; the `reason.*` status lines also show in goggles and on Display Links.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 380 | `aerowarptics.gui.rift_chute.aboard` | Aboard this ship | Chute screen: tag on a listed chute that is on the same ship | RiftChuteScreen |
| 381 | `aerowarptics.gui.rift_chute.access` | Access: %s | Chute screen: access button. %s = Public/Private | RiftChuteScreen |
| 382 | `aerowarptics.gui.rift_chute.bind` | Pair with this chute | Chute screen: button to pair with the selected chute | RiftChuteScreen |
| 383 | `aerowarptics.gui.rift_chute.cost` | Cost per item | Chute screen: label of the essence-per-item reading | RiftChuteScreen |
| 384 | `aerowarptics.gui.rift_chute.essence` | Essence: %s / %s mB | Chute goggles / screen: tank level | RiftChuteBlockEntity |
| 385 | `aerowarptics.gui.rift_chute.grounded` | Standing on the ground | Chute screen: tag on a listed chute that is not on a ship | RiftChuteScreen |
| 386 | `aerowarptics.gui.rift_chute.name` | Name | Chute screen: name field label | RiftChuteScreen |
| 387 | `aerowarptics.gui.rift_chute.no_chutes` | No other chutes in this world | Chute screen: empty list | RiftChuteScreen |
| 388 | `aerowarptics.gui.rift_chute.reason.empty` | Idle | Chute status (screen, goggles, Display Link): nothing to send |  |
| 389 | `aerowarptics.gui.rift_chute.reason.no_essence` | Out of Rift Essence - cannot send | Chute status: tank empty |  |
| 390 | `aerowarptics.gui.rift_chute.reason.partner_absent` | Far chute is not loaded | Chute status: paired chute's chunk is not loaded |  |
| 391 | `aerowarptics.gui.rift_chute.reason.partner_full` | Nowhere to put it at the far end | Chute status: paired chute has no space |  |
| 392 | `aerowarptics.gui.rift_chute.reason.partner_loading` | Loading the far chute | Chute status: waiting for the paired chute's chunk to load |  |
| 393 | `aerowarptics.gui.rift_chute.reason.ready` | Sending | Chute status: transferring |  |
| 394 | `aerowarptics.gui.rift_chute.reason.unbound` | Pair this with another chute | Chute status: not paired |  |
| 395 | `aerowarptics.gui.rift_chute.reason.warping` | Waiting for the warp to finish | Chute status: its ship (or the partner's) is mid-warp |  |
| 396 | `aerowarptics.gui.rift_chute.rift_collapsed` | No link | Chute goggles / display "Link": not connected | RiftChuteBlockEntity, RiftChuteDisplaySource, RiftChuteScreen |
| 397 | `aerowarptics.gui.rift_chute.rift_open` | Link open | Chute goggles / display "Link": connected | RiftChuteBlockEntity, RiftChuteDisplaySource, RiftChuteScreen |
| 398 | `aerowarptics.gui.rift_chute.supply` | Rift Essence | Chute screen: label of the tank gauge | RiftChuteScreen |
| 399 | `aerowarptics.gui.rift_chute.title` | Rift Chute | Screen title | RiftChuteScreen |
| 400 | `aerowarptics.gui.rift_chute.truncated` | Nearest %s of %s | Chute screen: list cut short. %s shown of %s | RiftChuteScreen |
| 401 | `aerowarptics.gui.rift_chute.unbind` | Break the pair | Chute screen: button to unpair | RiftChuteScreen |
| 402 | `aerowarptics.gui.rift_chute.unbound` | Not paired | Chute goggles: not paired | RiftChuteScreen |

### Rift Modulator (30)

**Seen:** right-click a Rift Modulator, and its goggles tooltip. The theme names are references to other works.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 531 | `aerowarptics.gui.rift_modulator.active` | Dressing this warp | Modulator goggles/screen state: currently restyling a warp | RiftModulatorBlockEntity, RiftModulatorScreen |
| 532 | `aerowarptics.gui.rift_modulator.core_hint` | L: core | Screen hint by the colour swatches: left-click sets the centre colour | RiftModulatorScreen |
| 533 | `aerowarptics.gui.rift_modulator.destination` | Destination | Screen label: the linked drive's course | RiftModulatorScreen |
| 534 | `aerowarptics.gui.rift_modulator.drive` | Drive | Screen label: the linked drive | RiftModulatorScreen |
| 535 | `aerowarptics.gui.rift_modulator.essence` | Essence: %s / %s mB | Goggles: tank level | RiftModulatorBlockEntity |
| 536 | `aerowarptics.gui.rift_modulator.idle` | Linked, not dressing | Goggles/screen state: attached, no warp in progress | RiftModulatorBlockEntity, RiftModulatorScreen |
| 537 | `aerowarptics.gui.rift_modulator.intensity` | Effect intensity | Screen: label of the effect-strength slider | RiftModulatorScreen |
| 538 | `aerowarptics.gui.rift_modulator.intensity_reading` | Intensity: %s | Goggles: effect strength | RiftModulatorBlockEntity |
| 539 | `aerowarptics.gui.rift_modulator.not_linked` | Not linked | Goggles/screen state: no drive beside it | RiftModulatorBlockEntity, RiftModulatorScreen |
| 540 | `aerowarptics.gui.rift_modulator.not_linked_hint` | Place this beside a Rift Drive to customise the rift it opens. | Screen text shown instead of the controls when not attached | RiftModulatorScreen |
| 541 | `aerowarptics.gui.rift_modulator.rim_hint` | R: rim | Screen hint by the swatches: right-click sets the edge colour | RiftModulatorScreen |
| 542 | `aerowarptics.gui.rift_modulator.state` | State | Screen label: the linked drive's state | RiftModulatorScreen |
| 543 | `aerowarptics.gui.rift_modulator.supply` | Rift Essence | Screen: label of the tank gauge | RiftModulatorScreen |
| 544 | `aerowarptics.gui.rift_modulator.theme.arcane` | Arcane | Name of a rift theme on the theme button |  |
| 545 | `aerowarptics.gui.rift_modulator.theme.bedrock` | Bedrock | Name of a rift theme on the theme button |  |
| 546 | `aerowarptics.gui.rift_modulator.theme.boldly_gone` | Boldly Gone | Name of a rift theme on the theme button |  |
| 547 | `aerowarptics.gui.rift_modulator.theme.clockwork` | Clockwork | Name of a rift theme on the theme button |  |
| 548 | `aerowarptics.gui.rift_modulator.theme.ember` | Ember | Name of a rift theme on the theme button |  |
| 549 | `aerowarptics.gui.rift_modulator.theme.ender` | Ender | Name of a rift theme on the theme button |  |
| 550 | `aerowarptics.gui.rift_modulator.theme.eventful_horizon` | Eventful Horizon | Name of a rift theme on the theme button |  |
| 551 | `aerowarptics.gui.rift_modulator.theme.improbability` | Improbability | Name of a rift theme on the theme button |  |
| 552 | `aerowarptics.gui.rift_modulator.theme.ludicrous` | Ludicrous Speed | Name of a rift theme on the theme button |  |
| 553 | `aerowarptics.gui.rift_modulator.theme.mostly_dangerous` | Mostly Dangerous | Name of a rift theme on the theme button |  |
| 554 | `aerowarptics.gui.rift_modulator.theme.sketch` | Sketch | Name of a rift theme on the theme button |  |
| 555 | `aerowarptics.gui.rift_modulator.theme.standard` | Standard | Name of a rift theme on the theme button |  |
| 556 | `aerowarptics.gui.rift_modulator.theme.starblocks` | Starblocks | Name of a rift theme on the theme button |  |
| 557 | `aerowarptics.gui.rift_modulator.theme.starlight` | Starlight | Name of a rift theme on the theme button |  |
| 558 | `aerowarptics.gui.rift_modulator.theme.vworp` | Vworp | Name of a rift theme on the theme button |  |
| 559 | `aerowarptics.gui.rift_modulator.theme_colours` | This theme brings its own colours | Screen note when the chosen theme ignores the colour swatches | RiftModulatorScreen |
| 560 | `aerowarptics.gui.rift_modulator.title` | Rift Modulator | Screen title | RiftModulatorScreen |

### Rift Coupler and flotillas (140)

**Seen:** the coupler's two screens (the flagship's Fleet Board and the escort's station screen), the big titles and action-bar lines during a fleet warp, the escort pilot's HUD, and chat notices to fleet members.

**Limit:** `CouplerTextTest` holds every button label to its button.

**Vocabulary used throughout:** *flotilla* = the group; *flagship* = the leader; *escort* = a follower; *slot* / *station* = an escort's assigned position; *fold* = the fleet warp; *sound stations* = tell escorts to get ready; *harmonise* = the few seconds an escort takes to sync once in position. Changing one of these words means changing it everywhere it appears, including the handbook, Ponder and JEI text.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 143 | `aerowarptics.flotilla.flagship.busy` | Drive busy | Fleet Board, flagship's own row: its drive is mid-warp or cooling |  |
| 144 | `aerowarptics.flotilla.flagship.cruise` | Set to cruise | Flagship row: drive is in Free Cruise mode, so it cannot lead a fold |  |
| 145 | `aerowarptics.flotilla.flagship.fastened` | Tied down | Flagship row: ship is attached to the ground or another ship |  |
| 146 | `aerowarptics.flotilla.flagship.folding` | Folding | Flagship row: the fold is under way |  |
| 147 | `aerowarptics.flotilla.flagship.no_course` | No course set | Flagship row: no destination set |  |
| 148 | `aerowarptics.flotilla.flagship.no_crossing` | Can't cross | Flagship row: course is in another dimension and that is not possible |  |
| 149 | `aerowarptics.flotilla.flagship.no_drive` | No drive | Flagship row: coupler is not attached to a drive |  |
| 150 | `aerowarptics.flotilla.flagship.no_essence` | Needs essence | Flagship row: coupler tank too low for the fold |  |
| 151 | `aerowarptics.flotilla.flagship.not_aboard` | Not aboard a ship | Flagship row: the coupler is not on an airship |  |
| 152 | `aerowarptics.flotilla.flagship.not_sounded` | Sound stations | Flagship row: Sound Stations has not been pressed yet |  |
| 153 | `aerowarptics.flotilla.flagship.obstructed` | Arrival blocked | Flagship row: arrival point is blocked |  |
| 154 | `aerowarptics.flotilla.flagship.ready` | Ready | Flagship row: ready to fold |  |
| 155 | `aerowarptics.flotilla.flagship.surveying` | Surveying | Flagship row: still checking the arrival point |  |
| 156 | `aerowarptics.flotilla.flagship.too_close` | Course too close | Flagship row: destination nearer than the minimum warp distance |  |
| 157 | `aerowarptics.flotilla.flagship.too_far` | Course too far | Flagship row: destination beyond the drive's range |  |
| 158 | `aerowarptics.flotilla.flagship.unaffordable` | Fleet too heavy | Flagship row: the fleet's cost is more than the drive's charge |  |
| 159 | `aerowarptics.flotilla.flagship.uncharged` | Charging | Flagship row: drive still charging |  |
| 160 | `aerowarptics.flotilla.flagship.unpowered` | Not turning | Flagship row: drive has no rotation |  |
| 161 | `aerowarptics.flotilla.join.already_member` | Already in station with that flagship. | Reply on the escort's screen after Take Station: already in that flotilla |  |
| 162 | `aerowarptics.flotilla.join.full` | That flagship has no room. | Take Station reply: flagship has its maximum escorts |  |
| 163 | `aerowarptics.flotilla.join.in_another` | Already with another flagship. Break off first. | Take Station reply: this ship is in a different flotilla |  |
| 164 | `aerowarptics.flotilla.join.joined` | Joined the flotilla. | Take Station reply: success |  |
| 165 | `aerowarptics.flotilla.join.not_a_flagship` | That ship is not a flagship. | Take Station reply: target's coupler is not set to Flagship |  |
| 166 | `aerowarptics.flotilla.join.not_an_escort` | A flagship cannot join as an escort. | Take Station reply: this coupler is set to Flagship |  |
| 167 | `aerowarptics.flotilla.join.out_of_reach` | Too far off, or not loaded. | Take Station reply: flagship too far away or unloaded |  |
| 168 | `aerowarptics.flotilla.join.requested` | Request sent. Waiting for the flagship's owner to accept. | Take Station reply: request sent to another player's flagship |  |
| 169 | `aerowarptics.flotilla.join.same_ship` | A ship can't escort itself. | Take Station reply: picked its own ship |  |
| 170 | `aerowarptics.flotilla.join.too_many_requests` | That flagship has too many waiting. | Take Station reply: flagship's request queue is full |  |
| 171 | `aerowarptics.flotilla.join.unknown` | Nothing happened. | Take Station reply: fallback when no specific reason applies |  |
| 172 | `aerowarptics.flotilla.name_refused` | Another ship in the flotilla has that name. | Fleet Board notice when renaming a ship to a name already in the flotilla | CouplerNotice |
| 173 | `aerowarptics.flotilla.notice.broke_off` | %s has broken off. | Chat notice to the flotilla: an escort left. %s = ship | FleetNotices |
| 174 | `aerowarptics.flotilla.notice.cast_off` | %s has cast %s off. | Chat notice: the flagship removed an escort. %s = flagship, %s = escort | FleetNotices |
| 175 | `aerowarptics.flotilla.notice.disbanded` | %s no longer leads a flotilla. %s is on its own. | Chat notice: flotilla dissolved. %s = flagship, %s = the escort being told | FleetNotices |
| 176 | `aerowarptics.flotilla.notice.left_behind` | %s folded without %s, which is no longer in its flotilla. | Chat notice: fold went ahead without a ship. %s = flagship, %s = ship left | FleetNotices |
| 177 | `aerowarptics.flotilla.notice.refused` | %s refused your request to join. | Chat notice to a player whose join request was refused. %s = flagship | FleetNotices |
| 178 | `aerowarptics.flotilla.notice.requested` | %s asks to join your flotilla. Answer at the Fleet Board. | Chat notice to a flagship's owner: someone asks to join. %s = ship | FleetNotices |
| 179 | `aerowarptics.flotilla.notice.taken_station` | %s has joined %s's flotilla. | Chat notice: a ship joined. %s = escort, %s = flagship | FleetNotices |
| 180 | `aerowarptics.flotilla.role.escort` | Escort | Coupler role name |  |
| 181 | `aerowarptics.flotilla.role.flagship` | Flagship | Coupler role name |  |
| 182 | `aerowarptics.flotilla.slot_refused.flagship` | Too close to the flagship. Slot not moved. | Fleet Board notice: a slot was dragged too close to the flagship | CouplerNotice |
| 183 | `aerowarptics.flotilla.slot_refused.ship` | Too close to %s. Slot not moved. | Fleet Board notice: a slot was dragged too close to another ship. %s = ship | CouplerNotice |
| 184 | `aerowarptics.flotilla.slot_unread` | That ship can't be read right now. Slot not moved. | Fleet Board notice: the ship's size could not be read, so its slot was not moved | CouplerNotice |
| 185 | `aerowarptics.flotilla.status.adrift` | Adrift | Escort's row on the Fleet Board / its own screen: not in its slot |  |
| 186 | `aerowarptics.flotilla.status.busy` | Drive busy | Escort status: drive mid-warp or cooling |  |
| 187 | `aerowarptics.flotilla.status.fastened` | Tied down | Escort status: attached to the ground or another ship |  |
| 188 | `aerowarptics.flotilla.status.harmonising` | Harmonising | Escort status: in its slot and syncing with the flagship (a few seconds) |  |
| 189 | `aerowarptics.flotilla.status.no_crossing` | Can't cross | Escort status: cannot follow into the other dimension |  |
| 190 | `aerowarptics.flotilla.status.obstructed` | Slot blocked | Escort status: its arrival slot is blocked |  |
| 191 | `aerowarptics.flotilla.status.out_of_contact` | Out of contact | Escort status: unloaded or too far from the flagship | FleetBoardScreen, StationScreen |
| 192 | `aerowarptics.flotilla.status.ready` | Ready | Escort status: ready to fold |  |
| 193 | `aerowarptics.flotilla.status.surveying` | Surveying | Escort status: its arrival slot is still being checked |  |
| 194 | `aerowarptics.flotilla.status.too_close` | Course too close | Escort status: destination nearer than the minimum warp distance |  |
| 195 | `aerowarptics.flotilla.status.too_far` | Course too far | Escort status: destination beyond this escort's drive range |  |
| 196 | `aerowarptics.flotilla.status.unaffordable` | Can't pay its share | Escort status: its drive lacks the charge for its part of the fold |  |
| 197 | `aerowarptics.flotilla.status.uncharged` | Charging | Escort status: drive still charging |  |
| 198 | `aerowarptics.flotilla.status.unpowered` | Not turning | Escort status: drive has no rotation |  |
| 313 | `aerowarptics.gui.flotilla.called_off` | The fold was called off | Action bar to crews when the flagship cancels a fold | FlotillaEffects |
| 314 | `aerowarptics.gui.flotilla.echo_inbound` | Something is coming through late | Action bar when a late escort is about to arrive | FlotillaEffects |
| 315 | `aerowarptics.gui.flotilla.hud.climb` | Climb %s | Escort pilot's HUD: go up by this much to reach the slot | FlotillaEffects |
| 316 | `aerowarptics.gui.flotilla.hud.descend` | Descend %s | Escort pilot's HUD: go down by this much | FlotillaEffects |
| 317 | `aerowarptics.gui.flotilla.hud.drift` | Drift %s | Escort pilot's HUD: horizontal distance from the slot | FlotillaEffects |
| 318 | `aerowarptics.gui.flotilla.hud.in_station` | In station | Escort pilot's HUD: in the slot | FlotillaEffects |
| 319 | `aerowarptics.gui.flotilla.hud.slot` | Slot %s | Escort pilot's HUD: slot number label | FlotillaEffects |
| 320 | `aerowarptics.gui.flotilla.resolved` | The fleet is in harmony | Action bar when every escort is synced and ready | FlotillaEffects |
| 321 | `aerowarptics.gui.flotilla.subtitle.left_behind` | The flotilla folded without you | Small line under the LEFT BEHIND title | FlotillaEffects |
| 322 | `aerowarptics.gui.flotilla.subtitle.straggler` | One of the fleet is still coming through | Small line under the title when an escort arrives late | FlotillaEffects |
| 323 | `aerowarptics.gui.flotilla.title.assembled` | FLOTILLA ASSEMBLED | Big on-screen title when the fleet is fully ready | FlotillaEffects |
| 324 | `aerowarptics.gui.flotilla.title.left_behind` | LEFT BEHIND | Big title on a ship the fleet folded without | FlotillaEffects |
| 325 | `aerowarptics.gui.flotilla.title.shaken_loose` | SHAKEN LOOSE | Big title on an escort that left its slot during the countdown | FlotillaEffects |
| 326 | `aerowarptics.gui.flotilla.title.slot_adrift` | SLOT %s - ADRIFT | Big title on an escort: out of its slot. %s = slot number | FlotillaEffects |
| 327 | `aerowarptics.gui.flotilla.title.slot_in_station` | SLOT %s - IN STATION | Big title on an escort: in its slot | FlotillaEffects |
| 328 | `aerowarptics.gui.flotilla.title.sound_stations` | SOUND STATIONS | Big title to all crews when the flagship presses Sound Stations | FlotillaEffects |
| 403 | `aerowarptics.gui.rift_coupler.accept` | Accept | Fleet Board: accept a join request | FleetBoardScreen |
| 404 | `aerowarptics.gui.rift_coupler.adrift` | Adrift %s | Fleet Board row: escort is this far from its slot | FleetBoardScreen |
| 405 | `aerowarptics.gui.rift_coupler.asked` | Asked %s | Escort screen: a request has been sent to this flagship | StationScreen |
| 406 | `aerowarptics.gui.rift_coupler.auto_accept_hint` | On: ships whose coupler you placed join without asking. Anyone else still asks. | Tooltip on the Auto button | FleetBoardScreen |
| 407 | `aerowarptics.gui.rift_coupler.auto_off` | Auto: Off | Fleet Board button: auto-accept your own ships, off | FleetBoardScreen |
| 408 | `aerowarptics.gui.rift_coupler.auto_on` | Auto: On | Fleet Board button: auto-accept your own ships, on | FleetBoardScreen |
| 409 | `aerowarptics.gui.rift_coupler.awaiting_stations` | Awaiting stations | Escort screen: waiting for the flagship to press Sound Stations | StationScreen |
| 410 | `aerowarptics.gui.rift_coupler.be_escort` | Be Escort | Role button: switch this coupler to Escort | FleetBoardScreen |
| 411 | `aerowarptics.gui.rift_coupler.be_escort_hint` | Make this coupler an escort. Its flotilla is disbanded. | Tooltip on that button | FleetBoardScreen |
| 412 | `aerowarptics.gui.rift_coupler.be_flagship` | Be Flagship | Role button: switch this coupler to Flagship | StationScreen |
| 413 | `aerowarptics.gui.rift_coupler.be_flagship_hint` | Make this coupler a flagship. It leaves any flotilla it is in. | Tooltip on that button | StationScreen |
| 414 | `aerowarptics.gui.rift_coupler.beacon_fleet` | Call: Fleet | Fleet Board button: a Rift Beacon calls the whole flotilla | FleetBoardScreen |
| 415 | `aerowarptics.gui.rift_coupler.beacon_hint` | What a Rift Beacon bound to this ship calls: the whole flotilla, or this ship alone. | Tooltip on that button | FleetBoardScreen |
| 416 | `aerowarptics.gui.rift_coupler.beacon_ship` | Call: Ship | Fleet Board button: a Rift Beacon calls this ship only | FleetBoardScreen |
| 417 | `aerowarptics.gui.rift_coupler.break_off` | Break Off | Escort screen: leave the flotilla | StationScreen |
| 418 | `aerowarptics.gui.rift_coupler.cast_off` | Cast Off | Fleet Board: remove the selected escort | FleetBoardScreen |
| 419 | `aerowarptics.gui.rift_coupler.course` | %s to %s, %s | Fleet Board course line. ship, destination, distance | FleetBoardScreen |
| 420 | `aerowarptics.gui.rift_coupler.course_unmeasured` | %s to %s | Course line when the distance is unknown | FleetBoardScreen |
| 421 | `aerowarptics.gui.rift_coupler.disband` | Disband? | Role button's confirmation when switching would dissolve a flotilla | FleetBoardScreen |
| 422 | `aerowarptics.gui.rift_coupler.drag_hint` | Drag to re-slot | Fleet Board hint under the formation map | FleetBoardScreen |
| 423 | `aerowarptics.gui.rift_coupler.drift` | Drift | Label of the gauge showing how far an escort is from its slot | StationScreen |
| 424 | `aerowarptics.gui.rift_coupler.escort_subject` | Escort - Slot %s | Escort screen heading. %s = slot number | StationScreen |
| 425 | `aerowarptics.gui.rift_coupler.escort_unattached` | Escort | Escort screen heading when in no flotilla | StationScreen |
| 426 | `aerowarptics.gui.rift_coupler.escorts_of` | %s of %s | Fleet Board: escort count against the drive's limit | StationScreen |
| 427 | `aerowarptics.gui.rift_coupler.escorts_unlimited` | %s escorts | Fleet Board: escort count when there is no limit | StationScreen |
| 428 | `aerowarptics.gui.rift_coupler.essence` | Essence: %s / %s mB | Coupler goggles: tank level | RiftCouplerBlockEntity |
| 429 | `aerowarptics.gui.rift_coupler.essence_line` | Essence %s | Fleet Board footer: tank level | FleetBoardScreen |
| 430 | `aerowarptics.gui.rift_coupler.flagship_subject` | Flagship - %s | Fleet Board heading. %s = ship name | FleetBoardScreen |
| 431 | `aerowarptics.gui.rift_coupler.fold` | Fold | Fleet Board: start the fleet warp | FleetBoardScreen |
| 432 | `aerowarptics.gui.rift_coupler.following` | Flagship: %s | Escort screen: which flagship it follows | StationScreen |
| 433 | `aerowarptics.gui.rift_coupler.harmony` | Harmony | Label of the gauge showing how far the escort's sync has got | StationScreen |
| 434 | `aerowarptics.gui.rift_coupler.hold_to_fold` | Hold to Fold | Fold button label when some ships are not ready (hold to go without them) | FleetBoardScreen |
| 435 | `aerowarptics.gui.rift_coupler.leaving` | Leaving %s behind | Fold button tooltip: who would be left. %s = ship names | FleetBoardScreen |
| 436 | `aerowarptics.gui.rift_coupler.mustering` | Mustering | Fleet Board status after Sound Stations: escorts are getting ready | RiftCouplerBlockEntity |
| 437 | `aerowarptics.gui.rift_coupler.nearby` | Flagships within reach | Escort screen: heading of the flagship list | StationScreen |
| 438 | `aerowarptics.gui.rift_coupler.no_escorts` | No escorts yet | Fleet Board: empty roster | FleetBoardScreen |
| 439 | `aerowarptics.gui.rift_coupler.none_nearby` | No flagships within reach. | Escort screen: empty flagship list | StationScreen |
| 440 | `aerowarptics.gui.rift_coupler.not_linked` | Not fitted to a Rift Drive | Coupler goggles / screen: not attached to a drive | RiftCouplerBlockEntity, StationScreen |
| 441 | `aerowarptics.gui.rift_coupler.open_slot` | + open slot | Fleet Board roster row for an unused escort slot | FleetBoardScreen |
| 442 | `aerowarptics.gui.rift_coupler.refuse` | Refuse | Fleet Board: refuse a join request | FleetBoardScreen |
| 443 | `aerowarptics.gui.rift_coupler.rename` | Rename | Fleet Board: rename-ship button | CouplerScreen, FleetBoardScreen, StationScreen |
| 444 | `aerowarptics.gui.rift_coupler.rename_hint` | Ship name | Placeholder in the rename box | CouplerScreen |
| 445 | `aerowarptics.gui.rift_coupler.rift_each` | Rift: Each | Fleet Board button: every ship opens its own rift | FleetBoardScreen |
| 446 | `aerowarptics.gui.rift_coupler.rift_hint` | One: the flagship opens a single large rift and the whole flotilla flies through it together, in formation. Each: every ship opens its own rift. | Tooltip on that button | FleetBoardScreen |
| 447 | `aerowarptics.gui.rift_coupler.rift_one` | Rift: One | Fleet Board button: one shared rift for the whole fleet | FleetBoardScreen |
| 448 | `aerowarptics.gui.rift_coupler.ship_above` | The ship is %s above its slot | Slot tooltip: ship's height relative to its slot | FleetBoardScreen |
| 449 | `aerowarptics.gui.rift_coupler.ship_below` | The ship is %s below its slot | Slot tooltip: ship's height relative to its slot | FleetBoardScreen |
| 450 | `aerowarptics.gui.rift_coupler.slot_above` | Slot %s above the flagship | Slot tooltip: slot height relative to the flagship | FleetBoardScreen |
| 451 | `aerowarptics.gui.rift_coupler.slot_below` | Slot %s below the flagship | Slot tooltip: slot height relative to the flagship | FleetBoardScreen |
| 452 | `aerowarptics.gui.rift_coupler.slot_drag` | Drag to move it; drop it on its ship to reset | Slot tooltip: how to move it | FleetBoardScreen |
| 453 | `aerowarptics.gui.rift_coupler.slot_here` | Right-click to reset it to where its ship is | Slot tooltip: how to reset it | FleetBoardScreen |
| 454 | `aerowarptics.gui.rift_coupler.slot_level` | Slot level with the flagship | Slot tooltip: same height as the flagship | FleetBoardScreen |
| 455 | `aerowarptics.gui.rift_coupler.slot_scroll` | Scroll to raise or lower it (Shift: 5 m) | Slot tooltip: how to change its height | FleetBoardScreen |
| 456 | `aerowarptics.gui.rift_coupler.sound_stations` | Sound Stations | Fleet Board: button that tells escorts to get ready | FleetBoardScreen |
| 457 | `aerowarptics.gui.rift_coupler.sound_stations_hint` | Sound the horn. Escorts in station start matching this ship's drive. | Tooltip on that button | FleetBoardScreen |
| 458 | `aerowarptics.gui.rift_coupler.stand_down` | Stand Down | Fleet Board: cancel the ready-up | FleetBoardScreen |
| 459 | `aerowarptics.gui.rift_coupler.stand_down_hint` | End the muster. The escorts stop matching this ship's drive, and start over the next time stations are sounded. | Tooltip on that button | FleetBoardScreen |
| 460 | `aerowarptics.gui.rift_coupler.take_station` | Take Station | Escort screen: join the selected flagship | StationScreen |
| 461 | `aerowarptics.gui.rift_coupler.tether_ragged` | Tether: Ragged | Escort screen: link quality reading, worst | StationScreen |
| 462 | `aerowarptics.gui.rift_coupler.tether_slack` | Tether: Slack | Escort screen: link quality reading, middling | StationScreen |
| 463 | `aerowarptics.gui.rift_coupler.tether_steady` | Tether: Steady | Escort screen: link quality reading, good | StationScreen |
| 464 | `aerowarptics.gui.rift_coupler.title` | Rift Coupler | Screen title | CouplerScreen, FleetBoardScreen, StationScreen |
| 465 | `aerowarptics.gui.rift_coupler.tolerance` | Tolerance | Label of the setting for how far an escort may be from its slot | StationScreen |
| 466 | `aerowarptics.gui.rift_coupler.unattached` | %s is in no flotilla | Escort screen: this ship is in no flotilla. %s = ship name | StationScreen |
| 467 | `aerowarptics.gui.rift_coupler.waiting` | Waiting to join | Escort screen: join request pending | FleetBoardScreen |
| 468 | `aerowarptics.gui.rift_coupler.waiting_on` | Waiting on %s | Fleet Board: who the fold is waiting for. %s = ship | StationScreen |
| 469 | `aerowarptics.gui.rift_coupler.wake` | Wake %sx%sx%s | Fleet Board footer: size of the shared rift / fleet, in blocks | FleetBoardScreen |
| 470 | `aerowarptics.gui.rift_coupler.withdraw` | Withdraw | Escort screen: cancel a pending join request | StationScreen |

### Collapse Cradle (60)

**Seen:** right-click the cradle (its panel), its goggles tooltip, and action-bar lines when placing it.

**Limit:** `RelicTextTest` measures the panel's lines and checks tooltips wrap.

**Vocabulary:** *arms* = input slot; *cup* = catalyst slot; *tray* = output slot; *collapse* = making a Singularity Core; *fold* = the simpler batch craft; *steady / flung / spat* = the three outcomes of a collapse.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 82 | `aerowarptics.collapse.failure.aboard_ship` | Can't collapse aboard a ship | Cradle refusal: the cradle is on a ship |  |
| 83 | `aerowarptics.collapse.failure.busy` | Already at work | Cradle refusal: it is already running |  |
| 84 | `aerowarptics.collapse.failure.no_core` | The arms are empty | Cradle refusal: nothing in the arms slot |  |
| 85 | `aerowarptics.collapse.failure.no_fold` | Nothing here can be folded | Cradle refusal: the item in the arms has no folding recipe |  |
| 86 | `aerowarptics.collapse.failure.no_heart` | No Scar Heart in the cup | Cradle refusal: a collapse needs a Scar Heart in the cup slot |  |
| 87 | `aerowarptics.collapse.failure.no_recipe` | The depots match no collapse | Cradle refusal: the items on the depots match no collapse recipe |  |
| 88 | `aerowarptics.collapse.failure.no_room` | No room in the tray | Cradle refusal: the output tray is full |  |
| 89 | `aerowarptics.collapse.failure.none` | Ready | Cradle status when nothing is wrong |  |
| 90 | `aerowarptics.collapse.failure.not_enough_essence` | Not enough essence | Cradle refusal: tank too low |  |
| 91 | `aerowarptics.collapse.failure.not_turning` | The shaft isn't turning | Cradle refusal: no rotation |  |
| 92 | `aerowarptics.collapse.failure.too_slow` | The shaft is too slow | Cradle refusal: rotating below the minimum speed |  |
| 93 | `aerowarptics.collapse_cradle.aboard` | Too heavy for any hull: set it down on the ground | Action bar when trying to place the cradle on a ship | CollapseCradleItem |
| 94 | `aerowarptics.collapse_cradle.no_room` | Needs clear room three blocks across and three high, centred where you aim | Action bar when the 3x3x3 space is not clear | CollapseCradleItem |
| 257 | `aerowarptics.gui.collapse_cradle.core` | Arms: %1$s | Goggles line: what is in the arms slot. %1$s = item or "nothing" | CollapseCradleBlockEntity |
| 258 | `aerowarptics.gui.collapse_cradle.essence` | Essence: %1$s / %2$s mB | Goggles line: tank level | CollapseCradleBlockEntity |
| 259 | `aerowarptics.gui.collapse_cradle.folding` | Folding: %1$s through, %2$s %3$s | Goggles line while folding. %1$s = percent done, %2$s %3$s = count and item being made | CollapseCradleBlockEntity |
| 260 | `aerowarptics.gui.collapse_cradle.heart` | Cup: %1$s | Goggles line: what is in the cup slot | CollapseCradleBlockEntity |
| 261 | `aerowarptics.gui.collapse_cradle.made` | Tray: %1$s %2$s | Goggles line: what is in the output tray. %1$s count, %2$s item | CollapseCradleBlockEntity |
| 262 | `aerowarptics.gui.collapse_cradle.nothing` | nothing | Word used in those goggles lines for an empty slot | CollapseCradleBlockEntity |
| 263 | `aerowarptics.gui.collapse_cradle.outcome.flung` | Core flung clear - look for its glow | Result line after a collapse: core was thrown out of the machine |  |
| 264 | `aerowarptics.gui.collapse_cradle.outcome.folded` | Fold done: take it from the tray | Result line after a fold finished |  |
| 265 | `aerowarptics.gui.collapse_cradle.outcome.settled` | Core settled in the tray | Result line after a clean collapse |  |
| 266 | `aerowarptics.gui.collapse_cradle.outcome.spat` | Spat out; heart lost | Result line after a failed collapse: items returned, Scar Heart consumed |  |
| 267 | `aerowarptics.gui.collapse_cradle.panel.arms_tip` | Arms: a Rift Core to collapse, or up to sixteen of something to fold | Panel tooltip on the arms slot |  |
| 268 | `aerowarptics.gui.collapse_cradle.panel.at_work` | At work | Panel status while running | CollapseCradleScreen |
| 269 | `aerowarptics.gui.collapse_cradle.panel.begin_collapse` | Begin collapse | Panel start button when a collapse is loaded | CollapseCradleScreen |
| 270 | `aerowarptics.gui.collapse_cradle.panel.begin_fold` | Begin fold | Panel start button when a fold is loaded | CollapseCradleScreen |
| 271 | `aerowarptics.gui.collapse_cradle.panel.collapsing` | Collapsing %1$s | Panel status line. %1$s = percent | CollapseCradleScreen |
| 272 | `aerowarptics.gui.collapse_cradle.panel.cost` | %1$s mB | Panel: essence the job will cost | CollapseCradleScreen |
| 273 | `aerowarptics.gui.collapse_cradle.panel.cup_tip` | Cup: the Scar Heart, or what a fold is folded with | Panel tooltip on the cup slot |  |
| 274 | `aerowarptics.gui.collapse_cradle.panel.dial_tip` | How unstable the collapse is. Steady: the core settles in the tray. Middle: the core is flung clear. Spat: the core and offerings are returned and only the Scar Heart is used up. | Panel tooltip on the instability dial; explains the three outcomes | CollapseCradleScreen |
| 275 | `aerowarptics.gui.collapse_cradle.panel.essence_short` | Tank | Panel label beside the tank reading | CollapseCradleScreen |
| 276 | `aerowarptics.gui.collapse_cradle.panel.essence_tip` | Rift Essence: %1$s of %2$s mB. This needs %3$s mB. | Panel tooltip on the tank. held, capacity, needed | CollapseCradleScreen |
| 277 | `aerowarptics.gui.collapse_cradle.panel.folding` | Folding %1$s | Panel status line while folding. %1$s = percent | CollapseCradleScreen |
| 278 | `aerowarptics.gui.collapse_cradle.panel.folding_what` | Folding into | Panel label above the item a fold will produce | CollapseCradleScreen |
| 279 | `aerowarptics.gui.collapse_cradle.panel.heading` | %1$s unsteady | Panel heading over the instability dial. %1$s = percent | CollapseCradleScreen |
| 280 | `aerowarptics.gui.collapse_cradle.panel.held` | Held: the shaft must turn faster | Panel status: paused because rotation dropped below the minimum | CollapseCradleScreen |
| 281 | `aerowarptics.gui.collapse_cradle.panel.idle` | Idle | Panel status when nothing is loaded or running | CollapseCradleScreen |
| 282 | `aerowarptics.gui.collapse_cradle.panel.lean_tip` | Switched-on Spatial Stabilisers around the cradle, and which way they pull the collapse. A dot in the middle means they are evenly spaced. | Panel tooltip on the stabiliser diagram | CollapseCradleScreen |
| 283 | `aerowarptics.gui.collapse_cradle.panel.made` | Tray | Panel label of the output tray | CollapseCradleScreen |
| 284 | `aerowarptics.gui.collapse_cradle.panel.makes` | %1$s %2$s | Panel: what the job will produce. count, item | CollapseCradleScreen |
| 285 | `aerowarptics.gui.collapse_cradle.panel.needs_speed` | Min %1$s rpm, best %2$s | Panel line: minimum and ideal RPM | CollapseCradleScreen |
| 286 | `aerowarptics.gui.collapse_cradle.panel.next_collapse` | Ready to collapse | Panel status: a collapse is loaded and can start | CollapseCradleScreen |
| 287 | `aerowarptics.gui.collapse_cradle.panel.next_fold` | Ready to fold | Panel status: a fold is loaded and can start | CollapseCradleScreen |
| 288 | `aerowarptics.gui.collapse_cradle.panel.no_stabilisers` | No stabilisers | Panel line when no stabilisers are linked | CollapseCradleScreen |
| 289 | `aerowarptics.gui.collapse_cradle.panel.nothing` | Nothing in its arms | Panel status: arms slot empty | CollapseCradleScreen |
| 290 | `aerowarptics.gui.collapse_cradle.panel.nothing_made` | nothing | Panel value for an empty tray | CollapseCradleScreen |
| 291 | `aerowarptics.gui.collapse_cradle.panel.phase.collapse` | The hush | Panel: name of the last stage of a collapse (the moment before it finishes) |  |
| 292 | `aerowarptics.gui.collapse_cradle.panel.phase.opening` | The rift opens | Panel: stage name - the rift is opening |  |
| 293 | `aerowarptics.gui.collapse_cradle.panel.phase.spin_up` | Spinning up | Panel: stage name - first stage |  |
| 294 | `aerowarptics.gui.collapse_cradle.panel.phase.winding` | Winding | Panel: stage name - building up |  |
| 295 | `aerowarptics.gui.collapse_cradle.panel.reading` | Reading the machine... | Panel placeholder while waiting for data from the server | CollapseCradleScreen |
| 296 | `aerowarptics.gui.collapse_cradle.panel.spat` | Spat | Label at the worst end of the instability dial | CollapseCradleScreen |
| 297 | `aerowarptics.gui.collapse_cradle.panel.speed_short` | Shaft | Panel label beside the RPM reading | CollapseCradleScreen |
| 298 | `aerowarptics.gui.collapse_cradle.panel.speed_tip` | Shaft: %1$s rpm. Needs %2$s, steadiest at %3$s. | Panel tooltip on the RPM. current, minimum, ideal | CollapseCradleScreen |
| 299 | `aerowarptics.gui.collapse_cradle.panel.stabilisers` | %1$s stabilisers | Panel line: number of linked stabilisers | CollapseCradleScreen |
| 300 | `aerowarptics.gui.collapse_cradle.panel.steady` | Steady | Label at the good end of the instability dial | CollapseCradleScreen |
| 301 | `aerowarptics.gui.collapse_cradle.panel.tray_tip` | Tray: what the cradle has made | Panel tooltip on the output tray |  |
| 302 | `aerowarptics.gui.collapse_cradle.panel.unstable` | It is failing! | Panel warning when instability is high enough to go wrong | CollapseCradleScreen |
| 303 | `aerowarptics.gui.collapse_cradle.running` | Collapsing: %1$s through, instability %2$s | Goggles line while collapsing. percent done, instability percent | CollapseCradleBlockEntity |

### Rift Plotter (107)

**Seen:** right-click the plotter (a tabbed table, plus a camera view with a narrow sidebar), its goggles tooltip, and action-bar lines when placing it.

**Limit:** `RelicTextTest` measures every row, tab, button and status line. The sidebar is narrow, which is why many of these are two words.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 340 | `aerowarptics.gui.goggles.rift_plotter` | Rift Plotter: | Heading line of the goggles tooltip | RiftPlotterBlockEntity |
| 341 | `aerowarptics.gui.goggles.rift_plotter.charge` | Charge: %1$s / %2$s mB | Plotter goggles: charge built toward the next shot | RiftPlotterBlockEntity |
| 342 | `aerowarptics.gui.goggles.rift_plotter.docking` | Docking a ship | Plotter goggles: the dock helper is moving a ship into place | RiftPlotterBlockEntity |
| 343 | `aerowarptics.gui.goggles.rift_plotter.essence` | Essence: %1$s / %2$s mB | Plotter goggles: tank level | RiftPlotterBlockEntity |
| 344 | `aerowarptics.gui.goggles.rift_plotter.exit_open` | Exit open at the target | Plotter goggles: far rift is open | RiftPlotterBlockEntity |
| 345 | `aerowarptics.gui.goggles.rift_plotter.exit_waiting` | Exit waits for traffic | Plotter goggles: far rift stays shut until something enters | RiftPlotterBlockEntity |
| 346 | `aerowarptics.gui.goggles.rift_plotter.held` | Charge held: %1$s mB | Plotter goggles: charge stored | RiftPlotterBlockEntity |
| 347 | `aerowarptics.gui.goggles.rift_plotter.lane` | Lane: %1$s mB a second | Plotter goggles: essence per second while held open as a lane | RiftPlotterBlockEntity |
| 348 | `aerowarptics.gui.goggles.rift_plotter.lined_up` | Ship lined up | Plotter goggles: a docked ship is aligned and ready | RiftPlotterBlockEntity |
| 349 | `aerowarptics.gui.goggles.rift_plotter.target` | Target %1$s, %2$s | Plotter goggles: target X, Z | RiftPlotterBlockEntity |
| 577 | `aerowarptics.gui.rift_plotter.aim_pitch` | Tipped | Entry tab: label of the up/down tilt of the entry rift | RiftPlotterScreen |
| 578 | `aerowarptics.gui.rift_plotter.aim_yaw` | Turned off | Entry tab: label of the sideways turn of the entry rift | RiftPlotterScreen |
| 579 | `aerowarptics.gui.rift_plotter.aimed` | Face the target | Entry/Face option: point the entry rift at the target | RiftPlotterScreen |
| 580 | `aerowarptics.gui.rift_plotter.at_rest` | Stop dead | Exit speed option: things leave the exit with no speed | RiftPlotterScreen |
| 581 | `aerowarptics.gui.rift_plotter.away` | Away | Label: how far from the machine the entry rift sits | RiftPlotterScreen |
| 582 | `aerowarptics.gui.rift_plotter.back` | Table | Button: return from the camera view to the main table | RiftPlotterScreen |
| 583 | `aerowarptics.gui.rift_plotter.bearing` | Bearing | Exit tab: compass direction of the exit from the target | RiftPlotterScreen |
| 584 | `aerowarptics.gui.rift_plotter.carries` | Carries | Label: what may pass through | RiftPlotterScreen |
| 585 | `aerowarptics.gui.rift_plotter.carries_all` | ships, creatures | Value of "Carries" when everything may pass | RiftPlotterScreen |
| 586 | `aerowarptics.gui.rift_plotter.carries_vehicles` | ships only | Value of "Carries" when the server limits it to ships | RiftPlotterScreen |
| 587 | `aerowarptics.gui.rift_plotter.charging` | Charging %1$s (%2$s / %3$s mB) | Status line while charging. percent, held, needed | RiftPlotterScreen |
| 588 | `aerowarptics.gui.rift_plotter.climb` | Climb %1$s° | Exit facing readout: angled upward | RiftPlotterScreen |
| 589 | `aerowarptics.gui.rift_plotter.closing` | The rift is healing | Status line while the rift shuts | RiftPlotterScreen |
| 590 | `aerowarptics.gui.rift_plotter.coverage` | %1$s known | Target tab: how much of the surveyed ground is known. percent | RiftPlotterScreen |
| 591 | `aerowarptics.gui.rift_plotter.distance` | Out from target | Exit tab: label - how far the exit is from the target | RiftPlotterScreen |
| 592 | `aerowarptics.gui.rift_plotter.dive` | Dive %1$s° | Exit facing readout: angled downward | RiftPlotterScreen |
| 593 | `aerowarptics.gui.rift_plotter.dock` | Dock helper | Dock tab: checkbox turning the dock helper on | RiftPlotterScreen |
| 594 | `aerowarptics.gui.rift_plotter.dock_auto` | Auto (%1$s m) | Dock size value when left on automatic | RiftPlotterScreen |
| 595 | `aerowarptics.gui.rift_plotter.dock_delay` | Docked wait | Dock tab: label - how long a ship waits docked before launch | RiftPlotterScreen |
| 596 | `aerowarptics.gui.rift_plotter.dock_depth` | Pull depth | Dock tab: label - how far back the dock catches ships | RiftPlotterScreen |
| 597 | `aerowarptics.gui.rift_plotter.dock_empty` | Dock: waiting for a ship | Status: dock is on, no ship present | RiftPlotterScreen |
| 598 | `aerowarptics.gui.rift_plotter.dock_holding` | Dock: lining a ship up | Status: dock is moving a ship into line | RiftPlotterScreen |
| 599 | `aerowarptics.gui.rift_plotter.dock_lined_up` | Dock: ship lined up | Status: ship aligned | RiftPlotterScreen |
| 600 | `aerowarptics.gui.rift_plotter.dock_radius` | Pull radius | Dock tab: label - how far out sideways the dock catches ships | RiftPlotterScreen |
| 601 | `aerowarptics.gui.rift_plotter.engage` | Engage | Main button: charge and open the rift | RiftPlotterScreen |
| 602 | `aerowarptics.gui.rift_plotter.entry_title` | Entry | Heading of the entry editor | RiftPlotterScreen |
| 603 | `aerowarptics.gui.rift_plotter.exit_speed` | Exit speed | Exit tab: label of the speed setting | RiftPlotterScreen |
| 604 | `aerowarptics.gui.rift_plotter.face_out` | Face away from machine | Face option: entry rift faces directly away from the machine | RiftPlotterScreen |
| 605 | `aerowarptics.gui.rift_plotter.face_pitch` | Tipped | Face tab: label of the tilt reading | RiftPlotterScreen |
| 606 | `aerowarptics.gui.rift_plotter.face_yaw` | Facing | Face tab: label of the direction reading | RiftPlotterScreen |
| 607 | `aerowarptics.gui.rift_plotter.far_end` | Exit %1$s %2$s  ·  costs %3$s mB | Footer summary: exit distance and direction, and essence cost | RiftPlotterScreen |
| 608 | `aerowarptics.gui.rift_plotter.fire_on_dock` | Fire when a ship docks | Fire tab: checkbox - open automatically when a ship docks | RiftPlotterScreen |
| 609 | `aerowarptics.gui.rift_plotter.fire_on_redstone` | Fire on redstone | Fire tab: checkbox - open on a redstone pulse | RiftPlotterScreen |
| 610 | `aerowarptics.gui.rift_plotter.fits` | Hulls up to | Label: widest ship the rift accepts | RiftPlotterScreen |
| 611 | `aerowarptics.gui.rift_plotter.fits_value` | %1$s m across | Value for that label | RiftPlotterScreen |
| 612 | `aerowarptics.gui.rift_plotter.ground` | Ground | Label of the ground-height reading at the target | RiftPlotterScreen |
| 613 | `aerowarptics.gui.rift_plotter.groundward` | Groundward | Exit facing readout: pointing straight down | RiftPlotterScreen |
| 614 | `aerowarptics.gui.rift_plotter.heading` | Bearing | Exit tab: label - which way the exit faces | RiftPlotterScreen |
| 615 | `aerowarptics.gui.rift_plotter.height` | Height | Exit tab: label - exit height above the target | RiftPlotterScreen |
| 616 | `aerowarptics.gui.rift_plotter.hint.field` | Drag to look around, scroll to come closer. Crouch aboard a docked ship to break free. | Hint under the camera view | RiftPlotterScreen |
| 617 | `aerowarptics.gui.rift_plotter.hint.target` | Click the model to move the target onto the block you click. Drag to turn it, scroll to come closer. | Hint under the 3D ground model | RiftPlotterScreen |
| 618 | `aerowarptics.gui.rift_plotter.keep_speed` | Own speed | Exit speed option: things keep the speed they entered with | RiftPlotterScreen |
| 619 | `aerowarptics.gui.rift_plotter.lane` | Hold open as a lane | Fire tab: checkbox - keep the rift open continuously | RiftPlotterScreen |
| 620 | `aerowarptics.gui.rift_plotter.lane_open` | Lane open - %1$s through | Status while a lane is open. %1$s = number of things that have gone through | RiftPlotterScreen |
| 621 | `aerowarptics.gui.rift_plotter.last` | Last: %1$s | Status line: why the last shot ended or was refused | RiftPlotterScreen |
| 622 | `aerowarptics.gui.rift_plotter.level` | Level | Exit facing readout: horizontal | RiftPlotterScreen |
| 623 | `aerowarptics.gui.rift_plotter.no_survey` | No model of the ground there yet. Survey the target to raise one. | Shown in place of the ground model before a survey | RiftPlotterScreen |
| 624 | `aerowarptics.gui.rift_plotter.no_target` | Type the target's X and Z on the Target tab, then Survey. | Prompt on the main table before a target is entered | RiftPlotterScreen |
| 625 | `aerowarptics.gui.rift_plotter.no_target_short` | No target set | Short form of that prompt for the sidebar | RiftPlotterScreen |
| 626 | `aerowarptics.gui.rift_plotter.open` | Open %1$ss - %2$s through | Status while the rift is open. seconds left, number through | RiftPlotterScreen |
| 627 | `aerowarptics.gui.rift_plotter.plotted` | PLOTTED | Stamp shown on the table once the settings are saved to the machine | RiftPlotterScreen |
| 628 | `aerowarptics.gui.rift_plotter.radius` | Radius | Entry tab: label - entry rift size | RiftPlotterScreen |
| 629 | `aerowarptics.gui.rift_plotter.reach` | Thrown out | Label: how far in front of the machine the entry opens | RiftPlotterScreen |
| 630 | `aerowarptics.gui.rift_plotter.reaching` | Reaching the far end | Status: loading the far end | RiftPlotterScreen |
| 631 | `aerowarptics.gui.rift_plotter.ready` | Ready to engage | Status: charged and able to open | RiftPlotterScreen |
| 632 | `aerowarptics.gui.rift_plotter.sending` | Laying the plot... | Placeholder while settings are being sent to the server | RiftPlotterScreen |
| 633 | `aerowarptics.gui.rift_plotter.side.charging` | Charging %1$s | Sidebar (camera view) status: charging percent | RiftPlotterScreen |
| 634 | `aerowarptics.gui.rift_plotter.side.cost` | Opens for %1$s mB | Sidebar: essence cost to open | RiftPlotterScreen |
| 635 | `aerowarptics.gui.rift_plotter.side.dock` | Dock | Sidebar tab: dock settings |  |
| 636 | `aerowarptics.gui.rift_plotter.side.facing` | Face | Sidebar tab: entry facing |  |
| 637 | `aerowarptics.gui.rift_plotter.side.fire` | Fire | Sidebar tab: firing options |  |
| 638 | `aerowarptics.gui.rift_plotter.side.lane_cost` | %1$s mB + %2$s/s | Sidebar: cost to open plus cost per second as a lane | RiftPlotterScreen |
| 639 | `aerowarptics.gui.rift_plotter.side.rift` | Rift | Sidebar tab: entry position and size |  |
| 640 | `aerowarptics.gui.rift_plotter.skyward` | Skyward | Exit facing readout: pointing straight up | RiftPlotterScreen |
| 641 | `aerowarptics.gui.rift_plotter.stand_down` | Stand down | Main button while charging/open: cancel | RiftPlotterScreen |
| 642 | `aerowarptics.gui.rift_plotter.survey` | Survey | Target tab: button that maps the ground at the target | RiftPlotterScreen |
| 643 | `aerowarptics.gui.rift_plotter.surveying` | Surveying the ground round the target... | Status while that survey runs | RiftPlotterScreen |
| 644 | `aerowarptics.gui.rift_plotter.surveying_short` | Surveying | Short form for the sidebar | RiftPlotterScreen |
| 645 | `aerowarptics.gui.rift_plotter.tab.entry` | Entry | Main table tab |  |
| 646 | `aerowarptics.gui.rift_plotter.tab.exit` | Exit | Main table tab |  |
| 647 | `aerowarptics.gui.rift_plotter.tab.target` | Target | Main table tab |  |
| 648 | `aerowarptics.gui.rift_plotter.tank` | Tank %1$s / %2$s mB  ·  charge %3$s mB | Footer: tank level and stored charge | RiftPlotterScreen |
| 649 | `aerowarptics.gui.rift_plotter.tilt` | Elevation | Exit tab: label - up/down angle of the exit | RiftPlotterScreen |
| 650 | `aerowarptics.gui.rift_plotter.title` | Rift Plotter | Screen title | RiftPlotterScreen |
| 651 | `aerowarptics.gui.rift_plotter.window` | Open for | Fire tab: label - how long the rift stays open | RiftPlotterScreen |
| 652 | `aerowarptics.gui.rift_plotter.x` | Target X | Target tab: coordinate field label | RiftPlotterScreen |
| 653 | `aerowarptics.gui.rift_plotter.z` | Target Z | Target tab: coordinate field label | RiftPlotterScreen |
| 966 | `aerowarptics.plotter.failure.busy` | Already at work | Plotter refusal / "Last:" reason: already charging or open |  |
| 967 | `aerowarptics.plotter.failure.disabled` | Plotters are off here | Plotter refusal: server has plotters turned off |  |
| 968 | `aerowarptics.plotter.failure.entry_buried` | Entry is in the ground | Plotter refusal: entry rift position is inside blocks |  |
| 969 | `aerowarptics.plotter.failure.exit_buried` | Exit is in the ground | Plotter refusal: exit position is inside blocks |  |
| 970 | `aerowarptics.plotter.failure.far_end_unreached` | Far end never loaded | Plotter refusal: target chunks would not load |  |
| 971 | `aerowarptics.plotter.failure.fastened` | Turned back: tied down | Reason a ship was not let through: attached to something |  |
| 972 | `aerowarptics.plotter.failure.lane_starved` | Lane ran out of essence | Reason a lane closed: tank ran dry |  |
| 973 | `aerowarptics.plotter.failure.lost_rotation` | Collapsed: shaft slowed | Reason the rift closed: shaft dropped below minimum speed |  |
| 974 | `aerowarptics.plotter.failure.no_target` | No target set | Plotter refusal: no target entered |  |
| 975 | `aerowarptics.plotter.failure.none` | Ready | Plotter status when nothing is wrong |  |
| 976 | `aerowarptics.plotter.failure.not_turning` | Not turning fast enough | Plotter refusal: shaft too slow |  |
| 977 | `aerowarptics.plotter.failure.outside_world` | Exit is outside the world | Plotter refusal: exit beyond the world border or height limits |  |
| 978 | `aerowarptics.plotter.failure.too_large` | Turned back: too wide | Reason a ship was not let through: wider than the rift |  |
| 979 | `aerowarptics.plotter.state.charging` | Charging | Plotter state (goggles, screen) |  |
| 980 | `aerowarptics.plotter.state.closing` | Healing | Plotter state: rift shutting |  |
| 981 | `aerowarptics.plotter.state.idle` | At rest | Plotter state: doing nothing |  |
| 982 | `aerowarptics.plotter.state.open` | Rift open | Plotter state: rift open |  |
| 983 | `aerowarptics.plotter.state.reaching` | Reaching the far end | Plotter state: loading the far end |  |
| 1156 | `aerowarptics.rift_plotter.aboard` | Too heavy for any hull: set it down on the ground | Action bar when trying to place the plotter on a ship | RiftPlotterItem |
| 1157 | `aerowarptics.rift_plotter.no_room` | Needs clear room three blocks across and three high, centred where you aim | Action bar when the 3x3x3 space is not clear | RiftPlotterItem |

### Rift Ward (37)

**Seen:** right-click the ward (Field and Who tabs), its goggles tooltip, and action-bar lines. In this mod's wording a ward *turns* a rift, meaning it deflects an incoming warp to the edge of its field.

**Limit:** `RelicTextTest`.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 351 | `aerowarptics.gui.goggles.rift_ward` | Rift Ward: | Heading line of the goggles tooltip | RiftWardBlockEntity |
| 352 | `aerowarptics.gui.goggles.rift_ward.charge` | Charge: %1$s / %2$s mB | Ward goggles: stored charge | RiftWardBlockEntity |
| 353 | `aerowarptics.gui.goggles.rift_ward.essence` | Essence: %1$s / %2$s mB | Ward goggles: tank level | RiftWardBlockEntity |
| 354 | `aerowarptics.gui.goggles.rift_ward.radius` | Field: %1$s blocks out | Ward goggles: field radius | RiftWardBlockEntity |
| 355 | `aerowarptics.gui.goggles.rift_ward.self_test` | Turns its owner too | Ward goggles: the "Turn me too" option is on | RiftWardBlockEntity |
| 356 | `aerowarptics.gui.goggles.rift_ward.turned` | Rifts turned aside: %1$s | Ward goggles: count of rifts deflected | RiftWardBlockEntity |
| 680 | `aerowarptics.gui.rift_ward.add` | Add | Who tab: add a network to the list | RiftWardScreen |
| 681 | `aerowarptics.gui.rift_ward.charge` | Charge | Field tab: label of the charge bar | RiftWardScreen |
| 682 | `aerowarptics.gui.rift_ward.close` | Close | Close button | RiftWardScreen |
| 683 | `aerowarptics.gui.rift_ward.everyone` | No one is let through | Who tab: shown when the mode blocks everyone and the list does not apply | RiftWardScreen |
| 684 | `aerowarptics.gui.rift_ward.hint.0` | Drag the world to look | Field tab: first line of the camera hint (split over two lines to fit) |  |
| 685 | `aerowarptics.gui.rift_ward.hint.1` | round, scroll to zoom | Second line of that hint - reads on from hint.0 |  |
| 686 | `aerowarptics.gui.rift_ward.network` | Network name | Who tab: placeholder in the network name box | RiftWardScreen |
| 687 | `aerowarptics.gui.rift_ward.none_named` | No networks named | Who tab: empty network list | RiftWardScreen |
| 688 | `aerowarptics.gui.rift_ward.off` | Field off | Field tab: on/off button, off | RiftWardScreen |
| 689 | `aerowarptics.gui.rift_ward.on` | Field on | Field tab: on/off button, on | RiftWardScreen |
| 690 | `aerowarptics.gui.rift_ward.per_minute` | %1$s mB a minute | Field tab: value of the running cost | RiftWardScreen |
| 691 | `aerowarptics.gui.rift_ward.radius` | Field radius | Field tab: label of the radius slider | RiftWardScreen |
| 692 | `aerowarptics.gui.rift_ward.save` | Save | Save button | RiftWardScreen |
| 693 | `aerowarptics.gui.rift_ward.self_test` | Turn me too | Who tab: checkbox - the ward deflects its owner as well | RiftWardScreen |
| 694 | `aerowarptics.gui.rift_ward.tab.field` | Field | Tab name |  |
| 695 | `aerowarptics.gui.rift_ward.tab.who` | Who | Tab name |  |
| 696 | `aerowarptics.gui.rift_ward.tank` | Tank | Field tab: label of the tank gauge | RiftWardScreen |
| 697 | `aerowarptics.gui.rift_ward.title` | Rift Ward | Screen title | RiftWardScreen |
| 698 | `aerowarptics.gui.rift_ward.turned` | Rifts turned aside: %1$s | Field tab: count of rifts deflected | RiftWardScreen |
| 699 | `aerowarptics.gui.rift_ward.upkeep` | Upkeep | Field tab: label of the running cost | RiftWardScreen |
| 946 | `aerowarptics.message.ward_no_such_network` | No network is called '%s' | Reply when adding a network name that does not exist to a ward | ServerboundConfigureWardPacket |
| 947 | `aerowarptics.message.ward_not_yours` | You do not own this ward | Reply when editing someone else's ward | ServerboundConfigureWardPacket |
| 948 | `aerowarptics.message.ward_saved` | Ward saved | Reply after saving a ward | ServerboundConfigureWardPacket |
| 1158 | `aerowarptics.rift_ward.aboard` | Too heavy for any hull: set it down on the ground | Action bar when trying to place the ward on a ship | RiftWardItem |
| 1159 | `aerowarptics.rift_ward.mode.allow_networks` | Lets networks through | Ward mode button: block everyone except the listed networks |  |
| 1160 | `aerowarptics.rift_ward.mode.block_all` | Turns everyone | Ward mode button: block everyone |  |
| 1161 | `aerowarptics.rift_ward.mode.block_networks` | Turns networks only | Ward mode button: block only the listed networks |  |
| 1162 | `aerowarptics.rift_ward.no_room` | Needs clear room three blocks across and three high, centred where you aim | Action bar when the 3x3x3 space is not clear | RiftWardItem |
| 1163 | `aerowarptics.rift_ward.state.down` | Field down | Ward state (goggles, screen): out of charge | RiftWardBlockEntity |
| 1164 | `aerowarptics.rift_ward.state.off` | Switched off | Ward state: switched off | RiftWardBlockEntity |
| 1165 | `aerowarptics.rift_ward.state.standing` | Field standing | Ward state: field up | RiftWardBlockEntity |

### Networks (shared access groups) (27)

**Seen:** the Members panel opened from an anchor or gate, and the filter strip on the chart and dial screens.

**Limit:** `NetworkTextTest` holds notices to the info line and labels to their buttons.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 363 | `aerowarptics.gui.network.add` | Add | Members panel: add-player button | NetworkScreen |
| 364 | `aerowarptics.gui.network.add_hint` | Player name | Members panel: placeholder in the name box | NetworkScreen |
| 365 | `aerowarptics.gui.network.asking` | Asking... | Members panel: placeholder while the member list loads | NetworkScreen |
| 366 | `aerowarptics.gui.network.confirm_disband` | Really disband? | Members panel: Disband button's second-click confirmation | NetworkScreen |
| 367 | `aerowarptics.gui.network.confirm_leave` | Really leave? | Members panel: Leave button's second-click confirmation | NetworkScreen |
| 368 | `aerowarptics.gui.network.disband` | Disband | Members panel: owner's button to delete the network | NetworkScreen |
| 369 | `aerowarptics.gui.network.filter_all` | All networks | Filter strip on the chart and gate dial: show destinations from every network | NetworkFilterStrip |
| 370 | `aerowarptics.gui.network.leave` | Leave | Members panel: member's button to leave | NetworkScreen |
| 371 | `aerowarptics.gui.network.member_count` | %s players | Members panel: count line | NetworkScreen |
| 372 | `aerowarptics.gui.network.members` | Members | Button on the anchor/gate screens that opens the members panel | RiftGateDialScreen, WarpAnchorScreen |
| 373 | `aerowarptics.gui.network.owner_tag` | owner | Tag beside the owner's name in the member list | NetworkScreen |
| 374 | `aerowarptics.gui.network.remove` | Remove | Members panel: remove-player button | NetworkScreen |
| 375 | `aerowarptics.gui.network.title` | Network | Members panel title | NetworkScreen |
| 950 | `aerowarptics.network.notice.added` | %s added | Info line on the members panel after an action. %s = player | NetworkNotice |
| 951 | `aerowarptics.network.notice.already_member` | %s is already in it | Members panel info line | NetworkNotice |
| 952 | `aerowarptics.network.notice.disabled` | Networks are off on this server | Info line: the server has networks turned off | NetworkNotice |
| 953 | `aerowarptics.network.notice.full` | That network is full | Info line: member limit reached | NetworkNotice |
| 954 | `aerowarptics.network.notice.gone` | That network is gone | Info line: the network was deleted while the panel was open | NetworkNotice |
| 955 | `aerowarptics.network.notice.name_invalid` | Names are 1 to 24 characters | Info line: bad network name | NetworkNotice |
| 956 | `aerowarptics.network.notice.name_taken` | %s is taken | Info line: a network with that name exists | NetworkNotice |
| 957 | `aerowarptics.network.notice.no_player` | %s is not online | Info line: can only add players who are online | NetworkNotice |
| 958 | `aerowarptics.network.notice.not_member` | Not a member of %s | Info line: you are not in the named network | NetworkNotice |
| 959 | `aerowarptics.network.notice.not_owner` | Only its owner can do that | Info line: owner-only action | NetworkNotice |
| 960 | `aerowarptics.network.notice.owner_stays` | Its owner cannot leave it | Info line: the owner pressed Leave | NetworkNotice |
| 961 | `aerowarptics.network.notice.removed` | %s removed | Info line after removing a player | NetworkNotice |
| 962 | `aerowarptics.network.notice.renamed` | Renamed to %s | Info line after renaming | NetworkNotice |
| 963 | `aerowarptics.network.notice.too_many` | You own too many networks | Info line: per-player network limit reached | NetworkNotice |

### Goggles tooltips for the smaller blocks (49)

**Seen:** looking at a block while wearing Create's goggles. `gui.goggles.<block>` is the heading line; the rest are the lines beneath.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 304 | `aerowarptics.gui.cooling_vent.hint` | Place it against a Rift Drive and turn it with a shaft | Goggles line on a vent that is not doing anything yet | CoolingVentBlockEntity |
| 305 | `aerowarptics.gui.cooling_vent.required_rpm` | Needs %1$s RPM | Goggles line: minimum speed | CoolingVentBlockEntity |
| 306 | `aerowarptics.gui.cooling_vent.status.not_linked` | Not beside a Rift Drive | Goggles status: no drive next to it |  |
| 307 | `aerowarptics.gui.cooling_vent.status.ready` | Ready - the next cooldown is shorter | Goggles status: turning, will shorten the next cooldown |  |
| 308 | `aerowarptics.gui.cooling_vent.status.stopped` | Not turning fast enough | Goggles status: shaft too slow |  |
| 309 | `aerowarptics.gui.cooling_vent.status.surplus` | The drive has all the cooling it can use | Goggles status: the drive already has the maximum four coolers |  |
| 310 | `aerowarptics.gui.cooling_vent.status.venting` | Venting the drive's heat | Goggles status: drive is cooling down now |  |
| 311 | `aerowarptics.gui.drive_extender.hint` | Build a solid cube of these with a Rift Drive inside it | Goggles line on a loose extender | DriveExtenderBlockEntity |
| 312 | `aerowarptics.gui.drive_extender.incomplete` | Not part of a drive - the cube is not complete yet | Goggles line / message on an extender in an unfinished cube | DriveExtenderBlock, DriveExtenderBlockEntity |
| 329 | `aerowarptics.gui.goggles.astrolabe` | Astrolabe | Heading line of the goggles tooltip | AstrolabeBlockEntity |
| 330 | `aerowarptics.gui.goggles.collapse_cradle` | Collapse Cradle: | Heading line of the goggles tooltip | CollapseCradleBlockEntity |
| 331 | `aerowarptics.gui.goggles.cooling_vent` | Cooling Vent | Heading line of the goggles tooltip | CoolingVentBlockEntity |
| 332 | `aerowarptics.gui.goggles.drive_extender` | Drive Extender | Heading line of the goggles tooltip | DriveExtenderBlockEntity |
| 333 | `aerowarptics.gui.goggles.rift_brazier` | Rift Brazier: | Heading line of the goggles tooltip | RiftBrazierBlockEntity |
| 334 | `aerowarptics.gui.goggles.rift_chute` | Rift Chute | Heading line of the goggles tooltip | RiftChuteBlockEntity |
| 335 | `aerowarptics.gui.goggles.rift_coupler` | Rift Coupler | Heading line of the goggles tooltip | RiftCouplerBlockEntity |
| 336 | `aerowarptics.gui.goggles.rift_drive` | Rift Drive | Heading line of the goggles tooltip | RiftDriveBlockEntity |
| 337 | `aerowarptics.gui.goggles.rift_fissure` | Rift Fissure | Heading line of the goggles tooltip | RiftFissureBlockEntity |
| 338 | `aerowarptics.gui.goggles.rift_gate` | Rift Gate | Heading line of the goggles tooltip | RiftGateBlockEntity |
| 339 | `aerowarptics.gui.goggles.rift_modulator` | Rift Modulator | Heading line of the goggles tooltip | RiftModulatorBlockEntity |
| 350 | `aerowarptics.gui.goggles.rift_probe` | Rift Probe | Heading line of the goggles tooltip | RiftProbeBlockEntity |
| 357 | `aerowarptics.gui.goggles.spatial_siphon` | Spatial Siphon | Heading line of the goggles tooltip | SpatialSiphonBlockEntity |
| 358 | `aerowarptics.gui.goggles.spatial_stabiliser` | Spatial Stabiliser | Heading line of the goggles tooltip | SpatialStabiliserBlockEntity |
| 359 | `aerowarptics.gui.goggles.spatial_wave_detector` | Spatial Wave Detector | Heading line of the goggles tooltip | SpatialWaveDetectorBlockEntity |
| 360 | `aerowarptics.gui.goggles.warp_anchor` | Warp Anchor | Heading line of the goggles tooltip | WarpAnchorBlockEntity |
| 361 | `aerowarptics.gui.handbook.title` | The Navigator's Handbook | Title of the handbook screen | HandbookScreen |
| 362 | `aerowarptics.gui.infinite` | Infinite | Word used where a range or reach has no limit (Creative drive) | AWLang |
| 376 | `aerowarptics.gui.rift_brazier.contents` | Essence: %1$s / %2$s mB | Brazier goggles: tank level | RiftBrazierBlockEntity |
| 377 | `aerowarptics.gui.rift_brazier.dry` | Powered, but out of essence | Brazier goggles: has redstone, no essence | RiftBrazierBlockEntity |
| 378 | `aerowarptics.gui.rift_brazier.lit` | Lit: burning with rift fire | Brazier goggles: burning | RiftBrazierBlockEntity |
| 379 | `aerowarptics.gui.rift_brazier.unlit` | Unlit - give it a redstone signal | Brazier goggles: no redstone signal | RiftBrazierBlockEntity |
| 510 | `aerowarptics.gui.rift_fissure.draining` | Drawing into a Spatial Siphon | Fissure goggles: a siphon is emptying it | RiftFissureBlockEntity |
| 511 | `aerowarptics.gui.rift_fissure.held` | %s / %s mB | Fissure goggles: essence left | RiftFissureBlockEntity |
| 512 | `aerowarptics.gui.rift_fissure.hint` | Place a Spatial Siphon beside it | Fissure goggles: what to do with it | RiftFissureBlockEntity |
| 700 | `aerowarptics.gui.spatial_siphon.contents` | %s / %s mB | Siphon goggles: tank level | SpatialSiphonBlockEntity |
| 701 | `aerowarptics.gui.spatial_stabiliser.hint` | Give it a redstone signal to switch it on | Stabiliser goggles: how to switch it on | SpatialStabiliserBlockEntity |
| 702 | `aerowarptics.gui.spatial_stabiliser.status.attending` | Linked to a Collapse Cradle | Stabiliser goggles: near a Collapse Cradle, will count toward a collapse |  |
| 703 | `aerowarptics.gui.spatial_stabiliser.status.cushioning` | Cushioning this ship | Stabiliser goggles: on and smoothing the ship's motion |  |
| 704 | `aerowarptics.gui.spatial_stabiliser.status.deferring` | Another stabiliser aboard is already doing this | Stabiliser goggles: another one on the ship is active, this one does nothing |  |
| 705 | `aerowarptics.gui.spatial_stabiliser.status.grounded` | Not aboard an airship | Stabiliser goggles: beside a drive that is not on a ship |  |
| 706 | `aerowarptics.gui.spatial_stabiliser.status.not_linked` | Not beside a Rift Drive or near a Collapse Cradle | Stabiliser goggles: nothing nearby for it to work with |  |
| 707 | `aerowarptics.gui.spatial_stabiliser.status.off` | Switched off | Stabiliser goggles: no redstone signal |  |
| 708 | `aerowarptics.gui.spatial_stabiliser.status.standing_down` | Standing by while the drive warps | Stabiliser goggles: paused during a warp |  |
| 709 | `aerowarptics.gui.spatial_stabiliser.status.steadying` | Steadying a collapse | Stabiliser goggles: a collapse is running and it is helping |  |
| 710 | `aerowarptics.gui.spatial_wave_detector.last` | Last arrival was %s away | Detector goggles: distance of the previous arrival | SpatialWaveDetectorBlockEntity |
| 711 | `aerowarptics.gui.spatial_wave_detector.listening` | Listening within %s | Detector goggles: current range | SpatialWaveDetectorBlockEntity |
| 712 | `aerowarptics.gui.spatial_wave_detector.quiet` | Nothing has arrived yet | Detector goggles: nothing detected yet | SpatialWaveDetectorBlockEntity |
| 713 | `aerowarptics.gui.spatial_wave_detector.range` | Detection Range | Label on the detector's scroll-to-set value box | SpatialWaveDetectorBlockEntity |
| 714 | `aerowarptics.gui.spatial_wave_detector.sensing` | Something arrived %s away | Detector goggles while it is giving off a pulse | SpatialWaveDetectorBlockEntity |

### Items: tooltips and use messages (27)

**Seen:** item tooltips, and the action bar when the item is used.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 75 | `aerowarptics.beacon.aim` | Nothing in sight to summon to | Action bar when a Rift Beacon is used while looking at nothing in range | RiftBeaconItem |
| 76 | `aerowarptics.beacon.bound` | Beacon bound to %s | Action bar after binding a beacon to a drive. %s = ship name | RiftBeaconItem |
| 77 | `aerowarptics.beacon.course` | Beacon (%s, %s, %s) | Name given to the course a beacon sets on the drive. %s = x, y, z | RiftBeaconItem |
| 78 | `aerowarptics.beacon.reaching` | Reaching for %s... | Action bar while the server loads the bound ship before calling it. %s = ship name | BeaconReach |
| 79 | `aerowarptics.beacon.tip.bound` | Calls %s | Beacon item tooltip once bound. %s = ship name | RiftBeaconItem |
| 80 | `aerowarptics.beacon.tip.rebind` | Aim and right-click to summon; sneak on another drive to rebind | Beacon item tooltip, second line once bound: how to use and rebind | RiftBeaconItem |
| 81 | `aerowarptics.beacon.tip.unbound` | Sneak and right-click a Rift Drive to bind it | Beacon item tooltip when not bound yet: how to bind | RiftBeaconItem |
| 233 | `aerowarptics.goggles.tip` | Reveals Rift Fissures, which are invisible without them | Tooltip on the Rift Infused Goggles item | RiftGogglesItem |
| 889 | `aerowarptics.item.rift_pearl.tooltip` | 250 mB of Rift Essence in a shell. Thrown, it teleports whatever it hits a short way, like a chorus fruit | Rift Pearl item tooltip | RiftPearlItem |
| 890 | `aerowarptics.item.scar_chart.none` | No uncharted rift scar within reach of here | Action bar when a Scar Chart finds no rift scar in range | ScarChartItem |
| 891 | `aerowarptics.item.scar_chart.tooltip` | Open it to chart the nearest rift scar nobody has charted yet | Scar Chart item tooltip | ScarChartItem |
| 892 | `aerowarptics.item.ship_compass.bound` | Points to %1$s | Ship's Compass tooltip once bound. %1$s = ship name | ShipCompassItem |
| 893 | `aerowarptics.item.ship_compass.bound_now` | Now points to %1$s | Action bar just after binding | ShipCompassItem |
| 894 | `aerowarptics.item.ship_compass.unbound` | Sneak and use it on a Rift Drive to point it at that ship | Ship's Compass tooltip before binding | ShipCompassItem |
| 895 | `aerowarptics.item.ship_compass.unnamed` | an unnamed ship | Stand-in ship name in those two lines | ShipCompassItem |
| 896 | `aerowarptics.item.stormglass.reading.clear` | Clear: no storm within a day | Stormglass tooltip reading: no storm due |  |
| 897 | `aerowarptics.item.stormglass.reading.gathering` | Gathering: a storm is due within a day | Stormglass tooltip reading: storm due within a Minecraft day |  |
| 898 | `aerowarptics.item.stormglass.reading.raging` | Raging | Stormglass tooltip reading: storm now |  |
| 899 | `aerowarptics.item.stormglass.reading.still` | Still: no storm will come on its own | Stormglass tooltip reading: natural storms are turned off |  |
| 900 | `aerowarptics.item.stormglass.tooltip` | Use to break it and call a Rift Storm down on the whole server | Stormglass item tooltip: what using it does | StormglassItem |
| 901 | `aerowarptics.item.unset_stormglass.setting` | Setting... %1$s seconds out in the storm | Tooltip while it is setting. %1$s = seconds done | UnsetStormglassItem |
| 902 | `aerowarptics.item.unset_stormglass.tooltip` | Sets only under open sky in a Rift Storm - held, or left on the ground | Unset Stormglass item tooltip: how to set it | UnsetStormglassItem |
| 1166 | `aerowarptics.stormglass.call.called` | The glass shatters, and the sky begins to tear open | Action bar after breaking a Stormglass successfully |  |
| 1167 | `aerowarptics.stormglass.call.forbidden` | The glass will not break - storms cannot be called here | Refusal: server has called storms turned off |  |
| 1168 | `aerowarptics.stormglass.call.frozen` | The weather is frozen; a storm called now would never end | Refusal: the weather-cycle gamerule is off |  |
| 1169 | `aerowarptics.stormglass.call.raging` | A storm is already raging | Refusal: a storm is already happening |  |
| 1170 | `aerowarptics.stormglass.call.too_soon` | Too soon after the last storm - the glass holds | Refusal: cooldown since the last storm |  |

### Create Display Link (91)

**Seen:** the Display Link's configuration screen when it is pointed at one of this mod's blocks, and the words it then writes onto a Display Board. The `create.display_source.*` keys must keep that prefix - Create looks them up by name.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 100 | `aerowarptics.display_source.collapse_cradle` | Collapse Cradle | Name of this source in the Display Link's list |  |
| 101 | `aerowarptics.display_source.collapse_cradle.collapsing` | Collapsing | Display Link reading: cradle running | CollapseCradleDisplaySource |
| 102 | `aerowarptics.display_source.collapse_cradle.idle` | Idle | Display Link reading: cradle idle | CollapseCradleDisplaySource |
| 103 | `aerowarptics.display_source.collapse_cradle.refused` | Refused | Display Link reading: cradle refused to start | CollapseCradleDisplaySource |
| 104 | `aerowarptics.display_source.rift_brazier` | Rift Brazier | Name of this source in the Display Link's list |  |
| 105 | `aerowarptics.display_source.rift_brazier.dry` | Dry | Display Link reading: powered but out of essence | RiftBrazierDisplaySource |
| 106 | `aerowarptics.display_source.rift_brazier.lit` | Lit | Display Link reading: burning | RiftBrazierDisplaySource |
| 107 | `aerowarptics.display_source.rift_brazier.unlit` | Unlit | Display Link reading: no redstone signal | RiftBrazierDisplaySource |
| 108 | `aerowarptics.display_source.rift_chute` | Rift Chute | Name of this source in the Display Link's list |  |
| 109 | `aerowarptics.display_source.rift_chute.held` | %s x %s | Display Link reading: item held. %s = count, %s = item name | RiftChuteDisplaySource |
| 110 | `aerowarptics.display_source.rift_coupler` | Rift Coupler | Name of this source in the Display Link's list |  |
| 111 | `aerowarptics.display_source.rift_coupler.no_flotilla` | No flotilla | Display Link reading: this coupler is in no flotilla | RiftCouplerDisplaySource, RiftCouplerRosterDisplaySource |
| 112 | `aerowarptics.display_source.rift_coupler.ready` | %s/%s in station | Display Link reading: %s of %s escorts in position | RiftCouplerDisplaySource |
| 113 | `aerowarptics.display_source.rift_coupler_roster` | Rift Coupler Roster | Name of this source in the Display Link's list |  |
| 114 | `aerowarptics.display_source.rift_coupler_roster.line` | %s: %s | One roster line on a display. %s = ship name, %s = its status | RiftCouplerRosterDisplaySource |
| 115 | `aerowarptics.display_source.rift_drive` | Rift Drive | Name of this source in the Display Link's list |  |
| 116 | `aerowarptics.display_source.rift_gate` | Rift Gate | Name of this source in the Display Link's list |  |
| 117 | `aerowarptics.display_source.rift_gate.opening` | %s x %s | Display Link reading: opening size, width x height | RiftGateDisplaySource |
| 118 | `aerowarptics.display_source.rift_modulator` | Rift Modulator | Name of this source in the Display Link's list |  |
| 119 | `aerowarptics.display_source.rift_probe` | Rift Probe | Name of this source in the Display Link's list |  |
| 120 | `aerowarptics.display_source.rift_ward` | Rift Ward | Name of this source in the Display Link's list |  |
| 121 | `aerowarptics.display_source.rift_ward.state.down` | Down | Display Link reading: field has run out of charge |  |
| 122 | `aerowarptics.display_source.rift_ward.state.off` | Off | Display Link reading: field switched off |  |
| 123 | `aerowarptics.display_source.rift_ward.state.standing` | Standing | Display Link reading: field is up |  |
| 124 | `aerowarptics.display_source.spatial_siphon` | Spatial Siphon | Name of this source in the Display Link's list |  |
| 125 | `aerowarptics.display_source.spatial_siphon.dormant` | Dormant | Display Link reading: siphon not collecting | SpatialSiphonDisplaySource |
| 126 | `aerowarptics.display_source.spatial_siphon.drawing` | Drawing | Display Link reading: siphon draining a fissure | SpatialSiphonDisplaySource |
| 1302 | `create.display_source.collapse_cradle.essence` | Essence (mB) | One choice in that selector for the `collapse_cradle` |  |
| 1303 | `create.display_source.collapse_cradle.instability` | Instability | One choice in that selector for the `collapse_cradle` |  |
| 1304 | `create.display_source.collapse_cradle.mode` | Display | Label of the "what to show" selector |  |
| 1305 | `create.display_source.collapse_cradle.progress` | Progress | One choice in that selector for the `collapse_cradle` |  |
| 1306 | `create.display_source.collapse_cradle.state` | State | One choice in that selector for the `collapse_cradle` |  |
| 1307 | `create.display_source.rift_brazier.essence` | Essence (mB) | One choice in that selector for the `rift_brazier` |  |
| 1308 | `create.display_source.rift_brazier.fill` | Fill level | One choice in that selector for the `rift_brazier` |  |
| 1309 | `create.display_source.rift_brazier.mode` | Display | Label of the "what to show" selector |  |
| 1310 | `create.display_source.rift_brazier.state` | State | One choice in that selector for the `rift_brazier` |  |
| 1311 | `create.display_source.rift_chute.essence` | Essence (mB) | One choice in that selector for the `rift_chute` |  |
| 1312 | `create.display_source.rift_chute.fill` | Fill level | One choice in that selector for the `rift_chute` |  |
| 1313 | `create.display_source.rift_chute.held` | Item held | One choice in that selector for the `rift_chute` |  |
| 1314 | `create.display_source.rift_chute.link` | Link | One choice in that selector for the `rift_chute` |  |
| 1315 | `create.display_source.rift_chute.mode` | Display | Label of the "what to show" selector |  |
| 1316 | `create.display_source.rift_chute.state` | Status | One choice in that selector for the `rift_chute` |  |
| 1317 | `create.display_source.rift_coupler.flotilla` | Flotilla | One choice in that selector for the `rift_coupler` |  |
| 1318 | `create.display_source.rift_coupler.mode` | Display | Label of the "what to show" selector |  |
| 1319 | `create.display_source.rift_coupler.ready` | Ready | One choice in that selector for the `rift_coupler` |  |
| 1320 | `create.display_source.rift_coupler.ready_count` | Ready (count) | One choice in that selector for the `rift_coupler` |  |
| 1321 | `create.display_source.rift_coupler.role` | Role | One choice in that selector for the `rift_coupler` |  |
| 1322 | `create.display_source.rift_coupler.status` | Status | One choice in that selector for the `rift_coupler` |  |
| 1323 | `create.display_source.rift_drive.charge` | Charge | One choice in that selector for the `rift_drive` |  |
| 1324 | `create.display_source.rift_drive.cooldown` | Cooldown (s) | One choice in that selector for the `rift_drive` |  |
| 1325 | `create.display_source.rift_drive.course` | Course | One choice in that selector for the `rift_drive` |  |
| 1326 | `create.display_source.rift_drive.fault` | Fault | One choice in that selector for the `rift_drive` |  |
| 1327 | `create.display_source.rift_drive.heading` | Heading | One choice in that selector for the `rift_drive` |  |
| 1328 | `create.display_source.rift_drive.mode` | Display | Label of the "what to show" selector |  |
| 1329 | `create.display_source.rift_drive.progress` | Progress | One choice in that selector for the `rift_drive` |  |
| 1330 | `create.display_source.rift_drive.state` | State | One choice in that selector for the `rift_drive` |  |
| 1331 | `create.display_source.rift_drive.tier` | Tier | One choice in that selector for the `rift_drive` |  |
| 1332 | `create.display_source.rift_gate.dial_cost` | Dial cost (mB) | One choice in that selector for the `rift_gate` |  |
| 1333 | `create.display_source.rift_gate.essence` | Essence (mB) | One choice in that selector for the `rift_gate` |  |
| 1334 | `create.display_source.rift_gate.fault` | Fault | One choice in that selector for the `rift_gate` |  |
| 1335 | `create.display_source.rift_gate.fill` | Fill level | One choice in that selector for the `rift_gate` |  |
| 1336 | `create.display_source.rift_gate.mode` | Display | Label of the "what to show" selector |  |
| 1337 | `create.display_source.rift_gate.opening` | Opening | One choice in that selector for the `rift_gate` |  |
| 1338 | `create.display_source.rift_gate.state` | State | One choice in that selector for the `rift_gate` |  |
| 1339 | `create.display_source.rift_modulator.charge` | Charge | One choice in that selector for the `rift_modulator` |  |
| 1340 | `create.display_source.rift_modulator.cooldown` | Cooldown (s) | One choice in that selector for the `rift_modulator` |  |
| 1341 | `create.display_source.rift_modulator.course` | Course | One choice in that selector for the `rift_modulator` |  |
| 1342 | `create.display_source.rift_modulator.fault` | Fault | One choice in that selector for the `rift_modulator` |  |
| 1343 | `create.display_source.rift_modulator.heading` | Heading | One choice in that selector for the `rift_modulator` |  |
| 1344 | `create.display_source.rift_modulator.mode` | Display | Label of the "what to show" selector |  |
| 1345 | `create.display_source.rift_modulator.progress` | Progress | One choice in that selector for the `rift_modulator` |  |
| 1346 | `create.display_source.rift_modulator.state` | State | One choice in that selector for the `rift_modulator` |  |
| 1347 | `create.display_source.rift_modulator.tier` | Tier | One choice in that selector for the `rift_modulator` |  |
| 1348 | `create.display_source.rift_probe.bearing` | Bearing | One choice in that selector for the `rift_probe` |  |
| 1349 | `create.display_source.rift_probe.cost` | Scan cost (mB) | One choice in that selector for the `rift_probe` |  |
| 1350 | `create.display_source.rift_probe.essence` | Essence (mB) | One choice in that selector for the `rift_probe` |  |
| 1351 | `create.display_source.rift_probe.fill` | Fill level | One choice in that selector for the `rift_probe` |  |
| 1352 | `create.display_source.rift_probe.mode` | Display | Label of the "what to show" selector |  |
| 1353 | `create.display_source.rift_probe.progress` | Progress | One choice in that selector for the `rift_probe` |  |
| 1354 | `create.display_source.rift_probe.range` | Range | One choice in that selector for the `rift_probe` |  |
| 1355 | `create.display_source.rift_probe.state` | State | One choice in that selector for the `rift_probe` |  |
| 1356 | `create.display_source.rift_ward.charge` | Charge (mB) | One choice in that selector for the `rift_ward` |  |
| 1357 | `create.display_source.rift_ward.mode` | Display | Label of the "what to show" selector |  |
| 1358 | `create.display_source.rift_ward.radius` | Radius (blocks) | One choice in that selector for the `rift_ward` |  |
| 1359 | `create.display_source.rift_ward.state` | Field | One choice in that selector for the `rift_ward` |  |
| 1360 | `create.display_source.rift_ward.turned` | Rifts turned aside | One choice in that selector for the `rift_ward` |  |
| 1361 | `create.display_source.spatial_siphon.essence` | Essence (mB) | One choice in that selector for the `spatial_siphon` |  |
| 1362 | `create.display_source.spatial_siphon.fill` | Fill level | One choice in that selector for the `spatial_siphon` |  |
| 1363 | `create.display_source.spatial_siphon.mode` | Display | Label of the "what to show" selector |  |
| 1364 | `create.display_source.spatial_siphon.room` | Room left (mB) | One choice in that selector for the `spatial_siphon` |  |
| 1365 | `create.display_source.spatial_siphon.state` | State | One choice in that selector for the `spatial_siphon` |  |

### JEI recipe categories (9)

**Seen:** the three custom recipe tabs in JEI and the extra lines drawn on each recipe.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 1147 | `aerowarptics.recipe.collapse` | Collapse | JEI recipe category name |  |
| 1148 | `aerowarptics.recipe.collapse.speed` | Turned at a %1$s's speed | JEI collapse recipe line: required speed, named by drive tier. %1$s = tier name | CollapseCategory |
| 1149 | `aerowarptics.recipe.collapse.stake` | If it goes wrong, only the Scar Heart is lost | JEI collapse recipe line: what is lost on failure | CollapseCategory |
| 1150 | `aerowarptics.recipe.collapse.time` | About %1$s seconds, less in a Rift Storm | JEI collapse recipe line: duration | CollapseCategory |
| 1151 | `aerowarptics.recipe.folding` | Folding | JEI recipe category name |  |
| 1152 | `aerowarptics.recipe.folding.essence` | %1$s mB of Rift Essence a craft | JEI folding recipe line: essence cost | FoldingCategory |
| 1153 | `aerowarptics.recipe.folding.stack` | Up to sixteen fold at once | JEI folding recipe line: batch size | FoldingCategory |
| 1154 | `aerowarptics.recipe.rifting` | Rifting | JEI recipe category name |  |
| 1155 | `aerowarptics.recipe.rifting.fan` | Encased Fan through an open rift | Name on the Encased Fan icon in the JEI rifting recipe | AWJeiPlugin |

### Sound subtitles (42)

**Seen:** bottom-right of the screen when vanilla's "Show Subtitles" accessibility option is on. Vanilla convention is a short "Thing does something" phrase.

| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 1171 | `aerowarptics.subtitle.anchor_configured` | Warp Anchor reconfigured | Closed caption for the sound `anchor_configured` |  |
| 1172 | `aerowarptics.subtitle.collapse_arc` | Rift lightning crackles | Closed caption for the sound `collapse_arc` |  |
| 1173 | `aerowarptics.subtitle.collapse_boom` | Collapse booms | Closed caption for the sound `collapse_boom` |  |
| 1174 | `aerowarptics.subtitle.collapse_drone` | Collapse Cradle drones | Closed caption for the sound `collapse_drone` |  |
| 1175 | `aerowarptics.subtitle.collapse_fling` | Core is flung | Closed caption for the sound `collapse_fling` |  |
| 1176 | `aerowarptics.subtitle.collapse_fold` | Rift folds shut | Closed caption for the sound `collapse_fold` |  |
| 1177 | `aerowarptics.subtitle.collapse_heartbeat` | Scar Heart beats | Closed caption for the sound `collapse_heartbeat` |  |
| 1178 | `aerowarptics.subtitle.collapse_howl` | Rift howls | Closed caption for the sound `collapse_howl` |  |
| 1179 | `aerowarptics.subtitle.collapse_inhale` | Collapse draws in | Closed caption for the sound `collapse_inhale` |  |
| 1180 | `aerowarptics.subtitle.collapse_settle` | Singularity Core settles | Closed caption for the sound `collapse_settle` |  |
| 1181 | `aerowarptics.subtitle.collapse_spin_up` | Collapse Cradle spins up | Closed caption for the sound `collapse_spin_up` |  |
| 1182 | `aerowarptics.subtitle.collapse_spit` | Collapse spits | Closed caption for the sound `collapse_spit` |  |
| 1183 | `aerowarptics.subtitle.collapse_tear` | Rift tears open | Closed caption for the sound `collapse_tear` |  |
| 1184 | `aerowarptics.subtitle.coolant_hiss` | Ice hisses | Closed caption for the sound `coolant_hiss` |  |
| 1185 | `aerowarptics.subtitle.destination_lock` | Destination locked | Closed caption for the sound `destination_lock` |  |
| 1186 | `aerowarptics.subtitle.drive_charged` | Rift Drive charged | Closed caption for the sound `drive_charged` |  |
| 1187 | `aerowarptics.subtitle.drive_charging` | Rift Drive spins up | Closed caption for the sound `drive_charging` |  |
| 1188 | `aerowarptics.subtitle.drive_cooldown` | Rift Drive vents | Closed caption for the sound `drive_cooldown` |  |
| 1189 | `aerowarptics.subtitle.drive_hum` | Rift Drive hums | Closed caption for the sound `drive_hum` |  |
| 1190 | `aerowarptics.subtitle.drive_spin_up` | Rift Drive winds up | Closed caption for the sound `drive_spin_up` |  |
| 1191 | `aerowarptics.subtitle.flotilla_bell` | Flotilla bell rings | Closed caption for the sound `flotilla_bell` |  |
| 1192 | `aerowarptics.subtitle.flotilla_chord` | Flotilla in harmony | Closed caption for the sound `flotilla_chord` |  |
| 1193 | `aerowarptics.subtitle.flotilla_countdown` | Flotilla counts down | Closed caption for the sound `flotilla_countdown` |  |
| 1194 | `aerowarptics.subtitle.flotilla_harmonised` | Drive harmonises | Closed caption for the sound `flotilla_harmonised` |  |
| 1195 | `aerowarptics.subtitle.flotilla_horn` | Flotilla horn sounds | Closed caption for the sound `flotilla_horn` |  |
| 1196 | `aerowarptics.subtitle.flotilla_tether` | Harmonic tether snaps out | Closed caption for the sound `flotilla_tether` |  |
| 1197 | `aerowarptics.subtitle.gate_arrive` | Traveller arrives | Closed caption for the sound `gate_arrive` |  |
| 1198 | `aerowarptics.subtitle.gate_passage` | Gate passage rushes | Closed caption for the sound `gate_passage` |  |
| 1199 | `aerowarptics.subtitle.rift_open` | Rift tears open | Closed caption for the sound `rift_open` |  |
| 1200 | `aerowarptics.subtitle.stabilizing` | Rift stabilises | Closed caption for the sound `stabilizing` |  |
| 1201 | `aerowarptics.subtitle.vent_blast` | Cooling Vent blows off steam | Closed caption for the sound `vent_blast` |  |
| 1202 | `aerowarptics.subtitle.wake_echo` | Wake Echo tears open | Closed caption for the sound `wake_echo` |  |
| 1203 | `aerowarptics.subtitle.ward_break` | Turned rift breaks open | Closed caption for the sound `ward_break` |  |
| 1204 | `aerowarptics.subtitle.ward_give_way` | Rift Ward gives way | Closed caption for the sound `ward_give_way` |  |
| 1205 | `aerowarptics.subtitle.ward_hum` | Rift Ward hums | Closed caption for the sound `ward_hum` |  |
| 1206 | `aerowarptics.subtitle.ward_return` | Rift Ward steadies | Closed caption for the sound `ward_return` |  |
| 1207 | `aerowarptics.subtitle.ward_toll` | Rift Ward tolls | Closed caption for the sound `ward_toll` |  |
| 1208 | `aerowarptics.subtitle.ward_wrench` | Rift wrenched aside | Closed caption for the sound `ward_wrench` |  |
| 1209 | `aerowarptics.subtitle.warp_exit` | Rift collapses | Closed caption for the sound `warp_exit` |  |
| 1210 | `aerowarptics.subtitle.warp_failed` | Rift Drive faults | Closed caption for the sound `warp_failed` |  |
| 1211 | `aerowarptics.subtitle.warp_scatter` | Exit destabilises | Closed caption for the sound `warp_scatter` |  |
| 1212 | `aerowarptics.subtitle.warp_travel` | Warp corridor howls | Closed caption for the sound `warp_travel` |  |

### Everything else in the language file (12)



| Line | Key | Current text | What it is for | Used in |
|---|---|---|---|---|
| 95 | `aerowarptics.commands.weather.set.rift_storm` | Set the weather to Rift Storm | Chat reply to the command that starts a Rift Storm | RiftStorm |
| 96 | `aerowarptics.configuration.title` | AeroWarptics | Title of the mod's config screen (Mods > AeroWarptics > Config) |  |
| 97 | `aerowarptics.dimension.minecraft.overworld` | Overworld | Dimension name where a screen names a dimension (probe, chart) |  |
| 98 | `aerowarptics.dimension.minecraft.the_end` | The End | Dimension name, as above |  |
| 99 | `aerowarptics.dimension.minecraft.the_nether` | The Nether | Dimension name, as above |  |
| 964 | `aerowarptics.pack.classic_recipes.description` | The drive ladder before rifting, folding and the Collapse | Description of the optional built-in data pack, on the data pack screen |  |
| 965 | `aerowarptics.pack.classic_recipes.title` | AeroWarptics: Classic Recipes | Name of that data pack | ClassicRecipes |
| 1266 | `aerowarptics.weather.rift_storm.began` | A Rift Storm is tearing the sky open. Rift Drives recover faster while it lasts, but any of them may throw a ship off course. | Chat announcement to everyone when a Rift Storm starts | RiftStorm |
| 1267 | `aerowarptics.weather.rift_storm.called` | %1$s broke a Stormglass and called a Rift Storm | Chat announcement when a player starts one with a Stormglass. %1$s = player | RiftStorm |
| 1268 | `aerowarptics.weather.rift_storm.ended` | The Rift Storm has passed. | Chat announcement when the storm ends | RiftStorm |
| 1366 | `death.attack.aerowarptics.collapse` | %1$s was folded into a point | Death message: killed by an unstable collapse. %1$s = player |  |
| 1367 | `death.attack.aerowarptics.collapse.player` | %1$s was folded into a point while fighting %2$s | Same, while in combat. %2$s = the attacker |  |

