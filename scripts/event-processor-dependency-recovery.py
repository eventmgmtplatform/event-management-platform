#!/usr/bin/env python3
"""Verify required-DB outage against the local production pipeline, always restoring DB."""
import datetime
import importlib.util
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('processor_certification', ROOT / 'scripts/event-processor-certification.py')
cert = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cert)


def health(probe):
    try:
        with urllib.request.urlopen('http://127.0.0.1:8082/health/' + probe, timeout=12) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def lag():
    result = subprocess.run(cert.COMPOSE + ['exec', '-T', 'kafka', '/opt/kafka/bin/kafka-consumer-groups.sh',
                            '--bootstrap-server', 'kafka:29092', '--group', 'enrichment-engine', '--describe'],
                            text=True, capture_output=True, check=True, timeout=20)
    rows = [line.split() for line in result.stdout.splitlines()
            if line.strip().startswith('enrichment-engine') and 'events.raw' in line]
    assert rows and all(row[5].isdigit() for row in rows), 'Consumer lag unavailable'
    return sum(int(row[5]) for row in rows)


def main():
    output = ROOT / 'evidence/os-02-event-processor/recovery' / datetime.datetime.now(datetime.timezone.utc).strftime('db-outage-%Y%m%dT%H%M%SZ')
    output.mkdir(parents=True)
    report = {'status': 'RUNNING', 'checks': []}
    restore_needed = False
    try:
        assert health('ready') == 200
        cert.wait(lambda: lag() == 0, 'consumer drained before outage')
        event_id = str(uuid.uuid4())
        report['eventId'] = event_id
        event = {'schemaVersion': '1.1', 'eventId': event_id, 'eventKey': 'db-recovery:' + event_id,
                 'tenant': {'code': 'processor-test'}, 'lifecycleAction': 'OPEN', 'effectiveSeverity': 5,
                 'sourceSeverity': 5, 'timestamps': {'receivedAt': datetime.datetime.now(datetime.timezone.utc).isoformat()}}
        restore_needed = True
        subprocess.run(['docker', 'stop', '--time', '30', 'event-postgres'], check=True, timeout=60)
        cert.publish(json.dumps(event))
        # Exceed acquisition timeout twice; verify the source offset was not prematurely acknowledged.
        time.sleep(12)
        report['duringOutage'] = {'live': health('live'), 'ready': health('ready'), 'consumerLag': lag()}
        assert report['duringOutage']['live'] == 200
        assert report['duringOutage']['ready'] == 503
        assert report['duringOutage']['consumerLag'] > 0
        report['checks'].append('DB outage: liveness up, readiness down, source offset unacknowledged')
    except Exception as error:
        report['status'] = 'FAIL'
        report['errorType'] = type(error).__name__
        raise
    finally:
        try:
            if restore_needed:
                subprocess.run(['bash', ROOT / 'scripts/emctl', 'postgres', 'start'], cwd=ROOT,
                               check=True, timeout=300)
                report['databaseRestored'] = True
        except Exception as error:
            report['status'] = 'FAIL'
            report['restoreErrorType'] = type(error).__name__
            raise
        finally:
            (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
            print('Evidence:', output, flush=True)
    try:
        cert.wait(lambda: health('ready') == 200, 'processor ready after DB recovery')
        query = ("SELECT count(*) FROM event_processor.processing_record p JOIN event_processor.output_outbox o "
                 "ON o.processing_id=p.processing_id WHERE p.event_id='" + event_id + "' AND o.published_at IS NOT NULL")
        cert.wait(lambda: cert.sql(query) == '1', 'pending event processed after DB recovery')
        cert.wait(lambda: lag() == 0, 'source offset acknowledged after durable recovery')
        report['checks'].append('DB restored: exactly one durable output delivered and consumer drained')
        report['status'] = 'PASS'
    except Exception as error:
        report['status'] = 'FAIL'
        report['errorType'] = type(error).__name__
        raise
    finally:
        (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        print('Evidence:', output, flush=True)


if __name__ == '__main__':
    main()
