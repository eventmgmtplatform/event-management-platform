"""Temporary UI fault/legacy fixture scoped to synthetic tenant and explicit test URL.
Does not change Processor, catalogs, SQL, or provider mocks. Restore in a finally block.
"""
from pathlib import Path
import json, subprocess, sys
ROOT=Path(__file__).resolve().parents[3]
source=ROOT/'services/event-management-console/nginx.conf'
target=Path('/tmp/blackouts-ui-fixture.conf')
mode=sys.argv[1]
text=source.read_text()
if mode=='install':
    marker='    set $processor_backend http://event-processor:8082;'
    text=text.replace(marker,'''    if ($http_x_tenant_id = "blackout-ui-20260910-1058") { return 503 '{"errorCode":"UI_TEST_OUTAGE"}'; }
'''+marker)
    fixture=json.dumps({'items':[{'id':'legacy-ui-fixture','name':'TEST FIXTURE — Legacy blackout','tenant':'blackout-ui-fixture','source':'Catálogo PostgreSQL','enabled':True,'start_at':'2026-09-10T18:00:00Z'}],'truncated':True,'mode':'read_only'},ensure_ascii=False)
    text=text.replace('  location /api/catalog/ {', '''  location = /api/catalog/views/blackouts {
    if ($http_referer ~ "blackout-legacy-test=1") { return 200 '%s'; }
    resolver 127.0.0.11 valid=10s;
    set $catalog_backend http://console-catalog-api:8094;
    proxy_pass $catalog_backend;
    add_header Cache-Control "no-store" always;
  }

  location /api/catalog/ {'''%fixture)
elif mode!='restore':raise SystemExit('install | restore')
target.write_text(text)
subprocess.run(['docker','cp',str(target),'event-management-console:/etc/nginx/conf.d/default.conf'],check=True)
subprocess.run(['docker','exec','event-management-console','nginx','-t'],check=True)
subprocess.run(['docker','exec','event-management-console','nginx','-s','reload'],check=True)
print('Blackouts UI fixture:',mode)
