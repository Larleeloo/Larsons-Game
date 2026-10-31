#!/usr/bin/env python3
"""Make every song again: each script beside this one that arranges a song
(it has a build()) writes its MIDI and the game's MP3.

    python3 tools/music/all.py            # every song's MIDI and MP3
    python3 tools/music/all.py --midi     # just the MIDI files
    python3 tools/music/all.py thunder    # just these
"""

import importlib
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)


def songs():
    for name in sorted(os.listdir(HERE)):
        if name.endswith(".py") and name not in ("all.py", "arrange.py", "medieval.py"):
            yield name[:-3]


def main():
    args = sys.argv[1:]
    flags = [a for a in args if a.startswith("--")]
    only = [a for a in args if not a.startswith("--")]
    for song_id in songs():
        if only and song_id not in only:
            continue
        module = importlib.import_module(song_id)
        if song_id == "dream_on":           # the first song, written before arrange.py
            sys.argv = [sys.argv[0], *flags]
            module.main()
        else:
            module.build().save(song_id, argv=flags)


if __name__ == "__main__":
    main()
