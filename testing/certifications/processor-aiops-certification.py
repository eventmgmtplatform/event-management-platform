#!/usr/bin/env python3
"""Certify independent AIOps CRUD and HTTP mock on the local deployment."""
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
BASE = 'http://127.0.0.1:8082'
spec = importlib.util.spec_from_file_location('shared', ROOT/'testing/e2e/blackout.py')
shared = importlib.util.module_from_spec(spec); spec.loader.exec_module(shared)


def main():
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:12]
    output = ROOT / 'evidences/os-02-event-processor/aiops' / stamp
    output.mkdir(parents=True)
    tenant = 'aiops-cert-' + stamp.lower()
    report = {'status': 'RUNNING', 'tenant': tenant, 'scope': 'local independent AIOps; internal mock only', 'checks': []}
    revision = None
    exchanges = []

    def request(method, path, expected, body=None, match=None, customer=None):
        headers = {'X-Tenant-Id': customer or tenant, 'X-Actor-Id': 'local-certification', 'Content-Type': 'application/json'}
        if match is not None:
            headers['If-Match'] = '"' + str(match) + '"'
        req = urllib.request.Request(BASE + '/api/v1/aiops' + path,
                                     data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
        try:
            response = urllib.request.urlopen(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            data = response.read()
            parsed = json.loads(data) if data else None
            exchanges.append({'method': method, 'path': path, 'request': body, 'status': response.status, 'response': parsed, 'etag': response.headers.get('ETag')})
            assert response.status == expected, (method, path, response.status, expected, parsed)
            if response.status in (200, 201) and isinstance(parsed, dict) and 'enabled' in parsed:
                assert response.headers.get('ETag') == '"'+str(parsed['revision'])+'"'
            return parsed

    def ready():
        for _ in range(90):
            try:
                with urllib.request.urlopen(BASE + '/health/ready', timeout=3) as response:
                    if response.status == 200:
                        return
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(1)
        raise RuntimeError('PROCESSOR_NOT_READY')

    try:
        ready()
        inspected = json.loads(subprocess.run(['docker', 'inspect', 'event-event-processor'], check=True, capture_output=True, text=True).stdout)[0]
        environment = dict(item.split('=', 1) for item in inspected['Config']['Env'] if '=' in item)
        assert environment.get('AIOPS_MOCK_URL') == 'http://aiops-mock:8080', 'Internal mock required'
        created = request('POST', '', 201, {'id': 'bridge', 'name': 'Synthetic Bridge prototype', 'enabled': False}, 0)
        revision = created['revision']
        request('POST', '', 409, {'id': 'bridge', 'name': 'Duplicate', 'enabled': True}, 0)
        request('GET', '/bridge', 404, customer=tenant+'-other')
        request('PUT', '/bridge', 428, {'name': 'Missing revision', 'enabled': True})
        request('POST', '', 400, {'id': 'invalid', 'name': 'Invalid', 'enabled': True, 'endpoint': 'http://example.invalid'}, 0)
        request('GET', '/invalid', 404)
        request('GET', '?limit=101', 422)
        request('POST', '/bridge/assessments', 409, {'resource': 'node-demo', 'summary': 'Synthetic assessment', 'severity': 3})
        updated = request('PUT', '/bridge', 200, {'name': 'Synthetic Bridge prototype', 'enabled': True}, revision)
        revision = updated['revision']
        assessment = request('POST', '/bridge/assessments', 200, {'resource': 'node-demo', 'summary': 'Synthetic assessment', 'severity': 3})
        request('POST', '/bridge/assessments', 422, {'resource': 'node-demo', 'summary': 'Invalid severity', 'severity': '3'})
        assert assessment['configurationId'] == 'bridge' and assessment['revision'] == revision
        assert assessment['provider'] == 'INTERNAL_MOCK'
        assert assessment['assessment'] == {'recommendation': 'INVESTIGATE', 'confidence': 0.75}
        (output / 'assessment.json').write_text(json.dumps(assessment, indent=2) + '\n')
        for table in ['processing_record', 'rule_definition', 'integration_command']:
            assert shared.sql("SELECT count(*) FROM event_processor."+table+" WHERE tenant='"+tenant+"'") == '0'
        assert shared.sql("SELECT count(*) FROM event_processor.aiops_change WHERE tenant='"+tenant+"'") == '2'
        _, catalog, _ = shared.http('GET', 'http://127.0.0.1:8090/api/catalog/views/aiops-extensions')
        assert [r for r in catalog['items'] if r['tenant'] == tenant] == [{'id': 'bridge', 'tenant': tenant, 'name': updated['name'], 'enabled': True, 'revision': revision}]
        report['checks'].append('ETag, tenant isolation, duplicate/invalid requests, missing precondition, catalog and audit; no event/rule/command side effects')
        report['checks'].append('CRUD creation/update and disabled guard; HTTP WireMock assessment passed')
        subprocess.run(['docker', 'restart', 'event-event-processor'], check=True, capture_output=True, timeout=90)
        ready()
        assert request('GET', '/bridge', 200) == updated
        assert request('GET', '', 200) == [updated]
        assert request('GET', '?limit=1&after=bridge', 200) == []
        request('PUT', '/bridge', 409, {'name': 'stale', 'enabled': False}, 1)
        report['checks'].append('configuration/revision persist across Processor restart; stale write rejected')
        request('DELETE', '/bridge', 204, match=revision)
        revision = None
        request('GET', '/bridge', 404)
        assert request('GET', '', 200) == []
        request('POST', '', 409, {'id': 'bridge', 'name': 'Reuse deleted ID', 'enabled': True}, 0)
        assert shared.sql("SELECT count(*) FROM event_processor.aiops_change WHERE tenant='"+tenant+"'") == '3'
        report['checks'].append('logical delete hides fixture; ID cannot be reused; three audit revisions retained')
        report['status'] = 'PASS'
    except Exception as error:
        report['status'] = 'FAIL'
        report['errorType'] = type(error).__name__
        raise
    finally:
        if revision is not None:
            try:
                current = request('GET', '/bridge', 200)
                request('DELETE', '/bridge', 204, match=current['revision'])
                report['cleanup'] = 'PASS'
            except Exception:
                report['cleanup'] = 'FAIL'
                report['status'] = 'FAIL'
        (output / 'http-exchanges.json').write_text(json.dumps(exchanges, indent=2) + '\n')
        (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        (output / 'SHA256SUMS').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest() + '  ' + f.name + '\n'
                                                for f in sorted(output.iterdir()) if f.name != 'SHA256SUMS'))
        print(output)
        if report['status'] != 'PASS':
            raise RuntimeError('AIOPS_CERTIFICATION_FAILED')


if __name__ == '__main__':
    main()
