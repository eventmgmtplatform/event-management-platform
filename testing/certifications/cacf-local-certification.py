#!/usr/bin/env python3
"""Historical isolated failure suite retained for deliberate recovery only."""
if __name__=='__main__':
    print('BLOCKED: isolated CACF failure suite archived in testing/legacy/cacf-local-certification.py. Use testing/run.py happy-path for shared E2E; see docs/environment-consolidation.md for explicit recovery.')
    raise SystemExit(2)
