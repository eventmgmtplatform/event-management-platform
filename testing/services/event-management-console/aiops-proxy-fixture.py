"""Scoped browser fault fixture; never changes the shared mock or Processor."""
import pathlib,subprocess,sys
root=pathlib.Path(__file__).resolve().parents[3]
config=(root/'services/event-management-console/nginx.conf').read_text()
if sys.argv[1]=='install':
    template='''  location = /api/processor/v1/aiops/bridge-ui{suffix} {{
    default_type application/json;
    set $fixture_scope "$http_x_tenant_id:$request_method";
    if ($fixture_scope = "aiops-ui-20260910-1255:{method}") {{ return 503 '{{"errorCode":"AIOPS_PROVIDER_UNAVAILABLE"}}'; }}
    resolver 127.0.0.11 valid=10s;
    set $processor_backend http://event-processor:8082;
    rewrite ^/api/processor/v1/(.*)$ /api/v1/$1 break;
    proxy_pass $processor_backend;
    proxy_set_header X-Tenant-Id $http_x_tenant_id;
    proxy_set_header X-Actor-Id $http_x_actor_id;
    proxy_set_header If-Match $http_if_match;
    proxy_set_header Idempotency-Key "";
    client_max_body_size 32k;
    proxy_read_timeout 10s;
    limit_except GET POST PUT DELETE {{ deny all; }}
  }}
'''
    config=config.replace('  location /api/ { return 404; }',template.format(suffix='/assessments',method='POST')+template.format(suffix='',method='PUT')+'  location /api/ { return 404; }')
elif sys.argv[1]!='restore':raise SystemExit('install or restore')
p=pathlib.Path('/tmp/aiops-browser-nginx.conf');p.write_text(config)
subprocess.run(['docker','cp',str(p),'event-management-console:/etc/nginx/conf.d/default.conf'],check=True)
subprocess.run(['docker','exec','event-management-console','nginx','-t'],check=True)
subprocess.run(['docker','exec','event-management-console','nginx','-s','reload'],check=True)
print('AIOps scoped fixture '+sys.argv[1])
