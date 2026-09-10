#!/usr/bin/env python3
"""Versioned correlation REST and durable Gateway/Kafka acceptance, synthetic tenant."""
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
shared = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shared)


def main():
    tenant = 'correlation-cert-'+uuid.uuid4().hex[:16]
    output = ROOT/'evidences/correlation'/tenant
    output.mkdir(parents=True)
    report = {'status': 'RUNNING', 'tenant': tenant, 'checks': []}
    exchanges = []
    owned = False
    api = 'http://127.0.0.1:8082/api/v1'
    headers = {'X-Tenant-Id': tenant, 'X-Actor-Id': 'correlation-certification'}
    now = dt.datetime.now(dt.timezone.utc)

    def call(method, path, body=None, revision=None, key=None, expected=200, customer=None):
        h = {**headers}
        if customer: h['X-Tenant-Id'] = customer
        if revision is not None: h['If-Match'] = '"'+str(revision)+'"'
        if key: h['Idempotency-Key'] = key
        try: code, data, _ = shared.http(method, api+path, body, h)
        except urllib.error.HTTPError as error: code, data = error.code, json.load(error)
        exchanges.append({'method': method, 'path': path, 'request': body, 'status': code, 'response': data})
        assert code == expected, (path, expected, code, data)
        return data

    def rule(version=1, maximum=4):
        return {'id': 'by-node', 'version': version, 'enabled': True, 'priority': 10,
                'strategy': 'ATTRIBUTE', 'scope': {'field': 'resource.node', 'operator': 'EQ', 'value': tenant},
                'candidateSelection': {'windowSeconds': 60, 'maxCandidates': maximum, 'activeOnly': True},
                'match': {'fields': ['resource.node']}, 'relationship': {'type': 'GROUP'},
                'metadata': {'owner': 'testing'}}

    def transition(action, version, revision, expected=200):
        return call('POST', '/rules/by-node/'+action, {'version': version, 'reason': 'Synthetic acceptance'},
                    revision, uuid.uuid4().hex, expected)

    def event(key, second, recovery=False, node=tenant):
        return {'schemaVersion': '1.1', 'eventId': uuid.uuid4().hex, 'eventKey': key,
                'tenant': {'code': tenant}, 'resource': {'name': node}, 'summary': 'Synthetic correlation',
                'lifecycleAction': 'CLOSE' if recovery else 'OPEN', 'effectiveSeverity': 0 if recovery else 3,
                'timestamps': {'receivedAt': (now+dt.timedelta(seconds=second)).isoformat()}}

    def simulate(events, candidate=None):
        body = {'events': events}
        if candidate is not None: body['candidateRules'] = [candidate]
        result = call('POST', '/simulations', body)
        assert result['correlationSource'] == 'EMPTY_REQUEST_SCOPED_STATE'
        return result['results']

    def decision(result):
        return result['correlation']['decisions'][0]

    def stored_groups():
        return json.loads(shared.sql("SELECT COALESCE(jsonb_agg(document),'[]'::jsonb)::text FROM event_processor.correlation_group WHERE tenant='"+tenant+"'"))

    def send(label, key, recovery=False):
        suffix = 'recovery' if recovery else 'problem'
        raw = json.loads((ROOT/f'testing/fixtures/events/sdc/zabbix-messagebus-{suffix}.json').read_text())
        raw.update(CustomerCode=tenant, Node=tenant, hostname=tenant, AlertKey=key, InstanceSituation=key, InstanceId=key)
        code, accepted, _ = shared.http('POST', 'http://127.0.0.1:8081/api/v1/events', raw)
        assert code == 202 and accepted['accepted']
        event_id = accepted['eventId']
        assert re.fullmatch(r'[A-Za-z0-9_-]+', event_id)
        record = json.loads(shared.wait(lambda: shared.sql("SELECT evidence::text FROM event_processor.processing_record WHERE tenant='"+tenant+"' AND event_id='"+event_id+"'"), label))
        pid = record['processingId']
        assert re.fullmatch(r'[a-f0-9]+', pid)
        shared.wait(lambda: shared.sql("SELECT count(*) FROM event_processor.output_outbox WHERE processing_id='"+pid+"' AND topic='events.normalized' AND published_at IS NOT NULL") == '1', 'normalized publication')
        assert call('GET', '/explain/'+pid) == record
        assert record['correlationApplied'] and record['directive'] == 'CONTINUE'
        (output/(label+'.json')).write_text(json.dumps({'input': raw, 'accepted': accepted, 'processing': record}, indent=2)+'\n')
        return record

    try:
        call('POST', '/rules/validate', {'rule': rule()})
        body = {'rule': rule(), 'reason': 'Synthetic acceptance'}
        key = uuid.uuid4().hex
        receipt = call('POST', '/rules', body, 0, key, 201)
        owned = True
        assert call('POST', '/rules', body, 0, key, 201) == receipt
        assert not simulate([event('a', 0)])[0]['correlation']['decisions']
        transition('enable', 1, 1)
        call('GET', '/rules/by-node', customer=tenant+'-other', expected=404)
        sequence = [event('a', 0), event('b', 1), event('a', 2, True), event('b', 3, True),
                    event('a', 3), event('a', 1), event('a', 4)]
        results = simulate(sequence)
        assert [decision(r)['reason'] for r in results] == ['GROUP_CREATED', 'MEMBER_ATTACHED', 'MEMBER_RESOLVED', 'MEMBER_RESOLVED', 'TIED_EVENT_IGNORED', 'LATE_EVENT_IGNORED', 'NEW_CYCLE']
        assert decision(results[0])['group']['groupId'] == decision(results[1])['group']['groupId']
        assert decision(results[3])['group']['resolved']
        assert decision(results[-1])['group']['cycle'] == 2
        assert decision(results[-1])['group']['groupId'] != decision(results[0])['group']['groupId']
        assert decision(simulate([event('outside', 0, node='outside')])[0])['reason'] == 'SCOPE_NO_MATCH'
        assert decision(simulate([event('orphan', 0, True)])[0])['reason'] == 'ORPHAN_RECOVERY'
        expiry = simulate([event('a', 0), event('b', 60), event('c', 121)])
        assert [decision(r)['group']['cycle'] for r in expiry] == [1, 1, 2]
        bounded = simulate([event('a', 0), event('b', 1), event('a', 2, True)], rule(maximum=1))
        assert decision(bounded[1])['reason'] == 'CANDIDATE_LIMIT' and bounded[1]['directive'] == 'DEAD_LETTER'
        assert decision(bounded[2])['group']['resolved']
        assert stored_groups() == [], 'Simulation wrote production relationships'
        report['checks'].append('Active REST snapshot simulation: membership, recovery, new cycles, late/tied events, scope, orphan, inclusive expiry and candidate limit; no durable simulation writes')
        for field, value in [('strategy', 'TOPOLOGICAL'), ('relationship', {'type': 'PARENT_CHILD'}),
                             ('match', {'fields': ['event.key']}), ('candidateSelection', {'windowSeconds': 0, 'maxCandidates': 4, 'activeOnly': True})]:
            invalid = rule(); invalid['id'] = 'invalid-'+field; invalid[field] = value
            call('POST', '/rules', {'rule': invalid, 'reason': 'Invalid fixture'}, 0, uuid.uuid4().hex, 422)
            call('GET', '/rules/'+invalid['id'], expected=404)
        first = decision(send('first-member', tenant+'-a'))
        assert first['reason'] == 'GROUP_CREATED'
        call('POST', '/rules', {'rule': rule(2), 'reason': 'Equivalent keys'}, 2, uuid.uuid4().hex, 201)
        current = call('GET', '/rules/by-node')
        assert current['latestVersion'] == 2 and current['activeVersion'] == 1
        assert decision(simulate([event('a', 0)])[0])['ruleVersion'] == 1
        transition('enable', 2, 2, 409)
        transition('enable', 2, 3)
        second = decision(send('second-member-v2', tenant+'-b'))
        assert second['reason'] == 'MEMBER_ATTACHED' and second['ruleVersion'] == 2
        assert second['group']['groupId'] == first['group']['groupId']
        assert len(second['group']['members']) == 2
        assert not decision(send('recover-first', tenant+'-a', True))['group']['resolved']
        closed = decision(send('recover-second', tenant+'-b', True))
        assert closed['group']['resolved']
        reopened = decision(send('new-cycle', tenant+'-a'))
        assert reopened['reason'] == 'NEW_CYCLE' and reopened['group']['cycle'] == 2
        assert reopened['group']['groupId'] != first['group']['groupId']
        assert stored_groups() == [reopened['group']]
        transition('disable', 2, 4)
        assert not send('disabled-rule', tenant+'-c')['correlation']['decisions']
        assert stored_groups() == [reopened['group']], 'Disabled rule mutated persisted group'
        transition('retire', 2, 5)
        transition('enable', 2, 6, 409)
        assert len(call('GET', '/rules/by-node/history')['items']) == 6
        assert shared.sql("SELECT count(*) FROM event_processor.integration_command WHERE tenant='"+tenant+"'") == '0'
        report['checks'].append('Gateway/Kafka: two members, equivalent version preserves group, partial/full recovery, distinct new cycle, PostgreSQL equality, explain and normalized publication; disable stops mutation')
        report['checks'].append('Versioned CRUD: idempotency, active/latest separation, optimistic conflict, retire/history, tenant isolation and unsupported semantic rejection')
        report['status'] = 'PASS'
    except Exception as error:
        report.update(status='FAIL', error=str(error))
        raise
    finally:
        errors = []
        if owned:
            try:
                current = call('GET', '/rules/by-node')
                if current['status'] == 'ENABLED': transition('disable', current['activeVersion'], current['revision'])
            except Exception as error: errors.append(str(error))
        report['cleanupErrors'] = errors
        if errors: report['status'] = 'FAIL'
        (output/'report.json').write_text(json.dumps(report, indent=2)+'\n')
        (output/'http-exchanges.json').write_text(json.dumps(exchanges, indent=2)+'\n')
        (output/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(output.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'))
        print(output)
        if report['status'] != 'PASS': raise RuntimeError('CORRELATION_CERTIFICATION_FAILED')


if __name__ == '__main__':
    main()
