"""Verify the public reboot action and save its persisted operation evidence."""
import json,time,uuid
from pathlib import Path
from urllib.request import Request,urlopen
BASE='http://127.0.0.1:8091'
def request(path,payload=None):
    data=None if payload is None else json.dumps(payload).encode()
    with urlopen(Request(BASE+path,data=data,headers={'Content-Type':'application/json','X-Console-Action':'1','Origin':BASE}),timeout=15) as r:return json.load(r)
id=str(uuid.uuid4())
op=request('/api/administration/apis/actions',{'id':id,'apiId':'ticketing-console','action':'reboot'})
print('Reboot submitted: '+id,flush=True)
for _ in range(180):
    op=request('/api/administration/operations/'+id)
    if op['status']!='running':break
    time.sleep(2)
report={'operation':op,'apis':request('/api/administration/apis'),'collection':{}}
assert op['status']=='succeeded',op
assert next(a for a in report['apis']['apis'] if a['id']=='ticketing-console')['status']=='UP'
for scope in ('today','history'):
    data=request('/api/dashboards/data-collection?scope='+scope)
    report['collection'][scope]={'total':data['total'],'counts':data['counts'],'source':data['source']}
Path('.local/oem-dashboards/collection-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'operation':op,'apisUp':sum(a['status']=='UP' for a in report['apis']['apis']),'collection':report['collection']},indent=2),flush=True)
