#!/usr/bin/env python3
"""Prepare only the isolated OS_11 laboratory; preserve all database volumes/offsets."""
import subprocess
import time
import urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
COMPOSE=['docker','compose','-f',str(ROOT/'testing/environments/lifecycle.compose.yml')]
def run(args,**kwargs):subprocess.run(args,cwd=ROOT,check=True,**kwargs)
def main():
    run(COMPOSE+['stop','gateway','processor','worker','ess'])
    for service in ['event-gateway','event-processor','integration-worker','event-state-service']:
        run(['mvn','-B','-ntp','-o','-f',str(ROOT/'services'/service/'pom.xml'),'package','-DskipTests'])
    run(COMPOSE+['up','-d','--wait','postgres','kafka','opensearch','servicenow-mock','gnm-mock','next-mock'])
    for name in ['020-processor-lifecycle.sql','021-worker-delivery-recovery.sql']:
        with (ROOT/'infrastructure/postgres/init'/name).open() as sql:
            run(COMPOSE+['exec','-T','postgres','psql','-X','-v','ON_ERROR_STOP=1','-U','lifecycle','-d','lifecycle'],stdin=sql)
    run(COMPOSE+['up','-d'])
    deadline=time.monotonic()+120
    while time.monotonic()<deadline:
        try:
            for port in [28082,28083,28084]:
                with urllib.request.urlopen(f'http://127.0.0.1:{port}/health/ready',timeout=5) as response:
                    if response.status!=200:raise RuntimeError('Service not ready')
            return
        except Exception:time.sleep(1)
    raise RuntimeError('Isolated services not ready; inspect os11-lifecycle logs')
if __name__=='__main__':main()
