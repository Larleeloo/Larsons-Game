# Scenery — the contract

The demo's meadow is planted from this folder: the trees, the grass tufts, the
rocks and the grass the ground is drawn with. Everything here is pixel art in
the game's 128-colour palette, rendered and drawn in Blender by the scripts in
[3D-Modeling](https://github.com/Larleeloo/3D-Modeling)'s `scenery/` folder,
which write these files exactly as the game reads them.

---

## Folder layout

```
assets/scenery/
├── ground/
│   ├── grass.png        512 × 512, opaque, seamless when tiled
│   └── profile.json     {"tileWorldSize": 9.6} - the metres one tile spans
├── oak_tree/            a tree: 48 frames swaying in the breeze
├── grass_tuft_a/        grass tufts: 24 frames each, blowing in the breeze
├── grass_tuft_b/
├── grass_tuft_c/
├── boulder/             rocks: one frame each, standing still
├── mossy_rock/
└── stones/
```

Every sprite folder holds two files:

- **`sheet.png`** — the frames, left to right and then top to bottom, in the
  near-square grid that wastes the fewest cells (48 frames: 8 across, 6
  down; 24 frames: 6 across, 4 down; one frame: just the frame). Each frame
  is the plant or rock seen from the side, orthographic, its bottom edge the
  ground and its middle column the plant's axis. Pixels are fully opaque or
  fully transparent, nothing in between.
- **`profile.json`** — what it is and how it stands in the world:

```json
{"kind": "tree", "frameWidth": 128, "frameHeight": 128, "frames": 48, "fps": 12,
 "frameWorldSize": 4.8, "radius": 0.3, "shadowRadius": 1.6}
```

| Field | Meaning | Tree | Tuft | Rock |
|---|---|---|---|---|
| `kind` | `tree`, `tuft` or `rock`: where the demo's layout plants it | | | |
| `frameWidth`, `frameHeight` | one frame's pixels | 128 × 128 | 32 × 32 | 128 × 128 |
| `frames`, `fps` | the loop; 0 or none given: every cell of the grid | 48 at 12 | 24 at 12 | 1 |
| `frameWorldSize` | the metres one frame spans across | 4.8 | 0.6 | 2.4 |
| `radius` | metres round the axis she cannot walk into (0: walked through) | 0.3 | 0 | 0.45 – 0.6 |
| `shadowRadius` | a soft round shadow under it (0: none) | 1.6 | 0 | 0.75 – 0.95 |
| `facing` | `cross`: two cards crossed at right angles, like Minecraft's plants; `camera`: one card facing the camera | cross | cross | camera |

A field left out keeps its kind's default (the sizes above), and a profile
with no `kind` is taken to be the kind its folder's name suggests (`tuft`,
`grass`, `fern` or `flower`: a tuft; `rock`, `stone`, `boulder` or
`pebble`: a rock; anything else: a tree). The rocks face the camera because
a low, wide rock drawn as a cross reads as an X from above; give one
`"facing": "cross"` to cross it like the rest.

The pixel sizes keep the art in step with the characters: a tuft, a rock and
the ground are drawn at the characters' 1.875 cm a pixel, the oak at twice
that (3.75 cm), so a 128-pixel frame can hold a tree about 4.4 m tall.

## Adding and changing scenery

A new folder with a `sheet.png` and a `profile.json` is picked up at the next
start: a new tree, tuft or rock joins the others of its kind in the meadow
(the layout is the same every run for the same sprites). A folder without a
sheet, or whose sheet is not a grid of its profile's frames, is skipped with
a message in the console; with no `ground/grass.png` the ground is the plain
studio floor, and with no folder at all there is simply no scenery.

To redraw the art, run the scripts in 3D-Modeling's `scenery/` folder (its
README says how) and copy their output folders here.
