#!/usr/bin/env python3
""""Everlong" (Foo Fighters, written by Dave Grohl) as a medieval piece: the
record's restless eighths strummed on the lute over a drone on D (the
record's open low string), the recorder singing, and the chorus lifted by
the tabor, the fiddle and the monks.

D major, 158 bpm, 64 bars (a minute and a half), made to loop:

    bars  1-8   Intro       the lute's eighths over the drone
    bars  9-24  Verse       "Hello, I've waited here for you, everlong ..."
    bars 25-32  Chorus      "And I wonder when I sing along with you ..."
    bars 33-40  Quiet       the harp and the recorder alone, as the record's whispered break
    bars 41-56  Chorus      everyone, twice round
    bars 57-64  Outro       the eighths again, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/everlong.py           # the MIDI and the game's MP3
    python3 tools/music/everlong.py --midi    # just the MIDI
"""

from arrange import Band

RIFF = "D | Bm7 | Gmaj7 | Gmaj7"
VERSE_TUNE = ("rq F#4h A4q | A4h. rq | rq F#4h A4q | B4h. rq | rq F#4h A4q | A4h. rq | "
              "rq E4q F#4q A4q | F#4h. rq")
CHORUS = "G | A | Bm | G | G | A | Bm | A"
CHORUS_TUNE = ("re D5e D5 D5 E5 F#5 E5 D5 | E5q. D5e B4h | re D5e D5 D5 E5 F#5 A5 F#5 | E5h. rq | "
               "re D5e D5 D5 E5 F#5 E5 D5 | E5q. D5e B4h | re B4e D5 E5 F#5 E5 D5 B4 | A4h. rq")

EIGHTHS = "D.U.D.U.D.U.D.U."
GALLOP = "B.T.D.TtB.T.D.Tt"


def build():
    band = Band("Everlong (medieval)", bpm=158, key="D",
                credit="Everlong (Dave Grohl / Foo Fighters), a medieval-style arrangement for Larson's Game. "
                       "Not for sale.")
    for bar, name in ((1, "Intro"), (9, "Verse"), (25, "Chorus"), (33, "Quiet"), (41, "Chorus"),
                      (57, "Outro")):
        band.mark(bar, name)

    def riff(bar, bars, vel, drums=None):
        spans = band.chords(bar, " | ".join([RIFF] * (bars // 4)))
        band.strum(spans, vel, EIGHTHS)
        band.drone(bar, bars, ["D3", "A3"], 72)
        if drums:
            band.drums(bar, bars, drums, vel=.85)
        return spans

    riff(1, 8, 58)
    verse = riff(9, 16, 60, "D.......T.......")
    band.bass(verse, 70, "R.......")
    band.lead(9, VERSE_TUNE, 82, shift=12)
    band.lead(17, VERSE_TUNE, 84, shift=12)
    band.harmony(17, VERSE_TUNE, 62, shift=12)

    def chorus(bar, bars, big):
        ch = band.chords(bar, " | ".join([CHORUS] * (bars // 8)))
        band.strum(ch, 68, "D.DUD.DUD.DUD.DU")
        band.bass(ch, 80, "R.R.R.R.", part="viol", low=40)
        for k in range(0, bars, 8):
            band.lead(bar + k, CHORUS_TUNE, 92, shift=12)
            band.harmony(bar + k, CHORUS_TUNE, 66, part="monks")
            if big:
                band.harmony(bar + k, CHORUS_TUNE, 76, shift=12)
        band.drums(bar, bars, GALLOP, vel=1.0, fill="B.T.D.TtB.T.TTTT")
        if not big:
            band.hold(ch, 72)
        band.pad(ch, 62)

    chorus(25, 8, False)

    quiet = band.chords(33, RIFF + " | " + RIFF)
    band.arp(quiet, 60, "B1231323", part="harp", low=43)
    band.lead(33, VERSE_TUNE, 70, shift=12)

    chorus(41, 16, True)
    riff(57, 8, 58, "D.......T.......")
    band.bars(64)
    return band


if __name__ == "__main__":
    build().save("everlong", doc=__doc__)
