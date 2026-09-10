#!/usr/bin/env python3
"""Compatibility entry point: restart certification now targets the shared runtime."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
if __name__=='__main__':
    raise SystemExit(subprocess.call([sys.executable,str(ROOT/'testing/run.py'),'happy-path','--runtime','shared','--restart'],cwd=ROOT))
