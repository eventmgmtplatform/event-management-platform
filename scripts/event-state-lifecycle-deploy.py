#!/usr/bin/env python3
"""Deploy Processor/Worker/ESS locally, with pinned images and image rollback."""
import argparse
import shutil
import tempfile
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ['docker', 'compose', '--env-file', str(ROOT / '.env'), '-f', str(ROOT / 'infrastructure/docker-compose.yml')]
SERVICES = [('event-state-service', 'event-state-service', 8084),
            ('integration-worker', 'event-integration-worker', 8083),
            ('event-processor', 'event-event-processor', 8082)]
MIGRATIONS = ['016-ess-quarantine.sql', '017-ess-lifecycle.sql',
              '020-processor-lifecycle.sql', '021-worker-delivery-recovery.sql']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--offline-build', action='store_true', help='Build a temporary source snapshot with the local Maven cache; preserve mounted targets')
    parser.add_argument('--ess-only', action='store_true', help='Upgrade only ESS; preserve Worker/Processor runtime')
    args = parser.parse_args()
    services = SERVICES[:1] if args.ess_only else SERVICES
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    output = ROOT / 'evidences/os-05-ess/lifecycle-deployment' / stamp
    output.mkdir(parents=True)
    report = {'status': 'RUNNING', 'services': {}, 'migrations': {},
              'scope': 'Shared Processor/Worker/ESS upgrade; ESS lifecycle certification with ServiceNow mock. Full CACF/GNM orchestration remains certified in isolated OS_11.'}
    report['selectedServices']=[service for service, _, _ in services]
    changed = []

    def run(args, data=None, timeout=90):
        return subprocess.run(args, cwd=ROOT, input=data, capture_output=True, check=True, text=True, timeout=timeout).stdout.strip()

    def logged(args, name, timeout=1200):
        with (output / name).open('w') as stream:
            subprocess.run(args, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True, timeout=timeout)

    def ready(port):
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                with urllib.request.urlopen(f'http://127.0.0.1:{port}/health/ready', timeout=5) as response:
                    if json.load(response)['status'] == 'UP':
                        return
            except OSError:
                pass
            time.sleep(2)
        raise RuntimeError('READINESS_TIMEOUT_' + str(port))

    try:
        config = json.loads(run(COMPOSE + ['config', '--format', 'json']))
        run(['bash', 'scripts/emctl', 'validate'])
        report['head'] = run(['git', 'rev-parse', 'HEAD'])
        report['branch'] = run(['git', 'branch', '--show-current'])
        for service, container, port in services:
            previous = run(['docker', 'inspect', '--format', '{{.Image}}', container])
            tag = run(['docker', 'inspect', '--format', '{{.Config.Image}}', container])
            report['services'][service] = {'previousImage': previous, 'tag': tag}
            run(['docker', 'image', 'tag', previous, f'event-management/{service}:rollback-' + previous.split(':')[1][:12]])
        print('Building ' + ', '.join(service for service, _, _ in services) + '; evidence: ' + str(output), flush=True)
        if args.offline_build:
            with tempfile.TemporaryDirectory(prefix='ess-coordinated-build-') as directory:
                snapshot = Path(directory)
                shutil.copytree(ROOT / 'testing/services', snapshot / 'testing/services')
                shutil.copytree(ROOT / 'infrastructure/postgres/init', snapshot / 'infrastructure/postgres/init')
                for service, _, _ in services:
                    source = ROOT / 'services' / service
                    target = snapshot / 'services' / service
                    target.mkdir(parents=True)
                    shutil.copy2(source / 'pom.xml', target / 'pom.xml')
                    shutil.copytree(source / 'src', target / 'src')
                    with (output / (service + '-maven.log')).open('w') as stream:
                        subprocess.run(['mvn', '-B', '-ntp', '-o', 'package', '-Dmaven.test.skip=true'],
                            cwd=target, stdout=stream, stderr=subprocess.STDOUT, check=True, timeout=300)
                    if service == 'event-state-service':
                        with (output / 'ess-verify.log').open('w') as stream:
                            subprocess.run(['mvn', '-B', '-ntp', '-o', 'verify', '-DskipITs=false',
                                '-Dess.test.jdbc.url=jdbc:postgresql://127.0.0.1:15440/ess_test',
                                '-Dtesting.reportsDirectory=' + str(output / 'java')],
                                cwd=target, stdout=stream, stderr=subprocess.STDOUT, check=True, timeout=300)
                    original = (source / 'Dockerfile').read_text()
                    runtime = original[original.index('FROM eclipse-temurin:'):]
                    runtime = runtime.replace('COPY --from=build', 'COPY').replace('/workspace/target/', 'target/')
                    dockerfile = target / 'Dockerfile.runtime'
                    dockerfile.write_text(runtime)
                    tag = config['services'][service].get('image', config['name'] + '-' + service)
                    logged(['docker', 'build', '-f', str(dockerfile), '-t', tag, str(target)], service + '-build.log', 300)
        else:
            logged(COMPOSE + ['build', *[service for service, _, _ in services]], 'build.log')
        candidates = {}
        for service, _, _ in services:
            tag = config['services'][service].get('image', config['name'] + '-' + service)
            candidate = run(['docker', 'image', 'inspect', '--format', '{{.Id}}', tag])
            report['services'][service]['candidateImage'] = candidate
            candidates[service] = {'image': candidate}
        override = output / 'candidate.compose.json'
        override.write_text(json.dumps({'services': candidates}))
        deployment = COMPOSE + ['-f', str(override)]
        backup = subprocess.check_output(['docker', 'exec', 'event-postgres', 'sh', '-c',
            'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --schema=event_management --schema=event_processor --no-owner --no-privileges'], timeout=120)
        backup_file = output / 'before-migration.dump'
        backup_file.write_bytes(backup)
        backup_file.chmod(0o600)
        for name in (MIGRATIONS[:2] if args.ess_only else MIGRATIONS):
            migration = ROOT / 'infrastructure/postgres/init' / name
            report['migrations'][name] = hashlib.sha256(migration.read_bytes()).hexdigest()
            run(['docker', 'exec', '-i', 'event-postgres', 'sh', '-c',
                 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], migration.read_text())
        run(['docker', 'exec', 'event-kafka', '/opt/kafka/bin/kafka-topics.sh', '--bootstrap-server', 'kafka:29092',
             '--create', '--if-not-exists', '--topic', 'events.state.requested', '--partitions', '3', '--replication-factor', '1'])
        # Start the new consumer before enabling its producer. Retain existing groups and volumes.
        for service, container, port in services:
            changed.append((service, container, port))
            logged(deployment + ['up', '-d', '--no-deps', '--no-build', service], service + '-replace.log', 180)
            ready(port)
            report['services'][service]['deployedImage'] = run(['docker', 'inspect', '--format', '{{.Image}}', container])
            if report['services'][service]['deployedImage'] != report['services'][service]['candidateImage']:
                raise RuntimeError('IMAGE_MISMATCH_' + service)
        logged(['python3', 'scripts/event-state-certification.py', '--restart'], 'certification.log', 1200)
        report['status'] = 'PASS'
    except Exception as error:
        report.update(status='FAIL', errorType=type(error).__name__)
        report['rollback'] = {}
        for service, container, port in reversed(services):
            if service not in report['services']:
                continue
            try:
                info = report['services'][service]
                run(['docker', 'image', 'tag', info['previousImage'], info['tag']])
                if (service, container, port) in changed:
                    rollback = output / (service + '-rollback.compose.json')
                    rollback.write_text(json.dumps({'services': {service: {'image': info['previousImage']}}}))
                    logged(COMPOSE + ['-f', str(rollback), 'up', '-d', '--no-deps', '--no-build', service], service + '-rollback.log', 180)
                    ready(port)
                report['rollback'][service] = 'PASS'
            except Exception:
                report['rollback'][service] = 'ACTION_REQUIRED'
    finally:
        report['schemaRollback'] = 'Additive schema and committed records retained; no automatic data restore.'
        (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        (output / 'SHA256SUMS').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + str(path.relative_to(output)) + '\n'
            for path in sorted(output.rglob('*')) if path.is_file() and path.name != 'SHA256SUMS'))
        print(json.dumps({'status': report['status'], 'evidence': str(output / 'report.json')}))
    return 0 if report['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
