#!/usr/bin/env python3
"""The game's own twenty songs: new songs written for Larson's Game, each
shaped like a pop song - an intro, verses, a chorus that comes back bigger,
sometimes a bridge or a break - and played by a medieval band: lute, harp,
recorder, fiddle, hammered dulcimer, shawm, bagpipe, bass viol, psaltery,
the hand drums, monks. None of them is anyone else's song: the tunes, and
how they go together, are their own.

    python3 tools/music/originals.py                    # every song: its MIDI and the game's MP3
    python3 tools/music/originals.py --midi             # just the MIDI files
    python3 tools/music/originals.py copper_hearts      # just these
    python3 tools/music/originals.py --list             # what there is

Each writes music/<id>_medieval.mid (open it in FL Studio) and
assets/sounds/music/<id>.mp3 (a seamless loop). The game's list of them is
audio/Songs.java; keep the two in step.

A song here is a plan: its sections in order, each with its chords (see
arrange.py), the tune over them (or none), how hard the band plays (0 the
intro's hush to 3 the last chorus) and who carries the tune. How a band
plays at each of those is its style (STYLES): what the lute does, the bass,
the drums, when the organ, the monks or a second voice come in.
"""

import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from arrange import Band  # noqa: E402

CREDIT = "{title}, an original song for Larson's Game in the manner of a medieval band. Not for sale."


# --- how a band plays -------------------------------------------------------------------
#
# Each entry is per how hard the band plays (0 .. 3):
#   comp   how the chords are played: ("strum", rhythm, vel[, part]) or ("arp", pattern, step, vel[, part])
#   bass   the bass line's rhythm (arrange.Band.bass), on bass_part
#   drums  the hand drum's patterns (arrange.Band.drums); drums2 more over them (hands clapped, a shaker)
# and from which strength on: harmony (a second voice under the tune), pad (held chords: [(from, part)]),
# roll (the harp's rolled chords), hold (a slow line of held notes), drone (the key's note and its fifth).

STYLES = {
    "tavern": dict(
        comp=[("arp", "B1232123", .5, 54), ("strum", "D.DU.UDU", 58), ("strum", "D.DU.UDU", 62),
              ("strum", "D.DUDUDU", 66)],
        bass=["R.......", "R...R...", "R...F...", "R.R.F.R."], bass_part="harp",
        drums=[None, ["D.......t.......", "D.......t...t..."], ["D...t.t.D.d.t..."], ["D.jtT.tjD.dtT.tj"]],
        fill="D.jtT.tjD.T.TTTT", harmony=3, pad=[(3, "organ")], roll=2),
    "waltz": dict(
        comp=[("arp", "B12321", .5, 52), ("strum", "D.U.U.", 56), ("strum", "D.U.U.", 60), ("strum", "D.UUU.", 64)],
        bass=["R.....", "R.....", "R...F.", "R.F.F."], bass_part="harp",
        drums=[None, ["D...t...t..."], ["D...t.t.t..."], ["B..jt.jjt.j."]],
        fill="B..jT.TTT.TT", harmony=3, harmony_part="recorder", pad=[(3, "organ")], hold=2,
        hold_part="viol", hold_range=(45, 57)),
    "ballad": dict(
        comp=[("arp", "B1234321", .5, 50, "harp"), ("arp", "B1232123", .5, 54), ("arp", "B1232123", .5, 58),
              ("strum", "D..U.UD.", 62)],
        bass=["R.......", "R.......", "R...R...", "R...F..."], bass_part="harp",
        drums=[None, None, ["B.......d......."], ["B.....d.D...t..d"]],
        fill="B.....d.D.TTT.TT", harmony=3, pad=[(2, "organ")], roll=3, hold=1, hold_part="viol",
        hold_range=(45, 57)),
    "dance": dict(
        comp=[("arp", "B1231231B1231231", .25, 46, "dulcimer"), ("strum", ".U.U.U.U", 52),
              ("strum", "D.Ux.UxU", 56), ("strum", "D.Ux.UxU", 60)],
        bass=["R.......", "RORORORO", "RORORORO", "RORORORO"], bass_part="viol", bass_low=36,
        drums=[None, ["B...C...B...C..."], ["B.S.C.S.B.S.C.S."], ["B.SJC.SJB.SJC.SJ"]],
        fill="B.SJC.SJB.T.TTTT", harmony=3, pad=[(3, "organ")]),
    "gallop": dict(
        comp=[("strum", "D...........", 58), ("strum", "D.UD.UD.UD.U", 58), ("strum", "D.UD.UD.UD.U", 62),
              ("strum", "D.UD.UD.UD.U", 66)],
        bass=["R.......", "R..R..R..R..", "R.RR.RR.RR.R", "R.RR.RR.RR.R"], bass_part="viol", bass_low=36,
        drums=[["D..........."], ["D.tD.tD.tD.t"], ["D.tD.tD.tT.t"], ["B.tD.tB.tD.t", "B.tD.tB.tDTT"]],
        fill="B.tD.tBTTTTT", harmony=3, drone=3, drone_part="bagpipe"),
    "groove": dict(
        comp=[("arp", "B.2.3.1.", .5, 52, "psaltery"), ("strum", ".x.U.x.U", 54), ("strum", "D.xU.xDU", 58),
              ("strum", "D.xU.xDU", 62)],
        bass=["R.......", "R..R..F.", "R..R.FO.", "R..R.FO."], bass_part="viol", bass_low=36,
        drums=[None, ["D...T..dD...T..."], ["D.jtT.jdD.jtT.jt"], ["D.jtT.jdD.jtT.jt"]],
        drums2=[None, None, None, ["....C.......C..."]], harmony=3, roll=3,
        arp2=[None, None, None, ("arp", "1.3.2.3.", .5, 48, "dulcimer")]),
    "country": dict(
        comp=[("arp", "B1232123", .5, 54), ("arp", "B.23b.23", .5, 56), ("arp", "B.23b.23", .5, 60),
              ("strum", "D.DU.UDU", 62)],
        bass=["R.......", "R...F...", "R...F...", "R.R.F.R."], bass_part="harp",
        drums=[None, ["D...t...D...t..."], ["D..tt.t.D..tt.t."], ["D.jtt.tjD.jtt.tj"]],
        fill="D.jtt.tjD.TTTTTT", harmony=3, hold=2, hold_part="viol", hold_range=(45, 57), roll=3),
    "epic": dict(
        comp=[("arp", "B1231231", .5, 50, "harp"), ("strum", "D...D.D.", 58), ("strum", "D.D.D.DU", 62),
              ("strum", "D.DUD.DU", 66)],
        bass=["R.......", "R...R...", "R.R.R.R.", "RORORORO"], bass_part="viol", bass_low=36,
        drums=[["B..............."], ["B.......B...b..."], ["B.b.D.d.B.b.D.dd"],
               ["B.b.D.d.B.b.D.dd", "B.b.D.d.B.D.DDDD"]],
        harmony=3, pad=[(1, "organ")], hold=2, hold_part="sackbut", hold_range=(50, 62), bells=3),
    "dreamy": dict(
        comp=[("arp", "B1234321", .5, 48, "harp"), ("arp", "B1234321", .5, 52, "harp"),
              ("arp", "B1234321", .5, 56, "harp"), ("arp", "B1234321", .5, 58, "harp")],
        bass=[None, "R.......", "R.......", "R...F..."], bass_part="viol", bass_low=36,
        drums=[None, None, ["d.......d.....j."], ["D.......d...t.j."]],
        harmony=2, harmony_part="fiddle", pad=[(2, "organ")], roll=3, roll_part="psaltery"),
    "punk": dict(
        comp=[("strum", "D.......", 60), ("strum", "D.D.D.D.", 62), ("strum", "DDDDDDDD", 64),
              ("strum", "DDDDDDDD", 68)],
        bass=["R.......", "RRRRRRRR", "RRRRRRRR", "RRRRRRRR"], bass_part="viol", bass_low=36,
        drums=[["D..............."], ["D...T...D...T..."], ["D.T.D.T.D.T.DDT."], ["B.TjD.TjB.TjDDTj"]],
        fill="B.TjD.TjBTTTTTTT", harmony=3, drone=3, drone_part="bagpipe"),
    "shanty": dict(
        comp=[("strum", "D.......", 56), ("strum", "D...D.U.", 58), ("strum", "D.U.D.U.", 62),
              ("strum", "D.UDD.UD", 66)],
        bass=["R.......", "R...F...", "R.F.R.F.", "R.F.R.F."], bass_part="viol", bass_low=36,
        drums=[None, ["B.......C......."], ["B...C...B.B.C..."], ["B...C...B.B.C...", "B...C...B.B.CCCC"]],
        harmony=3, harmony_part="recorder", pad=[(3, "monks")], drone=1),
    "anthem": dict(
        comp=[("arp", "B1232123", .5, 56), ("strum", "D...D...", 58), ("strum", "D.D.D.D.", 62),
              ("strum", "D.DUD.DU", 66)],
        bass=["R.......", "R.......", "R.R.R.R.", "R.R.R.R."], bass_part="viol", bass_low=36,
        drums=[None, ["D.......t......."], ["D...T...D...T..."], ["B...T.C.B.B.T.C."]],
        fill="B...T.C.B.TTTTTT", harmony=3, pad=[(3, "organ"), (3, "monks")], hold=2, hold_part="sackbut",
        hold_range=(50, 62)),
    "bright": dict(
        comp=[("arp", "B123123412312341", .25, 46, "psaltery"), ("arp", "B123123412312341", .25, 48, "psaltery"),
              ("arp", "B123123412312341", .25, 50, "psaltery"), ("arp", "B123123412312341", .25, 52, "psaltery")],
        comp2=[None, None, ("strum", "x.U.x.U.", 52), ("strum", "x.U.x.U.", 56)],
        bass=["R.......", "R.R.R.R.", "RORORORO", "RORORORO"], bass_part="viol", bass_low=36,
        drums=[["B...C...B...C..."], ["B...C...B...C..."], ["B.S.C.S.B.SBC.S."], ["B.SJC.SJB.SBC.SJ"]],
        harmony=3, harmony_part="recorder", pad=[(3, "organ")]),
}


