"""Read-only ESS console smoke test; reports counts/booleans only, no records or tokens."""
import json,re,urllib.request,urllib.error,urllib.parse
from pathlib import Path
BASE='http://127.0.0.1:8090'
def get(path,expected=200,method='GET'):
 req=urllib.request.Request(BASE+path,method=method)
 try:
  with urllib.request.urlopen(req,timeout=15) as response: status=response.status;raw=response.read();headers=response.headers
 except urllib.error.HTTPError as e:status=e.code;raw=e.read();headers=e.headers
 assert status==expected,('unexpected_status',status,expected)
 assert 'no-store' in headers.get('Cache-Control',''), 'missing_no_store'
 for token in tokens: assert token.encode() not in raw, 'secret_in_response'
 return json.loads(raw) if expected==200 else None
root=Path(__file__).resolve().parents[3]
secrets=json.loads((root/'.local/ess-admin/tokens.json').read_text());tokens=[*secrets['tenants'].values(),secrets['operatorToken']]
report={'tenantIsolation':True,'noTokensInResponses':True,'getOnly':True,'tenants':[]}
config=get('/api/ess/config')
for tenant in config['tenants']:
 params=urllib.parse.urlencode({'tenant':tenant,'limit':1})
 data=get('/api/ess/events?'+params)
 assert all(r['tenant']==tenant for r in data['items'])
 result={'tenant':tenant,'firstPageCount':len(data['items']),'hasNext':bool(data['nextCursor'])}
 if data['nextCursor']:
  following=get('/api/ess/events?'+params+'&'+urllib.parse.urlencode({'after':data['nextCursor']}))
  assert all(r['tenant']==tenant and r['event_key']!=data['nextCursor'] for r in following['items'])
 if data['items']:
  key=data['items'][0]['event_key'];query=urllib.parse.urlencode({'tenant':tenant,'eventKey':key})
  event=get('/api/ess/event?'+query);assert event['tenant']==tenant
  history=get('/api/ess/history?'+query+'&limit=1&afterVersion=0')
  if history['nextVersion']:
   hnext=get('/api/ess/history?'+query+'&limit=1&afterVersion='+str(history['nextVersion']))
   assert all(r['aggregate_version']>history['nextVersion'] for r in hnext['items'])
  other=next((t for t in config['tenants'] if t!=tenant),None)
  if other:get('/api/ess/event?'+urllib.parse.urlencode({'tenant':other,'eventKey':key}),404)
  result['detail']=True;result['history']=True
 report['tenants'].append(result)
get('/api/ess/events?tenant=not-authorized',403)
get('/api/ess/events?'+urllib.parse.urlencode({'tenant':config['tenants'][0],'limit':101}),400)
get('/api/ess/events?tenant='+config['tenants'][0]+'&url=http://invalid.example',400)
get('/api/ess/replay',404)
for method in ['POST','PUT','DELETE','PATCH']:get('/api/ess/events',405,method)
if config['operatorQuarantine']:
 summary=get('/api/ess/operator/quarantine');assert summary['scope']=='operator-global'
 assert all(set(r)=={'reason','count','last_seen_at'} for r in summary['items'])
 report['globalQuarantineReasons']=len(summary['items'])
 get('/api/ess/operator/quarantine?tenant='+config['tenants'][0],400)
html=urllib.request.urlopen(BASE+'/',timeout=10).read().decode()
for path in re.findall(r'(?:src|href)="(/assets/[^\"]+)"',html):
 content=urllib.request.urlopen(BASE+path,timeout=10).read()
 for token in tokens:assert token.encode() not in content,'secret_in_bundle'
report['noTokensInBundles']=True
folder=root/'evidences/ess-ui/20260910';folder.mkdir(parents=True,exist_ok=True)
(folder/'live-report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
