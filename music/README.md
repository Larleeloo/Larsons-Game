# Music

The game's songs as MIDI files, to open, play with and rearrange in FL Studio
(or any DAW). Each is a medieval-style arrangement of a song from the
roadmap's Sound milestone, made for this game and not for sale.

| File | Song | Plays |
|---|---|---|
| `dream_on_medieval.mid` | "Dream On" (Aerosmith, written by Steven Tyler) | the main menu, looping |

## Dream On (medieval)

F minor, 80 bpm, 4/4, 40 bars: exactly two minutes, made to loop (the last
bar leads back into the first). Markers in the file name the sections:

| Bars | Section | What plays |
|---|---|---|
| 1-8 | Intro | the lute alone, picking through the song's falling bass (F minor over F, E, E flat, D, then D flat and C); the harp's low notes join |
| 9-16 | Verse | the recorder takes the tune; the fiddle and a soft drum come in at the end |
| 17-24 | Pre-chorus | the lute strummed, the hand drum, the fiddle and the organ's drone |
| 25-32 | Chorus | everyone, stepping down F minor, E flat, D flat, C; the harp in running notes, the fiddle under the tune |
| 33-40 | Outro | the falling bass again, quieter, the recorder remembering the verse, into the intro |

One track per instrument, each on its own MIDI channel with the General MIDI
sound nearest to the real thing - swap in better instruments in FL Studio:

| Track | Channel | General MIDI sound | Stands in for |
|---|---|---|---|
| Lute | 1 | Acoustic Guitar (nylon) | a lute |
| Harp | 2 | Orchestral Harp | a medieval harp |
| Recorder | 3 | Recorder | the voice: the tune |
| Fiddle | 4 | Fiddle | a vielle |
| Portative organ (drone) | 5 | Reed Organ | a portative organ or hurdy-gurdy drone, on C |
| Hand drum | 10 (drums) | Low Conga, Mute High Conga, Low Floor Tom, Tambourine | a frame drum (the deep stroke and the rim), a tabor, a riq |

The tune is played from memory, so a note here and there may not be the
record's - fix it in the piano roll.

## Opening it in FL Studio

**File → Import → MIDI file**, pick the `.mid`, and leave "Start new
project" and "Accept tempo" ticked: each track arrives as its own channel
and pattern, at 80 bpm.

## From the MIDI to the game

The game plays `assets/sounds/music/main_menu.mp3`, rendered from this MIDI
by `tools/music/dream_on.py`:

```
python3 tools/music/dream_on.py            # this MIDI, and the game's MP3 from it
python3 tools/music/dream_on.py --midi     # just the MIDI
```

The arrangement itself lives in that script (the notes are written out bar
by bar), so a change made there comes out in both files. A change made in FL
Studio goes the other way: export the song from FL Studio as a WAV or MP3
and put it in the game as `assets/sounds/music/main_menu.mp3` - make it
exactly as long as the 40 bars (two minutes) with the reverb's tail
mixed back over the start, so it loops without a gap. You can also try any
MP3 in the game without touching the repository: **Pause → Sounds → Music →
Choose…** on "The main menu's song".