def section_bars(chords):
    """How many bars a chord progression spans."""
    return len([c for c in chords.split("|") if c.strip()])


def play(band, style, spans, bar, n, e):
    """The band's part in a section of `n` bars from `bar`, at strength `e`."""
    st = STYLES[style]
    for key in ("comp", "comp2", "arp2"):
        c = (st.get(key) or [None] * 4)[e]
        if not c:
            continue
        if c[0] == "strum":
            band.strum(spans, c[2], c[1], part=c[3] if len(c) > 3 else st.get("comp_part", "lute"))
        else:
            band.arp(spans, c[3], c[1], step=c[2], part=c[4] if len(c) > 4 else st.get("comp_part", "lute"))
    b = st["bass"][e]
    if b:
        band.bass(spans, 70 + 3 * e, b, part=st.get("bass_part", "harp"), low=st.get("bass_low", 31))
    d = st["drums"][e]
    if d:
        band.drums(bar, n, *d, vel=.68 + .1 * e, fill=st.get("fill") if e >= 2 else None)
    d2 = (st.get("drums2") or [None] * 4)[e]
    if d2:
        band.drums(bar, n, *d2, vel=.7)
    for at, part in st.get("pad", []):
        if e >= at:
            band.pad(spans, 56 + 4 * e, part=part)
    if e >= st.get("roll", 9):
        band.roll(spans, 50 + 3 * e, part=st.get("roll_part", "harp"))
    if e >= st.get("hold", 9):
        lo, hi = st.get("hold_range", (62, 74))
        band.hold(spans, 66 + 4 * e, part=st.get("hold_part", "fiddle"), low=lo, high=hi)
    if e >= st.get("drone", 9):
        root = band.tonic + 38                      # the key's note low, and its fifth
        band.drone(bar, n, [root, root + 7], 56 + 4 * e, part=st.get("drone_part", "organ"))
    if e >= st.get("bells", 9):
        bells = band.part("bells")
        for s in spans:
            bells.note(s.start, 2, s.voices[-1] + 12, 58)


