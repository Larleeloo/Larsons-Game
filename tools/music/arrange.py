"""Shorthand for writing the covers: chord symbols, tunes as text, and the
band's usual ways of playing (picking, strumming, a bass line, the hand
drum's patterns), on top of medieval.py.

A song script (california_dreamin.py, ...) makes a Band, writes each section
as chords and a tune, and plays them in its own order with whichever
instruments it likes:

    band = Band("California Dreamin' (medieval)", bpm=112, key="Am", credit=...)
    verse = band.chords(5, "Am G | F G | Esus4 | E")       # bars 5-8
    band.arp(verse, 64)                                     # the lute picks through them
    band.lead(5, "rs E4s A4e A4 G4 A4 C5q.", 84)            # the recorder sings
    band.drums(5, 4, "D...t.t.D.t.t...", vel=.8)            # the hand drum
    band.save("california_dreamin")

Tunes
    Notes are a name, an octave (C4 is middle C) and a length: w h q e s
    (whole, half, quarter, eighth, sixteenth), t and T (a third and two
    thirds of a beat, for triplets), x (a sixth), a dot for half as long
    again, joined with + for a held note (C5h+e). A note without a length
    takes the last one's. r is a rest; a ! after a note accents it, a ~
    slurs it into the next. | checks the tune is on a bar line there, and
    @3 jumps to beat 3 of the phrase.

Chords
    Am, G, F#m7, Bb, Esus4, Dadd9, A7sus4, C/E, Fmaj7, G5, Bdim ... each
    voiced as a bass and three notes above it, moving as little as it can
    from the chord before. Bars are split by |; chords in a bar share it
    equally unless they say how long (G:3 D:1); % is the bar before again,
    - holds the chord before.
"""

import argparse
import os
import re

import medieval as m

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")

LETTERS = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
UNITS = {"w": 4.0, "h": 2.0, "q": 1.0, "e": 0.5, "s": 0.25, "t": 1 / 3, "T": 2 / 3, "x": 1 / 6}

QUALITIES = {
    "": (0, 4, 7), "m": (0, 3, 7), "5": (0, 7, 12), "sus4": (0, 5, 7), "sus2": (0, 2, 7),
    "dim": (0, 3, 6), "aug": (0, 4, 8), "7": (0, 4, 7, 10), "m7": (0, 3, 7, 10),
    "maj7": (0, 4, 7, 11), "add9": (0, 4, 7, 2), "madd9": (0, 3, 7, 2), "6": (0, 4, 7, 9),
    "m6": (0, 3, 7, 9), "7sus4": (0, 5, 7, 10), "dim7": (0, 3, 6, 9), "9": (0, 4, 10, 2),
    "m9": (0, 3, 10, 2), "maj9": (0, 4, 11, 2), "m7b5": (0, 3, 6, 10),
}
CHORD = re.compile(r"([A-G][#b]?)(" + "|".join(sorted((q for q in QUALITIES if q), key=len, reverse=True))
                   + r")?(?:/([A-G][#b]?))?")
NOTE = re.compile(r"(r|[A-G][#b]?-?\d)((?:[whqestTx]\.?)(?:\+[whqestTx]\.?)*|:[0-9.]+)?([!~]*)")

SCALES = {"": (0, 2, 4, 5, 7, 9, 11), "m": (0, 2, 3, 5, 7, 8, 10)}


def pc(name):
    """A note name's pitch class: C 0, C# 1, Db 1 ... B 11."""
    return (LETTERS[name[0]] + name.count("#") - name[1:].count("b")) % 12


def pitch(name):
    """'C4' is middle C, 60; 'Bb3' 58; 'F#5' 78."""
    mt = re.fullmatch(r"([A-G])([#b]?)(-?\d)", name)
    if not mt:
        raise ValueError(f"not a note: {name!r}")
    letter, acc, octave = mt.groups()
    return 12 * (int(octave) + 1) + LETTERS[letter] + (1 if acc == "#" else -1 if acc == "b" else 0)


def length(text):
    """'q.' 1.5, 'h+e' 2.5, ':0.75' 0.75 (beats)."""
    if text.startswith(":"):
        return float(text[1:])
    total = 0.0
    for piece in text.split("+"):
        total += UNITS[piece[0]] * (1.5 if piece.endswith(".") else 1)
    return total


SHARPS = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]


def transpose(text, semitones):
    """A chord progression moved up `semitones` (a key change): "Em C | D G" up 2 is "F#m D | E A"."""
    def move(mt):
        root, rest, bass = mt.group(1), mt.group(2) or "", mt.group(3)
        out = SHARPS[(pc(root) + semitones) % 12] + rest
        return out + ("/" + SHARPS[(pc(bass) + semitones) % 12] if bass else "")
    return re.sub(r"(?<![A-Za-z#])([A-G][#b]?)((?:maj|m|sus|add|dim|aug|[0-9b])*)(?:/([A-G][#b]?))?", move, text)


