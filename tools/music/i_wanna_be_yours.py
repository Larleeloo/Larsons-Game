#!/usr/bin/env python3
""""I Wanna Be Yours" (Arctic Monkeys, written by Alex Turner and John Cooper
Clarke) as a medieval piece: the record's slow, heavy-lidded sway on a bass
viol and a harp, a tabor's slow boom and the hands, the recorder singing
low and the fiddle answering.

B flat minor, 67 bpm, 28 bars (just over a minute and a half), made to loop:

    bars  1-4   Intro       the viol and the harp
    bars  5-12  Verse       "I wanna be your vacuum cleaner, breathing in your dust ..."
    bars 13-20  Chorus      "Secrets I have held in my heart ... I wanna be yours"
    bars 21-28  Chorus      everyone, the fiddle answering, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/i_wanna_be_yours.py           # the MIDI and the game's MP3
    python3 tools/music/i_wanna_be_yours.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "Bbm | Gb | Db | Ab"
VERSE_TUNE = ("re F4e F4 F4 Eb4 Db4 Eb4 F4 | Db4q Bb3h rq | re F4e F4 F4 Ab4 F4 Eb4 Db4 | Eb4h rh | "
              "re F4e F4 F4 Eb4 Db4 Eb4 F4 | Db4q Bb3h rq | re Db4e Eb4 F4 Ab4 F4 Eb4 Db4 | C4h. rq")
CHORUS_TUNE = ("rq F5q. Eb5e Db5q | Bb4h. rq | rq F5q. Eb5e Db5q | Ab4h. rq | "
               "rq F5q. Ab5e F5q | Eb5e Db5 Bb4h rq | Db5q. Eb5e F5q Ab5q | F5h. rq")
ANSWER = "rw | rh. F5e Db5 | rw | rh. Eb5e C5 | rw | rh. Db5e Bb4 | rw | rh. Eb5e F5"

SLOW = "B.......C.......B..b....C......."


def build():
    band = Band("I Wanna Be Yours (medieval)", bpm=67, key="Bbm",
                credit="I Wanna Be Yours (Alex Turner, John Cooper Clarke / Arctic Monkeys), a medieval-style "
                       "arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Chorus")):
        band.mark(bar, name)
    band.mix("viol", volume=110)
    loop2 = LOOP + " | " + LOOP

    def base(bar, bars, vel=.85):
        spans = band.chords(bar, " | ".join([LOOP] * (bars // 4)))
        band.bass(spans, 72, "R.....F.R.....O.", part="viol", low=36)
        band.arp(spans, 70, "1.2.3.2.", part="harp", ring=1.4, shift=12)
        band.drums(bar, bars, SLOW[:16], SLOW[16:], vel=vel)
        return spans

    base(1, 4, .75)
    base(5, 8)
    band.lead(5, VERSE_TUNE, 86, shift=12)

    for start, big in ((13, False), (21, True)):
        ch = base(start, 8, .95)
        band.strum(ch, 54, "D.......D..U....")
        band.lead(start, CHORUS_TUNE, 92)
        band.pad(ch, 60)
        if big:
            band.lead(start, ANSWER, 80, part="fiddle", legato=.95)
            band.harmony(start, CHORUS_TUNE, 64, part="monks", shift=-12)

    band.bars(28)
    return band


if __name__ == "__main__":
    build().save("i_wanna_be_yours", doc=__doc__)
