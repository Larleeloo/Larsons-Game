#!/usr/bin/env python3
""""Like a Prayer" (Madonna, written by Madonna and Patrick Leonard) as a
medieval piece: the record's choir and church organ were halfway there
already - the monks and the portative organ open it, slow, then the lute,
the hand drum and the recorder take it dancing, with the monks for the
gospel answers.

D minor and D major, 112 bpm, 52 bars (just under two minutes), made to loop:

    bars  1-8   Chant       "Life is a mystery, everyone must stand alone ..." - the monks and the organ
    bars  9-16  Verse       "I hear you call my name, and it feels like home"
    bars 17-24  Chorus      "When you call my name it's like a little prayer ..."
    bars 25-32  Verse       the fiddle sings it
    bars 33-40  Chorus      everyone, the monks answering
    bars 41-48  Chorus      again, the bells ringing
    bars 49-52  Outro       the organ alone, into the chant

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/like_a_prayer.py           # the MIDI and the game's MP3
    python3 tools/music/like_a_prayer.py --midi    # just the MIDI
"""

from arrange import Band

CHANT = "Dm | Bb | C | Dm | Dm | Bb | C | A"
CHANT_TUNE = ("rq A4h F4q | G4h F4h | rq E4h G4q | A4w | rq A4h F4q | G4h F4h | rq E4h G4q | E4w")
VERSE = "D | G/D | A/D | G/D | D | G/D | A/D | A"
VERSE_TUNE = ("re F#4e F#4 F#4 A4 F#4 E4 D4 | E4q D4h rq | re F#4e F#4 F#4 A4 B4 A4 F#4 | E4h rh | "
              "re F#4e F#4 F#4 A4 F#4 E4 D4 | E4q D4h rq | re D4e E4 F#4 A4 B4 A4 F#4 | E4h. rq")
CHORUS = "D | G | A | D | D | G | A | A"
CHORUS_TUNE = ("re A4e A4 B4 A4q. F#4e | A4q F#4h rq | re E4e E4 F#4 A4q. E4e | F#4q D4h rq | "
               "re A4e A4 B4 D5q. B4e | A4q F#4h rq | re E4e F#4 A4 B4 A4 F#4 E4 | E4h. rq")
ANSWER = "rw | rh. A4e B4 | rw | rh. F#4e E4 | rw | rh. A4e B4 | rw | rh C#4q E4q"

GOSPEL = "D...T.t.D.d.T.tj"


def build():
    band = Band("Like a Prayer (medieval)", bpm=112, key="D",
                credit="Like a Prayer (Madonna, Patrick Leonard / Madonna), a medieval-style arrangement for "
                       "Larson's Game. Not for sale.")
    for bar, name in ((1, "Chant"), (9, "Verse"), (17, "Chorus"), (25, "Verse"), (33, "Chorus"), (41, "Chorus"),
                      (49, "Outro")):
        band.mark(bar, name)
    band.mix("organ", volume=96)

    chant = band.chords(1, CHANT)
    band.lead(1, CHANT_TUNE, 84, part="monks", legato=.98)
    band.harmony(1, CHANT_TUNE, 70, part="monks")
    band.pad(chant, 72, voices=3, octave=0, swell=(70, 110, 90))
    band.bass(chant, 60, "R.......", part="organ", low=38)

    for start, part, shift in ((9, "recorder", 12), (25, "fiddle", 12)):
        v = band.chords(start, VERSE)
        band.strum(v, 60, "D..DU.DUD..DU.DU")
        band.bass(v, 74, "R..R..F.")
        band.lead(start, VERSE_TUNE, 84, part=part, shift=shift)
        band.drums(start, 8, GOSPEL, vel=.8)

    for start, answers, bells in ((17, False, False), (33, True, False), (41, True, True)):
        ch = band.chords(start, CHORUS)
        band.strum(ch, 66, "D..DU.DUD..DU.DU")
        band.bass(ch, 80, "R.R.R.R.", part="viol", low=40)
        band.roll(ch, 54)
        band.lead(start, CHORUS_TUNE, 92, shift=12)
        band.pad(ch, 66)
        band.drums(start, 8, GOSPEL, vel=1.0, fill="D...T.t.D.T.TTTT")
        if answers:
            band.lead(start, ANSWER, 80, part="monks", legato=.98)
            band.harmony(start, CHORUS_TUNE, 74, shift=12)
        if bells:
            for k in range(8):
                band.part("bells").note(band.at(start + k), 3.5, ch[k].chord.bass(62) + 12, 86)

    out = band.chords(49, "Dm | Bb | C | A")
    band.pad(out, 70, voices=3, octave=0, swell=(90, 100, 60))
    band.bass(out, 58, "R.......", part="organ", low=38)
    band.bars(52)
    return band


if __name__ == "__main__":
    build().save("like_a_prayer", doc=__doc__)