def tune(text, beats_per_bar=4):
    """A tune as [(beat from its start, length, pitch or None for a rest, accent, slur)]."""
    out, at, last, bar = [], 0.0, 1.0, 1
    for token in text.split():
        if token == "|":
            if abs(at / beats_per_bar - round(at / beats_per_bar)) > 1e-6:
                raise ValueError(f"bar {bar} of the tune ends {at % beats_per_bar:g} beats off the bar line: "
                                 f"{text!r}")
            bar += 1
            continue
        if token.startswith("@"):
            at = float(token[1:])
            continue
        mt = NOTE.fullmatch(token)
        if not mt:
            raise ValueError(f"not a note: {token!r} in {text!r}")
        name, dur, marks = mt.groups()
        if dur:
            last = length(dur)
        if name != "r":
            out.append((at, last, pitch(name), "!" in marks, "~" in marks))
        at += last
    return out


def tune_length(text, beats_per_bar=4):
    """How many beats a tune lasts, rests included."""
    at, last = 0.0, 1.0
    for token in text.split():
        if token == "|" or token.startswith("@"):
            if token.startswith("@"):
                at = float(token[1:])
            continue
        name, dur, _ = NOTE.fullmatch(token).groups()
        if dur:
            last = length(dur)
        at += last
    return at


class Chord:
    """A chord symbol: its root, its notes (pitch classes, root first) and the note in the bass."""

    def __init__(self, symbol):
        mt = CHORD.fullmatch(symbol)
        if not mt:
            raise ValueError(f"not a chord: {symbol!r}")
        root, quality, bass = mt.groups()
        self.symbol = symbol
        self.root = pc(root)
        self.intervals = QUALITIES[quality or ""]
        self.tones = [(self.root + i) % 12 for i in self.intervals]
        self.bass_pc = pc(bass) if bass else self.root
        self.minor = (quality or "").startswith("m") and not (quality or "").startswith("maj")

    def bass(self, low=43):
        """The bass note at or above `low` (G2 by default), within the octave."""
        return low + (self.bass_pc - low) % 12

    def fifth(self, low=43):
        """The chord's fifth (or what stands in for it) above its bass."""
        f = (self.root + (6 if 6 in self.intervals else 8 if 8 in self.intervals else 7)) % 12
        b = self.bass(low)
        return b + (f - b) % 12

    def __repr__(self):
        return self.symbol


def voicing(chord, near=(60, 64, 67), low=55, high=76):
    """Three notes of `chord` between `low` and `high`, as close as can be to the three in `near`."""
    tones = list(chord.tones)
    if len(set(tones)) > 3:                     # four notes: keep the colour, drop the fifth
        fifth = (chord.root + 7) % 12
        tones = [t for t in tones if t != fifth] if fifth in tones else tones[:3]
    tones = list(dict.fromkeys(tones))[:3]
    if len(tones) == 2:                          # a power chord: the root twice
        tones.append(tones[0])
    best, cost = None, None
    candidates = [p for p in range(low, high + 1) if p % 12 in tones]
    for a in candidates:
        for b in candidates:
            if b <= a:
                continue
            for c in candidates:
                if c <= b or c - a > 14:
                    continue
                v = (a, b, c)
                if {p % 12 for p in v} != set(tones):
                    continue
                k = sum(abs(x - y) for x, y in zip(v, near))
                if cost is None or k < cost:
                    best, cost = v, k
    return best


class Span:
    """A chord sounding from `start` for `length` beats, with its voicing."""

    def __init__(self, start, length, chord, voices):
        self.start, self.length, self.chord, self.voices = start, length, chord, voices

    @property
    def end(self):
        return self.start + self.length

    def __repr__(self):
        return f"{self.chord}@{self.start:g}+{self.length:g}"


