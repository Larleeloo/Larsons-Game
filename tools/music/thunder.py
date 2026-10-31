#!/usr/bin/env python3
""""Thunder" (Imagine Dragons, written by Dan Reynolds, Wayne Sermon, Ben
McKee, Alex da Kid and Jayson DeZuzio) as a medieval war song: the record's
boom and clap on a great tabor and hands, the lute strummed hard, the
recorder for the voice and the monks for "thunder".

C major, 84 bpm, 40 bars (a little under two minutes), made to loop:

    bars  1-4   Intro       the tabor and the hands, the lute's chords
    bars  5-12  Verse       "Just a young gun with the quick fuse ..."
    bars 13-20  Chorus      "Thunder, feel the thunder, lightning then the thunder"
    bars 21-28  Verse       the fiddle sings it, the bells ring
    bars 29-36  Chorus      everyone
    bars 37-40  Outro       the boom and the clap, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/thunder.py           # the MIDI and the game's MP3
    python3 tools/music/thunder.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "C | C | Am | F"
VERSE_TUNE = ("re E5e E5 E5 D5 C5 D5 E5 | G5q E5h rq | re C5e C5 C5 D5 E5 D5 C5 | A4h rh | "
              "re E5e E5 E5 D5 C5 D5 E5 | G5q E5h rq | re C5e D5 E5 G5 E5 D5 C5 | C5h. rq")
CHORUS = "C | Am | F | C | C | Am | F | G"
CHORUS_TUNE = ("G5q. E5e rh | G5e G5 A5 G5 E5h | G5q. E5e rh | E5e D5 C5 D5 E5h | "
               "G5q. E5e rh | G5e G5 A5 G5 E5h | C6e B5 A5 G5 E5q D5q | D5h. rq")
ANSWER = "rh G4q. E4e | rw | rh G4q. E4e | rw | rh G4q. E4e | rw | rh A4q. F4e | rh. B4q"

BOOM = "B.......C.......B.......C......."
BOOM_BIG = "B.....b.C.....t.B..b..b.C...C.t."


def build():
    band = Band("Thunder (medieval)", bpm=84, key="C",
                credit="Thunder (Dan Reynolds, Wayne Sermon, Ben McKee, Alex da Kid, Jayson DeZuzio / Imagine "
                       "Dragons), a medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Verse"), (29, "Chorus"),
                      (37, "Outro")):
        band.mark(bar, name)
    band.mix("drum", volume=127)
    loop2 = LOOP + " | " + LOOP

    def intro(bar):
        spans = band.chords(bar, LOOP)
        band.strum(spans, 58, "D.......D..D....")
        band.bass(spans, 70, "R.......")
        band.drums(bar, 4, BOOM[:16], vel=1.1)

    intro(1)

    for start, part, shift in ((5, "recorder", 0), (21, "fiddle", -12)):
        v = band.chords(start, loop2)
        band.strum(v, 60, "D..D..D.D..D.UDU")
        band.bass(v, 74, "R...R...")
        band.lead(start, VERSE_TUNE, 84, part=part, shift=shift)
        band.drums(start, 8, BOOM[:16], vel=1.1)
        if part == "fiddle":
            band.lead(start, VERSE_TUNE, 72, part="bells", legato=1, shift=-12)

    for start, big in ((13, False), (29, True)):
        ch = band.chords(start, CHORUS)
        band.strum(ch, 70, "D.DUD.DUD.DUD.DU")
        band.bass(ch, 82, "R.R.R.R.", part="viol", low=40)
        band.roll(ch, 56)
        band.lead(start, CHORUS_TUNE, 92)
        band.lead(start, ANSWER, 76, part="monks", legato=.98)
        band.drums(start, 8, BOOM_BIG[:16], BOOM_BIG[16:], vel=1.25)
        band.pad(ch, 66)
        if big:
            band.harmony(start, CHORUS_TUNE, 76)

    intro(37)
    band.bars(40)
    return band


if __name__ == "__main__":
    build().save("thunder", doc=__doc__)
