#!/usr/bin/env python3
"""Backend acceptance for the future blackout form. No browser or frontend mutation."""
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
API = 'http://127.0.0.1:8082/api/v1'


def main():
    tenant = 'blackout-write-' + uuid.uuid4().hex[:16]
    output = ROOT / 'evidences/blackouts' / tenant
    output.mkdir(parents=True)
    report = {'status': 'RUNNING', 'tenant': tenant, 'scope': 'API/DB/active snapshot; no browser certification', 'checks': []}
    owned = []
    exchanges = []
    now = dt.datetime.now(dt.timezone.utc)
    start, end = now - dt.timedelta(minutes=5), now + dt.timedelta(minutes=30)

    def call(method, path, expected, body=None, revision=None, key=None, request_tenant=tenant):
        headers = {'X-Tenant-Id': request_tenant, 'X-Actor-Id': 'blackout-certification', 'Content-Type': 'application/json'}
        if revision is not None:
            headers['If-Match'] = '"' + str(revision) + '"'
        if key is not None:
            headers['Idempotency-Key'] = key
        req = urllib.request.Request(API + path, method=method, headers=headers,
                                     data=None if body is None else json.dumps(body).encode())
        try:
            response = urllib.request.urlopen(req, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            data = json.loads(raw) if raw else None
            exchanges.append({'method': method, 'path': path, 'status': response.status, 'body': body,
                              'response': data, 'revision': revision, 'idempotencyKey': key})
            assert response.status == expected, (method, path, expected, response.status, data)
            return data

    def change(id, action, version, revision, expected=200):
        return call('POST', '/rules/' + id + '/' + action, expected,
                    {'version': version, 'reason': 'Synthetic blackout acceptance'}, revision, uuid.uuid4().hex)

    def definition(id, mode='SCHEDULED', node='node-a'):
        rule = {'id': id, 'version': 1, 'type': mode, 'enabled': True, 'priority': 10,
                'scope': {'customerCode': tenant, 'node': node},
                'schedule': {'timezone': 'America/Mexico_City', 'validFrom': start.isoformat()},
                'reason': 'Synthetic blackout acceptance', 'metadata': {'owner': 'testing'}}
        if mode == 'SCHEDULED':
            rule['schedule']['validTo'] = end.isoformat()
        return rule

    def simulate(node, at, expected, recovery=False):
        event = {'schemaVersion': '1.1', 'eventId': uuid.uuid4().hex, 'eventKey': node,
                 'tenant': {'code': tenant}, 'resource': {'name': node}, 'summary': 'Synthetic maintenance test',
                 'lifecycleAction': 'CLOSE' if recovery else 'OPEN', 'effectiveSeverity': 0 if recovery else 3,
                 'timestamps': {'receivedAt': now.isoformat()}}
        result = call('POST', '/simulations', 200, {'event': event, 'evaluatedAt': at.isoformat()})
        stage = next(s for s in result['stages'] if s['stage'] == 'Blackout')
        assert stage['match'] == expected, result
        assert len(result['stages']) == 12
        assert result['directive'] == ('SUPPRESS_INTEGRATIONS' if expected == 'MATCH' else 'CONTINUE')
        assert result['configurationSource'] == 'ACTIVE'
        return result

    try:
        rule = definition('scheduled')
        body = {'rule': rule, 'reason': 'Synthetic creation'}
        call('POST', '/rules/validate', 200, {'rule': rule})
        call('POST', '/rules', 428, body, key=uuid.uuid4().hex)
        key = uuid.uuid4().hex
        created = call('POST', '/rules', 201, body, 0, key)
        owned.append('scheduled')
        assert created['revision'] == 1
        assert call('POST', '/rules', 201, body, 0, key) == created
        call('POST', '/rules', 409, {**body, 'reason': 'Changed request'}, 0, key)
        call('GET', '/rules/scheduled', 404, request_tenant=tenant + '-other')
        assert call('GET', '/rules/scheduled', 200)['status'] == 'DISABLED'
        simulate('node-a', now, 'NO_MATCH')
        change('scheduled', 'enable', 1, 1)
        for at, match in [(start-dt.timedelta(microseconds=1), 'NO_MATCH'), (start, 'MATCH'),
                          (end-dt.timedelta(microseconds=1), 'MATCH'), (end, 'NO_MATCH')]:
            simulate('node-a', at, match)
        simulate('node-other', now, 'NO_MATCH')
        simulate('node-a', now, 'MATCH', recovery=True)
        report['checks'].append('create disabled; idempotent retry/conflict; tenant isolation; active start-inclusive/end-exclusive, node and recovery')

        version2 = definition('scheduled', node='node-b')
        version2['version'] = 2
        call('POST', '/rules', 409, {'rule': version2, 'reason': 'Stale edit'}, 1, uuid.uuid4().hex)
        call('POST', '/rules', 201, {'rule': version2, 'reason': 'New version'}, 2, uuid.uuid4().hex)
        current = call('GET', '/rules/scheduled', 200)
        assert current['latestVersion'] == 2 and current['activeVersion'] == 1 and current['revision'] == 3
        assert call('GET', '/rules/scheduled?version=1', 200)['rule'] == rule
        simulate('node-a', now, 'MATCH')
        simulate('node-b', now, 'NO_MATCH')
        change('scheduled', 'enable', 2, 3)
        simulate('node-a', now, 'NO_MATCH')
        simulate('node-b', now, 'MATCH')
        change('scheduled', 'disable', 1, 4, 409)
        change('scheduled', 'disable', 2, 4)
        simulate('node-b', now, 'NO_MATCH')
        change('scheduled', 'retire', 2, 5)
        change('scheduled', 'enable', 2, 6, 409)
        assert call('GET', '/rules/scheduled/history', 200)['items'][-1]['status'] == 'RETIRED'
        assert len(call('GET', '/rules/scheduled/history', 200)['items']) == 6
        report['checks'].append('new version leaves old active until enable; optimistic concurrency; disable/retire and immutable historical version')

        immediate = definition('immediate', 'IMMEDIATE')
        call('POST', '/rules', 201, {'rule': immediate, 'reason': 'Immediate'}, 0, uuid.uuid4().hex)
        owned.append('immediate')
        change('immediate', 'enable', 1, 1)
        simulate('node-a', end+dt.timedelta(days=1), 'MATCH')
        change('immediate', 'disable', 1, 2)
        simulate('node-a', now, 'NO_MATCH')
        first = call('GET', '/rules?limit=1', 200)
        assert first['next'] is not None
        assert len(call('GET', '/rules?limit=1&after=' + first['next'], 200)['items']) == 1
        for variant in ['recurring', 'bad-zone', 'reverse', 'wrong-tenant']:
            invalid = definition('invalid-' + variant)
            if variant == 'recurring': invalid['type'] = 'RECURRING'
            if variant == 'bad-zone': invalid['schedule']['timezone'] = 'invalid/zone'
            if variant == 'reverse': invalid['schedule']['validTo'] = (start-dt.timedelta(seconds=1)).isoformat()
            if variant == 'wrong-tenant': invalid['scope']['customerCode'] = tenant + '-other'
            call('POST', '/rules', 422, {'rule': invalid, 'reason': 'Invalid fixture'}, 0, uuid.uuid4().hex)
            call('GET', '/rules/' + invalid['id'], 404)
        report['checks'].append('immediate without end; disable; pagination; invalid windows/zone/recurrence/tenant rejected without registration')

        # Exercise the actual catalog adapter against PostgreSQL with its existing read-only connection.
        # Scope results to owned fixtures; do not print unrelated customer data or credentials.
        source = (ROOT/'services/console-catalog-api/views.py').read_text()
        program = 'import server,json\nns={}\nexec(' + repr(source) + ',ns)\n'
        program += 'with server.connect() as c:\n rows=ns["snapshot"](c,"blackouts")["items"]\n'
        program += 'own=[r for r in rows if r["tenant"]==' + repr(tenant) + ']\n'
        program += 'assert {r["id"] for r in own}=={"scheduled","immediate"}, own\n'
        program += 'assert all(r["source"]=="Event Processor" for r in own)\nprint("PASS: scheduled/immediate catalog read against PostgreSQL")\n'
        result = subprocess.run(['docker','exec','-i','console-catalog-api','python','-'], input=program,
                                capture_output=True, text=True, timeout=20)
        (output/'catalog-test.log').write_text(result.stdout + result.stderr)
        assert result.returncode == 0, 'Catalog database test failed'
        report['checks'].append('corrected catalog adapter reads SCHEDULED and IMMEDIATE from actual PostgreSQL')
        with urllib.request.urlopen('http://127.0.0.1:8090/api/catalog/views/blackouts', timeout=15) as response:
            deployed = json.load(response)
        own = [row for row in deployed['items'] if row['tenant'] == tenant]
        assert {row['id'] for row in own} == {'scheduled', 'immediate'}, 'Deployed catalog does not expose both blackout types'
        assert all(row['source'] == 'Event Processor' for row in own)
        report['checks'].append('deployed catalog HTTP read lists both created modes through existing proxy; no browser writes')
        report['status'] = 'PASS'
    except Exception as error:
        report['status'] = 'FAIL'
        report['error'] = str(error)
        raise
    finally:
        cleanup = []
        for id in owned:
            try:
                current = call('GET', '/rules/'+id, 200)
                if current['status'] == 'ENABLED':
                    change(id, 'disable', current['activeVersion'], current['revision'])
                cleanup.append({'id': id, 'active': False})
            except Exception as error:
                cleanup.append({'id': id, 'error': str(error)})
                report['status'] = 'FAIL'
        report['cleanup'] = cleanup
        (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
        (output/'http-exchanges.json').write_text(json.dumps(exchanges,indent=2)+'\n')
        (output/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n'
                                               for p in sorted(output.iterdir()) if p.name!='SHA256SUMS'))
        print(output)
        if report['status'] != 'PASS':
            raise RuntimeError('BLACKOUT_WRITE_CERTIFICATION_FAILED')


if __name__ == '__main__':
    main()