# The band: name on the track, General MIDI program, channel, mixer settings.
PARTS = {
    "lute": ("Lute", m.LUTE, 0, dict(volume=104, pan=46, reverb=55)),
    "harp": ("Harp", m.HARP, 1, dict(volume=127, pan=86, reverb=72)),
    "recorder": ("Recorder", m.RECORDER, 2, dict(volume=80, pan=62, reverb=66)),
    "fiddle": ("Fiddle", m.FIDDLE, 3, dict(volume=96, pan=34, reverb=72)),
    "organ": ("Portative organ (drone)", m.ORGAN, 4, dict(volume=64, pan=74, reverb=80)),
    "dulcimer": ("Hammered dulcimer", m.DULCIMER, 5, dict(volume=120, pan=80, reverb=60)),
    "shawm": ("Shawm", 111, 6, dict(volume=100, pan=56, reverb=62)),
    "bagpipe": ("Bagpipe", 109, 7, dict(volume=60, pan=70, reverb=66)),
    "monks": ("Monks (choir)", 52, 8, dict(volume=112, pan=64, reverb=92)),
    "drum": ("Hand drum", 0, m.DRUMS, dict(volume=112, pan=72, reverb=38)),
    "viol": ("Bass viol", 42, 10, dict(volume=84, pan=52, reverb=66)),
    "flute": ("Wooden flute", 73, 11, dict(volume=78, pan=66, reverb=70)),
    "bells": ("Bells", 14, 12, dict(volume=58, pan=60, reverb=96)),
    "psaltery": ("Psaltery", 107, 13, dict(volume=120, pan=40, reverb=64)),
    "sackbut": ("Sackbut", 57, 14, dict(volume=70, pan=48, reverb=70)),
    "harpsichord": ("Harpsichord", 6, 15, dict(volume=110, pan=70, reverb=58)),
}

# The hand drum's strokes by letter, for drums(): (GM drum note, velocity).
STROKES = {
    "B": (m.BIG_DUM, 86), "b": (m.BIG_DUM, 66),          # the tabor's boom
    "D": (m.DUM, 92), "d": (m.DUM, 68),                  # the frame drum's deep stroke
    "T": (m.TAK, 66), "t": (m.TAK, 50),                  # its rim
    "K": (60, 70), "k": (60, 52),                        # a high goblet drum (bongo)
    "J": (m.JINGLE, 54), "j": (m.JINGLE, 40),            # the riq's jingles
    "I": (81, 52), "i": (80, 44),                        # a triangle, open and damped
    "W": (76, 64), "w": (77, 58),                        # wood blocks
    "S": (70, 40),                                       # a shaker
    "G": (56, 62), "g": (56, 46),                        # a bell (cowbell)
    "C": (39, 56),                                       # hands clapped
}


