#!/usr/bin/env python3
"""Single-runtime certification; historical all-environment stop/start is archived."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
if __name__=='__main__':
    for command in [ ['bash','scripts/emctl','validate'],['python3','testing/run.py','happy-path','--restart'],
                     ['python3','testing/run.py','blackout'],['python3','testing/certifications/event-state-certification.py'],
                     ['bash','scripts/emctl','health'] ]:
        subprocess.run(command,cwd=ROOT,check=True)
