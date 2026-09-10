#!/usr/bin/env python3
"""Base routing CRUD and real command delivery to the local ServiceNow mock."""
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import urllib.error
import uuid

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('shared', ROOT/'testing/e2e/blackout.py')
s = importlib.util.module_from_spec(spec); spec.loader.exec_module(s)


def main():
    tenant = 'routing-cert-'+uuid.uuid4().hex[:16]
    output = ROOT/'evidences/routing'/tenant; output.mkdir(parents=True)
    api = 'http://127.0.0.1:8082/api/v1'
    mock = 'http://127.0.0.1:8181'
    headers = {'X-Tenant-Id': tenant, 'X-Actor-Id': 'routing-certification'}
    owned = []; mapping = None; exchanges = []
    report = {'status': 'RUNNING', 'tenant': tenant, 'checks': []}
    group = {'id': 'group', 'version': 1, 'enabled': True, 'priority': 10,
             'strategy': 'ATTRIBUTE', 'scope': {'field': 'resource.node', 'operator': 'EQ', 'value': tenant},
             'candidateSelection': {'windowSeconds': 3600, 'maxCandidates': 8, 'activeOnly': True},
             'match': {'fields': ['resource.node']}, 'relationship': {'type': 'GROUP'}, 'metadata': {'owner': 'testing'}}

    def route(version=1):
        return {'id': 'route', 'version': version, 'type': 'ROUTING', 'enabled': True, 'priority': 10,
                'condition': {'field': 'resource.node', 'operator': 'EQ', 'value': tenant},
                'actions': [{'type': 'CREATE_TICKET', 'target': 'SERVICENOW',
                             'parameters': {'configuration': 'default', 'correlationRuleId': 'group'}}],
                'metadata': {'owner': 'testing'}}

    def call(method, path, body=None, revision=None, key=None, expected=200, customer=None):
        h = {**headers}
        if customer: h['X-Tenant-Id'] = customer
        if revision is not None: h['If-Match'] = '"'+str(revision)+'"'
        if key: h['Idempotency-Key'] = key
        try: code, data, _ = s.http(method, api+path, body, h)
        except urllib.error.HTTPError as error: code, data = error.code, json.load(error)
        exchanges.append({'method': method, 'path': path, 'request': body, 'status': code, 'response': data})
        assert code == expected, (path, expected, code, data)
        return data

    def save(rule, revision):
        body = {'rule': rule, 'reason': 'Synthetic routing acceptance'}; key = uuid.uuid4().hex
        result = call('POST', '/rules', body, revision, key, 201)
        if rule['id'] not in owned: owned.append(rule['id'])
        assert call('POST', '/rules', body, revision, key, 201) == result

    def change(id, action, version, revision, expected=200):
        return call('POST', '/rules/'+id+'/'+action, {'version': version, 'reason': 'Synthetic acceptance'}, revision, uuid.uuid4().hex, expected)

    def simulate(candidates=None, recovery=False):
        event = {'schemaVersion': '1.1', 'eventId': uuid.uuid4().hex, 'eventKey': 'synthetic',
                 'tenant': {'code': tenant}, 'resource': {'name': tenant}, 'summary': 'Synthetic routing',
                 'lifecycleAction': 'CLOSE' if recovery else 'OPEN', 'effectiveSeverity': 0 if recovery else 3,
                 'timestamps': {'receivedAt': dt.datetime.now(dt.timezone.utc).isoformat()}}
        body = {'event': event}
        if candidates is not None: body['candidateRules'] = candidates
        return call('POST', '/simulations', body)

    def commands():
        return json.loads(s.sql("SELECT COALESCE(jsonb_agg(envelope),'[]'::jsonb)::text FROM event_processor.integration_command WHERE tenant='"+tenant+"'"))

    def send(label, key, recovery=False):
        suffix = 'recovery' if recovery else 'problem'
        raw = json.loads((ROOT/f'testing/fixtures/events/sdc/zabbix-messagebus-{suffix}.json').read_text())
        raw.update(CustomerCode=tenant, Node=tenant, hostname=tenant, AlertKey=key, InstanceSituation=key, InstanceId=key)
        code, accepted, _ = s.http('POST', 'http://127.0.0.1:8081/api/v1/events', raw)
        assert code == 202 and accepted['accepted']
        eid = accepted['eventId']; assert re.fullmatch(r'[A-Za-z0-9_-]+', eid)
        record = json.loads(s.wait(lambda: s.sql("SELECT evidence::text FROM event_processor.processing_record WHERE tenant='"+tenant+"' AND event_id='"+eid+"'"), label))
        pid = record['processingId']; assert re.fullmatch(r'[a-f0-9]+', pid)
        s.wait(lambda: s.sql("SELECT count(*) FROM event_processor.output_outbox WHERE processing_id='"+pid+"' AND topic='events.normalized' AND published_at IS NOT NULL") == '1', 'normalized publication')
        assert call('GET', '/explain/'+pid) == record
        (output/(label+'.json')).write_text(json.dumps({'input': raw, 'accepted': accepted, 'processing': record}, indent=2)+'\n')
        return record

    try:
        inspected = json.loads(subprocess.run(['docker', 'inspect', 'event-integration-worker'], capture_output=True, text=True, check=True).stdout)[0]
        environment = dict(item.split('=', 1) for item in inspected['Config']['Env'] if '=' in item)
        assert environment.get('SERVICENOW_BASE_URL') == 'http://servicenow-mock:8080', 'Local mock required'
        mapping = str(uuid.uuid4()); ticket = 'INC'+uuid.uuid4().hex[:12].upper()
        s.http('POST', mock+'/__admin/mappings', {'id': mapping, 'priority': 1,
               'request': {'method': 'POST', 'urlPath': '/api/now/table/incident', 'bodyPatterns': [{'contains': tenant}]},
               'response': {'status': 201, 'headers': {'Content-Type': 'application/json'},
                            'jsonBody': {'result': {'number': ticket, 'sys_id': uuid.uuid4().hex, 'state': '1'}}}})
        save(group, 0); change('group', 'enable', 1, 1)
        call('POST', '/rules/validate', {'rule': route()}); save(route(), 0)
        assert not simulate()['candidates']
        change('route', 'enable', 1, 1)
        active = simulate(); assert len(active['candidates']) == 1
        duplicate = route(); duplicate['id'] = 'duplicate'
        assert len(simulate([group, route(), duplicate])['candidates']) == 1
        no_group = simulate([route()]); assert not no_group['candidates']
        assert no_group['routing']['decisions'][0]['reason'] == 'NO_CORRELATION_CYCLE'
        assert not simulate(recovery=True)['candidates']
        call('GET', '/rules/route', customer=tenant+'-other', expected=404)
        for field, value in [('type', 'CLOSE_TICKET'), ('target', 'GNM'), ('parameters', {'configuration': 'unknown', 'correlationRuleId': 'group'})]:
            invalid = route(); invalid['id'] = 'invalid-'+field; invalid['actions'][0][field] = value
            call('POST', '/rules', {'rule': invalid, 'reason': 'Invalid fixture'}, 0, uuid.uuid4().hex, 422)
            call('GET', '/rules/'+invalid['id'], expected=404)
        assert commands() == [], 'Simulation created durable command'
        first = send('first-command', tenant+'-a')
        assert first['directive'] == 'GENERATE_COMMANDS'
        original = commands(); assert len(original) == 1
        envelope = original[0]; cid = envelope['commandId']; assert re.fullmatch(r'[a-f0-9]+', cid)
        assert envelope['eventId'] == first['correlation']['decisions'][0]['group']['groupId']
        assert envelope['metadata']['sourceEventId'] == first['eventId']
        result = json.loads(s.wait(lambda: s.sql("SELECT result_payload::text FROM event_management.integration_command_execution WHERE command_id='"+cid+"' AND execution_status='COMPLETED' AND result_published_at IS NOT NULL"), 'Worker completed and published result'))
        (output/'worker-result.json').write_text(json.dumps(result, indent=2)+'\n')
        assert result['status'] == 'SUCCESS' and result['commandId'] == cid
        assert result['ticketNumber'] == ticket and result['httpStatus'] == 201
        s.wait(lambda: s.sql("SELECT count(*) FROM event_processor.output_outbox WHERE message_id='"+cid+"' AND topic='integration.commands' AND published_at IS NOT NULL") == '1', 'command publication')
        save(route(2), 2)
        current = call('GET', '/rules/route'); assert current['activeVersion'] == 1 and current['latestVersion'] == 2
        change('route', 'enable', 2, 2, 409); change('route', 'enable', 2, 3)
        second = send('same-cycle-v2', tenant+'-b')
        assert second['routing']['decisions'][0]['reason'] == 'EXISTING_SEMANTIC_COMMAND'
        assert second['routing']['decisions'][0]['ruleVersion'] == 2
        assert commands() == original, 'First envelope changed or command duplicated'
        send('recover-first', tenant+'-a', True); send('recover-second', tenant+'-b', True)
        assert commands() == original, 'Base route created recovery command'
        change('route', 'disable', 2, 4)
        disabled = send('disabled-new-cycle', tenant+'-c')
        assert disabled['correlation']['decisions'][0]['reason'] == 'NEW_CYCLE'
        assert not disabled['routing']['decisions'] and commands() == original
        change('route', 'retire', 2, 5); change('route', 'enable', 2, 6, 409)
        assert len(call('GET', '/rules/route/history')['items']) == 6
        _, journal, _ = s.http('GET', mock+'/__admin/requests')
        requests = [r for r in journal['requests'] if r['request']['method'] == 'POST' and tenant in r['request'].get('body', '')]
        assert len(requests) == 1, 'Expected one provider create'
        (output/'mock-requests.json').write_text(json.dumps(requests, indent=2)+'\n')
        (output/'command.json').write_text(json.dumps(envelope, indent=2)+'\n')
        report['checks'] = ['Versioned REST: validate/create/retry, active/latest separation, stale revision, disable/retire/history and tenant isolation',
                            'Simulation: duplicate route deduplication, no group/recovery negative controls; unsupported action/profile rejected; no durable writes',
                            'Gateway/Kafka/Processor/Worker/mock: one command and one provider create, result published, immutable envelope across rule v2 and member changes, no recovery command, disabled new cycle emits none']
        report['status'] = 'PASS'
    except Exception as error:
        report.update(status='FAIL', error=str(error)); raise
    finally:
        errors = []
        for id in reversed(owned):
            try:
                current = call('GET', '/rules/'+id)
                if current['status'] == 'ENABLED': change(id, 'disable', current['activeVersion'], current['revision'])
            except Exception as error: errors.append(str(error))
        # Retain owned mapping on failure so an in-flight command can still finish safely.
        if mapping and report['status'] == 'PASS' and not errors:
            try: s.http('DELETE', mock+'/__admin/mappings/'+mapping)
            except Exception as error: errors.append(str(error))
        else: report['retainedMockMapping'] = mapping
        report['cleanupErrors'] = errors
        if errors: report['status'] = 'FAIL'
        (output/'report.json').write_text(json.dumps(report, indent=2)+'\n')
        (output/'http-exchanges.json').write_text(json.dumps(exchanges, indent=2)+'\n')
        (output/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(output.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'))
        print(output)
        if report['status'] != 'PASS': raise RuntimeError('ROUTING_CERTIFICATION_FAILED')


if __name__ == '__main__': main()
