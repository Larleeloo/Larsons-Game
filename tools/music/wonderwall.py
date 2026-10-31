#!/usr/bin/env python3
""""Wonderwall" (Oasis, written by Noel Gallagher) as a medieval piece: the
lute strummed in the song's restless sixteenths, the recorder for the
voice, a bass viol for the record's cello, and the hand drum.

F sharp minor, 87 bpm, 44 bars (just over two minutes), made to loop:

    bars  1-4   Intro       the lute alone: F#m7, A, Esus4, B7sus4
    bars  5-12  Verse       "Today is gonna be the day ..."
    bars 13-24  Pre-chorus  "And all the roads we have to walk are winding ..."
    bars 25-32  Chorus      "Because maybe ..." - the viol and the drum come in
    bars 33-40  Chorus      again, everyone, the fiddle a third under the tune
    bars 41-44  Outro       the intro's chords, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/wonderwall.py           # the MIDI and the game's MP3
    python3 tools/music/wonderwall.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "F#m7 | A | Esus4 | B7sus4"
VERSE_TUNE = ("re E4e A4 A4 A4 A4 B4 C#5 | C#5q B4e A4 A4q rq | re E4e A4 A4 A4 A4 B4 C#5 | B4q A4e B4 A4q rq | "
              "re E4e A4 A4 A4 A4 B4 C#5 | E5q C#5e B4 A4q rq | re A4e A4 B4 A4 F#4 E4 F#4 | F#4h. rq")
PRE = "D | E | F#m | F#m | D | E | F#m | F#m | D E | A E | F#m A | B7sus4"
PRE_TUNE = ("re F#5e F#5 F#5 E5 D5 E5 F#5 | E5q D5e C#5 B4q rq | C#5h. rq | rw | "
            "re F#5e F#5 F#5 E5 D5 E5 F#5 | E5q D5e C#5 B4q rq | C#5h. rq | rw | "
            "re A4e B4 C#5 D5 C#5 B4 A4 | C#5q B4e A4 B4q A4q | A4q. F#4e A4q B4q | B4w")
CHORUS = "Dadd9 F#m7 | A F#m7 | Dadd9 F#m7 | A F#m7 | Dadd9 F#m7 | A F#m7 | Dadd9 F#m7 | A B7sus4"
CHORUS_TUNE = ("A4e C#5q. B4e A4q. | re A4e A4 A4 A4 B4 A4 C#5 | B4q A4h. | rw | "
               "A4e C#5q. B4e A4q. | re A4e C#5 B4 A4 B4q. | A4w | rh. E4q")

STRUM = "D.DU.UDUD.DU.UDU"
GROOVE = "D.....t.D.d.T..."
GROOVE_FULL = "D.jtT.tjD.dtT.tj"


def build():
    band = Band("Wonderwall (medieval)", bpm=87, key="F#m",
                credit="Wonderwall (Noel Gallagher / Oasis), a medieval-style arrangement for "
                       "Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Pre-chorus"), (25, "Chorus"), (33, "Chorus"),
                      (41, "Outro")):
        band.mark(bar, name)

    intro = band.chords(1, LOOP)
    band.strum(intro, 60, STRUM)

    verse = band.chords(5, LOOP + " | " + LOOP)
    band.strum(verse, 60, STRUM)
    band.bass(verse, 70, "R.......")
    band.lead(5, VERSE_TUNE, 80, shift=12)

    pre = band.chords(13, PRE)
    band.strum(pre, 64, STRUM)
    band.bass(pre, 74, "R...R...")
    band.lead(13, PRE_TUNE, 84)
    band.hold(pre, 72, part="viol", low=45, high=57, swell=(80, 110, 92))
    band.drums(21, 4, GROOVE, vel=.7)

    for start, big in ((25, False), (33, True)):
        ch = band.chords(start, CHORUS)
        band.strum(ch, 66 if big else 64, STRUM)
        band.bass(ch, 78, "R..R..R.")
        band.lead(start, CHORUS_TUNE, 90 if big else 86, shift=12)
        band.hold(ch, 78, part="viol", low=45, high=57, swell=(86, 116, 96))
        band.drums(start, 8, GROOVE_FULL if big else GROOVE, vel=.9 if big else .8,
                   fill="D.jtT.tjD.T.TTTT" if big else None)
        if big:
            band.harmony(start, CHORUS_TUNE, 76, shift=12)
            band.roll(ch, 54, at=0)
            band.pad(ch, 62)

    outro = band.chords(41, LOOP)
    band.strum(outro, 58, STRUM)
    band.bass(outro, 66, "R.......")
    band.hold(outro, 64, part="viol", low=45, high=57, swell=(80, 100, 70))
    band.bars(44)
    return band


if __name__ == "__main__":
    build().save("wonderwall", doc=__doc__)
