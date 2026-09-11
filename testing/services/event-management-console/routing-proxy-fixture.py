"""Temporary 503 for one synthetic tenant; never changes Processor or providers."""
import pathlib, subprocess, sys
root=pathlib.Path(__file__).resolve().parents[3]
config=(root/'services/event-management-console/nginx.conf').read_text()
if sys.argv[1]=='install':
    location='''  location = /api/processor/v1/simulations {
    default_type application/json;
    if ($http_x_tenant_id = "routing-ui-20260910-1340") { return 503 '{"errorCode":"ROUTING_LOCAL_FIXTURE_UNAVAILABLE"}'; }
    resolver 127.0.0.11 valid=10s;
    set $processor_backend http://event-processor:8082;
    rewrite ^/api/processor/v1/(.*)$ /api/v1/$1 break;
    proxy_pass $processor_backend;
    proxy_set_header X-Tenant-Id $http_x_tenant_id;
    proxy_set_header X-Actor-Id $http_x_actor_id;
    client_max_body_size 1m;
    proxy_read_timeout 15s;
    limit_except POST { deny all; }
  }
'''
    config=config.replace('  location /api/ { return 404; }',location+'  location /api/ { return 404; }')
elif sys.argv[1]!='restore': raise SystemExit('install or restore')
p=pathlib.Path('/tmp/routing-browser-nginx.conf');p.write_text(config)
subprocess.run(['docker','cp',str(p),'event-management-console:/etc/nginx/conf.d/default.conf'],check=True)
subprocess.run(['docker','exec','event-management-console','nginx','-t'],check=True)
subprocess.run(['docker','exec','event-management-console','nginx','-s','reload'],check=True)
