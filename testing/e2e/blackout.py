"""UC-002: real Gateway/Kafka/Processor, versioned rule and durable observations."""
import datetime as dt
import json
import re
import subprocess
import time
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ['docker', 'compose', '--env-file', str(ROOT / '.env'), '-f', str(ROOT / 'infrastructure/docker-compose.yml')]


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def http(method, url, body=None, headers=None):
    request = urllib.request.Request(url, method=method,
        data=None if body is None else json.dumps(body).encode(),
        headers={'Content-Type': 'application/json', **(headers or {})})
    with urllib.request.urlopen(request, timeout=15) as response:
        data = response.read()
        return response.status, json.loads(data) if data else None, dict(response.headers)


def sql(query):
    # Only generated identifiers are interpolated by this scenario. Never accept user SQL.
    result = subprocess.run(COMPOSE + ['exec', '-T', 'postgres', 'sh', '-c',
        'psql -X -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
        input=query, capture_output=True, text=True, check=True, timeout=15)
    return result.stdout.strip()


def wait(fetch, label, timeout=60):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = fetch()
        if value:
            return value
        time.sleep(.5)
    raise AssertionError('Timeout: ' + label)


def run(output, report, capability="BLACKOUT"):
    require(capability in ("BLACKOUT", "SUPPRESSION"), "Unsupported capability")
    stage_name = "Blackout" if capability == "BLACKOUT" else "AutoSuppression"
    report["capability"] = capability
    tenant = 'testing-' + uuid.uuid4().hex[:16]
    node = tenant + '-node'
    rule_id = tenant + '-blackout'
    headers = {'X-Tenant-Id': tenant, 'X-Actor-Id': 'testing'}
    api = 'http://127.0.0.1:8082/api/v1'
    report.update(tenant=tenant, node=node, ruleId=rule_id, checks=[])
    now = dt.datetime.now(dt.timezone.utc)
    rule = {'id': rule_id, 'version': 1, 'type': 'SCHEDULED', 'enabled': True,
        'scope': {'customerCode': tenant, 'node': node},
        'schedule': {'timezone': 'America/Mexico_City',
            'validFrom': (now-dt.timedelta(minutes=1)).isoformat(),
            'validTo': (now+dt.timedelta(minutes=15)).isoformat()},
        'priority': 10, 'reason': 'UC-002 synthetic maintenance', 'metadata': {'owner': 'testing'}}
    if capability == 'SUPPRESSION':
        rule.update(type='SUPPRESSION', source='CHANGE', externalStatus='APPROVED')
        rule['metadata']['externalReference'] = 'synthetic-change'
    (output / 'rule.json').write_text(json.dumps(rule, indent=2)+'\n')
    created = []
    correlation_id = tenant + '-correlation'
    route_id = tenant + '-route'
    correlation = {'id': correlation_id, 'version': 1, 'enabled': True, 'priority': 10,
        'strategy': 'ATTRIBUTE', 'scope': {'field': 'resource.node', 'operator': 'EQ', 'value': node},
        'candidateSelection': {'windowSeconds': 300, 'maxCandidates': 8, 'activeOnly': True},
        'match': {'fields': ['resource.node']}, 'relationship': {'type': 'GROUP'},
        'metadata': {'owner': 'testing'}}
    route = {'id': route_id, 'version': 1, 'type': 'ROUTING', 'enabled': True, 'priority': 10,
        'condition': {'field': 'resource.node', 'operator': 'EQ', 'value': node},
        'actions': [{'type': 'CREATE_TICKET', 'target': 'SERVICENOW',
            'parameters': {'configuration': 'default', 'correlationRuleId': correlation_id}}],
        'metadata': {'owner': 'testing'}}

    def mutate(path, body, revision):
        return http('POST', api+path, body, {**headers,
            'Idempotency-Key': uuid.uuid4().hex, 'If-Match': f'"{revision}"'})

    def send(label, target, recovery=False):
        suffix = 'recovery' if recovery else 'problem'
        event = json.loads((ROOT / f'testing/fixtures/events/sdc/zabbix-messagebus-{suffix}.json').read_text())
        event.update(CustomerCode=tenant, Node=target, hostname=target)
        status, accepted, _ = http('POST', 'http://127.0.0.1:8081/api/v1/events', event)
        require(status == 202 and accepted['accepted'], 'Gateway did not accept event')
        event_id = accepted['eventId']
        require(re.fullmatch(r'[A-Za-z0-9_-]+', event_id) is not None, 'Unexpected eventId')
        row = wait(lambda: sql("SELECT evidence::text FROM event_processor.processing_record WHERE tenant='"+tenant+"' AND event_id='"+event_id+"'"), label)
        record = json.loads(row)
        processing_id = record['processingId']
        require(re.fullmatch(r'[a-f0-9]+', processing_id) is not None, 'Unexpected processingId')
        wait(lambda: sql("SELECT count(*) FROM event_processor.output_outbox WHERE processing_id='"+processing_id+"' AND topic='events.normalized' AND published_at IS NOT NULL") == '1', 'normalized output delivered')
        status, explained, _ = http('GET', api+'/explain/'+processing_id, headers=headers)
        require(status == 200 and explained == record, 'Explain differs from persisted decision')
        (output / (label+'.json')).write_text(json.dumps({'input': event, 'accepted': accepted, 'processing': record}, indent=2)+'\n')
        require(len(record['stages']) == 12, 'Incomplete processing stages')
        stage = next(s for s in record['stages'] if s['stage'] == stage_name)
        return record, stage

    try:
        require(mutate('/rules', {'rule': rule, 'reason': 'UC-002 setup'}, 0)[0] == 201, 'Rule registration failed')
        created.append(rule_id)
        require(mutate('/rules/'+rule_id+'/enable', {'version': 1, 'reason': 'UC-002 activation'}, 1)[0] == 200, 'Rule activation failed')
        for definition in (correlation, route):
            require(mutate('/rules', {'rule': definition, 'reason': 'UC-002 routing control'}, 0)[0] == 201, 'Control registration failed')
            created.append(definition['id'])
            require(mutate('/rules/'+definition['id']+'/enable', {'version': 1, 'reason': 'UC-002 routing control'}, 1)[0] == 200, 'Control activation failed')
        # Prove that the very same route would emit a ticket without a blackout.
        # Simulation has no outbox or provider side effects.
        event = {'schemaVersion': '1.1', 'eventId': uuid.uuid4().hex, 'eventKey': node,
            'tenant': {'code': tenant}, 'resource': {'name': node}, 'summary': 'Synthetic fatal',
            'lifecycleAction': 'OPEN', 'effectiveSeverity': 5,
            'timestamps': {'receivedAt': now.isoformat()}}
        _, baseline, _ = http('POST', api+'/simulations',
            {'event': event, 'candidateRules': [correlation, route]}, headers)
        require(len(baseline['candidates']) == 1, 'Routing control cannot generate a ticket without blackout')
        (output/'routing-control.json').write_text(json.dumps(baseline, indent=2)+'\n')
        report['checks'].append('eligible active ticket route; simulation without blackout produces one command')
        record, stage = send('fatal-in-blackout', node)
        require(record['directive'] == 'SUPPRESS_INTEGRATIONS' and stage['match'] == 'MATCH', 'Blackout did not suppress matching fatal')
        require(rule_id in json.dumps(stage['evidence']), 'Matched rule missing from audit')
        report['checks'].append('registered blackout suppresses fatal; exact rule audited; normalized output published')
        clear, stage = send('clear-in-blackout', node, recovery=True)
        require(clear['directive'] == 'SUPPRESS_INTEGRATIONS' and stage['match'] == 'MATCH', 'Clear bypassed blackout')
        require(clear['correlationApplied'], 'Clear skipped correlation/state intent')
        report['checks'].append('clear during blackout retains processing/audit')
        other, stage = send('other-node', node+'-outside')
        require(stage['match'] == 'NO_MATCH' and other['directive'] == 'CONTINUE', 'Blackout leaked outside node scope')
        # Zero commands is checked after committed processing and publication, not an arbitrary sleep.
        require(sql("SELECT count(*) FROM event_processor.integration_command WHERE tenant='"+tenant+"'") == '0', 'Unexpected integration command')
        require(sql("SELECT count(*) FROM event_processor.output_outbox o JOIN event_processor.processing_record p USING(processing_id) WHERE p.tenant='"+tenant+"' AND o.topic='integration.commands'") == '0', 'Unexpected integration outbox')
        require(mutate('/rules/'+route_id+'/disable', {'version': 1, 'reason': 'UC-002 prevent control ticket'}, 2)[0] == 200, 'Routing disable failed')
        require(mutate('/rules/'+rule_id+'/disable', {'version': 1, 'reason': 'UC-002 disable control'}, 2)[0] == 200, 'Disable failed')
        record, stage = send('disabled-blackout', node)
        require(record['directive'] == 'CONTINUE' and stage['match'] == 'NO_MATCH', 'Disabled blackout still suppresses')
        report['checks'].append('node scope and disabled-rule negative controls pass; no integration commands')
    finally:
        cleanup_errors = []
        for owned_id in reversed(created):
            try:
                # Read revision: cleanup works after partial setup and attempts every owned rule.
                _, current, response_headers = http('GET', api+'/rules/'+owned_id, headers=headers)
                if current['status'] == 'ENABLED':
                    revision = next(v for k,v in response_headers.items() if k.lower() == 'etag').strip('"')
                    require(mutate('/rules/'+owned_id+'/disable', {'version': 1, 'reason': 'UC-002 cleanup'}, revision)[0] == 200, 'Cleanup failed')
            except Exception as error:
                cleanup_errors.append(owned_id+': '+str(error))
        report['cleanup'] = {'rules': created, 'errors': cleanup_errors,
            'retention': 'synthetic events and audit retained under unique tenant'}
        require(not cleanup_errors, 'Cleanup incomplete: '+str(cleanup_errors))
