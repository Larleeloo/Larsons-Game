#!/usr/bin/env python3
""""Pompeii" (Bastille, written by Dan Smith) as a medieval piece: the
record's "eh-eh-oh" chant sung by the monks, its pounding toms on the tabor
and the hand drum, the recorder singing and a bagpipe joining the last
chorus.

A major, 128 bpm, 56 bars (a minute and three quarters), made to loop:

    bars  1-8   Chant       the monks alone, then the drums
    bars  9-16  Verse       "I was left to my own devices ..."
    bars 17-24  Chorus      "But if you close your eyes, does it almost feel like nothing changed at all?"
    bars 25-32  Chant       the monks over the band
    bars 33-40  Verse       the fiddle sings it
    bars 41-48  Chorus      everyone, the bagpipe too
    bars 49-56  Chant       into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/pompeii.py           # the MIDI and the game's MP3
    python3 tools/music/pompeii.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "F#m | D | A | E"
CHANT = ("E4q E4e C#4 E4q C#4e B3 | E4q E4e C#4 E4q C#4e B3 | E4q E4e C#4 E4q C#4e B3 | E4q E4e C#4 B3h | "
         "E4q E4e C#4 E4q C#4e B3 | E4q E4e C#4 E4q C#4e B3 | E4q E4e C#4 E4q C#4e B3 | E4q E4e F#4 E4h")
VERSE_TUNE = ("re A4e A4 A4 A4 B4 A4 F#4 | A4q F#4h rq | re A4e A4 A4 A4 B4 C#5 A4 | B4q A4h rq | "
              "re A4e A4 A4 A4 B4 A4 F#4 | A4q F#4h rq | re A4e B4 C#5 E5 C#5 B4 A4 | B4h. rq")
CHORUS_TUNE = ("re C#5e C#5 C#5 E5 C#5 B4q | A4q F#4h rq | re C#5e C#5 C#5 E5 F#5 E5 C#5 | B4h. rq | "
               "re C#5e C#5 C#5 E5 C#5 B4q | A4q F#4h rq | re A4e B4 C#5 E5 F#5 E5 C#5 | B4q. A4e B4h")

TOMS = "B..b..B.D..d..D."
TOMS_BIG = "B..bT.B.D..dT.Dt"


def build():
    band = Band("Pompeii (medieval)", bpm=128, key="A",
                credit="Pompeii (Dan Smith / Bastille), a medieval-style arrangement for Larson's Game. "
                       "Not for sale.")
    for bar, name in ((1, "Chant"), (9, "Verse"), (17, "Chorus"), (25, "Chant"), (33, "Verse"), (41, "Chorus"),
                      (49, "Chant")):
        band.mark(bar, name)
    band.mix("monks", volume=120)
    loop2 = LOOP + " | " + LOOP

    def chant(bar, full):
        spans = band.chords(bar, loop2)
        band.lead(bar, CHANT, 84, part="monks", legato=.95)
        band.harmony(bar, CHANT, 70, part="monks", below=False)
        if full:
            band.strum(spans, 62, "D.DUD.DUD.DUD.DU")
            band.bass(spans, 78, "R.R.R.R.", part="viol", low=40)
            band.drums(bar, 8, TOMS_BIG, vel=1.0)
        else:
            band.drums(bar + 4, 4, TOMS, vel=.85)
        return spans

    chant(1, False)

    for start, part, shift in ((9, "recorder", 12), (33, "fiddle", 0)):
        v = band.chords(start, loop2)
        band.arp(v, 58, "B1231323")
        band.bass(v, 72, "R...R...")
        band.lead(start, VERSE_TUNE, 84, part=part, shift=shift)
        band.drums(start, 8, TOMS, vel=.8)

    for start, big in ((17, False), (41, True)):
        ch = band.chords(start, loop2)
        band.strum(ch, 68, "D.DUD.DUD.DUD.DU")
        band.bass(ch, 82, "R.R.R.R.", part="viol", low=40)
        band.roll(ch, 54)
        band.lead(start, CHORUS_TUNE, 92, shift=12)
        band.drums(start, 8, TOMS_BIG, vel=1.0, fill="B..bT.B.D.T.TTTT")
        band.pad(ch, 64)
        if big:
            band.lead(start, CHORUS_TUNE, 80, part="bagpipe")
            band.harmony(start, CHORUS_TUNE, 74, shift=12)

    chant(25, True)
    chant(49, True)
    band.bars(56)
    return band


if __name__ == "__main__":
    build().save("pompeii", doc=__doc__)
