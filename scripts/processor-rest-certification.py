#!/usr/bin/env python3
"""Local REST smoke: simulation only, no configuration activation or state mutation."""
import datetime
import hashlib
import json
from pathlib import Path
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:8082/api/v1'


def main():
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    output = ROOT / 'evidence/os-02-event-processor/rest' / stamp
    output.mkdir(parents=True)
    report = {'status': 'RUNNING', 'scope': 'local unauthenticated REST; synthetic candidate simulation', 'checks': []}

    def request(path, body=None, tenant='certification-rest'):
        headers = {'Content-Type': 'application/json'}
        if tenant is not None:
            headers['X-Tenant-Id'] = tenant
        req = urllib.request.Request(BASE + path, headers=headers,
                                     data=None if body is None else json.dumps(body).encode())
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.load(error)

    try:
        assert request('/rules', tenant=None)[0] == 400
        before = request('/rules')
        assert before[0] == 200
        report['checks'].append('REST without token; tenant header required')
        rule = {'id': 'maintenance-smoke', 'version': 1, 'type': 'SCHEDULED', 'enabled': False,
                'scope': {'customerCode': 'certification-rest', 'node': 'router-1'},
                'schedule': {'timezone': 'America/Mexico_City', 'validFrom': '2026-09-09T10:00:00Z',
                             'validTo': '2026-09-09T11:00:00Z'}, 'priority': 10,
                'reason': 'Synthetic maintenance', 'metadata': {'owner': 'certification'}}
        event = {'schemaVersion': '1.1', 'eventId': 'maintenance-smoke', 'eventKey': 'maintenance-smoke',
                 'tenant': {'code': 'certification-rest'}, 'lifecycleAction': 'CLOSE', 'effectiveSeverity': 0,
                 'timestamps': {'receivedAt': '2026-09-09T10:00:00Z'}, 'resource': {'name': 'router-1'}}
        payload = {'event': event, 'candidateRule': rule}
        status, result = request('/simulations', payload)
        assert status == 200 and result['mode'] == 'SIMULATION'
        assert result['directive'] == 'SUPPRESS_INTEGRATIONS' and result['candidates'] == []
        assert len(result['stages']) == 12 and result['stages'][6]['match'] == 'MATCH'
        assert result['stages'][8]['status'] == result['stages'][11]['status'] == 'SUCCESS'
        report['checks'].append('inactive candidate matches start boundary; recovery reaches policy/audit')
        payload['evaluatedAt'] = '2026-09-09T11:00:00Z'
        status, result = request('/simulations', payload)
        assert status == 200 and result['directive'] == 'CONTINUE'
        report['checks'].append('end boundary excludes blackout')
        inventory = {'id': 'inventory-smoke', 'version': 1, 'type': 'INVENTORY', 'enabled': False,
                     'priority': 10, 'scope': {'node': 'router-1'},
                     'facts': {'assignment.group': 'network', 'resource.managed': True},
                     'metadata': {'owner': 'certification'}}
        plan = {'id': 'enrichment-smoke', 'version': 1, 'type': 'ENRICHMENT', 'enabled': False,
                'priority': 10, 'condition': {'field': 'resource.node', 'operator': 'EXISTS'},
                'actions': [{'type': 'LOOKUP_INVENTORY', 'parameters': {'required': True}}],
                'metadata': {'owner': 'certification'}}
        policy = {'id': 'policy-smoke', 'version': 1, 'type': 'POLICY', 'enabled': False,
                  'priority': 10, 'condition': {'field': 'enrichment.resource.managed', 'operator': 'EQ', 'value': True},
                  'actions': [{'type': 'STATE_ONLY'}], 'metadata': {'owner': 'certification'}}
        payload = {'event': event, 'candidateRules': [inventory, plan, policy]}
        status, result = request('/simulations', payload)
        assert status == 200 and result['directive'] == 'STATE_ONLY'
        assert result['enrichment']['status'] == 'SUCCESS'
        assert result['enrichment']['facts']['assignment.group'] == 'network'
        assert result['enrichment']['provenance'][0]['source'] == 'inventory:inventory-smoke'
        payload['candidateRules'] = [plan, policy]
        status, result = request('/simulations', payload)
        assert status == 200 and result['directive'] == 'DEAD_LETTER'
        assert result['enrichment']['status'] == 'FAILED' and result['stages'][11]['status'] == 'SUCCESS'
        report['checks'].append('versioned inventory feeds enrichment and policy; missing required inventory fails explicitly')
        assert request('/rules') == before
        report['checks'].append('candidate simulation leaves configuration unchanged')
        report['status'] = 'PASS'
    except Exception as error:
        report['status'] = 'FAIL'
        report['errorType'] = type(error).__name__
        raise
    finally:
        path = output / 'report.json'
        path.write_text(json.dumps(report, indent=2) + '\n')
        (output / 'SHA256SUMS').write_text(hashlib.sha256(path.read_bytes()).hexdigest() + '  report.json\n')
        print(str(output))


if __name__ == '__main__':
    main()
