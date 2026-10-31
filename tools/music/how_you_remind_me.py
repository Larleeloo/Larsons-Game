#!/usr/bin/env python3
""""How You Remind Me" (Nickelback, written by Chad Kroeger, Mike Kroeger,
Ryan Peake and Ryan Vikedal) as a medieval piece: the quiet verse picked on
the lute, then the chorus crashing in with the tabor, the strummed lute,
the bass viol and the shawm.

E minor, 86 bpm, 40 bars (a little under two minutes), made to loop:

    bars  1-4   Intro       the lute picking, the harp
    bars  5-12  Verse       "Never made it as a wise man ..."
    bars 13-20  Chorus      "This is how you remind me of what I really am ..." - the tabor crashes in
    bars 21-28  Verse       the fiddle sings it, the drum stays
    bars 29-36  Chorus      everyone, the shawm with the tune
    bars 37-40  Outro       the quiet picking, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/how_you_remind_me.py           # the MIDI and the game's MP3
    python3 tools/music/how_you_remind_me.py --midi    # just the MIDI
"""

from arrange import Band

QUIET = "Em | Em | C | D"
VERSE = "Em | Em | C | D | Em | Em | C | D"
VERSE_TUNE = ("re B4e B4 B4 A4 G4 A4 B4 | G4q E4h rq | re E4e G4 A4 B4 A4 G4 E4 | F#4h rh | "
              "re B4e B4 B4 D5 B4 A4 G4 | A4q G4h rq | re E4e G4 A4 B4 D5 B4 A4 | B4h. rq")
CHORUS = "C | G | D | Em | C | G | D | D"
CHORUS_TUNE = ("re G4e G4 G4 A4 B4 A4 G4 | B4q. A4e G4q E4q | re D5e D5 D5 E5 D5 B4 A4 | B4h. rq | "
               "re G4e G4 G4 A4 B4 A4 G4 | B4q. A4e G4q E4q | re D5e D5 E5 D5 B4 A4 G4 | A4h F#4h")

HEAVY = "B...T.t.B.D.T..."
HEAVY_FILL = "B...T.t.B.T.TTTT"


def build():
    band = Band("How You Remind Me (medieval)", bpm=86, key="Em",
                credit="How You Remind Me (Chad Kroeger, Mike Kroeger, Ryan Peake, Ryan Vikedal / Nickelback), "
                       "a medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Verse"), (29, "Chorus"),
                      (37, "Outro")):
        band.mark(bar, name)

    intro = band.chords(1, QUIET)
    band.arp(intro, 60, "B1231323")
    band.bass(intro[2:], 66, "R.......")

    v1 = band.chords(5, VERSE)
    band.arp(v1, 58, "B1231323")
    band.bass(v1, 70, "R.......")
    band.lead(5, VERSE_TUNE, 82, shift=12)

    for start, big in ((13, False), (29, True)):
        ch = band.chords(start, CHORUS)
        band.strum(ch, 70, "D.DUD.DUD.DUD.DU")
        band.bass(ch, 82, "R.R.R.R.", part="viol", low=40)
        band.bass(ch, 74, "R...R...")
        band.lead(start, CHORUS_TUNE, 94, shift=12)
        band.drums(start, 8, HEAVY, vel=1.0, fill=HEAVY_FILL)
        band.pad(ch, 66)
        band.harmony(start, CHORUS_TUNE, 72, part="monks")
        if big:
            band.lead(start, CHORUS_TUNE, 72, part="shawm", legato=.9)
            band.hold(ch, 78)

    v2 = band.chords(21, VERSE)
    band.arp(v2, 58, "B1231323")
    band.bass(v2, 70, "R.......", part="viol", low=40)
    band.lead(21, VERSE_TUNE, 84, part="fiddle", shift=12)
    band.drums(21, 8, "D.......D...T...", vel=.7)

    outro = band.chords(37, QUIET)
    band.arp(outro, 58, "B1231323")
    band.bass(outro, 62, "R.......")
    band.bars(40)
    return band


if __name__ == "__main__":
    build().save("how_you_remind_me", doc=__doc__)
