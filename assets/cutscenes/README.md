# Cutscenes

A cutscene is a script in this folder: who is in it, what they wear, where
they stand, and a timeline of lines and effects. The game draws it from the
close-ups in [`assets/closeups/`](../closeups/README.md) - every character a
stack of layers, one per item she wears, each in any colour - so a cutscene
can star anyone, the player's own character included.

`menu.cut` is the main menu's: your character, as the wardrobe has her,
talking with Bryn. It plays behind the menu, and full screen from
**Cutscene** in the menu (Esc, Enter or Space to leave).

## The script

One command a line. `#` starts a comment (a `#rrggbb` colour is not one);
text is in double quotes. Times are in seconds.

```
actor <id> "<name>" player | <layer>=<item> ... [colour.<layer>=<colour> ...] [style=px128|px64]
stand <id> left | right
say <id> [<clip>] "<text>" [seconds]
clip <id> <clip> [seconds]
colour <id> <layer> <option> | #rrggbb | own | reset [seconds]
wear <id> <layer> <item> | none
flash <id> | all #rrggbb [seconds]
tint <id> | all #rrggbb | none [amount] [seconds]
fade <id> | all <alpha> [seconds]
wait <seconds>
loop
```

| Command | What it does |
|---|---|
| `actor` | Brings a character into the scene. `player` is the player's character: a copy of their wardrobe as it is when the scene starts (and at every loop), so nothing the scene does to it is saved. Otherwise she wears the items given, by layer, like `config/wardrobe.json` - `hair=bob shirt=laced_dress` - in the colours given - `colour.hair=platinum`, `colour.body=#d9a074` (the body's colour is the skin tone) - and in the 128-pixel art unless `style=px64`. Layers with no close-ups (shoes, trousers, belts, scabbards, what the hands carry) are not drawn. |
| `stand` | Left of the two-shot, facing right, or right, facing left (drawn mirrored). |
| `say` | A line: the speaker plays `<clip>` (`talk` if none is named) while the others listen, and the text types itself out in a box with the speaker's name. It lasts as long as it takes to read unless `seconds` says otherwise, and the script waits for it. |
| `clip` | Plays a clip - for `seconds`, then back to `listen`, or until another clip. Does not wait. |
| `colour` | Fades one layer to another colour over `seconds`: one of the item's options (`royal_blue`), any colour (`#3b7fe8` - the item's labels shaded like its own colours, round that one), the item's own colours (`own`) or what the actor started the scene with (`reset`). |
| `wear` | Changes an item (`none` takes it off). |
| `flash` | A flash of light or colour over the actor, fading out over `seconds`. |
| `tint` | Lights the actor in a colour: `amount` 0..1 of the way, over `seconds`; `none` takes it away. |
| `fade` | The whole actor to `alpha` (0 gone, 1 there) over `seconds`. |
| `wait` | Waits, with nobody speaking. |
| `loop` | At the end, start over: every actor as she began. |

Everything but `say` and `wait` starts at once and carries on while the script
moves on, so a colour can change in the middle of a line.

## Clips

The close-ups have five, every one looping and starting and ending on the same
quiet pose, so any can follow any:

| Clip | Frames | |
|---|---|---|
| `listen` | 90 | attentive: breathing, two nods, a blink, a little smile |
| `talk` | 120 | speaking: mouth shapes, the right hand open and beating with the words, brows lifting on the stresses |
| `explain` | 120 | both hands up shaping an idea, then palms down; nods, mouth shapes |
| `laugh` | 60 | an open smile, eyes narrowed, shoulders bouncing |
| `surprise` | 60 | brows up, mouth open, a lean back, a hand to the chest |

## In code

`cutscene/Cutscene.parse(script, playerWardrobe)` reads a script;
`update(dt)` plays it; `actors()` and `line()` say what is on screen, and
`cutscene/CutsceneStage.draw(ui, cutscene, x, y, w, h)` draws it. An `Actor`
can also be driven directly - `play(clip)`, `colour(layer, colour, seconds)`,
`flash`, `tint`, `fade`, its wardrobe's items - and every layer is drawn
through the GPU palette, so none of it costs a texture upload.
