"""Read-only durable verification of records created by the browser walkthrough."""
import json, urllib.request, urllib.error
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
base='http://127.0.0.1:8090'
tenant='blackout-ui-20260910-1058'
def read(path, tenant=tenant):
    with urllib.request.urlopen(urllib.request.Request(base+path,headers={'X-Tenant-Id':tenant}),timeout=15) as r:
        return {'status':r.status,'etag':r.headers.get('ETag'),'cacheControl':r.headers.get('Cache-Control'),'data':json.load(r)}
report={'tenant':tenant,'mode':'read-only verification of browser writes','records':{}}
for id,expected in [('manual-router',6),('retry-immediate',2)]:
    record=read('/api/processor/v1/rules/'+id);history=read('/api/processor/v1/rules/'+id+'/history?limit=50&after=0')
    assert record['data']['status']=='RETIRED'
    assert record['data']['activeVersion'] is None
    assert record['etag']=='"'+str(expected)+'"'
    assert len(history['data']['items'])==expected
    assert len([x for x in history['data']['items'] if x['version']==1 and x['status']=='CREATED'])==1
    assert all(x['actor']=='local-console' for x in history['data']['items'])
    report['records'][id]={'record':record,'history':history}
report['catalog']=read('/api/catalog/views/blackouts')['data']
assert all(any(r['id']==id and r['tenant']==tenant and r['status']=='RETIRED' for r in report['catalog']['items']) for id in report['records'])
report['catalog']={'testedRecordsPresent':True,'truncated':report['catalog']['truncated']}
report['status']='PASS'
out=ROOT/'evidences/blackouts/frontend-20260910/server-after-browser.json';out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'status':'PASS','records':len(report['records']),'output':str(out)}))
