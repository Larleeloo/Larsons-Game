#!/usr/bin/env python3
""""Beat It" (Michael Jackson) as a medieval piece: the record's guitar riff
on a fiddle and a bass viol together, the hand drum's backbeat, the
recorder singing, and the chorus shouted by the monks.

E minor, 138 bpm, 56 bars (a minute and a half), made to loop:

    bars  1-4   Riff        the fiddle and the viol
    bars  5-12  Verse       "They told him don't you ever come around here ..."
    bars 13-16  Pre-chorus  "So beat it, just beat it ..."
    bars 17-24  Chorus      "Just beat it, beat it, beat it, beat it ..."
    bars 25-32  Solo        the shawm over the riff
    bars 33-40  Verse       the riff under the recorder again
    bars 41-48  Chorus      everyone
    bars 49-56  Riff        into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/beat_it.py           # the MIDI and the game's MP3
    python3 tools/music/beat_it.py --midi    # just the MIDI
"""

from arrange import Band

RIFF_CHORDS = "Em D | Em D"
RIFF = "E3e E3 B3 E4 D4 B3 A3 B3 | E3e E3 B3 E4 G4e. F#4s E4e D4e"
VERSE_TUNE = ("re B4e B4 B4 D5 B4 A4 G4 | A4q B4h rq | re B4e B4 B4 D5 B4 A4 G4 | A4q E4h rq | "
              "re B4e B4 B4 D5 E5 D5 B4 | D5q B4h rq | re B4e B4 A4 G4 A4 B4 A4 | E4h. rq")
PRE = "C | D | C | D"
PRE_TUNE = "rq E5e D5 E5q D5q | B4h rh | rq E5e D5 E5q G5q | F#5h rh"
CHORUS = "Em D | C D | Em D | C D | Em D | C D | Em D | C B"
CHORUS_TUNE = ("rq E5e. D5s rq E5e. D5s | rq E5e. D5s rq E5e. D5s | re B4e D5 E5 G5 E5 D5 B4 | D5q E5h rq | "
               "rq E5e. D5s rq E5e. D5s | rq E5e. D5s rq E5e. D5s | re B4e D5 E5 G5 E5 D5 B4 | D5q C5q D#5h")
SOLO = ("B5e A5 G5 E5 D5 E5 G5 A5 | B5q. A5e G5q E5q | G5e F#5 E5 D5 B4 D5 E5 G5 | E5h rh | "
        "B5e A5 G5 E5 D5 E5 G5 B5 | D6q. B5e A5q G5q | A5e G5 E5 D5 E5 G5 A5 G5 | E5h rh")

BACKBEAT = "D...T...D.D.T..."
DRIVE = "D.jtT.jtD.DtT.jt"


def build():
    band = Band("Beat It (medieval)", bpm=138, key="Em",
                credit="Beat It (Michael Jackson), a medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Riff"), (5, "Verse"), (13, "Pre-chorus"), (17, "Chorus"), (25, "Solo"),
                      (33, "Verse"), (41, "Chorus"), (49, "Riff")):
        band.mark(bar, name)
    band.mix("viol", volume=110)

    def riff(bar, bars, vel=84, drums=BACKBEAT):
        spans = band.chords(bar, " | ".join([RIFF_CHORDS] * (bars // 2)))
        for k in range(0, bars, 2):
            band.lead(bar + k, RIFF, vel, part="fiddle", legato=.8, shift=12)
            band.lead(bar + k, RIFF, vel, part="viol", legato=.8)
        band.drums(bar, bars, drums, vel=.9)
        return spans

    riff(1, 4)
    riff(5, 8, 72)
    band.lead(5, VERSE_TUNE, 84, shift=12)

    pre = band.chords(13, PRE)
    band.strum(pre, 62, "D.......D.D.D...")
    band.bass(pre, 76, "R.R.R.R.", part="viol", low=40)
    band.lead(13, PRE_TUNE, 88, shift=12)
    band.drums(13, 4, BACKBEAT, vel=.9, fill="D...T...TTTTTTTT")

    for start, big in ((17, False), (41, True)):
        ch = band.chords(start, CHORUS)
        band.strum(ch, 66, "D.xUD.xUD.xUD.xU")
        band.bass(ch, 80, "R.R.R.R.", part="viol", low=40)
        band.lead(start, CHORUS_TUNE, 92, shift=12)
        band.lead(start, CHORUS_TUNE, 72, part="monks", legato=.98)
        band.drums(start, 8, DRIVE, vel=1.0)
        if big:
            band.harmony(start, CHORUS_TUNE, 76, shift=12)
            band.pad(ch, 62)

    solo = riff(25, 8, 70, DRIVE)
    band.lead(25, SOLO, 86, part="shawm", legato=.9)

    riff(33, 8, 72)
    band.lead(33, VERSE_TUNE, 86, shift=12)
    band.harmony(33, VERSE_TUNE, 60, part="monks")

    riff(49, 8)
    band.bars(56)
    return band


if __name__ == "__main__":
    build().save("beat_it", doc=__doc__)
