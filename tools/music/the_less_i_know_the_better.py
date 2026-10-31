#!/usr/bin/env python3
""""The Less I Know The Better" (Tame Impala, written by Kevin Parker) as a
medieval piece: the record's strutting bass line on a bass viol, the hand
drum's dance beat, the lute's chords chopped short, the recorder singing and
the psaltery shimmering where the record's synths do.

E minor, 117 bpm, 48 bars (a minute and a half), made to loop:

    bars  1-4   Intro       the viol's bass line and the drum
    bars  5-12  Verse       "Someone said they left together ..."
    bars 13-20  Chorus      "Oh my love, can't you see yourself by my side ..."
    bars 21-28  Bridge      the psaltery and the recorder over the bass line
    bars 29-36  Verse       the fiddle sings it
    bars 37-44  Chorus      everyone
    bars 45-48  Outro       the bass line, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/the_less_i_know_the_better.py           # the MIDI and the game's MP3
    python3 tools/music/the_less_i_know_the_better.py --midi    # just the MIDI
"""

from arrange import Band

GROOVE_CHORDS = "Em7 | A7 | Em7 | A7"
BASS_LINE = ("E3e. E3s rs G3s A3e B3e. A3s G3e E3e | A2e. A2s rs C#3s E3e G3e. E3s C#3e B2e | "
             "E3e. E3s rs G3s A3e B3e. A3s G3e E3e | A2e. A2s rs C#3s E3e G3e A3e G3e E3e")
VERSE_TUNE = ("re B4e B4 B4 A4 G4 A4 B4 | C#5q B4e A4 G4q rq | re B4e B4 B4 D5 B4 A4 G4 | A4h rh | "
              "re B4e B4 B4 A4 G4 A4 B4 | C#5q B4e A4 G4q rq | re G4e A4 B4 D5 E5 D5 B4 | A4h. rq")
CHORUS = "Cmaj7 | D | Bm7 | Em7 | Cmaj7 | D | Bm7 | A7"
CHORUS_TUNE = ("E5q. D5e B4q A4q | B4e D5 E5 D5 B4h | D5q. B4e A4q F#4q | G4e A4 B4 A4 E4h | "
               "E5q. D5e B4q A4q | B4e D5 E5 D5 F#5h | E5q. D5e B4q G4q | A4h. rq")
BRIDGE_TUNE = ("B5e A5 G5 E5 D5q E5q | rh. G5e A5 | B5e A5 G5 E5 D5q B4q | rh C#5e E5 A5q | "
               "B5e A5 G5 E5 D5q E5q | rh. G5e A5 | B5q. A5e G5q E5q | C#5h rh")

DISCO = "D.jtT.jtd.jtT.jt"
DISCO_SOFT = "D...T...d...T..."


def build():
    band = Band("The Less I Know The Better (medieval)", bpm=117, key="Em",
                credit="The Less I Know The Better (Kevin Parker / Tame Impala), a medieval-style "
                       "arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Chorus"), (21, "Bridge"), (29, "Verse"),
                      (37, "Chorus"), (45, "Outro")):
        band.mark(bar, name)
    band.mix("viol", volume=104, reverb=40)

    def groove(bar, bars, drums=DISCO, vel=.9, chop=True):
        spans = band.chords(bar, " | ".join([GROOVE_CHORDS] * (bars // 4)))
        for k in range(0, bars, 4):
            band.lead(bar + k, BASS_LINE, 86, part="viol", legato=.8)
        band.drums(bar, bars, drums, vel=vel)
        if chop:
            band.strum(spans, 52, "..x...x...x...x.")
            band.strum(spans, 56, "....D.......D...")
        return spans

    groove(1, 4, DISCO_SOFT, .8, chop=False)
    groove(5, 8, DISCO_SOFT, .8)
    band.lead(5, VERSE_TUNE, 82, shift=12)

    for start, big in ((13, False), (37, True)):
        ch = band.chords(start, CHORUS)
        band.bass(ch, 80, "R..R..R.", part="viol", low=40)
        band.strum(ch, 62, "D..DU.DUD..DU.DU")
        band.lead(start, CHORUS_TUNE, 90, shift=12)
        band.drums(start, 8, DISCO, vel=.95, fill="D.jtT.jtd.T.TTTT")
        band.arp(ch, 56, "1.2.3.2.", part="psaltery", ring=.8, shift=12)
        if big:
            band.harmony(start, CHORUS_TUNE, 76, shift=12)
            band.pad(ch, 62)

    bridge = groove(21, 8)
    band.lead(21, BRIDGE_TUNE, 96, part="psaltery", legato=1)
    band.lead(21, BRIDGE_TUNE, 62, legato=.85)

    groove(29, 8)
    band.lead(29, VERSE_TUNE, 84, part="fiddle")
    groove(45, 4, DISCO_SOFT, .85)
    band.bars(48)
    return band


if __name__ == "__main__":
    build().save("the_less_i_know_the_better", doc=__doc__)
