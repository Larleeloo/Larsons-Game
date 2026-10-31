#!/usr/bin/env python3
""""Something Just Like This" (The Chainsmokers and Coldplay, written by
Andrew Taggart, Chris Martin, Guy Berryman, Jonny Buckland and Will
Champion) as a medieval piece: the verse over a harpsichord, and the
record's plucked synth drop on a psaltery and a dulcimer, over the hand
drum's dance.

B major, 103 bpm, 48 bars (just under two minutes), made to loop:

    bars  1-4   Intro       the harpsichord, quietly
    bars  5-12  Verse       "I've been reading books of old ..."
    bars 13-20  Chorus      "I want something just like this ..."
    bars 21-28  Drop        the psaltery and the dulcimer's tune, the drum dancing
    bars 29-36  Chorus      everyone, the monks with the recorder
    bars 37-44  Drop        again, the recorder above
    bars 45-48  Outro       the harpsichord, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/something_just_like_this.py           # the MIDI and the game's MP3
    python3 tools/music/something_just_like_this.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "G#m | E | B | F#"
VERSE_TUNE = ("re D#5e D#5 D#5 C#5 B4 C#5 D#5 | B4q G#4h rq | re D#5e D#5 D#5 E5 D#5 C#5 B4 | C#5h rh | "
              "re D#5e D#5 D#5 C#5 B4 C#5 D#5 | B4q G#4h rq | re B4e C#5 D#5 F#5 D#5 C#5 B4 | A#4h. rq")
CHORUS_TUNE = ("re D#5e D#5 C#5 B4 C#5 D#5q | B4h rh | re D#5e D#5 C#5 B4 C#5 D#5q | E5h rh | "
               "re D#5e D#5 C#5 B4 C#5 D#5q | B4h rh | re F#5e F#5 E5 D#5 C#5 B4 C#5 | A#4h. rq")
DROP = ("G#5e B5 G#5 F#5 D#5q B4q | G#5e B5 G#5 F#5 E5h | D#5e F#5 D#5 C#5 B4q D#5q | C#5e D#5 C#5 B4 A#4h | "
        "G#5e B5 G#5 F#5 D#5q B4q | G#5e B5 G#5 F#5 E5h | D#5e F#5 B5 F#5 D#5q C#5q | B4e C#5 D#5 F#5 C#5h")

DANCE = "D.TjD.TjD.TjD.Tj"
SOFT = "D.......d...t..."


def build():
    band = Band("Something Just Like This (medieval)", bpm=103, key="B",
                credit="Something Just Like This (Andrew Taggart, Chris Martin, Guy Berryman, Jonny Buckland, "
                       "Will Champion / The Chainsmokers, Coldplay), a medieval-style arrangement for Larson's "
                       "Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Drop"), (29, "Chorus"), (37, "Drop"),
                      (45, "Outro")):
        band.mark(bar, name)
    loop2 = LOOP + " | " + LOOP

    def quiet(bar):
        spans = band.chords(bar, LOOP)
        band.strum(spans, 54, "D.......D.U.D...", part="harpsichord")
        band.bass(spans, 64, "R.......")

    quiet(1)

    v = band.chords(5, loop2)
    band.strum(v, 54, "D.......D.U.D...", part="harpsichord")
    band.bass(v, 70, "R...R...")
    band.lead(5, VERSE_TUNE, 82, shift=12)
    band.drums(9, 4, SOFT, vel=.7)

    for start, big in ((13, False), (29, True)):
        ch = band.chords(start, loop2)
        band.strum(ch, 62, "D..DU.DUD..DU.DU")
        band.bass(ch, 78, "R..R..R.")
        band.lead(start, CHORUS_TUNE, 90, shift=12)
        band.drums(start, 8, SOFT, vel=.9, fill="D...T...TTTTTTTT")
        band.hold(ch, 70)
        if big:
            band.lead(start, CHORUS_TUNE, 66, part="monks", legato=.98)
            band.pad(ch, 60)

    for start, high in ((21, False), (37, True)):
        d = band.chords(start, loop2)
        band.lead(start, DROP, 96, part="psaltery", legato=1)
        band.lead(start, DROP, 70, part="dulcimer", legato=1, shift=-12)
        band.bass(d, 82, "R.R.R.R.", part="viol", low=40)
        band.strum(d, 56, "..D...D...D...D.")
        band.drums(start, 8, DANCE, vel=1.0, fill="D.TjD.TjD.T.TTTT")
        if high:
            band.lead(start, DROP, 66, legato=.85, shift=12)

    quiet(45)
    band.bars(48)
    return band


if __name__ == "__main__":
    build().save("something_just_like_this", doc=__doc__)
