#!/usr/bin/env python3
"""OS_11 restart regression: same public E2E, restart after durable NEXT submission."""
import importlib.util
import json
import subprocess
import time
import uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('happy_path',ROOT/'testing/e2e/happy_path.py')
e2e=importlib.util.module_from_spec(spec);spec.loader.exec_module(e2e)
def main():
    output=ROOT/'evidences/os11/restart'/uuid.uuid4().hex;output.mkdir(parents=True)
    report={'status':'RUNNING'}
    def checkpoint(stage,identities):
        report['restart']={'stage':stage,'identities':identities}
        with (output/'restart.log').open('w') as log:
            subprocess.run(e2e.COMPOSE+['restart','processor','worker','ess'],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=90)
        def ready():
            try:return all(e2e.http('GET',f'http://127.0.0.1:{port}/health/ready')[0]==200 for port in [28082,28083,28084])
            except Exception:return False
        e2e.wait(ready,'services after restart',90)
    try:e2e.run(output,report,checkpoint);report['status']='PASS';code=0
    except Exception as e:report.update(status='FAIL',error=str(e));code=1
    (output/'report.json').write_text(json.dumps(report,indent=2)+'\n');print(str(output/'report.json'));return code
if __name__=='__main__':raise SystemExit(main())
