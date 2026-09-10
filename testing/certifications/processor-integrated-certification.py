#!/usr/bin/env python3
"""Integrated local engines through Lifecycle, Worker mocks and ESS projection."""
import hashlib
import importlib.util
import json
from pathlib import Path
import uuid
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('integrated',ROOT/'testing/e2e/happy_path.py')
scenario=importlib.util.module_from_spec(spec);spec.loader.exec_module(scenario)


def main():
    output=ROOT/'evidences/integrated'/uuid.uuid4().hex
    output.mkdir(parents=True)
    report={'status':'RUNNING','scope':'shared local runtime, isolated synthetic resource/rules; mocks only; AIOps certified independently'}
    try:
        scenario.run(output,report,runtime='shared',all_engines=True)
        report['status']='PASS'
    except Exception as error:
        report.update(status='FAIL',error=str(error));raise
    finally:
        (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
        (output/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(output.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
        print(output)


if __name__=='__main__':main()
