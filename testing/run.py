#!/usr/bin/env python3
"""Single testing entry point. Exit 0=pass, 1=fail, 2=blocked/incomplete."""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ('event-gateway', 'event-processor', 'integration-worker', 'event-state-service')


def module(path):
    spec = importlib.util.spec_from_file_location(path.stem.replace('-', '_'), path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('suite', choices=['list', 'unit', 'glpi', 'glpi-e2e', 'glpi-resolve-e2e', 'glpi-close-e2e', 'glpi-followup-e2e', 'console', 'dashboards', 'dashboards-integration', 'harness', 'blackout', 'happy-path', 'cacf-remediated', 'certification'])
    parser.add_argument('--service', choices=SERVICES)
    parser.add_argument('--name', choices=[p.stem for p in (ROOT/'testing/certifications').glob('*.py')])
    parser.add_argument('--runtime', choices=['os11','shared'], default='shared', help='Target for happy-path; os11 requires explicit laboratory recovery')
    parser.add_argument('--restart', action='store_true', help='Restart shared consumers after NEXT submission during happy-path')
    args = parser.parse_args()
    if args.restart and (args.suite != 'happy-path' or args.runtime != 'shared'):
        parser.error('--restart requires happy-path --runtime shared')
    if sys.flags.optimize:
        parser.error("Do not use -O/PYTHONOPTIMIZE: legacy certifications require assertions")
    catalog = json.loads((ROOT/'testing/cases/catalog.json').read_text())
    if args.suite == 'list':
        print(json.dumps(catalog, ensure_ascii=False, indent=2))
        return 0
    if args.suite == 'certification' and not args.name:
        parser.error('certification requires --name')
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'-'+uuid.uuid4().hex[:8]
    output = ROOT/'evidences/testing'/stamp/args.suite
    output.mkdir(parents=True)
    report = {'suite': args.suite, 'runId': stamp, 'status': 'RUNNING', 'startedAt': dt.datetime.now(dt.timezone.utc).isoformat()}
    code = 1
    try:
        if args.suite == 'happy-path':
            def checkpoint(stage, identities):
                report['restart']={'stage':stage,'identities':identities}
                for service in ['event-processor','integration-worker','event-state-service']:
                    subprocess.run(['bash',str(ROOT/'scripts/emctl'),service,'restart'],check=True,
                                   stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=360)
            module(ROOT/'testing/e2e/happy_path.py').run(output, report,
                checkpoint=checkpoint if args.restart else None, runtime=args.runtime)
            report['status'], code = 'PASS', 0
        elif args.suite == 'blackout':
            module(ROOT/'testing/e2e/blackout.py').run(output, report)
            report['status'], code = 'PASS', 0
        elif args.suite == 'cacf-remediated':
            report['scope']='UC-003 compatibility command: executes the shared UC-001 flow including CACF success and duplicate callbacks'
            module(ROOT/'testing/e2e/happy_path.py').run(output, report, runtime='shared')
            report['status'], code = 'PASS', 0

        else:
            report['commands'] = []
            commands = []
            if args.suite == 'unit':
                for service in ([args.service] if args.service else SERVICES):
                    commands.append((service, ['mvn', '-B', '-ntp', '-o', 'clean', 'test',
                        '-DfailIfNoTests=true', '-Dtesting.reportsDirectory='+str(output/service)], ROOT/'services'/service))
            elif args.suite == 'glpi-resolve-e2e':
                commands.append(('glpi-resolve-e2e', [sys.executable, str(ROOT / 'testing/e2e/glpi_resolve.py')], ROOT / 'testing/e2e'))
            elif args.suite == 'glpi-close-e2e':
                commands.append(('glpi-close-e2e', [sys.executable, str(ROOT / 'testing/e2e/glpi_close.py')], ROOT / 'testing/e2e'))
            elif args.suite == 'glpi-followup-e2e':
                commands.append(('glpi-followup-e2e', [sys.executable, str(ROOT / 'testing/e2e/glpi_followup.py')], ROOT / 'testing/e2e'))
            elif args.suite == 'glpi-e2e':
                commands.append(('glpi-create-e2e', [sys.executable, str(ROOT / 'testing/e2e/glpi_create.py')], ROOT / 'testing/e2e'))
            elif args.suite == 'glpi':
                commands.append(('glpi-api', [sys.executable, '-m', 'unittest', 'discover', '-s', 'testing/services/glpi-ticketing-api', '-v'], ROOT))
                commands.append(('glpi-worker', ['mvn', '-o', 'test', '-Dtest=GlpiProcessorTest'], ROOT/'services/integration-worker'))
                commands.append(('glpi-ui', ['node', '--test', str(ROOT/'testing/services/event-management-console/glpi-ticketing.test.mjs')], ROOT))
            elif args.suite == 'console':
                commands.append(('console', ['node', '--test', str(ROOT/'testing/services/event-management-console/platform.test.mjs')], ROOT/'services/event-management-console'))
            elif args.suite == 'dashboards':
                commands.append(('dashboard-contracts', [sys.executable, str(ROOT/'testing/services/oem-dashboards/test_dashboard.py'), 'ContractTests', '-v'], ROOT))
                commands.append(('dashboard-ui', ['node', '--test', str(ROOT/'testing/services/oem-dashboards/data.test.mjs'), str(ROOT/'testing/services/oem-dashboards/delivery.test.mjs'), str(ROOT/'testing/services/oem-dashboards/uuid.test.mjs')], ROOT/'services/oem-dashboards'))
            elif args.suite == 'dashboards-integration':
                commands.append(('dashboard-postgres', [sys.executable, str(ROOT/'testing/services/oem-dashboards/integration.py')], ROOT))
            elif args.suite == 'harness':
                commands.append(('harness', [sys.executable, '-m', 'unittest', 'discover', '-s', 'testing/harness', '-v'], ROOT))
            else:
                commands.append((args.name, [sys.executable, str(ROOT/'testing/certifications'/(args.name+'.py'))], ROOT))
            failed = False
            for name, command, cwd in commands:
                print('Running '+name, flush=True)
                with (output/(name+'.log')).open('w') as log:
                    result = subprocess.run(command, cwd=cwd, stdout=log, stderr=subprocess.STDOUT, timeout=900)
                report['commands'].append({'name': name, 'exitCode': result.returncode})
                failed |= result.returncode != 0
            totals = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
            for path in output.rglob('TEST-*.xml'):
                suite = ET.parse(path).getroot()
                for key in totals: totals[key] += int(suite.attrib.get(key, 0))
            if args.suite == 'unit': report['totals'] = totals
            report['status'] = 'FAIL' if failed else 'PASS_WITH_SKIPS' if totals['skipped'] else 'PASS'
            code = 1 if failed else 0
    except Exception as error:
        report.update(status='FAIL', errorType=type(error).__name__, error=str(error))
        code = 1
    finally:
        report['finishedAt'] = dt.datetime.now(dt.timezone.utc).isoformat()
        (output/'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
        checksums = [hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(output)) for p in sorted(output.rglob('*')) if p.is_file()]
        (output/'SHA256SUMS').write_text('\n'.join(checksums)+'\n')
        print(report['status']+': '+str(output/'report.json'), flush=True)
    return code


if __name__ == '__main__':
    sys.exit(main())
