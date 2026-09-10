#!/usr/bin/env python3
"""Exercise the packaged administrative HTTP API against an isolated test database.

Requires GATEWAY_RULE_TEST_JDBC_URL with migration 026 applied. Starts only rule
routes on loopback, never the receipt/Kafka routes or the deployed gateway.
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid


def main():
    root = Path(__file__).resolve().parents[2]
    jdbc = os.environ['GATEWAY_RULE_TEST_JDBC_URL']
    port = int(os.environ.get('GATEWAY_RULE_TEST_HTTP_PORT', '58091'))
    key = uuid.uuid4().hex
    env = {**os.environ, 'GATEWAY_POSTGRES_JDBC_URL': jdbc,
           'GATEWAY_POSTGRES_USER': 'postgres', 'GATEWAY_POSTGRES_PASSWORD': '',
           'GATEWAY_RULES_ADMIN_KEY': key, 'GATEWAY_RULES_ENABLED': 'true'}

    def request(method, path, body=None, *, auth=True, match=None):
        headers = {'Content-Type': 'application/json'}
        if auth:
            headers['X-Gateway-Admin-Key'] = key
        if match is not None:
            headers['If-Match'] = match
        req = urllib.request.Request(f'http://127.0.0.1:{port}/api/v1/gateway/rules{path}',
                                     None if body is None else json.dumps(body).encode(),
                                     headers, method=method)
        try:
            response = urllib.request.urlopen(req, timeout=5)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, response.headers, json.load(response)

    with tempfile.TemporaryFile(mode='w+') as logs:
        process = subprocess.Popen([
            'java', '-Dquarkus.http.host=127.0.0.1', f'-Dquarkus.http.port={port}',
            '-Dcamel.main.route-filter-include-pattern=gateway-rules-*',
            '-jar', str(root / 'services/event-gateway/target/quarkus-app/quarkus-run.jar')
        ], env=env, stdout=logs, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 30
            while True:
                try:
                    status, _, _ = request('GET', '')
                    assert status == 200, f'Catalog status {status}'
                    break
                except urllib.error.URLError:
                    if process.poll() is not None or time.monotonic() > deadline:
                        raise RuntimeError('Gateway test process did not become ready')
                    time.sleep(.2)
            assert request('GET', '', auth=False)[0] == 401
            rule = {'id': 'http_' + uuid.uuid4().hex, 'stage': 'ENRICHMENT',
                    'enabled': True, 'priority': 10, 'match': {}, 'set': {'site': 'MX'}}
            assert request('POST', '/validate', rule)[0] == 200
            status, headers, _ = request('POST', '', rule)
            assert status == 201 and headers['ETag'] == '"1"'
            assert request('POST', '', rule)[0] == 409
            path = '/' + rule['id']
            assert request('GET', path)[2]['revision'] == 1
            assert request('GET', '/missing_' + uuid.uuid4().hex)[0] == 404
            event = {'resource': 'host', 'summary': 'alert', 'severity': 5, 'status': 'PROBLEM'}
            status, _, result = request('POST', '/simulate', {'event': event, 'rules': [rule]})
            assert status == 200 and result['mode'] == 'SIMULATION'
            assert result['event']['enrichment']['base']['site'] == 'MX'
            assert result['event']['originalEvent'] == event
            rule['enabled'] = False
            assert request('PUT', path, rule)[0] == 428
            assert request('PUT', path, rule, match='"1"')[0] == 200
            assert request('PUT', path, rule, match='"1"')[0] == 409
            assert len(request('GET', path + '/history')[2]['items']) == 2
            status, _, result = request('POST', '/simulate', {'event': event, 'rules': [rule]})
            assert status == 200 and 'enrichment' not in result['event']
            print('PASS: packaged HTTP routes, authentication, CRUD, ETag conflicts, history, simulation and disable')
        except Exception:
            logs.seek(0)
            print(logs.read()[-6000:])
            raise
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


if __name__ == '__main__':
    main()
