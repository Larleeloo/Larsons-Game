#!/usr/bin/env python3
"""The game's placeholder sounds, made from nothing but arithmetic.

Every sound the Sounds menu lists (audio/SoundKeys.java, the "listed" rows)
gets a stand-in here: footsteps from filtered noise, swings from swept
noise, metal and crystal from ringing partials, bowstrings plucked
(Karplus-Strong), voices from a buzzing source through vowel formants,
loops from noise shaped in the frequency domain so they repeat without a
seam. All of it is original - no recordings, no sample packs - and seeded,
so running it again makes the same files.

They are placeholders: replace any of them with a real recording of the
same name in assets/sounds/, or pick your own file in the game (Pause →
Sounds).

    python3 tools/sounds/make_placeholders.py            # every sound
    python3 tools/sounds/make_placeholders.py ui/click   # just these
    python3 tools/sounds/make_placeholders.py --list     # what it makes

Needs Python 3.9+, numpy, scipy, and ffmpeg (with libmp3lame) on the PATH.
Writes mono 44.1 kHz MP3s (LAME's VBR quality 4) carrying the encoder's
delay and padding, which the game's decoder cuts off so every sound starts
on time and every loop loops without a gap.
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import wave
import zlib

import numpy as np
from scipy import signal

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "sounds")

# --- the basics ------------------------------------------------------------------------


def n_of(d):
    return max(1, int(round(d * SR)))


def t_of(d):
    return np.arange(n_of(d)) / SR


class Kit:
    """One sound's random numbers, seeded by its name, and the building blocks."""

    def __init__(self, name):
        self.rng = np.random.default_rng(zlib.crc32(name.encode()))

    def noise(self, d):
        return self.rng.standard_normal(n_of(d))

    def uniform(self, lo, hi, size=None):
        return self.rng.uniform(lo, hi, size)

    def periodic(self, d, shape):
        """Noise exactly d seconds long that repeats without a seam: random
        phases under the magnitude shape(f) (f in Hz), back through the FFT."""
        n = n_of(d)
        f = np.fft.rfftfreq(n, 1 / SR)
        mag = shape(np.maximum(f, 1e-3))
        spec = mag * np.exp(2j * np.pi * self.rng.random(len(f)))
        spec[0] = 0
        x = np.fft.irfft(spec, n)
        return x / (np.max(np.abs(x)) + 1e-12)


def band_shape(lo, hi, tilt=0.0, steep=4):
    """A soft band-pass magnitude for Kit.periodic, tilted (dB per octave above lo)."""
    def shape(f):
        rise = 1 / (1 + (lo / f) ** steep)
        fall = 1 / (1 + (f / hi) ** steep)
        return rise * fall * (f / lo) ** (tilt / 6.02)
    return shape


def env(d, attack=0.002, decay=0.2, hold=0.0, curve=1.0):
    """Attack (linear), hold, then decay to -60 dB over `decay` seconds."""
    t = t_of(d)
    e = np.ones_like(t)
    a = t < attack
    e[a] = (t[a] / max(attack, 1e-6)) ** curve
    late = t >= attack + hold
    e[late] = 10 ** (-3 * (t[late] - attack - hold) / max(decay, 1e-6))
    return e


def bell(d, peak=0.4, sharp=2.0):
    """Swells to its peak (fraction of d) and away again."""
    t = t_of(d) / d
    up = np.clip(t / peak, 0, 1)
    down = np.clip((1 - t) / (1 - peak), 0, 1)
    return np.where(t < peak, np.sin(up * np.pi / 2) ** sharp, np.sin(down * np.pi / 2) ** sharp)


def fade(x, fin=0.002, fout=0.012):
    x = x.copy()
    a, b = min(len(x), n_of(fin)), min(len(x), n_of(fout))
    x[:a] *= np.linspace(0, 1, a)
    x[len(x) - b:] *= np.linspace(1, 0, b)
    return x


