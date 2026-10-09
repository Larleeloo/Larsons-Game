# Sprite sheets — the contract

Everything drawn on a character in Larson's Game is a stack of pre-rendered
sprite sheets. This folder is where they live, and this file is the contract
between the Blender renders and the game. You rarely need to put files here
by hand: **drop them on the game window** and the importer names and files
them for you (see [Importing](#importing)).

---

## Folder layout

```
assets/sprites/
├── body/            <item>/   the base character (one folder per body)
├── underwear/       <item>/
├── bra/             <item>/
├── shoes/           <item>/
├── pants/           <item>/
├── shirt/           <item>/   shirts, and dresses
├── belt/            <item>/
├── necklace/        <item>/
├── gloves/          <item>/
├── wristwear/       <item>/   on the right wrist
├── wristwear_left/  <item>/   on the left wrist
├── sheath/          <item>/   scabbards and the like, worn at the hip
├── ears/            <item>/
├── earrings/        <item>/
├── nose/            <item>/
├── eyes/            <item>/
├── eyebrows/        <item>/
├── makeup/          <item>/
├── mouth/           <item>/
├── hair/            <item>/
├── hat/             <item>/
├── other/           <item>/
├── carry_left/      <item>/   held in the left hand
├── carry_right/     <item>/   held in the right hand (the sword: carry_right/sword/)
└── items/           <item>/icon.png   a pickup lying in the world
```

An *item* is any folder name — `body/hero`, `hat/straw_boater`,
`carry_right/sword`. Every item folder shows up in the pause menu's
**Wardrobe** for its layer.

### Bodies

Everything worn has to be drawn for the body that wears it - a shirt drawn on
one body does not fit another. So a body can have a sprites folder of its
own beside this one, named after it, with the body and every item drawn on
it under the same slot and item names:

```
assets/sprites/                  the default body's: body/feminine/, hair/bob_px128/, ...
assets/sprites_masculine/        his: body/masculine/, hair/bob_px128/, shirt/laced_dress_px64_lh/, ...
assets/sprites_<body>/           any other body's, in its folder's name
```

The body a character wears decides which folder all of its items come from:
a body found in `assets/sprites_<name>/body/` uses that folder, any other
(and the generated fallback body) this one. The Wardrobe lists the bodies of
every folder under **Base body**, so a character can switch body and keep
its outfit - the items keep their names (`hair: bob` is his bob on him, hers
on her). An item that one body's folder does not have is simply not drawn on
that body. Pickup icons (`items/`) are always this folder's. Importing saves
a layer into the folder of the body being worn (so it can be worn straight
away), a body into the folder it is already in (a new body: this one), and a
pickup icon here. The cutscene close-ups do the same: `assets/closeups/` and
`assets/closeups_<body>/`, the folder going with the body's sprites folder -
a body whose sprites folder has no close-ups yet has none of another body's
drawn on it.

### The first version of re-animated states

When states are animated again, the sheets they had are kept, as they were,
beside the sprites folders - the same slot and item folders, with their
`profile.json` and `variants.json`:

```
assets/animations_v1/sprites/<slot>/<item>/<state>_<elevation>_<direction>.png
assets/animations_v1/sprites_masculine/...
```

The game draws a state from its current sheets, and - in the **classic**
mode (**F9**, Settings → Animations, `-Dlarsons.animations=classic`) - from
the kept first version where there is one. The archive is as big as the
sprites folders, so it is only listed the first time classic is switched on
(in the background, a few seconds; until then the current sheets are drawn);
from then on either version stands in where the other has no sheet. The
choice is saved with the settings, so classic stays on until it is switched
back. Only the pixel-art styles have two versions: switch the Wardrobe to
Pixel 128 or Pixel 64 to compare. The weapon moves - every state
with a sword, axe, bow or crossbow in hand but the bow's and the crossbow's
parries and the axe's crouched sweep - are in their second version in the
pixel-art styles; their first is kept here.

The sword alone - no shield in either hand - has states of its own,
`blade_<state>` (idle, walk, run, sprint, jump, attack, the three crouches,
spin attack and parry): the first version of the sword stance's moves, the
free arm down, where the second version holds the shield up. Their sheets
are the archived first version's, under the new names, in every item's
folder but the shields'. Where a set has none (the 512-pixel one), the sword
and shield's stand in. The 512-pixel rendered sheets were not made again: they keep
their one (first) version, which both modes draw. The two
versions of an item share its palette labels (the new sheets are drawn
pinned to the old ones), so one `variants.json` colours both.

## File names

```
<state>_<elevation>_<direction>.png          e.g.  walk_middle_ne.png
```

| Part | Values |
|---|---|
| state | `idle` `walk` `run` `sprint` `jump` `attack` - the first six - and the 50 after them (see **The animations** below): `crouch_idle`, `axe_heavy_attack`, `bow_crouch_draw`, ... |
| elevation | `side` (rendered at 0°) · `middle` (45°) · `top` (90°, birds-eye) |
| direction | `s` `se` `e` `ne` `n` `nw` `w` `sw` |

That is 6 × 3 × 8 = **144 sheets** for an item with the first six, which is all
an item needs: a state it has no sheets for is shown as one of the first six
(see [Fallbacks](#fallbacks)). The feminine model's pixel-art items have all 56
states, 1,344 sheets an item (a carried item the states of its stance).

The **direction is the way the character faces in the picture**: `s` faces the
camera, `n` shows its back, `e` faces the viewer's right, `w` the viewer's left,
and the diagonals are the three-quarter views between them.

The game is forgiving about names — the importer and the folder scan both read
`Walk-45-NorthEast.png`, `hero/Run/Top/S.png`, `idle 0 front.png` and the like
(any separator, CamelCase, `low/mid/high` or `0/45/90` for elevations,
`north…`/`front/back/left/right` for directions) — but it always *saves* the
canonical form above.

## The sheet

- **PNG with transparency.** Frames are **512 × 512** unless the item's
  `profile.json` says otherwise.
- **Frames run left to right, then top to bottom** — a single strip or any
  grid. Empty cells at the end of a grid are ignored.
- **30 frames per second.** The frame count is whatever the animation needs
  and can differ between states (idle might be 60 frames, attack 18).
- Loose numbered frames (`walk_side_e_0001.png`, `…0002.png`, the way Blender
  writes an animation) are stitched into a sheet by the importer.

## The camera (how to render)

The game stands every frame back up in the 3D world as a camera-facing card.
For it to line up — feet on the shadow, the right size — every render uses the
same framing:

| Setting | Value |
|---|---|
| Camera | **Orthographic**, **Orthographic Scale 2.4** (the frame is 2.4 m wide) |
| Aimed at | the **pivot**, a point **0.9 m above the feet**, on the character's axis |
| Resolution | 512 × 512, Film → **Transparent** |
| Elevations | camera level with the pivot (`side`), 45° above it (`middle`), straight above it (`top`) |

With those numbers the feet sit 87.5 % of the way down a `side` frame, 76.5 % down
a `middle` frame and in the centre of a `top` frame, and the game works that out
by itself.

**Directions in Blender.** Model facing **−Y** (Front view, numpad 1), feet on
Z = 0. With the camera on the front (−Y) side, rotate the character about Z:

| direction | rotation about Z |
|---|---|
| `s` | 0° (facing the camera) |
| `se` | +45° |
| `e` | +90° |
| `ne` | +135° |
| `n` | 180° |
| `nw` | −135° |
| `w` | −90° |
| `sw` | −45° |

For the `top` set, swing the camera up over the character from the front, so
that in the `s` picture the character's face points down the screen.

**Already rendered differently?** Put a `profile.json` in the item folder
instead of re-rendering:

```json
{
  "frameWidth": 512,
  "frameHeight": 512,
  "frameWorldSize": 2.4,
  "pivotHeight": 0.9,
  "fps": 30,
  "stateFps": { "idle": 24 },
  "anchors": { "side": [0.5, 0.9], "middle": [0.5, 0.8], "top": [0.5, 0.5] },
  "pixelArt": false
}
```

Every field is optional. `anchors` is where the feet are in a frame of each
elevation, as fractions of the frame from its top-left corner — set it and the
pivot numbers no longer matter. `pixelArt` draws the sheets with hard pixel
edges (no smoothing, no mipmaps) and never shrinks them for the sprite
resolution setting; frames of 64 pixels or fewer are pixel art without saying
so, bigger pixel art (the 128-pixel sheets below) needs `"pixelArt": true`.

## Layers

The layers draw in this order, bottom to top:

```
body → underwear → bra → shoes → pants → shirt → belt → necklace → gloves →
wristwear → wristwear_left → sheath → ears → earrings → nose → eyes → eyebrows →
makeup → mouth → hair → hat → other → carry_left → carry_right
```

except for the cape (`other`), which hangs behind her: when she faces the
camera (`s`, `se`, `sw` from the side or the middle) it draws first, under the
body, and otherwise right after `mouth`, over the clothes but under the hair,
the hat and the carried items. Long hair falls over it and short hair does not
reach it, so it is cut against the body alone and nothing else is cut against
it.

Two rules make the stack line up from all 24 views:

1. **Same frames as the body.** A layer's sheet for a state has the same frame
   count as the body's sheet for that state, and the game plays the same frame
   index on every layer. (If a count differs it is stretched to fit, and the
   importer warns you.)
2. **Held out by the body.** Render each layer with the body in the scene set
   to **Holdout** (Outliner → collection → Holdout), so the layer is cut away
   wherever the body is in front of it. That is how the sword disappears into
   the fist and behind the back, and why the game can simply draw layers on
   top of each other.

A missing west-facing sheet (`w`, `nw`, `sw`) borrows its east-facing twin,
mirrored, and vice versa — a set rendered facing only one way still turns.
Mind that a mirrored sword swaps hands.

## Colours

An item can come in more colours than it was drawn in. A `variants.json` beside
its sheets lists them; the sheets must then be **palette PNGs** (indexed colour)
whose palette entries are the item's colour *labels* — entry 0 transparent,
entry 1 the empty-frame marker, label `i` at entry `i + 2`:

```json
{"version": 1, "labels": 36, "default": {"own": "brown"},
 "channels": {
   "own":  {"labels": [0, 1, 2, 3, 4, 5],
            "options": {"black": ["#141112", "#231d1e", ...], "royal_blue": [...], ...},
            "swatch":  {"black": "#231d1e", "royal_blue": "#2b4bb6", ...}},
   "skin": {"labels": [6, 7, 8, 9, 10, 11], "options": {...}, "swatch": {...}}}}
```

A **channel** is a set of labels that change colour together: `own` is what the
slot's colour picks (a shirt's cloth, the lips of a mouth, the hair, the gold of
an earring), `skin` is skin wherever an item shows it (the body, ears, nose, the
mouth round the lips) and follows the body's colour, the skin tone. For every
option the file gives each of the channel's labels a colour, in label order;
recolouring is swapping those palette entries, so one set of sheets draws every
colour. Labels in no channel (teeth, a brass buckle) never change. `default` is
the choice the sheets were drawn in (no entry: the item's own colours), and
`swatch` the colour a wardrobe shows for an option. A colour can also be any
`#rrggbb` (in `config/wardrobe.json`, a demo script or a cutscene): the
channel's labels are then shaded like its first option, round that colour.

The swap happens on the GPU. A palette PNG of pixel art is uploaded once, as
its palette indices, and each layer is drawn through a palette of its own - the
sheet's, with the wardrobe's colours swapped in - which the sprite shader looks
the indices up in. A sheet is one texture whatever colours it is worn in, and
the colours can change from one frame to the next (the cutscenes fade and
flash them) without anything being decoded again.

## Left-handed versions

An item folder with `_lh` after its name (`carry_left/sword_px64_lh/`) is the
same item drawn for a **left-handed** character: the right-handed character in
a mirror. Its sheets for a direction are the mirror image of the right-handed
art for the mirrored direction (`e` ↔ `w`, `ne` ↔ `nw`, `se` ↔ `sw`), taken
from the item's twin on the other side of the body — `carry_left/sword_lh` is
`carry_right/sword` mirrored, `wristwear/gold_bangle_lh` is
`wristwear_left/gold_bangle` mirrored, `sheath/sword_left_hip_lh` is
`sheath/sword_right_hip` mirrored — so an item stays where its name says,
and a left-handed attack is a mirrored slash with the sword in the left hand.
A left-handed character draws the `_lh` versions and swaps its hands in the
draw order. Without an `_lh` version the game mirrors the twin itself.

## Fallbacks

- **A state the body has no sheet for** (from that view) is shown as the
  first state along its fallbacks that it has: a crouch walk as the walk, the
  bow's draw as the bow's idle and then the idle, an emote as the idle. Every
  chain ends in one of the first six. Every layer plays the state the body
  shows.
- **No body sheets** (or a view missing from them): the game draws its own
  32 × 32 fallback character for that view, scaled up — the first six, all 24
  views, generated at start-up (the other states shown as theirs).
- **The sword** has a matching 32-pixel fallback layer, used while the body is
  the fallback.
- **Cosmetics without sheets are not drawn.**
- **A pickup without `items/<item>/icon.png`** uses a generated icon.

## Importing

Drag files or folders from your file manager onto the game window (main menu or
demo). The importer shows what it recognised, guesses the layer from the folder
you dropped (`hat/red_cap/` → `hat`, item `red_cap`), lets you change both, and
saves canonical sheets into this folder — in the repository, ready to commit.
With *Wear it after importing* ticked the character puts it on straight away.

## Memory

A complete 512-pixel item is several gigabytes of pixels, so sheets load on
demand (only the views the camera is looking at), decode on background threads,
and are evicted least-recently-used past a video-memory budget of 1.5 GB. Two
things keep that budget from filling up:

- **The empty part of a frame costs nothing.** As it loads, a sheet is cropped
  to the box its frames actually cover (plus a thin transparent border), and
  put back in its place in the frame when it is drawn. An earring rendered in
  the body's 512-pixel frame takes a few kilobytes a frame instead of a
  megabyte.
- **Loading ahead only into spare room.** What the character may need next —
  this view's other states, the neighbouring directions — is loaded ahead only
  while the budget has room for it, and never pushes anything out. With a lot of
  big layers it simply loads less ahead; nothing is loaded over and over.

For smaller GPUs use Settings → Sprite resolution, or launch with
`-Dlarsons.sprites.scale=0.5` / `-Dlarsons.sprites.vramMB=768`.

## Trying it without renders

```bash
./gradlew sampleSprites
```

writes a complete stand-in set to `build/sample-sprites/` — a body, a red cap
and the sword on separate held-out layers, plus one animation as loose frames —
rendered from the fallback figure at 256 px. Drop its folders on the window to
see the importer and the layer stack at work.

## Objects

Things in the world that are not characters - the treasure chest - are drawn
the same way, from the same camera and the same 24 views, as a stack of
layers, but with states of their own:

```
objects/<object>/profile.json                                  framing and fps, as a character's
objects/<object>/<layer>/<state>_<elevation>_<direction>.png
objects/ornate_chest/chest/open_middle_se.png
```

- **A state is any name** (`idle`, `open`, `opened`, `close`); a sheet's
  frames play at the profile's `fps`, looping or once as the game's object
  says (`world/Chest`).
- **Layers draw in name order**, except that one whose name ends in `_back`
  goes under the rest and one ending in `_front` over them - the chest's
  `smoke_back`, `chest`, `smoke_front`. Render a layer that goes round an
  object as these two halves, split at the object's middle, and the game
  needs no holdouts for it.
- **A layer with no sheet for the object's state is ambient**: it loops its
  own state (its first, alphabetically - the smoke's `swirl`) on the world's
  clock whatever the object does, and is tinted by the object's light.
- **Placement** is a character's: the frame stands on the object's spot on
  the ground, `pivotHeight` below the point the camera was aimed at. The
  chest's frames are 2.4 m across, aimed 0.6 m up, 128 pixels: the
  character's 128-pixel scale.
- A missing west-facing view borrows the east one, mirrored. Palette PNGs
  stay palette indices on the GPU, as everywhere else.

The chest is drawn by 3D-Modeling's
`items/ornate_treasure_chest/ornate_chest_sprites.py`.

## The sheets in this folder

Everything here is made from the rigged feminine model in
[3D-Modeling](https://github.com/Larleeloo/3D-Modeling) (`generic_feminine_model/`,
steps 10-14 of its README), all 144 sheets an item, at the default framing
above. She is 1.70 m tall. The first item of each layer below is the outfit the
holdouts were cut against.

- **Pixel art**, every item: in 128 x 128 frames (`<item>_px128/`) and 64 x 64
  frames (`<item>_px64/`), each also left-handed (`<item>_px128_lh/`,
  `<item>_px64_lh/`, see [Left-handed versions](#left-handed-versions)), with a
  `profile.json` and a `variants.json` (see [Colours](#colours)). They are
  cel-shaded palette PNGs with one-pixel line art, all in one palette of 128
  colours.
- **512-pixel renders** (`<item>/`, no `profile.json`): the first outfit's
  items, marked ● below. They were rendered before the pixel art's round of
  fixes (the briefs' cut, the mantle - still the short one there, cut against
  the long waves and the sun hat - the shield, the knees, the sword's
  clearance) and have the arched brows painted on the body.

| Layer | Item | |
|---|---|---|
| body | `feminine` ● | the base body, nude (the face's cut-outs are capped, so it is whole with the nose, mouth and eye layers empty); in the pixel art without brows, which are the eyebrows layer |
| underwear | `cotton_briefs` ● | lavender cotton briefs |
| bra | `leather_bralette` ● | a lace-up leather bralette |
| shoes | `leather_ankle_boots` ● | brown leather ankle boots |
| pants | `denim_jeans` ● | jeans, **cloth-simulated** per animation |
| shirt | `plaid_flannel` ●, `laced_shirt`, `laced_dress` | a red plaid flannel shirt; a plain linen shirt, the V at the throat laced with a leather cord; a dress - the laced bodice over a flared wool skirt to mid-calf. All **cloth-simulated** |
| belt | `leather_belt` | a leather belt with a brass buckle |
| necklace | `gold_pendant` | a gold chain with a drop pendant |
| gloves | `black_leather` ● | black leather gloves |
| wristwear | `gold_bangle` ● | a gold bangle on the right wrist |
| wristwear_left | `gold_bangle` | the same on the left wrist |
| sheath | `sword_left_hip`, `sword_right_hip` | a leather scabbard for the sword, hung from the belt on the left or the right hip |
| ears | `round` ●, `pointed` ●, `elven` ● | human ears; half-elf points; long, swept-back elven ears |
| earrings | `gold_hoops` ● | gold hoops |
| nose | `straight` ●, `button` ●, `aquiline` ● | |
| eyes | `hazel` ●, `ice_blue` ●, `amber_slit` ● | the amber eyes have slit pupils and a faint glow; in the pixel art an eye is coloured whole |
| eyebrows | `arched`, `soft`, `thin` | |
| makeup | `noir`, `rose`, `plum` | liner, shadow and lipstick |
| mouth | `full` ●, `wide` ●, `heart` ● | lips, teeth and tongue |
| hair | `long_waves` ●, `bob`, `ponytail`, `pixie`, `braids`, `curls`, `crew_cut`, `side_part`, `undercut`, `beanie_long`, `beanie_short` | long waves; a blunt bob with a fringe; a high ponytail; a pixie cut; two plaits; spiral curls; a crew cut; a short side parting; an undercut; long hair and a crew cut for under the beanie. All spring-simulated where they hang free |
| hat | `straw_sun_hat` ●, `beanie` | a wide-brimmed straw hat with a navy ribbon; a knitted beanie (wear it with `beanie_long` or `beanie_short`) |
| other | `wool_mantle` ● | a long red wool cape down to the ankles, **cloth-simulated**, flowing out behind as she runs |
| carry_left | `round_shield` ●, `sword` | a round wooden shield strapped to the left forearm; the sword in the left hand |
| carry_right | `sword` ●, `round_shield` | a short arming sword - the demo's pick-up sword (`items/sword/icon.png` is its pickup); the shield on the right forearm |
| carry_right | `battle_axe` | an ornate two-handed battle axe, 95 cm - the axe stance's weapon, pixel art only, drawn only in the `axe_*` states |
| carry_left | `longbow` | a 1.30 m recurve bow, its string drawn and an arrow nocked in the draw - the bow stance's, in the `bow_*` states |
| carry_right | `crossbow` | an 85 cm crossbow with a bolt that goes when it is shot - the crossbow stance's, in the `crossbow_*` states |

The three weapons are not worn: they are a **stance** each (`sprite/Stance` in
the game), held by picking them up (their pickups are `items/battle_axe/`,
`items/longbow/`, `items/crossbow/`). While a state of a weapon stance plays,
its weapon is drawn in its hand and the other hand is empty, whatever the
wardrobe carries; the emotes and the pick-ups are played with empty hands; the
wardrobe's own carried items (the sword and the shield) are drawn in the sword
stance's states only.

**Colours.** The pixel art can be recoloured (Pause → Wardrobe: the swatch
beside the item). The hair, the shirts and dress, jeans, briefs, bralette,
boots, gloves, beanie and mantle, and the eyes (coloured whole), brows, makeup
and lips come in the fourteen hair colours - `brown`, `black`, `auburn`,
`copper`, `honey_blonde`, `platinum`, `cherry_red`, `pink`, `electric_blue`,
`violet_ombre`, `emerald`, `mint`, `green_ombre` and `royal_blue` (hair keeps
its roots-to-ends shading, the ombres included); the necklace and earrings in
`gold` or `silver`, the bangles in those and the hair colours. The body's colour
is the **skin tone**, in the same fourteen, and every layer that shows skin
(ears, nose, the mouth round the lips) follows it. Trims keep their own colours
(laces, soles, the mantle's border, hair ties, teeth), and so do the belt, the
scabbards, the sun hat, the sword and the shield. Without a pick an item is
drawn in its own colours, and the hair brown.

**The animations.** Every item has the same frame count per state, so the
layers stay in step (the game plays one frame index on all of them):

| state | frames | seconds | notes |
|---|---|---|---|
| idle | 60 | 2.0 | loops |
| walk | 28 | 0.93 | loops; the stride is timed to the player's 1.7 m/s, so the planted foot stays put on the ground |
| run | 20 | 0.67 | loops; 4.2 m/s |
| sprint | 16 | 0.53 | loops; 7.0 m/s |
| jump | 27 | 0.9 | one-shot; leaves the ground at 20 % and lands at 82 % of the clip, as `Player` expects, and stays at ground height in the picture - the game does the rising (to 0.58 m) |
| attack | 18 | 0.6 | one-shot; a sword slash, kept inside the frame |
| crouch_idle, crouch_walk, crouch_walk_fast | 48, 32, 24 | | loops; crouched with the sword and shield, 0.9 and 1.8 m/s |
| pickup, crouch_pickup | 30 | 1.0 | one-shot, empty hands; the hand closes on the item half way through |
| emote_laugh, emote_cry, emote_surprise, emote_angry | 48, 60, 36, 48 | | one-shots, empty hands |
| spin_attack, parry, shield_ready, shield_bash | 24, 18, 30, 20 | | the sword and shield; `shield_ready` loops |
| axe_idle, axe_walk, axe_run, axe_sprint, axe_crouch_idle, axe_crouch_walk | 48, 28, 20, 16, 48, 32 | | loops, the walks at the first six's speeds (crouched 0.9 m/s); the same six for `bow_` and `crossbow_` |
| axe_attack, axe_heavy_attack, axe_spin_attack, axe_crouch_attack, axe_parry, axe_block | 24, 32, 30, 24, 18, 30 | | `axe_block` loops |
| bow_draw, bow_fire, bow_crouch_draw, bow_crouch_fire, bow_parry, bow_block | 24, 18, 24, 18, 18, 30 | | the draw ends held at full draw; `bow_block` loops |
| crossbow_fire, crossbow_crouch_fire, crossbow_parry, crossbow_block | 30, 30, 18, 30 | | `crossbow_block` loops |
| axe_jump, bow_jump, crossbow_jump | 27 | 0.9 | one-shots: the jump's take-off and landing, the weapon held through it |

**Holdouts.** Each layer was rendered with what can hide it set to holdout: the
body for every layer; for the garments the body and, where one tucks into or
slips under another, that one too (the jeans by the boots, the shirt by the
jeans, the bangle by the gloves) but nothing worn over them, so swapping a shirt
for another leaves no holes in the layers above; for hair, hat and the carried
items everything worn under them in the outfit above (the beanie is cut against
`beanie_long`, the hair it goes with, and the laced shirt and dress only
against the body); the cape only against the body, so another hair or no hat
leaves no hole in it (see Layers). A layer that is
out of sight in some frame (the nose from behind, say) still has that frame, a
blank cell with one pixel of alpha 1/255 so the sheet keeps its frame count.

**Wearing it.** Pick the items in Pause → Wardrobe, or dress the character in
one go with a `config/wardrobe.json` beside the game:

```json
{"body": "feminine", "underwear": "cotton_briefs", "bra": "leather_bralette",
 "shoes": "leather_ankle_boots", "pants": "denim_jeans", "shirt": "laced_shirt",
 "belt": "leather_belt", "necklace": "gold_pendant", "wristwear_left": "gold_bangle",
 "sheath": "sword_right_hip", "ears": "round", "earrings": "gold_hoops",
 "nose": "straight", "eyes": "hazel", "eyebrows": "arched", "mouth": "full",
 "hair": "beanie_long", "hat": "beanie", "other": "wool_mantle",
 "carry_left": "sword", "carry_right": "round_shield",
 "style": "px128", "hand": "left",
 "colours": {"hair": "royal_blue", "hat": "platinum", "shirt": "honey_blonde",
             "necklace": "silver", "wristwear_left": "silver"}}
```

Leave the sword out to find it in the demo and pick it up (E).

**Styles.** Pick a **style** in Pause → Wardrobe - *512 px*, *Pixel 128* or
*Pixel 64* - to draw every worn item from its version in that style: an item
without one is drawn as it is, and in *512 px* an item that is only pixel art
is drawn from its 128-pixel version. The items keep their names, so the lists
show each item once. In `config/wardrobe.json` it is `"style": "px128"` or
`"px64"` (leave it out for the renders), `"hand": "left"` / `"right"` (leave it
out for the hand the sword is in) and `"colours"` by layer; in a demo script
`style px64`, `hand left`, `wear hat beanie` and `colour hair royal_blue`. How
the pixel art is made is step 14 of the 3D-Modeling README.

The first outfit fits the default budget at full size. With every layer's
sheets for the view on screen loaded, plus that view's other five states and
the two neighbouring directions, the HUD's Sprites line settles at "162 sheets
resident (422 MB) · 0 loading"; as whole 512-pixel frames the same sheets would
be about 4.9 GB (see [Memory](#memory)).

### His sheets (`assets/sprites_masculine/`)

The same items drawn on the masculine body, `body/masculine`, made from
3D-Modeling's `generic_masculine_model/` (its README's *The masculine
model*): her finished body fitted to his shape, everything of hers moved
onto him and her scripts run on his rig. He is 1.80 m tall at the same
scale, so he stands a little taller in the same frame. Every item in the
table above is there under the same name - the laced dress, the bralette,
the makeup and the long hair styles too - as pixel art only (`_px128`,
`_px64` and their `_lh`; in the *512 px* style his items are drawn from
their 128-pixel versions), in the same states and frame counts (a carried
item in its stance's), cut
against the same outfit, in the same palette and with the same colour
options. His close-ups are in `assets/closeups_masculine/`. Wear him with
Pause → Wardrobe → Base body, or `"body": "masculine"` in
`config/wardrobe.json`; the rest of the outfit carries over as it is.
