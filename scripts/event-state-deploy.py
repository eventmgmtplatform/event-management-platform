#!/usr/bin/env python3
"""Build and replace only local ESS, retain the previous image, certify or roll back."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ['docker', 'compose', '--env-file', str(ROOT / '.env'), '-f', str(ROOT / 'infrastructure/docker-compose.yml')]


def main():
    output = ROOT / 'evidence/os-05-ess/deployment' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    output.mkdir(parents=True)
    report = {'status': 'RUNNING', 'schemaChanges': 'additive ess_quarantine table (016)'}
    previous = tag = None
    replaced = False

    def run(args):
        return subprocess.check_output(args, cwd=ROOT, text=True, timeout=60).strip()

    def logged(args, name, timeout=900):
        with (output / name).open('w') as log:
            subprocess.run(args, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=timeout)

    def ready():
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                with urllib.request.urlopen('http://127.0.0.1:8084/health/ready', timeout=5) as response:
                    if json.load(response)['status'] == 'UP':
                        return
            except OSError:
                pass
            time.sleep(2)
        raise RuntimeError('ESS_READINESS_TIMEOUT')

    try:
        previous = run(['docker', 'inspect', '--format', '{{.Image}}', 'event-state-service'])
        tag = run(['docker', 'inspect', '--format', '{{.Config.Image}}', 'event-state-service'])
        report.update(previousImage=previous, imageTag=tag, head=run(['git', 'rev-parse', 'HEAD']),
                      branch=run(['git', 'branch', '--show-current']))
        run(['docker', 'image', 'tag', previous, 'event-management/event-state-service:rollback-' + previous.split(':')[1][:12]])
        print('Building ESS; evidence: ' + str(output), flush=True)
        logged(COMPOSE + ['build', 'event-state-service'], 'build.log')
        migration = ROOT / 'infrastructure/postgres/init/016-ess-quarantine.sql'
        report['migrationSha256'] = hashlib.sha256(migration.read_bytes()).hexdigest()
        # Preserve authoritative schema before additive migration; backup remains ignored.
        backup = subprocess.check_output(['docker', 'exec', 'event-postgres', 'sh', '-c',
            'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --schema=event_management --no-owner --no-privileges'], timeout=120)
        (output / 'before-migration.dump').write_bytes(backup)
        subprocess.run(['docker', 'exec', '-i', 'event-postgres', 'sh', '-c',
            'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
            input=migration.read_bytes(), capture_output=True, check=True, timeout=60)
        replaced = True
        logged(COMPOSE + ['up', '-d', '--no-deps', 'event-state-service'], 'replace.log', 180)
        ready()
        report['deployedImage'] = run(['docker', 'inspect', '--format', '{{.Image}}', 'event-state-service'])
        logged(['python3', 'testing/certifications/event-state-certification.py', '--restart'], 'certification.log', 600)
        report['status'] = 'PASS'
    except Exception as error:
        report.update(status='FAIL', errorType=type(error).__name__)
        if previous and tag:
            try:
                run(['docker', 'image', 'tag', previous, tag])
                if replaced:
                    logged(COMPOSE + ['up', '-d', '--no-deps', 'event-state-service'], 'rollback.log', 180)
                    ready()
                report['rollback'] = 'PASS'
            except Exception:
                report['rollback'] = 'ACTION_REQUIRED'
    finally:
        (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        (output / 'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n'
            for p in sorted(output.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'))
        print(json.dumps({'status': report['status'], 'evidence': str(output / 'report.json')}))
    return 0 if report['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
