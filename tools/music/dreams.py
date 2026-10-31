#!/usr/bin/env python3
""""Dreams" (Fleetwood Mac, written by Stevie Nicks) as a medieval piece: the
song's two chords rocking under a lute and a hammered dulcimer, the
recorder singing, the hand drum keeping the record's steady walk.

A minor (over F and G, as the record), 120 bpm, 48 bars (a minute and a
half), made to loop:

    bars  1-4   Intro       the dulcimer and the lute on F and G
    bars  5-20  Verse       "Now here you go again, you say you want your freedom ..."
    bars 21-28  Chorus      "Thunder only happens when it's raining ..."
    bars 29-36  Interlude   the dulcimer's turn on the tune, the fiddle under it
    bars 37-44  Chorus      everyone, the monks with the recorder
    bars 45-48  Outro       F and G again, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/dreams.py           # the MIDI and the game's MP3
    python3 tools/music/dreams.py --midi    # just the MIDI
"""

from arrange import Band

ROCK = "Fmaj7 | G6 | Fmaj7 | G6"
VERSE_TUNE = ("rq C5e A4 A4 G4 A4q | re G4e A4 B4 A4q G4q | A4h rh | rw | "
              "rq C5e A4 A4 G4 A4q | re G4e A4 B4 A4q G4q | A4h rh | rw | "
              "re E5e E5 E5 D5 C5 D5 E5 | D5q B4h rq | re E5e E5 E5 D5 C5 D5 E5 | D5h rh | "
              "re C5e C5 C5 A4 C5 D5 C5 | B4q G4h rq | re A4e A4 C5 A4 G4 A4q | A4h rh")
CHORUS = "F | G | F | G | F | G | Am | G"
CHORUS_TUNE = ("re A4e C5 C5 C5 C5 D5 C5 | B4h A4h | re A4e C5 C5 C5 C5 D5 C5 | B4h A4h | "
               "C5h. A4q | B4e A4 G4 A4 B4 A4 G4q | re A4e A4 A4 C5 A4 G4 A4 | A4q. G4e A4h")
INTERLUDE = ("E5q. D5e C5q A4q | B4e A4 G4 A4 B4q D5q | C5q. A4e G4q E4q | G4e A4 B4 D5 C5h | "
             "E5q. D5e C5q A4q | B4e A4 G4 A4 B4q D5q | E5e D5 C5 A4 C5q. B4e | A4h. rq")

WALK = "D...T...D.D.T..."
WALK_RIQ = "D.j.T.j.D.D.T.jj"


def build():
    band = Band("Dreams (medieval)", bpm=120, key="Am",
                credit="Dreams (Stevie Nicks / Fleetwood Mac), a medieval-style arrangement for "
                       "Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (21, "Chorus"), (29, "Interlude"), (37, "Chorus"),
                      (45, "Outro")):
        band.mark(bar, name)

    intro = band.chords(1, ROCK)
    band.arp(intro, 58, "B1.2.3.2", part="lute")
    band.arp(intro, 56, "..3.1.2.", part="dulcimer", step=.5, ring=.9, shift=12)

    verse = band.chords(5, " | ".join([ROCK] * 4))
    band.arp(verse, 58, "B1.2.3.2")
    band.bass(verse, 72, "R..R..R.")
    band.lead(5, VERSE_TUNE, 82, shift=12)
    band.drums(5, 16, WALK, vel=.75)
    band.arp(verse[8:], 52, "..3.1.2.", part="dulcimer", ring=.9, shift=12)

    for start, big in ((21, False), (37, True)):
        ch = band.chords(start, CHORUS)
        band.strum(ch, 62, "D..DU.DUD..DU.DU")
        band.bass(ch, 78, "R..R..R.")
        band.lead(start, CHORUS_TUNE, 90, shift=12)
        band.drums(start, 8, WALK_RIQ, vel=.9, fill="D.j.T.j.D.T.TTTT")
        band.hold(ch, 72)
        if big:
            band.harmony(start, CHORUS_TUNE, 64, part="monks")
            band.pad(ch, 64)

    mid = band.chords(29, ROCK + " | " + ROCK)
    band.arp(mid, 58, "B1.2.3.2")
    band.bass(mid, 74, "R..R..R.")
    band.lead(29, INTERLUDE, 96, part="dulcimer", legato=1)
    band.harmony(29, INTERLUDE, 70, part="fiddle")
    band.drums(29, 8, WALK, vel=.8)

    outro = band.chords(45, ROCK)
    band.arp(outro, 56, "B1.2.3.2")
    band.arp(outro, 52, "..3.1.2.", part="dulcimer", ring=.9, shift=12)
    band.bass(outro, 66, "R.......")
    band.bars(48)
    return band


if __name__ == "__main__":
    build().save("dreams", doc=__doc__)
