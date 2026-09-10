"""Live byte-for-byte ingress check and demo API lifecycle smoke test."""
import base64,hashlib,json,time,uuid
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.error import HTTPError,URLError
BASE='http://127.0.0.1:8091'
def get(path):
    with urlopen(BASE+path,timeout=15) as r:return json.load(r)
def post(path,body):
    req=Request(BASE+path,data=json.dumps(body).encode(),headers={'Content-Type':'application/json','X-Console-Action':'1','Origin':BASE})
    with urlopen(req,timeout=15) as r:return json.load(r)
for attempt in range(60):
    try:
        with urlopen('http://127.0.0.1:8081/api/v1/gateway/ready',timeout=3) as r:assert json.load(r)['status']=='UP'
        get('/api/dashboards/data-collection');break
    except (OSError,ValueError,AssertionError):time.sleep(2)
else:raise RuntimeError('Deployment readiness timeout')
raw=b'  {"test":"Data Collection deployment validation", "invalid":true} \n'
try:urlopen(Request('http://127.0.0.1:8081/api/v1/events',data=raw,headers={'Content-Type':'application/json'}),timeout=15);raise AssertionError('Invalid event accepted')
except HTTPError as e:
    assert e.code==400,e.code
    receipt=e.headers['X-Gateway-Receipt-Id'];assert receipt
result=get('/api/dashboards/data-collection?scope=all&receipt='+receipt)
assert result['rows'][0]['status']=='REJECTED',result['rows'][0]['status']
assert base64.b64decode(result['original']['base64'])==raw
assert result['rows'][0]['sha256']==hashlib.sha256(raw).hexdigest()
print('Original rejection archived: byte-for-byte and SHA-256 verified.',flush=True)
fixture=json.loads(Path('testing/fixtures/events/sdc/zabbix-messagebus-recovery.json').read_text())
fixture.update(CustomerCode='DEMO-DASHBOARDS',Node='collection-smoke-'+uuid.uuid4().hex,hostname='collection-smoke',msg='Data Collection validation recovery; no existing incident')
valid_raw=('  '+json.dumps(fixture,ensure_ascii=False)+'\n').encode()
with urlopen(Request('http://127.0.0.1:8081/api/v1/events',data=valid_raw,headers={'Content-Type':'application/json'}),timeout=30) as r:
    assert r.status==202
    accepted_receipt=r.headers['X-Gateway-Receipt-Id']
accepted=get('/api/dashboards/data-collection?scope=all&receipt='+accepted_receipt)
assert accepted['rows'][0]['status']=='PUBLISHED'
assert base64.b64decode(accepted['original']['base64'])==valid_raw
print('Accepted recovery event: durable original and Kafka publication verified.',flush=True)

report={'receipt':receipt,'acceptedReceipt':accepted_receipt,'originalVerified':True,'operations':[]}
for action in ('testing','stop','start','reboot'):
    payload={'id':str(uuid.uuid4()),'apiId':'ticketing-console','action':action}
    op=post('/api/administration/apis/actions',payload)
    if action=='testing':assert op['status']=='UP';continue
    for attempt in range(180):
        op=get('/api/administration/operations/'+op['id'])
        if op['status']!='running':break
        time.sleep(2)
    assert op['status']=='succeeded',op
    probe=post('/api/administration/apis/actions',{'id':str(uuid.uuid4()),'apiId':'ticketing-console','action':'testing'})
    assert (probe['status']!='UP') if action=='stop' else (probe['status']=='UP'),probe
    report['operations'].append({**op,'verifiedStatus':probe['status']})
    print('Lifecycle verified: '+action+' -> '+probe['status'],flush=True)
report['apis']=[{k:r[k] for k in ('id','service','status','httpStatus')} for r in get('/api/administration/apis')['apis']]
for route in ('data-collection','api-management'):
    with urlopen(BASE+'/dashboards/'+route,timeout=10) as r:assert r.status==200
Path('.local/oem-dashboards/collection-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
