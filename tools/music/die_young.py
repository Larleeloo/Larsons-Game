#!/usr/bin/env python3
""""Die Young" (Kesha, written by Kesha Sebert, Lukasz Gottwald, Benjamin
Levin, Nate Ruess and Henry Walter) as a medieval revel: the record's
stomping beat on the tabor with hands clapped, the lute strummed, the
recorder and the monks shouting the chorus back.

C sharp minor, 128 bpm, 56 bars (a minute and three quarters), made to loop:

    bars  1-4   Intro       hands clapped, the lute
    bars  5-12  Verse       "I hear your heart beat to the beat of the drums ..."
    bars 13-20  Pre-chorus  "Young hunk, take your chance ..." - the drum builds
    bars 21-28  Chorus      "Let's make the most of the night like we're gonna die young"
    bars 29-36  Dance       the fiddle's jig on the chorus chords
    bars 37-44  Chorus      everyone, the monks answering
    bars 45-52  Dance       the jig again, the recorder above
    bars 53-56  Outro       hands clapped, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/die_young.py           # the MIDI and the game's MP3
    python3 tools/music/die_young.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "C#m | A | E | B"
VERSE_TUNE = ("re G#4e G#4 G#4 B4 G#4 E4 F#4 | G#4q E4h rq | re G#4e G#4 G#4 B4 G#4 E4 F#4 | F#4q D#4h rq | "
              "re G#4e G#4 B4 C#5 B4 G#4 E4 | F#4q E4h rq | re E4e F#4 G#4 B4 C#5 B4 G#4 | F#4h. rq")
PRE_TUNE = ("C#5q. B4e G#4h | rh. G#4e B4 | C#5q. B4e G#4h | rh. G#4e B4 | "
            "C#5e C#5 C#5 B4 C#5 E5 C#5 B4 | G#4h. rq | C#5e C#5 E5 C#5 B4 G#4 F#4 E4 | F#4h. rq")
CHORUS_TUNE = ("E5h. rq | C#5e B4 C#5 E5q. rq | E5h. rq | C#5e B4 C#5 E5q. rq | "
               "re C#5e C#5 C#5 B4 C#5 E5 F#5 | E5q C#5h rq | re C#5e C#5 B4 C#5 B4 G#4 B4 | C#5h. rq")
JIG = ("C#5e E5 G#5 E5 C#5 E5 G#5 E5 | C#5e E5 A5 E5 C#5 E5 A5 E5 | B4e E5 G#5 E5 B4 E5 G#5 E5 | "
       "B4e D#5 F#5 D#5 B4 D#5 F#5 A5 | G#5e F#5 E5 C#5 G#5 F#5 E5 C#5 | A5e G#5 F#5 E5 C#5h | "
       "G#5e F#5 E5 B4 G#5 F#5 E5 G#5 | F#5e E5 D#5 F#5 B4h")

STOMP = "B...C...B...C..."
CLAP = "....C.......C..."
REVEL = "B.jtC.jtB.BtC.jt"


def build():
    band = Band("Die Young (medieval)", bpm=128, key="C#m",
                credit="Die Young (Kesha Sebert, Lukasz Gottwald, Benjamin Levin, Nate Ruess, Henry Walter / "
                       "Kesha), a medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Pre-chorus"), (21, "Chorus"), (29, "Dance"),
                      (37, "Chorus"), (45, "Dance"), (53, "Outro")):
        band.mark(bar, name)
    loop2 = LOOP + " | " + LOOP

    def intro(bar):
        spans = band.chords(bar, LOOP)
        band.strum(spans, 56, "D.xUD.xUD.xUD.xU")
        band.drums(bar, 4, CLAP, vel=.8)

    intro(1)

    v = band.chords(5, loop2)
    band.strum(v, 58, "D.xUD.xUD.xUD.xU")
    band.bass(v, 72, "R...R...")
    band.lead(5, VERSE_TUNE, 82, shift=12)
    band.drums(5, 8, STOMP, vel=.75)

    pre = band.chords(13, loop2)
    band.strum(pre, 62, "D.DUD.DUD.DUD.DU")
    band.bass(pre, 76, "R.R.R.R.")
    band.lead(13, PRE_TUNE, 86, shift=12)
    band.drums(13, 4, STOMP, vel=.8)
    band.drums(17, 4, "B.C.B.C.B.C.B.C.", vel=.85, fill="BCBCBCBCTTTTTTTT")

    for start, big in ((21, False), (37, True)):
        ch = band.chords(start, loop2)
        band.strum(ch, 68, "D.DUD.DUD.DUD.DU")
        band.bass(ch, 82, "R.R.R.R.", part="viol", low=40)
        band.roll(ch, 54)
        band.lead(start, CHORUS_TUNE, 94, shift=12)
        band.drums(start, 8, REVEL, vel=1.0)
        band.pad(ch, 66)
        if big:
            band.lead(start, CHORUS_TUNE, 74, part="monks", legato=.98)
            band.harmony(start, CHORUS_TUNE, 76, shift=12)

    for start, high in ((29, False), (45, True)):
        d = band.chords(start, loop2)
        band.strum(d, 60, "D.UDD.UDD.UDD.UD")
        band.bass(d, 78, "R.F.R.F.", part="viol", low=40)
        band.lead(start, JIG, 86, part="fiddle", legato=.85)
        if high:
            band.lead(start, JIG, 66, legato=.8, shift=12)
        band.drums(start, 8, REVEL, vel=.9)

    intro(53)
    band.bars(56)
    return band


if __name__ == "__main__":
    build().save("die_young", doc=__doc__)
