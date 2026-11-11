# Larson's Game

A 3D game whose characters and items are **2D sprites** — pre-rendered in
Blender from eight directions and three heights, and stacked layer by layer so
every shirt, hat and sword is its own sprite sheet — built on mechanics from
[Larsons-Game-Engine](https://github.com/Larleeloo/Larsons-Game-Engine).
Everything is drawn on the GPU with OpenGL.

<p align="center">
  <img src="docs/screenshots/layers-walk.png" width="49%" alt="A layered character — body, cap and sword, each its own sprite sheet — walking in the 3D void">
  <img src="docs/screenshots/demo-fallback.png" width="49%" alt="The 32-pixel fallback character beside the hovering sword pickup">
</p>

This README is also the plan: each task is a set of chunks, and each chunk is
ticked off as it lands.

- [Quick start](#quick-start) (and [sharing the game](#sharing-the-game))
- [The loading screen](#the-loading-screen)
- [Controls](#controls)
- [What's in the demo](#whats-in-the-demo)
- [The sprite stack](#the-sprite-stack)
- [The inventory and the hotbar](#the-inventory-and-the-hotbar)
- [Wearing what you own](#wearing-what-you-own)
- [The treasure chest](#the-treasure-chest)
- [Items, rarity and loot](#items-rarity-and-loot)
- [Fighting](#fighting)
- [Sound](#sound)
- [The scenery](#the-scenery)
- [Biomes and water](#biomes-and-water)
- [Day and night](#day-and-night)
- [Life: critters and foraging](#life-critters-and-foraging)
- [Animals](#animals)
- [Buildings](#buildings) (and [going inside](#going-inside))
- [Creative Mode](#creative-mode)
- [GPU acceleration](#gpu-acceleration)
- [Project layout](#project-layout)
- [What came from the engine](#what-came-from-the-engine)
- [Tests and scripted runs](#tests-and-scripted-runs)
- [The plan](#the-plan)

---

## Quick start

Needs **JDK 21** and a graphics driver with **OpenGL 3.3** (anything from the
last decade). Gradle comes with the repository.

**IntelliJ IDEA:** open the folder, let it import the Gradle project, and pick
**Run Game (GPU)** from the run-configuration dropdown (**Build Game to Share (zip)** makes the
copy to send to friends).

**Command line:**

```bash
./gradlew runGpu        # the game, GPU acceleration on (what the IntelliJ profile runs)
./gradlew run           # the same game, letting the OS pick the GPU
./gradlew test          # unit tests — no GPU needed
./gradlew sampleSprites # a stand-in sprite set in build/sample-sprites/ to try the importer on
./gradlew gameJar       # build/libs/larsons-game.jar: LWJGL and its natives for Windows, macOS, Linux (assets/ beside it)
./gradlew shareZip      # build/distributions/Larsons-Game.zip: the game to send to friends (see below)
```

The main menu has three tabs: **Play** (the demo, a meadow below the
highlands), **Cutscene** (the menu's cutscene, played on its own) and **Dev**,
which shows only once **1025** has been typed on the menu (either row of
digits) and holds [Creative Mode](#creative-mode), the level editor.

## Sharing the game

```bash
./gradlew shareZip      # build/distributions/Larsons-Game.zip, about 4.2 GB
./gradlew shareZip -x spritePack   # only the code changed: keep the last pack (packing takes minutes)
```

In IntelliJ: pick **Build Game to Share (zip)** in the run-configuration
dropdown and press Run (or double-click `shareZip` under Gradle > Tasks >
distribution); the Run window prints where the zip is when it is done. To
try it first, unzip it somewhere and start it the way a friend would. The
build stops with an error rather than make a zip without the sprite pack.
Upload it to Google Drive, OneDrive or Dropbox and send the link - it is too
big for GitHub (2 GB a file) or a free WeTransfer.

The zip is the game for people who never open a terminal: one folder,
`Larsons-Game/`, to unzip and double-click. All they need is **Java 21** or
newer (free: [Temurin](https://adoptium.net/temurin/releases/?version=21)),
and `HOW TO PLAY.txt` walks them through it.

| In `Larsons-Game/` | |
|---|---|
| `Play on Windows.bat`, `Play on Mac.command`, `Play on Linux.sh` | double-click to play: each finds a Java 21 or newer (or says how to get one and opens the download page), starts the jar, and if the game stops with an error, says which log to send |
| `HOW TO PLAY.txt` | installing Java, unzipping, starting it (and getting past Windows' and macOS's warnings for files from the internet), the controls, what to send as feedback, and what to try when something goes wrong |
| `larsons-game.jar` | the game, LWJGL and its natives for Windows, macOS and Linux, x64 and ARM64 (5.5 MB) |
| `assets/` | the assets folder as it is, but for the sprites folders, which are... |
| `assets/sprites.pack.001`, `.002` ... and `sprites.pack.parts` | ...`sprites/`, `sprites_masculine/` and `animations_v1/` in one file, `sprites.pack`, in parts of 1 GB that the game joins the first time it starts |

**Why a pack.** The sprites folders are some two million small PNGs - 3.7 GB,
far too many files for Windows' own unzip, which writes them out one by one
for hours. So the
share build stores them, as they are and under the same paths, in one
uncompressed zip (`tools/PackSprites`, a few minutes), and the game reads
them out of it in place through Java's zip file system (`core/SpritePack`):
a sheet in the pack is an ordinary `Path` (`/sprites/hair/bob_px128/...`), so
the index, the bodies' folders and the classic animations work as they do
from folders. A real `assets/sprites/` folder always wins, so the repository
runs exactly as before. In a packed copy, sprite sheets dropped on the window
are not imported (there is no folder to put them in); everything else is
written where it always is (`config/`, `screenshots/`, Creative Mode's
levels in `assets/levels/`). The zip file system keeps the pack's index in
memory - about half a gigabyte of the heap for two million entries.

**Why parts.** One multi-gigabyte file is just what Windows' own unzip is
not to be trusted with, so the pack (`build/sprite-pack/`, a folder no
other task writes to) goes into the zip as `sprites.pack.001`, `.002` ...
of 1 GB each, listed in `sprites.pack.parts`. The first start joins them
(`SpritePack.joinParts`, from the launcher, before the window opens, and
the console window says so): part 1 is renamed to the pack and the others
are appended to it and deleted one by one, so it needs only a part's worth
of extra disk and carries on where it left off if it was stopped. Parts
win over a pack already there, so a newer copy unzipped over an older one
brings its own sprites.

**The jar sets itself up** (`core/Launcher`). Started from its jar - by a
launcher, `java -jar`, or a double-click, none of which can pass the JVM a
flag - the game relaunches itself once in a child JVM with what `runGpu`
gives it: a heap of 60 % of the machine, ZGC, headless AWT, on macOS
`-XstartOnFirstThread` (which a manifest cannot ask for), `larsons.gpu=required`,
4x MSAA and Linux's discrete-GPU hint. It runs in the jar's folder, so
`assets/`, `config/` and `screenshots/` are the ones beside it wherever it was
started from, and what it prints goes to `logs/latest.log` there too (the
run before: `logs/previous.log`) - the file to ask for with a bug report.
Anything on the command line wins (`java -Xmx4g -Dlarsons.sprites.scale=0.5
-jar larsons-game.jar`); `-Dlarsons.launch.relaunch=false` runs it in the
JVM it was started in. Under Gradle and IntelliJ nothing changes.

## The loading screen

The window opens at once, on a screen that shows everything the game loads as
it starts, step by step: what each step found, how long it took, and what is
loading right now.

<p align="center">
  <img src="docs/screenshots/loading-sprites.png" width="49%" alt="The loading screen indexing the sprite sheets: sprites/hair/side_part_px128, 228 of 578 folders, 637,848 sheets so far, and the folders just indexed listed on the right">
  <img src="docs/screenshots/loading-menu.png" width="49%" alt="The loading screen at its last step: every step ticked off in green with what it found and its time, the main menu's sprite sheets going up to the GPU, 11 of 14">
</p>

On the left, every step under its heading - done (green), working (gold), to
come (grey) or failed (red) - with what it found and its time:

- **Graphics**: the window and OpenGL (which GPU, which version), the fonts
  and the sprite shader.
- **Sprites**: every sprite sheet in `assets/sprites/` and each body's folder
  beside it indexed - the longest step, some 1.5 million sheets in 578
  folders, counted folder by folder -; the stand-in character drawn on the
  worker threads; the chest's and the dummies' sheets; the 128-colour
  palette, the items and their tiers; the cutscene close-ups.
- **The world**: the trees, grass and rocks found and planted, the highlands
  raised, the scenery's sheets and the ground textures put on the GPU, the
  world's shaders compiled and the hills built.
- **Sound**: the sound pack and your own sounds set up, then every sound
  effect decoded in the background, file by file.
- **Starting**: your character dressed, the main menu opened (Creative Mode's
  saved levels listed, the cutscene read), its music decoded, and the sprite
  sheets its first frame draws decoded and put on the GPU - so the menu is
  all there when the screen fades away, nothing popping in.

On the right, the step at work: what it is on (a folder, a file), how far
through it is and how long it took last time; the background work going on
beside it; and the last things loaded. The bar along the bottom is weighted
by how long each step took the last time the game started
(`config/boot.json`, safe to delete), so it moves at an even pace and never
shows 100 % while anything is left. The console gets the same in one line:
`[boot] ready in 8.37 s - Indexing the character sprite sheets 6.19 s, ...`.

If a step fails, the screen stays up with it in red and what went wrong,
until a key is pressed (the console has the stack trace). Closing the window
while it loads quits at once. **F12** takes a screenshot of it.

The sprite index also got quicker: it lists each folder's file names without
asking the disk about every file, and looks names in the usual form
(`walk_middle_ne.png`) up rather than parsing them - the same sheets, found
about six times faster (44 s to 6 s for the 1.5 million here, before the
window could even open).

## Controls

| Key | Action |
|---|---|
| **W A S D** | walk, relative to the camera |
| **Shift** / **Ctrl** (while moving) | run / sprint (crouched: Shift is the fast crouch walk) |
| **C** | crouch / stand up |
| **Space** | jump - with the sword, the axe, the bow, the crossbow, the crystal staff, the wand, the mace or the dagger |
| **Left Alt** or **Z** | dodge: a tap **dashes** (a low burst of 3.5 m); held, the dash turns into a **combat roll** (4.7 m, nothing can hurt her in it, she rolls through the dummies) - the way she is moving, or facing, with whatever is in her hands ([Dodging](#dodging)); pressed **in the air**, she lands in a roll, unhurt by the fall ([Heights](#heights)) |
| **L** / **K** | **lock on** to the enemy nearest the middle of the view - a dummy or a flier - (L again: let go) / on to the next one, left to right ([The lock-on](#the-lock-on)) |
| **deep water** | she **swims**: W A S D, Shift faster; **C** dives under, **Space** comes up - a breath bar shows under the water, and she drowns when it runs out ([Swimming](#swimming)) |
| walk into the **vines** | **climb** the vine-covered cliff: W up, S down, A / D along it; Space (or the dodge key, or C) lets go - it costs **stamina** ([Climbing](#climbing-and-stamina)) |
| walk off the **top of the vines** | **climb down**: she turns, steps down over the lip onto the vines and climbs on down while you keep walking out |
| **Space** by a table, a bench, a crate ... | **climb up onto it** - anything up to about her height; she stands on furniture up to 2 m tall ([Climbing onto furniture](#climbing-onto-furniture)) |
| **Left click** or **F** | attack - every blow rolls its [damage dice](#fighting) over her head; with the bow, hold to draw and let go to loose; with a wand or a Magic (or better) staff, cast a spell; **in the air**, a [jump attack](#jump-attacks) |
| **X** / **R** / **Q** | heavy attack (the axe, the staff, the wand, the mace or the dagger; once every 10 s) / spin attack (all but the bow and the crossbow) / parry |
| **B** (held) / **V** | block - the shield up, or the weapon held across / shield bash (or attack while blocking) |
| **1 – 5** or the **mouse wheel** | choose a hotbar slot - what is in it is in her hands: a battle axe, bow, crossbow, crystal staff, wand, mace or dagger in its stance, the sword with the sword and shield - or, with no shield worn, the sword alone; an empty slot, empty hands |
| **J** | her **cards** beside the inventory: her hand to play from and her perk tree - the game stands still while they are open ([Classes, cards and coins](#classes-cards-and-coins)) |
| **I** | the inventory - click an item to pick it up, click a slot to put it there, right-click to move it to or from the hotbar, click outside to drop it; 1 – 5 over a slot swaps it into that hotbar slot; **A–Z** / **Rarity** sort it |
| **F1 – F4** | laugh, cry, surprise, anger |
| **E** / **G** | pick up the item you are standing by (she reaches for it), open / shut the chest, look in a barrel, a crate or a storage chest ([storage](#barrels-crates-and-storage-chests)), pick a bush, go in at a door, up or down the stairs or out, **shop** at a market stall or a shop's counter ([Shops](#shops)) - whichever is nearest; by a horse, **saddle** it (with a saddle on her) or **ride** it, and on it, **get off** ([Riding](#riding)) / drop what you are holding |
| **W A S D**, **Shift**, **Ctrl** (riding) | steer the horse - it turns no faster than a horse can - at a walk, a canter or a gallop |
| **Right-drag** (or middle-drag), **arrow keys** | orbit the camera — yaw and height |
| **+ / −**, the wheel while orbiting | zoom |
| **Tab** / **O** | camera height preset: side → 45° → top-down / the [automatic camera height](#the-automatic-camera-height) on the hills, or fixed |
| **[ / ]** | loop the previous / next of all 131 states in place |
| **Backspace** | stop looping |
| **, / .** | turn the character 45° |
| **T** | turntable — step through all eight directions |
| **H** / **P** | hide the HUD / the scenery (trees, grass and rocks) |
| **N** (held) | time-lapse: the day runs sixty times as fast while it is held ([Day and night](#day-and-night)) |
| **U** | the **quest journal** ([People, foes and quests](#people-foes-and-quests)) |
| **F7** / **F8** | the developer's readout (the sheets on screen) / the [performance overlay](#performance) |
| **F9** | animations: new / classic - the first version of the weapon moves, to compare (also Pause → Settings → Animations) |
| **F11** | full screen, with the display switched to its fastest refresh rate - or back to the window ([frame rate](#120-frames-a-second)) |
| **F12** | screenshot (to `screenshots/`) |
| **Num-pad 1 0 2 5** | the debug code: every cosmetic offered in the wardrobe, and **Clear the ground** in the pause menu (every item lying about, the starting weapons and the chests' loot, taken away); typed again, off |
| **Esc** | pause menu — Wardrobe, Class, Compendium, Settings, Colours, Sounds, Controls, Import help, Main menu, Quit (or closes the inventory) |

Every key above can be changed, and a gamepad used instead: [Controllers and the Controls page](#controllers-and-the-controls-page).

Drop sprite sheets on the window at any time to import them.

## What's in the demo

- **A meadow** — an endless grass floor, one 512-pixel pixel-art tile
  repeating out to a soft sky, drawn in a single full-screen GPU pass that
  ray-casts the ground plane per pixel, so it has no edge and a clean horizon
  at any zoom; oaks, grass tufts and rocks stand on it (see below).
- **The character**, standing in the middle as a stack of sprite layers, in
  **131 animation states**, each from **8 directions × 3 heights**: idle, walk,
  run, sprint, jump and attack; crouching (an idle, a walk and a fast walk);
  the dash and the combat roll (played with whatever she holds); the bow,
  crossbow, staff and wand **aimed up** at what flies; climbing;
  reaching down to pick something up, standing or crouched; four emotes -
  laughing, crying, surprise and anger; riding a horse, at a walk and at a
  gallop; the sword's spin attack and parry and
  the shield held ready and bashed; the sword held alone, with no shield (11
  more: `blade_*`); and the weapons' stances below. The
  HUD names the exact sheet each layer is drawing (and, where a set has no
  sheets for a state, the state it is shown as instead), and a gauge at the
  top right shows the camera's angle against the three height zones.
- **A sword, a battle axe, a longbow, a crossbow, a crystal staff, a wand, a mace and a dagger** hovering over their
  shadows nearby. Walk up and press **E**: she reaches out for it (crouched,
  if she is) and it is hers as her hand closes on it - in the first free
  slot of her [hotbar](#the-inventory-and-the-hotbar), which is selected,
  so it is in her hands. The sword goes into her
  weapon hand - its own sprite layer, playing frame for frame with the body.
  The others are each a **stance** of its own, with its own idle, walk, run,
  sprint, jump, crouch and crouch walk: the **battle axe** in both hands (an attack,
  a heavy overhead blow, a spin attack, a crouched sweep, a parry, a block),
  the **bow** (drawn while the attack is held, loosed when it is let go, crouched
  too; a parry and a block), the **crossbow** (a shot, crouched too; a parry and
  a block), the **crystal staff** (carried in the right hand and used as a
  walking stick on the walk; both hands on it to fight: a cast - the crystal
  thrust out at the enemy -, a heavy slam into the ground, a spin attack, a
  crouched sweep, a parry and a block), and the three one-handed weapons, each
  in the right hand: the **wand** (30 cm of carved wood: a cast - flicked out
  at the enemy -, a great two-handed cast, a casting turn, a flick from the
  crouch, a ward thrown up to parry and a ward held to block), the **mace** (a
  flanged iron head on a 63 cm haft: an overhand smash, a two-handed smash
  into the ground, a spin attack, a low sweep, the haft thrown up overhead to
  parry and held across in both hands to block) and the **dagger** (40 cm, the
  left hand up as a guard: a thrust, a slash and a lunge, a spinning slash, a
  rising stab from the crouch, a parry and a guard). **1 – 5** or the mouse wheel choose between the hotbar's slots,
  **G** puts down what she holds. A
  left-handed character (Pause → Wardrobe → Hand) takes everything in the other
  hand and fights left-handed.
- **An inventory** (**I**) of diamond-shaped slots on a background of your
  own, with a **hotbar** of five along the bottom of the screen - see
  [The inventory and the hotbar](#the-inventory-and-the-hotbar).

  <img src="docs/screenshots/inventory.png" width="49%" alt="The inventory open: twenty diamond slots in an interlocking lattice, the battle axe in one with its name, and the five hotbar slots along the bottom, the crossbow's selected in gold">
  <img src="docs/screenshots/hotbar.png" width="49%" alt="The hotbar along the bottom of the screen with the axe, bow, crossbow and sword in it, the crossbow selected and in her hands">
- **An ornate treasure chest** on the ground, drawn like the character from
  all 8 directions and 3 heights in 128-pixel pixel art, magical smoke
  swirling round it. **E** throws its lid open: it lights up from inside,
  light welling out of it and its smoke glowing, and **E** again slams it
  shut. Every one of its animations has a sound hook - see
  [The treasure chest](#the-treasure-chest). It is a **loot chest**: the
  first time it opens it throws out 3 to 7 items, each in a beam of light the
  colour of its rarity - see [Items, rarity and loot](#items-rarity-and-loot).

  <img src="docs/screenshots/chest-open.png" width="98%" alt="The treasure chest shut, purple smoke swirling round it, and open, lit gold from inside with light rising out of it, the smoke glowing, beside the character">
- **The highlands** north of the meadow - a ramp up to a plateau 6 m high
  with a sheer **ledge** to jump off and a **vine-covered cliff** to climb, a
  hill and a hollow - with **fall damage**, a **roll out of a fall**,
  **stamina** and **health**, four more dummies at the feet of the ledge and
  the cliff to shoot down at from the **high ground**, and the camera rising on the peaks and
  sinking in the hollows - see [Heights](#heights).
- **Two flying strike dummies** circling over the meadow and the hollow,
  diving at her when she comes near - roll through them, block, parry or
  shoot them down, aimed up - and a **lock-on** (**L**) for every enemy; see
  [Heights](#heights).
- **Three training dummies** to fight - straw-stuffed burlap on a stake, a
  target painted on the chest. Every swing rolls **D20s over her head** in
  the colour of the weapon's rarity, and their total is the damage; the bow
  and crossbow loose arrows and bolts, wands and staffs cast spells, and her
  **mana** is beside the hotbar - see [Fighting](#fighting).

  <img src="docs/screenshots/combat-dice.png" width="49%" alt="Four purple Epic dice over her head after an axe blow, their total 33 and 4d20 over them and Venom's note above, the dummy she struck showing 33 and its health bar">
  <img src="docs/screenshots/combat-spell.png" width="49%" alt="A Magic Staff's blue spell bursting on a training dummy, three blue Rare dice over her head totalling 25">
- **Sound for everything that happens** - her footsteps on grass, dirt,
  gravel and the forest floor, every jump, landing, dash, roll and climb,
  each weapon's swing, shot and spell, the dice, the blows, the dummies and
  the flying dummies, the chests and the loot ringing out by rarity, the
  menus, and the breeze over it all: 125 original placeholder sounds, made
  in code, played through the engine's MP3 decoder and mixer. **Pause →
  Sounds** has a tab for each of 9 categories with its own volume, and every
  sound in it can be played, swapped for any MP3 on your computer, muted or
  reset - see [Sound](#sound).
- **Music on the main menu** - "Dream On" as a medieval tavern piece, for
  lute, recorder, harp, fiddle, a portative organ's drone and a hand drum:
  a two-minute seamless loop that fades in as the menu opens and out as it
  is left. Its MIDI is in [`music/`](music/), ready for FL Studio - see
  [Music](#music).

  <img src="docs/screenshots/new-states.png" width="98%" alt="Twelve of the new states in the demo, 128-pixel pixel art: the crouch walk, a pick-up, laughing, crying, surprise, the sword's spin attack, the shield up, the battle axe at the ready, its heavy attack and its spin, the bow at full draw and the crossbow aimed">
- **Scenery** — oak trees swaying and tufts of grass blowing in the breeze,
  and mossy rocks, all pixel art rendered in Blender and drawn like
  Minecraft's plants: the same sprite on two cards crossed at right angles
  (the rocks on one card facing the camera). The character walks behind a
  tree and in front of it, through the grass, and not through a trunk or a
  rock - see [The scenery](#the-scenery).

  <img src="docs/screenshots/meadow.png" width="98%" alt="The meadow from 45 degrees: pixel-art oaks round a clearing, grass tufts scattered across a grass floor, three rocks, and the character by the chest and the weapons">
- **A pause menu** with the **Wardrobe**: an oak armoire with its doors open
  (`ui/Medieval`: carved wood, iron brackets, parchment labels, a gilded
  mirror), its shelves every cosmetic layer, each cycling through what she
  owns to wear in it (see [Wearing what you own](#wearing-what-you-own)) and,
  for the pixel-art items, a shelf of colour gems - each unlocked once a dye
  of it is used on that item (the body's colour is the skin tone of every
  layer) - plus the drawing style and the hand, with a live preview in the
  mirror you can turn through the eight directions, tip through the three
  heights and play in every state. The hands are the hotbar's, not the
  wardrobe's.
- **A cutscene** behind the main menu: **your own character** - exactly as
  the wardrobe has her - talking with Bryn, close up, so their faces show:
  every character a stack of close-up layers, one per item she wears, each
  in any colour, with lines that type themselves out, colours that fade
  mid-sentence and warm and cold light. **Cutscene** in the menu plays it
  full screen. See [Cutscenes](#cutscenes).

  <img src="docs/screenshots/main-menu.png" width="49%" alt="The main menu: the player's own character - long waves, straw sun hat, leather gloves - explaining to Bryn behind it">
  <img src="docs/screenshots/cutscene.png" width="49%" alt="The cutscene full screen: the player's hair fading to pink mid-line while Bryn talks, both close up in 128-pixel pixel art">
- **Drag-and-drop importing** of sprite sheets into the repository.
- **A rendered character** in `assets/sprites/`: the rigged feminine model from
  [3D-Modeling](https://github.com/Larleeloo/3D-Modeling) with 52 items for the
  24 layers — eleven haircuts, three shirts (flannel, a laced linen shirt, a
  laced dress), jeans, briefs, bralette, boots, gloves, a belt, a necklace,
  bangles on either wrist, scabbards on either hip, a sun hat and a beanie, a
  long mantle (a cape to the ankles), a sword and a shield for either hand,
  and the face's ears, noses, eyes, eyebrows, makeup and mouths — each in all
  80 states from all 24 views (a carried item in the states of its stance),
  the clothes and mantle cloth-simulated per animation; and the battle axe,
  the longbow and the crossbow in theirs. They are **pixel art**,
  in 128- and 64-pixel frames in one 128-colour palette, right- and
  left-handed, and every one can be recoloured: fourteen hair colours (royal
  blue among them) for the hair, the clothes, the eyes, lips, brows and makeup
  and the skin, gold or silver for the jewellery. The first outfit's 26 items
  are also 512-pixel renders; Pause → Wardrobe → Style switches the whole
  character between the renders and the two pixel sizes. Put them on in the
  Wardrobe (or see [the list](assets/sprites/README.md#the-sheets-in-this-folder)
  for a ready-made `config/wardrobe.json`).

  <img src="docs/screenshots/rendered-walk.png" width="49%" alt="The rendered character in the full outfit - all 18 layers - walking in the demo">
  <img src="docs/screenshots/rendered-middle.png" width="49%" alt="The same outfit from the 45-degree sprites: sun hat, shield and sword">
  <img src="docs/screenshots/pixel-128-walk.png" width="49%" alt="The same outfit as 128-pixel pixel art, walking">
  <img src="docs/screenshots/pixel-64-walk.png" width="49%" alt="The same outfit as 64-pixel pixel art, walking">
  <img src="docs/screenshots/pixel-128-beanie.png" width="49%" alt="Pixel art: long royal-blue hair under a platinum beanie, the laced shirt, belt, scabbard and a silver necklace">
  <img src="docs/screenshots/pixel-128-dress.png" width="49%" alt="Pixel art: the laced dress, a short side-parted cut in copper and a honey-blonde skin tone">
  <img src="docs/screenshots/pixel-128-left-handed.png" width="49%" alt="A left-handed character: the sword in the left hand, the shield on the right arm, attacking">
- **A second body**, the masculine model (1.80 m) from 3D-Modeling's
  `generic_masculine_model/`, in `assets/sprites_masculine/`: every one of
  the 52 items - the laced dress, the bralette, the makeup and the long hair
  styles too - and the three weapons drawn on him as they are on her: the
  same states (a carried item in its stance's) from the same 24 views, in
  128- and 64-pixel frames, right- and left-handed, in the same palette and
  colours, with his own cutscene close-ups. Pause → Wardrobe →
  Base body switches body, and the outfit carries over: each item is drawn
  for the body that wears it.

  <img src="docs/screenshots/masculine-pixel-128-walk.png" width="49%" alt="The masculine body in the full outfit - long waves, sun hat, mantle, sword and shield - walking, as 128-pixel pixel art">
  <img src="docs/screenshots/masculine-pixel-128-dress.png" width="49%" alt="The masculine body in the laced dress, recoloured royal blue, with a black bob, plum makeup and gold hoops">
  <img src="docs/screenshots/masculine-pixel-128-axe.png" width="49%" alt="The masculine body swinging the battle axe in a laced shirt and a crew cut">
  <img src="docs/screenshots/masculine-cutscene.png" width="49%" alt="The cutscene with the masculine body close up, his long waves fading to royal blue mid-line, Bryn beside him">
- **A fallback character** when no Blender renders are present: a 32 × 32 pixel
  figure generated from a tiny rigged box model and rendered from the same 24
  camera positions the real sheets use, scaled up with crisp pixels.

<p align="center">
  <img src="docs/screenshots/fallback-walk-24-views.png" width="90%" alt="The fallback walk cycle from all 8 directions and 3 heights">
  <br><sub>One frame of the fallback walk from all 24 views — 8 directions × side, middle and top.
  The sword is its own layer, cut away wherever the hand or body is in front of it.</sub>
</p>

<p align="center">
  <img src="docs/screenshots/wardrobe.png" width="49%" alt="The wardrobe: an oak armoire with its doors open, shelves of cosmetic layers, a row of colour gems (the locked ones padlocked) asking to use up a cherry red dye, style and hand, and the character in a gilded mirror">
  <img src="docs/screenshots/import.png" width="49%" alt="The drag-and-drop importer reviewing 144 sheets">
  <br><sub>Pause → Wardrobe, and the importer after a folder of sheets was dropped on the window.
  (Captured headlessly by a scripted run on a software renderer — hence the red GPU line.)</sub>
</p>

## The sprite stack

The full contract — folder layout, naming, the Blender camera, `profile.json`,
the layer order and the holdout rule — is in
[`assets/sprites/README.md`](assets/sprites/README.md). In short:

```
assets/sprites/<layer>/<item>/<state>_<elevation>_<direction>.png
assets/sprites/body/hero/walk_middle_ne.png
assets/sprites/carry_right/sword/attack_top_s.png
assets/sprites/items/sword/icon.png                 (the pickup, lying in the world)
```

- **Bodies.** A body can have its own sprites folder beside `assets/sprites/`,
  named after it (`assets/sprites_masculine/`), holding every item drawn on
  that body under the same names; the body a character wears picks the folder
  its items come from, so one outfit (`hair: bob`) fits either body.
- **Views.** The camera's angle above the character picks the set: **side**
  (rendered at 0°) from 0° to 33°, **middle** (45°) from 34° to 75°, **top**
  (90°, birds-eye) from 75° to 90°. The direction is worked out per character
  from where the camera is, so a character across the room is seen from its own
  angle.
- **Frames.** 512 × 512, 30 fps, any count per state, left to right then top to
  bottom.
- **Layers** draw in a fixed order — body, underwear, bra, shoes, pants, shirt,
  belt, necklace, gloves, right and left wrist, sheath, ears, earrings, nose,
  eyes, eyebrows, makeup, mouth, hair, hat, other, left hand, right hand (the
  hands swap for a left-handed character; the cape draws first when she faces
  the camera and just under the hair otherwise) — every one on the body's frame
  index. Each is rendered in
  Blender with the body as a **holdout**, so the sword is already cut away where
  the hand grips it and the game only has to draw layers on top of each other.
- **Placement.** Each frame is a camera-facing card placed so the feet in the
  picture land on the character's spot on the ground — the feet stay on the
  shadow from every angle and every zoom.
- **Colours.** An item with a `variants.json` can be drawn in any of the
  colours it lists - or any colour at all, `#rrggbb`, shaded like the item's
  own. Its sheets are palette PNGs, uploaded once as their palette indices
  (a byte a texel); the sprite shader colours each layer from a palette of
  its own as it draws it (`gfx/PaletteAtlas`), so every colour of a sheet is
  one texture, two characters can wear it in different colours in the same
  draw call, and a colour can change every frame.
- **Stances.** Every state has a stance - what is in her hands while it plays
  (`sprite/Stance`): the wardrobe's own carried items (the sword and shield -
  or, with no shield in either hand, the sword alone: its own `blade_*`
  states, the free arm down instead of holding a shield up), the battle axe,
  the bow, the crossbow, the crystal staff or a one-handed weapon - the wand, the mace or the dagger -, or nothing at all (the emotes and the pick-ups). The hands draw the stance's: a weapon of a stance is drawn from
  `carry_right/battle_axe`, `carry_left/longbow`, `carry_right/crossbow`, `carry_right/crystal_staff`, `carry_right/wand`, `carry_right/mace` or `carry_right/dagger` (the
  twin hand, mirrored, for a left-handed character) whatever the wardrobe holds,
  and the other hand is empty.
- **Fallbacks.** No body sheet for a state from a view → the first state along
  its fallbacks that has one (a crouch walk is shown as the walk, the axe's
  block as the axe's idle and then the idle; every chain ends in one of the
  first six), and every layer plays that state with it - so the 512-pixel
  renders, made before the new states, still play them all. No body sheet at
  all → the generated 32-pixel body for that view. No cosmetic sheet → that
  layer is left blank. A missing west-facing view borrows the east-facing one
  mirrored.
- **Loading.** Sheets load on demand, decode on worker threads, are cropped to
  the part of the frame they use and live in a video-memory budget with
  least-recently-used eviction; what may be needed next (the other states of
  the same stance, the neighbouring directions) is loaded ahead only into room
  the budget has spare.
  A layer change is only shown once every layer of it is ready, so a sword is
  never a frame out of step with the arm holding it.

### Importing sprite sheets

Drag sheets, a folder of them, or Blender's loose numbered frames from your file
manager onto the game window. The importer:

1. reads every name (forgivingly — `Walk-45-NorthEast.png` is fine) and every
   PNG header, and shows what it found per state, how many of the 24 views, and
   how many frames;
2. guesses the layer and item name from the folder you dropped — dropping
   `hat/red_cap/` means layer `hat`, item `red_cap` — and lets you change both;
3. stitches numbered frames into sheets, copies finished sheets as they are, and
   writes them to `assets/sprites/<layer>/<item>/` under canonical names —
   **inside the repository**, ready to commit;
4. reloads the library and, if *Wear it after importing* is ticked, puts the new
   item on the character.

## The inventory and the hotbar

The player carries an **inventory** (`world/Inventory`) of any number of
slots - 25 by default, `-Dlarsons.inventory.slots=N` for another number (a
script's `slots <n>` changes it while the game runs; anything that no longer
fits is put on the ground). The first five slots are the **hotbar**:

- **1 – 5** or the **mouse wheel** choose a hotbar slot, and what is in it is
  what she holds - and nothing else is. The battle axe, the bow, the crossbow, the crystal staff, the wand, the mace and the dagger are each taken up
  in their own stance; the sword - worn in the wardrobe's weapon hand - is the
  sword-and-shield stance, the wardrobe's own carried items (the sword alone
  when no shield is worn: no shield to block or bash with). An **empty slot**,
  or one holding something worn, is **empty hands**: nothing is drawn in
  either hand (the sword alone's poses, the free arm hanging) and a swing is a
  bare-handed blow, one Common die. The sword is in her hand only while its
  slot is selected - moved out of the hotbar or dropped, it is put away.
  (The emotes, which were on 5 – 8, are on **F1 – F4**; stopping a preview,
  which was 0, is **Backspace**; the wheel zooms while orbiting, and **+ / −**
  always do.)
- Picking an item up puts it in the first free slot, the hotbar first; when
  that is a hotbar slot it is selected, so it is in her hands. A wearable is
  never in her hands: it goes into the rest of the inventory first (the hotbar
  only when that is full), changes neither what she holds nor her stance, and
  is hers to wear from the wardrobe. With every slot full, nothing is picked up.
- **G** drops what is in the selected slot.
- **Whatever leaves the inventory is unequipped**: a weapon is out of her
  hands, a wearable comes off (unless she owns another like it). What she
  wears when the demo starts - the wardrobe is saved, the inventory is not - is
  put in her inventory as hers, so dropping it takes it off too.

**I** opens the inventory over the middle of the screen: every slot a
**diamond** - a square stood on its corner - with a **white border**, the rest
of the inventory in an interlocking lattice that fits however many slots
there are (`InventoryPanel.lattice`), the hotbar's five in a row along the
bottom, the selected one in gold. Click an item to pick it up, click a slot to
put it there (swapping with what was in it), click outside the panel to put it
on the ground; right-click (or Shift-click) moves an item between the hotbar
and the rest; 1 – 5 over a slot swaps it into that hotbar slot. **A–Z** and
**Rarity**, either side of the title, sort the rest of the inventory - by name,
or the rarest first (Mythic down to Common, by name within a rarity) - packed
into its first slots; the hotbar stays as it is. She stands still while it is
open; **I** or **Esc** closes it.

**The background** is `assets/ui/inventory_background.png`, a **432 × 768**
picture, scaled to the height of the window (never above its own size) and
kept in its shape; until there is one, a plain dark panel is drawn. The one
that comes with the game is medieval, drawn in the 128-colour palette by
`tools/ui/make_inventory_background.py`: a frame of oak planks bound in
iron, the title on a carved plaque with a brass rim, the slots on a stitched
leather panel, the hotbar on a studded belt. It is read
again whenever the file changes, so a new background shows the next time the
inventory opens. Where things go on it, as fractions of the picture: the title
in the top 10 %, the lattice in the box 7 – 93 % across and 11 – 74 % down
(from its top), the hotbar's row 78 – 96 % down.

## Wearing what you own

What she wears is **items** (`world/Wearables`): clothes, armour, jewellery,
dyes, makeup, lipstick, hair styles and eyebrows are picked up or found in
chests like the weapons, carried in the inventory, and only then offered by
Pause → Wardrobe - each by its name ("Silver wrist band"), a wrist band on
either wrist, a sheath on either hip. The **underwear**, the **bra** and a
starter look - two hair styles, the **long waves** and the short **side
part**, and the **arched eyebrows** - are hers from the start
(`Wearables.OWNED_BY_DEFAULT`); everything else has to be found. **Free**,
not items, as before: the body and the skin tone, the ears, the eyes and their
colour, the nose and the mouth's shape - every folder found for the body. The
hands hold what the hotbar selects; the wardrobe leaves them alone.

| Rarity | Wearables |
|---|---|
| Common | underwear, bra (both owned by default), shoes, pants, baggy shirt, straw sun hat, beanie |
| Uncommon | belt, gloves, sheath, laced shirt, wool mantle, chainmail shirt, chainmail pants |
| Rare | laced dress, silver wrist band, silver necklace, silver hoops, the 14 dyes, makeup (noir, rose, plum), lipstick, the 11 hair styles, eyebrows (arched, soft, thin) - the long waves, the side part and the arched eyebrows owned by default - iron chestplate, iron leg plating, iron gauntlets, iron helmet |
| Epic | gold wrist band, gold necklace, gold hoops, steel chestplate, steel leg plating, steel gauntlets, steel helmet |

The first four rows are Larson's list; the rest are picks he did not make
himself: the chainmail Uncommon, the iron armour Rare and the steel Epic; the
straw sun hat and the beanie Common, the wool mantle Uncommon, the silver
hoops Rare and the gold hoops Epic. The **dyes** are the 14 colours every
item's `variants.json` shares - brown, black, auburn, copper, honey blonde,
platinum, cherry red, pink, electric blue, violet ombre, emerald, mint, green
ombre, royal blue - and the hair styles every folder under `hair/` (long waves,
bob, ponytail, pixie, braids, curls, crew cut, side part, undercut, beanie
long, beanie short).

- **Dyes unlock colours for good.** A dye (`dye_<colour>`) is a universal
  unlock for anything that takes a colour, from the makeup to the clothes:
  the colour shelf shows every colour of what is worn, the locked ones
  padlocked; with a dye of a locked colour in her bag, clicking it asks
  "Use up a red dye to make the bob hairstyle red for good?" and one dye is
  used up to unlock that colour on that item only (`Wearables.useDye`,
  saved in `wardrobe.json` under `unlocked`). An unlocked colour can be put
  on and taken off freely afterwards. The **lips** take every colour once she
  has the **lipstick**; a **metal** wearable only its own - the silver and
  the gold wrist band are one `gold_bangle`, worn in its `silver` or `gold`
  option. The skin tone and the eyes' colour are free.
- **Outfits saved before** keep what they wear, owned or not - it is listed
  in its layer, and so is a colour already on. Once taken off, it is offered
  again only if she owns it.
- **Losing an item** - dropping it, or it spilling out of a smaller inventory -
  takes it off when she has no other that gives the same; losing the lipstick
  takes the lips' colour off unless a dye unlocked it. What is owned by
  default, the free layers and the colours dyes unlocked never come off.
- **Chests** drop the wearables and the armour at their rarities, but never
  what is owned by default. So that the dyes and the hair styles do not drown
  out the weapons, each **family** - the dyes, the hair styles, the eyebrows,
  the makeup - is one drop of its rarity, and when it comes up, any one of it.
- Scripts give them like the weapons: `give silver_necklace`, `give dye_pink`,
  `give hair_long_waves`, `give steel_helmet`.

The armour is drawn on both bodies in every state (`chainmail/`,
`chainmail_legs/`, `chestplate/`, `leg_plating/`, `gloves/iron_gauntlets` ...
- see [`assets/sprites/README.md`](assets/sprites/README.md)) and hides what it
covers: the mail shirt the shirt and the bra, the mail leggings the underwear
and the pants (`Wardrobe.hidden`); a dress under the mail shirt keeps its
skirt and loses only what would poke out above the mail's hem
(`Wardrobe.cutBy`, `SheetImage.cutUnder`).

Lying on the ground and in the inventory, a wearable is one of three turning
128-pixel pickups (`Wearables.pickup`, `items/wearable_hanger/`,
`items/armour_shield/`, `items/dye_bottle/`, drawn by 3D-Modeling's
`items/item_spin_sprites.py`): clothes, jewellery, hair styles, makeup and
the lipstick a **t-shirt on a coat hanger** in their rarity's colour, armour
a **shield** in it, and a dye a **bottle** of its colour (Rare blue glow).
The old `items/<id>/icon.png` renders are only a fallback now.

## The treasure chest

An **ornate treasure chest** stands a few metres behind her at the start:
dark planks under gold straps, corner posts and a gold rim, an amethyst on
each front post, a ruby in the lock plate, a sapphire in a medallion on the
lid, a row of glowing runes - and **magical smoke** winding round it, three
twisting wisps of violet with sparkles in them. It is a 2D sprite like the
character, pre-rendered from the same **8 directions × 3 heights** as **128 ×
128 pixel art** at the character's scale, and drawn as a stack of three layers
(`sprite/ObjectSprites`, `sprite/ObjectStack`):

```
assets/sprites/objects/ornate_chest/profile.json                frame 2.4 m, aimed 0.6 m up, 24 fps
assets/sprites/objects/ornate_chest/smoke_back/swirl_<elev>_<dir>.png    48 frames, loops
assets/sprites/objects/ornate_chest/chest/idle_<elev>_<dir>.png          48 frames, loops
assets/sprites/objects/ornate_chest/chest/open_<elev>_<dir>.png          20 frames, once
assets/sprites/objects/ornate_chest/chest/opened_<elev>_<dir>.png        48 frames, loops
assets/sprites/objects/ornate_chest/chest/close_<elev>_<dir>.png         16 frames, once
assets/sprites/objects/ornate_chest/smoke_front/swirl_<elev>_<dir>.png   48 frames, loops
```

- **Idle**: shut, the runes pulsing, a glint running over its gems.
- **Open** (**E** beside it): the lid rattles, is thrown back past open and
  settles, and the chest **lights up from inside** - the velvet lining and the
  heap of gold coins lit warm, light welling up out of it in shafts, a pool of
  light on the ground round it - and holds **open** (lit, the light
  flickering, sparkles over the coins). **E** again **closes** it: the lid
  slams, the light goes.
- **The smoke** is its own two layers, the part behind the chest's middle
  (drawn under it) and the part in front (over it), playing one loop on the
  world's clock whatever the chest does, so it never jumps when the lid moves;
  as the light comes up it is tinted warm, on the GPU, by a palette swap.
- **Sound hooks** on all four: `idle` and `opened` loop while they hold, `open`
  and `close` play once as they start - quieter the farther you are, panned to
  the chest's side of the screen (`chests/ornate_chest/<state>.mp3`, see
  [Sound](#sound)).
- She cannot walk into it (it pushes her back out), and the prompt says
  whether E opens or shuts it, or picks up an item nearer to her.

It is a **loot chest**: `world/Chest` is a small state machine (`IDLE →
OPENING → OPEN → CLOSING`), and the first time it opens - and again once
everything it gave has been picked up - the world throws its loot out of it
(see [Items, rarity and loot](#items-rarity-and-loot)). The sheets are drawn by
[3D-Modeling](https://github.com/Larleeloo/3D-Modeling)'s
`items/ornate_treasure_chest/ornate_chest_sprites.py`, which models the chest
in Blender and draws the pixel art; any object laid out like it under
`assets/sprites/objects/` can be drawn the same way (see
[`assets/sprites/README.md`](assets/sprites/README.md#objects)).

### Barrels, crates and storage chests

A **barrel** (9 slots), a **crate** (12) and a **storage chest** (20: a chest
set not to throw out loot) hold things (`world/Store`). Walk up to one and
**E** (*Look in the barrel*): it opens beside the inventory, the two side by
side. Shift-click or right-click moves a whole stack across; **Take all** and
**Put all in** move everything; **E**, **I** or **Esc** closes it (what is on
the pointer goes back where it came from). A storage chest's lid opens as she
looks in, with none of a loot chest's smoke.

<p align="center">
  <img src="docs/screenshots/storage-open.png" width="49%" alt="A barrel opened beside the inventory: its nine slots, Take all and Put all in">
  <img src="docs/screenshots/storage-break.png" width="49%" alt="A barrel broken open, what it held spilled round where it stood">
</p>

A **pot** (4 slots) and a **large pot** (6) hold things too.

**Barrels, crates, pots and storage chests break.** Blows and shots land on
them (they are never locked on to, and never count for Bonded); broken, they
**shatter into pieces** (`world/Shards`) - splinters, staves and shards in
their own colours thrown up and out, tumbling, bouncing and skidding to a
stop, lying a moment and fading - with a sound of their own, and spill what
they held round where they stood, clear of the walls. A pot breaks at a
touch, a barrel takes 30, a crate 22, a storage chest 40. A loot chest does
not break.

<p align="center">
  <img src="docs/screenshots/pot-shatters.png" width="49%" alt="A pot by the market stall shattering: its shards thrown up and out">
  <img src="docs/screenshots/barrel-shatters.png" width="49%" alt="A barrel breaking into splinters and staves">
</p>

In Creative Mode a barrel, a crate, a pot, a large pot (Place → Clutter), a **Storage chest**
(Place → Inside) or a **weapon rack** (Place → Decor: what it holds stands on its pegs) has **Holds**: a list of items, each with how many -
`wild berries x6, sage, longbow perfect, forged:duskfang` - names as the
scripts write them. A treasure chest whose **Throws out loot** is off is a
storage chest too. Loot thrown out of a chest never lands in or through a
wall: it falls short of it.

## Items, rarity and loot

<p align="center">
  <img src="docs/screenshots/loot-beams.png" width="98%" alt="The chest open and seven weapons lying round it, each hovering and turning in a beam of light the colour of its rarity - white, green and blue - with sparks rising in the beams and pools of light on the ground">
</p>

Every item is an **Item** (`world/Item`): what kind of thing it is
(`ItemDef`), its **tier** where it is a weapon of Larson's chart, its
**rarity**, and the **rare border** it may have dropped with. Its name says
all of it - "Venom Crystal Axe" - and so does a script's word for it:
`battle_axe:crystal:venom`.

| Rarity | Colour | Drops |  | Tier | Rarity | Looks |
|---|---|---|---|---|---|---|
| Common | white | 45 % |  | Basic | Common | as rendered |
| Uncommon | green | 35 % |  | Sharper | Uncommon | a black handle |
| Rare | blue | 15 % |  | Magic | Rare | as rendered (the crystal staff is the Magic Staff); blue projectiles |
| Epic | purple | 3.9 % |  | Crafted | Rare | a black body |
| Legendary | orange | 1 % |  | Honed | Epic | darker wood; yellow projectiles |
| Mythic | cyan (the palette's sea teal) | 0.1 % |  | Crystal | Epic | a pink-purple blade or tip |
|  |  |  |  | Perfect | Legendary | gold |
|  |  |  |  | Mythic | Mythic | smoky purple |

Which weapon comes in which tiers is the chart (`world/Tier.CHART`): the axe,
the sword, the mace and the dagger are Basic, Sharper, Crystal, Perfect or
Mythic; the staff Basic, Magic, Honed, Perfect or Mythic; the longbow and the
crossbow Basic, Crafted, Perfect or Mythic; the wand Magic, Honed, Perfect or
Mythic. Anything without tiers (a wearable) carries its own rarity. The
rarities' and the borders' colours are all entries of the art's 128-colour
palette.

**Rare borders** (`world/Border`), one chance in a hundred each, never two:
**Venom** (lime) - half as much damage again as damage over time for 10
seconds after the first hit; **Mystic** (smoky purple) - +150 % damage for
twice the mana; **Bonded** (pink) - +10 % damage for every living thing hit
with it in the last 5 minutes. The numbers are in `Border` for the fight to
use.

**The loot chest** (`world/Loot`): 3 to 7 items, each one's rarity drawn by
the drop rates, then one of that rarity's drops (any tier of any weapon in
the chart, and anything else added to the table), then perhaps a border; at
least one of them is Rare or better (one is drawn again if none is). They pop
out of the lid one after another, fly in an arc and land round its front.

**In the world** (`world/ItemRenderer`) an item hovers over its shadow and
turns in place - **128 × 128 pixel art**, 32 frames a turn, from the height
the camera sees it from (`assets/sprites/items/<id>/spin_px128/`, drawn by
3D-Modeling's `items/item_spin_sprites.py`) - in its tier's colours, with a
**halo** of its rarity's colour round it and a pool of it on the ground.
Chest loot stands in a **Borderlands beam** of its rarity's colour once it
lands - round the item and 3.6 m up into the air, pulsing, sparks rising in
it. A rare border is a crisp outline in its colour, pulsing, with what comes
off it: Venom's lime drips, Mystic's smoky puffs, Bonded's pink hearts.

<p align="center">
  <img src="docs/screenshots/rare-borders.png" width="98%" alt="A Venom Crystal Sword with a lime outline and drips, a Mystic Perfect Axe in gold with smoke round it, and a Bonded Magic Staff with a pink outline and hearts floating up from it, each hovering in its rarity's halo">
</p>

**Tiers are recoloured in the engine**, never drawn again: a weapon's sheets
name which of their materials is which part (handle, blade, body, wood,
tip, fittings, string - `parts.json` beside the turning sprites, and the
`carried` map in `assets/sprites/tiers.json` for the sheets in her hands),
and `sprite/TierLook` makes each tier's look (`tiers.json`) on those parts as
a palette swap, snapped back to the 128 colours (`sprite/PixelPalette`,
`assets/sprites/pixel_palette.json`). So the weapon on the ground, its icon
and the one in her hands are the same Sharper Axe, Crystal Sword or Perfect
Bow.

<p align="center">
  <img src="docs/screenshots/axe-tiers-in-hand.png" width="98%" alt="The battle axe in her hand five times: Basic as rendered, Sharper with a black handle, Perfect all gold, Crystal with a pink-purple blade, Mythic smoky purple">
</p>

**In the inventory** each slot is tinted and edged in its item's rarity, the
item drawn from its turning sprite lying corner to corner (turning under the
pointer, or when selected) in its tier's colours, glowing in its rarity's
colour and ringed in its border's; the tooltip gives its name in the rarity's
colour, its rarity and tier and its border's effect. The pick-up prompt and
the hotbar's name are in the rarity's colour too.

<p align="center">
  <img src="docs/screenshots/inventory-rarity.png" width="60%" alt="Inventory slots tinted white, green, blue, purple, orange and teal by their items' rarities, each item glowing in its colour; a Venom bow ringed in lime">
</p>

```
assets/sprites/tiers.json                        each tier's look by part, and which material of the in-hand sheets is which part
assets/sprites/pixel_palette.json                the art's 128 colours (3D-Modeling's generic_feminine_model/pixel_palette.json)
assets/sprites/items/<id>/spin_px128/            profile.json, spin_side/middle/top.png (32 frames), variants.json, parts.json
```

## Fighting

**Three training dummies** stand round her at the start
(`combat/Dummy`): a straw-stuffed burlap figure on a stake with a target
painted on its chest, a 2D sprite like the chest - 8 directions × 3 heights
of 128 × 128 pixel art - drawn by
[3D-Modeling](https://github.com/Larleeloo/3D-Modeling)'s
`items/attack_dummy/attack_dummy_sprites.py`:

```
assets/sprites/objects/attack_dummy/dummy/idle_<elev>_<dir>.png          48 frames, loops: a slow sway
assets/sprites/objects/attack_dummy/dummy/hit_<side>_<elev>_<dir>.png    16 frames: rocks away from the blow, wobbles back
assets/sprites/objects/attack_dummy/dummy/fall_<side>_<elev>_<dir>.png   12 frames: tips over away from the blow
assets/sprites/objects/attack_dummy/dummy/down_<side>_<elev>_<dir>.png    1 frame: lying there
assets/sprites/objects/attack_dummy/dummy/rise_<side>_<elev>_<dir>.png   18 frames: springs back up
```

Every reaction is drawn four ways - struck on its **front**, **back**,
**left** or **right** - and it plays the one for the side the blow came from,
flashing white as it is struck. It takes **100** before it falls, lies for
**6 s** and springs back up whole; lying down, nothing touches it. A health
bar shows over it once it is hurt. She cannot walk through it (she can [roll](#dodging) through it).

### The damage dice

**Every swing rolls D20s above her head**, in the **colour of the weapon's
rarity** (a rare border's colour as the die's rim), tumbling as she swings
and coming up their numbers one after another as the blow lands; **their
total is the damage**, which floats up off whatever was struck in the same
colour. The number of dice is the rarity's:

| Rarity | Dice | Damage |
|---|---|---|
| Common | 1d20 | 1 – 20 |
| Uncommon | 2d20 | 2 – 40 |
| Rare | 3d20 | 3 – 60 |
| Epic | 4d20 | 4 – 80 |
| Legendary | 5d20 | 5 – 100 |
| Mythic | 6d20 | 6 – 120 |

A **heavy** attack (the axe's, the staff's, the mace's and the dagger's) rolls
**one die more**, and her bare hands one Common die. **The wand's heavy
attack** - its great two-handed cast - sends no spell: it is the wand brought
down on what is in front of her, so it rolls **one D20** whatever the wand's
rarity, costs no mana, and its border adds nothing. A heavy attack can be made
**once every 10 seconds**: the orange bar to the left of the hotbar fills back
up after each one ("Heavy (X) ready"), and X before then does nothing. A sword swing, the axe's and the staff's blows and
the spins hit everything alive in front of her within reach (the spins all
round); a swing turns her toward a dummy just off to one side. The dice are
the 64-pixel sheets on an ordinary screen and the 128-pixel ones on a
high-density one (`assets/sprites/combat/d20_px64/`, `d20_px128/`: a
tumbling `roll.png` and `land_01` … `land_20.png`, the die coming down on
that number), drawn once in grey and coloured on the GPU by a palette swap.
The other dice a [forged weapon](#forged-weapons) can roll are drawn the same
way beside them (3D-Modeling's `items/combat_fx/combat_fx_sprites.py`):
`d4_`, `d6_`, `d8_`, `d10_` (its `land_10` shows the 0), `d12_` and
`dpct_` (the percentile die: `land_00`, `land_10` … `land_90`).

### Arrows, bolts and spells

- **The bow** looses an arrow when the draw is let go, **the crossbow** a bolt
  as it fires: they fly in an arc, **aimed at the dummy she faces** (within
  25° either side, 25 m away at most), and **stick in the ground** where they
  miss, fading after 8 s. The roll is made as she shoots and lands on what
  the shot strikes.
- **A wand, or a Magic or better staff**, casts a **spell** (the staff's
  attack) that flies straight and bursts on what it hits or at the end of
  its flight: **blue** for a Magic Staff or wand (and a plain wand), **yellow**
  for a Honed one, otherwise the rarity's colour (a Perfect staff's orange,
  a Mythic one's teal). A Basic staff has no magic in it and thrusts. Each spell costs
  **10 mana**.

### Dodging

**Left Alt** (or **Z**) is the dodge key, and how long it is held decides the
move (`world/Player`):

- **A tap dashes** - she drops her weight and drives off the back foot, low
  and leant into it with the arms swept back and the weapon trailing, glides
  **3.5 m** and brakes on the back foot coming up ready (12 frames, 0.4 s).
  Pale blue afterimages - her silhouette, fading - are left behind her while
  she is going fast.
- **Held, it rolls** - the dash starts the moment the key goes down, so a
  dodge is never late; if the key is still down when the push-off is over (5
  frames, a sixth of a second) the dash turns into the **combat roll** at that
  very frame (the two share their first five frames, so nothing jumps): a dive
  into a tucked shoulder roll, the weapon held across in front of the shins,
  once over onto her feet and up ready - **4.7 m** in 22 frames (0.73 s). From
  the dive until she lands **nothing can hurt her** (`Player.invulnerable()`,
  for the enemies to come), and she rolls straight **through the training
  dummies** (a dash stops at them like walking does).

<img src="docs/screenshots/dodge.png" width="98%" alt="The dash with the sword and shield, pale blue afterimages trailing behind her; the combat roll's dive, tuck, landing crouch and rise; a roll with the battle axe held across; a dash with the crystal staff">

Either goes the way she is moving - she turns to it at once - or, standing
still, the way she faces, with whatever is in her hands: the sword and shield,
the axe, the bow, the crossbow, the staff, a one-handed weapon or nothing (both
states are drawn with every carried item). She can dodge from standing,
walking, running, crouched or from behind the shield, not in the air or in the
middle of a swing, a cast or a shot (an emote is cut short); the next dodge
can start **0.35 s** after the last one ended (`cooldowns.dodgeSeconds`), so
dodging is no quicker a way about than sprinting.

### Mana

Her **mana** is the blue bar beside the hotbar: **100**, filling back up at
**5 a second**. A spell with too little mana fizzles ("Not enough mana", no
dice).

### The rare borders

The borders the [rarity](#items-rarity-and-loot) system gives some
weapons do what they say, and each one's effect is written under the dice:

- **Venom** - the blow's damage again by half, as poison over the next
  **10 s** (a tick a second, in small green numbers): 100 becomes 150. Every
  blow poisons anew.
- **Mystic** - **×2.5** damage (+150 %), for **twice the mana**: a spell costs
  20, and a Mystic melee weapon spends 10 a blow (its bonus is lost when she
  has too little; a Mystic spell fizzles).
- **Bonded** - **+10 %** for each living thing this very weapon has struck in
  the last **5 minutes**, the one it is striking included (a dummy lying
  knocked down does not count until it is up again).

### Forged weapons

A weapon made in Creative Mode's [Forge](#the-forge) is one of the eight -
its look, its stance and its moves - with what the Forge gave it: its own
**name** and **rarity**, each **part in a colour of its own** (the blade, the
handle, the fittings, the wood ... or all of it), its own **dice** - **how
many** (1 to 10) and **of what**: a **d4, d6, d8, d10, d%, d12 or d20** - in a
**colour of their own**, and **any of the three effects**, Venom, Mystic and
Bonded, even all three at once. In a fight it rolls its dice in their colour
(a heavy attack one more), every effect it has lands and is written under
them, and the dice's rims are its first effect's colour. A **d%** is rolled
as on a table: a percentile die (00 – 90) and a d10 side by side, 00 and 0 a
hundred. It is drawn in its colours in her hands, lying in the world and in
the inventory, whose tooltip says what it rolls.

<p align="center">
  <img src="docs/screenshots/forge-dice.png" width="49%" alt="A forged sword's three blue d8s over her head: 14, Mystic ×2.5 and Venom under them">
  <img src="docs/screenshots/forge-dice-lineup.png" width="49%" alt="The d4, d6, d8, d10, percentile die and d12 beside the d20, each landed on its faces">
</p>

Items name it `forged:<id>` - in a storage chest's or a barrel's **Holds**, a
script's `give forged:duskfang`. `assets/forge/duskfang.json` is a sample: an
Epic sword with a purple blade rolling 3d8 in blue, Venom and Mystic.

### Jump attacks

Attack **in the air** and she drops into a **plunging strike**: the weapon
brought down two-handed as she falls, landing with a blow that hits
everything round her (a little over a metre and a half) and rolls **a die
more** than a swing - dust (or a spell's colour) thrown out in a ring where
she came down. The bow and the crossbow loose a shot from the air instead.

<p align="center">
  <img src="docs/screenshots/jump-attack.png" width="49%" alt="In the air, the sword raised two-handed over a training dummy">
  <img src="docs/screenshots/jump-attack-landing.png" width="49%" alt="Landing: the blow rings out round her and three dice roll">
</p>

### Dice that add, subtract, multiply, divide or scale

A forged weapon's roll can be more than one kind of die. After its own dice,
up to four **more groups** each do something to the total so far, left to
right (`combat/DiceRoll`): **+** adds them, **−** takes them away, **×**
multiplies, **÷** divides, and **%** scales - a d% adds that many percent of
the total so far. `2d8 + 1d6 × 1d4` rolls the two d8s, adds the d6, and
multiplies all of it by the d4. Each die comes up in its colour over her
head with its sign beside it; the total under them. Set in the Forge's **Dice
& ammo** tab ([The Forge](#the-forge)).

### Sneaking

How easily she is noticed is a number (`world/Stealth`): 1 walking about in
the open by day, less **crouched** (0.5 moving, 0.35 still), standing still,
in **tall grass**, at **night**, in fog or rain or snow, or **under the
water** (0.25); more running, sprinting, riding or swinging. At **0.45 or
less she is hidden** - an eye shut over the stamina bar says so - a skittish
animal lets her come nearly up to it, and her blows are **sneak attacks**:
twice the damage, said under the dice. A card's cloak hides her better still.

<p align="center">
  <img src="docs/screenshots/sneaking.png" width="70%" alt="Crouched by the training dummies: the eye over the stamina bar says she is hidden, her blows sneak attacks">
</p>

### Ammunition

Every weapon uses something up as it is used (`world/Ammo`, `world/Supplies`):

| Weapon | Uses | Weapon | Uses |
|---|---|---|---|
| sword, dagger | **whetstones** | wand | **small crystals** |
| battle axe | **iron wedges** | crystal staff | **large crystals** |
| mace | **mace studs** | longbow | **arrow bundles** |
| crossbow | **bolt bundles** | | |

One is taken from her inventory every **12** blows or shots (each tier of a
weapon can last longer, and the Forge sets it for a forged one) - *Reloaded*
pops over her head when a fresh one is taken up. With none left, a blade or a
mace still strikes, **dull**, for half (*Dull: no whetstone*); a bow, a
crossbow, a wand or a staff will not shoot (*Out of arrow bundles*). The HUD
shows the one in hand and how many she carries, and her coins. They are found
in chests, sold in [shops](#shops), and lie about as pickups; she starts the
demo with two of each. A level uses ammunition unless its `extra` says
`"ammo": false`.

### Upgrade tiers

A forged weapon has **tiers**: 2 to 10 of them, each its own name, rarity,
dice, colours and how long its ammunition lasts, set in the Forge's **Tiers**
tab. **Right-click** it in the inventory and its tiers stand beside it - each
one's name, rarity, dice and what it costs, the one it is at marked - with
the **Upgrade** button. An upgrade costs **upgrade ingots**, by the next
tier's rarity: Common or Uncommon 1, Rare 2, Epic 3, Legendary 5, Mythic 8.
A tier set as a **mystery** shows as **???** until it is reached (the last
of the chart's tiers is one unless the Forge says otherwise). Upgraded, it
changes colour as its tier says. In scripts, `forged:<id>:<tier>`.

<p align="center">
  <img src="docs/screenshots/weapon-tiers.png" width="70%" alt="A sword right-clicked in the inventory: its five tiers beside it, the second marked as the one it is at, the last a mystery, and the Upgrade button">
</p>

### Tuning

The numbers are in **`assets/combat/combat.json`**, read when the demo
starts - the dice per rarity, the heavy die, the wand's heavy dice
(`dice.wandHeavy`), the heavy attack's cooldown (`cooldowns.heavySeconds`), the wait between dodges
(`cooldowns.dodgeSeconds`), mana, the shots' speeds, range
and aim, how long arrows stay stuck, a dummy's health and how long it lies.
Any value left out keeps its default (`combat/CombatTuning`); the borders'
own numbers are `world/Border`'s. The fighting's other sprites are under
`assets/sprites/combat/`: `arrow/` and `bolt/` (`fly.png`, the head to the
right), `magic_bolt/` (`fly.png`, 8 frames), `magic_burst/` and `hit_spark/`
(`burst.png`), each with a `profile.json` saying how big it is in the world.

## Classes, cards and coins

### Choosing a class

She is one of **nine classes** - **Mage, Bard, Knight, Warrior, Warlock,
Druid, Rogue, Ranger** or **Cleric** (`assets/cards/cards.json`). The first
time the demo starts, the nine stand as tiles over the game - each its name
in its colour, what it is about and its starting deck - and the one clicked
is hers; **Pause → Class** chooses again (a new class starts her deck
afresh and gives back her perk points). Her class is kept between runs.

<p align="center">
  <img src="docs/screenshots/class-picker.png" width="49%" alt="The class picker: the nine classes, each with its pointer, what it is about and its starting deck">
  <img src="docs/screenshots/cards-hand.png" width="49%" alt="The Mage's hand of four beside the inventory: Healing Spell, Cast, Cast and Bind, the piles and the Reshuffle button">
</p>

### The deck

Each class starts with a **deck of 8 cards** - the Mage's: **4 Cast, 2 Bind,
Transfigure, Healing Spell** - and **draws 4** of them. Every **5 minutes**
she draws one more, up to **8** in hand. **J** (or the **Cards** button
beside the inventory) shows her hand beside the inventory - the game stands
still - and a click **plays** a card: no slot, no waiting. A played card goes
to the **discard pile** until she reads a **reshuffle scroll** (the
**Reshuffle** button), which shuffles her hand and her discards back into the
deck and deals a fresh hand of four. A card she gains - from her perk tree or
a shop - goes onto the discard pile.

There are **200 cards**, each with its own name, rarity and what it does, by
the class: helps for so long or so many blows - more damage, on which blows
(any, melee, ranged or spells), spells for less mana, faster stamina, armour,
thorns, lifesteal, poison on her blows, more coins or experience, a cloak to
hide in - and things at once: health, mana, stamina, mana into health, a card
drawn, coins, experience, breath. 22 or more for each class, two for any
class. What a card does stands on its face (`ui/CardFace`), its rarity's
colour round it; the helps she has show over the HUD with how long they last.

### Experience, levels and perks

Felling a dummy gives **20 experience**, a flying dummy **30**, an animal
hunted **10**, a chest opened **10**. A level takes **100 × the level** to
reach, and each gives a **perk point**. The **Perks** tab beside the hand is
her class's **perk tree**: four rows of three, each a card for her deck or a
help **for good**, costing 1, 1, 2 and 3 points by its row - a row is open
once she has one of the row above.

### Coins

Felled dummies drop a few **coins**, chests throw out more, and a card or two
gives them; picked up, they go into her **purse** (under the ammunition on
the HUD). She spends them in [shops](#shops).

### Shops

A **market stall** or a **shop's counter** sells things (`world/Shop`): walk
up to it and **E** (*Shop at Market stall*) - its stock stands beside her
inventory, the game stands still. Each thing for sale is a row: its picture
(a card's colours for a card), its name in its rarity's colour, how many are
left (*Plenty*: it never runs out) and its price, and a **Buy** button. A
card bought goes onto her discard pile. A shop that **buys** takes what she
sells for **half what it is worth**: right-click (or Shift-click) one of her
things to sell one. The demo has a striped stall off to her left at the start.

<p align="center">
  <img src="docs/screenshots/market-stall.png" width="49%" alt="The striped market stall in the demo, E to shop at it">
  <img src="docs/screenshots/shop-panel.png" width="49%" alt="The stall's stock beside her inventory: supplies, ingots and scrolls, each with how many are left, its price and Buy">
</p>

In Creative Mode a shop is Place → **Town**: a stall of supplies, a card
seller's, a smith's counter, any stall, or a keeper's spot at a counter
already built. Its **Sells** is a line of what it has - an item, how many,
its price:

```
whetstone x20 @3, arrow bundle, upgrade ingot x5, longbow rare @90, card:ember_script @60, cards x4
```

`x20` is how many (none: it never runs out; a card, one); `@3` its price in
coins (none: what it is worth); `card:<id>` a card; `cards x4` four of the
shop cards of whoever is shopping, the same four each time for each class.
Left empty, a shop sells the seven kinds of ammunition, upgrade ingots,
reshuffle scrolls and three cards. **Buys** says whether it takes her things.

### The mouse pointer

Her class has its own **pointer**: a wand for a Mage, a lute for a Bard, a
sword for a Knight, an axe for a Warrior, a gloved hand for a Warlock, a leafy
branch for a Druid, a dagger for a Rogue, an arrow for a Ranger, a holy symbol
for a Cleric - 32-pixel pixel art in the 128 colours, doubled on a
high-density screen (`tools/ui/make_pointers.py`). **Pause → Class** picks
any of the nine instead, **Custom** (a picture of your own:
`assets/ui/pointers/custom.png`, its hot spot its top-left corner, taken up
again when it changes), or the system's arrow.

## Heights

North of the meadow the ground rises into **the highlands**
(`world/Terrain`): a heightmap on a half-metre grid, drawn as one mesh of
triangles (`world/TerrainRenderer`) in the meadow's own grass, with **rock**
where it is too steep to walk (gravel and strata, mapped on all three axes so
a cliff is not stretched) and **vines** painted on the cliff she can climb.
Everything stands on it - her, the dummies, the chest and its loot, the
items, the trees, the shadows (bent over the slopes) - and outside the
highlands the ground is the flat meadow as before. The meadow is solid
earth: from beside the hollow, its near rim hides its inside.

```
the ramp       3 m wide, from the end of the north path up to the plateau (her pace slows uphill, quickens down)
the plateau    6 m high, 12 x 12 m; the chest on top
the ledge      its west side, sheer: 6 m down, two dummies below it
the cliff      its east side, sheer and hung with vines: climb it (two dummies at its foot)
the hill       7 m high, round and walkable all the way up, west of the plateau
the hollow     2.5 m deep, east of the ramp: a flier circles over it
```

<img src="docs/screenshots/highlands.png" width="98%" alt="The highlands: the dirt path climbing a ramp to the grassy plateau with its rock sides, the chest on top, the round hill behind, the hollow to the right and a flying dummy beyond">

Ground steeper than 1 m up a metre is too steep to walk up (she stops at
its foot, or slides along it); off an edge she **falls** (the stance's jump,
held), and ground too steep to stand on she slides down.

### Falls, health and the roll

A fall of more than **3 m** hurts, **9** for every metre more - the ledge,
about 27. Press the **dodge key in the air** and she lands in a **combat
roll** instead, unhurt (from 1.2 m up). Her **health** (100) is the red bar
on the left, over the heavy attack's; it fills again 5 s after she was last hurt, 4 a
second. At 0 she is **knocked out** and back on her feet at the start.

### Swimming

Water deeper than she stands she **swims** (`world/Player`): treading water
when still, the breaststroke when moving, Shift faster - stamina going as
she does. **C dives** under; under the water she swims in any direction the
camera looks (W A S D, Space up, C down), the view tinted and muffled, and a
**breath bar** shows; she comes up with a gasp. When the breath is gone she
**drowns**, a little at a time, until she reaches the air. A fall into deep
water does not hurt her. Out of it, she wades onto the shallows with water
running off her.

<p align="center">
  <img src="docs/screenshots/swimming-under-water.png" width="70%" alt="Diving: she swims under the water, seen down through it in its colour, her breath bar going">
</p>

### Climbing and stamina

Walk into the vines and she takes hold (the `climb` animation, her hands
empty): **W** climbs (0.82 m a second), **S** climbs down, **A** / **D** go
along the wall. With her hands at the lip she **steps up** over it
(`ledge_up`): she pulls herself up, presses up on her arms, puts her right
foot on the top and stands up on it.

To **climb down**, walk off the top of the vines: a step from the lip she
turns her back to the drop and **steps down** over it (`ledge_down`, the
step up the other way round) until she hangs by her hands, and keeps
climbing down while you keep walking out. Too tired to climb, she stops at
the lip instead of stepping off it; a jump still takes her off the top.

<img src="docs/screenshots/ledge.png" width="98%" alt="Top row: she pulls herself up over the lip of the vine cliff, swings a foot up onto the top and stands. Bottom row: she stands at the lip with her back to the drop, lowers herself over it and hangs by her hands on the vines">
Climbing costs **stamina** - the yellow bar on the right, shown when it
is not full - 9 a second climbing and 3 hanging still; it
fills again 0.8 s after she stops, 25 a second. She needs 15 to take hold
(or to step down onto the vines), and at 0 she lets go and falls. **Space**, the dodge key or **C** let go.

<img src="docs/screenshots/climb.png" width="49%" alt="She climbs the vine-covered cliff, her stamina bar showing 69 beside the mana">
<img src="docs/screenshots/aim-up-flier.png" width="49%" alt="Locked on to a flying dummy overhead, she draws the bow aimed up at it">

### Climbing onto furniture

She stands on the tops of furniture no taller than **2 m** - a table, a
bench, a counter, a crate, a barrel - dropped or jumped onto, stepped up
onto when it is no higher than 0.4 m, and walked off (falling from it).
**Space** by one whose top is up to **1.8 m** over her feet - about her height
- and she **climbs up onto it**, the step up over the vines' lip played from as
far into it as the top is high; in the air, coming up against one's edge, she
takes hold of it and climbs up too. Furniture is as tall as its 3D model.

<img src="docs/screenshots/climb-onto-table.png" width="98%" alt="She runs up to a table, takes hold of its edge, pulls herself up and stands on it">

### The flying strike dummy

**Two flying strike dummies** (`combat/FlyingDummy`) - the training dummy's
body on burlap wings - circle 4 m up, out of a sword's reach, one over the
meadow's north side and one over the hollow. When she comes within 11 m it
hangs, screeching, and **dives** through where her chest is and climbs away
the other side: **8** if it strikes her, half behind a block, nothing if she
**parries** it (it is knocked back) or **rolls** through it. Low in its dive
a sword reaches it; otherwise shoot it - **aimed up** (below). Its health
gone, it tumbles out of the air, lies a while and beats back up whole. Its
shadow on the ground under it, and every arrow's, bolt's and spell's, shows
where it is.

```
assets/sprites/objects/flying_dummy/dummy/fly_<elev>_<dir>.png            16 frames, loops: flapping, bobbing
assets/sprites/objects/flying_dummy/dummy/swoop_<elev>_<dir>.png           8 frames, loops: diving, the wings swept back
assets/sprites/objects/flying_dummy/dummy/hit_<side>_<elev>_<dir>.png     12 frames: knocked away, the wings flung up
assets/sprites/objects/flying_dummy/dummy/fall_<side>_<elev>_<dir>.png    12 frames: tumbling over, the wings folding
assets/sprites/objects/flying_dummy/dummy/down_<side>_<elev>_<dir>.png     1 frame: lying on the ground
assets/sprites/objects/flying_dummy/dummy/rise_<side>_<elev>_<dir>.png    18 frames: righting itself, beating back up
```

drawn by [3D-Modeling](https://github.com/Larleeloo/3D-Modeling)'s
`items/flying_dummy/flying_dummy_sprites.py`.

### The lock-on

**L** locks on to whatever enemy is nearest the middle of the view within
**30 m** - a training dummy or a flier - with brackets round it, its name and
its health; **K** moves the lock on to the next one, left to right, and **L**
again lets go. Locked on, her shots and spells go for it wherever it is
(leading it as it flies), her swings turn to it, and the camera keeps it in
view. When it goes down the lock moves on to the next nearest, if there is
one.

### Aiming up

A shot at something well over her hands - **20°** up and more: a flier, or a
dummy on the plateau above her - is made from the **aimed-up** animations,
the bow drawn and loosed at the sky (`bow_draw_up`, `bow_fire_up`), the
crossbow raised (`crossbow_fire_up`), the staff and the wand cast upward
(`staff_attack_up`, `wand_attack_up`). Crouched, she shoots as before.

### The high ground

A shot or a spell at something **2.5 m** or more below her rolls **a die
more** ("High ground +1 die" over her head) and carries a quarter farther. A
swing reaches only what is about level with her - from half a metre below
her feet to 2.4 m above them.

### The automatic camera height

On the hills the camera **rises on the peaks** - looking down over the land
round her, drawn back a little - and **sinks in the hollows**, by how far she
stands above or below the ground about her (Pause → Settings → Camera height,
or **O**: automatic or fixed). It never dips into a hillside.

## Sound

The sound system is Larsons-Game-Engine's (`audio/`): its own **MP3 decoder**
(a pure-Java port of the public-domain minimp3, so no codec or library is
needed - WAV, AIFF and AU load through the JDK), a software **mixer** that
plays any number of sounds at once, each at its own pitch, volume and stereo
position, with every one-shot at a slightly different pitch each time, and
the **sound pack**: sound keys resolved to files by name. A machine with no
audio device is silent rather than broken. The decoder is **gapless**: it
reads the encoder's delay and padding from the MP3's LAME header and trims
them, so a sound starts on its first sample and a loop (the wind, a chest
standing open) goes round without a hiccup.

### What makes a sound

Everything that happens in the game is a **sound hook**, and every sound
belongs to one of nine **categories** (`audio/SoundCategory`), each with its
own volume:

| Category | What is in it |
|---|---|
| **Footsteps & movement** | a footstep each time a foot comes down - on the ground under it (grass, a dirt path, gravel, the forest floor, sand, mud, snow, stone or crystal) or a splash wading through water, quieter the softer she goes - jumping, landing (louder the farther she fell), a hard landing, rolling out of a fall, crouching and standing, the dash and the combat roll, stepping over a ledge, each hand taking hold of the vines, letting go |
| **Voices** | each body's laugh, cry, surprise and anger, being hurt, knocked out, and out of breath |
| **Weapons** | every swing, chop, smash, thrust, shot and cast of all eight weapons, the parry, taking each weapon in hand, the heavy attack ready again |
| **Hits & magic** | the dice, a blow landing, arrows and bolts striking or sticking, spells cast, hitting, bursting on the ground or fizzling, venom, being struck, blocking, parrying, dodging |
| **Creatures** | the training dummies struck, falling and rising; the flying dummies' wings on their rounds, the screech before a dive, the dive, struck, falling, hitting the ground and beating back up; the critters' chirps, croaks and plops; every animal's calls, cries when hurt, warnings and wingbeats |
| **World & loot** | the chests (shut and humming, thrown open, standing open, closing), the loot thrown out and landing, and its rarity ringing out from Uncommon to Mythic, picking things up and dropping them |
| **Ambient** | the breeze over everything, and each biome's own by day and by night - the plains' birdsong and crickets, the marsh's insects and frogs, the mountain wind, the jungle by day and by night, the savannah's cicadas, the sea, a stream, the waste's hollow wind, the crystals' chimes - fading into each other across the biomes' edges (and any of them placed as a sound in a level) |
| **Music** | the songs, each looping where it plays: the main menu's |
| **Interface** | menus, the inventory, the wardrobe, the lock-on, screenshots, cutscene lines, and the level editor's tools |

The animations are hooks themselves (`audio/AnimationSound`, updated each
frame by `audio/WorldSounds`): an animation's sound starts with it, looping
for as long as a held animation holds and once for a one-shot. A footstep
falls on the gait itself - each walk, run and sprint cycle starts with the
right heel down and has the left down halfway, as the Blender gaits are
authored. Sounds in the world are placed: quieter the farther off, panned to
the side of the screen they are on. Combat, the player's falls and climbs,
the menus and the inventory play where they happen.

### Choosing your own sounds

**Pause → Sounds** lists every sound, a tab per category. Each row has a play
button, where its sound comes from now (the game's file, a file it shares
with sounds like it, yours, muted, or silent), and:

- **Choose…** - your computer's open-file dialog, for any MP3 or WAV (on
  Linux it needs `zenity` or `kdialog`); or **drop the file on the row**;
- **Mute** / **Unmute**;
- **Reset** - back to the game's own.

<img src="docs/screenshots/sounds-menu.png" width="98%" alt="Pause → Sounds on the Weapons tab: eight category tabs down the left with their counts, a master and a Weapons volume slider, and rows of sounds each with a play button, where its sound comes from (the sword swing is the player's own my_sword_swing.mp3, the battle axe's spin attack muted, the rest the game's files), and Choose…, Mute and Reset">

A master volume, a volume per category, and **Rescan sound files** (after
changing `assets/sounds/` with the game running) sit above the list. Your
choices and volumes are yours alone, saved as you make them
(`config/sounds.json`, `config/settings.json`).

### The sound pack

```
assets/sounds/
  SOUND_KEYS.txt          every sound in the game, by category, and the file to name it (kept up to date by the game)
  README.md               how to name and add sounds
  soundpack.json          volume, pitch, pitch variation, per-sound overrides
  player/                 footstep_grass.mp3 ... jump.mp3, land.mp3, dash.mp3, roll.mp3, climb_grip.mp3,
                          attack.mp3, axe_attack.mp3, bow_fire.mp3, wand_attack.mp3 ... (both bodies)
  player/feminine/        her voice: emote_laugh.mp3, hurt.mp3 ... and any of her 131 animations of her own
  player/masculine/       ... and his
  chests/                 idle.mp3, open.mp3, opened.mp3, close.mp3 (every chest; chests/<chest>/ for one's own)
  items/                  pickup.mp3, drop.mp3, equip.mp3, and items/<item>/equip.mp3 for each weapon
  combat/                 dice_roll.mp3, blow.mp3, arrow_hit.mp3, spell_cast.mp3, block.mp3, parry.mp3 ...
  combat/attack_dummy/    hit.mp3, fall.mp3, rise.mp3
  combat/flying_dummy/    fly.mp3, warn.mp3, swoop.mp3, hit.mp3, fall.mp3, down.mp3, rise.mp3
  world/                  loot_throw.mp3, loot_land.mp3, loot_uncommon.mp3 ... loot_mythic.mp3
  ambient/                wind.mp3, stream.mp3, crickets.mp3
  music/                  dream_on.mp3 ... one per song (seamless loops); main_menu.mp3, your own menu song
  ui/                     click.mp3, hover.mp3, pause.mp3, inventory_open.mp3, wear.mp3, error.mp3 ...
```

**Fallbacks** keep the pack small: a sound one folder up covers everything
like it until one has its own - `player/attack.mp3` is both bodies' sword
swing, `player/masculine/attack.mp3` his alone; a move falls back to the
move it is a version of (the axe's heavy attack to its chop, a crouched shot
to the standing one); a footstep on one ground to `player/footstep.mp3`;
`chests/open.mp3` is every chest's. A body's sounds are the body the
wardrobe wears, in any style.

The files there now, but for the music, are **placeholders**, every one made in code by
[`tools/sounds/make_placeholders.py`](tools/sounds/make_placeholders.py)
(Python with numpy and scipy, and ffmpeg) - filtered noise, struck and
plucked strings, formant voices - so they are the game's own, with no
licence to worry about. Replace any of them with a real recording of the same
name; `python3 tools/sounds/make_placeholders.py --list` lists them, and
naming some (`... player/jump ui/click`) makes just those again.

Other code can add sounds of its own (`SoundKeys.register`, the level
editor's objects): each gets a row in its category's tab, a line in the key
list, and the same fallbacks and volume.

### Music

The songs are medieval-style covers of the songs on the roadmap's Sound
milestone - lute, harp, recorder, fiddle, a portative organ's drone and a
hand drum - arranged for this game and not for sale. Each is listed in
`audio/Songs` and is a seamless loop at `assets/sounds/music/<song>.mp3`,
heard under its own sound key (`music/dream_on`); the full list, with what
each arrangement does, is in [`music/`](music/README.md).

Where they play:

- **The main menu** plays **"Dream On"** (Aerosmith), looping. Its key is
  `music/main_menu`, which falls back to `music/dream_on`: put your own
  `main_menu.mp3` in the pack (or choose one in Pause → Sounds → Music) and
  the menu plays that instead.
- **A level** plays the music its maker picked in Creative Mode's Level tool:
  one song looping, **Shuffle all** (every song once in a random order, then
  shuffled again, never the same one twice running), or **None**. The demo
  and every new level shuffle; a level saved before levels had music does
  too. It plays a little under the menu's level (`DemoScene.MUSIC_VOLUME`).
- **Creative Mode** itself is quiet but for the Level tool's song player:
  pick a song under **Listen to**, play or pause it, and drag the timeline to
  anywhere in it (it is silent while held and plays on from where it is let
  go); **Use this song** makes it the level's. The choice is saved in
  `level.json` (`"music": "dream_on"`) and undoes like any other change.

A scene names the music it wants as it opens and lets it go as it closes
(`audio/Music`). A song is read on a thread of its own, so nothing waits for
it, then fades in over 2.5 seconds; leaving fades it out over 1.5, and the
next scene's song (if it has one) starts once it is quiet. Going from one
menu straight into another keeps it playing, and a level entered again with
the same playlist carries on with it. In a playlist each song plays through
once, fades as it ends, and the next one (already read ahead) follows. A song
always plays at its own pitch, and like any sound it can be swapped for your
own MP3 or muted in **Pause → Sounds → Music**, with its own volume.

```
music/<song>_medieval.mid         an arrangement: open it in FL Studio (a track per instrument)
tools/music/<song>.py             writes that MIDI and renders assets/sounds/music/<song>.mp3
tools/music/medieval.py           the toolkit: parts, the MIDI file, rendering and the seamless loop
tools/music/arrange.py            chord names, tunes written as note names, and the band's ways of playing
```

`python3 tools/music/dream_on.py` makes both of a song's files (FluidSynth
and the FluidR3 General MIDI SoundFont render it; `--midi` writes just the
MIDI), and `python3 tools/music/all.py` makes every song's. To use an
arrangement edited in FL Studio instead, export it from there as a loop and
put it over the song's MP3 - see [`music/`](music/README.md).

## Cutscenes

A cutscene is a script of actors and lines, played over close-up layers:

```
assets/cutscenes/<scene>.cut                          the script (menu.cut: the main menu's)
assets/closeups/<layer>/<item>_px128/<clip>_side_se.png    one layer's clip, close up (and _px64)
```

- **Close-ups** are the wardrobe's items again, rendered waist-up and close
  from the front-left three-quarter view - a face about 50 pixels tall in a
  128-pixel frame instead of 12 - for five looping conversation clips:
  `listen`, `talk`, `explain`, `laugh` and `surprise`, with mouth shapes,
  blinks, brows, nods and gestures. Every item of a layer that can show in
  the frame has them (shoes, trousers and what the hands carry are below it
  or put away), each its own layer cut against the outfit like the game
  sprites, so **any character - the player's own included - can play a
  cutscene**, in any items and any colours. A character on the right of a
  two-shot is drawn mirrored, so the two face each other.
- **Actors** (`cutscene/Actor`) are a wardrobe each - a copy of the player's,
  or dressed in the script - playing clips, and what the scene does to their
  look: a layer's colour fading to another (an option or any `#rrggbb`), a
  flash, a tint (warm light, cold light) and a fade. All of it is palette and
  vertex-colour work at draw time; no sheet is ever re-uploaded.
- **The script** (`cutscene/Cutscene`): `actor`, `stand`, `say`, `clip`,
  `colour`, `wear`, `flash`, `tint`, `fade`, `wait`, `loop` - see
  [`assets/cutscenes/README.md`](assets/cutscenes/README.md). `say` plays the
  speaker's clip for the length of the line while the others listen.
- **The stage** (`cutscene/CutsceneStage`) draws it: a backdrop, the actors'
  layers in the game's draw order at whole-pixel zoom, and the line in a box
  with the speaker's name. Layers load through the sprite library (its memory
  budget, its decoders); an actor's other clips load ahead, and while a new
  clip is still loading the actor holds its last complete frame.

## The scenery

The demo's meadow is all pixel art, modelled, rendered and drawn in Blender by
the scripts in [3D-Modeling](https://github.com/Larleeloo/3D-Modeling)'s
`scenery/` folder, every pixel in the same 128-colour palette as the
characters:

- **An oak** - a fully textured tree, 48 frames of it swaying in the breeze
  (a four-second loop), drawn in 128 × 128-pixel frames twice the size of
  the characters' pixels, so it stands about 4.4 m tall and 4 m across.
- **Three grass tufts**, 32 × 32 pixels, each 24 frames blowing in the
  breeze. The wind rolls across the field as a gust: a tuft downwind sways a
  moment after one upwind, so the grass moves in waves rather than all at
  once.
- **Three rocks** - a boulder, a mossy rock and a cluster of stones -
  128 × 128 pixels, standing still.
- **The ground**: a 512 × 512 grass tile, its pixels the size of the
  characters', repeating seamlessly, sharp up close and smooth far off.

Like Minecraft's plants, a tree or a tuft is the same sprite on **two cards
crossed at right angles**, so it has body from every side and needs no
directions of its own. A rock is one card that turns to face the camera
instead, because from above a crossed rock reads as a star (a rock whose
profile says `"facing": "cross"` is crossed like the rest). The cards are cut
out at their pixels' edges and depth-tested against the characters, so she
walks behind a tree, in front of it and through the grass, but not into a
trunk or a rock. The trees and rocks cast shadows.

The meadow is planted from whatever is in `assets/scenery/` (a folder per
sprite: `sheet.png` and its `profile.json`; `ground/grass.png`), the same way
every run, clear of the spawn, the chest and the weapons. **P** (or Pause →
Settings → Scenery) hides it all. The folder's format is in
[assets/scenery/README.md](assets/scenery/README.md).

## Biomes and water

The moon is more than a meadow. There are **nine biomes**, each with ground,
plants, trees, rocks, water and sounds of its own, all painted in Creative
Mode with the biome brush, and a **Biome tour** level in `assets/levels/`
(Dev tab, **Play** or **Edit**) that has every one of them:

<p align="center">
  <img src="docs/screenshots/biome-tour.png" width="98%" alt="The Biome tour level from above: snowy mountains, jungle, the crystal biome, marsh, plains, savannah, waste and a sea bay, a river running through">
</p>
<p align="center">
  <img src="docs/screenshots/biome-jungle.png" width="49%" alt="The jungle: giant trees with hanging vines, palms and ferns by the river">
  <img src="docs/screenshots/biome-shore.png" width="49%" alt="The ocean shore: a sea bay, palms on the sand, the river running in, acacias on the savannah behind">
</p>
<p align="center">
  <img src="docs/screenshots/biome-crystal.png" width="49%" alt="The crystal biome: iridescent ground, crystal trees and spires, a crystal stream flowing to the river">
  <img src="docs/screenshots/biome-marsh.png" width="49%" alt="The marsh: mud and meadow, weeping willows, reeds, mossy logs and swamp pools">
</p>
<p align="center">
  <img src="docs/screenshots/biome-crystal-night.png" width="49%" alt="The crystal biome at night, its trees and crystals glowing and lighting the ground">
  <img src="docs/screenshots/creative-biome.png" width="49%" alt="Creative Mode's Paint tool on its Biome page: the nine biomes, Sow its plants, and what the marsh lays down">
</p>

| Biome | Ground | What grows there | Water | Its sound by day / by night |
|---|---|---|---|---|
| **Plains** | grass, flower meadow | the meadow's grass, tall grass, ferns, wildflowers; oak, birch, pine, autumn trees; rocks, mossy logs | clear | birdsong / crickets |
| **Marsh** | mud, meadow | reeds, wildflowers; weeping willows; mossy logs, river stones | swamp, with lily pads | insects over the water / frogs |
| **Mountains** | stone, snow | snowy pines; snow boulders | clear | high wind |
| **Jungle** | jungle floor, forest floor | ferns, wildflowers; jungle giants with hanging vines, palms; mossy logs | swamp | birds and insects / the jungle at night |
| **Savannah** | dry grass, red earth | dry grass, tall dry grass; acacias; termite mounds | clear | cicadas / crickets |
| **Ocean shore** | sand, gravel | beach grass; palms; beach rocks | the sea, its waves running up the beach | waves |
| **River** | gravel, sand | reeds; willows; river stones | clear, flowing | the stream |
| **Waste** | ash, stone | thorn bushes; dead trees; ash spires | swamp | a hollow wind |
| **Crystal** | iridescent crystal, stone | crystal grass; crystal trees, clusters and spires, iridescent rocks - **glowing at night** | crystal water, shimmering violet and mint | chimes and hum |

- **Water** is painted (Creative Mode's Water tool): ponds and lakes lying
  level in a hollow, streams and rivers following the ground down and
  flowing - their ripples drift with the current - swamp water with lily
  pads, crystal water, and **the sea**, which fills everything below its level
  (and runs on to the horizon when it is above the land round the level).
  It is pixel art like the rest: banded by depth, the sky in it at a glancing
  angle, the sun glittering, a ragged line of foam at the shore. She **wades**
  through the shallows - slower the deeper it is - and cannot walk in deeper
  than 1.05 m.
- **Tall grass** bends aside as she walks through it, and springs back.
- **The crystals glow at night**, lighting the ground round them in their
  own colours.
- **Her footsteps** sound of what she walks on - grass, dirt, gravel, forest
  floor, sand (and ash), mud, snow, stone, crystal - and splash in water.
- **Each biome's sound** is heard as much as the country round her is that
  biome, so one fades into the next as she crosses from one to another, and
  changes with the day.

All of it was modelled and drawn by the scripts in 3D-Modeling's `scenery/`
(the trees in `tree_species.py`, the rocks in `rock_sprites.py`, the plants in
`grass_tuft_sprites.py`, the ground in `ground_textures.py`), in the same
128-colour palette; a sprite's `profile.json` names the biomes it grows in
([assets/scenery/README.md](assets/scenery/README.md)).

## Day and night

The world is a moon of a gas giant, and it keeps one face to it: the planet
hangs in the same place in the sky all day and all night, banded and huge, and
the sun goes round it. The sun rises in the east at 06:00, stands in the south
at noon and sets in the west at 18:00. The planet is lit on its side towards
the sun, so it is a thin crescent near the sun by day and **full at
midnight**, when it lights the night.

<p align="center">
  <img src="docs/screenshots/day-and-night.png" width="98%" alt="The meadow at dawn, noon, sunset and midnight under the gas giant">
</p>
<p align="center">
  <img src="docs/screenshots/light-rays.png" width="49%" alt="Morning mist and light rays slanting through the trees">
  <img src="docs/screenshots/night-lights.png" width="49%" alt="The open chest and its loot lighting the ground at night">
</p>
<p align="center">
  <img src="docs/screenshots/creative-time.png" width="60%" alt="Creative Mode's Time tool at nine in the evening, two lights placed">
</p>

- **The light** is white by day, gold low in the morning and evening, rose at
  the sunset and blue by night - warmer under a full planet. Every sprite, the
  ground, the hills and the scenery are lit by it.
- **The sky** shades from the horizon up, glows round the sun at dawn and
  dusk, and has drifting pixel clouds lit by the sun (pink at sunset, grey
  under the planet), stars that wheel round the pole through the night, and an
  aurora.
- **Mist** lies on the ground, thickest at dawn and thinning as it rises,
  drifting in banks.
- **Light rays** slant down through the trees' crowns and the mist while the
  sun is low, in the morning and the evening.
- **Lights of their own** - an open chest's warm glow, the loot's rarity
  colours, lanterns and crystals placed in a level - light what is round them
  at night, in pixel-hard bands as the art is shaded.

A day lasts 24 minutes (an hour a minute); hold **N** for the time-lapse.
In a demo script, `time 6:30` sets the hour and `timelapse on|off|<speed>`
runs it fast. Each level sets its own start hour, the day's length (0: the
time stands still), the planet - where it hangs, how big, its bands, rings -
and its clouds, stars, aurora, mist and light rays, with Creative Mode's
**Time** tool.

### Weather

A level's weather is **none**, **by biome** - each biome's own weather coming
and going in spells of a few minutes, fading out before the next fades in -
or **one weather all the time** (`world/Weather`):

| Weather | What it does |
|---|---|
| **Rain** | slanting streaks falling round her, the sky greyed and lowered, the fog in, the light dimmed |
| **Thunderstorm** | heavier rain, more slanted, the clouds dark; **lightning** lights the whole scene a moment and the **thunder** comes after it, later the further off |
| **Snow** | flakes drifting down and swaying as they fall, the light cold |
| **Fog** | the fog comes in close and the mist thickens, no sun |
| **Wind** | dust and leaves skittering past, the grass and the trees swaying faster |
| **Sandstorm** | sand streaming along the wind, the light gone sandy, the fog close |

| Biome | Its weathers, the likeliest first |
|---|---|
| plains | rain, wind, thunderstorm, fog |
| marsh | fog, rain, thunderstorm |
| mountains | snow, wind, fog |
| jungle | rain, thunderstorm, fog |
| savannah | sandstorm, wind, thunderstorm |
| ocean | wind, rain, thunderstorm, fog |
| river | rain, fog, wind |
| waste | sandstorm, wind, fog |
| crystal | snow, fog, wind |

<p align="center">
  <img src="docs/screenshots/weather.png" width="98%" alt="Rain, a thunderstorm's lightning, snow, fog, wind and a sandstorm">
</p>

Each has its sound held while it lasts (Pause → Sounds → Ambient: rain, gale,
sandstorm, snowfall, thunder), muffled indoors. The demo has its biomes'
weather now and then. In Creative Mode the **Time** tool's **Weather** page
sets the mode, the weather when it is fixed, **How heavy** (light rain to a
downpour) and, by biome, **How often** there is any; **See it here now** shows
one in the editor. In a demo script `weather <kind> [heaviness]`; in a level
file:

```json
"weather": {"mode": "biome", "kind": "rain", "strength": 0.8, "often": 0.35}
```

### Halls and caves

A level need not be under the sky. The **Sky** tool's **Over it** is the
**open sky**, **inside** - a great hall, a dungeon, under a roof - or a
**cave**'s rock: no sky, no sun, no planet, no weather, dark beyond the light
(**Dark from**, metres), cold and blue in a cave. Its lamps, candles and
fires are lit all day, and they are what lights it. In a level file `"cover":
"inside"` or `"cover": "cave"`.

<p align="center">
  <img src="docs/screenshots/cave-level.png" width="49%" alt="A cave level lit by lanterns, dark round it">
  <img src="docs/screenshots/creative-weather.png" width="49%" alt="Creative Mode's Time tool on its Weather page">
</p>

## Life: critters and foraging

The world has little creatures living in it, come and gone round the player
by where she is and the hour, and bushes to pick food from.

<p align="center">
  <img src="docs/screenshots/life-fireflies.png" width="49%" alt="Fireflies over the marsh at night, a frog by the path">
  <img src="docs/screenshots/life-crystal-night.png" width="49%" alt="The crystal biome at night: a crystal bloom glowing, a frog and a cricket">
</p>
<p align="center">
  <img src="docs/screenshots/life-forage.png" width="49%" alt="A sage bush to pick: E Pick sage">
  <img src="docs/screenshots/creative-life.png" width="49%" alt="Creative Mode's Life tool">
</p>

- **Fireflies** blink over the meadows, the marsh and the river after dark
  (blue and violet in the crystal biome), and light the grass round them.
- **Crickets** sit in the grass and chirp, mostly at night, and hop away from
  her feet.
- **Frogs** keep to the water's edge, croak, and hop off - into the water
  with a plop - when she comes near.
- **Dragonflies** dart and hover over water by day.

Each kind has a weight for every biome, keeps near water or not, and has its
hour (`ambient/CritterKind`); `ambient/Critters` brings them in round her as
the place and the time call for them and lets them go as she moves on. They
are drawn from 3D-Modeling's 24-view sprites (`creatures/critters.py`), at
about twice life size so they read at the camera's distance, and each makes
its sound where it is (Pause → Sounds → Creatures).

**Foraging bushes** - wild berries, bilberries, sage, sunfruit, thornberries,
sea buckthorn and the crystal bloom, each in its own biomes - stand among the
plants. Walk up to one and press **E**: she reaches in and what is on it goes
into the inventory, and the bush is bare until it grows back (four to seven
minutes). Pickings **stack** (20 to a slot, the count on the slot). Eat one by
clicking with it in hand on the hotbar, or right-clicking it in the inventory:
berries give back health, sage stamina, the crystal bloom mana, the rest a
mix - only what she has spent; nothing is eaten when she is full.

In Creative Mode the **Life** tool (key 0) sets how thick each kind lives in
the level (0 to 3 times), how fast the bushes grow back and how much they give;
**Place → Life** has the bushes, the pickings and **swarms** - fireflies over a
pond, frogs at its edge - that live where they are put, at their hour, always,
by day or by night. Scatter has a "*Biome*: bushes" set for each biome, and a
biome's paint sows a few. In a level file:

```json
"life": {"critters": {"firefly": 2, "frog": 0.5}, "regrow": 0.5, "yield": 1}
```

## Animals

Thirty-one animals live in the world, come and gone round the player by biome
as the critters are: **horses, cows, bears, sheep, pigs, dogs, foxes, cats,
rabbits, squirrels, deer, ducks, geese, swans, butterflies, bees and
iguanas**, and fourteen more birds - the **northern** woods' and fields'
**robin, blue jay, cardinal, crow, owl, eagle and pheasant**, and the
**tropical** **macaw, toucan, cockatoo, flamingo, peacock and hummingbird**
(the peacock fans its train at her; the perching birds hop about and flit
off when she comes near).

<p align="center">
  <img src="docs/screenshots/animals-lineup.png" width="98%" alt="All sixteen animals">
</p>
<p align="center">
  <img src="docs/screenshots/animals-plains.png" width="49%" alt="Horses, cows, sheep and rabbits on the plains">
  <img src="docs/screenshots/animals-marsh.png" width="49%" alt="Pigs, a fox, a goose and a duck in the marsh">
</p>
<p align="center">
  <img src="docs/screenshots/animals-goose-warns.png" width="49%" alt="A goose, locked on, wings up, warning her off">
  <img src="docs/screenshots/animals-goose-pecks.png" width="49%" alt="The goose pecks her: Pecked! -3">
</p>

Each is drawn the way the player is: pre-rendered in Blender from eight
directions and three camera heights (3D-Modeling's `creatures/animals.py`),
with its own sheets - standing, walking, running and eating, the rabbit's and
the squirrel's hops, and the birds' swimming, flying and wings-up hissing
attack. The four-legged ones also **lie down** now and then to rest, **swim**
across water too deep to wade (sunk to their waterline, their heads up), and
**fall** when they are hunted down; the cow lowers its whole neck to graze
and the swan curves its long one, joined smoothly to the body.
Ducks, geese and swans swim, sunk to their waterline, and take off when
startled; butterflies and bees flit about the flowers by day. Every animal
keeps to ground she could walk: up any slope she could walk up (to 45°),
never up a rock face or off a ledge.

Every kind has a **nature** (`ambient/Temperament`), and a herd placed in a
level can be given another:

| Nature | What it does | Whose it is |
|---|---|---|
| **Tame** | never runs off, never fights | (a farm's own, placed) |
| **Calm** | minds its own business, runs when hit | horses, cows, pigs, bears, butterflies |
| **Skittish** | runs (or flies) off when she comes near | rabbits, squirrels, foxes, sheep, ducks, iguanas |
| **Friendly** | comes up to her and follows her a while | dogs, cats |
| **Defensive** | warns her off when she comes near (a "!" over it, its hiss or growl), goes for her if she stays or hits it | geese, swans, bees |
| **Hostile** | goes for her on sight | (placed: a hostile bear, an angry swarm) |

They are hit, locked on to and shown with a health bar as the dummies are, and
their blows are dodged, parried and blocked as the flying dummy's are. Hit a
bee and the bees round it come too. A tame animal beaten - the horse, the
dog, the cat - **runs off**: it is not killed, it flees, fades and is gone
until the herd is next made up. The rest are **game** (`ambient/Hunting`). The bigger ones (a horse, a cow, a bear) are
in her way, as the dummies are; she rolls through them.

Each makes its sounds where it is (Pause → Sounds → Creatures): the horse's
neigh and snort, the cow's moo, the bear's growl and roar, the goose's honk,
hiss and wingbeats, and the rest.

### Hunting

Hunted down, an animal that is game **falls** and leaves what it gives where
it lies, to pick up (`world/Hunted`): **meat** to eat - raw for now, giving
back a little health and stamina (cooking comes with the crafting) - and
the **ingredients** of the crafting to come, which are not eaten:

| Animal | Leaves |
|---|---|
| cow | raw beef 2 – 3, a cow hide |
| deer | raw venison 2 – 3, a deer hide, 0 – 2 antlers |
| pig | raw pork 2 – 3 |
| sheep | raw mutton 1 – 2, wool 1 – 3 |
| bear | bear meat 2 – 4, a bear pelt |
| fox, rabbit | raw game, a fox's or a rabbit's pelt |
| squirrel | raw game |
| the birds | raw fowl (the bigger ones) and feathers - an eagle's, a peacock's, a macaw's and a flamingo's their own |

They go in the inventory in stacks of 20, as the foraged pickings do, and
are drawn - lying about and in the inventory - as six turning pickups
(3D-Modeling's `items/item_spin_sprites.py`) painted for each kind: a cut of
meat, a drumstick, a hide rolled and tied, a feather, an antler and a tuft
of wool.

<p align="center">
  <img src="docs/screenshots/hunted-pickups.png" width="98%" alt="The six hunted pickups turning: a cut of meat, a drumstick, a rolled hide, a feather, an antler and a tuft of wool">
</p>
<p align="center">
  <img src="docs/screenshots/hunted-inventory.png" width="49%" alt="The inventory holding venison, a bear pelt, a peacock feather, wool, an antler, raw game and a forged sword">
</p>

### Animals in Creative Mode

The **Life** tool's **Animals** page sets how thick each kind lives in the
level (0 to 3 times) and says how many are about. **Place → Life → Animals**
puts down a **herd**: a kind, how many, how far they wander, their nature
(their own, or any of the six) and when they are out (their time, always, by
day, by night) - or, with **One animal, just here**, a single one standing
where it is placed, facing the way it was turned, staying near. Placing a
herd asks **what colours** it comes in (`ambient/HerdColours`): as drawn, the
coats its kind comes in - a horse bay, chestnut, black, grey, white,
palomino or dun; a sheep white, black, brown or grey - or any of the 128
colours, several at once for a mixed herd (each animal one of them). Its
**Size** (0.3 to 3 times) and how much the sizes **vary** make a herd of
foals or of giants. In a level file:

```json
"life": {"animals": {"bear": 0, "horse": 2}}
{"type": "animals", "x": 4, "z": 40, "kind": "horse", "count": 4, "radius": 8, "temperament": "tame", "when": "always",
 "colours": ["#7a4a24", "#ece8e0"], "size": 1, "vary": 0.1}
{"type": "animals", "x": 9, "z": 40, "yaw": 90, "kind": "deer", "single": true}
```

<p align="center">
  <img src="docs/screenshots/creative-animals.png" width="49%" alt="Creative Mode: Life, the Animals page">
  <img src="docs/screenshots/creative-herd.png" width="49%" alt="A herd of four horses placed and selected">
</p>

### Riding

A horse can be ridden with a **saddle** (`world/Tack`): an Uncommon item found
in the chests, kept in the inventory out of the hotbar's way. Walk up to a
horse with one and press **E** - *Saddle the horse* - and it has the saddle on
(its own sheets, `horse_saddled`: the red pad, the leather seat, the stirrups,
the bridle and the reins). A saddled horse waits for her: it never runs from
her, stays within a few metres of where she left it, and is never sent away
with the animals that come and go, however far she goes.

**E** by it - *Ride the horse* - and she hops up into the saddle. On it:

<p align="center">
  <img src="docs/screenshots/riding-walk.png" width="49%" alt="Riding at a walk in the laced dress, its skirt over the horse's side">
  <img src="docs/screenshots/riding-gallop.png" width="49%" alt="At the gallop, up in the stirrups, the cape streaming back">
</p>

| | |
|---|---|
| **W A S D** | steer: it turns toward the way you push, no faster than a horse can (wider at a gallop), slowing round a sharp turn and turning near on the spot to go back |
| (nothing) / **Shift** / **Ctrl** | a walk (1.9 m/s) / a canter (6 m/s) / a gallop (9.5 m/s); it gathers itself up over a second or so and pulls up quicker |
| **E** | get off, to its left - or its right, or behind it, wherever there is ground to stand on |

It goes up what she could walk up and never off a ledge or into deep water;
uphill and through water it is slower. The trees, the rocks, the buildings
and the other animals keep it out by its body, not her feet.

She sits it in two states of her own, **ride** (at a stand and a walk: the
reins low in both hands, the heels down in the stirrups, the hips going with
its back) and **ride_run** (the canter and the gallop: up in the stirrups,
leant over its neck). They are rendered in Blender sitting the saddled horse
itself - it hides her far leg, and her skirt and her cape fall over its back
and its sides - and played frame for frame with its sheet: its clock is hers,
and her sprite stands where its `ride.json` says the seat is in that frame,
so she rises and falls with it. Its hooves sound under her, a step at a walk
and a stride at a canter.

In Creative Mode, a herd of horses can be placed **saddled** (Place → Life →
*Saddled horse*, or the herd's *Saddled* switch), and the saddle placed as an
item (Place → Life → *Saddle*):

```json
{"type": "animals", "x": 4, "z": 40, "kind": "horse", "count": 1, "radius": 2, "when": "always", "saddled": true}
{"type": "item", "x": 2, "z": 38, "item": "saddle"}
```

<p align="center">
  <img src="docs/screenshots/riding-side.png" width="49%" alt="Sitting the walk, from the side: the cape over its rump">
  <img src="docs/screenshots/creative-saddled-horse.png" width="49%" alt="Creative Mode: a saddled horse and a saddle placed, the herd's Saddled switch on">
</p>

## Buildings

Twelve buildings stand in the world as **3D models in pixel art**: each is
built in code from its kit (3D-Modeling's `buildings/buildings.py`), its
model written to `assets/buildings/<kind>/mesh.bin` (the script's `mesh`
step, no Blender needed) and drawn in the round (`building/ModelRenderer`) -
every face in its part's colours, banded light, middle or shadow by how it
faces the light, its pattern laid over it a texel at a time, a dark line
along every edge where it turns a corner or meets another part - so it is the
same pixel art from wherever the camera looks. The homes and
mills: a **cottage**, a **town house**, a **log cabin**, a **windmill** (its
sails turning) and a **watermill** (its wheel turning). The castle's pieces: a
**keep**, a length of **castle wall**, a **gatehouse** (its portcullis up), a
**round tower**, a **square tower**, a **turret** and a **spire**.

<p align="center">
  <img src="docs/screenshots/buildings-homes.png" width="49%" alt="The cottage, town house, log cabin, windmill and watermill">
  <img src="docs/screenshots/buildings-castle-pieces.png" width="49%" alt="The keep, castle wall, gatehouse, round and square towers, turret and spire">
</p>
<p align="center">
  <img src="docs/screenshots/buildings-village.png" width="49%" alt="The sample village: two cottages and two town houses, each dressed differently, a small castle behind">
  <img src="docs/screenshots/buildings-castle.png" width="49%" alt="The small castle: keep, curtain walls, four round towers and the gatehouse">
</p>
<p align="center">
  <img src="docs/screenshots/buildings-3d.png" width="70%" alt="Two town houses as 3D models from close by, the camera looking down past them: their walls, timbers, windows and roofs keep their shape at any angle">
</p>

**Place → Buildings → Small castle** puts a whole castle down at once - a keep
in a square of curtain walls, a round tower at each corner with its door to the
courtyard and the gatehouse in the front wall - and each piece is then a
building of its own, to move, turn, dress or take away (`building/Prefab`).

**Any colour and any pattern, part by part**, as walls are done in the Sims.
Every face of a model knows which **part** it is (the walls, the side walls,
the roof, the trim, the door, the shutters, the windows, the foundation, the
chimney, the beams, the logs, the sails...) and where on the part it lies.
The watermill's door and its lower window sit in its stone base, under the
timber floor above. The game colours each part with any of the
128 colours - drawn in six shades of the palette, dark to light, as the rest
of the pixel art is - and lays any of 20 **patterns** over it: brick, stone
blocks, fieldstone, cobbles, flagstones, plaster, clapboard, logs, timber,
boards, floorboards, thatch, slate, clay tiles, shingles, window panes, metal,
woven reeds, checker and smooth. No rendering again: a new look is a click.

<p align="center">
  <img src="docs/screenshots/creative-building-look.png" width="49%" alt="Creative Mode: a cottage selected, its Look section with the part, the 128 colours, its shades and its pattern">
  <img src="docs/screenshots/buildings-night.png" width="49%" alt="The village at night, its windows lit">
</p>

In Creative Mode, select a building (or choose one to place) and its **Look**
section has the **Part**, the 128 swatches, the **Shades** it will be drawn
in, its **Pattern** and **As it comes** (every part back as the building
comes).

**Solid.** A model is drawn at its true depth, so she goes behind the corner
of a house and walks in front of the rest of it, a roof hides her when the camera looks over
it, and the trees, the items and the animals sort against it the same way. Its
footprint is solid: she walks round it, and the animals go round it. On a slope
it is set into the hill down to its lowest corner, never standing off the
ground; a soft shadow lies round its walls. **At night** its windows are lit
from inside, warm, from dusk to dawn.

**Walls in the way**, as in the Sims, set apart **indoors** and **outdoors**
(Pause → Settings, and the editor's Level tool): **Up** (always whole),
**Cut away** (the walls between the camera and her drop to a stub, so the
house she walks behind never hides her) or **Down** (every wall a stub). A
selected building in the editor is **outlined**, not tinted.

**Ways through.** Any building or piece of furniture can be a door of its own -
a bookcase that is a secret door, a well that
goes down: its **Way through** goes to a named spot, up a floor, down one or
outside, and **Hidden way** keeps it quiet (*look closer*) until she is right at
it. **E** at a **wardrobe** opens the wardrobe, to change what she wears.

In a level file a building is its kind and the parts changed from how it comes:

```json
{"type": "building", "x": -15, "z": 16, "yaw": 270, "kind": "cottage",
 "look": {"walls": {"colour": "#e8c0b0"}, "roof": {"colour": "#707888", "texture": "slate"}}}
```

`assets/levels/village` is a sample to play and to open in the editor: the two
cottages and the two town houses dressed differently, the log cabin, both mills
and the small castle; `assets/levels/building_lineup` has all twelve in two
rows (the town's seven are below). A new building is a class in 3D-Modeling's
`buildings.py`; its model is written into `assets/buildings/` by the `mesh`
step (`python3 buildings/buildings.py mesh <Larsons-Game>/assets/buildings`, no Blender), and
a small thing drawn from views instead (a pot) is rendered (`passes`) and
written (`draw`): the palette lists it from its folder.

### The town

The town has its own buildings (Place → **Town**, all 3D models): a **market
stall** (a canopy over its counter, sacks and crates set out) and a **striped
stall** (a red and cream awning, a hanging sign), a **narrow town house** (a
stone ground floor, two timber-framed floors over it, slate roof), a **shop
house** (a shop window either side of the door under a striped awning, its
sign, rooms over it), and the steampunk works: a **steam workshop** (brick,
iron lintels, a great gear turning in its gable, pipes to a banded
smokestack), a **clock tower** (four clock faces, a steep slate roof and a
brass finial) and a **foundry** (furnace light glowing in its tall windows
and the lantern along its ridge, two tall chimneys). Each comes furnished
inside: the shop house has a **shop** downstairs - a counter and its shelves
of jars, a keeper's spot to buy and sell at - the workshop its boiler,
lightning coil and alchemy table, the foundry its cauldrons, boilers and
still.

<p align="center">
  <img src="docs/screenshots/town-square.png" width="49%" alt="The sample town by day: the two market stalls in the square, narrow town houses, the shop house, the clock tower, the workshop and the foundry">
  <img src="docs/screenshots/town-night.png" width="49%" alt="The town at night: windows lit, the lantern posts and the foundry glowing">
</p>
<p align="center">
  <img src="docs/screenshots/town-houses.png" width="49%" alt="The three narrow town houses, each dressed differently, the shop house and the clock tower">
  <img src="docs/screenshots/shop-house-inside.png" width="49%" alt="Inside the shop house: its counter and scales, E to shop at it, the stairs, a rug, a crate and a barrel">
</p>
<p align="center">
  <img src="docs/screenshots/workshop-inside.png" width="49%" alt="Inside the steam workshop: its lab gear, a table and stools, crates and barrels">
  <img src="docs/screenshots/foundry-inside.png" width="49%" alt="Inside the foundry: cauldrons bubbling, boilers, a still and crates">
</p>

`assets/levels/town` is a sample to play and to open in the editor: a market
square with both stalls selling (supplies at one, cards at the other), pots,
barrels and crates round them to break, lantern posts, three narrow town houses
dressed differently, the shop house, the workshop, the clock tower and the
foundry.

The new furniture (Place → **Inside**): a **shop counter** (with its scales
and a wall of shelves), a **rug**, a **painting** and a **banner** (both hung
on a wall), a **tall clock**, a **potted plant**, the **pot** and **large
pot** (they hold things, and shatter); and the laboratory's: an **alchemy
table** (flasks glowing green and violet, a retort over its burner), a
**steam boiler** (its firebox aglow, a gauge, its pipe), a **lightning coil**
(sparks off its ring), a **copper still** and a **cauldron** bubbling over
its fire.

### Going inside

Every home, mill and castle piece with a door can be **gone into**: walk up to
its door and **E** (*Go into the cottage*). Inside she is in its rooms - lit by
their candles, lanterns and the fire in the hearth, dim and warm, a little
lighter by day with the daylight in the windows - with the walls between the
camera and her **cut down to a stub**, as in the Sims, so the room she is in is
seen into from above like a dolls' house. **E** at a staircase's foot goes up a
floor, at a stairwell's top down one, and at the **door out** back outside, to
the building's door, the world outside just as she left it. A floor she goes
back to is as she left it too: what she dropped, the chests she opened.

<p align="center">
  <img src="docs/screenshots/inside-door.png" width="49%" alt="She stands at a cottage's door: E Go into the cottage">
  <img src="docs/screenshots/inside-cottage.png" width="49%" alt="Inside the cottage: the bed, the fire, the table and chairs, a cat, the front wall cut away">
</p>
<p align="center">
  <img src="docs/screenshots/inside-keep-hall.png" width="49%" alt="The keep's great hall: flagstones, stone walls, the long tables, two dogs">
  <img src="docs/screenshots/inside-upstairs.png" width="49%" alt="The town house's second floor: the bedroom wall with its doorway, the beds, a bookshelf">
</p>

Each kind comes **furnished** (`level/Interiors`): the cottage one room with a
bed, a fireplace, a table and chairs and a cat; the log cabin with a dog; the
town house its kitchen downstairs and its bedrooms up; the keep its great hall,
its chambers and its armoury on three floors (chests to open up there); the
mills, the towers, the gatehouse, the spire and the turret theirs. Ten
pieces of **furniture** are 3D models as the buildings are: a bed, a table, a
chair, a bench, a stool, a bookshelf, a cupboard, a wardrobe, a fireplace
(its fire always burning, lighting the room) and a counter - each dressed
part by part (a bed's frame, blanket and sheets) as a building is. Furniture
up to 2 m tall is stood on and climbed onto ([Climbing onto
furniture](#climbing-onto-furniture)). The **barrel** and the **crate**, which
are opened and broken ([Barrels, crates and storage
chests](#barrels-crates-and-storage-chests)), stay sprites drawn from eight
directions and three heights, as the chests do.

**Light fittings**: a light placed in Creative Mode (Place → Light) has a
**Fitting** - a candlestick, a candle, a candelabra, a standing candelabra, a
chandelier, a wall sconce, a lantern, a lantern post, or its glow alone -
drawn as the furniture is, its flame where the light is.

The rooms are built of **pieces** (`building/Room`): a **wall** as long and as
high as it is set, plain or with a **doorway**, a **window** or an **archway**
in it, its two sides each their own colour and pattern and its trim another; a
**floor**; **stairs up** and **stairs down**; and the **door out**. They are
drawn as boxes, not views (`building/RoomRenderer`), in the same 128 colours
and patterns, outlined and shaded as the pixel art is.

<p align="center">
  <img src="docs/screenshots/creative-edit-inside.png" width="49%" alt="Creative Mode inside the town house's second floor: the floors along the top, a bed selected and its blanket coloured blue">
  <img src="docs/screenshots/inside-tower-night.png" width="49%" alt="A round tower's ground floor at night, lit by its lantern">
</p>

**In Creative Mode**, select a building and **Edit inside**: its floors open in
the editor - the walls, floors, stairs, furniture, chests, lights, animals,
anything from the palette - with **Place → Inside** holding the room pieces and
the furniture. Along the top: the floor shown, **<** and **>** (or
Shift+PgUp / PgDn) to go between floors, **+ Floor** (a floor on top, with the
top one's walls and a stairwell over each staircase) and **- Floor** (the top
one, pressed twice); **Play (F5)** starts her right there, inside; **Outside**
(or Esc) goes back out. It is all saved with the level, under the building's
number:

```json
"interiors": {"20": [{"name": "Ground floor", "objects": [
    {"id": 1, "type": "floor", "x": 0, "z": 0, "yaw": 0, "width": 8.2, "depth": 6.2},
    {"id": 2, "type": "wall", "x": 0, "z": -3, "yaw": 180, "length": 8.2, "height": 3, "opening": "window"},
    {"id": 9, "type": "exit", "x": -1.2, "z": 3, "yaw": 0},
    {"id": 14, "type": "building", "x": -2.6, "z": -2.0, "yaw": 180, "kind": "bed"}, ...]}]}
```

A building whose inside has not been edited is as its kind comes.

## Creative Mode

Creative Mode is the game's level editor - the platform the game's real areas
are built on. Type **1025** on the main menu to show its **Dev** tab (typed
again, the tab hides; it stays shown between runs). There: **New level**, **Copy
the demo** (the meadow and the highlands as a level, everything in them
editable), and every saved level with **Edit**, **Play**, **Copy** and
**Delete**.

<p align="center">
  <img src="docs/screenshots/creative-editor.png" width="49%" alt="The level editor placing an oak by a sculpted hill, the palette on the left and the oak's properties on the right">
  <img src="docs/screenshots/creative-backdrops.png" width="49%" alt="A level's own sky: snow-capped mountains, rolling hills and a tree line round the horizon">
</p>
<p align="center">
  <img src="docs/screenshots/creative-demo-copy.png" width="49%" alt="The demo copied into the editor: its paths, trees, dummies, chests, weapons and named spots">
  <img src="docs/screenshots/creative-menu.png" width="49%" alt="The main menu's Dev tab with a saved level">
</p>

The editor shows the level as it will be played - the ground, the scenery, the
items turning, the dummies, the chests - with what you are about to place
standing under the pointer. Twelve tools, ten of them on the number keys:

| Tool | What it does |
|---|---|
| **1 Place** | the palette: every tree, rock and tuft, every weapon and wearable, the chest, the **buildings** (and the small castle, all its pieces at once; each dressed part by part in its **Look** section, and made wider, deeper or taller with **Width ×**, **Depth ×**, **Height ×**), the furniture in its own tabs - **Furniture**, **Kitchen, bath**, **Decor**, **Plants**, **Farm**, **Clutter** ([Furniture, decor and clutter](#furniture-decor-and-clutter)) - the training and flying dummies, climbing vines, sounds, **lights** (a colour - lantern, candle, fire, moonlight, crystal, magic, fairy, rose, white -, how far it reaches, how bright, how high, glowing or not) and **light rays** (shafts of sunlight in the mist, for clearings and windows), the player's spawn and named spots, the barrels, crates and storage chests and what each **Holds**, the [forged weapons](#forged-weapons), and the **templates** saved. A tree, rock or tuft has a **Size** (0.2 to 5 times) and a **Tint** (any of the 128 colours, how strongly); a light its **Fitting**; a herd its colours, size, or one animal alone. Click to place; R / Shift+R turn 15°, Q / E 45°, PgUp / PgDn lift, G snaps to a half-metre grid. Something small - clutter, a potted plant, an item for show - placed with the pointer over a table, a counter or a shelf's top is set on it. A **wall** or a **floor** is drawn as in the Sims: press where it starts and let go where it ends (a wall end to end, straight or at 45° - Shift: any angle; a floor corner to corner, with **Walls round it** on: its four walls too); a click places one as it is set. **Right-click** (without dragging) anywhere switches to a plain pointer, to select, move and edit without leaving the tool; right-click again, or Esc, to go back The right panel sets what is placed next: its facing, lift and own settings (a dummy's health, an item's tier, a tree's sprite, a sound) |
| **2 Select** | click, Shift+click to add, drag on the ground to box things in; drag to move, R / Q / E to turn, Del to delete, Ctrl+D duplicate, Ctrl+C / Ctrl+V copy and paste (at the pointer), Ctrl+A everything, F flies to it. The right panel edits what is selected (more than one, it counts them by kind). A building's **Look** has **Copy look** and **Paste look**: its colours and patterns onto another building part by part (walls to walls, roofs to roofs - [Copying a look](#copying-a-look)), or onto all of those selected at once. A room's floor has its **Width** and **Depth**: with **Walls follow** on, its walls move with its edges and what stands against them goes with them **Save as a template** keeps what is selected - a cottage and its furnished rooms, a market stall and its barrels, a fenced paddock and its horses - in `assets/templates/`, to place again from **Place → Templates** in any level, turned as you like |
| **3 Sculpt** | hold to raise, lower, smooth or flatten the ground (Shift: the other way; Ctrl+click picks the height to flatten to). **Speed** is how fast it works, **Feather** how soft its edge is (0 a hard edge, 1 soft all the way in). Steeper than 45° is rock she cannot walk up - put vines on it to climb. The level's edge stays at the height of the flat ground round it. A **round** or a **square** brush (a plateau with straight sides, a square pit) |
| **4 Paint** | **Ground**: any of the sixteen grounds - dirt, forest floor, gravel, flower meadow, sand, mud, snow, stone, jungle floor, dry grass, ash, crystal, and for roads and paths **cobbles**, **flagstones**, a **brick path** and a **packed path** - over the grass. **Biome**: one of the nine biomes - its ground with patches of its second ground worked through it, and (**Sow its plants**, on by default) its plants, trees and rocks sown as you paint. Shift paints back to grass. **Paint over rock faces** shows the paint on ground too steep to walk too (Bare rock takes it off again). **Tint**: any of the 128 colours over the ground, keeping its pattern - autumn's gold, a blighted grey - Shift takes it off |
| **5 Scatter** | grass, trees or rocks - one sprite, the meadow's mixed, a biome's plants, trees or rocks, or **a brush of your own** (★: the sprites you pick, how often each, the sizes they come in, how far apart, how thick; **Save brush** keeps it in `assets/brushes/` for every level) - sown under the brush as the demo has them: trees 2.8 m apart, nothing inside a trunk or a rock, nothing on ground too steep, nothing in water but reeds. Shift pulls up what you are sowing |
| **6 Sky** | the colour overhead and at the horizon (the fog's colour), from the 128-colour palette; how far the fog reaches; and up to eight **distant backgrounds** round the horizon - mountains (snow on the peaks or not), rolling hills, a tree line, towers and spires, the sea - each with its colour, height, haze, an arc of the horizon or all the way round, and a seed for a different range; each in **two parts** with a colour of its own - the rock and its **snow** (how far down the snow line comes), grass and shade, leaves and **trunks**, walls and **roofs**, water and glints - and **Stands** far off at the horizon or **round the level's edge** (a ring of mountains just past where she can go). Drag one up or down the list (or ↑ / ↓) to put it in front of or behind the others **Over it**: the open sky, inside (a hall) or a cave ([Halls and caves](#halls-and-caves)) |
| **7 Time** | the time of day and the weather: the hour the level starts at (Dawn, Noon, Dusk, Night), how many minutes a day lasts (0: the time stands still) and **Run it here** to watch the day go by in the editor; the planet - where it hangs, how high, how big, its bands, rings or not -; and how cloudy, starry and misty it is, the aurora, how high the mist reaches and the light rays; and the [weather](#weather) - none, by biome or always one, how heavy and how often |
| **8 Level** | its name, its size (64, 128, 192, 256, 512, 1024 or 2048 m a side: a great forest, its ground on a coarser grid past 256 m), **Walls indoors** and **Walls outdoors** - Up, Cut away (those between the camera and her drop away) or Down (the game's own setting too, in Pause → Settings), its music (a song, Shuffle all, or None) with a player to listen first (play, pause, a timeline to drag), save, play, back to the menu |
| **9 Water** | clear, swamp, sea or crystal water, how deep, and either **lying level** from where you press (a pond: sculpt a hollow, fill it from its bottom) or following the ground down (a stream); **Flows** makes it run the way the brush moves, at the current you set. **Fill to brim** fills the hollow you click from its bottom to where it would spill over (a pond in one click); **Fill to depth** fills it that deep. **The sea**, on or off, and its level. Shift dries it up |
| **0 Life** | how thick the critters and each kind of animal live here, how fast the bushes grow back and what they give |
| **Forge** | [the Forge](#the-forge): weapons of your own - their dice, ammunition and upgrade tiers |
| **Cards** | every card of every class: its name, what it says, its rarity, where it is had (a starting deck, the perk tree, shops), its price, how many are in the class's first deck, and what it does, effect by effect - a help for so long or so many blows, on which blows, or something at once; **New card**, **Delete**, **Save** (`assets/cards/cards.json`). The right panel shows the card as it looks and edits the class's perk tree: each node's cost in points, its row, the card it gives |

`[` and `]` (or Shift+wheel) size the brush, Shift+`[` / `]` its strength.
**Filters** (Sculpt, Paint and Scatter alike, as World Painter has them) keep a
brush to ground between two slopes, between two heights, only on (or never
on) one ground, and on dry ground or under water only; each edge fades over a
degree or two, so the work never stops in a step.
Everywhere: **WASD** fly, **Space** / **C** up and down (Shift faster),
right-drag or the arrow keys turn the view, the wheel zooms, **Tab** steps
through the camera heights, **H** hides the panels, **Ctrl+Z** / **Ctrl+Y**
undo and redo (sixty steps), **Ctrl+S** saves, **Esc** goes back to the menu
(asking first when there is something unsaved).

**F5 play-tests** the level from the player's **spawn** - where she starts
when the level is played (**Shift+F5**: from the pointer) - the whole game,
fighting, chests, climbing and all. **F5** again, or the pause
menu's **Back to the editor**, returns to the editor as you left it.

### Furniture, decor and clutter

The furniture is filed by what it is. **Furniture**: beds, tables, chairs,
side tables, nightstands, armchairs, a settee, a rocking chair, footstools,
chests of drawers, a writing desk, tall shelves, shelves on a wall, a coat
rack. **Kitchen, bath**: an iron stove, a bread oven, a sink and pump, a
butcher's block, pots and pans and a plate rack for the wall, pantry shelves,
a bathtub, a washstand, a privy seat, a towel rail, a mirror. **Decor**:
mounted deer, boar and bear heads, a shield and swords, an armour stand, a
vase of flowers, and a **weapon rack** - hang weapons on it (in Creative Mode
in its **Holds**, in the game by looking in it with **E**) and they stand on its
pegs to be seen. **Plants**: small potted plants, pots of flowers, a cactus, a
fern in an urn, a little tree in a tub, hanging planters and window boxes.
**Farm**: hay bales, round bales, a haystack, a water trough, an animal pen,
fields of wheat, cabbages and pumpkins, a scarecrow, a chicken coop, beehives,
a hand cart. **Clutter**: piles of books, plates and cups, bottles, sacks of
grain, a basket of apples, firewood, a bucket, a broom, farm tools, a keg on a
stand, a pile of gems, a bowl of berries, meat on a board - and **anything of
the game's own set out for show**: bear meat on a counter, berries, a crystal
on a shelf, a sword on a table (*… (for show)*, or **Any item, for show** and
its **Item**). It stands still, turned as it is placed, without its glow, and
is never picked up. The town gains a **stable**, an **outhouse**, a **wishing
well**, a **fountain**, a **statue**, a **notice board**, **stocks**, a
**market cross** and stone **flower beds**; and a **water wheel** stands on its
own, turning, by any stream.

Under **Scenery** are three **tall trees** as tall as the buildings - a **great
oak** (10 m), a **tall pine** (13 m) and a **tall birch** (11 m) - drawn at the
same pixel as every other tree rather than an oak blown up with **Size**.

<p align="center">
  <img src="docs/screenshots/editor-tall-trees.png" width="49%" alt="The great oak, the tall pine and the tall birch standing over a cottage">
  <img src="docs/screenshots/editor-weapon-rack.png" width="49%" alt="A weapon rack holding a sword, a longbow, a battle axe and a mace, beside an armour stand">
</p>

Barrels, crates, pots, large pots and the storage chest are 3D models, opened,
filled and broken as before; a loot chest is the sprite it always was.

<p align="center">
  <img src="docs/screenshots/editor-furniture.png" width="49%" alt="The new furniture, kitchen, bath, decor, plants and clutter in rows, a pile of gems and bear meat set out on a table">
  <img src="docs/screenshots/editor-farm-town.png" width="49%" alt="The farm and town pieces: a stable, an outhouse, a wishing well, a fountain, a statue, hay, fields, a pen and a water wheel">
</p>

### Copying a look

Select a building and **Copy look** in its Look section; select another (or
several) and **Paste look**: each of its parts takes the colours and pattern of
the part that does the same job on the first - walls to walls, roofs to roofs,
doors to doors, trim to trim - whatever the two are called.

### The Forge

The **Forge** tool makes weapons of your own ([Forged
weapons](#forged-weapons)). **Working on** picks one made before or **A new
weapon**; then its **Name**, its **Weapon** (any of the eight: its look, stance
and moves), its **Rarity**, its **Dice** - how many and which: **d4, d6, d8,
d10, d%, d12** or **d20** - and its effects, **Venom**, **Mystic** and
**Bonded**, any or all of them. **Colour** chooses what the 128 swatches
colour: **All of it**, one of its parts (a sword's blade, fittings and
handle; an axe's handle and blade; a bow's body and string ...), or **The
dice**; **As it is drawn** / **The rarity's colour** takes a colour off. The
right panel shows it as it will be: turning in its colours, its name in its
rarity's, what it rolls and does, and its dice come up.

The **Dice & ammo** tab adds up to four more groups of dice, each with what
it does to the total (**+ − × ÷ %**), how many and of what, and how many
blows its ammunition lasts. The **Tiers** tab sets how many [upgrade
tiers](#upgrade-tiers) it has (1 to 10), and for each its name, rarity, dice,
what it costs in upgrade ingots, how long its ammunition lasts, whether it is
a mystery, and its colours.

<p align="center">
  <img src="docs/screenshots/creative-forge.png" width="49%" alt="The Forge: Duskfang, an Epic sword with a purple blade, 3d8 in blue, Venom and Mystic">
  <img src="docs/screenshots/creative-forge-mace.png" width="49%" alt="The Forge: Ember maul, a Legendary mace all red, 2d% in red, Bonded">
</p>

**Save** keeps it in `assets/forge/<name>.json`, for every level; **Place it**
saves it and takes up placing it (it is in **Place → Weapons** from then on);
**Delete** throws it away. In a script: `tool forge`, `forge new <name>`,
`forge base mace`, `forge rarity mythic`, `forge dice 3`, `forge sides 8` (or
`%`), `forge effect venom on`, `forge tint blade #b722e7`, `forge dicecolour
#5d86fa`, `forge save`, `forge place`.

### Levels on disk

A level is a folder of `assets/levels/`, meant to be read and to sit in git:

```
assets/levels/<level>/
  level.json    its name, size, sky and backdrops, music, and every object - one a line
  height.png    the sculpted ground, 16-bit grey: a height every 0.5 m (value / 256 - 128 m)
  ground.png    the paint, a pixel every 0.25 m: red dirt, green forest floor, blue gravel, alpha meadow
  ground2.png   the same for sand, mud, snow, stone (only when any is painted)
  ground3.png   the same for jungle floor, dry grass, ash, crystal
  water.png     the painted water, a pixel every 0.5 m: its surface's height in red and green (16 bits,
                value / 256 - 128 m), its kind (1 clear, 2 swamp, 3 sea, 4 crystal) and the way it flows
                (sixteenths of a turn, << 3) in blue, how fast in alpha (of 3 m/s)
```

```json
{
  "format": 1,
  "name": "Lakeside",
  "size": 128,
  "sky": {"zenith": "#4A6FA5", "horizon": "#BDC9DB", "fog": 70, "backdrops": [...],
          "time": {"start": 10, "day": 24},
          "planet": {"show": true, "azimuth": 200, "elevation": 16, "size": 26, "rings": false, "seed": 1},
          "clouds": 0.35, "stars": 0.85, "aurora": 0.35, "mist": {"thick": 0.25, "height": 2.5}, "rays": 0.6},
  "sea": -1.5,
  "objects": [
    {"id": 1, "type": "spawn", "x": 0, "z": 0, "yaw": 0},
    {"id": 2, "type": "scenery", "x": 6.5, "z": -8, "yaw": 37.5, "sprite": "oak_tree"},
    {"id": 3, "type": "item", "x": 1.4, "z": -2.2, "yaw": 0, "item": "sword", "tier": "iron"},
    {"id": 4, "type": "dummy", "x": 3, "z": -2, "yaw": 200, "health": 100},
    {"id": 5, "type": "sound", "x": -12, "z": 8, "yaw": 0, "sound": "ambient/stream"},
    {"id": 6, "type": "light", "x": 4, "z": 9, "yaw": 0, "colour": "lantern", "radius": 5, "strength": 1}
  ]
}
```

A level without `time`, `planet` and the rest gets the demo's day. The images are left out while the ground is flat and all grass. Keys and kinds
of object this game does not know are read and written back as they were, so
a level made by a newer game loses nothing in an older editor.

### Adding a kind of thing

Every kind of object - and every kind still to come: NPCs with their dialogue,
quests, enemies and their summon stations, biomes' critters - is one
registration in `level/ObjectTypes`:

```java
// for example, once there are NPCs:
ObjectTypes.register(new ObjectType("npc", "Villager", Category.MARKERS,
        List.of(Prop.text("name", "Name", "Villager"), Prop.text("dialogue", "Dialogue", "village_elder")),
        0.4, 1.7, 0xF0C080, false,
        (o, b) -> b.world().addNpc(new Npc(o.str("name", "Villager"), b.onGround(o), o.yaw()))));
```

Its props are edited in the editor's right panel and saved in `level.json`
with no more work; its spawner says what it is when the level is played. The
palette, the picking, undo, copy and paste, and saving take it as it is.

## A playthrough: the main menu, saves and the map

The main menu has four tabs: **Play**, **Horde**, **Cutscene** and **Dev**
(the Dev tab shows once 1 0 2 5 is typed on the main menu; typed again, it hides).

**Play** has three save slots. *New game* in an empty slot opens the
**character creator**; *Continue* takes up the slot saved to last.

- **The character creator** (`scene/CreatorScene`) has four pages: *Who* (her
  name, her people - Human, Drurinean, Half Drurinean or Khorigale - her
  body, her skin and how tall she stands, 0.7 to 1.3 of the usual height),
  *Look* (the wardrobe: face, hair, the clothes she sets out in), *Class*
  (the nine classes, each with its deck and its first weapon) and *Begin*.
- **The overworld map** (`scene/OverworldScene`) is walked like the map of a
  platform game: the move keys, the arrows or the stick take the road that
  sets off that way, a click on a destination walks her there. **Use**,
  **Jump** or **Enter** loads the destination's level at its spawn. The
  roads on from a destination are shut until it is **cleared** - by going
  there, by reaching the level's far way out, by felling its foes or by
  handing in its quest, as the map says - and she can always go back the
  way she came.
- **Saving** (`save/SaveGame`, `save/Saves`): her place on the map is
  written each time she arrives somewhere, and all of her - what she
  carries, her purse, her class, cards and level, her quests, the doors she
  has opened - when she comes back from a level. The slots are
  `config/saves/slot1.json` to `slot3.json`.

The game comes with one map, **the Brass Vale** - the first of the main
lands - and its eleven levels (`assets/overworld/maps/brass_vale.json`,
`assets/levels/`): Oakhollow (home), Miller's Crossing, Brasswick, the
Whispering Wood, Fisher's Rest, Copperpit Mine, the Drurin Observatory, the
Khorigale Camp, the Sallow Marsh, the Old Watchtower and Crystal Hollow,
with the East Gate leading on. `./gradlew makeBrassVale` writes the levels
again from `tools/BrassVale.java`; `tools/overworld/make_main_lands.py`
writes the map.

**The map editor** (Dev tab, or `-Dlarsons.start=mapedit`;
`editor/OverworldEditor`): paint the fourteen terrains, place destinations
(a name, an icon, **the level it loads** - any level saved in Creative Mode -
what clears it, whether its roads are open from the start, another map it
leads to), draw the roads between them, and choose where a new game starts.
*Try it* walks the map as a playthrough would, with nothing saved.

## People, foes and quests

**Peoples** (`actor/People`, `actor/Looks`): Humans, Drurineans (tall, long
ears), Half Drurineans, Khorigale (horned and long-tailed, a little
smaller), Bortzal (goblin yellow or ashy purple, half a person's height to
half as tall again) and the Risen. A person's look - skin, hair, face and
clothes in their people's way of dressing - comes from a seed, the same
every time. The Khorigale's horns and tails are sprite layers of their own
(`tools/sprites/make_khorigale.py`).

**Foes** (`actor/Breed`, `actor/Folk`, `actor/Folks`): Bortzal, Drurineans
(wands and staffs), Half Drurineans (crossbows), Khorigale, bandits, six
kinds of Risen (walker, runner, brute, crawler, spitter and the Risen
Bortzal), and from the sky the Vérnees serpents, which come through
portals. They notice her by sight, close in, strike, loose arrows and
spells, guard and dodge; wild boar and buffalo charge. In a level they are
placed in Creative Mode (the Foes shelf: a foe, a **summoning station** that
calls a band when used, a **sky portal**), each with its breed, size
(0.5 to 1.5), weapon, name and health.

**People** to talk to are placed the same way (the Story shelf): a name, a
people, an outfit, what they say when greeted, **topics** to ask about
(`Topic=Answer; Topic=Answer`), and - for a shopkeeper - a stock and
whether they buy.

**Quests** (`story/Quest`, `story/Quests`) are objects of a level too: who
gives it, what kind, and what it pays (items, coins and experience).

| Kind | Done when |
|---|---|
| **Fell something** | so many of a kind - or someone by name - are felled, anywhere or inside the quest's ring ("a large blue bear in the Whispering Wood") |
| **Clear the area** | no foe is left inside its ring (or in the level) |
| **Talk to someone** | she has talked to the one it names |
| **Bring something** | she has so many of an item to hand over |
| **Get to the area** | she stands inside its ring |

A quest with an **id** is one quest wherever it is told: the Reeve of
Brasswick's word for the Magister is given in Brasswick, done at the
observatory and handed in back in Brasswick. A quest can wait for another
(*Only after the quest*). **U** opens the journal; the quests under way are
tracked at the side of the screen, and their places are marked in the world.

## Goods, crafting and brewing

Everything has a rarity, Common (white) to Mythic (cyan). The goods
(`world/Goods`) are new kinds of item beside the weapons and the clothes:

- **Potions** (drunk from the hand: health, mana, stamina, swiftness, might,
  stoneskin, shadow, gills, springheel, fortune, thornblood, the crystal
  elixir) and **scrolls** (read: stronger, never made - fury, warding,
  frost, haste, renewal, venom, insight, the storm, return).
- **Keys** (rusty, iron, brass, silver, gold, bone, crystal, and the Mythic
  skeleton key that opens anything) for the **locked doors and gates** of a
  level: each lock says which key opens it, whether the key is used up, or
  a price it opens for instead.
- **Spawner eggs**, one for each animal and foe, hatched where she stands -
  and the Mythic dragon egg, which hatches nothing yet.
- **Fishing rods** (willow, reedcane, brass-reel, clockwork, crystal) and
  the **fish** they catch; **meals**; the **crystal ball**, the **crystal
  shield** and the **drinking horn**; **packs** (satchel, backpack,
  explorer's pack) that add slots to her inventory for good.
- **Makings**: bottles, parchment, ink, twine, hooks, gears, iron bars,
  crystal dust, leather, cloth.

**Fishing** (`scene/Fishing`): with a rod in her hand and water before her,
a click casts; when the float dips, a click in time hooks what took it;
then hold and let go of the attack key to keep the fish inside the catch on
the gauge until the line is in. A better rod gives a longer moment to
strike, a wider catch, and rarer fish.

**Crafting and brewing** (`world/Recipes`): at a **workbench** the makings,
the rods, the packs, keys, ammunition and trinkets are made; at a
**cauldron** the potions and the meals are brewed (a brew bubbles a few
seconds). Both are objects to place in a level. Nothing made sells for more
than its parts cost to buy (a test holds the recipes to that).

**Shops**: a stall, a counter or a person sells what its **stock** line says
(`sword sharper, health potion x5 @20, satchel`): any item in any tier,
rarity or colour, with a count and a price - set in Creative Mode. A shop
that buys pays half what a thing is worth.

## The horde

The **Horde** tab plays a horde map (`scene/HordeGame`): the Risen come in
rounds, more and tougher each round, out of the map's spawns and through
its barricades, until she is knocked out. **Points** are her purse - 10 for
a blow that lands, 60 for a Risen felled, more for a board nailed back on -
and buy the weapons chalked on the **boards** on the walls, a turn of the
**mystery box**, and the **gates** to the rest of the map. The felled now
and then leave a gift: max ammo, insta-kill, double points, kaboom,
carpenter. The best round on each map is kept in `config/horde.json`.

Two maps come with the game (`assets/horde/`): the Foundry Yard and
Gravewood Clearing (`tools/levels/make_horde_maps.py`). The **horde
editor** (the Horde tab's *New horde map* and *Edit*, with the Dev tab
unlocked; or `-Dlarsons.start=hordeedit:<map>`) is Creative
Mode with a Zombies shelf - Risen spawns (which kind, from which round,
behind which gate), barricades, weapon boards (what, which tier, the
price), the mystery box, gates that open for points - and a Horde panel
for the map's rules (`level/HordeRules`: the points she starts with, how
the rounds grow, how many are up at once, her lives, her kit, which Risen
come).

## Cards

**J** opens the **card table** (`scene/CardTable`): the game stands still
behind it and her hand is fanned along the bottom. The card under the
pointer rises to be read; click it, or drag it up off the board, to play
it. The draw pile lies on the left and the discard pile on the right;
*Deck* and *Perks* are along the top. Every card has a **rarity**, Common
to Mythic, shown in its frame, and a background of its class in brass, oak
and parchment (`ui/CardArt`). What her cards are doing for her now stands
down the left of the screen as brass plates with the time left on each.
**Pause → Compendium** shows every card there is, by class and by rarity,
with where it is had. (Cards are no longer edited in Creative Mode.)

## Colour grading

**Pause → Colours** (`grade/Grading`, `scene/ColoursPanel`): each of the
art's 128 colours can be given another, all of them shifted together (hue,
saturation, brightness, contrast, one hue), and the 128 brought down to as
few as two. Presets: Original, Gameboy, Pocket, 8-bit console, Home
computer, Four-colour PC, N64, Red visor, One colour, Greyscale and Sepia.
*Snapped* draws the picture
in the palette and nothing else; *smooth* keeps the light's and the mist's
shades. Kept in `config/colours.json`.

## Controllers and the Controls page

An Xbox-style gamepad works throughout (`input/Pad`, `input/Input`): the
left stick walks, the right stick turns the camera, **A** jumps, **B**
dodges, **X** uses, **Y** spins, **RB** attacks, **RT** is the heavy
attack, **LB** blocks, **LT** parries, the d-pad steps along the hotbar and
opens the cards, **View** opens the inventory and **Menu** pauses. The
prompts on screen follow whichever was touched last, the keyboard or the
pad.

**Pause → Controls** (`input/Bindings`, `scene/ControlsPanel`) lists every
action with two keys and two gamepad inputs each; click one and press what
it should be. A key given to an action is taken from the one that had it
(the pause menu always keeps a key). Kept in `config/controls.json`.

## Performance

**F8** shows the performance overlay, in the game and in the editors
(`core/Perf`): the frame rate, what the update and the drawing take, draw
calls and quads, and what the scene is running - folk, animals, scenery and
how much of it is in view, buildings, lights, sprite sheets and their
memory. `-Dlarsons.perf.log=true` prints the same once a second. (**F7** is
the developer's readout of the sheets on screen.)

What keeps a frame short: ground shadows and scenery outside the view are
not drawn (`math/Frustum`); a character's sheets for what she may do next
are asked for when her state, view or outfit changes, not every frame; a
colour choice's palette swap is made once.

## GPU acceleration

**The whole game renders through OpenGL 3.3 core** (via LWJGL 3.3.3): the ground,
the scenery, every sprite layer and shadow, and the entire UI, text included.
AWT is only used off-screen — to decode PNGs, rasterise the font atlas once,
and generate the fallback sprites.

- Rendering is premultiplied-alpha throughout, and the 512-pixel sheets get
  trilinear mipmaps, so minified characters have clean edges without dark
  fringes. The 32-pixel fallback is sampled nearest-neighbour.
- 4× MSAA and vsync are on by default, and the game holds a steady
  **120 frames a second** - see [below](#120-frames-a-second).
- The renderer reports what it got — shown in the HUD, the Settings page and
  the console. On a software rasteriser (Mesa llvmpipe, "GDI Generic", Microsoft
  Basic Render) it still runs, and says so.

### 120 frames a second

The game aims for **120 fps with every frame the same length** (Pause →
Settings → Frame rate: 60, 120, 144 or uncapped; `-Dlarsons.fps=N` for one
run). The HUD's top line shows the frame rate, the average frame and the
longest frame of the last half second - a hitch shows there - and how frames
are being paced; `-Dlarsons.fps.log=true` prints the same to the console.

- **Pacing** (`core/FramePacer`). Vsync is used wherever it gives the target:
  a 120 Hz display is synced every refresh, a 240 Hz one every other. On a
  144 or 165 Hz display, or with vsync off, the game waits out each frame
  itself against a fixed 1/120 s cadence - sleeping while there is time,
  spinning the last fraction of a millisecond - so frames start within a few
  hundredths of a millisecond of their slot (with G-Sync, FreeSync or
  ProMotion they are also shown exactly so). A 60 Hz display shows 60: 120
  there needs vsync off (Pause → Settings → Vsync, saved; or
  `-Dlarsons.gpu.vsync=off` for one run) and tears. If the driver ignores vsync
  the cap takes over.
- **A fast display set to 60 Hz.** The refresh rate is the one the system
  runs the display at, and that is often 60 Hz on a 120 or 144 Hz screen (the
  system's default, a power-saving setting, the cable). The game reads the
  monitor's other modes too, and when it can go faster it says so - on the
  HUD ("the display is set to 60 Hz but can do 144"), in Pause → Settings and
  in the console - with the ways to get it: **F11** (or the Settings button)
  plays **full screen with the display switched to its fastest mode** while
  the game has it; the system's display settings raise it for good (Windows:
  Settings → System → Display → Advanced display → Choose a refresh rate); or
  vsync off. While frames keep to the cadence, animation steps by
  the cadence's period rather than the measured time, whose sub-millisecond
  wobble would otherwise show as stutter.
- **No GPU stalls.** The sprite and UI batch uploads each batch's vertices
  once, into a ring buffer mapped unsynchronized, instead of rewriting one
  buffer the GPU was still drawing from for every texture change; palette
  rows stay on the GPU from frame to frame and only new colours are sent.
- **Less overdraw.** Each sprite layer is drawn over only the box its pixels
  cover, not the whole frame card: close up, a 20-layer outfit was 20
  full-screen passes. (Sprites test depth against the scenery but no longer
  write it - billboards all face the camera, so far-to-near order is the
  same answer - which is what lets layers be separate quads.)
- **Loading never costs a frame.** Sheet uploads get about 2 ms of each
  frame, each one's cost predicted from its size and how fast uploads have
  been going; decoding runs on lower-priority threads.
- **No collector pauses.** `run`, `runGpu` and the IntelliJ profile use
  generational ZGC, whose pauses are well under a millisecond; the default
  collector stopped the game for up to ~50 ms while 512-pixel sheets
  decoded. For the jar, pass it yourself:
  `java -XX:+UseZGC -XX:+ZGenerational -jar build/libs/larsons-game.jar`
  (`-XX:+UseZGC` alone on JDK 23 and later).

On Mesa's software rasteriser (4 CPU cores, 1280 × 720, 4× MSAA, uncapped),
these took the demo from about 60 to about 110 fps and the closest camera
from about 22 to about 100 - on the CPU alone; a real GPU has plenty of room
at 120.

**The IntelliJ profile "Run Game (GPU)"** (`.idea/runConfigurations/Run_Game__GPU_.xml`)
runs the Gradle task `runGpu`, which on top of a plain `run`:

- asks for the **discrete GPU** on hybrid Linux laptops (`DRI_PRIME=1`, plus
  NVIDIA PRIME render offload when the NVIDIA driver is loaded). On Windows,
  set `java.exe` to *High performance* once in Settings → System → Display →
  Graphics; macOS switches to the discrete GPU by itself;
- turns on 4× MSAA explicitly (vsync is the saved choice in Pause → Settings, on until changed);
- sets `larsons.gpu=required`, so a software renderer is flagged in red.

Launch options, all forwarded by Gradle like the engine's `-Dlarsons.*` flags:

| Option | Default | |
|---|---|---|
| `-Dlarsons.gpu.msaa=4` | 4 | multisample anti-aliasing (0 = off) |
| `-Dlarsons.gpu.vsync=on` | saved (on) | wait for the display refresh, for this run (otherwise Pause → Settings → Vsync) |
| `-Dlarsons.fps=120` | 120 | the frame rate to hold, for this run (0 = uncapped); otherwise Pause → Settings → Frame rate |
| `-Dlarsons.fps.log=true` | off | print the frame rate, average and longest frame twice a second |
| `-Dlarsons.sprites.scale=1` | 1 | shrink sprite frames on load (0.5 = half) |
| `-Dlarsons.sprites.vramMB=1536` | 1536 | sprite-cache video memory budget |
| `-Dlarsons.assets=assets` | `./assets` | where `assets/sprites` is |
| `-Dlarsons.start=menu` | menu | `demo` to skip the main menu |
| `-Dlarsons.inventory.slots=25` | 25 | the player's inventory slots, the hotbar's five included |
| `-Dlarsons.script=…` | — | a scripted run (see below) |

## Project layout

```
src/main/java/com/larsons/game/
  Main.java                 entry point (headless AWT, the jar's relaunch)
  core/                     Game (window, loop, overlays), Boot and LoadingScreen (what loads as the game starts,
                            and the screen that shows it), FramePacer, Settings, Scene, Autopilot, Screenshot,
                            Launcher (the jar's relaunch, set up as runGpu), SpritePack (a shared copy's sprites)
  audio/                    the engine's sound system: Mp3Decoder, Mp3Tables, PcmClip, SoundLoader, SoundMixer,
                            Sounds, SoundPack, SoundKeys, SoundCategory, SoundDef, SoundStore; the hooks:
                            AnimationSound, SoundEmitter, WorldSounds; Music (a scene's song, faded in and out)
  gfx/                      the GPU layer: Window (GLFW), Shader, Texture, Batch, PaletteAtlas, Font,
                            GpuInfo, Images (a PNG from any Path, the sprite pack's too)
  math/                     Vec3, Mat4, Frustum (what the camera sees)
  input/                    Input — keys, mouse, gamepad, typed text, dropped files; Action (what she does),
                            Bind and Bindings (what is pressed for it), Pad (a gamepad's inputs)
  actor/                    People, Looks, Breed, Folk, Folks, Serpent — the peoples, the foes and how they fight
  story/                    Quest, Quests — the kinds of quest, and the log of them a save keeps
  save/                     SaveGame, Saves — a playthrough and its three slots
  overworld/                Overworld (a map: terrain, destinations, roads), MapView (drawing it)
  grade/                    Grading, Preset, Colours — the colour grade over the 128 colours
  sprite/                   Facing, Elevation, AnimState, Slot, SpriteView, SpriteProfile,
                            SpriteNames, SheetImage, SheetTexture, SpriteLibrary, LayerStack, Wardrobe,
                            Variants, Palettes, ObjectSprites and ObjectStack (things that are not characters),
                            ItemSprites (turning items, icons), TierLook and PixelPalette (weapon tiers),
                            ScenerySprites and SceneryProfile (the trees, grass, rocks and ground)
  cutscene/                 CloseupLibrary, Actor, Cutscene (the script), CutsceneStage
  sprite/fallback/          the 32×32 fallback: Puppet (rigged box figure), PuppetRaster, FallbackSprites
  importer/                 SpriteImport — plan and save a drag-and-drop
  world/                    OrbitCamera, Player, World, ItemDef, Item, Tier, Rarity, Border, Loot, Inventory,
                            Chest, Scenery, SceneryLayout, SceneryRenderer, VoidRenderer (the ground, the sky
                            and its distant backgrounds), Sky, GroundMap and GroundTexture (what the ground is),
                            WorldRenderer, ItemRenderer, Wearable and Wearables (what is worn, as items),
                            Terrain and TerrainRenderer (the highlands' heightmap, its rock and vines)
  ambient/                  the life in the world: Critters, CritterKind and Critter (the little creatures),
                            Animals, AnimalKind, Animal and Temperament (the sixteen animals), Life (how thick
                            each lives in a level)
  building/                 BuildingCatalog (every kind, from assets/buildings), BuildingProfile (a kind: its
                            parts, views, footprint, door), BuildingLook (a building's colours and patterns, and
                            the six shades of a colour), Building (one standing: its footprint, its own metres),
                            BuildingRenderer (its views coloured, patterned and sorted pixel by pixel), Prefab
                            (sets of buildings: the small castle), Room (the pieces rooms are built of: walls,
                            floors, stairs, the door out; the walls cut away) and RoomRenderer (drawn as boxes)
  combat/                   Combat (the dice, swings, shots, mana, the borders' effects), CombatTuning, Move,
                            DiceRoll, Strike, Target, Dummy, FlyingDummy, Projectile; drawing: CombatRenderer, CombatHud,
                            CombatSprites, CombatColours
  scene/                    MainMenuScene, DemoScene, CutsceneScene, PauseMenu, WardrobePanel, InventoryPanel,
                            SoundsPanel, DebugCode, DemoLevel (the demo as a level); CreatorScene (a new
                            character), OverworldScene (the map walked), Story (talk, quests, the workbench and
                            the cauldron, goods used), Fishing, HordeGame and HordeProps (the horde), CardTable
                            and Compendium (the cards), ControlsPanel, ColoursPanel
  level/                    Creative Mode's levels: Level, LevelObject, ObjectType and ObjectTypes (every kind
                            of thing, by key), HeightField (the sculpted ground), LevelWorld (a level played),
                            LevelIO (its folder), Interiors (each kind of building's floors as it comes)
  editor/                   the level editor: CreativeScene, LevelView, Picking, Gizmos, Undo, Scatter, Widgets;
                            OverworldEditor (the map editor)
  ui/                       Ui (immediate-mode GPU UI), MenuList, Theme, ImportPanel, FilePicker
  tools/                    SampleSprites, PackSprites (the sprites folders into one sprites.pack, in parts),
                            Land and BrassVale (the Brass Vale's eleven levels, written by ./gradlew makeBrassVale)
  util/                     Json
assets/sprites/             the sprite sheets (one folder per layer) and their contract; objects/: the chest,
                            the attack dummy, the flying dummy, the critters and the animals; items/<id>/spin_px128/: the turning items; combat/: the
                            dice, arrows, bolts, spells and sparks; tiers.json, pixel_palette.json
assets/combat/              combat.json: the fighting's numbers
assets/buildings/           the buildings and the furniture: a folder each (profile.json and its 24 views, a mill's
                            turning part beside them) and textures/ (the 20 patterns and textures.json)
assets/levels/              Creative Mode's levels, a folder each (level.json, height.png, ground.png)
assets/sounds/              the sound pack: the placeholder sounds by category and object, the music,
                            SOUND_KEYS.txt
dist/                       the shared copy's launchers (Play on Windows.bat, Play on Mac.command, Play on
                            Linux.sh) and HOW TO PLAY.txt, for ./gradlew shareZip
tools/sounds/               make_placeholders.py: the placeholder sounds, made in code
music/                      the songs' MIDI files, for FL Studio (one a song: <song>_medieval.mid)
tools/music/                the songs' arrangements in code (one script a song) and their toolkit (medieval.py, arrange.py)
assets/scenery/             the trees, grass tufts, rocks and the ground tile, and their contract
assets/ui/                  inventory_background.png (432 × 768) when there is one
assets/closeups/            the cutscene close-ups (one folder per layer, like the sprites)
assets/sprites_masculine/,  the masculine body's sheets and close-ups, laid out the same
  assets/closeups_masculine/
assets/cutscenes/           cutscene scripts (menu.cut) and their language
.idea/runConfigurations/    Run Game (GPU), Tests
```

## What came from the engine

From [Larsons-Game-Engine](https://github.com/Larleeloo/Larsons-Game-Engine),
either **copied** or **adapted** to a game that is GPU-first and has a single
window:

| Here | From the engine | |
|---|---|---|
| `util/Json` | `util/Json` | copied — the dependency-free JSON parser/writer (plus typed getters) |
| `sprite/Facing` | `graphics/Facing` | copied — the eight directions, their keys, the mirrored-twin rule |
| `gradlew`, `gradle/wrapper`, `gradle/lwjgl-natives.gradle.kts` | same | copied — Gradle 9 wrapper, LWJGL 3.3.3 native selection |
| `build.gradle.kts` | same | adapted — `-Dlarsons.*` forwarding to forked JVMs, the runnable-jar task |
| `ui/Theme` | `ui/MenuTheme.dark()` | copied — the menu palette |
| `gfx/Window`, `gfx/GpuInfo` | `gl/GlContext`, `gl/GlWindow` | adapted — GL 3.3 core context hints, GLFW callbacks into latched input, the driver report, vsync |
| `core/MacLauncher` | `core/MacGlLauncher` | adapted — relaunch on macOS's first thread for a double-clicked jar |
| `math/Mat4` | `graphics/Mat4` | adapted — immutable column-major matrices, switched to OpenGL's −Z convention |
| `sprite/SheetImage` | `graphics/SpriteSheet` | adapted — left-to-right, top-to-bottom frame slicing |
| `sprite/AnimState` | `graphics/Animation` | adapted — delta-driven frame timing, looping vs. held one-shots |
| `sprite/fallback/*` | `graphics/DirectionalSprites` | adapted — generated per-facing fallback art (here from a 3D figure, for all three heights) |
| `input/Input` | `input/InputManager` | adapted — press latching, repeats, typed text |
| `ui/MenuList` | `ui/Menu` | adapted — keyboard and mouse menu navigation |
| `audio/Mp3Decoder`, `Mp3Tables`, `PcmClip`, `SoundLoader` | `audio/` same | copied — the pure-Java MP3 decoder (minimp3, public domain), WAV/AIFF/AU through the JDK, the decoded-clip cache; gapless here (the LAME header's delay and padding trimmed) |
| `audio/SoundMixer` | `audio/SoundMixer` | copied — the software mixer (pitch, overlap, loops, headless-safe), plus moving a playing sound's pan and a volume per category |
| `audio/Sounds`, `SoundDef`, `SoundStore`, `SoundPack` | same | adapted — play by key, fresh pitch, per-sound overrides, `soundpack.json`, `SOUND_KEYS.txt`; the pack in `assets/sounds/`, no synthesized voices or music |
| `audio/SoundKeys` | `audio/SoundKeys` | adapted — the game's catalogue: categories, a folder per body, chest and item, every animation a key, fallbacks from a move to the move it is a version of |
| `audio/WorldSounds` | `audio/SceneSounds` | adapted — the frame-by-frame hooks: here every animation, through `AnimationSound` |

The engine's Java2D backend, level system, creative mode and mini-games were
left behind: this game draws everything through its own OpenGL renderer.

## Tests and scripted runs

`./gradlew test` (or the **Tests** run configuration) runs the unit tests, none
of which need a GPU: view selection for all 8 × 3 views, the zone boundaries,
file-name parsing, sheet slicing and packing, the fallback character (all 144
views, and that body + held-out sword composites to exactly the picture of both
rendered together), the importer (copying, stitching, layer/name guessing), the
library's lookup and mirroring rules, palette sheets staying palette indices
(one texture whatever the colours), palette swaps and custom colours, the
palette rows a batch shares, cutscene scripts and their timeline, actors'
colour fades and flashes, the close-up folders, the camera and the player's
state machine, the dodges (a tap's dash and a hold's roll, carried on from the
same frame, their distances, the roll's safe frames and rolling through a
dummy, the cooldown, and the weapon in hand drawn in them); the inventory (slots, the hotbar, resizing, what the selected
slot puts in her hands - and that nothing else is -, dropping, sorting by name and by rarity, what she wears made hers and
taken off when it leaves), the debug code, the inventory screen's diamonds (every
slot placed, none overlapping, for any number), the chest's states and light,
rarity and the weapon chart (the drop rates, the borders, names and keys, a
chest's 3 to 7 items with one Rare or better), the weapon tiers' looks (a
Sharper Axe's black handle, a Crystal Sword's purple blade, a Perfect one's
gold, a bow's string never recoloured, all in the palette), the turning
items' sheets and icons, the loot beam, halo and border motes, and the
weapon in her hands drawn in its tier,
the object sheets (names, layer order, all 24 views of every state of the
chest), the sound catalogue (a sound for every animation of both bodies, every
sound in the category whose tab lists it, registered sounds), the pack's lookup
and fallbacks (a move to the move it is a version of, a footstep to any
footstep), your own sounds beating the game's and muting, the footstep's
ground, every listed sound having a placeholder that decodes gaplessly, its
key list being up to date, the music (a song read off the game loop, faded
in and out, coming back up when asked for again, the next one waiting for
the last, a muted one silent, the main menu's decoding to exactly its two
minutes with no seam), and the engine's
MP3 decoder tests; the fighting (`combat/CombatTest`): the dice per
rarity, swings' reach and arc, heavy and unarmed rolls, the wand's one-die heavy blow, the heavy attack's cooldown, every attack state
having a move, arrows arcing onto a dummy, bolts sticking, spells' colours
and mana, fizzling, Venom's 150 %, Mystic's damage and mana, Bonded's count,
and a dummy's hits, fall and rise; the heights (`world/TerrainTest`,
`world/HighlandsTest`, `combat/FlyingDummyTest`, `combat/LockOnAndHighGroundTest`):
the highlands meeting the meadow without a seam, the heights being the
triangles drawn, the ramp walkable and the rock not, the ramp slower, falls
and their hurt, the roll out of one, climbing to the top and stepping up
over the lip, stepping down over it from the top and climbing down, stopping
at the lip too tired to climb, letting go out of stamina, being knocked out, items and the chest on the slopes, the plateau chest's loot flying up onto it and its beams standing there, the flier's
rounds, its dive and what a roll, a block and a parry do to it, its fall and
rising, shots led to where it will be, the lock-on and its next, the
aimed-up bow and crossbow, and the high ground's die; and the scenery (its profiles, where each
frame is in its sheet, the sheets and the ground tile in `assets/scenery/`
keeping to their format, the meadow's layout - the same every run, nothing in
a clearing or on top of anything solid -, the wind, and the player kept out
of the trunks and rocks but not the grass); and Creative Mode (`level/LevelTest`,
`scene/DemoLevelTest`, `editor/EditorTest`): levels' objects and their numbers,
the sculpting brushes and the level's edge, resizing, a level's folder written
and read back the same - unknown kinds and keys kept -, the demo copied into a
level with everything in it and that level played as the demo, sound spots,
undo and redo of every kind of change, the scatter brush keeping trees apart
and off cliffs, and pointing at the ground and at things through the camera;
and the loading screen (`core/BootTest`, `core/LoadingScreenTest`,
`sprite/SpriteLibraryTest`, `audio/SoundPreloadTest`): the steps in order and
what each reports, the screen drawn as a step starts and as it goes but never
more than 30 times a second, background work watched until it is done (and let
go if it breaks), a failed step held on screen, a closed window not taken for
a failure, the bar weighted by last time's timings and the file they are kept
in, the sprite index's running count and its listing finding the same sheets
as parsing every name, and the sound effects counted as they decode; and the
day (`world/DayCycleTest`): the sun's round, the planet's place and phase, the
light from noon to the planet-lit night, the stars, the sunset's warm horizon,
the clock, the time-lapse and the still day, the mist and the light rays by
the hour, lights falling off in bands, and a level's time, weather, lights and
rays saved and played back; and the biomes and water (`world/WaterAndBiomeTest`,
`editor/BiomeScatterTest`): painted water's surface, kind and current, the sea
below its level and a pool above it, the water's bytes, the water's mesh
running on from cell to cell and slanting where the paint stops, a biome read
from the ground and the water, the biome brush's mix, the twelve grounds in
three groups and their snapshots, a level's water, sea and grounds saved and
read back (and their files gone when dry), how deep she stands, what she hears
by day and by night, tall grass leaning away from her, each ground's
footstep, each biome's sets, and nothing but reeds sown in a pond; and the
buildings and their insides (`building/BuildingTest`, `building/InteriorTest`):
a kind's profile, its own metres, its footprint and the click on it, a
colour's six shades, a look kept in its level, the small castle; a doorway
walked through and a wall and a window not, the walls on the eye's side cut
down and the far ones left standing, a wall's two sides and trim, the twelve
pieces of furniture, every kind's inside as it comes - a door out on the
ground floor, stairs between its floors, nothing in the way of a door or a
staircase, no furniture in a wall or another piece - a building's floors saved
with its level, and going in and out again to the outdoors as she left it.

The game can also drive itself, for smoke tests and screenshots on a headless
machine:

```bash
xvfb-run ./gradlew run -Dlarsons.script="scene demo; wait 1; pitch 12; state walk; \
  wait 0.5; shot walk-side.png; key escape; click 640 267; shot wardrobe.png; quit"
```

Commands: `wait`, `scene`, `shot`, `key`, `click`, `drop <path>`,
`importsave [layer/name]`, `importclose`, `quit`, and in the demo `pitch`,
`yaw`, `zoom`, `state`, `face`, `move`, `jump`, `dash`, `roll` (the way she is
scripted to move, else the way she faces), `attack`, `draw` / `loose` (the
bow), `crouch [on|off]`, `heavy`, `spin`, `parry`, `block on|off`, `bash`,
`emote <laugh|cry|surprise|angry>`, `stance <sword|axe|bow|crossbow|staff|wand|mace|dagger|free>` (`free`: empty hands), `teleport`, `pickup`,
`dropitem`, `inventory [open|close]`, `hotbar <1-5>`, `slots <n>`, `give <item> [tier|rarity]
[border]` (`give battle_axe crystal venom`), `seed <n>` (the chest's loot and the dice from a seed),
`chest` (open or shut it), `chest goto`, `store [n] goto|open|hit|fill <what>` (barrel, crate, pot or storage
chest n), `shop goto` / `shop` (the nearest shop: at its counter / open or shut it), `hero <class>` or
`hero picker`, `cards` (her hand and perks), `tiers <slot>` (a weapon's tiers; `tiers <slot> up`: upgrade it),
`door` (go through the door, stairs or door out she is at),
`enter` (into the nearest building with an inside, wherever it is), `upstairs` / `downstairs` (by the
nearest stairs), `outside`, `dummy goto [n] [metres] [front|back|left|right]` (stand
by dummy n, facing it), `dummy add <x> <z>`, `time <hour>` (`time 6:30`), `timelapse on|off|<speed>`, `goto <place|spawn>` (the
highlands' `ramp`, `plateau`, `ledge`, `cliff`, `hill`, `hollow`), `lock [on|off|next]`, `flier swoop|under|lock [n]`
(dive at her now / stand under flier n / lock on to it), `animal <kind> [nature] [metres]` (one put that
far in front of her, facing her), `animal goto|lock <kind>` (stand by / lock on to the nearest), `health <n>`, `stamina <n>`, `fall <metres> [roll]` (fall from
that high, rolling out of it), `climb [down|top]`, `autocamera on|off`, `mana <n>`, `cooldown <seconds>` (the heavy attack's), `sort name|rarity`,
`debug [on|off]` (the num-pad code), `clearground`, `pause`, `resume`, `hud`, `scenery` (or `props`), `style` (`rendered`, `px128`,
`px64`), `wear <layer> [item]` (no item: take it off), `colour <layer> [option]`
(an option or any `#rrggbb`; the body's colour is the skin tone; no option:
the item's own colours) and
`hand` (`auto`, `right`, `left`). Opening the wardrobe saves the outfit to
`config/wardrobe.json` when it closes, a scripted run included.

`scene` also takes `creative` (a new level), `edit:<folder>` and
`play:<folder>` (a level of `assets/levels/`), `newgame` (the character
creator), `mapedit` (the map editor), `horde:<map>` and `hordeedit:<map>`
(a map of `assets/horde/`). In a level: `hero <class>`, `coins <n>`, `give
<item>`, `say [n]` / `endtalk` (talk to whoever is nearest; choose topic n),
`journal`, `station cauldron|workbench`, `usehand` (what is in her hand),
`shop`, `summon`, `autofish [off]`, `round <n>` (the horde's). In the
creator: `name`, `class`, `people`, `size`, `skin`, `begin`. On the map: `go
<destination>`, `enter`, `mapmenu`. In the level editor: `tool
<place|select|sculpt|paint|scatter|sky|time|level|water|life>`, `entry <sprite|item|kind|label>`
(the palette's), `place <x> <z> [degrees] [lift]`, `set <key> <value>` (on what is selected), `pointer on|off`,
`draw <x0> <z0> <x1> <z1>` (a wall or a room, as dragged), `copylook <id>`, `pastelook <id> [id …]`,
`roomsize <width> <depth>`, `cursor <x> <z>`, `look <x> <z>
[distance pitch yaw]`, `select <x> <z>` (the nearest), `delete`, `duplicate`,
`turn <degrees>`, `dress <part> <#rrggbb|-> [pattern]` (the selected buildings' part, or the one being
placed), `sculpt <raise|lower|smooth|flatten> <x> <z> <radius>
<strength> [seconds] [height]`, `paint <0 grass … 12 crystal, 13 cobbles … 16 packed path> <x> <z> <radius>`,
`biome <plains|marsh|mountains|jungle|savannah|ocean|river|waste|crystal> <x> <z> <radius> [0]` (its
ground, and its plants sown unless 0), `sow <biome> <x> <z> <radius> [dabs] [tuft|tree|rock]`, `water
<clear|swamp|sea|crystal> <x> <z> <radius> [surface|auto] [flowx flowz]` (`auto`: the ground there and
the depth), `waterdepth <m>`, `water erase <x> <z> <radius>`, `water sea <level|off>`, `fill <clear|swamp|sea|crystal> <x> <z>
[depth]`, `feather <0-1>`, `filter off|slope <from> <to>|height <from> <to>|only <ground>|not <ground>|water <0-2>`,
`scatter <set> <x> <z> <radius> [thickness]`, `sky zenith|horizon <#rrggbb>`,
`sky fog <m>`, `time <hour>`, `time day <minutes>`, `time run on|off`, `weather <clouds|stars|aurora|mist|misth|rays|planetsize> <0-1 or degrees>`, `weather planet|rings on|off`, `weather planetat <azimuth> <elevation>`, `backdrop <mountains|hills|trees|spires|ocean|clear> [#colour
height haze seed from to] [at=horizon|edge] [part=#colour] [extent=0-1]`, `backdrop pick <n> [part]`, `backdrop move <from> <to>`, `name`, `size`, `save`, `undo`, `redo`, `ui
on|off`, `fog game|thin`, `life <critter|animal|regrow|yield|animals> <n>`, `life reset`, `lifepage
critters|animals`, `playtest [x z]`, `leave`, and for a building's inside `inside` (edit the selected
building's), `floor <n>` (1: the ground floor), `addfloor` and `outside`.

---

## The plan

This is a descriptive plan for a new game that uses mechanics from Larson's Game
Engine. Each section is broken up into chunks, or tasks to be done.

### Task 1: Create the game layout ✅

- This game is a **3D environment with 2D entities** for the player and items.
- The player renders from **8 directions** like the sprites from Larson's Game
  Engine, and from **3 vertical rendering zones** for the different
  third-person heights you can view the character from:

  | Zone | Rendered at | Used while the camera is |
  |---|---|---|
  | side | 0° | 0° – 33° above the character |
  | middle | 45° | 34° – 75° |
  | top (birds-eye) | 90° | 75° – 90° |

  Zoom in/out is a feature.
- All characters (playable or otherwise) are rendered as a series of
  **stackable sprite sheets**. The elements for each character line up with
  their animations: a sprite sheet for a held sword renders in-hand for a player
  character. The character and sword sheets have the same number of frames
  (30 fps, 512 × 512 frames) for any given animation state, and the sword
  follows the character's movements when held. The sword is cut out where the
  player's hand is, so it renders correctly in the stack of sprite sheets. The
  sheets are pre-rendered in Blender.
- **Items** are 2D sprites as well that hover with a shadow on the ground. They
  always face the camera from any angle.
- All other **environmental elements** (trees, rocks, houses, etc.) are
  rendered in 3D. *Since then the trees, the grass and the rocks have become
  pixel-art sprites rendered in Blender and the house is gone - see
  [The scenery](#the-scenery).*

**To do**

- [x] Create a blank 3D void area that can render the animations (all 8 points
      and 3 heights for each) for the character sprite in the states idle, walk,
      run, sprint, jump and attack — *the Demo scene; preview any state with
      `[ ]`, turn with `, .` or `T`, change height with `Tab` - and since then
      50 more states: crouching, pick-ups, emotes, the sword's and shield's
      moves and the battle axe's, bow's and crossbow's stances*
- [x] Create room for cosmetics as sprite-sheet layers over the base character
      sheet — shirts, pants, underwear, bras, shoes, hairs, noses, eyes, mouths,
      ears, earrings, wristwear, gloves, hats, carried items (left and right
      hand) and "other" — all editable from a generic pause menu — *24 layers
      under `assets/sprites/` (belts, necklaces, a left wrist, sheaths, eyebrows
      and makeup since), edited in Pause → Wardrobe*
- [x] Render one pick-up-able item in the scene — *a sword that hovers over its
      shadow and renders in hand when equipped (E / G)*
- [x] Create a fallback profile for each animation for when the base player
      character is not provided — roughly 32 × 32 pixels, scaled up; cosmetics
      left blank if not provided — *generated from a rigged box figure for all
      144 views, with a matching sword layer*
- [x] Make a main menu scene that only loads "demo" for the blank void scene
- [x] Create a drag-and-drop file system for saving sprite sheets to the
      repository — *drop on the window; saved to `assets/sprites/`*
- [x] Copy over all pertinent code and enable GPU acceleration for the entire
      game — *OpenGL 3.3 core for everything; see
      [What came from the engine](#what-came-from-the-engine)*
- [x] Create an IntelliJ profile to run the game with GPU acceleration on —
      *Run Game (GPU)*
- [x] Refine the README.md with added features and polish the formatting

### Task 2: An inventory, a treasure chest and sound ✅

**To do**

- [x] Create an inventory system that can handle a variable number of slots
      and is opened with **I** — *`world/Inventory`, any size
      (`-Dlarsons.inventory.slots`); the screen lays out however many there
      are*
- [x] Give it 5 hotbar slots, navigated with the scroll wheel or 1 – 5 — *what
      is in the selected slot is in her hands; picking up puts an item in the
      hotbar first*
- [x] Stop handling animations with the number keys — *the weapons are chosen
      from the hotbar, the emotes are F1 – F4, stopping a preview is
      Backspace*
- [x] Draw the inventory as square diamond-shaped slots with a white border
      over the screen, on a background loaded from the assets folder —
      *`assets/ui/inventory_background.png`, 432 × 768*
- [x] Add an ornate treasure chest on the ground, viewable from 8 directions
      and 3 heights like the player — *`assets/sprites/objects/ornate_chest/`,
      modelled and drawn in 3D-Modeling*
- [x] Give it an opening animation the player triggers and an idle animation,
      at 128 × 128 pixels, with swirling magical smoke circling it — *idle,
      open, opened and close, and the smoke as two layers of its own*
- [x] Light it up from inside when it opens — *the lining and the hoard lit,
      light rising out of it, the smoke tinted, a pool of light on the ground*
- [x] Attach sound hooks to its animations, using the engine's MP3 sound
      system and sound asset storage — *`audio/`, `assets/sounds/chests/`*
- [x] Give every player animation, masculine and feminine, sound asset
      storage — *`assets/sounds/player/feminine/` and `masculine/`, a sound
      key and a hook for all 56 states*
- [x] Make the chest a loot chest for in-game items — *3 to 7 items of six
      rarities, at least one Rare, thrown out in beams of light; see
      [Items, rarity and loot](#items-rarity-and-loot)*

### Task 3: TBD
