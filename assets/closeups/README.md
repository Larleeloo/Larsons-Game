# Close-ups

The cutscene layers: the wardrobe's items again, rendered waist-up and close
enough to read a face, for conversation clips. A cutscene
([`assets/cutscenes/`](../cutscenes/README.md)) draws a character as a stack of
these, one per item she wears, exactly as the game draws her from
[`assets/sprites/`](../sprites/README.md) - so any character, the player's own
included, can be in a cutscene, in any items and any colours.

```
assets/closeups/<layer>/<item>_px128/<clip>_side_se.png   128-pixel frames
assets/closeups/<layer>/<item>_px64/<clip>_side_se.png    64-pixel frames
assets/closeups/<layer>/<item>_px128/profile.json         the close framing
assets/closeups/<layer>/<item>_px128/variants.json        the item's colours
```

`<layer>` and `<item>` are the sprites' (`hair/long_waves`,
`shirt/laced_dress`, `eyes/ice_blue`); `<clip>` is one of `listen`, `talk`,
`explain`, `laugh`, `surprise`.

- **Framing.** An orthographic render 0.62 m wide, aimed 1.50 m above the
  feet - from the chest to just over the head - so a 128-pixel frame shows a
  face about 50 pixels tall (12 in a game sprite). `profile.json` says so
  (`frameWorldSize`, `pivotHeight`).
- **One view.** `side_se`: from the front-left three-quarter view, the
  character turned a little to her left, towards someone right of the camera.
  A character on the right of a two-shot is drawn mirrored, so the two look
  at each other.
- **Clips.** Each loops (30 fps) and starts and ends on the same quiet pose -
  arms down, mouth closed - so a cutscene can cut from any to any as a line
  starts: `listen` 90 frames, `talk` 120, `explain` 120, `laugh` 60,
  `surprise` 60.
- **Layers.** Every item of the layers that can show in the frame: body, bra,
  shirt, necklace, gloves, both wrists, ears, earrings, nose, eyes, eyebrows,
  makeup, mouth, hair, hat and the cape (`other`). Shoes, trousers, belts and
  scabbards are below the frame, and a conversation puts away what the hands
  carry. Each layer is cut against the same outfit as the game sprites, so
  stacked in the game's draw order they line up whatever is worn.
- **Pixel art** in the game sprites' 128-colour palette, as palette PNGs with
  a `variants.json`: every layer takes every colour its sprites do, or any
  `#rrggbb`, recoloured on the GPU as it is drawn.

They are rendered from the rig in [3D-Modeling](https://github.com/Larleeloo/3D-Modeling)
(`feminine_model_conversation.py` for the clips, `feminine_model_pixel_passes.py
... closeup` and `feminine_model_pixel_art.py` for the sheets).
