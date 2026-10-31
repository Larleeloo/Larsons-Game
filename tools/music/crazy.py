#!/usr/bin/env python3
""""Crazy" (Gnarls Barkley, written by Brian Burton, Thomas Callaway, Gian
Piero Reverberi and Gian Franco Reverberi) as a medieval piece: the record's
spaghetti-western swagger on a bass viol and a plucked harp, the recorder
singing, and the monks' answering "crazy".

C minor, 112 bpm, 48 bars (a minute and three quarters), made to loop:

    bars  1-4   Intro       the viol and the harp
    bars  5-12  Verse       "I remember when, I remember, I remember when I lost my mind ..."
    bars 13-20  Chorus      "Does that make me crazy? Possibly"
    bars 21-28  Verse       the fiddle sings it
    bars 29-36  Chorus      everyone
    bars 37-44  Break       the shawm over the verse's chords
    bars 45-48  Outro       into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/crazy.py           # the MIDI and the game's MP3
    python3 tools/music/crazy.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "Cm | Eb | Ab | G"
VERSE_TUNE = ("re G4e G4 G4 Bb4 G4 F4 Eb4 | G4q C4h rq | re G4e G4 G4 Bb4 C5 Bb4 G4 | Ab4q Eb4h rq | "
              "re G4e G4 G4 Bb4 G4 F4 Eb4 | G4q C4h rq | re C5e C5 Bb4 Ab4 G4 F4 Eb4 | D4h. rq")
CHORUS_TUNE = ("re C5e C5 Bb4 C5 Eb5q. | rw | re C5e C5 Bb4 C5 Ab4q. | rw | "
               "re C5e C5 Bb4 C5 Eb5 F5 Eb5 | C5h rh | re C5e Bb4 Ab4 G4 F4 Eb4 D4 | B3h. rq")
ANSWER = "rw | rq G4e. Ab4s G4h | rw | rq Eb4e. F4s Eb4h | rw | rw | rw | rw"
BREAK = ("G5q. F5e Eb5q C5q | Bb4e C5 Eb5 G5 Bb5h | Ab5q. G5e F5q Eb5q | D5e Eb5 F5 G5 B4h | "
         "C5e Eb5 G5 C6 Bb5q G5q | Eb5e F5 G5 Bb5 G5h | Ab5e G5 F5 Eb5 C5q Eb5q | D5h. rq")

SWAGGER = "D.....t.D.d.T..."
SWAGGER_RIQ = "D..j..tjD.d.T.jj"


def build():
    band = Band("Crazy (medieval)", bpm=112, key="Cm",
                credit="Crazy (Brian Burton, Thomas Callaway, Gian Piero Reverberi, Gian Franco Reverberi / "
                       "Gnarls Barkley), a medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Verse"), (29, "Chorus"), (37, "Break"),
                      (45, "Outro")):
        band.mark(bar, name)
    band.mix("viol", volume=110)
    loop2 = LOOP + " | " + LOOP

    def base(bar, bars, vel=.85, drums=SWAGGER):
        spans = band.chords(bar, " | ".join([LOOP] * (bars // 4)))
        band.bass(spans, 80, "R..R..F.R..R..O.", part="viol", low=36)
        band.arp(spans, 56, ".1.2.3.2", part="harp", ring=.6, shift=12)
        band.drums(bar, bars, drums, vel=vel)
        return spans

    base(1, 4, .75)
    for start, part, shift in ((5, "recorder", 12), (21, "fiddle", 0)):
        base(start, 8)
        band.lead(start, VERSE_TUNE, 84, part=part, shift=shift)

    for start, big in ((13, False), (29, True)):
        ch = base(start, 8, 1.0, SWAGGER_RIQ)
        band.strum(ch, 60, "D..DU.DUD..DU.DU")
        band.lead(start, CHORUS_TUNE, 92, shift=12)
        band.lead(start, ANSWER, 78, part="monks", legato=.98)
        band.pad(ch, 62)
        if big:
            band.harmony(start, CHORUS_TUNE, 74, shift=12)

    base(37, 8, .95, SWAGGER_RIQ)
    band.lead(37, BREAK, 88, part="shawm", legato=.9)
    base(45, 4, .8)
    band.bars(48)
    return band


if __name__ == "__main__":
    build().save("crazy", doc=__doc__)
