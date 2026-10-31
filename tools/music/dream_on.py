#!/usr/bin/env python3
""""Dream On" (Aerosmith, written by Steven Tyler) as a medieval tavern piece:
the main menu's music, and one of the songs a level can play.

The song's own bones are kept - the falling bass under the minor chord that
opens it (F, E, E flat, D, D flat, C: the old lament bass), and the chorus
stepping down F minor, E flat, D flat, C - and given to a lute, a harp, a
recorder for the voice, a fiddle, a portative organ's drone and a hand
drum. The tune is played from memory, so a note here and there may not be
the record's; fix it in the MIDI.

    python3 tools/music/dream_on.py            # music/dream_on_medieval.mid + the game's MP3
    python3 tools/music/dream_on.py --midi     # just the MIDI
    python3 tools/music/dream_on.py --wav x.wav  # also a WAV, to listen to

Writes music/dream_on_medieval.mid (open it in FL Studio) and
assets/sounds/music/dream_on.mp3 (a seamless two-minute loop). An
arrangement for Larson's Game, not for sale.

40 bars of 4/4 at 80 bpm (two minutes exactly), in F minor:

    bars  1-8   Intro       the lute alone on the falling bass, the harp joining
    bars  9-16  Verse       the recorder takes the tune
    bars 17-24  Pre-chorus  strummed lute, the drum, the fiddle and the drone come in
    bars 25-32  Chorus      everyone; the harp in running notes
    bars 33-40  Outro       back to the falling bass, quieter, into the intro again
"""

import argparse
import os

import medieval as m

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
MIDI = os.path.join(ROOT, "music", "dream_on_medieval.mid")
MP3 = os.path.join(ROOT, "assets", "sounds", "music", "dream_on.mp3")

# Chords as (bass, three notes above it), MIDI note numbers (60 = middle C).
FM = (53, (60, 65, 68))          # F minor
FM_E = (52, (60, 65, 68))        # ... over E
FM_EB = (51, (60, 65, 68))       # ... over E flat
FM_D = (50, (60, 65, 68))        # ... over D
DB_MAJ7 = (49, (60, 65, 68))     # D flat with a C on top
DB = (49, (61, 65, 68))          # D flat
EB = (51, (58, 63, 67))          # E flat
C_SUS = (48, (60, 65, 67))       # C with an F hanging over from the last chord
C = (48, (60, 64, 67))           # C major: the way home to F minor

LAMENT = [FM, FM_E, FM_EB, FM_D, DB_MAJ7, DB, C_SUS, C]
PRE = [DB, EB, FM, FM, DB, EB, FM, C]
CHORUS = [FM, EB, DB, C, FM, EB, DB, C]

