"""HTTP API registry and bounded, non-mutating availability probes."""
import concurrent.futures
import datetime
import time
import urllib.request
import urllib.error
import control

# URLs are server-owned: callers cannot supply URLs, commands or Compose services.
REGISTRY = [
    ('gateway','event-gateway','http://event-gateway:8081/api/v1/gateway/ready','Collectors','POST /api/v1/events'),
    ('processor','event-processor','http://event-processor:8082/health/ready','Gateway / administration','GET /api/v1/rules'),
    ('worker','integration-worker','http://integration-worker:8083/health/live','Event processor / CACF','POST /api/v1/automations'),
    ('state','event-state-service','http://event-state-service:8084/health/ready','Event processor / dashboards','Event state'),
    ('ticketing','servicenow-mock','http://servicenow-mock:8080/__admin/health','Integration worker / Ticketing','/api/now/table/incident'),
    ('gnm','gnm-mock','http://gnm-mock:8080/__admin/health','Integration worker / GNM','Everbridge incident API'),
    ('aiops','aiops-mock','http://aiops-mock:8080/__admin/health','Event processor / AIOps','Assessment API'),
    ('ticketing-console','servicenow-console-mock','http://servicenow-console-mock:8080/health','WebUI / Ticketing','/api/now/table/incident'),
    ('next','next-mock','http://next-mock:8080/__admin/health','Integration worker / CACF','NEXT automation API'),
    ('catalog','console-catalog-api','http://console-catalog-api:8094/health','WebUI / configuration','Catalog API'),
    ('dashboards','oem-dashboards-api','http://oem-dashboards-api:8092/ready','OEM dashboards','GET /api/dashboards/{domain}'),
    ('management','frontend-management-api','http://frontend-management-api:8093/health','WebUI / administration','/api/administration/*'),
    ('search','opensearch','http://opensearch:9200/_cluster/health','Event state / search','OpenSearch REST API'),
]
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs): return None

def probe(entry):
    key, service, url, consumers, endpoint = entry
    start=time.monotonic()
    code=None
    try:
        with urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect()).open(urllib.request.Request(url,method='GET'), timeout=2) as response:
            code=response.status
        status='UP' if 200<=code<300 else 'DOWN'
    except urllib.error.HTTPError as exc:
        code=exc.code;status='DOWN'
    except (OSError,ValueError):status='UNREACHABLE'
    return dict(id=key,service=service,consumers=consumers,endpoint=endpoint,probeUrl=url,status=status,httpStatus=code,latencyMs=round((time.monotonic()-start)*1000),checkedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),actions=['testing']+(['stop','start','reboot'] if service in control.SERVICES else []))

def snapshot():
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        rows=list(pool.map(probe,REGISTRY))
    return {'apis':rows,'observedAt':datetime.datetime.now(datetime.timezone.utc).isoformat()}

def action(payload):
    if not isinstance(payload,dict) or set(payload)!={'id','apiId','action'}:raise ValueError('invalid_request')
    entry=next((e for e in REGISTRY if e[0]==payload['apiId']),None)
    if not entry:raise ValueError('unknown_api')
    if payload['action']=='testing':return 200,probe(entry)
    operation={'id':payload['id'],'service':entry[1],'action':{'reboot':'restart','start':'start','stop':'stop'}.get(payload['action'])}
    return 202,control.submit(operation)
