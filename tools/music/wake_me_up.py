#!/usr/bin/env python3
""""Wake Me Up" (Avicii, written by Tim Bergling, Aloe Blacc and Mike
Einziger) as a medieval dance: the lute strumming the song's folk chords,
the recorder singing, and the record's dance drop turned into a reel for the
fiddle and the recorder over the hand drum's four-to-the-floor.

B minor, 124 bpm, 52 bars (a little over a minute and a half), made to loop:

    bars  1-4   Intro       the lute strummed, quietly: Bm, G, D, A
    bars  5-12  Verse       "Feeling my way through the darkness ..."
    bars 13-20  Chorus      "So wake me up when it's all over ..."
    bars 21-28  Dance       the drop as a reel: fiddle and recorder, the drum driving
    bars 29-36  Verse       the fiddle sings it this time
    bars 37-44  Chorus      everyone
    bars 45-52  Dance       the reel again, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/wake_me_up.py           # the MIDI and the game's MP3
    python3 tools/music/wake_me_up.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "Bm | G | D | A"
VERSE_TUNE = ("rq D5e D5 C#5 B4 C#5 D5 | D5q B4h rq | rq D5e D5 E5 F#5 E5 D5 | C#5h. rq | "
              "re D5e D5 D5 C#5 B4 C#5 D5 | D5q B4h rq | rq D5e D5 E5 F#5 E5 D5 | C#5h. rq")
CHORUS_TUNE = ("re D5e D5 D5 E5 F#5 E5 D5 | B4h rh | re D5e D5 D5 E5 F#5 E5 D5 | C#5h rh | "
               "re D5e D5 D5 E5 F#5 A5 F#5 | E5q D5h rq | re D5e D5 D5 E5 F#5 E5 D5 | C#5h. rq")
REEL = ("D5e D5 F#5 D5 E5 D5 B4 A4 | B4e B4 D5 B4 A4q F#4q | D5e D5 F#5 D5 E5 D5 B4 A4 | C#5e C#5 E5 C#5 A4h | "
        "D5e D5 F#5 D5 E5 D5 B4 A4 | B4e B4 D5 B4 A4q F#4q | A4e B4 D5 E5 F#5 E5 D5 E5 | C#5q A4q C#5q E5q")

FOUR = "D.TjD.TjD.TjD.Tj"
SOFT = "D.....t.D...t..."
BACK = "D...T.t.D.d.T..."


def build():
    band = Band("Wake Me Up (medieval)", bpm=124, key="Bm",
                credit="Wake Me Up (Tim Bergling, Aloe Blacc, Mike Einziger / Avicii), a medieval-style "
                       "arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Dance"), (29, "Verse"),
                      (37, "Chorus"), (45, "Dance")):
        band.mark(bar, name)
    loop2 = LOOP + " | " + LOOP

    intro = band.chords(1, LOOP)
    band.strum(intro, 56, "D..D..D.D..D.UDU")

    v1 = band.chords(5, loop2)
    band.strum(v1, 58, "D..D..D.D..D.UDU")
    band.bass(v1, 70, "R.......")
    band.lead(5, VERSE_TUNE, 82, shift=12)
    band.drums(9, 4, SOFT, vel=.7)

    for start, big in ((13, False), (37, True)):
        ch = band.chords(start, loop2)
        band.strum(ch, 66, "D..D..D.D..D.UDU")
        band.bass(ch, 78, "R..R..R.")
        band.lead(start, CHORUS_TUNE, 90, shift=12)
        band.drums(start, 8, BACK, vel=.9, fill="D...T.t.D.T.TTTT")
        band.pad(ch, 64)
        if big:
            band.harmony(start, CHORUS_TUNE, 78, shift=12)
            band.lead(start, CHORUS_TUNE, 60, part="monks", legato=.98)

    for start in (21, 45):
        d = band.chords(start, loop2)
        band.strum(d, 64, "D.UDD.UDD.UDD.UD")
        band.bass(d, 82, "R.R.R.R.")
        band.lead(start, REEL, 88, part="fiddle", legato=.85)
        band.lead(start, REEL, 74, shift=12, legato=.8)
        band.drums(start, 8, FOUR, vel=1.0, fill="D.TjD.TjD.T.TTTT")
        band.drone(start, 8, ["B2", "F#3"], 76)

    v2 = band.chords(29, loop2)
    band.arp(v2, 60, "B1231323")
    band.bass(v2, 72, "R...F...")
    band.lead(29, VERSE_TUNE, 84, part="fiddle")
    band.harmony(29, VERSE_TUNE, 60, part="recorder", shift=12, below=False)
    band.drums(29, 8, SOFT, vel=.75)
    band.bars(52)
    return band


if __name__ == "__main__":
    build().save("wake_me_up", doc=__doc__)
