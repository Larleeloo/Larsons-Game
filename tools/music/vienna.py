#!/usr/bin/env python3
""""Vienna" (Billy Joel) as a medieval piece: the record's piano on a
harpsichord, its mandolin on a hammered dulcimer, the recorder singing and
the portative organ taking the accordion's turn.

B flat major, 80 bpm, 44 bars (two and a quarter minutes), made to loop:

    bars  1-4   Intro       the dulcimer over the harpsichord
    bars  5-12  Verse       "Slow down, you crazy child ..."
    bars 13-20  Chorus      "... Vienna waits for you"
    bars 21-24  Interlude   the organ, as the accordion
    bars 25-32  Verse       the fiddle sings it, the recorder above
    bars 33-40  Chorus      everyone
    bars 41-44  Outro       the intro again, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/vienna.py           # the MIDI and the game's MP3
    python3 tools/music/vienna.py --midi    # just the MIDI
"""

from arrange import Band

INTRO = "Bb | Gm | Eb | F"
INTRO_TUNE = "D5e F5 Bb5 F5 D5q C5q | Bb4e D5 G5 D5 Bb4h | G4e Bb4 Eb5 G5 F5q Eb5q | C5e D5 Eb5 F5 C5h"
VERSE = "Bb | Bb/A | Gm | Gm/F | Eb | F | Bb | F"
VERSE_TUNE = ("re F4e Bb4 C5 D5 C5 D5q | C5h rh | re D5e D5 D5 Eb5 D5 C5 Bb4 | C5q Bb4q A4h | "
              "re G4e Bb4 C5 Eb5 D5 C5q | C5h rh | re D5e D5 D5 F5 Eb5 D5 C5 | C5h. rq")
CHORUS = "Eb | F | Dm | Gm | Cm | F | Eb F | Bb"
CHORUS_TUNE = ("G5q. F5e Eb5q Bb4q | C5e D5 Eb5 D5 C5h | F5q. Eb5e D5q A4q | Bb4e C5 D5 C5 Bb4h | "
               "Eb5q. D5e C5q G4q | A4e Bb4 C5 Eb5 D5q C5q | Bb4e C5 D5 F5 Eb5q. C5e | Bb4w")
ORGAN_TUNE = "F5q. Eb5e D5q Bb4q | Bb4e C5 D5 Eb5 D5h | Eb5q. D5e C5q G4q | A4e Bb4 C5 D5 C5h"

LILT = "D..UD.U.D..UD.U."
STEP = "D.....t.d.....t."


def build():
    band = Band("Vienna (medieval)", bpm=80, key="Bb",
                credit="Vienna (Billy Joel), a medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Interlude"), (25, "Verse"),
                      (33, "Chorus"), (41, "Outro")):
        band.mark(bar, name)
    band.mix("organ", volume=100)

    def intro(bar):
        spans = band.chords(bar, INTRO)
        band.strum(spans, 56, LILT, part="harpsichord")
        band.bass(spans, 66, "R...F...")
        band.lead(bar, INTRO_TUNE, 78, part="dulcimer", legato=1)
        return spans

    intro(1)

    v1 = band.chords(5, VERSE)
    band.strum(v1, 54, LILT, part="harpsichord")
    band.bass(v1, 70, "R...F...")
    band.lead(5, VERSE_TUNE, 82, shift=12)
    band.drums(9, 4, STEP, vel=.6)

    for start, big in ((13, False), (33, True)):
        ch = band.chords(start, CHORUS)
        band.strum(ch, 58, LILT, part="harpsichord")
        band.arp(ch, 54, "B1232123")
        band.bass(ch, 74, "R...F...")
        band.lead(start, CHORUS_TUNE, 88)
        band.hold(ch, 70)
        band.drums(start, 8, STEP, vel=.75)
        if big:
            band.harmony(start, CHORUS_TUNE, 72, part="monks", shift=-12)
            band.pad(ch, 60)

    mid = band.chords(21, INTRO)
    band.strum(mid, 54, LILT, part="harpsichord")
    band.bass(mid, 70, "R...F...")
    band.lead(21, ORGAN_TUNE, 84, part="organ", legato=.95)
    band.lead(21, ORGAN_TUNE, 56, part="dulcimer", legato=1, shift=12)

    v2 = band.chords(25, VERSE)
    band.strum(v2, 54, LILT, part="harpsichord")
    band.arp(v2, 52, "B1232123")
    band.bass(v2, 72, "R...F...")
    band.lead(25, VERSE_TUNE, 84, part="fiddle")
    band.harmony(25, VERSE_TUNE, 66, shift=12, below=False)
    band.drums(25, 8, STEP, vel=.65)

    intro(41)
    band.bars(44)
    return band


if __name__ == "__main__":
    build().save("vienna", doc=__doc__)
