#!/usr/bin/env python3
"""Certify the local foundation using production Kafka/PG adapters; no provider calls."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ['docker', 'compose', '--env-file', str(ROOT / '.env'), '-f',
           str(ROOT / 'infrastructure/docker-compose.yml')]


def sql(query):
    result = subprocess.run(['docker', 'exec', '-i', 'event-postgres', 'sh', '-c',
                             'psql -X -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
                            input=query, text=True, capture_output=True, check=True, timeout=15)
    return result.stdout.strip()


def publish(body):
    subprocess.run(COMPOSE + ['exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-producer.sh',
                             '--bootstrap-server', 'kafka:29092', '--topic', 'events.raw'],
                   input=body + '\n', text=True, capture_output=True, check=True, timeout=30)


def wait(predicate, label):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(1)
    raise AssertionError(label)


def main():
    evidence = ROOT / 'evidences/os-02-event-processor/runtime' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    evidence.mkdir(parents=True)
    report = {'status': 'RUNNING', 'checks': []}
    try:
        event_id = str(uuid.uuid4())
        event = {'schemaVersion': '1.1', 'eventId': event_id, 'eventKey': 'processor-test:' + event_id,
                 'tenant': {'code': 'processor-test'}, 'lifecycleAction': 'OPEN', 'effectiveSeverity': 5,
                 'sourceSeverity': 5, 'timestamps': {'receivedAt': datetime.datetime.now(datetime.timezone.utc).isoformat()}}
        report['eventId'] = event_id
        body = json.dumps(event)
        publish(body)
        query = "SELECT count(*) FROM event_processor.processing_record WHERE event_id='" + event_id + "'"
        wait(lambda: sql(query) == '1', 'durable input record')
        publish(body)
        # A marker after replay in the same keyless single-producer stream alone is insufficient
        # across partitions; wait for source consumer lag to drain below before asserting counts.
        def group_drained():
            result = subprocess.run(COMPOSE + ['exec', '-T', 'kafka', '/opt/kafka/bin/kafka-consumer-groups.sh',
                         '--bootstrap-server', 'kafka:29092', '--group', 'enrichment-engine', '--describe'],
                         text=True, capture_output=True, check=True, timeout=15)
            lines = [line.split() for line in result.stdout.splitlines()
                     if line.strip().startswith('enrichment-engine') and 'events.raw' in line]
            return bool(lines) and all(line[5] == '0' for line in lines)
        wait(group_drained, 'source consumer drained after replay')
        assert sql(query) == '1'
        report['checks'].append('same-event replay: one durable processing record')
        published = ("SELECT count(*) FROM event_processor.output_outbox o JOIN event_processor.processing_record p "
                     "ON p.processing_id=o.processing_id WHERE p.event_id='" + event_id + "' AND o.published_at IS NOT NULL")
        wait(lambda: sql(published) == '1', 'normalized outbox delivered')
        report['checks'].append('one normalized output intent delivered')
        evidence_query = "SELECT evidence::text FROM event_processor.processing_record WHERE event_id='" + event_id + "'"
        record = json.loads(sql(evidence_query))
        assert len(record['stages']) == 12
        assert record['stages'][4]['evidence']['capabilityStatus'] == 'PENDING'
        report['checks'].append('12 ordered stages, pending dedup reported honestly')
        report['processingId'] = record['processingId']
        (evidence / 'processing-evidence.json').write_text(json.dumps(record, indent=2) + '\n')
        # Lifecycle restart must not cause a new durable decision/output for the same event.
        subprocess.run(['bash', ROOT / 'scripts/emctl', 'event-processor', 'restart'],
                       cwd=ROOT, check=True, timeout=300)
        publish(body)
        wait(group_drained, 'source consumer drained after restart/replay')
        assert sql(query) == '1' and sql(published) == '1'
        report['checks'].append('container restart/replay preserves record and output identity')
        for reason, bad_body in [('INVALID_GATEWAY_CONTRACT', 'invalid-' + event_id),
                                  ('EVENT_ID_COLLISION', json.dumps({**event, 'effectiveSeverity': 4}))]:
            digest = hashlib.sha256()
            for value in ('payload-v1', bad_body):
                data = value.encode()
                digest.update(len(data).to_bytes(4, 'big'))
                digest.update(data)
            publish(bad_body)
            dlq_query = ("SELECT count(*) FROM event_processor.output_outbox WHERE topic='events.dlq' "
                         "AND published_at IS NOT NULL AND payload->>'payloadHash'='" + digest.hexdigest()
                         + "' AND payload->>'errorCode'='" + reason + "'")
            wait(lambda: sql(dlq_query) == '1', reason + ' durable DLQ')
            report['checks'].append(reason + ': sanitized DLQ delivered')
        assert sql(query) == '1' and sql(published) == '1'
        report['status'] = 'PASS'
    except Exception as error:
        report['status'] = 'FAIL'
        report['errorType'] = type(error).__name__
        raise
    finally:
        (evidence / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        print('Evidence:', evidence, flush=True)


if __name__ == '__main__':
    main()