def bp(x, lo, hi, order=2):
    hi = min(hi, SR / 2 * 0.95)
    return signal.sosfilt(signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos"), x)


def lp(x, fc, order=2):
    return signal.sosfilt(signal.butter(order, min(fc, SR / 2 * 0.95), btype="low", fs=SR, output="sos"), x)


def hp(x, fc, order=2):
    return signal.sosfilt(signal.butter(order, fc, btype="high", fs=SR, output="sos"), x)


def sweep(x, fc, q=2.0, mode="bp"):
    """A resonant filter whose centre moves: fc is an array (Hz) as long as x
    (a TPT state-variable filter, one sample at a time)."""
    out = np.zeros_like(x)
    ic1 = ic2 = 0.0
    k = 1.0 / q
    g = np.tan(np.pi * np.clip(fc, 20, SR * 0.45) / SR)
    for i in range(len(x)):
        gi = g[i]
        a1 = 1 / (1 + gi * (gi + k))
        a2 = gi * a1
        a3 = gi * a2
        v3 = x[i] - ic2
        v1 = a1 * ic1 + a2 * v3
        v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        out[i] = v1 if mode == "bp" else v2
    return out


def glide(d, points):
    """A curve through (time fraction, value) points, eased - for pitches and filter sweeps."""
    t = t_of(d) / d
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    return np.interp(t, xs, ys)


def osc(freq, d=None):
    """A sine at freq (a number, or an array of Hz per sample)."""
    if np.isscalar(freq):
        return np.sin(2 * np.pi * freq * t_of(d))
    return np.sin(2 * np.pi * np.cumsum(freq) / SR)


def modal(d, partials, attack=0.0015):
    """Ringing partials: (Hz, amplitude, seconds to -60 dB) each - a bell, a bar, a plank."""
    t = t_of(d)
    out = np.zeros_like(t)
    for f, a, dec in partials:
        if f >= SR / 2 * 0.9:
            continue
        out += a * np.sin(2 * np.pi * f * t + 0.3) * 10 ** (-3 * t / dec)
    ramp = np.clip(t / attack, 0, 1)
    return out * ramp


def pluck(f, d, damp=0.996, bright=0.5, kit=None):
    """A plucked string (Karplus-Strong)."""
    n = n_of(d)
    p = max(2, int(round(SR / f)))
    rng = kit.rng if kit else np.random.default_rng(1)
    buf = rng.uniform(-1, 1, p)
    buf = lp(buf, 1500 + 9000 * bright, 1) if p > 12 else buf
    out = np.zeros(n)
    for i in range(n):
        j = i % p
        out[i] = buf[j]
        buf[j] = damp * 0.5 * (buf[j] + buf[(j + 1) % p])
    return out


def place(out, x, at, gain=1.0, wrap=False):
    """Add x into out from `at` seconds (wrapping round the end for a loop)."""
    i = int(round(at * SR))
    if wrap:
        idx = (np.arange(len(x)) + i) % len(out)
        np.add.at(out, idx, x * gain)
        return out
    if i >= len(out):
        return out
    m = min(len(x), len(out) - i)
    out[i:i + m] += x[:m] * gain
    return out


def silence(d):
    return np.zeros(n_of(d))


def norm(x, peak):
    x = x - np.mean(x)
    return x / (np.max(np.abs(x)) + 1e-12) * peak


# --- the families ---------------------------------------------------------------------


def thump(kit, d=0.12, f=90, drop=0.6, decay=0.09, noise=0.4, nlp=500):
    """A soft low impact: a falling sine and a puff of dull noise."""
    fc = glide(d, [(0, f), (1, f * drop)])
    body = osc(fc) * env(d, 0.002, decay)
    puff = lp(kit.noise(d), nlp) * env(d, 0.001, decay * 0.6)
    return body + noise * puff / (np.max(np.abs(puff)) + 1e-9)


def whoosh(kit, d, f0, f1, f2, q=1.6, peak=0.45, sharp=1.5, body=0.0):
    """Air pushed aside: noise through a band swept f0 -> f1 -> f2, swelling and gone."""
    x = kit.noise(d)
    fc = glide(d, [(0, f0), (peak, f1), (1, f2)])
    y = sweep(x, fc, q) * bell(d, peak, sharp)
    if body:
        y += body * sweep(x, fc * 0.4, 0.8, "lp") * bell(d, peak, sharp) * 0.6
    return y


def crunch(kit, d, rate=180, lo=1800, hi=7000, decay=0.06):
    """Many tiny clicks - gravel, straw, twigs, the rasp of a hinge."""
    out = silence(d)
    t = 0.0
    while t < d:
        c = kit.noise(0.004) * env(0.004, 0.0003, 0.003)
        place(out, c, t, kit.uniform(0.2, 1.0))
        t += kit.rng.exponential(1 / rate)
    return bp(out, lo, hi) * env(d, 0.003, decay * 3)


def creak(kit, d, f0=180, f1=320, roughness=0.35):
    """Wood creaking: friction pulses at a rate that moves, through a woody band."""
    rate = glide(d, [(0, f0), (0.5, (f0 + f1) / 2 * 1.1), (1, f1)])
    rate = rate * (1 + roughness * 0.2 * kit.noise(d).cumsum() / np.sqrt(np.arange(n_of(d)) + 1))
    phase = np.cumsum(rate) / SR
    pulses = (np.diff(np.floor(phase), prepend=0) > 0).astype(float)
    x = signal.lfilter([1], [1, -0.97], pulses) * (1 + roughness * kit.noise(d) * 0.3)
    return bp(x, 250, 2400) * bell(d, 0.5, 1.0)


def chime(notes, d, spacing=0.07, decay=0.9, bright=0.35, start=0.0):
    """Bell-like notes one after another: each a fundamental with a few inharmonic partials."""
    out = silence(d)
    for i, f in enumerate(notes):
        tone = modal(d, [(f, 1.0, decay), (f * 2.0, 0.35 * bright * 2, decay * 0.6),
                         (f * 2.76, 0.25 * bright, decay * 0.4), (f * 5.4, 0.12 * bright, decay * 0.25)])
        place(out, tone, start + i * spacing)
    return out


def sparkle(kit, d, n=12, lo=2500, hi=7000, ping=0.06):
    """Glittering pings scattered over d."""
    out = silence(d)
    for _ in range(n):
        f = kit.uniform(lo, hi)
        place(out, modal(ping * 3, [(f, 1, ping), (f * 1.5, 0.3, ping * 0.6)]), kit.uniform(0, d * 0.85),
              kit.uniform(0.3, 1.0))
    return out


def metal(f, d, decay=0.8, strike=0.5, kit=None):
    """A struck metal bar: its inharmonic modes (1, 2.76, 5.40, 8.93)."""
    x = modal(d, [(f, 1, decay), (f * 2.76, 0.6, decay * 0.7), (f * 5.40, 0.4, decay * 0.45),
                  (f * 8.93, 0.25, decay * 0.3), (f * 1.5, 0.15, decay * 0.5)])
    if kit is not None and strike:
        x += strike * hp(kit.noise(d), 3000) * env(d, 0.0005, 0.02)
    return x


def wood(f, d, decay=0.12, kit=None):
    """A knock on wood: a few quickly damped modes and a tap."""
    x = modal(d, [(f, 1, decay), (f * 2.3, 0.5, decay * 0.6), (f * 4.1, 0.25, decay * 0.4)])
    if kit is not None:
        x += 0.6 * bp(kit.noise(d), 1500, 6000) * env(d, 0.0003, 0.01)
    return x


def rustle(kit, d, lo=1200, hi=6000, grain=0.25):
    """Cloth or leaves: noise with a grainy, jittering level."""
    x = bp(kit.noise(d), lo, hi)
    jitter = lp(np.abs(kit.noise(d)), 60) * grain + (1 - grain)
    return x * jitter * bell(d, 0.35, 1.2)


def chirp(f0, f1, d):
    """A tone gliding f0 -> f1, faded in and out."""
    return osc(glide(d, [(0, f0), (1, f1)])) * bell(d, 0.2, 1.0)


# --- voices -----------------------------------------------------------------------------

VOWELS = {  # formant (Hz, bandwidth Hz, level) sets, an adult masculine voice's
    "a": [(730, 90, 1.0), (1090, 110, 0.5), (2440, 160, 0.25)],
    "o": [(570, 80, 1.0), (840, 90, 0.5), (2410, 160, 0.18)],
    "u": [(330, 70, 1.0), (870, 90, 0.35), (2240, 160, 0.12)],
    "uh": [(640, 90, 1.0), (1190, 110, 0.45), (2390, 160, 0.2)],
    "e": [(530, 80, 1.0), (1840, 120, 0.45), (2480, 160, 0.25)],
    "i": [(300, 60, 1.0), (2200, 140, 0.35), (2950, 180, 0.2)],
    "m": [(260, 60, 1.0), (1000, 200, 0.08), (2200, 250, 0.05)],
}

BODIES = {"feminine": (215.0, 1.17), "masculine": (112.0, 1.0)}   # pitch (Hz), formants' scale


def resonator(x, f, bw):
    r = np.exp(-np.pi * bw / SR)
    a = [1, -2 * r * np.cos(2 * np.pi * f / SR), r * r]
    return signal.lfilter([1 - r], a, x)


def voiced(kit, d, f0, vowel, scale, breath=0.08, rough=0.0, vib=0.0, vib_rate=5.5):
    """A vowel sung at the pitch curve f0 (Hz per sample, or a number)."""
    n = n_of(d)
    f0 = np.full(n, float(f0)) if np.isscalar(f0) else f0[:n]
    jitter = 1 + 0.012 * lp(kit.noise(d), 30) / 0.3
    f = f0 * jitter * (1 + vib * np.sin(2 * np.pi * vib_rate * t_of(d)))
    phase = 2 * np.pi * np.cumsum(f) / SR
    src = np.zeros(n)
    for k in range(1, 60):
        mask = (k * f) < SR * 0.45
        if not mask.any():
            break
        src += mask * np.sin(k * phase) / k ** 1.1
    if rough:
        src *= 1 + rough * np.sin(phase / 2)
    src += breath * hp(kit.noise(d), 500)
    out = np.zeros(n)
    for F, bw, lvl in VOWELS[vowel]:
        out += lvl * resonator(src, F * scale, bw * scale)
    return out


def breathy(kit, d, vowel, scale, lo=400):
    """An unvoiced vowel - a breath, an 'h'."""
    src = hp(kit.noise(d), lo)
    out = np.zeros(n_of(d))
    for F, bw, lvl in VOWELS[vowel]:
        out += lvl * resonator(src, F * scale, bw * scale * 2.5)
    return out


def syllable(kit, d, f0s, vowel, scale, h=0.04, amp=1.0, **kw):
    """'ha', 'oh', 'uh': an optional breath, then the voiced vowel with its own attack and release."""
    out = silence(h + d)
    if h:
        place(out, breathy(kit, h + 0.02, vowel, scale) * bell(h + 0.02, 0.6, 1.0) * 0.6, 0)
    v = voiced(kit, d, glide(d, f0s) if isinstance(f0s, list) else f0s, vowel, scale, **kw)
    place(out, v * env(d, 0.015, d * 1.2, hold=d * 0.35) * fade(np.ones(n_of(d)), 0.01, 0.04), h)
    return out * amp


def voice(kit, body, what):
    f0, sc = BODIES[body]
    if what == "emote_laugh":
        out = silence(1.05)
        for i, (p, a) in enumerate([(1.28, 1.0), (1.22, 0.95), (1.15, 0.85), (1.08, 0.7), (1.0, 0.5)]):
            place(out, syllable(kit, 0.11, [(0, f0 * p * 1.04), (1, f0 * p * 0.94)], "a", sc, h=0.05, amp=a),
                  i * 0.17)
        return out
    if what == "emote_cry":
        out = silence(1.7)
        for i in range(3):
            place(out, breathy(kit, 0.12, "i", sc) * bell(0.12, 0.7) * 0.35, i * 0.24)
            place(out, syllable(kit, 0.12, [(0, f0 * 1.35), (1, f0 * 1.15)], "uh", sc, h=0, vib=0.03), i * 0.24 + 0.1)
        place(out, syllable(kit, 0.9, [(0, f0 * 1.45), (0.4, f0 * 1.35), (1, f0 * 0.95)], "u", sc, h=0.03,
                            vib=0.045, vib_rate=6.5, breath=0.15), 0.75)
        return out
    if what == "emote_surprise":
        out = silence(0.65)
        place(out, breathy(kit, 0.16, "o", sc) * bell(0.16, 0.8, 1.0) * 0.5, 0)
        place(out, syllable(kit, 0.36, [(0, f0 * 1.05), (0.45, f0 * 1.7), (1, f0 * 1.45)], "o", sc, h=0), 0.15)
        return out
    if what == "emote_angry":
        out = silence(0.85)
        place(out, syllable(kit, 0.16, [(0, f0 * 0.95), (1, f0 * 0.9)], "m", sc, h=0, amp=0.6), 0)
        place(out, breathy(kit, 0.16, "uh", sc) * env(0.16, 0.005, 0.15) * 0.9, 0.15)
        place(out, syllable(kit, 0.42, [(0, f0 * 0.82), (1, f0 * 0.72)], "uh", sc, h=0, rough=0.8, breath=0.2,
                            amp=0.8), 0.38)
        return out
    if what == "hurt":
        out = silence(0.32)
        place(out, syllable(kit, 0.2, [(0, f0 * 1.3), (1, f0 * 0.9)], "uh", sc, h=0.02, rough=0.4, breath=0.15), 0)
        return out
    if what == "knocked_out":
        out = silence(1.2)
        place(out, syllable(kit, 1.0, [(0, f0 * 1.1), (0.3, f0 * 1.0), (1, f0 * 0.55)], "o", sc, h=0.05,
                            breath=0.25, vib=0.02, vib_rate=4), 0)
        return out
    if what == "out_of_breath":
        out = silence(1.45)
        for i in range(3):
            place(out, breathy(kit, 0.22, "a", sc) * bell(0.22, 0.3, 1.2) * 0.9, i * 0.46)
            place(out, breathy(kit, 0.17, "i", sc, lo=900) * bell(0.17, 0.6, 1.2) * 0.45, i * 0.46 + 0.25)
        return out
    raise KeyError(what)


# --- the sounds -------------------------------------------------------------------------
# name (path in assets/sounds, no extension) -> (peak level, function of a Kit)

SOUNDS = {}


def sound(name, peak):
    def register(fn):
        SOUNDS[name] = (peak, fn)
        return fn
    return register


# Footsteps and movement -----------------------------------------------------------------

@sound("player/footstep_grass", 0.42)
def _(k):
    d = 0.2
    return 0.7 * thump(k, d, 80, 0.7, 0.05, 0.5, 350) + rustle(k, d, 1800, 7500, 0.5) * env(d, 0.004, 0.16)


@sound("player/footstep_dirt", 0.45)
def _(k):
    d = 0.16
    return thump(k, d, 95, 0.6, 0.06, 0.8, 700) + 0.25 * crunch(k, d, 120, 1500, 5000, 0.03)


@sound("player/footstep_gravel", 0.42)
def _(k):
    d = 0.22
    return 0.6 * thump(k, d, 85, 0.6, 0.05, 0.6, 500) + crunch(k, d, 420, 1800, 8000, 0.05)


@sound("player/footstep_forest", 0.42)
def _(k):
    d = 0.24
    out = 0.7 * thump(k, d, 75, 0.7, 0.06, 0.7, 400) + 0.6 * rustle(k, d, 900, 4500, 0.7) * env(d, 0.005, 0.18)
    for t in k.uniform(0.0, 0.08, 2):            # a twig or two
        place(out, wood(k.uniform(1400, 2600), 0.05, 0.03, k) * 0.35, t)
    return out


@sound("player/jump", 0.4)
def _(k):
    d = 0.3
    out = whoosh(k, d, 300, 900, 600, 1.2, 0.35)
    return out + place(silence(d), 0.5 * thump(k, 0.12, 90, 0.6, 0.05, 0.8, 600), 0)


@sound("player/land", 0.5)
def _(k):
    d = 0.3
    return thump(k, d, 70, 0.6, 0.12, 0.9, 450) + 0.4 * rustle(k, d, 1500, 6000, 0.5) * env(d, 0.003, 0.2)


@sound("player/hard_landing", 0.7)
def _(k):
    d = 0.5
    out = 1.2 * thump(k, d, 55, 0.5, 0.25, 1.0, 300) + 0.5 * crunch(k, d, 250, 1200, 5000, 0.08)
    return out + 0.3 * rustle(k, d, 800, 4000, 0.6)


@sound("player/landing_roll", 0.55)
def _(k):
    out = silence(0.6)
    for i, t in enumerate([0.0, 0.16, 0.34]):
        place(out, thump(k, 0.15, 80 - i * 6, 0.7, 0.07, 0.8, 450) * (1 - i * 0.2), t)
    return out + 0.5 * rustle(k, 0.6, 700, 4000, 0.5)


@sound("player/crouch", 0.25)
def _(k):
    d = 0.24
    return rustle(k, d, 900, 4500, 0.5) * glide(d, [(0, 1), (1, 0.6)]) + 0.3 * thump(k, d, 70, 0.8, 0.06, 0.5, 300)


@sound("player/stand", 0.22)
def _(k):
    d = 0.22
    return sweep(k.noise(d), glide(d, [(0, 1200), (1, 3800)]), 1.0) * bell(d, 0.5, 1.2)


@sound("player/dash", 0.5)
def _(k):
    d = 0.32
    return whoosh(k, d, 400, 2200, 700, 1.4, 0.3, 1.2, body=0.6) + 0.4 * thump(k, d, 85, 0.6, 0.05, 0.7, 500)


@sound("player/roll", 0.55)
def _(k):
    d = 0.6
    out = whoosh(k, d, 300, 1200, 400, 1.0, 0.25, 1.0)
    for t in (0.18, 0.36):
        place(out, thump(k, 0.15, 75, 0.7, 0.07, 0.7, 400) * 0.8, t)
    return out + 0.4 * rustle(k, d, 800, 4000, 0.5)


@sound("player/ledge_up", 0.4)
def _(k):
    d = 0.75
    scrape = bp(k.noise(d), 600, 3000) * (0.5 + 0.5 * lp(np.abs(k.noise(d)), 25) * 3) * bell(d, 0.4, 1.0)
    out = 0.6 * scrape + 0.5 * rustle(k, d, 900, 5000, 0.5)
    return place(out, thump(k, 0.15, 90, 0.6, 0.06, 0.8, 600) * 0.8, 0.55)


@sound("player/ledge_down", 0.38)
def _(k):
    d = 0.65
    scrape = bp(k.noise(d), 500, 2500) * bell(d, 0.6, 1.0)
    return 0.6 * scrape + 0.5 * rustle(k, d, 1000, 5000, 0.6)


@sound("player/climb_grip", 0.3)
def _(k):
    d = 0.16
    return rustle(k, d, 1500, 6500, 0.7) * env(d, 0.004, 0.12) + 0.3 * creak(k, d, 260, 420, 0.4)


@sound("player/climb_top", 0.45)
def _(k):
    d = 0.2
    return thump(k, d, 90, 0.6, 0.07, 0.8, 600) + 0.25 * crunch(k, d, 160, 1500, 5000, 0.04)


@sound("player/let_go", 0.32)
def _(k):
    d = 0.3
    return rustle(k, d, 1200, 6000, 0.6) + 0.5 * whoosh(k, d, 600, 1200, 400, 1.0, 0.5)


# Voices: each body's ---------------------------------------------------------------------

for _body in BODIES:
    for _what in ("emote_laugh", "emote_cry", "emote_surprise", "emote_angry", "hurt", "knocked_out",
                  "out_of_breath"):
        SOUNDS[f"player/{_body}/{_what}"] = (0.5, (lambda b, w: lambda k: voice(k, b, w))(_body, _what))


# Weapons ---------------------------------------------------------------------------------

def swing(k, d, f0, f1, f2, q=1.6, shimmer=0.0, body=0.0):
    out = whoosh(k, d, f0, f1, f2, q, 0.42, 1.6, body)
    if shimmer:
        out += shimmer * hp(k.noise(d), 6000) * bell(d, 0.42, 3.0)
    return out


@sound("player/attack", 0.5)
def _(k):
    return swing(k, 0.26, 500, 2600, 900, 1.8, 0.15)


@sound("player/spin_attack", 0.5)
def _(k):
    out = silence(0.5)
    place(out, swing(k, 0.26, 450, 2200, 800, 1.6, 0.12), 0)
    return place(out, swing(k, 0.26, 500, 2600, 900, 1.6, 0.12), 0.2)


@sound("player/parry", 0.5)
def _(k):
    d = 0.5
    out = 0.6 * swing(k, 0.16, 800, 3000, 1500, 1.6)
    out = place(silence(d), out, 0)
    return place(out, metal(1250, 0.4, 0.35, 0.6, k) * 0.5, 0.06)


@sound("player/shield_bash", 0.6)
def _(k):
    d = 0.35
    out = 0.6 * whoosh(k, 0.2, 300, 1100, 500, 1.2, 0.5)
    out = place(silence(d), out, 0)
    return place(out, wood(160, 0.25, 0.15, k) + thump(k, 0.25, 80, 0.6, 0.1, 0.8, 600), 0.1)


@sound("player/axe_attack", 0.55)
def _(k):
    return swing(k, 0.34, 280, 1500, 450, 1.4, body=0.8)


@sound("player/axe_heavy_attack", 0.6)
def _(k):
    d = 0.6
    out = swing(k, 0.48, 200, 1300, 350, 1.2, body=1.0)
    return place(np.pad(out, (0, n_of(d) - len(out))), thump(k, 0.2, 60, 0.6, 0.12, 0.5, 300) * 0.6, 0.0)


@sound("player/axe_spin_attack", 0.55)
def _(k):
    d = 0.62
    x = k.noise(d)
    fc = 700 + 500 * np.sin(2 * np.pi * 3.2 * t_of(d)) ** 2 + glide(d, [(0, 0), (0.5, 400), (1, 0)])
    return sweep(x, fc, 1.3) * bell(d, 0.5, 1.0) + 0.5 * sweep(x, fc * 0.4, 0.8, "lp") * bell(d, 0.5, 1.0)


@sound("player/bow_draw", 0.35)
def _(k):
    d = 0.6
    return creak(k, d, 120, 380, 0.5) * glide(d, [(0, 0.4), (1, 1.0)]) + 0.2 * rustle(k, d, 2000, 7000, 0.3)


@sound("player/bow_fire", 0.55)
def _(k):
    d = 0.4
    string = pluck(98, d, 0.993, 0.6, k) * env(d, 0.001, 0.35)
    return string + 0.7 * place(silence(d), whoosh(k, 0.18, 1500, 5000, 3000, 2.0, 0.25), 0.01)


@sound("player/crossbow_fire", 0.6)
def _(k):
    d = 0.4
    out = metal(2100, 0.06, 0.04, 0.8, k) * 0.6                  # the trigger's clack
    out = place(silence(d), out, 0)
    place(out, pluck(72, 0.34, 0.985, 0.4, k) * env(0.34, 0.001, 0.2), 0.02)
    place(out, wood(240, 0.15, 0.08, k) * 0.7, 0.02)
    return place(out, whoosh(k, 0.16, 1800, 5500, 3200, 2.0, 0.2) * 0.7, 0.03)


@sound("player/staff_attack", 0.45)
def _(k):
    d = 0.55
    out = whoosh(k, d, 400, 1600, 2400, 1.2, 0.5) * 0.6
    out += chime([880, 1108.7, 1318.5], d, 0.05, 0.4, 0.4) * 0.5
    return out + 0.3 * sparkle(k, d, 8, 3000, 7000)


@sound("player/staff_heavy_attack", 0.7)
def _(k):
    d = 0.9
    boom = osc(glide(d, [(0, 90), (1, 38)])) * env(d, 0.003, 0.6)
    out = 1.2 * boom + 0.6 * lp(k.noise(d), 900) * env(d, 0.002, 0.25)
    out += place(silence(d), chime([659.3, 987.8, 1318.5], d, 0.02, 0.7, 0.6) * 0.5, 0.02)
    return out + 0.3 * sparkle(k, d, 10, 2500, 6000)


@sound("player/wand_attack", 0.4)
def _(k):
    d = 0.4
    return 0.45 * whoosh(k, d, 1200, 3500, 2500, 2.0, 0.3) + sparkle(k, d, 14, 2800, 7500, 0.05)


@sound("player/wand_heavy_attack", 0.5)
def _(k):
    d = 0.8
    out = 0.6 * whoosh(k, d, 600, 2600, 3800, 1.4, 0.55, 1.0)
    out += chime([523.3, 659.3, 784.0, 1046.5], d, 0.06, 0.6, 0.5) * 0.45
    return out + sparkle(k, d, 22, 2500, 8000, 0.06)


@sound("player/mace_attack", 0.55)
def _(k):
    return swing(k, 0.3, 220, 1100, 380, 1.3, body=1.0)


@sound("player/mace_heavy_attack", 0.75)
def _(k):
    d = 0.8
    out = place(silence(d), swing(k, 0.32, 200, 1000, 300, 1.2, body=1.0) * 0.7, 0)
    place(out, 1.4 * thump(k, 0.5, 55, 0.5, 0.35, 1.0, 400), 0.26)
    return place(out, 0.6 * crunch(k, 0.45, 300, 1200, 6000, 0.1), 0.28)


@sound("player/dagger_attack", 0.45)
def _(k):
    return swing(k, 0.16, 1400, 4200, 2200, 2.2, 0.2)


@sound("player/dagger_heavy_attack", 0.5)
def _(k):
    out = silence(0.45)
    place(out, swing(k, 0.15, 1300, 4000, 2000, 2.0, 0.2), 0)
    place(out, swing(k, 0.15, 1500, 4400, 2400, 2.0, 0.2), 0.1)
    return place(out, thump(k, 0.12, 90, 0.6, 0.05, 0.8, 600) * 0.6, 0.28)


def shing(k, d, f, decay):
    """A blade drawn: a sliding hiss and the ring of the steel."""
    hiss = sweep(k.noise(d), glide(d, [(0, 3500), (0.6, 7500), (1, 6000)]), 3.0) * bell(d, 0.4, 1.0)
    return 0.6 * hiss + 0.45 * metal(f, d, decay, 0.3, k)


@sound("items/equip", 0.35)
def _(k):
    d = 0.25
    return rustle(k, d, 900, 5000, 0.5) + 0.25 * metal(1900, d, 0.15, 0.2, k)


@sound("items/sword/equip", 0.45)
def _(k):
    return shing(k, 0.6, 1480, 0.5)


@sound("items/dagger/equip", 0.4)
def _(k):
    return shing(k, 0.4, 2350, 0.3)


@sound("items/battle_axe/equip", 0.45)
def _(k):
    d = 0.4
    return 0.7 * metal(620, d, 0.3, 0.6, k) + thump(k, d, 90, 0.6, 0.08, 0.6, 500) + 0.4 * rustle(k, d, 800, 4000)


@sound("items/mace/equip", 0.45)
def _(k):
    d = 0.4
    return 0.6 * metal(480, d, 0.28, 0.5, k) + thump(k, d, 80, 0.6, 0.09, 0.6, 500) + 0.3 * rustle(k, d, 800, 4000)


@sound("items/longbow/equip", 0.4)
def _(k):
    d = 0.4
    return wood(330, d, 0.08, k) + 0.4 * pluck(110, d, 0.99, 0.3, k) * env(d, 0.002, 0.3)


@sound("items/crossbow/equip", 0.42)
def _(k):
    out = silence(0.32)
    place(out, metal(2400, 0.08, 0.05, 0.8, k) * 0.6, 0)
    place(out, wood(280, 0.12, 0.07, k), 0.03)
    return place(out, metal(1900, 0.1, 0.06, 0.8, k) * 0.5, 0.15)


@sound("items/crystal_staff/equip", 0.35)
def _(k):
    d = 0.9
    return chime([1318.5, 1975.5], d, 0.09, 0.8, 0.6) + 0.3 * sparkle(k, d, 6, 3000, 7000)


@sound("items/wand/equip", 0.32)
def _(k):
    d = 0.6
    return sparkle(k, d, 10, 3000, 8000) + 0.4 * chime([1760.0], d, 0, 0.5, 0.4)


@sound("combat/heavy_ready", 0.35)
def _(k):
    return chime([659.3, 987.8], 0.6, 0.11, 0.55, 0.45)


# Hits and magic ------------------------------------------------------------------------

@sound("combat/dice_roll", 0.42)
def _(k):
    d = 0.75
    out = silence(d)
    t, gap, a = 0.0, 0.035, 1.0
    while t < d - 0.08 and a > 0.12:
        place(out, wood(k.uniform(1800, 3200), 0.06, 0.03, k) * a, t)
        if k.uniform(0, 1) < 0.5:
            place(out, wood(k.uniform(1800, 3200), 0.05, 0.025, k) * a * 0.6, t + gap * 0.4)
        t += gap
        gap *= 1.22
        a *= 0.86
    return out


@sound("combat/blow", 0.7)
def _(k):
    d = 0.25
    out = thump(k, d, 95, 0.5, 0.09, 1.0, 1200) + 0.5 * bp(k.noise(d), 2000, 7000) * env(d, 0.0005, 0.03)
    return out + 0.4 * crunch(k, d, 250, 1500, 5000, 0.04)


@sound("combat/arrow_hit", 0.6)
def _(k):
    d = 0.3
    out = wood(520, d, 0.08, k) + 0.6 * thump(k, d, 110, 0.6, 0.05, 1.0, 900)
    return out + 0.25 * osc(glide(d, [(0, 34), (1, 28)])) * env(d, 0.002, 0.25) * np.sin(2 * np.pi * 21 * t_of(d))


@sound("combat/bolt_hit", 0.65)
def _(k):
    d = 0.32
    return 1.1 * thump(k, d, 85, 0.5, 0.09, 1.0, 900) + 0.8 * wood(380, d, 0.1, k)


@sound("combat/arrow_stick", 0.42)
def _(k):
    d = 0.3
    quiver = osc(48, d) * np.sin(2 * np.pi * 26 * t_of(d)) * env(d, 0.003, 0.25)
    return thump(k, d, 85, 0.6, 0.06, 1.0, 500) + 0.35 * quiver + 0.2 * crunch(k, d, 150, 1200, 4000, 0.03)


@sound("combat/spell_cast", 0.42)
def _(k):
    d = 0.45
    glow = osc(glide(d, [(0, 420), (1, 1500)])) + 0.5 * osc(glide(d, [(0, 426), (1, 1522)]))
    return 0.35 * glow * bell(d, 0.5, 1.0) + 0.5 * whoosh(k, d, 700, 2600, 3200, 1.3, 0.55)


@sound("combat/spell_hit", 0.6)
def _(k):
    d = 0.55
    out = thump(k, d, 120, 0.4, 0.12, 0.6, 2000) + 0.6 * bp(k.noise(d), 600, 5000) * env(d, 0.001, 0.15)
    return out + 0.6 * sparkle(k, d, 16, 1800, 6000, 0.07)


@sound("combat/spell_burst", 0.45)
def _(k):
    d = 0.45
    fizz = bp(k.noise(d), 2500, 9000) * env(d, 0.002, 0.35) * (0.6 + 0.4 * np.sign(k.noise(d)))
    return 0.7 * thump(k, d, 140, 0.4, 0.06, 0.5, 1500) + 0.4 * fizz + 0.3 * sparkle(k, d, 8, 2000, 5000)


@sound("combat/fizzle", 0.35)
def _(k):
    d = 0.42
    crackle = crunch(k, d, 260, 800, 4000, 0.12) * glide(d, [(0, 1), (1, 0.2)])
    return crackle + 0.3 * osc(glide(d, [(0, 520), (1, 180)])) * env(d, 0.005, 0.3)


@sound("combat/venom_tick", 0.35)
def _(k):
    d = 0.32
    out = 0.3 * bp(k.noise(d), 3000, 9000) * env(d, 0.01, 0.25)
    for t in k.uniform(0, 0.18, 4):
        place(out, chirp(k.uniform(300, 500), k.uniform(800, 1300), 0.03), t, k.uniform(0.5, 1))
    return out


@sound("combat/player_hurt", 0.6)
def _(k):
    d = 0.25
    return 1.2 * thump(k, d, 75, 0.6, 0.1, 1.0, 700) + 0.4 * bp(k.noise(d), 1000, 4000) * env(d, 0.001, 0.05)


@sound("combat/block", 0.6)
def _(k):
    d = 0.32
    return wood(230, d, 0.15, k) + 0.6 * wood(690, d, 0.08, k) + 0.6 * thump(k, d, 85, 0.6, 0.08, 0.8, 600)


@sound("combat/parry", 0.55)
def _(k):
    return metal(940, 0.8, 0.7, 0.8, k) + 0.5 * metal(1410, 0.8, 0.45, 0.0)


@sound("combat/dodge", 0.4)
def _(k):
    return whoosh(k, 0.28, 600, 2400, 500, 1.5, 0.5, 2.0)


# Creatures ----------------------------------------------------------------------------

def straw(k, d):
    return crunch(k, d, 300, 1500, 6000, 0.06)


@sound("combat/attack_dummy/hit", 0.6)
def _(k):
    d = 0.4
    return thump(k, d, 100, 0.6, 0.08, 1.0, 800) + 0.6 * straw(k, d) + 0.3 * creak(k, d, 150, 110, 0.5)


@sound("combat/attack_dummy/fall", 0.65)
def _(k):
    d = 0.75
    out = 0.5 * creak(k, 0.4, 140, 90, 0.6)
    out = place(silence(d), out, 0)
    place(out, 1.3 * thump(k, 0.35, 70, 0.5, 0.2, 1.0, 500), 0.32)
    return place(out, 0.6 * straw(k, 0.35), 0.32)


@sound("combat/attack_dummy/rise", 0.5)
def _(k):
    d = 0.6
    t = t_of(d)
    spring = osc(260 + 90 * np.sin(2 * np.pi * 11 * t) * np.exp(-t * 5)) * env(d, 0.004, 0.5)
    return 0.6 * spring + 0.5 * creak(k, d, 110, 260, 0.5)


def flap(k, d=0.16):
    return lp(k.noise(d), 1100) * env(d, 0.03, 0.12) + 0.3 * bp(k.noise(d), 1500, 4000) * env(d, 0.02, 0.06)


@sound("combat/flying_dummy/fly", 0.32)
def _(k):
    d = 2.0
    out = silence(d)
    for i in range(8):
        place(out, flap(k), i * 0.25 + k.uniform(-0.008, 0.008), k.uniform(0.75, 1.0), wrap=True)
        place(out, metal(k.uniform(2600, 3000), 0.05, 0.03, 0.0) * 0.06, i * 0.25 + 0.05, wrap=True)  # a gear's tick
    return out


@sound("combat/flying_dummy/warn", 0.45)
def _(k):
    d = 0.6       # a steam whistle's shriek
    f = glide(d, [(0, 1150), (0.25, 1500), (0.7, 1450), (1, 1100)]) * (1 + 0.01 * np.sin(2 * np.pi * 7 * t_of(d)))
    phase = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(phase) + 0.4 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase)
    steam = bp(k.noise(d), 2500, 9000)
    return (0.7 * tone + 0.35 * steam) * bell(d, 0.2, 0.8)