def build(song):
    """A Band playing `song` (one of SONGS), ready to save."""
    band = Band(song["title"] + " (medieval)", bpm=song["bpm"], key=song["key"],
                credit=CREDIT.format(title=song["title"]), beats_per_bar=song.get("beats", 4))
    bar = 1
    style = song["style"]
    for sec in song["plan"]:
        name, chords, tune, e = sec[:4]
        opts = sec[4] if len(sec) > 4 else {}
        n = section_bars(chords)
        band.mark(bar, name)
        spans = band.chords(bar, chords)
        play(band, style, spans, bar, n, e)
        if tune:
            lead = opts.get("lead", song.get("lead", "recorder"))
            band.lead(bar, tune, opts.get("vel", 76 + 4 * e), part=lead, shift=opts.get("shift", 0))
            st = STYLES[style]
            if e >= st.get("harmony", 9) and not opts.get("alone"):
                second = st.get("harmony_part", "fiddle")
                if second == lead:
                    second = "recorder" if lead != "recorder" else "fiddle"
                band.harmony(bar, tune, 64 + 3 * e, part=second, shift=opts.get("shift", 0))
        bar += n
    band.bars(bar - 1)
    return band


# --- the songs ------------------------------------------------------------------------------
#
# Each: id (its file names), title, about (what kind of song: shown beside its title), key, tempo, style,
# who sings by default (lead), and its plan: (section, chords, tune or None, strength 0-3[, options]).

SONGS = []


def song(**kw):
    SONGS.append(kw)


# 1 ------------------------------------------------------------------------------------------
LANTERN_VERSE = ("re F#4e F#4 E4 D4q F#4q | E4q. C#4e E4h | re F#4e F#4 G4 A4q F#4q | G4h. rq | "
                 "re A4e A4 B4 A4q F#4q | E4q. D4e E4h | re D4e E4 F#4 G4q. F#4e | E4w")
LANTERN_PRE = "G4q G4e A4 B4q A4q | B4q B4e C#5 D5h | D5q C#5e B4 A4q F#4q | E4h. rq"
LANTERN_CHORUS = ("D5q. B4e D5q E5q | F#5h. D5q | E5q. C#5e A4q B4q | B4h. rq | "
                  "D5q. B4e D5q E5q | F#5h A5q F#5q | E5q. D5e C#5q E5q | D5w")
song(id="lanterns_over_brasswick", title="Lanterns Over Brasswick", about="a tavern anthem", key="D", bpm=116,
     style="tavern", lead="recorder", plan=[
         ("Intro", "D | A | Bm | G", None, 0),
         ("Verse", "D | A | Bm | G | D | A | G | A", LANTERN_VERSE, 1, dict(lead="fiddle")),
         ("Pre-chorus", "Em | G | Bm | A", LANTERN_PRE, 2, dict(lead="fiddle")),
         ("Chorus", "G | D | A | Bm | G | D | A | D", LANTERN_CHORUS, 2),
         ("Verse", "D | A | Bm | G | D | A | G | A", LANTERN_VERSE, 2, dict(shift=12)),
         ("Pre-chorus", "Em | G | Bm | A", LANTERN_PRE, 2, dict(shift=12)),
         ("Chorus", "G | D | A | Bm | G | D | A | D", LANTERN_CHORUS, 3),
         ("Outro", "G | A | D | D", None, 1),
     ])

# 2 ------------------------------------------------------------------------------------------
MILLER_VERSE = ("D4q G4q A4q | B4h. | C5q B4q A4q | B4h G4q | E4q G4q B4q | C5q. B4e A4q | A4q B4q C5q | "
                "D5h. |")
MILLER_CHORUS = ("E5q D5q C5q | B4h G4q | A4q B4q C5q | B4h. | E5q D5q C5q | D5q. B4e G4q | A4q G4q F#4q | "
                 "G4h. |")
MILLER_BRIDGE = ("B4q. A4e G4q | E4h. | G4q A4q B4q | D5h. | B4q. A4e G4q | E4h G4q | A4q B4q A4q | F#4h. |")
song(id="the_millers_daughter", title="The Miller's Daughter", about="a country waltz", key="G", bpm=126,
     beats=3, style="waltz", lead="flute", plan=[
         ("Intro", "G | C | D | G", None, 0),
         ("Verse", "G | G | C | G | Em | C | D | D", MILLER_VERSE, 1),
         ("Chorus", "C | G | D | Em | C | G | D | G", MILLER_CHORUS, 2, dict(lead="fiddle")),
         ("Verse", "G | G | C | G | Em | C | D | D", MILLER_VERSE, 1, dict(lead="fiddle")),
         ("Bridge", "Em | C | G | D | Em | C | D | D", MILLER_BRIDGE, 1, dict(lead="dulcimer", shift=12)),
         ("Chorus", "C | G | D | Em | C | G | D | G", MILLER_CHORUS, 3, dict(lead="fiddle")),
         ("Chorus", "C | G | D | Em | C | G | D | G", MILLER_CHORUS, 3, dict(lead="flute", shift=12)),
         ("Outro", "C | D | G | G", None, 0),
     ])

# 3 ------------------------------------------------------------------------------------------
COPPER_VERSE = ("rq E4q A4q B4q | C5h. A4q | G4q. A4e G4q E4q | G4w | rq E4q A4q B4q | C5h. D5q | "
                "E5q. D5e C5q B4q | B4w")
COPPER_CHORUS = ("A5h G5q. E5e | D5h. B4q | C5q. D5e E5q A4q | A4h. rq | A5h G5q. E5e | D5h. G5q | "
                 "E5q. D5e C5q D5q | B4h. rq")
song(id="copper_hearts", title="Copper Hearts", about="a slow ballad", key="Am", bpm=82, style="ballad",
     lead="flute", plan=[
         ("Intro", "Am | F | C | G", None, 0),
         ("Verse", "Am | F | C | G | Am | F | G | G", COPPER_VERSE, 1, dict(shift=12)),
         ("Chorus", "F | G | Am | Am | F | G | C | E", COPPER_CHORUS, 2),
         ("Verse", "Am | F | C | G | Am | F | G | G", COPPER_VERSE, 1, dict(lead="fiddle")),
         ("Chorus", "F | G | Am | Am | F | G | C | E", COPPER_CHORUS, 3),
         ("Outro", "F | G | Am | Am", None, 0),
     ])

