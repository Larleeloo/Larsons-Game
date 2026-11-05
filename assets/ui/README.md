# Interface pictures

## `inventory_background.png`

The inventory's background (press **I** in the demo): a **432 × 768** picture
(any picture of that shape works), drawn behind the inventory's slots,
scaled to the height of the window but never above its own size. Without it
a plain dark panel is drawn instead.

The one here is a chest-lid of oak planks bound in iron, in the game's
128-colour palette: the title on a carved plaque, the slots on a stitched
leather panel, the hotbar on a studded belt. `python3
tools/ui/make_inventory_background.py` draws it again (a pixel at a time, at
half size, doubled). A barrel, crate or storage chest opened beside the
inventory is drawn on the same picture, its "Take all" and "Put all in" on
the belt. The game reads it again whenever
the file changes, so a new one shows the next time the inventory opens.

The slots are drawn over it in these places, as fractions of the picture, so
it can be drawn round them:

| What | Across | Down |
|---|---|---|
| The title ("Inventory") and the count | centred | 0 – 10 % |
| The inventory's slots: diamonds in an interlocking lattice, from the top of the box down | 7 – 93 % | 11 – 74 % |
| The hotbar: five diamonds in a row, 1 – 5 | 7 – 93 % | 78 – 96 % |

Each slot is a diamond (a square stood on its corner) with a white border
and a dark, partly transparent middle; the selected hotbar slot's border is
gold.