class Band:
    """A Song with the band's usual ways of playing, and the chords each bar has."""

    def __init__(self, title, bpm, key, credit, beats_per_bar=4):
        self.key = key
        self.tonic = pc(key.rstrip("m"))
        self.scale = [(self.tonic + s) % 12 for s in SCALES["m" if key.endswith("m") else ""]]
        self.song = m.Song(title, bpm=bpm, bars=1, beats_per_bar=beats_per_bar, key=key, credit=credit)
        self.parts = {}
        self.spans = []
        self.last_voicing = (60, 64, 67)
        self.seed = sum(map(ord, title)) % 1000

    # --- the parts -------------------------------------------------------------------------

    def part(self, name):
        """The band's `name` part ('lute', 'recorder', ...), made the first time it plays."""
        if name not in self.parts:
            track, program, channel, mix = PARTS[name]
            self.parts[name] = self.song.part(track, program, channel, seed=self.seed + len(self.parts) * 7, **mix)
        return self.parts[name]

    def mix(self, name, **settings):
        """Change a part's volume, pan, reverb or chorus from the band's usual."""
        p = self.part(name)
        for k, v in settings.items():
            setattr(p, k, v)
        return p

    def at(self, bar, beat=0.0):
        return self.song.at(bar, beat)

    def mark(self, bar, name):
        self.song.mark(bar, name)

    def bars(self, n):
        """The song is `n` bars long."""
        self.song.bars = n

    # --- chords ----------------------------------------------------------------------------

    def chords(self, bar, text):
        """The chords from `bar` on, as written (see the module's notes); returns their Spans."""
        bpb = self.song.beats_per_bar
        out, prev_bar = [], None
        for i, cell in enumerate(text.split("|")):
            tokens = cell.split()
            if not tokens:
                continue
            if tokens == ["%"]:
                tokens = prev_bar
            prev_bar = tokens
            fixed = [length(":" + t.split(":")[1]) if ":" in t else None for t in tokens]
            free = sum(1 for f in fixed if f is None)
            share = (bpb - sum(f for f in fixed if f)) / free if free else 0
            beat = 0.0
            for t, f in zip(tokens, fixed):
                dur = f if f is not None else share
                start = self.at(bar + i, beat)
                beat += dur
                if t.split(":")[0] == "-":
                    if out:
                        out[-1].length += dur
                    elif self.spans:
                        self.spans[-1].length += dur
                    continue
                ch = Chord(t.split(":")[0])
                v = voicing(ch, self.last_voicing)
                self.last_voicing = v
                out.append(Span(start, dur, ch, v))
        self.spans.extend(out)
        return out

    def chord_at(self, beat):
        for s in reversed(self.spans):
            if s.start - 1e-6 <= beat < s.end - 1e-6:
                return s
        return None

    # --- ways of playing chords -------------------------------------------------------------

    def arp(self, spans, vel, pattern="B1232123", step=0.5, part="lute", ring=1.1, low=43, shift=0):
        """Picked through each chord: B the bass, b the bass an octave up, 1 2 3 the notes above, 4 the
        lowest of them an octave up, . nothing; the pattern starts again at each chord."""
        p = self.part(part)
        for s in spans:
            bass = s.chord.bass(low) + shift
            k, t = 0, s.start
            while t < s.end - 1e-6:
                c = pattern[k % len(pattern)]
                if c != ".":
                    if c in "Bb":
                        note, ln, v = bass + (12 if c == "b" else 0), s.end - t, vel + 8
                    else:
                        note = (s.voices + (s.voices[0] + 12,))["1234".index(c)] + shift
                        ln, v = ring, vel + (4 if c == "3" else 0)
                    p.note(t, min(ln, s.end - t + ring * .5), note, v)
                k += 1
                t += step

    def strum(self, spans, vel, rhythm="D.DU.UDU", part="lute", low=43, shift=0):
        """Strummed on a grid across each bar (the rhythm's length is one bar): D down, U up (lighter, the
        upper strings), d a soft down, x a muted chuck, . nothing."""
        p = self.part(part)
        bpb = self.song.beats_per_bar
        step = bpb / len(rhythm)
        for s in spans:
            bar_start = (s.start // bpb) * bpb
            t = bar_start
            hits = []
            while t < s.end - 1e-6:
                k = int(round((t - bar_start) / step)) % len(rhythm)
                if t >= s.start - 1e-6 and rhythm[k] != ".":
                    hits.append((t, rhythm[k]))
                t += step
            for j, (t, c) in enumerate(hits):
                nxt = hits[j + 1][0] if j + 1 < len(hits) else s.end
                full = [s.chord.bass(low) + shift, *(v + shift for v in s.voices), s.voices[0] + 12 + shift]
                if c == "D":
                    p.chord(t, nxt - t + .05, full, vel, roll=.035, up=True)
                elif c == "d":
                    p.chord(t, nxt - t + .05, full, vel - 12, roll=.04, up=True)
                elif c == "U":
                    p.chord(t, nxt - t + .05, full[1:], vel - 16, roll=.025, up=False)
                elif c == "x":
                    p.chord(t, .08, full[:4], max(20, vel - 30), roll=.012, up=True)

    def bass(self, spans, vel, rhythm="R.......", part="harp", low=31, shift=0):
        """A bass line on a grid across each bar: R the chord's bass, F its fifth, O the bass an octave up,
        L the bass an octave down, . the note before rings on."""
        p = self.part(part)
        bpb = self.song.beats_per_bar
        step = bpb / len(rhythm)
        for s in spans:
            bar_start = (s.start // bpb) * bpb
            t, hits = bar_start, []
            while t < s.end - 1e-6:
                k = int(round((t - bar_start) / step)) % len(rhythm)
                if t >= s.start - 1e-6 and rhythm[k] != ".":
                    hits.append((t, rhythm[k]))
                t += step
            if not hits or hits[0][0] > s.start + 1e-6:     # a chord change between hits: the bass moves with it
                hits.insert(0, (s.start, "R"))
            for j, (t, c) in enumerate(hits):
                nxt = hits[j + 1][0] if j + 1 < len(hits) else s.end
                b = s.chord.bass(low) + shift
                note = {"R": b, "F": s.chord.fifth(low) + shift, "O": b + 12, "L": b - 12}[c]
                p.note(t, nxt - t + .02, note, vel + (6 if t == s.start else 0))

    def roll(self, spans, vel, part="harp", octave=12, at=0.0, roll=.06):
        """Each chord rolled once, `at` beats into it (the harp's rolled chords)."""
        p = self.part(part)
        for s in spans:
            if at < s.length:
                p.chord(s.start + at, s.length - at, [v + octave for v in s.voices], vel, roll=roll)

    def pad(self, spans, vel, part="organ", voices=2, octave=-12, swell=(84, 108, 96)):
        """Each chord held: its top `voices` notes, `octave` from where the lute has them, swelling."""
        p = self.part(part)
        for s in spans:
            for v in s.voices[-voices:]:
                p.note(s.start, s.length - .02, v + octave, vel, swing=0)
            if swell:
                p.swell(s.start, s.length, *swell, steps=6)

    def drone(self, bar, bars, pitches, vel, part="organ", swell=(80, 105, 96)):
        """Notes held under everything for `bars` bars from `bar` (a drone)."""
        p = self.part(part)
        start, ln = self.at(bar), bars * self.song.beats_per_bar
        for n in pitches:
            p.note(start, ln, pitch(n) if isinstance(n, str) else n, vel, swing=0)
        if swell:
            p.swell(start, ln, *swell)

    def hold(self, spans, vel, part="fiddle", low=62, high=74, swell=(88, 118, 98)):
        """A slow line of held notes, one per chord: the note of each nearest the one before."""
        p = self.part(part)
        prev = None
        for s in spans:
            cands = [n for n in range(low, high + 1) if n % 12 in s.chord.tones]
            n = min(cands, key=lambda c: (abs(c - prev) if prev is not None else abs(c - (low + high) // 2),
                                         -c))
            p.note(s.start, s.length - .1, n, vel)
            if swell:
                p.swell(s.start, s.length, *swell, steps=6)
            prev = n

    # --- tunes -----------------------------------------------------------------------------

    def lead(self, bar, text, vel, part="recorder", legato=.92, shift=0, beat=0.0, swell=None):
        """A tune (see the module's notes) from `bar` (and `beat`), on `part`."""
        p = self.part(part)
        start = self.at(bar, beat)
        for at, ln, note, accent, slur in tune(text, self.song.beats_per_bar):
            held = ln if slur else ln * legato if ln < 2 else ln - .1
            p.note(start + at, held, note + shift, vel + (12 if accent else 0))
            if swell and ln >= 2:
                p.swell(start + at, ln, *swell, steps=6)
        return start + tune_length(text, self.song.beats_per_bar)

    def harmony(self, bar, text, vel, part="fiddle", legato=.95, shift=0, beat=0.0, below=True):
        """A second voice to a tune: under each note the nearest of the chord's notes a third (or a sixth,
        or a fourth) away - parallel lines, as a medieval band would."""
        p = self.part(part)
        start = self.at(bar, beat)
        for at, ln, note, accent, slur in tune(text, self.song.beats_per_bar):
            s = self.chord_at(start + at)
            h = self.second(note + shift, s.chord if s else None, below)
            if h is None:
                continue
            p.note(start + at, ln if slur else ln * legato if ln < 2 else ln - .1, h,
                   vel + (8 if accent else 0))

    def second(self, note, chord, below=True):
        """The note a third (else a sixth, else a fourth) from `note`, from the chord if it can be."""
        sign = -1 if below else 1
        prefer = (3, 4, 8, 9, 5)
        if chord:
            for d in prefer:
                h = note + sign * d
                if h % 12 in chord.tones:
                    return h
        # a passing note: a third in the key's scale
        for d in (3, 4):
            h = note + sign * d
            if h % 12 in self.scale:
                return h
        return None

    def drums(self, bar, bars, *patterns, vel=1.0, fill=None, part="drum"):
        """The hand drum for `bars` bars from `bar`: each pattern is one bar on a grid (16 letters, sixteenths;
        12, triplet eighths; 8, eighths), the patterns taking turns bar by bar; `fill` replaces the last
        bar. The letters are the strokes in STROKES; . is nothing."""
        p = self.part(part)
        bpb = self.song.beats_per_bar
        for i in range(bars):
            pat = fill if fill and i == bars - 1 else patterns[i % len(patterns)]
            step = bpb / len(pat)
            for k, c in enumerate(pat):
                if c in STROKES:
                    note, v = STROKES[c]
                    p.note(self.at(bar + i, k * step), min(.5, step), note, int(v * vel))

    # --- out -------------------------------------------------------------------------------

    def save(self, song_id, argv=None, doc=None):
        """Write music/<song_id>_medieval.mid and assets/sounds/music/<song_id>.mp3 (see main())."""
        ap = argparse.ArgumentParser(description=doc, formatter_class=argparse.RawDescriptionHelpFormatter)
        ap.add_argument("--midi", action="store_true", help="write only the MIDI file")
        ap.add_argument("--wav", help="also write the looping mix as a WAV here, to listen to")
        a = ap.parse_args(argv)
        mid, mp3 = paths(song_id)
        self.song.save(mid, None if a.midi else mp3, wav_path=a.wav)


def paths(song_id):
    """Where a song's MIDI and MP3 go."""
    return (os.path.join(ROOT, "music", song_id + "_medieval.mid"),
            os.path.join(ROOT, "assets", "sounds", "music", song_id + ".mp3"))