# 4 ------------------------------------------------------------------------------------------
CLOCK_RIFF = ("E5e G5 B5 G5 E5 G5 A5q | G5e E5 D5 E5 B4h | C5e E5 G5 E5 C5 E5 F#5q | D5e F#5 A5 F#5 D5h")
CLOCK_VERSE = ("E4q E4e G4 rq E4q | D4e E4 G4q E4h | E4q E4e G4 rq A4q | G4e F#4 D4q D4h | "
               "E4q E4e G4 rq B4q | A4e G4 E4q E4h | G4q G4e A4 rq B4q | A4e G4 F#4q F#4h")
CLOCK_PRE = "A4q C5q E5q C5q | E5q. D5e C5q G4q | A4q C5q E5q A5q | F#5h D#5h"
CLOCK_CHORUS = ("B5q. A5e G5q E5q | G5q. F#5e E5h | D5q. E5e G5q D5q | F#5h. rq | "
                "B5q. A5e G5q E5q | G5q. F#5e E5q G5q | A5q. G5e F#5q E5q | D#5h. rq")
song(id="clockwork_heart", title="Clockwork Heart", about="a clockwork dance", key="Em", bpm=122,
     style="dance", lead="shawm", plan=[
         ("Intro", "Em | Em | C | D", CLOCK_RIFF, 1, dict(lead="dulcimer", alone=True)),
         ("Verse", "Em | Em | C | D | Em | Em | C | D", CLOCK_VERSE, 1, dict(lead="fiddle")),
         ("Pre-chorus", "Am | C | Am | B", CLOCK_PRE, 2),
         ("Chorus", "Em | C | G | D | Em | C | G | B", CLOCK_CHORUS, 3, dict(lead="recorder")),
         ("Break", "Em | Em | C | D", CLOCK_RIFF, 2, dict(lead="dulcimer", alone=True)),
         ("Verse", "Em | Em | C | D | Em | Em | C | D", CLOCK_VERSE, 2, dict(lead="fiddle")),
         ("Pre-chorus", "Am | C | Am | B", CLOCK_PRE, 2),
         ("Chorus", "Em | C | G | D | Em | C | G | B", CLOCK_CHORUS, 3, dict(lead="recorder")),
         ("Outro", "Em | Em | C | D", CLOCK_RIFF, 1, dict(lead="dulcimer", alone=True)),
     ])

# 5 ------------------------------------------------------------------------------------------
DAWN_VERSE = ("B4q B4q A4q B4q | D5h B4h | G4q G4q F#4q G4q | A4h. rq | B4q B4q A4q B4q | D5q. E5e F#5h | "
              "E5q D5q B4q D5q | C#5h. rq")
DAWN_CHORUS = ("F#5h. E5q | E5q. C#5e A4h | D5q. C#5e B4q D5q | D5h B4h | F#5h. A5q | A5q. G5e E5h | "
               "D5q. C#5e B4q D5q | C#5w")
DAWN_BRIDGE = ("G4q B4q E5q B4q | D5h. B4q | F#5q E5q D5q B4q | C#5h. rq | G4q B4q E5q G5q | G5q. F#5e D5h | "
               "E5q D5q C#5q B4q | A#4w")
song(id="ride_out_at_dawn", title="Ride Out at Dawn", about="a galloping rock song", key="Bm", bpm=144,
     style="gallop", lead="fiddle", plan=[
         ("Intro", "Bm | G | D | A", None, 2),
         ("Verse", "Bm | Bm | G | A | Bm | Bm | G | A", DAWN_VERSE, 1),
         ("Chorus", "D | A | Bm | G | D | A | G | A", DAWN_CHORUS, 3, dict(lead="recorder")),
         ("Verse", "Bm | Bm | G | A | Bm | Bm | G | A", DAWN_VERSE, 2, dict(lead="shawm")),
         ("Chorus", "D | A | Bm | G | D | A | G | A", DAWN_CHORUS, 3, dict(lead="recorder")),
         ("Bridge", "Em | G | Bm | A | Em | G | A | F#", DAWN_BRIDGE, 2),
         ("Chorus", "D | A | Bm | G | D | A | G | A", DAWN_CHORUS, 3, dict(lead="recorder")),
         ("Outro", "Bm | G | A | Bm", None, 2),
     ])

# 6 ------------------------------------------------------------------------------------------
MARKET_VERSE = ("re A4e C5 A4 G4q F4q | re G4e A4 G4 E4h | re F4e G4 A4 Bb4q A4q | G4q. F4e G4h | "
                "re A4e C5 A4 G4q F4q | re G4e A4 C5 D5h | C5q. Bb4e A4q G4q | E4h. rq")
MARKET_CHORUS = ("D5q D5e F5 rq E5q | D5q C5e Bb4 rq A4q | A4q A4e C5 rq F5q | E5h C5h | "
                 "D5q D5e F5 rq E5q | D5q C5e D5 rq F5q | G5q. F5e E5q C5q | C5h. rq")
song(id="moonlit_market", title="Moonlit Market", about="a sly market groove", key="F", bpm=104,
     style="groove", lead="recorder", plan=[
         ("Intro", "F | Am | Bb | C", None, 0),
         ("Verse", "F | Am | Bb | C | F | Am | Bb | C", MARKET_VERSE, 1, dict(shift=12)),
         ("Chorus", "Dm | Bb | F | C | Dm | Bb | C | C", MARKET_CHORUS, 2, dict(lead="fiddle")),
         ("Verse", "F | Am | Bb | C | F | Am | Bb | C", MARKET_VERSE, 2, dict(lead="psaltery", shift=12)),
         ("Chorus", "Dm | Bb | F | C | Dm | Bb | C | C", MARKET_CHORUS, 3),
         ("Break", "Dm | Bb | C | C", None, 2),
         ("Chorus", "Dm | Bb | F | C | Dm | Bb | C | C", MARKET_CHORUS, 3, dict(lead="fiddle")),
         ("Outro", "F | Am | Bb | C", None, 1),
     ])