@sound("combat/flying_dummy/swoop", 0.5)
def _(k):
    d = 0.8
    return whoosh(k, d, 2400, 1600, 350, 1.5, 0.55, 1.2, body=0.5)


@sound("combat/flying_dummy/hit", 0.55)
def _(k):
    d = 0.4
    out = thump(k, d, 110, 0.6, 0.07, 1.0, 900) + 0.5 * straw(k, d)
    for t in (0.06, 0.13, 0.19):
        place(out, flap(k, 0.1) * 0.5, t)
    return out


@sound("combat/flying_dummy/fall", 0.45)
def _(k):
    d = 0.75
    whistle = osc(glide(d, [(0, 1600), (1, 500)])) * bell(d, 0.3, 1.0) * 0.4
    out = whistle + 0.5 * whoosh(k, d, 1500, 900, 300, 1.2, 0.5)
    for t in (0.0, 0.1, 0.22):
        place(out, flap(k, 0.12) * 0.6, t)
    return out


@sound("combat/flying_dummy/down", 0.6)
def _(k):
    d = 0.4
    return 1.2 * thump(k, d, 70, 0.5, 0.15, 1.0, 500) + 0.5 * straw(k, d) + 0.2 * metal(1300, d, 0.2, 0.3, k)


@sound("combat/flying_dummy/rise", 0.45)
def _(k):
    d = 0.75
    out = silence(d)
    for i in range(6):
        place(out, flap(k, 0.14) * (0.5 + i * 0.1), i * 0.1)
    return out + 0.25 * osc(glide(d, [(0, 500), (1, 1300)])) * bell(d, 0.7, 1.0)


