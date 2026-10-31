#!/usr/bin/env python3
""""Espresso" (Sabrina Carpenter, written by Sabrina Carpenter, Amy Allen,
Steph Jones and Julian Bunetta) as a medieval piece: the record's sly funk
on a hammered dulcimer's stabs and a bass viol, the hand drum's groove, the
recorder singing and the psaltery's shimmer.

A minor, 104 bpm, 48 bars (just under two minutes), made to loop:

    bars  1-4   Intro       the viol's bass line, the dulcimer's stabs
    bars  5-12  Verse       "Now he's thinkin' 'bout me every night ..."
    bars 13-20  Chorus      "That's that me espresso ..."
    bars 21-28  Verse       the fiddle sings it
    bars 29-36  Chorus      everyone
    bars 37-44  Bridge      the psaltery and the recorder
    bars 45-48  Outro       the groove, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/espresso.py           # the MIDI and the game's MP3
    python3 tools/music/espresso.py --midi    # just the MIDI
"""

from arrange import Band

GROOVE = "Am7 | D9 | Am7 | D9"
BASS_LINE = "A2e. A2s rs C3s E3e G3e. E3s D3e C3e | D3e. D3s rs F#3s A3e C4e. A3s F#3e D3e"
VERSE_TUNE = ("re E5e E5 D5 E5 G5 E5q | D5e C5 A4h rq | re E5e E5 D5 E5 G5 A5q | F#5e E5 D5h rq | "
              "re E5e E5 D5 E5 G5 E5q | D5e C5 A4h rq | re C5e D5 E5 G5 A5 G5 E5 | F#5h. rq")
CHORUS = "Fmaj7 | Em7 | Dm7 | G | Fmaj7 | Em7 | Dm7 | E7"
CHORUS_TUNE = ("re A5e A5 G5 A5q. G5e | E5q C5h rq | re A5e A5 G5 A5 C6 A5 G5 | D5h. rq | "
               "re A5e A5 G5 A5q. G5e | E5q C5h rq | re D5e E5 F5 E5 D5 C5 B4 | G#4h. rq")
BRIDGE_TUNE = ("A5e G5 E5 D5 C5q A4q | C5e D5 E5 F#5 A5h | A5e G5 E5 D5 C5q A4q | D5e E5 F#5 E5 D5h | "
               "E5q. D5e C5q A4q | C5e D5 E5 G5 F#5h | E5e D5 C5 A4 C5 D5 E5 C5 | B4h. rq")

FUNK = "D..tT.d.D.dtT.tj"
FUNK_SOFT = "D.....d.D...T..."


def build():
    band = Band("Espresso (medieval)", bpm=104, key="Am",
                credit="Espresso (Sabrina Carpenter, Amy Allen, Steph Jones, Julian Bunetta / Sabrina Carpenter), "
                       "a medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Verse"), (29, "Chorus"),
                      (37, "Bridge"), (45, "Outro")):
        band.mark(bar, name)
    band.mix("viol", volume=110, reverb=40)

    def groove(bar, bars, drums=FUNK, vel=.9):
        spans = band.chords(bar, " | ".join([GROOVE] * (bars // 4)))
        for k in range(0, bars, 2):
            band.lead(bar + k, BASS_LINE, 86, part="viol", legato=.75)
        band.strum(spans, 60, "..D...U...D..U..", part="dulcimer")
        band.drums(bar, bars, drums, vel=vel)
        return spans

    groove(1, 4, FUNK_SOFT, .8)
    for start, part, shift in ((5, "recorder", 0), (21, "fiddle", -12)):
        groove(start, 8)
        band.lead(start, VERSE_TUNE, 84, part=part, shift=shift)
        if part == "fiddle":
            band.harmony(start, VERSE_TUNE, 60, part="recorder", below=False)

    for start, big in ((13, False), (29, True)):
        ch = band.chords(start, CHORUS)
        band.bass(ch, 80, "R..R..F.", part="viol", low=40)
        band.strum(ch, 62, "D..DU.DUD..DU.DU")
        band.arp(ch, 52, "1.2.3.2.", part="psaltery", ring=.8, shift=12)
        band.lead(start, CHORUS_TUNE, 96)
        band.drums(start, 8, FUNK, vel=1.0, fill="D..tT.d.D.T.TTTT")
        if big:
            band.harmony(start, CHORUS_TUNE, 66, part="monks", shift=-12)
            band.pad(ch, 60)

    groove(37, 8)
    band.lead(37, BRIDGE_TUNE, 96, part="psaltery", legato=1)
    band.lead(37, BRIDGE_TUNE, 70, legato=.85)
    groove(45, 4, FUNK_SOFT, .85)
    band.bars(48)
    return band


if __name__ == "__main__":
    build().save("espresso", doc=__doc__)
