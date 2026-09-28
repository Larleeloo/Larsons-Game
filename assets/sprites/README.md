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
