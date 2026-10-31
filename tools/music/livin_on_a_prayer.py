#!/usr/bin/env python3
""""Livin' On A Prayer" (Bon Jovi, written by Jon Bon Jovi, Richie Sambora and
Desmond Child) as a medieval piece: the record's pulsing bass on a bass viol,
the talk box's riff on a shawm (as nasal as a talk box), the recorder
singing, and the last chorus a whole step up, as the record does it.

E minor, 123 bpm, 48 bars (a minute and a half), made to loop:

    bars  1-4   Intro       the viol's pulse, the shawm's riff
    bars  5-12  Verse       "Tommy used to work on the docks ..."
    bars 13-20  Pre-chorus  "We've got to hold on to what we've got ..."
    bars 21-28  Chorus      "Whoa, we're half way there ..."
    bars 29-36  Solo        the fiddle over the chorus chords
    bars 37-44  Chorus      a whole step up, everyone
    bars 45-48  Outro       the riff again, back in E minor, into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/livin_on_a_prayer.py           # the MIDI and the game's MP3
    python3 tools/music/livin_on_a_prayer.py --midi    # just the MIDI
"""

from arrange import Band, transpose

RIFF_CHORDS = "Em | C D | Em | C D"
RIFF = "E5q. D5e E5h | rh G5e F#5 E5 D5 | E5q. D5e E5h | rh B4e D5 E5q"
VERSE_TUNE = ("re B4e B4 B4 A4 G4 A4 B4 | G4q E4h. | re B4e B4 B4 A4 G4 A4 B4 | G4q E4h. | "
              "re B4e B4 B4 D5 B4 A4 G4 | A4h. rq | re B4e B4 B4 D5 B4 A4 G4 | E4h. rq")
PRE = "C | D | Em | Em | C | D | G | D"
PRE_TUNE = ("re G4e G4 G4 A4 G4 E4 G4 | A4h. rq | re B4e B4 B4 C5 B4 G4 B4 | B4h. rq | "
            "re C5e C5 C5 D5 C5 B4 C5 | D5h. rq | re D5e D5 E5 D5 B4 D5 E5 | F#5h. rq")
CHORUS = "Em C | D G | Em C | D G | C D | G Em | Em C | D G"
CHORUS_TUNE = ("E5q. D5e rq B4e D5 | E5q D5q B4h | E5q. D5e rq B4e D5 | E5e D5 B4 D5q G4q. | "
               "rq B4e B4 B4 A4 G4 A4 | B4h. rq | E5q. D5e rq B4e D5 | E5e D5 B4 D5q E5q.")
SOLO = ("B5q. A5e G5q E5q | F#5e G5 A5 F#5 D5h | E5e G5 B5 G5 E5q D5q | B4e D5 G5 D5 B4h | "
        "C5e E5 G5 E5 D5e F#5 A5 F#5 | G5q D5q B4h | E5e F#5 G5 A5 B5q A5q | G5e F#5 D5 F#5 E5h")

PULSE = "RRRRRRRR"
ROCK = "D...T.t.D.D.T..."
DRIVE = "D.jtT.jtD.DtT.jt"


def build():
    band = Band("Livin' On A Prayer (medieval)", bpm=123, key="Em",
                credit="Livin' On A Prayer (Jon Bon Jovi, Richie Sambora, Desmond Child / Bon Jovi), a "
                       "medieval-style arrangement for Larson's Game. Not for sale.")
    for bar, name in ((1, "Intro"), (5, "Verse"), (13, "Pre-chorus"), (21, "Chorus"), (29, "Solo"),
                      (37, "Chorus (up a step)"), (45, "Outro")):
        band.mark(bar, name)
    band.mix("viol", volume=127)

    def riff(bar):
        spans = band.chords(bar, RIFF_CHORDS)
        band.bass(spans, 70, PULSE, part="viol", low=40)
        band.lead(bar, RIFF, 84, part="shawm", legato=.9)
        band.strum(spans, 52, "D.......D...D...")
        band.drums(bar, 4, ROCK, vel=.8)

    riff(1)

    verse = band.chords(5, RIFF_CHORDS + " | " + RIFF_CHORDS)
    band.bass(verse, 66, PULSE, part="viol", low=40)
    band.arp(verse, 56, "B1231323")
    band.lead(5, VERSE_TUNE, 82, shift=12)
    band.drums(5, 8, "D.......D...T...", vel=.7)

    pre = band.chords(13, PRE)
    band.bass(pre, 70, PULSE, part="viol", low=40)
    band.strum(pre, 60, "D..DU.DUD..DU.DU")
    band.lead(13, PRE_TUNE, 86, shift=12)
    band.hold(pre, 72)
    band.drums(13, 8, ROCK, vel=.85, fill="D...T.t.D.T.TTTT")

    for start, up in ((21, 0), (37, 2)):
        ch = band.chords(start, transpose(CHORUS, up))
        band.bass(ch, 76, PULSE, part="viol", low=40)
        band.strum(ch, 66, "D..DU.DUD..DU.DU")
        band.roll(ch, 56)
        band.lead(start, CHORUS_TUNE, 92, shift=12 + up)
        band.lead(start, CHORUS_TUNE, 66, part="monks", legato=.98, shift=up)
        band.drums(start, 8, DRIVE, vel=.95, fill="D.jtT.jtD.T.TTTT")
        band.pad(ch, 66)
        if up:
            band.harmony(start, CHORUS_TUNE, 78, shift=12 + up)

    solo = band.chords(29, CHORUS)
    band.bass(solo, 72, PULSE, part="viol", low=40)
    band.strum(solo, 60, "D..DU.DUD..DU.DU")
    band.lead(29, SOLO, 88, part="fiddle", legato=.9)
    band.drums(29, 8, DRIVE, vel=.85)

    riff(45)
    band.bars(48)
    return band


if __name__ == "__main__":
    build().save("livin_on_a_prayer", doc=__doc__)