# 7 ------------------------------------------------------------------------------------------
EMBER_VERSE = ("G4q. Eb4e C4q Eb4q | F4h Eb4h | G4q. Bb4e G4q F4q | D4w | G4q. Eb4e C4q Eb4q | F4h Ab4h | "
               "G4q. F4e Eb4q D4q | D4w")
EMBER_CHORUS = ("C5h Eb5h | F5q. Eb5e D5h | Eb5q. D5e C5q G4q | C5w | C5h Eb5h | F5q. G5e Ab5h | "
                "G5q. F5e Eb5q D5q | D5w")
song(id="ember_and_ash", title="Ember and Ash", about="a power ballad", key="Cm", bpm=74, style="ballad",
     lead="fiddle", plan=[
         ("Intro", "Cm | Ab | Eb | Bb", None, 0),
         ("Verse", "Cm | Ab | Eb | Bb | Cm | Ab | Fm | G", EMBER_VERSE, 1, dict(lead="flute", shift=12)),
         ("Chorus", "Ab | Bb | Cm | Cm | Ab | Bb | Eb | G", EMBER_CHORUS, 2),
         ("Verse", "Cm | Ab | Eb | Bb | Cm | Ab | Fm | G", EMBER_VERSE, 1),
         ("Chorus", "Ab | Bb | Cm | Cm | Ab | Bb | Eb | G", EMBER_CHORUS, 3, dict(lead="recorder")),
         ("Outro", "Ab | Bb | Cm | Cm", None, 0),
     ])

# 8 ------------------------------------------------------------------------------------------
TAVERN_VERSE = ("C4q E4q G4q E4q | F4q A4q C5h | C5q B4q A4q G4q | G4h D4h | C4q E4q G4q C5q | "
                "A4q G4q F4q A4q | G4q F4q E4q D4q | C4h. rq")
TAVERN_CHORUS = ("A4q. A4e C5q A4q | G4q. E4e G4h | G4q. G4e B4q D5q | C5h. rq | A4q. A4e C5q F5q | "
                 "E5q. D5e C5h | D5q. C5e B4q G4q | C5h. rq")
song(id="tavern_lights", title="Tavern Lights", about="a pub singalong", key="C", bpm=128, style="tavern",
     lead="recorder", plan=[
         ("Intro", "C | G | Am | F", None, 1),
         ("Verse", "C | F | C | G | C | F | G | C", TAVERN_VERSE, 1, dict(shift=12)),
         ("Chorus", "F | C | G | Am | F | C | G | C", TAVERN_CHORUS, 2, dict(shift=12)),
         ("Verse", "C | F | C | G | C | F | G | C", TAVERN_VERSE, 2, dict(lead="fiddle")),
         ("Chorus", "F | C | G | Am | F | C | G | C", TAVERN_CHORUS, 3, dict(shift=12)),
         ("Chorus", "F | C | G | Am | F | C | G | C", TAVERN_CHORUS, 3, dict(lead="monks")),
         ("Outro", "F | G | C | C", None, 1),
     ])

# 9 ------------------------------------------------------------------------------------------
ROAD_VERSE = ("B4q. A4e G4q D4q | F#4q. G4e A4h | G4q. F#4e E4q B4q | C5h. rq | B4q. A4e G4q D4q | "
              "F#4q. G4e A4q D5q | C5q. B4e A4q G4q | A4h. rq")
ROAD_CHORUS = ("E5h D5q B4q | D5h. B4q | A4q. B4e A4q F#4q | G4h E4h | E5h D5q B4q | D5h. G5q | "
               "F#5q. E5e D5q A4q | B4h G4h")
song(id="the_long_road_home", title="The Long Road Home", about="a country road song", key="G", bpm=94,
     style="country", lead="fiddle", plan=[
         ("Intro", "G | D | Em | C", None, 0),
         ("Verse", "G | D | Em | C | G | D | C | D", ROAD_VERSE, 1),
         ("Chorus", "C | G | D | Em | C | G | D | G", ROAD_CHORUS, 2, dict(lead="recorder")),
         ("Verse", "G | D | Em | C | G | D | C | D", ROAD_VERSE, 1, dict(lead="recorder", shift=12)),
         ("Chorus", "C | G | D | Em | C | G | D | G", ROAD_CHORUS, 3, dict(lead="recorder")),
         ("Outro", "C | D | G | G", None, 0),
     ])

# 10 -----------------------------------------------------------------------------------------
PORTAL_VERSE = ("D4q D4e E4 F4q D4q | A4h. G4q | F4q F4e G4 A4q Bb4q | G4h. rq | D4q D4e E4 F4q D4q | "
                "A4h. C5q | Bb4q. A4e G4q F4q | E4h. rq")
PORTAL_CHORUS = ("F5h. G5q | G5h. E5q | F5q. E5e D5q A4q | D5w | F5h. G5q | G5h. C6q | A5q. G5e F5q E5q | "
                 "E5w")
song(id="sky_portals", title="Sky Portals", about="an epic march", key="Dm", bpm=112, style="epic",
     lead="shawm", plan=[
         ("Intro", "Dm | Bb | C | A", None, 0),
         ("Verse", "Dm | Dm | Bb | C | Dm | Dm | Gm | A", PORTAL_VERSE, 1),
         ("Chorus", "Bb | C | Dm | Dm | Bb | C | A | A", PORTAL_CHORUS, 3, dict(lead="recorder")),
         ("Interlude", "Bb | C | Dm | Dm | Bb | C | A | A", None, 2),
         ("Verse", "Dm | Dm | Bb | C | Dm | Dm | Gm | A", PORTAL_VERSE, 2, dict(lead="sackbut")),
         ("Chorus", "Bb | C | Dm | Dm | Bb | C | A | A", PORTAL_CHORUS, 3, dict(lead="recorder")),
         ("Outro", "Bb | C | Dm | Dm", None, 1),
     ])

# 11 -----------------------------------------------------------------------------------------
STAR_VERSE = ("G5q. F5e Eb5h | G5q. Bb5e C6h | Ab5q. G5e F5q Eb5q | F5w | G5q. F5e Eb5h | Eb5q. F5e G5h | "
              "C6q. Bb5e Ab5q G5q | F5w")
