# Music

The game's songs as MIDI files, to open, play with and rearrange in FL Studio
(or any DAW). There are twenty, all written for this game: each is shaped
like a pop song (an intro, verses, a chorus that comes back bigger, a bridge
or a break) and played by a medieval band. None of them copies another song;
the tunes and the way they go together are their own. Made for this game and
not for sale.

| File | Song | Key, tempo, length | How it goes |
|---|---|---|---|
| `lanterns_over_brasswick_medieval.mid` | Lanterns Over Brasswick, a tavern anthem | D major, 116 bpm, 1:39 | the recorder and the fiddle trade the tune over a strummed lute, the organ under the last chorus; the main menu's song |
| `the_millers_daughter_medieval.mid` | The Miller's Daughter, a country waltz | G major in 3/4, 126 bpm, 1:20 | a wooden flute and the fiddle, the dulcimer on the bridge |
| `copper_hearts_medieval.mid` | Copper Hearts, a slow ballad | A minor, 82 bpm, 1:57 | the harp picked, the flute and the fiddle singing |
| `clockwork_heart_medieval.mid` | Clockwork Heart, a clockwork dance | E minor, 122 bpm, 1:42 | the dulcimer's ticking riff alone, a fiddle verse, the recorder in the chorus |
| `ride_out_at_dawn_medieval.mid` | Ride Out at Dawn, a galloping rock song | B minor, 144 bpm, 1:33 | the tabor at a gallop, the fiddle and the shawm, the recorder in the chorus |
| `moonlit_market_medieval.mid` | Moonlit Market, a sly market groove | F major, 104 bpm, 2:00 | stabs on the lute and a walking bass viol, the psaltery and the fiddle |
| `ember_and_ash_medieval.mid` | Ember and Ash, a power ballad | C minor, 74 bpm, 2:10 | a hushed flute verse, then the chorus crashing in on the tabor |
| `tavern_lights_medieval.mid` | Tavern Lights, a pub singalong | C major, 128 bpm, 1:30 | the fiddle's verse, and the monks singing the last chorus |
| `the_long_road_home_medieval.mid` | The Long Road Home, a country road song | G major, 94 bpm, 1:42 | a boom-chick lute under the fiddle and the recorder |
| `sky_portals_medieval.mid` | Sky Portals, an epic march | D minor, 112 bpm, 1:43 | the shawm and the sackbut, the great tabor, the monks and the organ |
| `starlit_courtyard_medieval.mid` | Starlit Courtyard, a starlit lullaby | E flat major, 76 bpm, 2:06 | a wooden flute and the psaltery over a slow harp |
| `iron_and_ivy_medieval.mid` | Iron and Ivy, a punk jig | E major, 168 bpm, 1:20 | the lute flat out, the shawm, a fiddle chorus |
| `dancing_on_the_drawbridge_medieval.mid` | Dancing on the Drawbridge, a dance-floor romp | A minor, 118 bpm, 1:46 | four on the floor on the tabor, the fiddle and the shawm, a dulcimer break |
| `golden_fields_medieval.mid` | Golden Fields, a sunny lilt | A major, 90 bpm, 1:47 | an easy swaying lute, the recorder and the fiddle |
| `the_crystal_cave_medieval.mid` | The Crystal Cave, a crystal-cave mystery | F sharp minor, 100 bpm, 1:36 | a drone, a wooden flute, the dulcimer ringing high |
| `two_candles_medieval.mid` | Two Candles, a candlelit love song | B flat major, 76 bpm (in triplets), 2:06 | the harp rolling in triplets under the recorder, the flute and the fiddle |
| `steam_and_sails_medieval.mid` | Steam and Sails, a sea-shanty stomp | D minor, 106 bpm, 1:49 | a stamping shanty: the fiddle, the shawm, the monks for the last chorus |
| `heroes_of_the_hollow_medieval.mid` | Heroes of the Hollow, a stadium anthem | E major, 120 bpm, 1:36 | a fiddle verse building to the monks' chorus over hands clapped |
| `feather_and_flame_medieval.mid` | Feather and Flame, a bright, bouncing pop song | G minor, 126 bpm, 1:31 | the recorder and the fiddle bouncing, a break for the bells |
| `farewell_fair_moon_medieval.mid` | Farewell, Fair Moon, a farewell song | F major, 80 bpm, 2:00 | the recorder and the fiddle, slow, to close the night |

