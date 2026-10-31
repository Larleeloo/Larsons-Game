# Music

The game's songs as MIDI files, to open, play with and rearrange in FL Studio
(or any DAW). Each is a medieval-style arrangement of a song from the
roadmap's Sound milestone, made for this game and not for sale.

The tunes are played by ear, from the songs' chords and how they go, not
copied from sheet music - so a note here and there will not be the
record's. Fix it in the piano roll (or in the song's script, below).

| File | Song | Key, tempo, length | How it goes |
|---|---|---|---|
| `dream_on_medieval.mid` | "Dream On" (Aerosmith) | F minor, 80 bpm, 2:00 | the lute on the falling bass, the recorder singing; the main menu's song |
| `california_dreamin_medieval.mid` | "California Dreamin'" (The Mamas & the Papas) | A minor, 112 bpm, 2:00 | the recorder sings each line and the monks answer it; a wooden flute for the flute solo |
| `wonderwall_medieval.mid` | "Wonderwall" (Oasis) | F sharp minor, 87 bpm, 2:01 | the lute strummed in sixteenths, a bass viol for the cello |
| `wake_me_up_medieval.mid` | "Wake Me Up" (Avicii) | B minor, 124 bpm, 1:41 | a folk song, and the drop as a reel for fiddle and recorder |
| `dreams_medieval.mid` | "Dreams" (Fleetwood Mac) | A minor, 120 bpm, 1:36 | F and G rocking under the lute and a hammered dulcimer |
| `dont_fear_the_reaper_medieval.mid` | "(Don't Fear) The Reaper" (Blue Öyster Cult) | A minor, 140 bpm, 1:29 | the picked riff on the lute, the monks' "don't fear the reaper", a shawm solo, and more bell |
| `livin_on_a_prayer_medieval.mid` | "Livin' On A Prayer" (Bon Jovi) | E minor, 123 bpm, 1:34 | the pulsing bass on a bass viol, the talk box's riff on a shawm, the last chorus a step up |
| `vienna_medieval.mid` | "Vienna" (Billy Joel) | B flat major, 80 bpm, 2:12 | the piano on a harpsichord, the mandolin on a dulcimer, the organ as the accordion |
| `the_less_i_know_the_better_medieval.mid` | "The Less I Know The Better" (Tame Impala) | E minor, 117 bpm, 1:38 | the strutting bass line on the viol, a dance beat, a shimmering psaltery |
| `nine_to_five_medieval.mid` | "9 to 5" (Dolly Parton) | A major, 108 bpm, 2:04 | a country-dance lute, the fiddle singing, the typewriter on wood blocks, a bagpipe chorus |
| `how_you_remind_me_medieval.mid` | "How You Remind Me" (Nickelback) | E minor, 86 bpm, 1:52 | a quiet picked verse, then the chorus crashing in on the tabor |
| `die_young_medieval.mid` | "Die Young" (Kesha) | C sharp minor, 128 bpm, 1:45 | a revel: the tabor and hands clapped, the monks shouting the chorus back, a fiddle jig |
| `sunflower_medieval.mid` | "Sunflower" (Post Malone and Swae Lee) | D major, 90 bpm, 1:47 | lazy, sunny chords on a psaltery and a lute |
| `thunder_medieval.mid` | "Thunder" (Imagine Dragons) | C major, 84 bpm, 1:54 | a war song: the great tabor's boom and the hands' clap, the monks for "thunder" |
| `beat_it_medieval.mid` | "Beat It" (Michael Jackson) | E minor, 138 bpm, 1:37 | the guitar riff on a fiddle and a bass viol together, a shawm solo |
| `basket_case_medieval.mid` | "Basket Case" (Green Day) | E flat major, 172 bpm, 1:29 | a punk jig: the lute flat out, the tabor at a gallop, a bagpipe chorus |
| `espresso_medieval.mid` | "Espresso" (Sabrina Carpenter) | A minor, 104 bpm, 1:51 | sly funk on a dulcimer's stabs and a bass viol, a psaltery bridge |
| `everlong_medieval.mid` | "Everlong" (Foo Fighters) | D major, 158 bpm, 1:37 | restless eighths on the lute over a drone on D, a quiet harp break |
| `pompeii_medieval.mid` | "Pompeii" (Bastille) | A major, 128 bpm, 1:45 | the monks' "eh-eh-oh" chant, pounding toms on the tabor, a bagpipe chorus |
| `ride_medieval.mid` | "Ride" (Twenty One Pilots) | G minor, 75 bpm, 1:55 | an easy reggae lilt: the lute's chords on the offbeats |
| `fast_car_medieval.mid` | "Fast Car" (Tracy Chapman) | D major, 104 bpm, 1:51 | the picked riff on the lute in running sixteenths |

Each file has markers naming its sections (Intro, Verse, Chorus ...); the
song's script in `tools/music/` lists them with their bars.

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
the song's script in `tools/music/`:

```
python3 tools/music/wonderwall.py            # music/wonderwall_medieval.mid, and the game's MP3 from it
python3 tools/music/wonderwall.py --midi     # just the MIDI
python3 tools/music/wonderwall.py --wav x.wav   # also a WAV, to listen to
```

The arrangement itself lives in that script - the chords as chord names
(`"F#m7 | A | Esus4 | B7sus4"`), the tunes as note names
(`"A4e C#5q. B4e A4q."`, see `tools/music/arrange.py`) - so a change made
there comes out in both files. A change made in FL Studio goes the other
way: export the song from FL Studio as a WAV or MP3 and put it in the game
over `assets/sounds/music/<song>.mp3` - make it exactly as long as the song's
bars, with the reverb's tail mixed back over the start, so it loops without
a gap. You can also try any MP3 in the game without touching the
repository: **Pause → Sounds → Music → Choose…** on the song.

Where the songs play: the main menu plays Dream On; a level plays the song
picked for it in Creative Mode's Level tool, or all of them shuffled (the
demo, and a new level), or none.