STAR_CHORUS = ("C6h Bb5h | G5h. Eb5q | F5q. G5e Ab5q C6q | Bb5w | C6h Bb5h | G5h. Bb5q | Ab5q. G5e F5q D5q | "
               "Eb5w")
song(id="starlit_courtyard", title="Starlit Courtyard", about="a starlit lullaby", key="Eb", bpm=76,
     style="dreamy", lead="flute", plan=[
         ("Intro", "Eb | Ab | Eb | Bb", None, 0),
         ("Verse", "Eb | Cm | Ab | Bb | Eb | Cm | Ab | Bb", STAR_VERSE, 1),
         ("Chorus", "Ab | Eb | Fm | Bb | Ab | Eb | Bb | Eb", STAR_CHORUS, 2),
         ("Verse", "Eb | Cm | Ab | Bb | Eb | Cm | Ab | Bb", STAR_VERSE, 1, dict(lead="psaltery")),
         ("Chorus", "Ab | Eb | Fm | Bb | Ab | Eb | Bb | Eb", STAR_CHORUS, 3),
         ("Outro", "Ab | Bb | Eb | Eb", None, 0),
     ])

# 12 -----------------------------------------------------------------------------------------
IVY_VERSE = ("E4q E4q E4q F#4q | G#4h E4h | F#4q F#4q F#4q G#4q | D#4h B3h | E4q E4q G#4q B4q | C#5h B4h | "
             "A4q G#4q F#4q E4q | F#4h. rq")
IVY_CHORUS = ("C#5q C#5q B4q A4q | B4h G#4h | B4q B4q A4q G#4q | G#4h F#4h | C#5q C#5q B4q A4q | B4h E5h | "
              "D#5q C#5q B4q F#4q | F#4h. rq")
song(id="iron_and_ivy", title="Iron and Ivy", about="a punk jig", key="E", bpm=168, style="punk",
     lead="shawm", plan=[
         ("Intro", "E | B | C#m | A", None, 3),
         ("Verse", "E | E | B | B | C#m | C#m | A | B", IVY_VERSE, 2),
         ("Chorus", "A | E | B | C#m | A | E | B | B", IVY_CHORUS, 3, dict(lead="fiddle")),
         ("Verse", "E | E | B | B | C#m | C#m | A | B", IVY_VERSE, 2),
         ("Chorus", "A | E | B | C#m | A | E | B | B", IVY_CHORUS, 3, dict(lead="fiddle")),
         ("Breakdown", "C#m | A | E | B | C#m | A | E | B", None, 1),
         ("Chorus", "A | E | B | C#m | A | E | B | B", IVY_CHORUS, 3, dict(lead="recorder", shift=12)),
         ("Outro", "A | B | E | E", None, 3),
     ])

# 13 -----------------------------------------------------------------------------------------
DRAW_VERSE = ("A4e A4 rq C5e B4 A4q | D5q. C5e A4h | B4e B4 rq D5e C5 B4q | E5q. D5e C5h | "
              "A4e A4 rq C5e B4 A4q | D5q. E5e F5h | E5q D5q C5q B4q | G#4h. rq")
DRAW_CHORUS = ("C5q C5e D5 E5q C5q | D5q D5e E5 F5h | E5q E5e D5 B4q G4q | A4h. rq | "
               "C5q C5e D5 E5q A5q | G5q. F5e D5h | E5q. D5e C5q B4q | G#4w")
DRAW_BREAK = "A5e E5 C5 E5 A5 E5 C5 E5 | A5e E5 C5 E5 A5 E5 C5 E5 | D5e F5 A5 F5 D5 F5 A5 F5 | E5e G#5 B5 G#5 E5h"
song(id="dancing_on_the_drawbridge", title="Dancing on the Drawbridge", about="a dance-floor romp", key="Am",
     bpm=118, style="dance", lead="recorder", plan=[
         ("Intro", "Am | Am | Dm | E", None, 2),
         ("Verse", "Am | Dm | G | C | Am | Dm | E | E", DRAW_VERSE, 2, dict(lead="fiddle")),
         ("Chorus", "F | G | Em | Am | F | G | E | E", DRAW_CHORUS, 3),
         ("Verse", "Am | Dm | G | C | Am | Dm | E | E", DRAW_VERSE, 2, dict(lead="shawm")),
         ("Chorus", "F | G | Em | Am | F | G | E | E", DRAW_CHORUS, 3),
         ("Break", "Am | Am | Dm | E", DRAW_BREAK, 2, dict(lead="dulcimer", alone=True)),
         ("Chorus", "F | G | Em | Am | F | G | E | E", DRAW_CHORUS, 3),
         ("Outro", "Am | Am | Dm | E", None, 1),
     ])

# 14 -----------------------------------------------------------------------------------------
FIELDS_VERSE = ("re C#5e C#5 B4 A4q E4q | F#4q. A4e A4h | re C#5e C#5 B4 A4q C#5q | B4h. rq | "
                "re C#5e C#5 B4 A4q E4q | F#4q. A4e D5h | C#5q B4q A4q G#4q | B4h. rq")
FIELDS_CHORUS = ("F#5q. E5e D5q A4q | C#5h. A4q | B4q. C#5e B4q G#4q | A4h F#4h | F#5q. E5e D5q F#5q | "
                 "E5h. C#5q | D5q. C#5e B4q G#4q | A4h. rq")
STYLES["lilt"] = dict(
    comp=[("arp", "B1232123", .5, 52), (("strum", ".U.U.U.U", 56)), ("strum", ".U.U.U.U", 60),
          ("strum", "xU.UxU.U", 62)],
    bass=["R.......", "R..R.F..", "R..R.F..", "R..R.FO."], bass_part="viol", bass_low=36,
    drums=[None, ["D...T...d...T..."], ["D...T.S.d...T.S."], ["D.S.T.S.d.S.T.SJ"]],
    harmony=3, harmony_part="flute", pad=[(2, "organ")])
