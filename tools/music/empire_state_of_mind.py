#!/usr/bin/env python3
""""Empire State Of Mind" (Jay-Z and Alicia Keys, written by Shawn Carter,
Alicia Keys, Angela Hunte, Janet Sewell-Ulepic, Al Shuckburgh, Bert Keyes
and Sylvia Robinson) as a medieval piece: the record's piano loop on a
harpsichord, its boom-bap on the hand drum, the verses' rhymes as a
rhythmic figure on the fiddle, and the chorus on the recorder and the monks.

F sharp major, 87 bpm, 40 bars (just under two minutes), made to loop:

    bars  1-4   Intro       the harpsichord's loop
    bars  5-12  Verse       the fiddle's rhythm over the loop and the drum
    bars 13-20  Chorus      "Concrete jungle where dreams are made of ... New York"
    bars 21-28  Verse       the dulcimer and the fiddle
    bars 29-36  Chorus      everyone
    bars 37-40  Outro       the loop, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/empire_state_of_mind.py           # the MIDI and the game's MP3
    python3 tools/music/empire_state_of_mind.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "F# | A#m | D#m | B"
LOOP_TUNE = ("A#4e C#5 F#5 C#5 A#4 C#5 F#5 C#5 | A#4e C#5 F5 C#5 A#4 C#5 F5 C#5 | "
             "A#4e C#5 F#5 C#5 A#4 C#5 F#5 C#5 | B4e D#5 F#5 D#5 B4 D#5 F#5 D#5")
RHYMES = ("rs C#5s C#5 C#5 A#4e C#5s C#5 rs C#5s D#5e C#5 A#4 | rs C#5s C#5 C#5 A#4e F4s F4 rs F4s G#4e F4 re | "
          "rs C#5s C#5 C#5 A#4e C#5s C#5 rs C#5s D#5e C#5 A#4 | rs D#5s D#5 D#5 C#5e B4s B4 A#4e B4 C#5q")
CHORUS_TUNE = ("re C#5e C#5 D#5 C#5q. A#4e | C#5q A#4h rq | re C#5e C#5 D#5 C#5q. A#4e | F#5q D#5h rq | "
               "re F#5e F#5 F5 D#5 C#5 A#4 C#5 | C#5h rh | F#5h. F5q | D#5e C#5 A#4h rq")

BOOM_BAP = "D......dT.....t.D.d...d.T.....tj"


def build():
    band = Band("Empire State Of Mind (medieval)", bpm=87, key="F#",
                credit="Empire State Of Mind (Shawn Carter, Alicia Keys, Angela Hunte, Janet Sewell-Ulepic, "
                       "Al Shuckburgh, Bert Keyes, Sylvia Robinson / Jay-Z, Alicia Keys), a medieval-style "
                       "arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Verse"), (29, "Chorus"), (37, "Outro")):
        band.mark(bar, name)

    def loop(bar, bars, drums=True, vel=74):
        spans = band.chords(bar, " | ".join([LOOP] * (bars // 4)))
        for k in range(0, bars, 4):
            band.lead(bar + k, LOOP_TUNE, vel, part="harpsichord", legato=1)
        band.bass(spans, 76, "R.....R.R.......")
        if drums:
            band.drums(bar, bars, BOOM_BAP[:16], BOOM_BAP[16:], vel=.95)
        return spans

    loop(1, 4, drums=False)
    loop(5, 8)
    band.lead(5, RHYMES, 82, part="fiddle", legato=.7)
    band.lead(9, RHYMES, 84, part="fiddle", legato=.7)

    for start, big in ((13, False), (29, True)):
        ch = loop(start, 8, vel=70)
        band.strum(ch, 58, "D..DU.DUD..DU.DU")
        band.lead(start, CHORUS_TUNE, 94, shift=12)
        band.lead(start, CHORUS_TUNE, 72, part="monks", legato=.98)
        band.pad(ch, 62)
        if big:
            band.harmony(start, CHORUS_TUNE, 76, shift=12)

    loop(21, 8)
    band.lead(21, RHYMES, 80, part="fiddle", legato=.7)
    band.lead(25, RHYMES, 82, part="fiddle", legato=.7)
    band.harmony(25, RHYMES, 70, part="dulcimer", shift=12, below=False)

    loop(37, 4)
    band.bars(40)
    return band


if __name__ == "__main__":
    build().save("empire_state_of_mind", doc=__doc__)
