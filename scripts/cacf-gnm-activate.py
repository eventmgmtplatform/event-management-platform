#!/usr/bin/env python3
"""Activate synthetic CACF/GNM in the existing shared runtime; preserve laboratory."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import time
import urllib.request
ROOT=Path(__file__).resolve().parents[1]
COMPOSE=['docker','compose','--env-file',str(ROOT/'.env'),'-f',str(ROOT/'infrastructure/docker-compose.yml')]

def main():
    output=ROOT/'evidences/cacf-gnm-activation'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    output.mkdir(parents=True)
    report={'status':'RUNNING','scope':'Shared runtime; synthetic ServiceNow/GNM/NEXT; laboratory preserved'}
    changed=False
    def run(args, data=None, timeout=90):
        return subprocess.run(args,cwd=ROOT,input=data,text=True,capture_output=True,check=True,timeout=timeout).stdout.strip()
    def ready():
        end=time.monotonic()+180
        while time.monotonic()<end:
            try:
                with urllib.request.urlopen('http://127.0.0.1:8083/health/ready',timeout=5) as response:
                    if json.load(response)['status']=='UP':return
            except OSError:pass
            time.sleep(2)
        raise RuntimeError('WORKER_NOT_READY')
    try:
        run(['bash','scripts/emctl','validate'])
        previous=json.loads(run(['docker','inspect','event-integration-worker']))[0]
        env=dict(v.split('=',1) for v in previous['Config']['Env'] if '=' in v)
        keys=['CACF_ENABLED','NEXT_BASE_URL','NEXT_USERNAME','NEXT_PASSWORD','CACF_API_TOKEN','GNM_BASE_URL','GNM_PROVIDER_REGISTRY_RESOURCE']
        restore={key:env.get(key, '') for key in keys}
        restore['CACF_ENABLED']=env.get('CACF_ENABLED','false')
        restore['GNM_PROVIDER_REGISTRY_RESOURCE']=env.get('GNM_PROVIDER_REGISTRY_RESOURCE','gnm/provider-registry.json')
        rollback=output/'rollback.compose.json'
        rollback.write_text(json.dumps({'services':{'integration-worker':{'image':previous['Image'],'environment':restore}}}))
        rollback.chmod(0o600)
        report.update(head=run(['git','rev-parse','HEAD']),workerImage=previous['Image'])
        backup=output/'before-activation.dump'
        with backup.open('wb') as stream:
            backup.chmod(0o600)
            subprocess.run(['docker','exec','event-postgres','sh','-c','pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --schema=event_management --schema=event_processor --no-owner --no-privileges'],stdout=stream,stderr=subprocess.PIPE,check=True,timeout=120)
        migration=ROOT/'infrastructure/postgres/init/008-cacf-core.sql'
        run(['docker','exec','-i','event-postgres','sh','-c','psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],migration.read_text())
        report['migration008Sha256']=hashlib.sha256(migration.read_bytes()).hexdigest()
        print('Backup and migration complete; starting NEXT and activating Worker',flush=True)
        run(COMPOSE+['up','-d','--no-build','--no-deps','--wait','next-mock'],timeout=180)
        changed=True
        run(COMPOSE+['up','-d','--no-build','--no-deps','integration-worker'],timeout=180)
        ready()
        run(['bash','scripts/emctl','next-mock','health'])
        print('Running shared UC-001 with consumer restart',flush=True)
        with (output/'certification.log').open('w') as stream:
            subprocess.run(['python3','testing/run.py','happy-path','--runtime','shared','--restart'],cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,check=True,timeout=1200)
        report['status']='PASS'
    except Exception as error:
        report.update(status='FAIL',errorType=type(error).__name__)
        if changed:
            try:
                run(COMPOSE+['-f',str(rollback),'up','-d','--no-build','--no-deps','integration-worker'],timeout=180)
                ready();report['rollback']='PASS (worker configuration restored; additive data and NEXT retained)'
            except Exception:report['rollback']='ACTION_REQUIRED'
    finally:
        (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
        (output/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(output.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
        print(json.dumps({'status':report['status'],'evidence':str(output/'report.json')}))
    return 0 if report['status']=='PASS' else 1
if __name__=='__main__':raise SystemExit(main())
