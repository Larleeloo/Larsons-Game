# Interface pictures

## `inventory_background.png`

The inventory's background (press **I** in the demo): a **432 × 768** picture
(any picture of that shape works), drawn behind the inventory's slots,
scaled to the height of the window but never above its own size. Until it is
here, a plain dark panel is drawn instead. The game reads it again whenever
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