# World and loot -----------------------------------------------------------------------

@sound("chests/idle", 0.22)
def _(k):
    d = 3.0       # magical smoke, swirling: a seamless loop
    t = t_of(d)
    lo = k.periodic(d, band_shape(250, 1400, -3))
    hi = k.periodic(d, band_shape(2500, 6000, 0))
    swirl = 0.6 + 0.25 * np.sin(2 * np.pi * t / d * 2) + 0.15 * np.sin(2 * np.pi * t / d * 5 + 1)
    shimmer = 0.5 + 0.5 * np.sin(2 * np.pi * t / d * 3 + 2)
    return lo * swirl + 0.25 * hi * shimmer


@sound("chests/open", 0.5)
def _(k):
    d = 0.95
    out = 0.8 * creak(k, 0.55, 110, 300, 0.5)
    out = place(silence(d), out, 0)
    place(out, whoosh(k, 0.6, 500, 2500, 3500, 1.2, 0.4) * 0.6, 0.25)
    return place(out, chime([523.3, 784.0, 1046.5], 0.6, 0.06, 0.6, 0.4) * 0.35, 0.35)


@sound("chests/opened", 0.2)
def _(k):
    d = 4.0       # standing open and aglow: a warm, seamless loop
    t = t_of(d)
    out = np.zeros_like(t)
    for f, a in ((220.0, 0.5), (329.75, 0.35), (440.0, 0.3), (554.25, 0.2)):
        for detune in (-0.5, 0.0, 0.5):           # whole cycles in 4 s, so the loop meets itself
            out += a / 3 * np.sin(2 * np.pi * (f + detune) * t + k.uniform(0, 6.3))
    breath = 0.7 + 0.3 * np.sin(2 * np.pi * t / d * 2)
    return out * breath + 0.12 * k.periodic(d, band_shape(3000, 8000))


