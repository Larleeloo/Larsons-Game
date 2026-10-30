# Sounds

Every sound the game makes is a file in this folder, named after
what it is the sound of. MP3 or WAV (also AIFF and AU); MP3 is
read by the game's own decoder, so no codec is needed.

The files here now are **placeholders**, made in code by
`tools/sounds/make_placeholders.py` - replace any of them with a
real recording of the same name. You can also pick your own file
for any sound in the game without touching this folder: Pause →
Sounds, one tab per category (footsteps and movement, voices,
weapons, hits and magic, creatures, world and loot, ambient,
interface), each sound with a play button, Choose…, Mute and
Reset. Those choices are yours alone (`config/sounds.json`).

## Naming

Each folder is an object, and each file is named after the
action it plays on:

    player/footstep_grass.mp3         a footstep on grass (each foot, as it comes down)
    player/attack.mp3                 a sword swing (once, as it starts)
    player/axe_attack.mp3             the battle-axe chop
    player/feminine/emote_laugh.mp3   her laugh
    player/masculine/hurt.mp3         his grunt when he is hurt
    chests/open.mp3                   a chest's lid thrown open
    chests/opened.mp3                 a chest standing open and lit (held)
    items/sword/equip.mp3             taking the sword in hand
    combat/attack_dummy/hit.mp3       a training dummy struck
    combat/dice_roll.mp3              the damage dice tumbling over her head
    world/loot_legendary.mp3          a Legendary item landing out of a chest
    ambient/wind.mp3                  the breeze over the meadow (held)
    ui/click.mp3                      any button clicked

A file in a body's or an object's own folder is that one's alone
and comes first; one folder up covers every one of its kind:
`player/masculine/attack.mp3` is his sword swing,
`player/attack.mp3` both bodies'; `chests/ornate_chest/open.mp3`
that chest's, `chests/open.mp3` every chest's.

A move without a file of its own sounds like the move it is a
version of: the axe's heavy attack like the axe's attack, a bow
shot from the crouch like the standing one.

A held sound (a chest standing open, the breeze, a flier
flapping) loops for as long as what it is the sound of lasts;
anything else plays once as it starts - so record a loop as one
seamless loop, and an attack from its first frame.

`SOUND_KEYS.txt` lists the exact name of every sound in the game, by
category.

## Fresh pitch

Every sound plays at a slightly different pitch each time - the
trick Minecraft uses so a run of footsteps never sounds like a
stuck record. The spread is `pitchVariation` in `soundpack.json`
(0.08 = plus or minus 8%); set it to 0 to play every sound
exactly as recorded.

## Settings

`soundpack.json` holds the volume and pitch the whole pack plays at, and an
`overrides` block for single sounds:

    "overrides": {
      "chest/ornate_chest/idle": { "volume": 0.4 },
      "player/feminine/run":     { "pitch": 1.1, "varyPitch": false }
    }

Each category's volume, and the master volume, are in Pause →
Sounds. The game picks up new files here the next time it starts
(or at once, with Rescan in the Sounds menu).
