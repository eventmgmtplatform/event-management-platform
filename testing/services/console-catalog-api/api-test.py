"""Real HTTP/PG catalog contract test. Creates and removes only unique test records."""
import json,urllib.request,urllib.error,uuid
BASE='http://127.0.0.1:8090/api/catalog/'
def request(path, method='GET', data=None, status=200, headers=None):
    h={'Content-Type':'application/json','X-Console-Action':'1'}
    if headers:h.update(headers)
    req=urllib.request.Request(BASE+path,data=None if data is None else json.dumps(data).encode(),headers=h,method=method)
    try:
        with urllib.request.urlopen(req,timeout=25) as r: code=r.status; result=json.load(r)
    except urllib.error.HTTPError as e: code=e.code;result=json.load(e)
    assert code==status,(method,path,code,result)
    return result
code='TEST-CONSOLE-'+uuid.uuid4().hex[:10]
c=dict(customer_code=code,customer='Test customer',bamid='test-bam',gnmorgid='test-org',snow_company_id='',snow_assignment_group='',gnm_assignment_group='',cacf_assignment_group='',chatops_team='',aiops_extension='',timezone='America/Mexico_City',enabled=True)
fid=None
try:
    c=request('customers','POST',c,201)['item']
    request('customers','POST',c,409)
    f=dict(name='Test filter',description='Temporary contract test',customer_code=code,applid=None,filter_state=1,filter_weight=0,severities=[4,5],criteria={'Service':{'operator':'eq_ci','value':'test'}},targets=[dict(target=t,behavior='enable',assignmentGroup='TEST-GROUP',actionReference='TEST-EXT',delaySeconds=2,dependsOnTicketing=t in ('gnm','cacf','chatops')) for t in ['gnm','snow','cacf','chatops','extensions']])
    f=request('filters','POST',f,201)['item'];fid=f['filter_id']
    assert len(request('filters?q='+code)['items'])==1
    assert any(x['filter_id']==fid for x in request('filters?q=TEST-GROUP')['items'])
    request('customers/'+code,'DELETE',c,409)
    original=dict(f); f['filter_state']=0; f['targets'][0]['assignmentGroup']='CHANGED'
    f=request('filters/'+fid,'PUT',f)['item']
    assert request('filters/'+fid)['filter_state']==0
    request('filters/'+fid,'PUT',original,409)
    bad=dict(f,targets=[dict(f['targets'][0],target='invalid')])
    request('filters/'+fid,'PUT',bad,400)
    assert len(request('filters/'+fid)['targets'])==5
    request('filters/'+fid,'PUT',f,403,{'Origin':'http://external.invalid'})
    request('filters/'+fid,'DELETE',f)
    fid=None
    request('filters/'+f['filter_id'],status=404)
    c['bamid']='changed-bam'; c=request('customers/'+code,'PUT',c)['item']
    assert request('customers/'+code)['bamid']=='changed-bam'
    print('PASS: PostgreSQL CRUD, 5 targets, ticket dependencies, search, stale update, atomic invalid update, referenced customer protection, origin validation.')
finally:
    if fid:
        current=request('filters/'+fid);request('filters/'+fid,'DELETE',current)
    current=request('customers/'+code);request('customers/'+code,'DELETE',current)
