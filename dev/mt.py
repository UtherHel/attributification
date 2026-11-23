#!/usr/bin/env python3
"""Mod test harness of this repository; see dev/README.md, or run `python dev/mt.py -h`."""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from modtest.cli import main  # noqa: E402

if __name__ == "__main__":
    main(HERE.parent)
