# Scenery — the contract

The demo's meadow is planted from this folder: the trees, the grass tufts, the
rocks and the ground - the grass, and the dirt, forest floor, gravel and
flower meadow laid over it in places - and so are the nine biomes' plants,
trees, rocks and grounds that Creative Mode paints and sows (`world/Biome`). Everything here is pixel art in
the game's 128-colour palette, rendered and drawn in Blender by the scripts in
[3D-Modeling](https://github.com/Larleeloo/3D-Modeling)'s `scenery/` folder,
which write these files exactly as the game reads them.

---

## Folder layout

```
assets/scenery/
├── ground/
│   ├── grass.png        512 × 512, opaque, seamless when tiled
│   ├── dirt.png         the same size: the paths' trodden earth
│   ├── forest_floor.png             the groves' fallen leaves, needles and moss
│   ├── gravel.png                   the rock beds' stones
│   ├── meadow.png                   the grass thick with flowers
│   ├── sand.png, mud.png, snow.png, stone.png        the biomes' grounds: the shore, the marsh,
│   ├── jungle_floor.png, dry_grass.png, ash.png      the mountains, the jungle, the savannah,
│   ├── crystal.png                                   the waste, the crystal biome
│   └── profile.json     {"tileWorldSize": 9.6} - the metres one tile spans
├── oak_tree/            trees: 48 frames each, swaying in the breeze
├── pine_tree/             (the pine and the birch 128 × 160, taller than square)
├── birch_tree/
├── autumn_tree/
├── willow_tree/         the biomes' trees: willow (marsh, river), palm (shore, jungle),
├── palm_tree/             jungle giant 128 × 192, acacia 160 × 128 (savannah), dead tree
├── jungle_tree/           (waste), snowy pine (mountains), crystal tree (crystal, glowing)
├── acacia_tree/
├── dead_tree/
├── snowy_pine/
├── crystal_tree/
├── grass_tuft_a/        grass tufts: 24 frames each, blowing in the breeze
├── grass_tuft_b/
├── grass_tuft_c/
├── tall_grass_a/        the biomes' plants: tall grass (bends as she walks through), reeds,
├── tall_grass_b/          fern, dry grass, tall dry grass, beach grass, thorn bush,
├── reeds/                 crystal grass, wildflowers
├── fern/ dry_grass_tuft/ tall_dry_grass/ beach_grass/ thorn_bush/ crystal_grass/ wildflowers/
├── boulder/             rocks: one frame each, standing still
├── mossy_rock/
├── stones/
├── standing_stone/
├── sandstone_rock/
├── flat_rock/
└── snow_boulder/ river_stones/ beach_rock/ termite_mound/ ash_spire/ mossy_log/
    crystal_cluster/ crystal_spire/ iridescent_rock/      the biomes' rocks (the crystals glowing)
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
| `frameWidth`, `frameHeight` | one frame's pixels | 128 × 128 (pine, birch 128 × 160) | 32 × 32 | 128 × 128 |
| `frames`, `fps` | the loop; 0 or none given: every cell of the grid | 48 at 12 | 24 at 12 | 1 |
| `frameWorldSize` | the metres one frame spans across | 4.8 | 0.6 | 2.4 |
| `radius` | metres round the axis she cannot walk into (0: walked through) | 0.2 – 0.3 | 0 | 0.4 – 0.65 |
| `shadowRadius` | a soft round shadow under it (0: none) | 1.2 – 1.6 | 0 | 0.7 – 1.0 |
| `facing` | `cross`: two cards crossed at right angles, like Minecraft's plants; `camera`: one card facing the camera | cross | cross | camera |
| `biomes` | the biomes it grows in (`plains`, `marsh`, `mountains`, `jungle`, `savannah`, `ocean`, `river`, `waste`, `crystal`): the biome brush and the scatter brush's biome sets sow it there. None: it is the demo's meadow's, planted in the demo and on the plains | | | |
| `glow` | a colour (`"#7FE8FF"`) it shines with at night, lighting the ground round it | crystal tree | | the crystals |
| `bend` | `true`: tall grass that leans aside as someone walks through it | | tall grass, reeds | |

A field left out keeps its kind's default (the sizes above), and a profile
with no `kind` is taken to be the kind its folder's name suggests (`tuft`,
`grass`, `fern` or `flower`: a tuft; `rock`, `stone`, `boulder` or
`pebble`: a rock; anything else: a tree). The rocks face the camera because
a low, wide rock drawn as a cross reads as an X from above; give one
`"facing": "cross"` to cross it like the rest.

The pixel sizes keep the art in step with the characters: a tuft, a rock and
the ground are drawn at the characters' 1.875 cm a pixel, the trees at twice
that (3.75 cm), so a 128-pixel frame holds the 4.4 m oak and a 160-pixel one
the 5.7 m pine. A frame taller than it is wide stands `frameWorldSize` across
and as much taller as its pixels are.

## The ground

`ground/grass.png` covers the whole floor; the other textures in `ground/`
are laid over it where the demo's layout puts them (`world/SceneryLayout`'s
`GroundMap`): `dirt` on the paths winding out from the spawn, `forest_floor`
under the groves, `gravel` round the rock beds, `meadow` in open patches -
and, in a level, wherever Creative Mode's Paint tool lays them, the biomes'
eight among them. All
are 512 × 512, opaque, seamless and tiled every `tileWorldSize` metres, so
their pixels line up with the grass's. Where one meets the grass the edge is
pixel-hard and ragged (`world/GroundTexture`); a texture that is missing
leaves its patches grass.

## Where the demo plants it

Four paths of earth run out from the spawn; trees stand on the old spots
(each species in turn), round where the cottage was, scattered, and in eight
groves of one species each on forest floor; rocks lie on the old spots,
scattered, and in five beds on gravel; six flower meadows open up away from
the groves; grass tufts grow everywhere but on the paths. The groves, rock
beds, meadows and paths keep out of the hills north of the spawn (x −20 to
22, z −55 to −18), where the vertical demos' terrain rises. The same seed
plants the same world every run.

## Adding and changing scenery

A new folder with a `sheet.png` and a `profile.json` is picked up at the next
start: a new tree, tuft or rock with no `biomes` joins the others of its kind
in the meadow - a new tree gets groves of its own (the layout is the same
every run for the same sprites); one that names its biomes is sown there by
the biome brush, and is on Creative Mode's palette and scatter sets either
way. A folder without a
sheet, or whose sheet is not a grid of its profile's frames, is skipped with
a message in the console; with no `ground/grass.png` the ground is the plain
studio floor, and with no folder at all there is simply no scenery.

To redraw the art, run the scripts in 3D-Modeling's `scenery/` folder (its
README says how) and copy their output folders here.