Each file has markers naming its sections (Intro, Verse, Chorus ...); the
song's plan in `tools/music/originals.py` lists them in order.

## The band

One track per instrument, each on its own MIDI channel with the General MIDI
sound nearest to the real thing - swap in better instruments in FL Studio.
Not every song has every one.

| Track | Channel | General MIDI sound | Stands in for |
|---|---|---|---|
| Lute | 1 | Acoustic Guitar (nylon) | a lute |
| Harp | 2 | Orchestral Harp | a medieval harp: the bass, and rolled chords |
| Recorder | 3 | Recorder | the voice: the tune |
| Fiddle | 4 | Fiddle | a vielle |
| Portative organ (drone) | 5 | Reed Organ | a portative organ or hurdy-gurdy drone |
| Hammered dulcimer | 6 | Dulcimer | a hammered dulcimer |
| Shawm | 7 | Shanai | a shawm |
| Bagpipe | 8 | Bag pipe | a bagpipe |
| Monks (choir) | 9 | Choir Aahs | voices |
| Hand drum | 10 (drums) | Low Conga, Mute High Conga, Low Floor Tom, Tambourine, Hi Bongo, Triangle, Wood Block, Cowbell | a frame drum (the deep stroke and the rim), a tabor, a riq, a goblet drum, a bell |
| Bass viol | 11 | Cello | a bass viol |
| Wooden flute | 12 | Flute | a wooden transverse flute |
| Bells | 13 | Tubular Bells | church bells |
| Psaltery | 14 | Koto | a psaltery |
| Sackbut | 15 | Trombone | a sackbut |
| Harpsichord | 16 | Harpsichord | a harpsichord |

## Opening one in FL Studio

**File → Import → MIDI file**, pick the `.mid`, and leave "Start new
project" and "Accept tempo" ticked: each track arrives as its own channel
and pattern, at the song's tempo.

## From the MIDI to the game

The game plays `assets/sounds/music/<song>.mp3`, rendered from the MIDI by
`tools/music/originals.py`, which holds every song's plan:

```
python3 tools/music/originals.py                  # every song: its MIDI and the game's MP3
python3 tools/music/originals.py copper_hearts    # just this one
python3 tools/music/originals.py --midi           # just the MIDI files
python3 tools/music/originals.py --list           # the songs, their keys, tempos and lengths
```

Rendering needs FluidSynth with the FluidR3 General MIDI SoundFont and ffmpeg
(`sudo apt install fluidsynth fluid-soundfont-gm ffmpeg`) and
`pip install numpy scipy mido`; the MIDI alone needs only mido. A new song is
a new plan in `originals.py`, its MP3 in `assets/sounds/music/`, and a line
in `audio/Songs.java`.

A song's plan is its sections in order, each with its chords as chord names
(`"Am | F | C | G"`), its tune as note names (`"A4e C5q. B4e A4q."`, see
`tools/music/arrange.py`), how hard the band plays (0 to 3) and who carries
the tune; its style says what the lute, the bass, the drums and the rest do
at each strength. A change made there comes out in both files. A change made
in FL Studio goes the other way: export the song from FL Studio as a WAV or
MP3 and put it in the game over `assets/sounds/music/<song>.mp3` - make it
exactly as long as the song's bars, with the reverb's tail mixed back over
the start, so it loops without a gap. You can also try any MP3 in the game
without touching the repository: **Pause → Sounds → Music → Choose…** on the
song.

Where the songs play: the main menu plays Lanterns Over Brasswick; a level
plays what was picked for it in Creative Mode's Level tool: one song, a
playlist of several played in turn and round again, all of them shuffled
(the demo, and a new level), or none.
