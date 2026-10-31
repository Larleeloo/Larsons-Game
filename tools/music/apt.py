#!/usr/bin/env python3
""""APT." (ROSÉ and Bruno Mars, written by Rosé, Bruno Mars, Amy Allen,
Christopher Brody Brown, Henry Walter, Omer Fedi, Theron Thomas and others)
as a medieval drinking song: the record's "apateu, apateu" chant shouted by
the monks over hands clapped, the lute and the tabor at a gallop, the
recorder and the fiddle for the two voices.

C major, 149 bpm, 64 bars (a minute and three quarters), made to loop:

    bars  1-8   Chant       "Apateu, apateu" - the monks and the hands
    bars  9-16  Verse       "Kissy face, kissy face, sent to your phone ..." - the recorder
    bars 17-24  Chorus      "Don't you want me like I want you, baby ..."
    bars 25-32  Chant       everyone
    bars 33-40  Verse       the fiddle sings it
    bars 41-48  Chorus      everyone
    bars 49-56  Dance       a fiddle reel on the chorus chords
    bars 57-64  Chant       into the start

The tune is played by ear, so a note here and there may not be the
record's; fix it in the MIDI. An arrangement for Larson's Game, not for sale.

    python3 tools/music/apt.py           # the MIDI and the game's MP3
    python3 tools/music/apt.py --midi    # just the MIDI
"""

from arrange import Band

LOOP = "C | Em | Am | F"
CHANT = ("G4e G4 E4q G4e G4 E4q | G4e G4 E4q G4e G4 E4q | G4e G4 E4q G4e G4 E4q | A4e A4 G4q E4h | "
         "G4e G4 E4q G4e G4 E4q | G4e G4 E4q G4e G4 E4q | G4e G4 E4q G4e G4 E4q | A4e A4 G4q C5h")
VERSE_TUNE = ("re E5e E5 E5 D5 E5 G5 E5 | D5q B4h rq | re C5e C5 C5 B4 C5 E5 C5 | A4h rh | "
              "re E5e E5 E5 D5 E5 G5 E5 | D5q B4h rq | re C5e C5 D5 E5 D5 C5 A4 | C5h rh")
CHORUS_TUNE = ("G5q. E5e G5q A5q | G5e E5 B4h rq | A5q. G5e E5q C5q | C5e A4 C5h rq | "
               "G5q. E5e G5q A5q | G5e E5 B4h rq | A5e G5 E5 C5 D5q E5q | C5h. rq")
REEL = ("E5e G5 C6 G5 E5 G5 C6 G5 | E5e G5 B5 G5 E5 G5 B5 G5 | E5e A5 C6 A5 E5 A5 C6 A5 | F5e A5 C6 A5 F5 E5 D5 C5 | "
        "E5e G5 C6 G5 E5 G5 C6 G5 | E5e G5 B5 G5 E5 G5 B5 G5 | E5e A5 C6 A5 G5 E5 D5 C5 | D5e C5 A4 G4 C5h")

CLAPS = "C...C...C...C..."
GALLOP = "B.C.D.CjB.C.D.Cj"


def build():
    band = Band("APT. (medieval)", bpm=149, key="C",
                credit="APT. (Rosé, Bruno Mars, Amy Allen, Christopher Brody Brown, Henry Walter, Omer Fedi, "
                       "Theron Thomas and others / ROSÉ, Bruno Mars), a medieval-style arrangement for Larson's "
                       "Game. Not for sale.")
    for bar, name in ((1, "Chant"), (9, "Verse"), (17, "Chorus"), (25, "Chant"), (33, "Verse"), (41, "Chorus"),
                      (49, "Dance"), (57, "Chant")):
        band.mark(bar, name)
    band.mix("monks", volume=120)
    loop2 = LOOP + " | " + LOOP

    def chant(bar, full):
        spans = band.chords(bar, loop2)
        band.lead(bar, CHANT, 90, part="monks", legato=.9)
        band.harmony(bar, CHANT, 74, part="monks", below=False)
        band.drums(bar, 8, GALLOP if full else CLAPS, vel=1.0 if full else .9)
        if full:
            band.strum(spans, 66, "D.DUD.DUD.DUD.DU")
            band.bass(spans, 80, "R.R.R.R.", part="viol", low=40)
            band.lead(bar, CHANT, 70, shift=12, legato=.85)
        return spans

    chant(1, False)

    for start, part, shift in ((9, "recorder", 0), (33, "fiddle", -12)):
        v = band.chords(start, loop2)
        band.strum(v, 62, "D.xUD.xUD.xUD.xU")
        band.bass(v, 76, "R...R...")
        band.lead(start, VERSE_TUNE, 86, part=part, shift=shift)
        band.drums(start, 8, "D...C...D.D.C...", vel=.85)

    for start, big in ((17, False), (41, True)):
        ch = band.chords(start, loop2)
        band.strum(ch, 68, "D.DUD.DUD.DUD.DU")
        band.bass(ch, 82, "R.R.R.R.", part="viol", low=40)
        band.roll(ch, 54)
        band.lead(start, CHORUS_TUNE, 94)
        band.drums(start, 8, GALLOP, vel=1.0, fill="B.C.D.CjB.T.TTTT")
        band.pad(ch, 64)
        if big:
            band.harmony(start, CHORUS_TUNE, 76)
            band.harmony(start, CHORUS_TUNE, 66, part="monks", shift=-12)

    chant(25, True)

    d = band.chords(49, loop2)
    band.strum(d, 62, "D.UDD.UDD.UDD.UD")
    band.bass(d, 80, "R.F.R.F.", part="viol", low=40)
    band.lead(49, REEL, 88, part="fiddle", legato=.85)
    band.drums(49, 8, GALLOP, vel=.95)

    chant(57, True)
    band.bars(64)
    return band


if __name__ == "__main__":
    build().save("apt", doc=__doc__)
