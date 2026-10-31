"""The toolkit behind the game's medieval-style songs: write an arrangement
as parts of notes, save it as a MIDI file (for FL Studio, or any DAW), and
render it to a looping MP3 for the game.

A song is a script beside this one (dream_on.py) that builds a Song out of
Parts - a lute, a recorder, a harp, a hand drum - note by note, bar by bar,
and calls song.save(...). The MIDI it writes is a type-1 file with one
track per instrument, named, panned and set to the General MIDI sound
nearest a medieval one, so FL Studio opens it with every part on its own
channel ready to be given a better instrument.

The MP3 is rendered with FluidSynth and the FluidR3 General MIDI SoundFont
(MIT licensed), then made to loop without a seam: whatever is still ringing
when the song ends - the last lute notes, the reverb - is laid over its
start, the way it would ring into the next time round. The game's MP3
decoder trims LAME's delay and padding, so the loop is exactly as long as
the song.

Needs Python 3.9+, numpy, scipy, mido, FluidSynth with FluidR3_GM.sf2 and ffmpeg
(with libmp3lame) on the PATH. On Debian or Ubuntu:

    sudo apt install fluidsynth fluid-soundfont-gm ffmpeg
    pip install numpy scipy mido
"""

import os
import random
import shutil
import subprocess
import tempfile
import wave

import mido
import numpy as np
from scipy import signal

SR = 44100
PPQ = 480
DRUMS = 9               # MIDI channel 10, counted from 0

# Where FluidR3 lives on Debian/Ubuntu (fluid-soundfont-gm), and elsewhere.
SOUNDFONTS = ["/usr/share/sounds/sf2/FluidR3_GM.sf2", "/usr/share/soundfonts/FluidR3_GM.sf2",
              "/usr/share/sounds/sf2/default-GM.sf2"]

# General MIDI programs standing in for medieval instruments (counted from 0).
LUTE = 24               # Acoustic Guitar (nylon): the nearest GM has to a lute
HARP = 46               # Orchestral Harp
RECORDER = 74           # Recorder
FIDDLE = 110            # Fiddle: a medieval vielle
ORGAN = 20              # Reed Organ: a portative organ's drone
DULCIMER = 15           # Dulcimer

# The hand drum's strokes on the GM drum channel.
DUM = 64                # Low Conga: the frame drum's deep stroke, struck in the middle
TAK = 62                # Mute High Conga: its rim
BIG_DUM = 41            # Low Floor Tom: a tabor's boom on the downbeat
JINGLE = 54             # Tambourine


class Part:
    """One instrument's notes, in beats from the start of the song (a beat is a quarter note)."""

    def __init__(self, name, program, channel, volume=100, pan=64, reverb=60, chorus=0, seed=1):
        self.name, self.program, self.channel = name, program, channel
        self.volume, self.pan, self.reverb, self.chorus = volume, pan, reverb, chorus
        self.notes = []         # (start, length, pitch, velocity), in beats
        self.controls = []      # (time, controller, value), in beats
        self.rng = random.Random(seed)

    def note(self, at, length, pitch, velocity, swing=0.012, spread=5):
        """A note at beat `at`, nudged off the grid by up to `swing` beats and `spread` velocity, as hands are."""
        if swing:
            at = max(0.0, at + self.rng.uniform(-swing, swing))
        velocity = int(max(1, min(127, velocity + self.rng.randint(-spread, spread))))
        self.notes.append((at, length, int(pitch), velocity))

    def chord(self, at, length, pitches, velocity, roll=0.03, up=True):
        """A strummed or rolled chord: each string a little after the last (`roll` beats), up or down."""
        order = list(pitches) if up else list(reversed(pitches))
        for i, p in enumerate(order):
            self.note(at + i * roll, length - i * roll, p, velocity - i * 2, swing=0.006)

    def control(self, at, controller, value):
        self.controls.append((at, controller, int(max(0, min(127, value)))))

    def swell(self, at, length, start, peak, end, steps=12):
        """An expression (CC11) swell: from `start` up to `peak` halfway, and down to `end`."""
        for i in range(steps + 1):
            t = i / steps
            v = start + (peak - start) * (t * 2) if t <= 0.5 else peak + (end - peak) * ((t - 0.5) * 2)
            self.control(at + length * t, 11, v)