@sound("chests/close", 0.6)
def _(k):
    d = 0.6
    out = 0.5 * creak(k, 0.25, 250, 120, 0.5)
    out = place(silence(d), out, 0)
    place(out, wood(140, 0.35, 0.18, k) + thump(k, 0.35, 70, 0.6, 0.12, 0.9, 500), 0.2)
    return out


@sound("world/loot_throw", 0.38)
def _(k):
    d = 0.3
    pop = osc(glide(0.05, [(0, 700), (1, 220)])) * env(0.05, 0.001, 0.05)
    return place(silence(d), pop, 0) + 0.6 * whoosh(k, d, 900, 2400, 1200, 1.4, 0.3)


@sound("world/loot_land", 0.35)
def _(k):
    d = 0.25
    return thump(k, d, 100, 0.6, 0.06, 0.7, 500) + 0.25 * rustle(k, d, 1800, 7000) + 0.15 * metal(2600, d, 0.12, 0)


@sound("world/loot_uncommon", 0.3)
def _(k):
    return chime([783.99], 0.6, 0, 0.5, 0.4)


@sound("world/loot_rare", 0.34)
def _(k):
    return chime([783.99, 1174.66], 0.85, 0.09, 0.6, 0.45)


@sound("world/loot_epic", 0.38)
def _(k):
    return chime([659.25, 830.61, 987.77, 1318.51], 1.1, 0.08, 0.7, 0.5) + 0.2 * sparkle(k, 1.1, 8, 3000, 7000)


