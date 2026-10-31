#!/usr/bin/env python3
""""Misery Business" (Paramore, written by Hayley Williams and Josh Farro) as
a medieval piece: the record's chattering sixteenth-note guitar on a fiddle,
the lute and the tabor flat out, the recorder for the voice.

E major, 172 bpm, 64 bars (a minute and a half), made to loop:

    bars  1-8   Riff        the fiddle's sixteenths, the drums
    bars  9-24  Verse       "I'm in the business of misery, let's take it from the top ..."
    bars 25-40  Chorus      "Whoa, I never meant to brag ..."
    bars 41-48  Riff
    bars 49-64  Chorus      everyone, the shawm with the recorder

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/misery_business.py           # the MIDI and the game's MP3
    python3 tools/music/misery_business.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "E | B | C#m | A"


def cell(low, high, mid):
    """Half a bar of the riff's sixteenths: the low note chattering under two above it."""
    return f"{low}s {low} {high} {low} {mid} {low} {high} {low}"


RIFF = " | ".join(cell(*n) + " " + cell(*n).replace("s ", " ", 1) for n in
                  (("E4", "B4", "G#4"), ("D#4", "B4", "F#4"), ("C#4", "G#4", "E4"), ("A3", "E4", "C#4")))
VERSE_TUNE = ("re B4e B4 B4 G#4 B4 C#5 B4 | G#4h rh | re B4e B4 B4 G#4 B4 C#5 E5 | D#5h rh | "
              "re C#5e C#5 C#5 B4 C#5 E5 C#5 | B4h rh | re A4e B4 C#5 B4 A4 G#4 F#4 | G#4h rh")
CHORUS_TUNE = ("E5h. D#5q | B4h rh | E5h. D#5q | C#5h rh | re E5e E5 E5 D#5 E5 F#5 E5 | C#5h rh | "
               "re C#5e C#5 B4 A4 B4 C#5 B4 | B4h rh")

PUNK = "B.T.D.T.B.T.D.TT"
PUNK_RIQ = "BjTjDjTjBjTjDjTT"


def build():
    band = Band("Misery Business (medieval)", bpm=172, key="E",
                credit="Misery Business (Hayley Williams, Josh Farro / Paramore), a medieval-style arrangement "
                       "for Larson's Game. Not for sale.")
    for bar, name in ((1, "Riff"), (9, "Verse"), (25, "Chorus"), (41, "Riff"), (49, "Chorus")):
        band.mark(bar, name)
    band.mix("fiddle", volume=127)

    def riff(bar, bars, vel=98):
        spans = band.chords(bar, " | ".join([LOOP] * (bars // 4)))
        for k in range(0, bars, 4):
            band.lead(bar + k, RIFF, vel, part="fiddle", legato=.7, shift=12)
        band.bass(spans, 78, "R.R.R.R.", part="viol", low=40)
        return spans

    r = riff(1, 8)
    band.drums(5, 4, PUNK, vel=.9)
    band.strum(r[4:], 60, "D.D.D.D.D.D.D.D.")

    v = riff(9, 16, 82)
    band.strum(v, 62, "D.DUD.DUD.DUD.DU")
    band.lead(9, VERSE_TUNE, 86, shift=12)
    band.lead(17, VERSE_TUNE, 88, shift=12)
    band.harmony(17, VERSE_TUNE, 64, part="monks")
    band.drums(9, 16, PUNK, vel=.9)

    for start, big in ((25, False), (49, True)):
        ch = band.chords(start, LOOP + " | " + LOOP + " | " + LOOP + " | " + LOOP)
        band.strum(ch, 70, "DUDUDUDUDUDUDUDU")
        band.bass(ch, 82, "R.R.R.R.", part="viol", low=40)
        band.lead(start, CHORUS_TUNE, 92, shift=12)
        band.lead(start + 8, CHORUS_TUNE, 92, shift=12)
        band.pad(ch, 64)
        band.drums(start, 16, PUNK_RIQ, vel=1.0, fill="BjTjDjTjTTTTTTTT")
        if big:
            band.lead(start, CHORUS_TUNE, 80, part="shawm", legato=.9)
            band.lead(start + 8, CHORUS_TUNE, 80, part="shawm", legato=.9)
            band.harmony(start, CHORUS_TUNE, 70, part="monks")
            band.harmony(start + 8, CHORUS_TUNE, 70, part="monks")

    r2 = riff(41, 8)
    band.strum(r2, 60, "D.D.D.D.D.D.D.D.")
    band.drums(41, 8, PUNK, vel=.95)
    band.bars(64)
    return band


if __name__ == "__main__":
    build().save("misery_business", doc=__doc__)