song(id="golden_fields", title="Golden Fields", about="a sunny lilt", key="A", bpm=90, style="lilt",
     lead="recorder", plan=[
         ("Intro", "A | D | E | D", None, 1),
         ("Verse", "A | D | A | E | A | D | E | E", FIELDS_VERSE, 1),
         ("Chorus", "D | A | E | F#m | D | A | E | A", FIELDS_CHORUS, 2),
         ("Verse", "A | D | A | E | A | D | E | E", FIELDS_VERSE, 2, dict(lead="fiddle")),
         ("Chorus", "D | A | E | F#m | D | A | E | A", FIELDS_CHORUS, 3),
         ("Outro", "D | E | A | A", None, 1),
     ])

# 15 -----------------------------------------------------------------------------------------
CAVE_VERSE = ("F#4e A4 C#5q C#5q B4q | A4h. F#4q | E4e A4 C#5q C#5q E5q | E5h. rq | F#4e A4 C#5q C#5q B4q | "
              "A4h. D5q | C#5q. B4e A4q F#4q | E#4w")
CAVE_CHORUS = ("A5h. F#5q | G#5h. E5q | E5q. F#5e G#5q C#5q | F#5w | A5h. F#5q | G#5h. B5q | "
               "G#5q. F#5e E#5q G#5q | C#5w")
STYLES["mystic"] = dict(STYLES["dreamy"], bells=2, comp=[
    ("arp", "B1234321", .5, 48, "psaltery"), ("arp", "B1234321", .5, 52, "harp"),
    ("arp", "B1234321", .5, 56, "harp"), ("arp", "B1234321", .5, 58, "harp")],
    drums=[None, ["d.....t.....t..."], ["d.....t.d...t.j."], ["D.....t.D...t.jj"]])
song(id="the_crystal_cave", title="The Crystal Cave", about="a crystal-cave mystery", key="F#m", bpm=100,
     style="mystic", lead="flute", plan=[
         ("Intro", "F#m | D | F#m | C#", None, 0),
         ("Verse", "F#m | D | A | E | F#m | D | Bm | C#", CAVE_VERSE, 1, dict(shift=12)),
         ("Chorus", "D | E | C#m | F#m | D | E | C# | C#", CAVE_CHORUS, 2, dict(lead="recorder")),
         ("Verse", "F#m | D | A | E | F#m | D | Bm | C#", CAVE_VERSE, 1, dict(lead="dulcimer", shift=12)),
         ("Chorus", "D | E | C#m | F#m | D | E | C# | C#", CAVE_CHORUS, 3, dict(lead="recorder")),
         ("Outro", "F#m | D | F#m | C#", None, 0),
     ])

# 16 -----------------------------------------------------------------------------------------
CANDLE_VERSE = ("F4T D4t F4T Bb4t D5h | C5T Bb4t G4T Bb4t D5h | Eb5T D5t C5T Bb4t G4h | A4w | "
                "F4T D4t F4T Bb4t D5h | C5T Bb4t G4T Bb4t G5h | Eb5T D5t C5T Bb4t C5h | C5w")
CANDLE_CHORUS = ("G5T F5t Eb5T D5t Eb5h | F5T Eb5t D5T C5t C5h | D5T C5t Bb4T A4t F5h | D5w | "
                 "G5T F5t Eb5T D5t Eb5h | F5T G5t A5T Bb5t F5h | D5T C5t Bb4T A4t C5h | Bb4w")
STYLES["candlelit"] = dict(STYLES["dreamy"], comp=[
    ("arp", "B12312312312", 1 / 3, 48, "harp"), ("arp", "B12312312312", 1 / 3, 50, "harp"),
    ("arp", "B12312312312", 1 / 3, 54, "harp"), ("arp", "B12312312312", 1 / 3, 56, "harp")],
    bass=[None, "R.....", "R.....", "R..F.."],
    drums=[None, None, ["d.....d....."], ["D.....d..t.j"]])
song(id="two_candles", title="Two Candles", about="a candlelit love song", key="Bb", bpm=76,
     style="candlelit", lead="recorder", plan=[
         ("Intro", "Bb | Gm | Eb | F", None, 0),
         ("Verse", "Bb | Gm | Eb | F | Bb | Gm | Cm | F", CANDLE_VERSE, 1, dict(lead="flute", shift=12)),
         ("Chorus", "Eb | F | Dm | Gm | Eb | F | Bb | Bb", CANDLE_CHORUS, 2),
         ("Verse", "Bb | Gm | Eb | F | Bb | Gm | Cm | F", CANDLE_VERSE, 1, dict(lead="fiddle")),
         ("Chorus", "Eb | F | Dm | Gm | Eb | F | Bb | Bb", CANDLE_CHORUS, 3),
         ("Outro", "Eb | F | Bb | Bb", None, 0),
     ])

# 17 -----------------------------------------------------------------------------------------
SAILS_VERSE = ("A4q A4e A4 D5q A4q | G4q E4e E4 C4h | A4q A4e A4 D5q E5q | E5h C#5h | F5q E5e D5 A4q F4q | "
               "G4q E4e E4 C4q E4q | F4q. E4e D4q C#4q | D4h. rq")
SAILS_CHORUS = ("F5q. F5e F5q E5q | E5q. D5e C5h | D5q. E5e F5q D5q | E5h. rq | F5q. F5e A5q G5q | "
                "E5q. D5e C5h | D5q E5q C#5q E5q | D5h. rq")
song(id="steam_and_sails", title="Steam and Sails", about="a sea-shanty stomp", key="Dm", bpm=106,
     style="shanty", lead="fiddle", plan=[
         ("Intro", "Dm | C | Dm | A", None, 1),
         ("Verse", "Dm | C | Dm | A | Dm | C | Dm A | Dm", SAILS_VERSE, 1),
         ("Chorus", "F | C | Dm | A | F | C | Dm A | Dm", SAILS_CHORUS, 3, dict(lead="shawm")),
         ("Verse", "Dm | C | Dm | A | Dm | C | Dm A | Dm", SAILS_VERSE, 2, dict(lead="recorder", shift=12)),
         ("Chorus", "F | C | Dm | A | F | C | Dm A | Dm", SAILS_CHORUS, 3, dict(lead="shawm")),
         ("Chorus", "F | C | Dm | A | F | C | Dm A | Dm", SAILS_CHORUS, 3, dict(lead="monks")),
         ("Outro", "Dm | C | Dm | Dm", None, 1),
     ])

