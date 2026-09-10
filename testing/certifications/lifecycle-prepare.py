#!/usr/bin/env python3
"""Check the single runtime; never recreate an archived laboratory implicitly."""
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[2]
if __name__=='__main__':
    for action in ['validate','health']:
        subprocess.run(['bash',str(ROOT/'scripts/emctl'),action],cwd=ROOT,check=True)