class Song:
    """A whole arrangement: its tempo, its length in bars, its parts and the sections marked in it."""

    def __init__(self, title, bpm, bars, beats_per_bar=4, key="Fm", credit=""):
        self.title, self.bpm, self.bars, self.beats_per_bar = title, bpm, bars, beats_per_bar
        self.key, self.credit = key, credit
        self.parts = []
        self.markers = []       # (beat, name)

    @property
    def beats(self):
        return self.bars * self.beats_per_bar

    @property
    def seconds(self):
        return self.beats * 60.0 / self.bpm

    def part(self, *args, **kwargs):
        p = Part(*args, **kwargs)
        self.parts.append(p)
        return p

    def mark(self, bar, name):
        self.markers.append(((bar - 1) * self.beats_per_bar, name))

    def at(self, bar, beat=0.0):
        """Bar `bar` (counted from 1), beat `beat` (from 0), in beats from the start."""
        return (bar - 1) * self.beats_per_bar + beat

    # --- the MIDI file --------------------------------------------------------------------

    def midi(self, tail_seconds=0.0):
        """The song as a type-1 MIDI file: a conductor track, then one track per part.

        Every note ends by the end of the last bar, so the file loops cleanly in
        a DAW; `tail_seconds` adds silence after it (rendering lets the last
        notes and the reverb ring out into it).
        """
        f = mido.MidiFile(type=1, ticks_per_beat=PPQ)
        end = self.beats * PPQ
        conductor = [(0, mido.MetaMessage("track_name", name=self.title, time=0)),
                     (0, mido.MetaMessage("set_tempo", tempo=mido.bpm2tempo(self.bpm), time=0)),
                     (0, mido.MetaMessage("time_signature", numerator=self.beats_per_bar, denominator=4,
                                          time=0)),
                     (0, mido.MetaMessage("key_signature", key=self.key, time=0))]
        if self.credit:
            conductor.append((0, mido.MetaMessage("text", text=self.credit, time=0)))
        for beat, name in self.markers:
            conductor.append((round(beat * PPQ), mido.MetaMessage("marker", text=name, time=0)))
        f.tracks.append(track(conductor, end + round(tail_seconds * self.bpm / 60.0 * PPQ)))

        for p in self.parts:
            ch = p.channel
            events = [(0, mido.MetaMessage("track_name", name=p.name, time=0))]
            if ch != DRUMS:
                events.append((0, mido.Message("program_change", channel=ch, program=p.program, time=0)))
            for cc, v in ((7, p.volume), (10, p.pan), (91, p.reverb), (93, p.chorus), (11, 127)):
                events.append((0, mido.Message("control_change", channel=ch, control=cc, value=v, time=0)))
            for at, cc, v in p.controls:
                events.append((min(end, round(at * PPQ)), mido.Message("control_change", channel=ch, control=cc,
                                                                       value=v, time=0)))
            for on, off, pitch, vel in notes(p.notes, end):
                events.append((on, mido.Message("note_on", channel=ch, note=pitch, velocity=vel, time=0)))
                events.append((off, mido.Message("note_off", channel=ch, note=pitch, velocity=0, time=0)))
            f.tracks.append(track(events, end))
        return f

    # --- the MP3 --------------------------------------------------------------------------

    def render(self, tail_seconds=6.0, gain=0.5):
        """The song through FluidSynth as float stereo, with `tail_seconds` of ring-out after it."""
        sf2 = next((s for s in SOUNDFONTS if os.path.isfile(s)), None)
        if sf2 is None:
            raise SystemExit("FluidR3_GM.sf2 not found (apt install fluid-soundfont-gm): looked in "
                             + ", ".join(SOUNDFONTS))
        if shutil.which("fluidsynth") is None:
            raise SystemExit("fluidsynth is needed to render the MP3 (apt install fluidsynth)")
        with tempfile.TemporaryDirectory() as tmp:
            mid, raw = os.path.join(tmp, "song.mid"), os.path.join(tmp, "song.raw")
            self.midi(tail_seconds).save(mid)
            subprocess.run(["fluidsynth", "-ni", "-q", "-g", str(gain), "-r", str(SR), "-O", "float",
                            "-T", "raw", "-F", raw,
                            "-o", "synth.reverb.active=1", "-o", "synth.reverb.room-size=0.72",
                            "-o", "synth.reverb.damp=0.35", "-o", "synth.reverb.width=0.9",
                            "-o", "synth.reverb.level=0.75", "-o", "synth.chorus.active=1",
                            "-o", "synth.chorus.level=1.2", "-o", "synth.chorus.depth=6",
                            sf2, mid], check=True)
            x = np.fromfile(raw, dtype="<f4").reshape(-1, 2).astype(np.float64)
        return x

    def loop(self, x):
        """Fold everything after the song's end back over its start, so it loops without a seam."""
        n = int(round(self.seconds * SR))
        if len(x) < n:
            x = np.vstack([x, np.zeros((n - len(x), 2))])
        out = x[:n].copy()
        tail = x[n:]
        while len(tail):
            k = min(n, len(tail))
            out[:k] += tail[:k]
            tail = tail[k:]
        return out

    def save(self, mid_path, mp3_path=None, loudness=-21.0, peak=-3.0, wav_path=None):
        """Write the MIDI file, and the looping MP3 rendered from it, at about `loudness` LUFS."""
        os.makedirs(os.path.dirname(mid_path), exist_ok=True)
        self.midi().save(mid_path)
        print(f"wrote {mid_path} ({self.bars} bars, {self.seconds:.1f} s at {self.bpm} bpm)")
        if mp3_path is None:
            return
        x = self.loop(self.render())
        x = master(x, loudness, peak)
        write_mp3(x, mp3_path)
        print(f"wrote {mp3_path} ({len(x) / SR:.2f} s, loops)")
        if wav_path:
            write_wav(x, wav_path)
            print(f"wrote {wav_path}")


