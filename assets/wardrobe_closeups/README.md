# Wardrobe close-ups

The wardrobe's **Close up**: every item that shows from the waist up, at rest,
from all eight sides, so the mirror can be turned round a close look at her.
The same kind of layers as the cutscenes' close-ups
([`assets/closeups/`](../closeups/README.md)) - one per item, cut against the
same outfit as the game sprites, stacked in the game's draw order, recoloured
as they are drawn - in the sprites' rest stance instead of the conversation
clips.

```
assets/wardrobe_closeups/<layer>/<item>_px128/rest_side_<dir>.png             the feminine body's
assets/wardrobe_closeups_masculine/<layer>/<item>_px128/rest_side_<dir>.png   the masculine body's
assets/wardrobe_closeups*/<layer>/<item>_px128/profile.json                   the close framing
assets/wardrobe_closeups*/<layer>/<item>_px128/variants.json                  the item's colours
```

`<dir>` is one of `s`, `se`, `e`, `ne`, `n`, `nw`, `w`, `sw`, as the sprites'
directions. The framing is the cutscenes' (0.62 m wide, aimed 1.50 m above the
feet). The rest is 90 frames long; the close-ups take every other one (45
frames), played at half the rate, so a loop lasts as long. Only 128-pixel
frames are drawn: the wardrobe's small sprites use them too.

An item without a close-up yet is simply not drawn there; a body without one
shows her sprite brought close instead.

They are rendered from the rig in [3D-Modeling](https://github.com/Larleeloo/3D-Modeling):

```
tools/run_in_blender.sh generic_feminine_model.blend feminine_model_pixel_passes LOG CACHE closeup packed \
    states=rest views=side:s,side:se,side:e,side:ne,side:n,side:nw,side:w,side:sw step=2
python3 feminine_model_pixel_art.py CACHE <Larsons-Game>/assets/wardrobe_closeups sizes=128 palette=pixel_palette.json
```

(the masculine model's `masculine_model_*` scripts the same, into
`wardrobe_closeups_masculine`). The Khorigale's horns are drawn on them by
`tools/sprites/make_khorigale.py --closeups`.