@sound("world/loot_legendary", 0.42)
def _(k):
    d = 1.6
    out = chime([523.25, 659.25, 783.99, 1046.5, 1318.51], d, 0.075, 0.9, 0.5)
    pad = sum(osc(f, d) for f in (261.63, 392.0, 523.25)) * bell(d, 0.35, 1.0)
    return out + 0.25 * pad + 0.3 * sparkle(k, d, 16, 2500, 7000)


@sound("world/loot_mythic", 0.42)
def _(k):
    d = 2.1
    t = t_of(d)
    pad = sum(np.sin(2 * np.pi * (f + det) * t) for f in (293.66, 440.0, 587.33, 698.46) for det in (-1.2, 0, 1.3))
    out = 0.12 * pad * bell(d, 0.4, 1.0)
    out += chime([587.33, 880.0, 1174.66, 1396.91, 1760.0], d, 0.11, 1.2, 0.6) * 0.8
    return out + 0.45 * sparkle(k, d, 30, 2500, 9000, 0.08)


@sound("items/pickup", 0.35)
def _(k):
    d = 0.22
    return rustle(k, d, 900, 5000, 0.5) + 0.3 * place(silence(d), metal(2300, 0.15, 0.1, 0.2, k), 0.05)


@sound("items/drop", 0.4)
def _(k):
    d = 0.25
    return thump(k, d, 90, 0.6, 0.06, 0.8, 500) + 0.3 * rustle(k, d, 1500, 6000) + 0.2 * metal(1700, d, 0.15, 0, k)


