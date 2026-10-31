#!/usr/bin/env python3
""""Basket Case" (Green Day, written by Billie Joe Armstrong) as a medieval
punk jig: the lute strummed flat out, the tabor and the riq at a gallop,
the bagpipe and the recorder for the voice.

E flat major, 172 bpm, 64 bars (a minute and a half), made to loop:

    bars  1-8   Intro       the lute alone, as the record starts
    bars  9-24  Verse       "Do you have the time to listen to me whine ..."
    bars 25-40  Chorus      "Sometimes I give myself the creeps ..."
    bars 41-48  Jig         the fiddle on the verse's chords
    bars 49-64  Chorus      everyone, the bagpipe with the recorder

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/basket_case.py           # the MIDI and the game's MP3
    python3 tools/music/basket_case.py --midi    # just the MIDI
"""

from arrange import Band

VERSE = "Eb | Bb | Cm | Gm | Ab | Eb | Bb | Bb"
VERSE_TUNE = ("re Bb4e Bb4 Bb4 Bb4 C5 Bb4 G4 | Bb4h rh | re G4e G4 G4 G4 Bb4 G4 Eb4 | D4h rh | "
              "re Eb4e Eb4 Eb4 Eb4 F4 Eb4 C4 | Eb4q G4h rq | re D4e D4 D4 F4 G4 F4 D4 | F4h. rq")
CHORUS = "Ab | Bb | Eb | Eb | Ab | Bb | Eb | Eb | Ab | Bb | Eb | Bb/D | Cm | Ab | Bb | Bb"
CHORUS_TUNE = ("re C5e C5 C5 Bb4 C5 Eb5 C5 | D5q Bb4h rq | re Bb4e Bb4 G4 G4 Bb4 C5 Bb4 | G4h rh | "
               "re C5e C5 C5 Bb4 C5 Eb5 C5 | D5q Bb4h rq | re Bb4e Bb4 G4 G4 Bb4 C5 Bb4 | G4h rh | "
               "re C5e C5 C5 Bb4 C5 Eb5 F5 | D5q Bb4h rq | re Bb4e Bb4 G4 G4 Bb4 C5 Bb4 | F4h rh | "
               "Eb5q. D5e C5q G4q | Ab4e Bb4 C5 Eb5 C5q Ab4q | Bb4w | rh. Bb4q")
JIG = ("G5e F5 Eb5 Bb4 G5 F5 Eb5 Bb4 | F5e Eb5 D5 Bb4 F5 Eb5 D5 Bb4 | Eb5e D5 C5 G4 Eb5 D5 C5 G4 | "
       "D5e C5 Bb4 G4 D5 C5 Bb4 D5 | C5e Bb4 Ab4 Eb4 C5 Bb4 Ab4 C5 | Bb4e G4 Eb4 G4 Bb4 Eb5 G5 Bb5 | "
       "Ab5e G5 F5 D5 Bb4 D5 F5 Ab5 | G5q F5q D5q Bb4q")

GALLOP = "B.T.D.T."
GALLOP_RIQ = "BjTjDjTj"


def build():
    band = Band("Basket Case (medieval)", bpm=172, key="Eb",
                credit="Basket Case (Billie Joe Armstrong / Green Day), a medieval-style arrangement for "
                       "Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (9, "Verse"), (25, "Chorus"), (41, "Jig"), (49, "Chorus")):
        band.mark(bar, name)
    band.mix("lute", volume=110)

    intro = band.chords(1, VERSE)
    band.strum(intro, 62, "D.DUD.DU")

    verse = band.chords(9, VERSE + " | " + VERSE)
    band.strum(verse, 64, "D.DUD.DU")
    band.bass(verse, 78, "R.R.R.R.", part="viol", low=40)
    band.lead(9, VERSE_TUNE, 84, shift=12)
    band.lead(17, VERSE_TUNE, 86, shift=12)
    band.harmony(17, VERSE_TUNE, 64, shift=12)
    band.drums(9, 16, GALLOP, vel=.85, fill="B.T.TTTT")

    for start, big in ((25, False), (49, True)):
        ch = band.chords(start, CHORUS)
        band.strum(ch, 70, "DUDUDUDU")
        band.bass(ch, 82, "R.R.R.R.", part="viol", low=40)
        band.lead(start, CHORUS_TUNE, 92, shift=12)
        band.drums(start, 16, GALLOP_RIQ, vel=1.0, fill="BjTjTTTT")
        band.pad(ch, 66)
        if big:
            band.lead(start, CHORUS_TUNE, 92, part="bagpipe")
            band.harmony(start, CHORUS_TUNE, 66, part="monks")

    jig = band.chords(41, VERSE)
    band.strum(jig, 62, "D.DUD.DU")
    band.bass(jig, 78, "R.F.R.F.", part="viol", low=40)
    band.lead(41, JIG, 88, part="fiddle", legato=.85)
    band.drums(41, 8, GALLOP_RIQ, vel=.9)
    band.bars(64)
    return band


if __name__ == "__main__":
    build().save("basket_case", doc=__doc__)