# The tune, bar by bar: (beat, length in beats, note).
VERSE_TUNE = [
    [(0.5, .5, 72), (1, .5, 72), (1.5, .5, 72), (2, .5, 75), (2.5, .5, 77), (3, .5, 75), (3.5, .5, 72)],
    [(0, 1, 72), (1, 2.5, 68)],
    [(0.5, .5, 72), (1, .5, 72), (1.5, .5, 72), (2, .5, 75), (2.5, .5, 77), (3, .5, 80), (3.5, .5, 77)],
    [(0, 1, 80), (1, 2.5, 77)],
    [(1, .5, 72), (1.5, 1, 77), (2.5, .5, 75), (3, 1, 73)],
    [(0.5, .5, 72), (1, .5, 73), (1.5, .5, 75), (2, .5, 77), (2.5, .5, 73), (3, 1, 72)],
    [(0.5, .5, 77), (1, .5, 77), (1.5, .5, 79), (2, 2, 77)],
    [(0, .5, 79), (0.5, .5, 77), (1, .5, 76), (1.5, .5, 77), (2, .5, 79), (2.5, 1.5, 72)],
]
PRE_TUNE = [
    [(0, 1, 77), (1, .5, 77), (1.5, 1.5, 80)],
    [(0, 1, 79), (1, .5, 77), (1.5, .5, 75), (2, 2, 79)],
    [(0, 1, 80), (1, .5, 80), (1.5, .5, 79), (2, 1, 80), (3, 1, 77)],
    [(0, 1, 80), (1, .5, 79), (1.5, .5, 77), (2, 2, 77)],
    [(0, 1, 80), (1, .5, 80), (1.5, 1.5, 82)],
    [(0.5, .5, 79), (1, .5, 79), (1.5, .5, 80), (2, .5, 82), (2.5, 1.5, 79)],
    [(0, .5, 80), (0.5, .5, 79), (1, .5, 77), (1.5, .5, 77), (2, .5, 75), (2.5, .5, 77), (3, .5, 79),
     (3.5, .5, 80)],
    [(0, .5, 80), (0.5, .5, 79), (1, 1, 76), (2, 2, 79)],
]
CHORUS_TUNE = [
    [(0, 1.5, 84), (1.5, 2.5, 80)],
    [(0, 1.5, 82), (1.5, 2.5, 79)],
    [(0, 1.5, 80), (1.5, 2.5, 77)],
    [(0, .5, 79), (0.5, .5, 80), (1, .5, 79), (1.5, .5, 77), (2, .5, 76), (2.5, .5, 77), (3, 1, 79)],
    [(0, 1.5, 84), (1.5, 2.5, 80)],
    [(0, 1.5, 82), (1.5, 2.5, 79)],
    [(0, 1.5, 80), (1.5, 2.5, 77)],
    [(0, .5, 79), (0.5, .5, 80), (1, .5, 79), (1.5, .5, 77), (2, 2, 76)],
]

# F harmonic minor, for the fiddle's line a third under the tune.
SCALE = [0, 2, 3, 5, 7, 8, 10]          # F G Ab Bb C Db Eb, from F


def third_below(note, chord):
    """The note a third under `note` in F minor - E natural over a C chord, as the tune has it."""
    pc = (note - 65) % 12
    if pc == 11:                        # E natural (over C): a third under is C
        return note - 4
    i = SCALE.index(pc) if pc in SCALE else min(range(7), key=lambda k: abs(SCALE[k] - pc))
    j = (i - 2) % 7
    drop = (SCALE[i] - SCALE[j]) % 12
    below = note - drop
    if chord is C and (below - 65) % 12 == 10:     # E flat over C: make it E
        below += 1
    return below


def step_above(note):
    """The next note up the F minor scale from `note`."""
    pc = (note - 65) % 12
    above = [s for s in SCALE + [12] if s > pc]
    return note + above[0] - pc