# Ambient (loops) ----------------------------------------------------------------------

@sound("ambient/wind", 0.22)
def _(k):
    d = 8.0
    t = t_of(d)
    low = k.periodic(d, band_shape(120, 700, -4))
    mid = k.periodic(d, band_shape(500, 2200, -3))
    gust = 0.55 + 0.3 * np.sin(2 * np.pi * t / d + 0.5) + 0.15 * np.sin(2 * np.pi * 3 * t / d + 2)
    whistle = 0.5 + 0.5 * np.sin(2 * np.pi * 2 * t / d + 4)
    return low * gust + 0.35 * mid * gust * whistle


@sound("ambient/stream", 0.3)
def _(k):
    d = 6.0
    out = 0.6 * k.periodic(d, band_shape(300, 2500, -2))
    for _ in range(140):
        f = k.uniform(500, 1400)
        place(out, chirp(f, f * k.uniform(1.4, 2.2), k.uniform(0.01, 0.03)) * 0.5, k.uniform(0, d),
              k.uniform(0.2, 1.0), wrap=True)
    return out


@sound("ambient/crickets", 0.22)
def _(k):
    d = 6.0
    out = 0.05 * k.periodic(d, band_shape(2000, 8000))
    for f, every, start in ((4600.0, 0.75, 0.1), (5150.0, 1.1, 0.45), (4350.0, 1.5, 0.9)):
        t = start
        while t < d:
            for p in range(3):
                pulse = osc(f, 0.014) * bell(0.014, 0.3, 1.0)
                place(out, pulse, t + p * 0.03, 0.6 + 0.4 * (f == 4600.0), wrap=True)
            t += every
    return out


# Interface ----------------------------------------------------------------------------

@sound("ui/click", 0.32)
def _(k):
    return wood(2100, 0.06, 0.035, k)


@sound("ui/hover", 0.14)
def _(k):
    return wood(3200, 0.04, 0.02, k)


@sound("ui/back", 0.28)
def _(k):
    out = silence(0.16)
    place(out, wood(2000, 0.05, 0.03, k), 0)
    return place(out, wood(1500, 0.06, 0.035, k), 0.07)


@sound("ui/pause", 0.3)
def _(k):
    return chime([659.25, 493.88], 0.5, 0.1, 0.45, 0.2)


