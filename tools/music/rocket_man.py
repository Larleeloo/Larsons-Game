#!/usr/bin/env python3
""""Rocket Man" (Elton John, written by Elton John and Bernie Taupin) as a
medieval piece: the record's piano on a harpsichord, its slide guitar on a
fiddle, the recorder singing, and the chorus opening out with the monks and
the bells.

G minor and B flat major, 68 bpm, 32 bars (just under two minutes), made to loop:

    bars  1-4   Intro       the harpsichord
    bars  5-12  Verse       "She packed my bags last night pre-flight ..."
    bars 13-20  Chorus      "And I think it's gonna be a long, long time ..."
    bars 21-24  Interlude   the fiddle's slide
    bars 25-32  Chorus      everyone, "I'm a rocket man", into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/rocket_man.py           # the MIDI and the game's MP3
    python3 tools/music/rocket_man.py --midi    # just the MIDI
"""

from arrange import Band

INTRO = "Gm7 | C9 | Gm7 | C9"
VERSE = "Gm7 | C9 | Gm7 | C9 | Ebmaj7 | Bb | Eb | F"
VERSE_TUNE = ("re D5e D5 D5 C5 Bb4 C5 D5 | E5q C5h rq | re D5e D5 D5 F5 D5 C5 Bb4 | C5h rh | "
              "re G4e Bb4 D5 Eb5 D5 C5 Bb4 | D5q Bb4h rq | re G4e Bb4 C5 Eb5 D5 C5 Bb4 | C5h. rq")
CHORUS = "Bb | Eb | Bb | Eb | Cm | F | Eb | F"
CHORUS_TUNE = ("re F5e F5 F5 D5 F5 G5q | F5h rh | re D5e F5 F5 D5 Bb4 C5q | Bb4h rh | "
               "re Eb5e Eb5 Eb5 D5 C5 Bb4 C5 | C5h. rq | re G4e Bb4 C5 Eb5 D5 C5 Bb4 | C5h. D5q")
SLIDE = "D5h. C5e D5 | E5q C5h. | D5h. F5e D5 | C5w"

EASY = "D.......d...t..."


def build():
    band = Band("Rocket Man (medieval)", bpm=68, key="Gm",
                credit="Rocket Man (Elton John, Bernie Taupin / Elton John), a medieval-style arrangement for "
                       "Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Interlude"), (25, "Chorus")):
        band.mark(bar, name)

    intro = band.chords(1, INTRO)
    band.arp(intro, 60, "B1231323", part="harpsichord")

    v = band.chords(5, VERSE)
    band.arp(v, 58, "B1231323", part="harpsichord")
    band.bass(v, 70, "R...F...")
    band.lead(5, VERSE_TUNE, 84)
    band.drums(9, 4, EASY, vel=.6)

    for start, big in ((13, False), (25, True)):
        ch = band.chords(start, CHORUS)
        band.strum(ch, 58, "D..DU.DUD..DU.DU", part="harpsichord")
        band.arp(ch, 54, "B1231323")
        band.bass(ch, 76, "R...F...")
        band.lead(start, CHORUS_TUNE, 92)
        band.drums(start, 8, EASY, vel=.85)
        band.hold(ch, 70)
        if big:
            band.harmony(start, CHORUS_TUNE, 70, part="monks", shift=-12)
            band.pad(ch, 60)
            for k in (0, 2, 4, 6):
                band.part("bells").note(band.at(start + k), 7, ch[k].chord.bass(62) + 12, 84)

    mid = band.chords(21, INTRO)
    band.arp(mid, 58, "B1231323", part="harpsichord")
    band.bass(mid, 72, "R...F...")
    band.lead(21, SLIDE, 88, part="fiddle", legato=1)
    band.drums(21, 4, EASY, vel=.7)
    band.bars(32)
    return band


if __name__ == "__main__":
    build().save("rocket_man", doc=__doc__)
