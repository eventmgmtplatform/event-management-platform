#!/usr/bin/env python3
"""Repeatable local ESS health scenario. Fails closed; JSON evidence, no provider credentials."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = ROOT / 'testing/services/event-state-service/resources/health/event-service.json'
COMPOSE = ['docker', 'compose', '--env-file', str(ROOT / '.env'), '-f', str(ROOT / 'infrastructure/docker-compose.yml')]


def run(args, data=None, timeout=60, cwd=ROOT):
    return subprocess.run([str(x) for x in args], cwd=cwd, input=data, text=True,
                          capture_output=True, check=True, timeout=timeout,
                          env={**os.environ, 'EVENTMANAGEMENT_RUNTIME': 'local'}).stdout.strip()


def sql(query):
    return run(['docker', 'exec', '-i', 'event-postgres', 'sh', '-c',
                'psql -X -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], query)


def http(port, path, body=None, headers=None, expected=200):
    request = urllib.request.Request(f'http://127.0.0.1:{port}' + path,
        data=None if body is None else json.dumps(body).encode(),
        headers={'Content-Type': 'application/json', **(headers or {})})
    with urllib.request.urlopen(request, timeout=15) as response:
        if response.status != expected:
            raise AssertionError('Unexpected HTTP status')
        return json.load(response)


def wait(check, label, timeout=240):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if check():
            return
        time.sleep(1)
    raise AssertionError(label)


def publish(result, topic="integration.results"):
    run(COMPOSE + ['exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-producer.sh',
        '--bootstrap-server', 'kafka:29092', '--topic', topic,
        '--property', 'parse.key=true', '--property', 'key.separator=|'],
        result['eventKey'] + '|' + json.dumps(result) + '\n')


def drained(topic="integration.results", group="event-state-service"):
    output = run(COMPOSE + ['exec', '-T', 'kafka', '/opt/kafka/bin/kafka-consumer-groups.sh',
                 '--bootstrap-server', 'kafka:29092', '--group', group, '--describe'])
    rows = [line.split() for line in output.splitlines()
            if line.strip().startswith(group + ' ') and topic in line]
    return bool(rows) and all(row[5] == '0' for row in rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify', action='store_true', help='Run unit tests and isolated PostgreSQL/JTA integration tests first')
    parser.add_argument('--restart', action='store_true', help='Also restart ESS through emctl and replay the same result')
    args = parser.parse_args()
    template = json.loads(TEMPLATE.read_text())
    run_id = uuid.uuid4().hex
    tenant = 'ess-health-' + run_id[:12]
    output = ROOT / 'evidences/os-05-ess/runtime' / run_id
    output.mkdir(parents=True)
    report = {'status': 'RUNNING', 'runId': run_id, 'tenant': tenant, 'checks': [],
              'templateSha256': hashlib.sha256(TEMPLATE.read_bytes()).hexdigest(),
              'scope': template['scope'], 'targetChecksPending': template['targetChecksPending']}
    rules = []

    def admin(path, body=None, revision=None, expected=200):
        headers = {'X-Tenant-Id': tenant, 'Idempotency-Key': str(uuid.uuid4())}
        if revision is not None:
            headers['If-Match'] = '"' + str(revision) + '"'
        return http(8082, '/api/v1/rules' + path, body, headers, expected)

    def state(key):
        # Keys used here come from locally generated fixtures or a hash returned by the Processor.
        escaped = key.replace("'", "''")
        data = sql("SELECT row_to_json(s)::text FROM event_management.event_state s WHERE event_key='" + escaped + "'")
        return json.loads(data) if data else None

    def projected(key, version):
        try:
            doc = http(9200, '/events-current/_doc/' + urllib.parse.quote(key, safe=''))['_source']
            stored = state(key)
            return (stored is not None and doc['version'] == stored['version'] == version
                    and doc['tenant'] == stored['tenant'] and doc['eventKey'] == key
                    and doc['ticketNumber'] == stored['ticket_number']
                    and doc['notificationId'] == stored['notification_id']
                    and doc['integrations'] == stored['integration_state'])
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return False
            raise

    try:
        report['branch'] = run(['git', 'branch', '--show-current'])
        report['head'] = run(['git', 'rev-parse', 'HEAD'])
        report['dirty'] = bool(run(['git', 'status', '--porcelain']))
        if args.verify:
            build = run(['mvn', '-o', 'verify', '-DskipITs=false',
                         '-Dess.test.jdbc.url=jdbc:postgresql://127.0.0.1:15440/ess_test'],
                        timeout=240, cwd=ROOT / 'services/event-state-service')
            (output / 'maven.log').write_text(build + '\n')
            report['checks'].append('java-verification')
        report['image'] = run(['docker', 'inspect', '--format', '{{.Image}}', 'event-state-service'])
        run(['bash', ROOT / 'scripts/emctl', 'validate'])
        services = run(['bash', ROOT / 'scripts/emctl', 'services']).splitlines()
        if 'event-state-service' not in services:
            raise AssertionError('ESS_NOT_MANAGED')
        run(['bash', ROOT / 'scripts/emctl', 'event-state-service', 'health'])
        report['checks'].append('cli')
        def ready(port, path):
            try:
                return http(port, path)['status'] == 'UP'
            except (urllib.error.URLError, TimeoutError):
                return False
        for port, path in ((8081, '/api/v1/gateway'), (8082, '/health/ready'),
                           (8083, '/health/ready'), (8084, '/health/ready')):
            wait(lambda: ready(port, path), 'SERVICE_NOT_READY_' + str(port))
        report['checks'].append('readiness')
        # Verify the actual Worker endpoint is the local mock before enabling synthetic routes.
        actual = run(['docker', 'exec', 'event-integration-worker', 'printenv', 'SERVICENOW_BASE_URL'])
        if actual != 'http://servicenow-mock:8080':
            raise AssertionError('LOCAL_SERVICENOW_MOCK_REQUIRED')
        http(8181, '/__admin/mappings')
        correlation = {'id': 'ess-health-correlation', 'version': 1, 'enabled': True, 'priority': 10,
            'strategy': 'ATTRIBUTE', 'scope': {'field': 'resource.node', 'operator': 'EXISTS'},
            'candidateSelection': {'windowSeconds': 60, 'maxCandidates': 4, 'activeOnly': True},
            'match': {'fields': ['resource.node']}, 'relationship': {'type': 'GROUP'},
            'metadata': {'owner': 'ess-health'}}
        route = {'id': 'ess-health-route', 'version': 1, 'type': 'ROUTING', 'enabled': True, 'priority': 10,
            'condition': {'field': 'event.severity', 'operator': 'GTE', 'value': 2},
            'actions': [{'type': 'CREATE_TICKET', 'target': 'SERVICENOW',
                'parameters': {'configuration': 'default', 'correlationRuleId': correlation['id']}}],
            'metadata': {'owner': 'ess-health'}}
        for rule in (correlation, route):
            receipt = admin('', {'rule': rule, 'reason': 'ESS synthetic health'}, 0, 201)
            rules.append([rule['id'], receipt['revision']])
            receipt = admin('/' + rule['id'] + '/enable', {'version': 1, 'reason': 'ESS synthetic health'}, receipt['revision'])
            rules[-1][1] = receipt['revision']
        event = json.loads((ROOT / 'testing/fixtures/events/sdc/zabbix-messagebus-problem.json').read_text())
        event.update(CustomerCode=tenant, Node=tenant, hostname=tenant, AlertKey='ess-health', InstanceId='disk-test')
        response = http(8081, '/api/v1/events', event, expected=202)
        if not response.get('accepted'):
            raise AssertionError('GATEWAY_REJECTED')
        report['sourceEventId'] = response['eventId']
        request_query = ("SELECT payload::text FROM event_processor.output_outbox WHERE topic='events.state.requested' "
                         "AND payload->>'eventId'='" + response['eventId'].replace("'", "''") + "'")
        wait(lambda: bool(sql(request_query)), 'Processor did not emit a state request')
        opening = json.loads(sql(request_query))
        source_key = opening['eventKey']
        report['sourceEventKey'] = source_key
        wait(lambda: projected(source_key, 1), 'Source OPEN missing from state/projection')
        query = "SELECT envelope::text FROM event_processor.integration_command WHERE tenant='" + tenant + "'"
        wait(lambda: bool(sql(query)), 'Processor did not emit a command')
        command = json.loads(sql(query))
        key = command['eventKey']
        report['eventKey'] = key
        report['commandId'] = command['commandId']
        wait(lambda: state(key) is not None, 'ESS did not persist Worker result')
        wait(lambda: projected(key, 1), 'Search does not match PostgreSQL version 1')
        first = state(key)
        if first['servicenow_status'] != 'SUCCESS' or not first['ticket_number']:
            raise AssertionError('WORKER_DID_NOT_CREATE_TICKET')
        if first['last_result']['commandId'] != command['commandId']:
            raise AssertionError('COMMAND_RESULT_TRACE_MISMATCH')
        result = first['last_result']
        report['resultId'] = result['resultId']
        report['checks'].append('gateway-processor-worker-state-search')
        publish(result)
        wait(drained, 'ESS replay lag did not drain')
        if state(key) != first:
            raise AssertionError('REDELIVERY_MUTATED_STATE')
        report['checks'].append('identical-redelivery')
        second = {**template['result'], 'eventKey': key, 'eventId': first['event_id'], 'tenant': tenant,
                  'resultId': 'ess-health-' + run_id, 'integrationType': 'GNM',
                  'operation': 'SEND_NOTIFICATION', 'externalId': 'GNM-' + run_id[:12]}
        publish(second)
        wait(lambda: projected(key, 2), 'Second integration missing from state/projection')
        consolidated = state(key)
        if consolidated['ticket_number'] != first['ticket_number'] or consolidated['notification_id'] != second['externalId']:
            raise AssertionError('INTEGRATION_MERGE_FAILED')
        report['checks'].append('second-integration')
        invalid = {**second, 'probe': run_id}
        del invalid['resultId']
        digest = hashlib.sha256(json.dumps(invalid).encode()).hexdigest()
        publish(invalid)
        wait(lambda: sql("SELECT count(*) FROM event_management.ess_quarantine WHERE payload_hash='" + digest + "'") == '1',
             'Invalid message was not durably quarantined')
        wait(drained, 'Lag did not drain after quarantine')
        if state(key) != consolidated:
            raise AssertionError('QUARANTINE_MUTATED_STATE')
        report['checks'].append('invalid-message-quarantine')
        # Exercise the source lifecycle independently of the correlation situation key.
        source_first = state(source_key)
        recovery = {**event, 'Type': '0', 'InstanceValue': '1'}
        http(8081, '/api/v1/events', recovery, expected=202)
        wait(lambda: projected(source_key, 2), 'Source CLOSE missing from state/projection')
        closed = state(source_key)
        if (closed['lifecycle_status'] != 'CLOSED' or closed['effective_severity'] != 0
                or closed['source_severity'] != source_first['source_severity'] or closed['tally'] != 1):
            raise AssertionError('CLOSE_SEVERITY_OR_TALLY_INVALID')
        http(8081, '/api/v1/events', event, expected=202)
        wait(lambda: projected(source_key, 3), 'Source REOPEN missing from state/projection')
        reopened = state(source_key)
        if (reopened['lifecycle_status'] != 'OPEN' or reopened['tally'] != 2
                or reopened['event_id'] != source_first['event_id']):
            raise AssertionError('REOPEN_IDENTITY_OR_TALLY_INVALID')
        document = http(9200, '/events-current/_doc/' + urllib.parse.quote(source_key, safe=''))['_source']
        if document['tally'] != 2 or document['effectiveSeverity'] != reopened['effective_severity']:
            raise AssertionError('LIFECYCLE_PROJECTION_FIELDS_INVALID')
        escaped_source = source_key.replace("'", "''")
        history_query = "SELECT count(*) FROM event_management.ess_event_transition WHERE event_key='" + escaped_source + "'"
        if sql(history_query) != '3':
            raise AssertionError('LIFECYCLE_HISTORY_INVALID')
        report['checks'].append('lifecycle-open-close-reopen')
        publish(opening, 'events.state.requested')
        stale = {**opening, 'messageId': 'stale-' + run_id}
        publish(stale, 'events.state.requested')
        wait(lambda: drained('events.state.requested', 'event-state-service-lifecycle'), 'Lifecycle replay lag')
        if state(source_key) != reopened or sql(history_query) != '3':
            raise AssertionError('LIFECYCLE_REPLAY_MUTATED_STATE')
        if sql("SELECT disposition FROM event_management.ess_state_request WHERE message_id='" + stale['messageId'] + "'") != 'STALE':
            raise AssertionError('STALE_REQUEST_NOT_RECORDED')
        report['checks'].append('lifecycle-replay-stale')
        if args.restart:
            run(['bash', ROOT / 'scripts/emctl', 'event-state-service', 'restart'], timeout=360)
            publish(result)
            wait(drained, 'ESS replay after restart did not drain')
            wait(lambda: projected(key, 2), 'Projection after restart is stale')
            if state(key) != consolidated:
                raise AssertionError('RESTART_REPLAY_MUTATED_STATE')
            publish(opening, 'events.state.requested')
            wait(lambda: drained('events.state.requested', 'event-state-service-lifecycle'), 'Lifecycle restart replay lag')
            if state(source_key) != reopened:
                raise AssertionError('LIFECYCLE_RESTART_MUTATED_STATE')
            report['checks'].append('restart-redelivery')
        else:
            report['notRun'] = ['restart-redelivery (use --restart)']
        required = set(template['requiredChecks'])
        if not args.restart:
            required.discard('restart-redelivery')
        if not required.issubset(report['checks']):
            raise AssertionError('TEMPLATE_CHECKS_INCOMPLETE')
        report['status'] = 'PASS'
    except Exception as error:
        report['status'] = 'FAIL'
        report['errorType'] = type(error).__name__
        if isinstance(error, urllib.error.HTTPError):
            report['httpStatus'] = error.code
            report['httpPath'] = urllib.parse.urlsplit(error.url).path
            try:
                detail = json.load(error)
                report['httpErrorCode'] = detail.get('errorCode', detail.get('code', 'UNSPECIFIED'))
            except (ValueError, AttributeError):
                pass
        # Do not dump provider responses, environment, or command stderr into the report.
        if isinstance(error, AssertionError):
            report['error'] = str(error)
    finally:
        report['cleanup'] = []
        for rule_id, revision in reversed(rules):
            try:
                current = admin('/' + rule_id)
                if current['status'] == 'ENABLED':
                    receipt = admin('/' + rule_id + '/disable', {'version': 1, 'reason': 'ESS health cleanup'}, current['revision'])
                    revision = receipt['revision']
                else:
                    revision = current['revision']
                admin('/' + rule_id + '/retire', {'version': 1, 'reason': 'ESS health cleanup'}, revision)
                report['cleanup'].append(rule_id + ': retired')
            except Exception:
                report['status'] = 'FAIL'
                report['cleanup'].append(rule_id + ': ACTION_REQUIRED')
        report['fixtureRetention'] = 'Synthetic event, mock ticket and audit retained under unique tenant; no shared data deleted.'
        (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        (output / 'SHA256SUMS').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest()
            + '  ' + path.name + '\n' for path in sorted(output.iterdir()) if path.name != 'SHA256SUMS'))
        print(json.dumps({'status': report['status'], 'checks': report['checks'], 'evidence': str(output / 'report.json')}))
    return 0 if report['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
