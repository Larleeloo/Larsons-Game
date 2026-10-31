#!/usr/bin/env python3
""""California Dreamin'" (The Mamas & the Papas, written by John Phillips and
Michelle Phillips) as a medieval piece: a lute picking the verse's chords,
the recorder singing the tune and the monks answering each line, as the
record's voices answer one another, and a wooden flute for the flute solo.

A minor, 112 bpm, 56 bars (two minutes), made to loop:

    bars  1-4   Intro       the lute alone, then the harp
    bars  5-20  Verse       "All the leaves are brown ..." - the recorder, the monks echoing
    bars 21-36  Solo        the wooden flute over the verse, the lute strummed
    bars 37-52  Verse       everyone: the fiddle and the monks echoing, the drone
    bars 53-56  Outro       the intro again, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/california_dreamin.py           # the MIDI and the game's MP3
    python3 tools/music/california_dreamin.py --midi    # just the MIDI
"""

from arrange import Band

INTRO = "Am G | F G | Esus4 | E"
VERSE = ("Am G | F G | Esus4 | E | F C | E Am | F | Esus4 E | "
         "Am G | F G | Esus4 | E | Am G | F G | Esus4 | E")

# The tune and its echo, a bar each in turn: the voice sings a line, the others answer it.
TUNE = ("rq A4e A4 G4 A4 C5q | rw | rq A4e A4 B4 A4 A4q | rw | "
        "re C5e C5 D5 C5 E5q. | rw | re A4e A4 C5 B4 A4q. | rw | "
        "rq A4e A4 G4 A4 C5q | rw | re A4e A4 A4 B4 A4 A4q | rw | "
        "re E4e A4 C5 C5 B4 A4q | rw | re C5e B4 A4 B4 A4 A4q | G#4h. rq")
ECHO = ("rw | rq A4e A4 G4 A4 C5q | rw | rq A4e A4 B4 A4 G#4q | "
        "rw | re B4e B4 C5 B4 A4q. | rw | rh A4e B4 G#4q | "
        "rw | rq A4e A4 G4 A4 C5q | rw | re A4e A4 A4 B4 A4 G#4q | "
        "rw | re E4e A4 C5 C5 B4 A4q | rw | rw")

SOLO = ("E5e A5 G5 E5 D5 C5 D5 E5 | F5q. E5e D5q B4q | A4e B4 C5 D5 E5q D5q | B4h G#4q E4q | "
        "A4e C5 F5 A5 G5q E5q | G#5e F5 E5 D5 C5q A4q | C5e D5 F5 A5 G5 F5 E5 D5 | E5h. B4q | "
        "A5q. G5e E5q D5q | C5e D5 E5 F5 G5q D5q | E5e D5 B4 A4 B4q. A4e | G#4h B4q E5q | "
        "A5e G5 E5 A5 G5 E5 D5 B4 | C5q A4q B4q D5q | C5e B4 A4 B4 A4q. E4e | G#4h+q rq")

GROOVE = "D...t.t.D.t.t..."
GROOVE_FULL = "D.j.T.tjD.jdT.tj"


def build():
    band = Band("California Dreamin' (medieval)", bpm=112, key="Am",
                credit="California Dreamin' (John Phillips, Michelle Phillips / The Mamas & the Papas), "
                       "a medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (21, "Solo"), (37, "Verse"), (53, "Outro")):
        band.mark(bar, name)
    band.mix("flute", volume=84)

    # --- Intro: the lute alone, the harp's low notes joining
    intro = band.chords(1, INTRO)
    band.arp(intro, 64)
    band.bass(intro[2:], 72, "R.......")

    # --- Verse: the recorder sings, the monks answer
    verse = band.chords(5, VERSE)
    band.arp(verse, 60)
    band.bass(verse, 74, "R...F...")
    band.lead(5, TUNE, 82, shift=12)
    band.lead(5, ECHO, 72, part="monks", legato=.98)
    band.drums(13, 8, GROOVE, vel=.75)

    # --- Solo: the wooden flute, the lute strummed, the fiddle holding the chords
    solo = band.chords(21, VERSE)
    band.strum(solo, 62, "D..DU.DU")
    band.bass(solo, 76, "R..R.F..")
    band.lead(21, SOLO, 86, part="flute", legato=.9)
    band.hold(solo, 70)
    band.drums(21, 16, GROOVE, GROOVE_FULL, vel=.85)

    # --- Verse again: everyone; the fiddle and the monks answer
    last = band.chords(37, VERSE)
    band.arp(last, 62)
    band.strum(last, 56, "D...D.U.")
    band.bass(last, 78, "R..R.F..")
    band.roll(last, 54, at=2)
    band.lead(37, TUNE, 88, shift=12)
    band.lead(37, ECHO, 84, part="fiddle", legato=.95)
    band.harmony(37, ECHO, 64, part="monks")
    band.pad(last, 66)
    band.drums(37, 16, GROOVE_FULL, vel=.9)

    # --- Outro: the intro again, quieter, back to the start
    outro = band.chords(53, INTRO)
    band.arp(outro, 60)
    band.bass(outro, 66, "R.......")
    band.part("recorder").note(band.at(55), 7.6, 69 + 12, 60)
    band.bars(56)
    return band


if __name__ == "__main__":
    build().save("california_dreamin", doc=__doc__)
