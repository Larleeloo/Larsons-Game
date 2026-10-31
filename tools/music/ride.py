#!/usr/bin/env python3
""""Ride" (Twenty One Pilots, written by Tyler Joseph) as a medieval piece:
the record's easy reggae lilt on the lute (chords chopped on the offbeats),
a harp for its bass, the recorder singing, and the hand drum laid back.

G minor, 75 bpm, 36 bars (just under two minutes), made to loop:

    bars  1-4   Intro       the lute's offbeats and the harp
    bars  5-12  Verse       "I just wanna stay in the sun where I find ..."
    bars 13-20  Chorus      "I'm falling, so I'm taking my time on my ride"
    bars 21-28  Verse       the fiddle sings it, the dulcimer under it
    bars 29-36  Chorus      everyone, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/ride.py           # the MIDI and the game's MP3
    python3 tools/music/ride.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "Gm | Eb | Bb | F"
VERSE_TUNE = ("re D5e D5 D5 C5 Bb4 C5 D5 | Bb4q G4h rq | re D5e D5 D5 F5 D5 C5 Bb4 | C5h rh | "
              "re D5e D5 D5 C5 Bb4 C5 D5 | Bb4q G4h rq | re Bb4e C5 D5 F5 D5 C5 Bb4 | A4h. rq")
CHORUS_TUNE = ("G5q. F5e D5q C5q | D5e C5 Bb4h rq | G5q. F5e D5q F5q | C5h. rq | "
               "G5q. F5e D5q C5q | D5e C5 Bb4h rq | Bb4e C5 D5 F5 D5q C5q | A4h. rq")

SKANK = "....x.U.....x.U."
LAID_BACK = "D.....t.....T..."


def build():
    band = Band("Ride (medieval)", bpm=75, key="Gm",
                credit="Ride (Tyler Joseph / Twenty One Pilots), a medieval-style arrangement for Larson's Game. "
                       "Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Verse"), (29, "Chorus")):
        band.mark(bar, name)
    loop2 = LOOP + " | " + LOOP

    intro = band.chords(1, LOOP)
    band.strum(intro, 78, SKANK)
    band.bass(intro, 74, "R.....F.")

    for start, part, shift in ((5, "recorder", 12), (21, "fiddle", 0)):
        v = band.chords(start, loop2)
        band.strum(v, 76, SKANK)
        band.bass(v, 76, "R.....F.")
        band.lead(start, VERSE_TUNE, 84, part=part, shift=shift)
        band.drums(start, 8, LAID_BACK, vel=.75)
        if part == "fiddle":
            band.arp(v, 64, ".1.2.3.2", part="dulcimer", ring=.8, shift=12)

    for start, big in ((13, False), (29, True)):
        ch = band.chords(start, loop2)
        band.strum(ch, 78, SKANK)
        band.arp(ch, 54, "B1231323")
        band.bass(ch, 80, "R..R..F.")
        band.lead(start, CHORUS_TUNE, 90)
        band.drums(start, 8, "D.....t.D...T.t.", vel=.85)
        band.hold(ch, 68)
        if big:
            band.harmony(start, CHORUS_TUNE, 66, part="monks", shift=-12)
            band.pad(ch, 58)

    band.bars(36)
    return band


if __name__ == "__main__":
    build().save("ride", doc=__doc__)
