# Sounds

Drop audio files in this folder and the game plays them. Every
animation in the game is a sound slot - both bodies' 56 states,
the treasure chest's four, what is done with an item, the
inventory - and **every one is silent until a file with its
name is here**. MP3 or WAV (also AIFF and AU); MP3 is read by the
game's own decoder, so no codec is needed.

## Naming

Each folder is an object, and each file is named after the
action it plays on:

    player/feminine/walk.mp3          her walk (held: repeats while she walks)
    player/feminine/attack.mp3        her sword swing (once, as it starts)
    player/masculine/axe_attack.mp3   his battle-axe chop
    chests/ornate_chest/idle.mp3      the chest shut, its smoke swirling (held)
    chests/ornate_chest/open.mp3      the lid thrown open (once)
    chests/ornate_chest/opened.mp3    the chest standing open and lit (held)
    chests/ornate_chest/close.mp3     the lid slammed (once)
    items/battle_axe/pickup.mp3       picking the battle axe up
    ui/inventory_open.mp3             the inventory opening (I)

A file one folder up covers every object of its kind until an
object has its own: `player/walk.mp3` is both bodies' walk,
`chests/open.mp3` every chest's opening, `items/pickup.mp3`
picking anything up.

A held animation's sound (a walk, an idle, the chest standing
open) loops for as long as the animation holds; anything else
plays once as its animation starts - so record a walk as one
seamless loop, and an attack from its first frame.

`SOUND_KEYS.txt` lists the exact name of every sound in the game.

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

The game picks up new files the next time it starts.
