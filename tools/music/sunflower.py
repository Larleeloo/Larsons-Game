#!/usr/bin/env python3
""""Sunflower" (Post Malone and Swae Lee, written by Austin Post, Khalif
Brown, Carter Lang, Louis Bell, Billy Walsh and Carl Rosen) as a medieval
piece: the record's lazy, sunny chords on a psaltery and a lute, the
recorder singing, a soft hand drum.

D major, 90 bpm, 40 bars (a minute and three quarters), made to loop:

    bars  1-4   Intro       the psaltery picking, the harp
    bars  5-12  Verse       "Needless to say, I keep her in check ..."
    bars 13-20  Chorus      "Then you're left in the dust ... you're a sunflower"
    bars 21-28  Verse       the fiddle sings it
    bars 29-36  Chorus      everyone
    bars 37-40  Outro       the psaltery again, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/sunflower.py           # the MIDI and the game's MP3
    python3 tools/music/sunflower.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "Dmaj7 | Em7 | F#m7 | Bm7"
VERSE_TUNE = ("re F#5e F#5 E5 F#5 A5 F#5q | E5e D5 B4h rq | re F#5e F#5 E5 F#5 A5 B5q | A5e F#5 E5h rq | "
              "re F#5e F#5 E5 F#5 A5 F#5q | E5e D5 B4h rq | re D5e E5 F#5 A5 F#5 E5 D5 | B4h. rq")
CHORUS_TUNE = ("A5q. F#5e E5q D5q | E5e F#5 E5h rq | A5q. F#5e E5q D5q | C#5e D5 B4h rq | "
               "re B4e D5 E5 F#5q A5q | B5e A5 F#5h rq | re B4e D5 E5 F#5 E5 D5 C#5 | D5h. rq")

LAZY = "D.....t.d..t..t."


def build():
    band = Band("Sunflower (medieval)", bpm=90, key="D",
                credit="Sunflower (Austin Post, Khalif Brown, Carter Lang, Louis Bell, Billy Walsh, Carl Rosen / "
                       "Post Malone, Swae Lee), a medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Verse"), (29, "Chorus"),
                      (37, "Outro")):
        band.mark(bar, name)
    loop2 = LOOP + " | " + LOOP

    def intro(bar):
        spans = band.chords(bar, LOOP)
        band.arp(spans, 60, "1231.321", part="psaltery", step=.5, ring=1.2, shift=12)
        band.bass(spans, 66, "R.......")

    intro(1)

    for start, part, shift in ((5, "recorder", 0), (21, "fiddle", -12)):
        v = band.chords(start, loop2)
        band.arp(v, 56, "B1231323")
        band.arp(v, 50, "1231.321", part="psaltery", ring=1.2, shift=12)
        band.bass(v, 70, "R.....F.")
        band.lead(start, VERSE_TUNE, 84, part=part, shift=shift)
        band.drums(start, 8, LAZY, vel=.7)
        if part == "fiddle":
            band.harmony(start, VERSE_TUNE, 60, part="recorder")

    for start, big in ((13, False), (29, True)):
        ch = band.chords(start, loop2)
        band.strum(ch, 58, "D..D..D.D..D.UDU")
        band.arp(ch, 52, "1231.321", part="psaltery", ring=1.2, shift=12)
        band.bass(ch, 74, "R..R..F.")
        band.lead(start, CHORUS_TUNE, 88)
        band.drums(start, 8, LAZY, vel=.85)
        band.hold(ch, 66)
        if big:
            band.harmony(start, CHORUS_TUNE, 62, part="monks", shift=-12)
            band.pad(ch, 58)

    intro(37)
    band.bars(40)
    return band


if __name__ == "__main__":
    build().save("sunflower", doc=__doc__)
