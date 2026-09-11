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
    ('glpi-ticketing','glpi-ticketing-api','http://glpi-ticketing-api:8095/health','WebUI / GLPI Ticketing','/api/glpi/tickets'),
    ('glpi','glpi-mock','http://glpi-mock:8080/health','Integration worker / GLPI','/apirest.php/Ticket'),
    ('ticketing-console','servicenow-console-mock','http://servicenow-console-mock:8080/health','WebUI / Ticketing','/api/now/table/incident'),
    ('next','next-mock','http://next-mock:8080/__admin/health','Integration worker / CACF','NEXT automation API'),
    ('catalog','console-catalog-api','http://console-catalog-api:8094/health','WebUI / configuration','Catalog API'),
    ('dashboards','oem-dashboards-api','http://oem-dashboards-api:8092/ready','OEM dashboards','GET /api/dashboards/{domain}'),
    ('management','frontend-management-api','http://frontend-management-api:8093/health','WebUI / administration','/api/administration/*'),
    ('search','opensearch','http://opensearch:9200/_cluster/health','Event state / search','OpenSearch REST API'),
]
# Capabilities added after the original service-level inventory. Protected or
# mutating routes use their owner's health probe; never fabricate credentials.
def capability(key, owner, consumers, endpoint, read_path=None):
    base=next(entry for entry in REGISTRY if entry[0]==owner)
    url=base[2] if read_path is None else base[2].split('/',3)[0]+'//'+base[2].split('/',3)[2]+read_path
    REGISTRY.append((key,base[1],url,consumers,endpoint))

capability('gateway-rules','gateway','WebUI / Gateway rules','GET, POST /api/v1/gateway/rules · GET, PUT /{id} · GET /{id}/history')
capability('gateway-validation','gateway','Gateway rule editor','POST /api/v1/gateway/rules/validate')
capability('gateway-simulation','gateway','Gateway rule editor','POST /api/v1/gateway/rules/simulate')
capability('processor-rule-lifecycle','processor','WebUI / Policies, blackouts, suppression','GET, POST /api/v1/rules · GET /{id}/history · POST /{id}/{enable|disable|retire}')
capability('processor-validation','processor','WebUI / Rule editor','POST /api/v1/rules/validate')
capability('processor-simulations','processor','WebUI / Rule editor','POST /api/v1/simulations')
capability('processor-explain','processor','WebUI / Diagnostics','GET /api/v1/explain/{processingId}')
capability('processor-aiops','processor','WebUI / AIOps','GET, POST /api/v1/aiops · GET, PUT, DELETE /{id} · POST /{id}/assessments')
capability('processor-enrichment','processor','WebUI / Enrichment','GET /api/v1/enrichment', '/api/v1/enrichment')
for kind in ('events','event','history','quarantine'):
    capability('state-'+kind,'state','Console Catalog API / ESS','GET /api/v1/state/'+kind)
capability('ess-console','catalog','WebUI / ESS','GET /api/ess/{config|events|event|history|operator/quarantine}', '/api/ess/config')
for kind in ('customers','filters'):
    capability('catalog-'+kind,'catalog','WebUI / Configuration','GET, POST /api/catalog/'+kind+' · GET, PUT, DELETE /{id}', '/api/catalog/'+kind)
for kind in ('inventory-services','inventory-records','enrichment-plans','blackouts','policies','auto-suppression','aiops-extensions'):
    path='/api/catalog/views/'+kind
    capability('catalog-'+kind,'catalog','WebUI / Product views','GET '+path,path)
for kind in ('events','ticketing','gnm','cacf','delivery','data-collection'):
    path='/api/dashboards/'+kind
    capability('dashboard-'+kind,'dashboards','OEM Dashboards','GET '+path,path+'?limit=1')
capability('dashboard-config','dashboards','OEM CLI / Dashboards','GET /api/dashboards/config','/api/dashboards/config')
capability('cacf-metrics','worker','Observability / CACF','GET /metrics')
capability('cacf-automation','worker','CACF / Automation clients','POST /api/v1/automations · GET /{id} · PUT /{id}/ticket')
capability('cacf-callback','worker','NEXT / CACF','POST /api/v1/providers/next/callback · POST /data')
capability('gnm-diagnostics','worker','GNM / Diagnostics','GET /internal/gnm/incidents/{organizationId}/{incidentId}')
capability('integration-control','worker','Operations / Integration worker','GET, PUT /integration/control')
for kind in ('platform','sources','middleware'):
    capability('management-'+kind,'management','WebUI / Administration','GET /api/administration/'+kind)
capability('management-operations','management','WebUI / API Management','POST /api/administration/operations · GET /{id} · POST /api/administration/apis/actions')

def describe(entry, result):
    key,service,url,consumers,endpoint=entry
    return dict(result,id=key,service=service,consumers=consumers,endpoint=endpoint,probeUrl=url,
                probeScope='service' if url.endswith(('/health','/ready','/live','/_cluster/health')) else 'endpoint',
                actions=['testing']+(['stop','start','reboot'] if service in control.SERVICES else []))

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
    return describe(entry,dict(status=status,httpStatus=code,latencyMs=round((time.monotonic()-start)*1000),checkedAt=datetime.datetime.now(datetime.timezone.utc).isoformat()))

def snapshot():
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        # One request per unique health URL even when several APIs share a service.
        unique={entry[2]:entry for entry in REGISTRY}
        results=dict(zip(unique,pool.map(probe,unique.values())))
        rows=[describe(entry,results[entry[2]]) for entry in REGISTRY]
    return {'apis':rows,'observedAt':datetime.datetime.now(datetime.timezone.utc).isoformat()}

def action(payload):
    if not isinstance(payload,dict) or set(payload)!={'id','apiId','action'} or not all(isinstance(v,str) for v in payload.values()):raise ValueError('invalid_request')
    entry=next((e for e in REGISTRY if e[0]==payload['apiId']),None)
    if not entry:raise ValueError('unknown_api')
    if payload['action']=='testing':return 200,probe(entry)
    operation={'id':payload['id'],'service':entry[1],'action':{'reboot':'restart','start':'start','stop':'stop'}.get(payload['action'])}
    return 202,control.submit(operation)
