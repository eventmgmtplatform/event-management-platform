"""Deploy only OEM dashboards; prepare additive schema and opt-in isolated demo rows."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
from urllib.parse import quote
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / '.local/oem-dashboards'


def run(args, text=None):
    result = subprocess.run(args, input=text, text=True, capture_output=True, cwd=ROOT)
    if result.returncode:
        # Do not include subprocess arguments/input/output: they may contain credentials.
        raise RuntimeError('Deployment command failed: ' + args[0])
    return result.stdout.strip()


def sql(statement):
    return run(['docker', 'exec', '-i', 'event-postgres', 'sh', '-c',
                'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'], statement)


def cli(*args):
    return run(['bash', 'scripts/emctl', 'ui', 'oem-dashboards', *args])


def main():
    os.umask(0o077)
    PRIVATE.mkdir(parents=True, exist_ok=True)
    previous_image = run(['docker', 'inspect', '--format', '{{.Image}}', 'event-management-console'])
    (PRIVATE / 'console-previous-image.txt').write_text(previous_image + '\n')
    run(['docker', 'image', 'tag', previous_image, 'event-management/event-management-console:before-dashboards'])
    before = run(['docker', 'exec', 'event-postgres', 'sh', '-c',
                  'exec pg_dump --schema-only -U "$POSTGRES_USER" -d "$POSTGRES_DB"'])
    (PRIVATE / ('schema-before-' + time.strftime('%Y%m%dT%H%M%S') + '.sql')).write_text(before)
    for name in ('008-cacf-core.sql', '017-ess-lifecycle.sql', '018-oem-dashboard-views.sql', '022-delivery-filter-catalog.sql'):
        sql((ROOT / 'infrastructure/postgres/init' / name).read_text())
        print('Migration ready: ' + name, flush=True)
    config = PRIVATE / 'runtime.env'
    if not config.exists():
        password = secrets.token_urlsafe(36)
        database = sql('SELECT current_database();').splitlines()[0]
        sql("""DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='oem_dashboards_ui')
            THEN CREATE ROLE oem_dashboards_ui LOGIN; END IF; END $$;
            ALTER ROLE oem_dashboards_ui PASSWORD '""" + password + """';
            ALTER ROLE oem_dashboards_ui SET default_transaction_read_only = on;
            GRANT oem_dashboard_reader TO oem_dashboards_ui;""")
        config.write_text('OEM_POSTGRES_DSN=postgresql://oem_dashboards_ui:' + quote(password, safe='') + '@postgres:5432/' + quote(database, safe='') + '\nOEM_DASHBOARD_PORT=8091\n')
        config.chmod(0o600)
    sql((ROOT / 'scripts/dashboards/seed-demo.sql').read_text())
    sql((ROOT / 'scripts/dashboards/seed-delivery-demo.sql').read_text())
    print('Demo rows ready in isolated dashboard_demo schema.', flush=True)
    # Build output has no private environment values. Reuse the existing Nginx container.
    result = subprocess.run(['bash', 'scripts/emctl', 'ui', 'oem-dashboards', 'start'], cwd=ROOT)
    if result.returncode:
        raise RuntimeError('Dashboard build/start failed; additive DB data retained for retry.')
    for _ in range(40):
        try:
            cli('source', 'set', 'postgresql')
            cli('smoke-test')
            break
        except RuntimeError:
            time.sleep(2)
    else:
        raise RuntimeError('Dashboard data readiness did not become healthy.')
    report = {'url': 'http://localhost:8091', 'source': 'postgresql', 'demoTenant': 'DEMO-DASHBOARDS', 'domains': {}}
    for domain in ('events','ticketing','gnm','cacf','delivery'):
        with urlopen('http://127.0.0.1:8091/api/dashboards/' + domain + ('?customer=DEMO-DASHBOARDS' if domain == 'delivery' else '?tenant=DEMO-DASHBOARDS'), timeout=15) as response:
            data = json.load(response)
        if data['source'] != 'postgresql' or data['total'] == 0:
            raise RuntimeError('Demo readiness failed for ' + domain)
        report['domains'][domain] = {'count': data['total'], 'states': data['facets']['states'] if domain == 'delivery' else data['counts']}
    with urlopen('http://127.0.0.1:8091/health', timeout=5) as response:
        report['health'] = json.load(response)
    (PRIVATE / 'deployment.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
