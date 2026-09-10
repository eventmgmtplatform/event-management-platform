#!/usr/bin/env python3
"""Typed POLICY REST acceptance and real suppression of an eligible route."""
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import urllib.error
import uuid

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('shared', ROOT/'testing/e2e/blackout.py')
s = importlib.util.module_from_spec(spec); spec.loader.exec_module(s)


def main():
    tenant = 'policy-cert-'+uuid.uuid4().hex[:16]
    output = ROOT/'evidences/policy'/tenant; output.mkdir(parents=True)
    api = 'http://127.0.0.1:8082/api/v1'
    headers = {'X-Tenant-Id': tenant, 'X-Actor-Id': 'policy-certification'}
    report = {'status': 'RUNNING', 'tenant': tenant, 'checks': []}; exchanges = []; owned = []
    group = {'id': 'group', 'version': 1, 'enabled': True, 'priority': 10, 'strategy': 'ATTRIBUTE',
             'scope': {'field': 'resource.node', 'operator': 'EQ', 'value': tenant},
             'candidateSelection': {'windowSeconds': 3600, 'maxCandidates': 8, 'activeOnly': True},
             'match': {'fields': ['resource.node']}, 'relationship': {'type': 'GROUP'}, 'metadata': {'owner': 'testing'}}
    route = {'id': 'route', 'version': 1, 'type': 'ROUTING', 'enabled': True, 'priority': 10,
             'condition': group['scope'], 'actions': [{'type': 'CREATE_TICKET', 'target': 'SERVICENOW',
             'parameters': {'configuration': 'default', 'correlationRuleId': 'group'}}], 'metadata': {'owner': 'testing'}}

    def policy(version=1, directive='SUPPRESS_INTEGRATIONS'):
        return {'id': 'policy', 'version': version, 'type': 'POLICY', 'enabled': True, 'priority': 10,
                'condition': {'all': [group['scope'], {'field': 'event.severity', 'operator': 'BETWEEN', 'value': [0, 5]}]},
                'actions': [{'type': directive}], 'metadata': {'owner': 'testing'}}

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
        body = {'rule': rule, 'reason': 'Synthetic policy acceptance'}; key = uuid.uuid4().hex
        result = call('POST', '/rules', body, revision, key, 201)
        if rule['id'] not in owned: owned.append(rule['id'])
        assert call('POST', '/rules', body, revision, key, 201) == result

    def change(id, action, version, revision, expected=200):
        return call('POST', '/rules/'+id+'/'+action, {'version': version, 'reason': 'Synthetic acceptance'}, revision, uuid.uuid4().hex, expected)

    def stage(result): return next(v for v in result['stages'] if v['stage'] == 'PolicyEvaluation')

    def simulate(candidates=None, node=tenant, customer=tenant):
        event = {'schemaVersion': '1.1', 'eventId': uuid.uuid4().hex, 'eventKey': node,
                 'tenant': {'code': customer}, 'resource': {'name': node}, 'summary': 'Synthetic policy',
                 'lifecycleAction': 'OPEN', 'effectiveSeverity': 3,
                 'timestamps': {'receivedAt': dt.datetime.now(dt.timezone.utc).isoformat()}}
        body = {'event': event}
        if candidates is not None: body['candidateRules'] = candidates
        return call('POST', '/simulations', body, customer=customer)

    def send(label, expected, recovery=False):
        suffix = 'recovery' if recovery else 'problem'
        raw = json.loads((ROOT/f'testing/fixtures/events/sdc/zabbix-messagebus-{suffix}.json').read_text())
        raw.update(CustomerCode=tenant, Node=tenant, hostname=tenant)
        code, accepted, _ = s.http('POST', 'http://127.0.0.1:8081/api/v1/events', raw)
        assert code == 202 and accepted['accepted']
        eid = accepted['eventId']; assert re.fullmatch(r'[A-Za-z0-9_-]+', eid)
        record = json.loads(s.wait(lambda: s.sql("SELECT evidence::text FROM event_processor.processing_record WHERE tenant='"+tenant+"' AND event_id='"+eid+"'"), label))
        pid = record['processingId']; assert re.fullmatch(r'[a-f0-9]+', pid)
        assert record['directive'] == expected and record['correlationApplied']
        assert len(record['stages']) == 12 and not record['routing']['commands']
        assert call('GET', '/explain/'+pid) == record
        s.wait(lambda: s.sql("SELECT count(*) FROM event_processor.output_outbox WHERE processing_id='"+pid+"' AND topic='events.normalized' AND published_at IS NOT NULL") == '1', 'normalized publication')
        (output/(label+'.json')).write_text(json.dumps({'input': raw, 'accepted': accepted, 'processing': record}, indent=2)+'\n')
        return stage(record)

    try:
        call('POST', '/rules/validate', {'rule': policy()}); save(policy(), 0)
        assert stage(simulate())['match'] == 'NO_MATCH'
        change('policy', 'enable', 1, 1)
        for rule in [group, route]: save(rule, 0); change(rule['id'], 'enable', 1, 1)
        assert len(simulate([group, route])['candidates']) == 1
        live = simulate(); assert live['directive'] == 'SUPPRESS_INTEGRATIONS' and not live['candidates']
        assert stage(simulate(node='outside'))['match'] == 'NO_MATCH'
        assert stage(simulate(customer=tenant+'-other'))['evidence']['ruleCount'] == '0'
        call('GET', '/rules/policy', customer=tenant+'-other', expected=404)
        conflict = policy(directive='CONTINUE'); conflict.update(id='higher-priority', priority=100)
        restrictive = policy(directive='STATE_ONLY')
        resolved = simulate([conflict, restrictive, group, route])
        assert resolved['directive'] == 'STATE_ONLY' and not resolved['candidates']
        assert stage(resolved)['evidence']['rule.0.id'] == 'higher-priority'
        assert stage(resolved)['evidence']['rule.1.id'] == 'policy'
        first = send('suppressed', 'SUPPRESS_INTEGRATIONS')
        assert first['evidence']['rule.0.id'] == 'policy' and first['evidence']['rule.0.version'] == '1'
        assert re.fullmatch(r'[a-f0-9]{64}', first['evidence']['rule.0.checksum'])
        send('recovery-suppressed', 'SUPPRESS_INTEGRATIONS', True)
        revision = 2
        for version, directive in [(2, 'STATE_ONLY'), (3, 'CORRELATE_ONLY')]:
            previous = stage(simulate())['directive']
            save(policy(version, directive), revision); revision += 1
            assert stage(simulate())['directive'] == previous
            assert call('GET', '/rules/policy')['activeVersion'] == version-1
            change('policy', 'enable', version, revision-1, 409)
            change('policy', 'enable', version, revision); revision += 1
            assert send(directive.lower(), directive)['evidence']['rule.0.version'] == str(version)
        # Disable the eligible route before proving CONTINUE against the real runtime.
        change('route', 'disable', 1, 2)
        save(policy(4, 'CONTINUE'), revision); revision += 1
        change('policy', 'enable', 4, revision); revision += 1
        send('continue-no-route', 'CONTINUE')
        change('policy', 'disable', 4, revision); revision += 1
        assert send('disabled', 'CONTINUE')['evidence']['ruleCount'] == '0'
        change('policy', 'retire', 4, revision); revision += 1
        change('policy', 'enable', 4, revision, 409)
        assert len(call('GET', '/rules/policy/history')['items']) == revision
        invalid_conditions = [{'field': 'event.severity', 'operator': 'EQ', 'value': '3'},
                              {'field': 'unknown.field', 'operator': 'EXISTS'},
                              {'field': 'event.identifier', 'operator': 'REGEX', 'value': '(a+)+'}]
        for index, condition in enumerate(invalid_conditions):
            invalid = policy(); invalid.update(id='invalid-'+str(index), condition=condition)
            call('POST', '/rules', {'rule': invalid, 'reason': 'Invalid fixture'}, 0, uuid.uuid4().hex, 422)
            call('GET', '/rules/'+invalid['id'], expected=404)
        assert s.sql("SELECT count(*) FROM event_processor.integration_command WHERE tenant='"+tenant+"'") == '0'
        _, catalog, _ = s.http('GET', 'http://127.0.0.1:8090/api/catalog/views/policies')
        rows = [r for r in catalog['items'] if r['tenant'] == tenant]
        assert len(rows) == 1 and rows[0]['status'] == 'RETIRED' and rows[0]['source'] == 'Event Processor'
        report['checks'] = ['Typed REST, idempotent versions, optimistic conflicts, active/latest separation, disable/retire/history, tenant and scope isolation, invalid field/type/regex rejected',
                            'Priority ordering retains all proposals; STATE_ONLY overrides higher-priority CONTINUE; real SUPPRESS_INTEGRATIONS/STATE_ONLY/CORRELATE_ONLY block eligible routing',
                            'Recovery and 12-stage audit retained; normalized publication/explain and exact rule/checksum; no durable commands; persisted catalog verified']
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
        report['cleanupErrors'] = errors
        if errors: report['status'] = 'FAIL'
        (output/'report.json').write_text(json.dumps(report, indent=2)+'\n')
        (output/'http-exchanges.json').write_text(json.dumps(exchanges, indent=2)+'\n')
        (output/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(output.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'))
        print(output)
        if report['status'] != 'PASS': raise RuntimeError('POLICY_CERTIFICATION_FAILED')


if __name__ == '__main__': main()
