"""Activate gateway rules locally, preserving existing rules and other services."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / '.local/gateway-rules'


def run(args, data=None):
    result = subprocess.run(args, input=data, text=True, capture_output=True, cwd=ROOT)
    if result.returncode:
        raise RuntimeError('Command failed: ' + args[0])
    return result.stdout.strip()


def sql(statement):
    return run(['docker', 'exec', '-i', 'event-postgres', 'sh', '-c',
                'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'], statement)


def request(path, key=None, body=None):
    headers = {'Content-Type': 'application/json'}
    if key:
        headers['X-Gateway-Admin-Key'] = key
    req = urllib.request.Request('http://127.0.0.1:8081' + path,
                                 None if body is None else json.dumps(body).encode(), headers)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.load(response)


def main():
    os.umask(0o077)
    PRIVATE.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime('%Y%m%dT%H%M%S')
    before = json.loads(run(['docker', 'inspect', 'event-gateway']))[0]
    labels = before['Config']['Labels']
    compose = ['docker', 'compose', '-p', labels['com.docker.compose.project'], '--env-file', str(ROOT / '.env')]
    dashboard_env = ROOT / '.local/oem-dashboards/runtime.env'
    if dashboard_env.exists():
        compose += ['--env-file', str(dashboard_env)]
    for file in labels['com.docker.compose.project.config_files'].split(','):
        compose += ['-f', file]
    config = PRIVATE / 'runtime.env'
    previous_config = config.read_bytes() if config.exists() else None
    previous_env = dict(item.split('=', 1) for item in before['Config']['Env'])
    credentials = {}
    if config.exists():
        credentials = dict(line.split('=', 1) for line in config.read_text().splitlines() if '=' in line and not line.startswith('#'))
    key = credentials.get('GATEWAY_RULES_ADMIN_KEY') or previous_env.get('GATEWAY_RULES_ADMIN_KEY') or secrets.token_urlsafe(48)
    (PRIVATE / f'previous-image-{stamp}.txt').write_text(before['Image'] + '\n')
    # Build first: runtime and database are untouched if compilation fails.
    subprocess.run(compose + ['build', 'event-gateway'], cwd=ROOT, check=True)
    sql((ROOT / 'infrastructure/postgres/init/026-gateway-rules.sql').read_text())
    print('Migration 026 applied.', flush=True)
    catalog_before = sql('SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY id),\'[]\'::jsonb) FROM event_management.gateway_rule r;')
    (PRIVATE / f'catalog-before-{stamp}.json').write_text(catalog_before + '\n')
    credentials.update(GATEWAY_RULES_ENABLED='true', GATEWAY_RULES_ADMIN_KEY=key)
    config.write_text(''.join(k + '=' + v + '\n' for k, v in credentials.items()))
    config.chmod(0o600)
    try:
        subprocess.run(compose + ['up', '-d', '--no-build', '--no-deps', 'event-gateway'], cwd=ROOT, check=True)
        for _ in range(60):
            try:
                status, catalog = request('/api/v1/gateway/rules', key)
                if status == 200 and request('/api/v1/gateway/ready')[0] == 200:
                    break
            except (OSError, ValueError):
                pass
            time.sleep(2)
        else:
            raise RuntimeError('Gateway readiness timed out')
        assert request('/api/v1/gateway/rules')[0] == 401
        rule = {'id': 'activation-probe', 'stage': 'ENRICHMENT', 'priority': 10,
                'enabled': True, 'match': {}, 'set': {'verification': 'gateway-rules'}}
        assert request('/api/v1/gateway/rules/validate', key, rule)[0] == 200
        event = {'resource': 'gateway-activation-probe', 'summary': 'Simulation only', 'severity': 0, 'status': 'OK'}
        status, simulation = request('/api/v1/gateway/rules/simulate', key, {'event': event, 'rules': [rule]})
        assert status == 200 and simulation['event']['enrichment']['base']['verification'] == 'gateway-rules'
        assert simulation['event']['originalEvent'] == event
        # The input route must still reject an invalid event. It stores a receipt but cannot publish it.
        status, rejected = request('/api/v1/events', body={})
        assert status == 400 and rejected['errorCode'] == 'INVALID_EVENT'
        catalog_after = sql('SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY id),\'[]\'::jsonb) FROM event_management.gateway_rule r;')
        assert catalog_before == catalog_after, 'Catalog changed during activation'
        after = json.loads(run(['docker', 'inspect', 'event-gateway']))[0]
        active_env = dict(item.split('=', 1) for item in after['Config']['Env'])
        assert active_env.get('GATEWAY_RULES_ENABLED') == 'true'
        report = {'status': 'PASS', 'image': after['Image'], 'rulesEnabled': True,
                  'catalogCount': len(catalog['items']), 'catalogUnchanged': True,
                  'authenticatedApi': 200, 'unauthenticatedApi': 401,
                  'simulation': 'PASS', 'invalidIngress': 400,
                  'receiptId': rejected.get('receiptId'), 'otherServicesRecreated': False}
        (PRIVATE / 'deployment.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report, indent=2), flush=True)
    except Exception:
        print('Activation failed; restoring the previous gateway image and rule settings.', flush=True)
        if previous_config is not None:
            config.write_bytes(previous_config)
        else:
            config.write_text('GATEWAY_RULES_ENABLED=' + previous_env.get('GATEWAY_RULES_ENABLED', 'false') + '\n'
                              + 'GATEWAY_RULES_ADMIN_KEY=' + previous_env.get('GATEWAY_RULES_ADMIN_KEY', '') + '\n')
        run(['docker', 'image', 'tag', before['Image'], before['Config']['Image']])
        subprocess.run(compose + ['up', '-d', '--no-build', '--no-deps', 'event-gateway'], cwd=ROOT, check=True)
        raise


if __name__ == '__main__':
    main()
