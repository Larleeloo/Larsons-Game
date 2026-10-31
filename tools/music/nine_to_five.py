#!/usr/bin/env python3
""""9 to 5" (Dolly Parton) as a medieval piece: a busy country-dance lute,
the fiddle singing, the record's typewriter clacking on wood blocks, and
the bagpipe and the recorder taking the chorus.

A major, 108 bpm, 56 bars (two minutes), made to loop:

    bars  1-4   Intro       the wood blocks typing, the lute
    bars  5-12  Verse       "Tumble outta bed and I stumble to the kitchen ..."
    bars 13-20  Chorus      "Workin' 9 to 5, what a way to make a livin' ..."
    bars 21-28  Chorus      "They just use your mind ..."
    bars 29-36  Verse       the recorder sings it
    bars 37-52  Chorus      everyone, the bagpipe too
    bars 53-56  Outro       the typing again, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/nine_to_five.py           # the MIDI and the game's MP3
    python3 tools/music/nine_to_five.py --midi    # just the MIDI
"""

from arrange import Band

INTRO = "A | A | D | E"
VERSE = "A | A | D | A | A | A | E | E"
VERSE_TUNE = ("re E4e E4 A4 A4 A4 B4 C#5 | B4q A4e B4 A4q rq | re F#4e A4 A4 A4 B4 A4 F#4 | E4h rh | "
              "re E4e E4 A4 A4 A4 B4 C#5 | E5q C#5e B4 A4q rq | re B4e B4 B4 C#5 B4 A4 G#4 | B4h. rq")
CHORUS_A = "D | A | D | A | D | A | Bm | E"
CHORUS_A_TUNE = ("F#5e F#5 E5 F#5 A5q. rs F#5s | E5e C#5 B4 A4 B4 C#5 A4q | F#5e F#5 E5 F#5 A5q. rs F#5s | "
                 "E5e C#5 B4 A4 C#5q rq | F#5e F#5 E5 F#5 A5q F#5q | E5e C#5 A4 C#5 E5q C#5q | "
                 "D5e D5 C#5 B4 C#5 D5 C#5 B4 | B4h. rq")
CHORUS_B = "D | A | D | A | D | E | A | E"
CHORUS_B_TUNE = ("re F#5e F#5 F#5 A5 F#5 E5 C#5 | E5h rh | re F#5e F#5 F#5 A5 F#5 E5 C#5 | E5h rh | "
                 "re D5e D5 D5 F#5 E5 D5 C#5 | B4q. C#5e B4q E4q | A4e B4 C#5 E5 C#5q A4q | B4h. rq")

TYPE = "W.w.WWw.W.w.WwWw"
TWO_STEP = "D...T...D...T..."
DANCE = "D.j.T.j.D.D.T.jj"


def build():
    band = Band("9 to 5 (medieval)", bpm=108, key="A",
                credit="9 to 5 (Dolly Parton), a medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Chorus"), (29, "Verse"),
                      (37, "Chorus"), (45, "Chorus"), (53, "Outro")):
        band.mark(bar, name)

    def intro(bar):
        spans = band.chords(bar, INTRO)
        band.drums(bar, 4, TYPE, vel=.7, fill="W.w.WWw.W.w.I...")
        band.strum(spans, 56, "D.xUD.xUD.xUD.xU")
        band.bass(spans, 70, "R...F...")

    intro(1)

    def verse(bar, part, shift, vel):
        spans = band.chords(bar, VERSE)
        band.strum(spans, 58, "D.xUD.xUD.xUD.xU")
        band.bass(spans, 74, "R...F...")
        band.lead(bar, VERSE_TUNE, vel, part=part, shift=shift)
        band.drums(bar, 8, TWO_STEP, vel=.8)
        band.drums(bar, 8, "........W.w.....", vel=.6)

    verse(5, "fiddle", 0, 86)

    def chorus(bar, big):
        a = band.chords(bar, CHORUS_A)
        b = band.chords(bar + 8, CHORUS_B)
        for spans in (a, b):
            band.strum(spans, 64, "D.DUD.DUD.DUD.DU")
            band.bass(spans, 78, "R.F.R.F.")
            band.roll(spans, 52)
        band.lead(bar, CHORUS_A_TUNE, 90)
        band.lead(bar + 8, CHORUS_B_TUNE, 90)
        band.harmony(bar, CHORUS_A_TUNE, 74)
        band.harmony(bar + 8, CHORUS_B_TUNE, 74)
        band.drums(bar, 16, DANCE, vel=.95, fill="D.j.T.j.D.T.TTTT")
        if big:
            band.lead(bar, CHORUS_A_TUNE, 76, part="bagpipe")
            band.lead(bar + 8, CHORUS_B_TUNE, 76, part="bagpipe")
            band.drone(bar, 16, ["A2", "E3"], 70)

    chorus(13, False)
    verse(29, "recorder", 12, 82)
    chorus(37, True)
    intro(53)
    band.bars(56)
    return band


if __name__ == "__main__":
    build().save("nine_to_five", doc=__doc__)
