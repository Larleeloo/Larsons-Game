#!/usr/bin/env python3
""""Fast Car" (Tracy Chapman) as a medieval piece: the record's famous
picked riff on the lute in running sixteenths, the recorder singing, the
harp and a soft hand drum, and the fiddle for the chorus.

D major, 104 bpm, 48 bars (just under two minutes), made to loop:

    bars  1-4   Riff        the lute alone
    bars  5-12  Verse       "You got a fast car, I want a ticket to anywhere ..."
    bars 13-20  Verse       "... Starting from zero, got nothing to lose"
    bars 21-28  Chorus      "So remember when we were driving, driving in your car ..."
    bars 29-32  Riff
    bars 33-40  Chorus      everyone
    bars 41-48  Riff        the recorder remembering the verse, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/fast_car.py           # the MIDI and the game's MP3
    python3 tools/music/fast_car.py --midi    # just the MIDI
"""

from arrange import Band

RIFF = "Dadd9 | A | F#m | E"
VERSE_TUNE = ("re A4e A4 A4 A4 F#4 A4q | B4e A4 F#4h rq | re A4e A4 A4 B4 C#5 B4 A4 | B4q. A4e F#4h | "
              "re A4e A4 A4 A4 F#4 A4q | B4e A4 F#4h rq | re A4e B4 C#5 E5 C#5 B4 A4 | E4h. rq")
CHORUS = "G | A | F#m | Bm | G | A | D | E"
CHORUS_TUNE = ("re B4e B4 D5 B4q. A4e | B4q A4h rq | re A4e A4 C#5 A4q. F#4e | A4q F#4h rq | "
               "re B4e B4 D5 B4q. D5e | E5q C#5h rq | re D5e D5 E5 F#5 E5 D5 C#5 | B4h. rq")

SOFT = "D.....t.d.....t."


def build():
    band = Band("Fast Car (medieval)", bpm=104, key="D",
                credit="Fast Car (Tracy Chapman), a medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Riff"), (5, "Verse"), (21, "Chorus"), (29, "Riff"), (33, "Chorus"), (41, "Riff")):
        band.mark(bar, name)

    def riff(bar, bars, vel=60):
        spans = band.chords(bar, " | ".join([RIFF] * (bars // 4)))
        band.arp(spans, vel, "B1231323b3231323", step=.25, ring=.6)
        return spans

    riff(1, 4, 62)
    verse = riff(5, 16, 58)
    band.bass(verse, 70, "R.......")
    band.lead(5, VERSE_TUNE, 82, shift=12)
    band.lead(13, VERSE_TUNE, 84, shift=12)
    band.harmony(13, VERSE_TUNE, 60, part="fiddle", shift=12)
    band.drums(13, 8, SOFT, vel=.7)

    for start, big in ((21, False), (33, True)):
        ch = band.chords(start, CHORUS)
        band.arp(ch, 58, "B1231323b3231323", step=.25, ring=.6)
        band.bass(ch, 76, "R...R...")
        band.lead(start, CHORUS_TUNE, 90, shift=12)
        band.hold(ch, 72)
        band.drums(start, 8, "D...T.t.D.d.T...", vel=.85)
        if big:
            band.harmony(start, CHORUS_TUNE, 64, part="monks")
            band.pad(ch, 58)

    riff(29, 4, 60)
    band.drums(29, 4, SOFT, vel=.7)
    end = riff(41, 8, 58)
    band.bass(end, 64, "R.......")
    band.lead(41, VERSE_TUNE[:VERSE_TUNE.index("| re A4e A4 A4 A4 F#4")], 66, shift=12)
    band.bars(48)
    return band


if __name__ == "__main__":
    build().save("fast_car", doc=__doc__)
