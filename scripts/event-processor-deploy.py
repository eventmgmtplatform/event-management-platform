#!/usr/bin/env python3
"""Deploy the local Processor with a schema backup, retained image and automatic image rollback."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ['docker', 'compose', '--env-file', str(ROOT / '.env'), '-f',
           str(ROOT / 'infrastructure/docker-compose.yml')]
IMAGE = 'event-management/event-processor:1.0.0'


def main():
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    output = ROOT / 'evidence/os-02-event-processor/deployment' / stamp
    output.mkdir(parents=True)
    report = {'status': 'RUNNING', 'scope': 'local deployment, no customer rule activation', 'checks': []}
    old_image = None
    replacement_started = False

    def run(args, data=None, timeout=120):
        result = subprocess.run(args, cwd=ROOT, input=data, capture_output=True, timeout=timeout, check=True)
        return result.stdout

    def logged(args, filename, timeout):
        with (output / filename).open('wb') as log:
            subprocess.run(args, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=timeout, check=True)

    def sql(query):
        return run(['docker', 'exec', '-i', 'event-postgres', 'sh', '-c',
                    'psql -X -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
                   query.encode()).decode().strip()

    def ready():
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                with urllib.request.urlopen('http://127.0.0.1:8082/health/ready', timeout=5) as response:
                    if response.status == 200:
                        return
            except Exception:
                pass
            time.sleep(2)
        raise RuntimeError('PROCESSOR_READINESS_TIMEOUT')

    try:
        report['sourceCommit'] = run(['git', 'rev-parse', 'HEAD']).decode().strip()
        old_image = run(['docker', 'inspect', '--format', '{{.Image}}', 'event-event-processor']).decode().strip()
        assert old_image.startswith('sha256:') and len(old_image) == 71
        report['previousImage'] = old_image
        rollback_tag = 'event-management/event-processor:rollback-' + old_image.split(':')[1][:12]
        run(['docker', 'image', 'tag', old_image, rollback_tag])
        report['rollbackTag'] = rollback_tag
        ready()
        backup = run(['docker', 'exec', 'event-postgres', 'sh', '-c',
                      'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom '
                      '--schema=event_processor --no-owner --no-privileges'])
        (output / 'before-migration.dump').write_bytes(backup)
        report['backupSha256'] = hashlib.sha256(backup).hexdigest()
        report['checks'].append('previous image retained; transactional schema backup captured')
        print('Building Processor image; evidence: ' + str(output), flush=True)
        logged(COMPOSE + ['build', 'event-processor'], 'image-build.log', 1200)
        migration = ROOT / 'infrastructure/postgres/init/011-processor-rule-registry.sql'
        report['migrationSha256'] = hashlib.sha256(migration.read_bytes()).hexdigest()
        sql(migration.read_text())
        for name, field in [('012-processor-administration.sql', 'adminMigrationSha256'),
                            ('013-processor-correlation.sql', 'correlationMigrationSha256'),
                            ('014-processor-command-ledger.sql', 'commandMigrationSha256')]:
            migration = ROOT / 'infrastructure/postgres/init' / name
            sql(migration.read_text())
            report[field] = hashlib.sha256(migration.read_bytes()).hexdigest()
        assert sql("SELECT count(*) FROM information_schema.tables WHERE table_schema='event_processor' "
                   "AND table_name IN ('rule_definition','rule_version','rule_change','admin_request','admin_audit','correlation_group','integration_command')") == '7'
        report['checks'].append('migrations 011/012/013/014 applied; configuration tables verified')
        report['activeRules'] = int(sql("SELECT count(*) FROM event_processor.rule_definition WHERE status='ENABLED'"))
        print('Migration applied; replacing only event-processor', flush=True)
        replacement_started = True
        logged(COMPOSE + ['up', '-d', '--no-deps', 'event-processor'], 'replace.log', 180)
        ready()
        report['deployedImage'] = run(['docker', 'inspect', '--format', '{{.Image}}', 'event-event-processor']).decode().strip()
        report['checks'].append('new image ready; existing Kafka consumer group retained')
        print('Running replay/restart/DLQ certification', flush=True)
        logged(['python3', str(ROOT / 'scripts/event-processor-certification.py')], 'runtime-certification.log', 600)
        ready()
        report['checks'].append('runtime replay, restart, ordered audit and sanitized DLQ passed')
        report['status'] = 'PASS'
    except Exception as error:
        report['status'] = 'FAIL'
        report['errorType'] = type(error).__name__
        if old_image:
            try:
                run(['docker', 'image', 'tag', old_image, IMAGE])
                if replacement_started:
                    logged(COMPOSE + ['up', '-d', '--no-deps', 'event-processor'], 'rollback.log', 180)
                    ready()
                report['rollback'] = 'PASS; image tag restored, additive schema and evidence retained'
            except Exception as rollback_error:
                report['rollback'] = 'FAIL'
                report['rollbackErrorType'] = type(rollback_error).__name__
        raise
    finally:
        (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        (output / 'SHA256SUMS').write_text(''.join(
            hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n'
            for path in sorted(output.iterdir()) if path.is_file() and path.name != 'SHA256SUMS'))
        print('Deployment evidence: ' + str(output), flush=True)


if __name__ == '__main__':
    main()