def notes(part_notes, end):
    """A part's notes as (on tick, off tick, pitch, velocity), each within the song and none overlapping itself.

    A key struck again while it still sounds (a lute string ringing on as it
    is picked again, a drone held into the next section) ends just as it is
    struck again: in MIDI a note's off would otherwise silence the new one.
    """
    out = []
    for at, length, pitch, vel in sorted(part_notes):
        on = round(at * PPQ)
        off = min(end, round((at + length) * PPQ))
        if on < end and off > on:
            out.append([on, off, pitch, vel])
    held = {}
    for n in out:
        last = held.get(n[2])
        if last is not None and last[1] > n[0]:
            last[1] = n[0]
        held[n[2]] = n
    return [tuple(n) for n in out if n[1] > n[0]]


def track(events, end):
    """A mido track from (absolute tick, message) pairs: offs before ons at a tick, ended at `end`."""
    def order(e):
        tick, msg = e
        rank = 0 if msg.is_meta else 1 if msg.type in ("program_change", "control_change") else \
            2 if msg.type == "note_off" else 3
        return tick, rank
    t = mido.MidiTrack()
    now = 0
    for tick, msg in sorted(events, key=order):
        t.append(msg.copy(time=tick - now))
        now = tick
    t.append(mido.MetaMessage("end_of_track", time=max(0, end - now)))
    return t


# --- mastering ----------------------------------------------------------------------------


def loudness_of(x):
    """Integrated loudness in LUFS (ITU-R BS.1770: K-weighting, 400 ms blocks, the two gates)."""
    k = k_weight(x)
    block, hop = int(0.4 * SR), int(0.1 * SR)
    power = np.array([np.mean(k[i:i + block] ** 2, axis=0).sum() for i in range(0, len(k) - block, hop)])
    lufs = -0.691 + 10 * np.log10(np.maximum(power, 1e-12))
    gated = power[lufs > -70]
    rel = -0.691 + 10 * np.log10(np.mean(gated)) - 10
    gated = power[(lufs > -70) & (lufs > rel)]
    return -0.691 + 10 * np.log10(np.mean(gated))


def k_weight(x):
    """BS.1770's K-weighting at 44.1 kHz: a high shelf, then a high-pass."""
    array = np.array
    shelf_b, shelf_a = array([1.53090959966, -2.65116903469, 1.16916686598]), \
        array([1.0, -1.66375011340, 0.71265752775])
    hp_b, hp_a = array([1.0, -2.0, 1.0]), array([1.0, -1.98916967363, 0.98919159781])
    return biquad(biquad(x, shelf_b, shelf_a), hp_b, hp_a)


def biquad(x, b, a):
    """A second-order IIR filter down each channel."""
    return signal.lfilter(b, a, x, axis=0)


def master(x, loudness, peak):
    """Bring the mix to `loudness` LUFS, then soft-limit anything over `peak` dBFS."""
    x = x - x.mean(axis=0)
    x *= 10 ** ((loudness - loudness_of(x)) / 20)
    ceiling = 10 ** (peak / 20)
    over = np.abs(x) > ceiling * 0.8
    if over.any():          # a gentle knee rather than a hard clip
        knee = ceiling * 0.8
        s = np.sign(x[over])
        a = np.abs(x[over]) - knee
        x[over] = s * (knee + (ceiling - knee) * np.tanh(a / (ceiling - knee)))
    return x


def write_wav(x, path):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def write_mp3(x, path):
    """Stereo 44.1 kHz, LAME's VBR quality 4, with the encoder's delay and padding in its tag (for a gapless loop)."""
    if shutil.which("ffmpeg") is None:
        raise SystemExit("ffmpeg is needed (with libmp3lame) to write MP3s")
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "song.wav")
        write_wav(x, wav)
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-map_metadata", "-1",
                        "-fflags", "+bitexact", "-flags:a", "+bitexact", "-id3v2_version", "0",
                        "-codec:a", "libmp3lame", "-q:a", "4", path], check=True)
