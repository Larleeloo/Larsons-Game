#!/usr/bin/env python3
""""(Don't Fear) The Reaper" (Blue Öyster Cult, written by Donald "Buck
Dharma" Roeser) as a medieval piece: the record's picked riff on the lute
(A minor, G, F, G), the recorder singing, the monks answering "don't fear
the reaper", a shawm for the solo - and more bell.

A minor, 140 bpm, 52 bars (a minute and a half), made to loop:

    bars  1-4   Riff        the lute's riff, the bell
    bars  5-12  Verse       "All our times have come ..."
    bars 13-20  Chorus      "Come on baby" - the monks: "don't fear the reaper"
    bars 21-24  Riff
    bars 25-32  Solo        the shawm over the riff
    bars 33-40  Verse       the fiddle sings it
    bars 41-48  Chorus      everyone
    bars 49-52  Riff        into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/dont_fear_the_reaper.py           # the MIDI and the game's MP3
    python3 tools/music/dont_fear_the_reaper.py --midi    # just the MIDI
"""

from arrange import Band

RIFF = "Am G | F G"
VERSE_TUNE = ("rq E5e E5 D5q C5q | D5h rh | rq E5e E5 D5q C5q | B4h rh | "
              "re C5e C5 C5 D5 C5 A4 G4 | A4h rh | re C5e C5 C5 D5 E5 D5 C5 | D5h rh")
CHORUS = "F G | Am | F G | Am | F G | Am | F G | E"
CHORUS_TUNE = ("re C5e C5 C5 D5q E5q | E5h rh | rq E5e E5 D5 C5 D5q | C5h. rq | "
               "rq E5e E5 D5 C5 D5q | C5h. rq | rq E5e E5 D5 C5 D5q | B4h. rq")
ANSWER = ("rw | rw | rw | rq A4e A4 G4 A4 E4q | rw | rq A4e A4 G4 A4 E4q | rw | rq B4e B4 A4 B4 G#4q")
SOLO = ("E5q. D5e C5q A4q | B4e C5 D5 B4 G4h | A4e C5 E5 A5 G5q E5q | D5e E5 D5 C5 B4h | "
        "A4e C5 E5 C5 B4 D5 G5 D5 | A4e C5 F5 C5 B4 D5 G5 D5 | C5q. B4e A4q G4q | A4h. rq")

BELL = "G...G...G...G..."
BEAT = "D...T.t.D.D.T..."
DRIVE = "D.j.T.jtD.D.T.jt"


def build():
    band = Band("(Don't Fear) The Reaper (medieval)", bpm=140, key="Am",
                credit="(Don't Fear) The Reaper (Donald Roeser / Blue Oyster Cult), a medieval-style "
                       "arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Riff"), (5, "Verse"), (13, "Chorus"), (21, "Riff"), (25, "Solo"), (33, "Verse"),
                      (41, "Chorus"), (49, "Riff")):
        band.mark(bar, name)

    def riff(bar, bars, vel, drums=None, bell=True):
        spans = band.chords(bar, " | ".join([RIFF] * (bars // 2)))
        band.arp(spans, vel, "B132", ring=.9)
        band.bass(spans, 74, "R...R...")
        if bell:
            band.drums(bar, bars, BELL, vel=.8)
        if drums:
            band.drums(bar, bars, drums, vel=.85)
        return spans

    riff(1, 4, 64)
    riff(5, 8, 60, BEAT)
    band.lead(5, VERSE_TUNE, 84, shift=12)

    for start, big in ((13, False), (41, True)):
        ch = band.chords(start, CHORUS)
        band.arp(ch, 62, "B132", ring=.9)
        band.strum(ch, 58, "D...D.U.D...D.U.")
        band.bass(ch, 78, "R...R...")
        band.lead(start, CHORUS_TUNE, 90, shift=12)
        band.lead(start, ANSWER, 78, part="monks", legato=.98)
        band.harmony(start, ANSWER, 64, part="monks")
        band.drums(start, 8, DRIVE, vel=.9, fill="D.j.T.jtD.T.TTTT")
        band.drums(start, 8, BELL, vel=.8)
        if big:
            band.harmony(start, CHORUS_TUNE, 74, shift=12)
            band.pad(ch, 64)

    riff(21, 4, 64, BEAT)
    solo = riff(25, 8, 60, DRIVE)
    band.lead(25, SOLO, 82, part="shawm", legato=.9)
    band.hold(solo, 66)

    riff(33, 8, 58, BEAT)
    band.lead(33, VERSE_TUNE, 84, part="fiddle")
    riff(49, 4, 62, BEAT)
    band.bars(52)
    return band


if __name__ == "__main__":
    build().save("dont_fear_the_reaper", doc=__doc__)
