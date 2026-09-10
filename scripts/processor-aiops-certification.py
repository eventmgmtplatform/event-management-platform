#!/usr/bin/env python3
"""Certify independent AIOps CRUD and HTTP mock on the local deployment."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:8082'


def main():
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    output = ROOT / 'evidence/os-02-event-processor/aiops' / stamp
    output.mkdir(parents=True)
    tenant = 'aiops-cert-' + stamp.lower()
    report = {'status': 'RUNNING', 'tenant': tenant, 'scope': 'local independent AIOps; internal mock only', 'checks': []}
    revision = None

    def request(method, path, expected, body=None, match=None):
        headers = {'X-Tenant-Id': tenant, 'X-Actor-Id': 'local-certification', 'Content-Type': 'application/json'}
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
            assert response.status == expected, (method, path, response.status, expected)
            return json.loads(data) if data else None

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
        created = request('POST', '', 201, {'id': 'bridge', 'name': 'Synthetic Bridge prototype', 'enabled': False}, 0)
        revision = created['revision']
        request('POST', '/bridge/assessments', 409, {'resource': 'node-demo', 'summary': 'Synthetic assessment', 'severity': 3})
        updated = request('PUT', '/bridge', 200, {'name': 'Synthetic Bridge prototype', 'enabled': True}, revision)
        revision = updated['revision']
        assessment = request('POST', '/bridge/assessments', 200, {'resource': 'node-demo', 'summary': 'Synthetic assessment', 'severity': 3})
        assert assessment['provider'] == 'INTERNAL_MOCK'
        assert assessment['assessment'] == {'recommendation': 'INVESTIGATE', 'confidence': 0.75}
        (output / 'assessment.json').write_text(json.dumps(assessment, indent=2) + '\n')
        report['checks'].append('CRUD creation/update and disabled guard; HTTP WireMock assessment passed')
        subprocess.run(['docker', 'restart', 'event-event-processor'], check=True, capture_output=True, timeout=90)
        ready()
        assert request('GET', '/bridge', 200) == updated
        assert request('GET', '', 200) == [updated]
        request('PUT', '/bridge', 409, {'name': 'stale', 'enabled': False}, 1)
        report['checks'].append('configuration/revision persist across Processor restart; stale write rejected')
        request('DELETE', '/bridge', 204, match=revision)
        revision = None
        request('GET', '/bridge', 404)
        assert request('GET', '', 200) == []
        report['checks'].append('logical delete hides fixture; audit retained')
        report['status'] = 'PASS'
    except Exception as error:
        report['status'] = 'FAIL'
        report['errorType'] = type(error).__name__
        raise
    finally:
        if revision is not None:
            try:
                request('DELETE', '/bridge', 204, match=revision)
                report['cleanup'] = 'PASS'
            except Exception:
                report['cleanup'] = 'PENDING'
        (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        (output / 'SHA256SUMS').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest() + '  ' + f.name + '\n'
                                                for f in sorted(output.iterdir()) if f.name != 'SHA256SUMS'))
        print(output)


if __name__ == '__main__':
    main()
