#!/usr/bin/env python3
"""Master local lifecycle + existing OS-01/OS-05 contract certification."""
import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
CLI = ROOT / 'scripts/emctl'


def run(args, *, runtime='local', timeout=600, capture=False):
    return subprocess.run([str(x) for x in args], cwd=ROOT,
                          env={**os.environ, 'EVENTMANAGEMENT_RUNTIME': runtime},
                          check=True, timeout=timeout, text=True,
                          stdout=subprocess.PIPE if capture else None)


def kafka(*args):
    return run(['docker', 'compose', '--env-file', ROOT / '.env', '-f',
                ROOT / 'infrastructure/docker-compose.yml', 'exec', '-T', 'kafka',
                *args], capture=True, timeout=45).stdout


def offsets():
    output = kafka('/opt/kafka/bin/kafka-get-offsets.sh', '--bootstrap-server',
                   'kafka:29092', '--topic', 'events.normalized', '--time', '-1')
    return {int(line.split(':')[1]): int(line.split(':')[2])
            for line in output.splitlines() if line.startswith('events.normalized:')}


def simulate():
    before = offsets()
    assert before, 'No partitions for events.normalized'
    fixture_dir = ROOT / 'services/event-gateway/test/events/sdc'
    node = 'service-test-' + uuid.uuid4().hex[:12]
    expected = {}
    for suffix, lifecycle in [('problem', 'OPEN'), ('recovery', 'CLOSE')]:
        data = json.loads((fixture_dir / f'zabbix-messagebus-{suffix}.json').read_text())
        data['Node'] = data['hostname'] = node
        request = urllib.request.Request('http://127.0.0.1:8081/api/v1/events',
                                         json.dumps(data).encode(),
                                         {'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=20) as response:
            assert response.status == 202
            result = json.load(response)
        assert result['accepted'] and result['topic'] == 'events.raw'
        expected[result['eventId']] = lifecycle
    found = {}
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline and len(found) < 2:
        for partition, end in offsets().items():
            start = before[partition]
            if end <= start:
                continue
            output = kafka('/opt/kafka/bin/kafka-console-consumer.sh',
                           '--bootstrap-server', 'kafka:29092', '--topic', 'events.normalized',
                           '--partition', str(partition), '--offset', str(start),
                           '--max-messages', str(end-start), '--timeout-ms', '15000')
            for line in output.splitlines():
                event = json.loads(line)
                if event.get('eventId') in expected:
                    assert event['lifecycleAction'] == expected[event['eventId']]
                    assert event['processing']['enrichment']['status'] == 'PENDING_RULES'
                    found[event['eventId']] = event
            before[partition] = end
        if len(found) < 2:
            time.sleep(1)
    assert len(found) == 2, 'OPEN/CLOSE missing from events.normalized'
    assert len({event['eventKey'] for event in found.values()}) == 1
    print('PASS gateway → Kafka raw → enrichment → normalized: OPEN/CLOSE, same eventKey', flush=True)
    return {'node': node, 'eventIds': list(found), 'eventKey': next(iter(found.values()))['eventKey']}


def main():
    output = ROOT / 'artifacts/service-administration' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    output.mkdir(parents=True)
    report = {'status': 'RUNNING', 'checks': [], 'limits': [
        'No automatic orchestrator from events.normalized to integration.commands (DP-13).',
        'CACF uses existing isolated certification; principal runtime CACF activation and state projection not certified (DP-22).']}
    try:
        # Validate both topologies before interrupting either runtime.
        for runtime in ('local', 'cacf-certification'):
            run(['bash', CLI, 'validate'], runtime=runtime)
        for runtime in ('local', 'cacf-certification'):
            for action in ('status', 'stop', 'status', 'start', 'health'):
                print(f'RUN {runtime}: {action}', flush=True)
                run(['bash', CLI, action], runtime=runtime)
                report['checks'].append(f'{runtime}:{action}')
        report['simulation'] = simulate()
        run(['python3', ROOT / 'scripts/cacf-local-certification.py'])
        report['checks'].append('cacf-existing-certification')
        for runtime in ('local', 'cacf-certification'):
            run(['bash', CLI, 'health'], runtime=runtime)
        report['status'] = 'PASS'
    except Exception as error:
        report['status'] = 'FAIL'
        report['error'] = str(error)
        raise
    finally:
        (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        print(f'Evidence: {output / "report.json"}', flush=True)


if __name__ == '__main__':
    main()