# 18 -----------------------------------------------------------------------------------------
HOLLOW_VERSE = ("B4q B4e B4 B4q G#4q | F#4h. D#4q | E4q E4e F#4 G#4q B4q | A4h. rq | B4q B4e B4 B4q G#4q | "
                "F#4h. B4q | C#5q. B4e A4q G#4q | F#4h. rq")
HOLLOW_PRE = "G#4q G#4e A4 B4q C#5q | C#5h. A4q | B4q B4e C#5 D#5q E5q | F#5h. D#5q"
HOLLOW_CHORUS = ("E5h. C#5q | D#5h. B4q | E5q. D#5e C#5q B4q | G#4h. rq | E5h. C#5q | D#5h. F#5q | "
                 "G#5q. F#5e E5q D#5q | E5h. rq")
song(id="heroes_of_the_hollow", title="Heroes of the Hollow", about="a stadium anthem", key="E", bpm=120,
     style="anthem", lead="recorder", plan=[
         ("Intro", "E | C#m | A | B", None, 2),
         ("Verse", "E | B | C#m | A | E | B | A | B", HOLLOW_VERSE, 1, dict(lead="fiddle")),
         ("Pre-chorus", "C#m | A | E | B", HOLLOW_PRE, 2, dict(lead="fiddle")),
         ("Chorus", "A | B | C#m | E | A | B | E | E", HOLLOW_CHORUS, 3),
         ("Verse", "E | B | C#m | A | E | B | A | B", HOLLOW_VERSE, 2, dict(shift=12)),
         ("Pre-chorus", "C#m | A | E | B", HOLLOW_PRE, 2),
         ("Chorus", "A | B | C#m | E | A | B | E | E", HOLLOW_CHORUS, 3, dict(lead="monks")),
         ("Outro", "A | B | E | E", None, 2),
     ])

# 19 -----------------------------------------------------------------------------------------
FEATHER_VERSE = ("G4e Bb4 D5q D5e C5 Bb4q | Bb4e C5 Eb5q D5h | D5e C5 Bb4q Bb4e A4 F4q | F4h. rq | "
                 "G4e Bb4 D5q D5e C5 Bb4q | Bb4e C5 Eb5q G5h | F5e Eb5 D5q C5e Bb4 A4q | A4h. rq")
FEATHER_CHORUS = ("G5q G5e F5 Eb5q D5q | F5q F5e Eb5 D5q C5q | D5q. C5e Bb4q A4q | G4h. rq | "
                  "G5q G5e F5 Eb5q G5q | A5q A5e G5 F5q C5q | D5w | F#5h. rq")
song(id="feather_and_flame", title="Feather and Flame", about="a bright, bouncing pop song", key="Gm", bpm=126,
     style="bright", lead="recorder", plan=[
         ("Intro", "Gm | Eb | Bb | F", None, 1),
         ("Verse", "Gm | Eb | Bb | F | Gm | Eb | Bb | D", FEATHER_VERSE, 1, dict(lead="fiddle")),
         ("Chorus", "Eb | F | Gm | Gm | Eb | F | D | D", FEATHER_CHORUS, 3),
         ("Verse", "Gm | Eb | Bb | F | Gm | Eb | Bb | D", FEATHER_VERSE, 2, dict(shift=12)),
         ("Chorus", "Eb | F | Gm | Gm | Eb | F | D | D", FEATHER_CHORUS, 3),
         ("Break", "Gm | Eb | Bb | F | Gm | Eb | Bb | D", FEATHER_VERSE, 1, dict(lead="bells", alone=True)),
         ("Outro", "Gm | Eb | Bb | F", None, 1),
     ])

# 20 -----------------------------------------------------------------------------------------
MOON_VERSE = ("A4q. G4e F4q C4q | E4q. F4e G4h | F4q. E4e D4q A4q | Bb4h. rq | A4q. G4e F4q C4q | "
              "E4q. F4e G4q C5q | D5q. C5e Bb4q A4q | G4h. rq")
MOON_CHORUS = ("D5h. C5q | C5h. A4q | G4q. A4e Bb4q C5q | A4h. rq | D5h. C5q | C5h. F5q | E5q. D5e C5q Bb4q | "
               "A4h. rq")
song(id="farewell_fair_moon", title="Farewell, Fair Moon", about="a farewell song", key="F", bpm=80,
     style="ballad", lead="recorder", plan=[
         ("Intro", "F | C | Dm | Bb", None, 0),
         ("Verse", "F | C | Dm | Bb | F | C | Bb | C", MOON_VERSE, 1, dict(shift=12)),
         ("Chorus", "Bb | F | C | Dm | Bb | F | C | F", MOON_CHORUS, 2, dict(shift=12)),
         ("Verse", "F | C | Dm | Bb | F | C | Bb | C", MOON_VERSE, 1, dict(lead="fiddle")),
         ("Chorus", "Bb | F | C | Dm | Bb | F | C | F", MOON_CHORUS, 3, dict(shift=12)),
         ("Outro", "Bb | C | F | F", None, 0),
     ])


def by_id(song_id):
    for s in SONGS:
        if s["id"] == song_id:
            return s
    return None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("ids", nargs="*", help="only these songs")
    ap.add_argument("--midi", action="store_true", help="write only the MIDI files")
    ap.add_argument("--list", action="store_true", help="list the songs")
    a = ap.parse_args()
    if a.list:
        for s in SONGS:
            b = build(s)
            beats = b.song.bars * b.song.beats_per_bar
            secs = beats * 60 / s["bpm"]
            print(f'{s["id"]:28} {s["title"]:28} {s["key"]:4} {s["bpm"]:4} bpm  {b.song.bars:3} bars  '
                  f'{int(secs // 60)}:{int(secs % 60):02d}  {s["about"]}')
        return
    for s in SONGS:
        if a.ids and s["id"] not in a.ids:
            continue
        print("--", s["title"], flush=True)
        build(s).save(s["id"], argv=["--midi"] if a.midi else [])


if __name__ == "__main__":
    main()
