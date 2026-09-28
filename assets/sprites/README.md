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
├── shirt/           <item>/
├── gloves/          <item>/
├── wristwear/       <item>/
├── ears/            <item>/
├── earrings/        <item>/
├── nose/            <item>/
├── eyes/            <item>/
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

## File names

```
<state>_<elevation>_<direction>.png          e.g.  walk_middle_ne.png
```

| Part | Values |
|---|---|
| state | `idle` `walk` `run` `sprint` `jump` `attack` |
| elevation | `side` (rendered at 0°) · `middle` (45°) · `top` (90°, birds-eye) |
| direction | `s` `se` `e` `ne` `n` `nw` `w` `sw` |

That is 6 × 3 × 8 = **144 sheets** for a complete item.

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
  "anchors": { "side": [0.5, 0.9], "middle": [0.5, 0.8], "top": [0.5, 0.5] }
}
```

Every field is optional. `anchors` is where the feet are in a frame of each
elevation, as fractions of the frame from its top-left corner — set it and the
pivot numbers no longer matter.

## Layers

The layers draw in this order, bottom to top:

```
body → underwear → bra → shoes → pants → shirt → gloves → wristwear → ears →
earrings → nose → eyes → mouth → hair → hat → other → carry_left → carry_right
```

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

## Fallbacks

- **No body sheets** (or a view missing from them): the game draws its own
  32 × 32 fallback character for that view, scaled up — every state, all 24
  views, generated at start-up.
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
and are evicted least-recently-used past a video-memory budget of 1.5 GB. For
smaller GPUs use Settings → Sprite resolution, or launch with
`-Dlarsons.sprites.scale=0.5` / `-Dlarsons.sprites.vramMB=768`.

## Trying it without renders

```bash
./gradlew sampleSprites
```

writes a complete stand-in set to `build/sample-sprites/` — a body, a red cap
and the sword on separate held-out layers, plus one animation as loose frames —
rendered from the fallback figure at 256 px. Drop its folders on the window to
see the importer and the layer stack at work.

## The sheets in this folder

Everything here is rendered from the rigged feminine model in
[3D-Modeling](https://github.com/Larleeloo/3D-Modeling) (`generic_feminine_model/`,
steps 10-13 of its README): at least one item for every layer - and the model's
other ears, noses, eyes and mouths as alternatives - all 144 sheets each, at the
default framing above (no `profile.json`). She is 1.70 m tall. The first item
of each layer below is the outfit the holdouts were cut against.

| Layer | Item | |
|---|---|---|
| body | `feminine` | the base body with brows (nude; the face's cut-outs are capped, so it is whole even with the nose, mouth and eye layers empty) |
| underwear | `cotton_briefs` | lavender cotton briefs |
| bra | `leather_bralette` | a lace-up leather bralette |
| shoes | `leather_ankle_boots` | brown leather ankle boots |
| pants | `denim_jeans` | jeans, **cloth-simulated** per animation |
| shirt | `plaid_flannel` | a red plaid flannel shirt, **cloth-simulated** per animation |
| gloves | `black_leather` | black leather gloves |
| wristwear | `gold_bangle` | a gold bangle on the right wrist |
| ears | `round`, `pointed`, `elven` | human ears; half-elf points; long, swept-back elven ears |
| earrings | `gold_hoops` | gold hoops |
| nose | `straight`, `button`, `aquiline` | |
| eyes | `hazel`, `ice_blue`, `amber_slit` | the amber eyes have slit pupils and a faint glow |
| mouth | `full`, `wide`, `heart` | lips, teeth and tongue |
| hair | `long_waves_brown` | long brown waves (spring-simulated) |
| hat | `straw_sun_hat` | a wide-brimmed straw hat with a navy ribbon |
| other | `wool_mantle` | a short red wool mantle over the shoulders, **cloth-simulated** |
| carry_left | `round_shield` | a round wooden shield strapped to the left forearm |
| carry_right | `sword` | a short arming sword - the demo's pick-up sword (`items/sword/icon.png` is its pickup) |

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

**Holdouts.** Each layer was rendered with what can hide it set to holdout: the
body for every layer; for the garments the body and, where one tucks into or
slips under another, that one too (the jeans by the boots, the shirt by the
jeans, the bangle by the gloves) but nothing worn over them, so swapping a shirt
for another leaves no holes in the layers above; for hair, hat, other and the
carried items everything worn under them in the outfit above. A layer that is
out of sight in some frame (the nose from behind, say) still has that frame, a
blank cell with one pixel of alpha 1/255 so the sheet keeps its frame count.

**Wearing it.** Pick the items in Pause → Wardrobe, or dress the character in
one go with a `config/wardrobe.json` beside the game:

```json
{"body": "feminine", "underwear": "cotton_briefs", "bra": "leather_bralette",
 "shoes": "leather_ankle_boots", "pants": "denim_jeans", "shirt": "plaid_flannel",
 "gloves": "black_leather", "wristwear": "gold_bangle", "ears": "round",
 "earrings": "gold_hoops", "nose": "straight", "eyes": "hazel", "mouth": "full",
 "hair": "long_waves_brown", "hat": "straw_sun_hat", "other": "wool_mantle",
 "carry_left": "round_shield", "carry_right": "sword"}
```

Leave `carry_right` out to find the sword in the demo and pick it up (E). A
full outfit is a lot of pixels: if a small GPU struggles, lower Settings →
Sprite resolution (see [Memory](#memory)).