@sound("ui/resume", 0.3)
def _(k):
    return chime([493.88, 659.25], 0.5, 0.1, 0.45, 0.2)


@sound("ui/inventory_open", 0.35)
def _(k):
    d = 0.35
    out = rustle(k, d, 700, 4500, 0.6)
    return place(out, metal(2200, 0.12, 0.08, 0.3, k) * 0.4, 0.12)


@sound("ui/inventory_close", 0.33)
def _(k):
    d = 0.3
    return rustle(k, d, 600, 3500, 0.6) + 0.5 * place(silence(d), thump(k, 0.12, 110, 0.6, 0.05, 0.8, 500), 0.15)


@sound("ui/hotbar_select", 0.28)
def _(k):
    return wood(1700, 0.07, 0.04, k) + 0.25 * metal(3400, 0.07, 0.03, 0)


@sound("ui/item_pick", 0.28)
def _(k):
    d = 0.14
    return sweep(k.noise(d), glide(d, [(0, 1500), (1, 4500)]), 1.2) * bell(d, 0.5) + 0.2 * metal(2800, d, 0.06, 0)


@sound("ui/item_place", 0.3)
def _(k):
    d = 0.14
    return thump(k, d, 140, 0.6, 0.04, 0.8, 900) + 0.3 * wood(1900, d, 0.03, k)


@sound("ui/sort", 0.28)
def _(k):
    out = silence(0.4)
    for i in range(5):
        place(out, rustle(k, 0.07, 1200, 5000, 0.4) + 0.4 * wood(1600 + i * 120, 0.07, 0.02, k), i * 0.06)
    return out


@sound("ui/wear", 0.3)
def _(k):
    return rustle(k, 0.32, 800, 5000, 0.6)


@sound("ui/error", 0.3)
def _(k):
    out = silence(0.28)
    for i, f in enumerate((196.0, 185.0)):
        tone = signal.square(2 * np.pi * f * t_of(0.1)) * env(0.1, 0.004, 0.12)
        place(out, lp(tone, 1200), i * 0.12)
    return out


@sound("ui/screenshot", 0.3)
def _(k):
    out = silence(0.2)
    place(out, metal(3000, 0.05, 0.03, 0.8, k), 0)
    return place(out, wood(1800, 0.06, 0.03, k) + 0.5 * metal(2400, 0.06, 0.03, 0.6, k), 0.09)


@sound("ui/secret", 0.35)
def _(k):
    d = 0.95
    return chime([1046.5, 1318.51, 1567.98, 2093.0], d, 0.07, 0.6, 0.4) + 0.4 * sparkle(k, d, 12, 3000, 8000)


@sound("combat/lock_on", 0.3)
def _(k):
    # a short rising pair and a tick: the sights settle on it
    d = 0.22
    return chime([1318.51, 1975.53], d, 0.05, 0.12, 0.3) + 0.4 * metal(3100, d, 0.05, 0.4, k)


@sound("combat/lock_off", 0.25)
def _(k):
    d = 0.2
    return chime([1975.53, 1318.51], d, 0.05, 0.1, 0.2)


@sound("ui/dialogue", 0.2)
def _(k):
    d = 0.1
    return 0.6 * bp(k.noise(d), 1500, 5000) * env(d, 0.003, 0.05) + 0.5 * chime([1174.66], d, 0, 0.12, 0.2)


@sound("ui/select", 0.25)
def _(k):
    return wood(2500, 0.07, 0.04, k) + 0.3 * chime([1567.98], 0.07, 0, 0.1, 0.2)


@sound("ui/place", 0.35)
def _(k):
    d = 0.18
    return thump(k, d, 120, 0.6, 0.05, 0.8, 800) + 0.5 * wood(900, d, 0.05, k)


@sound("ui/remove", 0.3)
def _(k):
    d = 0.25
    return sweep(k.noise(d), glide(d, [(0, 2500), (1, 300)]), 1.0, "lp") * env(d, 0.003, 0.2)


@sound("ui/rotate", 0.25)
def _(k):
    out = silence(0.18)
    for i in range(3):
        place(out, wood(2600 - i * 200, 0.04, 0.015, k), i * 0.045)
    return out


@sound("ui/brush", 0.1)
def _(k):
    d = 0.05
    return bp(k.noise(d), 2000, 6000) * env(d, 0.003, 0.04)


@sound("ui/undo", 0.25)
def _(k):
    d = 0.18
    x = sweep(k.noise(d), glide(d, [(0, 600), (1, 3000)]), 1.2) * glide(d, [(0, 0.1), (0.9, 1), (1, 0)])
    return x + 0.4 * place(silence(d), wood(2200, 0.03, 0.02, k), d - 0.03)


@sound("ui/save", 0.3)
def _(k):
    d = 0.5
    scratch = bp(k.noise(0.16), 2500, 8000) * (0.5 + 0.5 * np.sin(2 * np.pi * 28 * t_of(0.16))) * bell(0.16)
    return place(chime([523.25, 783.99], d, 0.1, 0.45, 0.3), scratch * 0.4, 0)


@sound("ui/playtest", 0.35)
def _(k):
    d = 0.5
    return 0.6 * whoosh(k, d, 400, 2400, 3000, 1.3, 0.6) + chime([659.25, 987.77], d, 0.09, 0.4, 0.3)


# --- writing -------------------------------------------------------------------------------


def render(name):
    peak, fn = SOUNDS[name]
    x = np.asarray(fn(Kit(name)), dtype=float)
    looping = name.startswith("ambient/") or name in ("chests/idle", "chests/opened", "combat/flying_dummy/fly")
    x = norm(x, peak)
    if not looping:
        x = fade(x)
    return x


def write_mp3(x, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "s.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-map_metadata", "-1",
                        "-fflags", "+bitexact", "-flags:a", "+bitexact", "-id3v2_version", "0",
                        "-codec:a", "libmp3lame", "-q:a", "4", path], check=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("names", nargs="*", help="only these (paths in assets/sounds, no extension)")
    ap.add_argument("--out", default=OUT, help="the sound pack folder (default: assets/sounds)")
    ap.add_argument("--list", action="store_true", help="list what it makes and stop")
    ap.add_argument("--wav", action="store_true", help="also write each as a .wav beside this script, to listen")
    a = ap.parse_args()
    if a.list:
        for name in SOUNDS:
            print(name)
        return
    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg is needed (with libmp3lame) to write MP3s")
    names = a.names or list(SOUNDS)
    for name in names:
        if name not in SOUNDS:
            sys.exit(f"no placeholder called {name} (--list shows them)")
        x = render(name)
        write_mp3(x, os.path.join(a.out, name + ".mp3"))
        if a.wav:
            p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wav", name + ".wav")
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with wave.open(p, "wb") as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(SR)
                w.writeframes(np.clip(np.round(x * 32767), -32768, 32767).astype("<i2").tobytes())
        print(f"{name}.mp3  {len(x) / SR:.2f} s")


if __name__ == "__main__":
    main()
