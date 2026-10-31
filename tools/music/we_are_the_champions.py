#!/usr/bin/env python3
""""We Are The Champions" (Queen, written by Freddie Mercury) as a medieval
anthem: the record's swaying triplet time on a harpsichord and a harp, the
recorder singing the verse, and the chorus sung by the monks over the
organ, the bells and the great tabor.

C minor and F major, 64 bpm in a triplet swing (the record's 6/8), 28 bars
(just under two minutes), made to loop:

    bars  1-2   Intro       the harpsichord alone
    bars  3-10  Verse       "I've paid my dues, time after time ..."
    bars 11-18  Chorus      "We are the champions, my friends ..." - the monks, the organ, the bells
    bars 19-26  Chorus      everyone, the recorder above
    bars 27-28  Outro       the harpsichord, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/we_are_the_champions.py           # the MIDI and the game's MP3
    python3 tools/music/we_are_the_champions.py --midi    # just the MIDI
"""

from arrange import Band

INTRO = "Cm Gm | Cm Gm"
VERSE = "Cm Gm | Cm Gm | Cm Gm | Ab Bb | Eb Bb/D | Cm Gm/Bb | F | G7"
VERSE_TUNE = ("rT C5t Eb5T D5t C5T Bb4t G4q | rT C5t Eb5T D5t C5h | rT C5t Eb5T D5t C5T Bb4t G4q | "
              "Ab4T Bb4t C5T D5t Eb5h | rT Eb5t F5T G5t F5T Eb5t D5q | Eb5T C5t Bb4T G4t Bb4h | "
              "A4T C5t F5T C5t A4h | B4T D5t G5T F5t D5h")
CHORUS = "F Am | Dm G | C Gm | C | F Am | Dm Bb | Gm C | F C7"
CHORUS_TUNE = ("C5q A4T G4t F4T E4t F4q | rT A4t D5T C5t B4h | C5T E5t G5T F5t E5T D5t C5q | rw | "
               "C5q A4T G4t F4T E4t F4q | rT A4t D5T C5t Bb4h | Bb4T C5t D5T E5t F5T E5t D5T C5t | C5h. rq")

SWAY = "D.....t.....d.....t....."[:12]
BOOM = "B.....t.....b.....t....."[:12]


def build():
    band = Band("We Are The Champions (medieval)", bpm=64, key="F",
                credit="We Are The Champions (Freddie Mercury / Queen), a medieval-style arrangement for "
                       "Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (3, "Verse"), (11, "Chorus"), (19, "Chorus"), (27, "Outro")):
        band.mark(bar, name)
    triplets = "B12321B12321"

    intro = band.chords(1, INTRO)
    band.arp(intro, 60, triplets, part="harpsichord", step=1 / 3, ring=.9)

    v = band.chords(3, VERSE)
    band.arp(v, 58, triplets, part="harpsichord", step=1 / 3, ring=.9)
    band.bass(v, 70, "R.....F.....")
    band.lead(3, VERSE_TUNE, 84, legato=.95)
    band.drums(7, 4, SWAY, vel=.65)

    for start, big in ((11, False), (19, True)):
        ch = band.chords(start, CHORUS)
        band.arp(ch, 58, triplets, part="harpsichord", step=1 / 3, ring=.9)
        band.bass(ch, 80, "R.....R.....", part="viol", low=36)
        band.roll(ch, 56)
        band.lead(start, CHORUS_TUNE, 92, part="monks", legato=.98, shift=-12)
        band.harmony(start, CHORUS_TUNE, 76, part="monks", shift=-12)
        band.pad(ch, 74, voices=3, octave=0, swell=(80, 115, 100))
        band.drums(start, 8, BOOM, vel=1.0)
        for k in (0, 4):
            band.part("bells").note(band.at(start + k), 7, band.chord_at(band.at(start + k)).chord.bass(62) + 12, 82)
        if big:
            band.lead(start, CHORUS_TUNE, 84, legato=.95, shift=12)
            band.lead(start, CHORUS_TUNE, 76, part="fiddle", legato=.95)

    outro = band.chords(27, INTRO)
    band.arp(outro, 58, triplets, part="harpsichord", step=1 / 3, ring=.9)
    band.bass(outro, 64, "R.....F.....")
    band.bars(28)
    return band


if __name__ == "__main__":
    build().save("we_are_the_champions", doc=__doc__)
