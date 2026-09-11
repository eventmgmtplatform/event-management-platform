"""Scoped browser acceptance fixture. install temporarily changes UI proxy only; restore afterward."""
from pathlib import Path
import json,subprocess,sys
root=Path(__file__).resolve().parents[3]
text=(root/'services/event-management-console/nginx.conf').read_text()
if sys.argv[1]=='install':
    text=text.replace('    set $processor_backend http://event-processor:8082;', '''    if ($http_x_tenant_id = "inventory-ui-20260910-1140") { return 503 '{"errorCode":"UI_TEST_OUTAGE"}'; }
    if ($http_x_tenant_id = "suppression-ui-20260910-1148") { return 503 '{"errorCode":"UI_TEST_OUTAGE"}'; }
    set $processor_backend http://event-processor:8082;''')
    data=json.dumps({'items':[{'id':'legacy-fixture','tenant':'inventory-ui-fixture','name':'TEST FIXTURE — Legacy inventory','enabled':True,'resource_type':'router'}],'truncated':False,'mode':'read_only'},ensure_ascii=False)
    text=text.replace('  location /api/catalog/ {', '''  location = /api/catalog/views/inventory-services {
    if ($http_referer ~ "inventory-legacy-test=1") { return 200 '%s'; }
    resolver 127.0.0.11 valid=10s;
    set $catalog_backend http://console-catalog-api:8094;
    proxy_pass $catalog_backend;
    add_header Cache-Control "no-store" always;
  }
  location /api/catalog/ {'''%data)
elif sys.argv[1]!='restore':raise SystemExit('install | restore')
p=Path('/tmp/inventory-suppression-ui-fixture.conf');p.write_text(text)
for command in [['docker','cp',str(p),'event-management-console:/etc/nginx/conf.d/default.conf'],['docker','exec','event-management-console','nginx','-t'],['docker','exec','event-management-console','nginx','-s','reload']]:subprocess.run(command,check=True)
print(sys.argv[1])