def build():
    song = m.Song("Dream On (medieval)", bpm=80, bars=40, key="Fm",
                  credit="Dream On (Steven Tyler / Aerosmith), a medieval-style arrangement for "
                         "Larson's Game. Not for sale.")
    lute = song.part("Lute", m.LUTE, 0, volume=104, pan=46, reverb=55, seed=11)
    harp = song.part("Harp", m.HARP, 1, volume=127, pan=86, reverb=72, seed=12)
    recorder = song.part("Recorder", m.RECORDER, 2, volume=80, pan=62, reverb=66, seed=13)
    fiddle = song.part("Fiddle", m.FIDDLE, 3, volume=80, pan=34, reverb=72, seed=14)
    organ = song.part("Portative organ (drone)", m.ORGAN, 4, volume=48, pan=74, reverb=80, seed=15)
    drum = song.part("Hand drum", 0, m.DRUMS, volume=112, pan=72, reverb=38, seed=16)

    for bar, name in ((1, "Intro"), (9, "Verse"), (17, "Pre-chorus"), (25, "Chorus"), (33, "Outro")):
        song.mark(bar, name)

    def arpeggio(bar, chord, vel):
        """The lute picking through a chord in quavers: the bass, then up and down the three above."""
        bass, (a, b, c) = chord
        at = song.at(bar)
        lute.note(at, 4, bass, vel + 10)
        for i, p in enumerate((a, b, c, b, a, b, c)):
            lute.note(at + (i + 1) * .5, 1.1, p, vel + (5 if p == c else 0))

    def strum(bar, chord, vel, beats):
        """The lute strummed: down on the beats, lighter up-strokes on the offbeats."""
        bass, upper = chord
        pitches = [bass, *upper, upper[0] + 12]
        for k, beat in enumerate(beats):
            length = (beats[k + 1] if k + 1 < len(beats) else 4) - beat
            down = beat == int(beat)
            lute.chord(song.at(bar, beat), length + .05, pitches if down else pitches[1:],
                       vel if down else vel - 16, roll=.035 if down else .025, up=down)

    def tune(bar, notes, vel, part=recorder, legato=.92, shift=0):
        for beat, length, p in notes:
            part.note(song.at(bar, beat), length * legato if length < 2 else length - .1, p + shift, vel)

    def grace(bar, beat, note, vel):
        """A quick turn into a long note, the recorder's ornament: the note above, flicked."""
        recorder.note(song.at(bar, beat) - .12, .1, step_above(note), vel - 15, swing=0)

    # --- Intro: the lute alone on the lament bass, the harp's low notes joining
    for i, ch in enumerate(LAMENT):
        bar = 1 + i
        arpeggio(bar, ch, 64 if i < 4 else 68)
        if i >= 4:
            harp.note(song.at(bar), 4, ch[0] - 12, 74)

    # --- Verse: the recorder sings over it
    for i, ch in enumerate(LAMENT):
        bar = 9 + i
        arpeggio(bar, ch, 62)
        harp.note(song.at(bar), 2, ch[0] - 12, 76)
        harp.note(song.at(bar, 2), 2, ch[0], 64)
        if i >= 4:
            harp.chord(song.at(bar), 4, [p + 12 for p in ch[1]], 60, roll=.06)
        tune(bar, VERSE_TUNE[i], 80)
    grace(14, 3, 72, 80)
    grace(16, 2.5, 72, 80)
    for bar, notes in ((13, [65]), (14, [65]), (15, [65]), (16, [64])):      # the fiddle's held note, resolving
        fiddle.note(song.at(bar), 3.9, notes[0], 80)
        fiddle.swell(song.at(bar), 4, 85, 118, 95)
    for bar in range(13, 17):                                               # the drum, softly
        drum.note(song.at(bar), .5, m.DUM, 66)
        drum.note(song.at(bar, 2), .5, m.DUM, 54)
        drum.note(song.at(bar, 3), .25, m.TAK, 44)

    # --- Pre-chorus: strummed, with the drum, the fiddle and the drone
    fiddle_pre = [(0, 4, 65), (0, 4, 67), (0, 4, 68), (0, 4, 72), (0, 4, 68), (0, 4, 70), (0, 4, 68),
                  (0, 2, 67), (2, 2, 64)]
    for i, ch in enumerate(PRE):
        bar = 17 + i
        strum(bar, ch, 66, [0, 1.5, 2, 3, 3.5])
        harp.note(song.at(bar), 4, ch[0] - 12, 80)
        harp.chord(song.at(bar, 2), 2, [p + 12 for p in ch[1]], 66, roll=.05)
        tune(bar, PRE_TUNE[i], 88)
        drum.note(song.at(bar), .5, m.DUM, 88)
        drum.note(song.at(bar, 1), .25, m.TAK, 62)
        drum.note(song.at(bar, 1.5), .25, m.TAK, 52)
        drum.note(song.at(bar, 2.5), .5, m.DUM, 76)
        drum.note(song.at(bar, 3), .25, m.TAK, 62)
        drum.note(song.at(bar, 1), .25, m.JINGLE, 48)
        drum.note(song.at(bar, 3), .25, m.JINGLE, 48)
    for k, (beat, length, p) in enumerate(fiddle_pre):
        bar = 17 + min(k, 7)
        fiddle.note(song.at(bar, beat), length - .1, p, 84)
        fiddle.swell(song.at(bar, beat), length, 90, 120, 100)
    grace(17, 1.5, 80, 88)
    grace(21, 1.5, 82, 88)
    for t in (3.25, 3.5, 3.75):                                             # into the chorus
        drum.note(song.at(24, t), .25, m.TAK, 58 + int(t * 4))
    organ.note(song.at(17), 32, 48, 80)
    organ.note(song.at(17), 32, 60, 72)
    organ.swell(song.at(17), 32, 80, 105, 100)

    # --- Chorus: everyone
    for i, ch in enumerate(CHORUS):
        bar = 25 + i
        bass, (a, b, c) = ch
        strum(bar, ch, 72, [0, 1, 1.5, 2, 2.5, 3, 3.5])
        for k, p in enumerate((bass - 12, bass, a + 12, b + 12, c + 12, b + 12, a + 12, bass)):
            harp.note(song.at(bar, k * .5), 1.2, p, 64 if k else 78)
        tune(bar, CHORUS_TUNE[i], 100)
        drum.note(song.at(bar), .5, m.BIG_DUM, 84)
        drum.note(song.at(bar), .5, m.DUM, 94)
        drum.note(song.at(bar, 1), .25, m.TAK, 68)
        drum.note(song.at(bar, 1.5), .25, m.TAK, 56)
        drum.note(song.at(bar, 2), .5, m.DUM, 70)
        drum.note(song.at(bar, 2.5), .5, m.DUM, 82)
        drum.note(song.at(bar, 3), .25, m.TAK, 68)
        drum.note(song.at(bar, 3.5), .25, m.TAK, 54)
        for e in range(8):
            drum.note(song.at(bar, e * .5), .25, m.JINGLE, 54 if e % 2 == 0 else 42)
        if i < 4:                       # the fiddle under the tune: the chords' thirds, stepping down with the bass
            fiddle.note(song.at(bar), 3.9, (68, 67, 65, 64)[i], 86)
            fiddle.swell(song.at(bar), 4, 92, 122, 100)
        else:                           # then a third under the tune itself
            tune(bar, [(beat, length, third_below(p, ch)) for beat, length, p in CHORUS_TUNE[i]], 88,
                 part=fiddle, legato=.97)
    for bar in (25, 29):
        grace(bar, 0, 84, 100)
    for t in (3, 3.25, 3.5, 3.75):                                          # a roll, back down
        drum.note(song.at(32, t), .25, m.TAK, 60 + int(t * 6))
    organ.note(song.at(25), 32, 48, 88)
    organ.note(song.at(25), 32, 60, 80)
    organ.swell(song.at(25), 32, 100, 120, 95)

    # --- Outro: the lament again, quieter, the recorder remembering the verse
    drum.note(song.at(33), 1, m.BIG_DUM, 76)
    for i, ch in enumerate(LAMENT):
        bar = 33 + i
        arpeggio(bar, ch, 64 if i < 6 else 62)
        harp.note(song.at(bar), 2, ch[0] - 12, 70)
        harp.chord(song.at(bar, 2), 2, [p + 12 for p in ch[1]], 56, roll=.06)
        if i < 4:
            tune(bar, VERSE_TUNE[i], 64)
    recorder.note(song.at(37), 3.8, 77, 58)
    recorder.note(song.at(38), 3.6, 73, 52)
    grace(37, 0, 77, 58)
    return song


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--midi", action="store_true", help="write only the MIDI file")
    ap.add_argument("--wav", help="also write the looping mix as a WAV here, to listen to")
    a = ap.parse_args()
    build().save(MIDI, None if a.midi else MP3, wav_path=a.wav)


if __name__ == "__main__":
    main()
