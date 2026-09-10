#!/usr/bin/env python3
"""Auto-suppression REST lifecycle and actual Gateway/Kafka suppression acceptance."""
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path
import urllib.error
import uuid

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('maintenance_e2e',ROOT/'testing/e2e/blackout.py')
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)


def main():
    tenant='suppression-cert-'+uuid.uuid4().hex[:16]
    output=ROOT/'evidences/auto-suppression'/tenant;output.mkdir(parents=True)
    report={'status':'RUNNING','tenant':tenant,'scope':'local SUPPRESSION backend; no frontend or external synchronization','checks':[]}
    headers={'X-Tenant-Id':tenant,'X-Actor-Id':'suppression-certification'}
    api='http://127.0.0.1:8082/api/v1';owned=[];exchanges=[]
    start=dt.datetime.now(dt.timezone.utc)-dt.timedelta(minutes=1);end=start+dt.timedelta(minutes=30)

    def call(method,path,body=None,revision=None,key=None,expected=200,customer=None):
        h={**headers}
        if customer:h['X-Tenant-Id']=customer
        if revision is not None:h['If-Match']='"'+str(revision)+'"'
        if key:h['Idempotency-Key']=key
        try:code,data,_=shared.http(method,api+path,body,h)
        except urllib.error.HTTPError as error:code=error.code;data=json.load(error)
        exchanges.append({'method':method,'path':path,'status':code,'request':body,'response':data})
        assert code==expected,(path,expected,code,data)
        return data

    def rule(version,status,source='CHANGE'):
        return {'id':'maintenance','version':version,'type':'SUPPRESSION','enabled':True,'priority':10,
                'source':source,'externalStatus':status,'scope':{'customerCode':tenant,'node':'router-1'},
                'schedule':{'timezone':'America/Mexico_City','validFrom':start.isoformat(),'validTo':end.isoformat()},
                'reason':'Synthetic acceptance','metadata':{'owner':'testing','externalReference':'change-synthetic'}}

    def save(definition,revision):
        body={'rule':definition,'reason':'Synthetic version'};key=uuid.uuid4().hex
        result=call('POST','/rules',body,revision,key,201)
        assert call('POST','/rules',body,revision,key,201)==result
        if definition['id'] not in owned:owned.append(definition['id'])
        return result

    def transition(action,version,revision,expected=200):
        return call('POST','/rules/maintenance/'+action,{'version':version,'reason':'Synthetic transition'},revision,uuid.uuid4().hex,expected)

    def simulate(at,match,node='router-1',customer=tenant):
        event={'schemaVersion':'1.1','eventId':uuid.uuid4().hex,'eventKey':node,'tenant':{'code':customer},
               'resource':{'name':node},'summary':'Synthetic suppression','lifecycleAction':'OPEN','effectiveSeverity':3,
               'timestamps':{'receivedAt':at.isoformat()}}
        result=call('POST','/simulations',{'event':event,'evaluatedAt':at.isoformat()},customer=customer)
        stage=next(s for s in result['stages'] if s['stage']=='AutoSuppression')
        assert stage['match']==match
        assert result['directive']==('SUPPRESS_INTEGRATIONS' if match=='MATCH' else 'CONTINUE')
        return stage

    try:
        call('POST','/rules/validate',{'rule':rule(1,'ACTIVE','MANUAL')})
        save(rule(1,'ACTIVE','MANUAL'),0);simulate(start,'NO_MATCH');transition('enable',1,1)
        for at,match in [(start-dt.timedelta(microseconds=1),'NO_MATCH'),(start,'MATCH'),(end-dt.timedelta(microseconds=1),'MATCH'),(end,'NO_MATCH')]:simulate(at,match)
        stage=simulate(start,'MATCH');assert stage['evidence']['suppression.0.externalReference']=='change-synthetic'
        simulate(start,'NO_MATCH',node='other');simulate(start,'NO_MATCH',customer=tenant+'-other')
        call('GET','/rules/maintenance',customer=tenant+'-other',expected=404)
        revision=2
        for version,status,source in [(2,'APPROVED','MAINTENANCE'),(3,'CANCELLED','CHANGE'),(4,'COMPLETED','CHANGE')]:
            previous=simulate(start,'MATCH' if version<4 else 'NO_MATCH')
            save(rule(version,status,source),revision);revision+=1
            current=call('GET','/rules/maintenance');assert current['activeVersion']==version-1
            assert simulate(start,previous['match'])['ruleVersion']==previous['ruleVersion']
            transition('enable',version,revision-1,409);transition('enable',version,revision);revision+=1
            simulate(start,'MATCH' if status=='APPROVED' else 'NO_MATCH')
        transition('disable',4,revision);revision+=1;transition('retire',4,revision);revision+=1
        transition('enable',4,revision,409)
        assert len(call('GET','/rules/maintenance/history')['items'])==revision
        report['checks'].append('idempotent create/versioning, active/latest separation, stale writes, disable/retire/history and tenant isolation')
        report['checks'].append('MANUAL/MAINTENANCE/CHANGE; ACTIVE/APPROVED match, CANCELLED/COMPLETED never match; exact interval and external reference evidence')
        for field,value in [('source','UNKNOWN'),('externalStatus','PENDING'),('scope',{'customerCode':tenant+'-other'}),('schedule',{'timezone':'UTC','validFrom':start.isoformat()})]:
            invalid=rule(1,'ACTIVE');invalid['id']='invalid-'+field;invalid[field]=value
            call('POST','/rules',{'rule':invalid,'reason':'Invalid fixture'},0,uuid.uuid4().hex,422)
            call('GET','/rules/'+invalid['id'],expected=404)
        _,page,_=shared.http('GET','http://127.0.0.1:8090/api/catalog/views/auto-suppression')
        rows=[r for r in page['items'] if r['tenant']==tenant];assert len(rows)==1 and rows[0]['status']=='RETIRED'
        report['checks'].append('invalid status/source/tenant/unbounded interval rejected; persisted catalog lists retired version')
        runtime=output/'runtime';runtime.mkdir();runtime_report={}
        try:shared.run(runtime,runtime_report,capability='SUPPRESSION')
        finally:(runtime/'report.json').write_text(json.dumps(runtime_report,indent=2)+'\n')
        report['checks'].append('Gateway/Kafka/Processor: eligible route suppressed, normalized publication, recovery/correlation retained, other resource and disabled negative controls; no integration commands')
        report['status']='PASS'
    except Exception as error:report['status']='FAIL';report['error']=str(error);raise
    finally:
        errors=[]
        for id in owned:
            try:
                current=call('GET','/rules/'+id)
                if current['status']=='ENABLED':transition('disable',current['activeVersion'],current['revision'])
            except Exception as error:errors.append(str(error))
        report['cleanupErrors']=errors
        if errors:report['status']='FAIL'
        (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
        (output/'http-exchanges.json').write_text(json.dumps(exchanges,indent=2)+'\n')
        (output/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(output))+'\n' for p in sorted(output.rglob('*')) if p.is_file() and p.name!='SHA256SUMS'))
        print(output)
        if report['status']!='PASS':raise RuntimeError('SUPPRESSION_CERTIFICATION_FAILED')


if __name__=='__main__':main()
