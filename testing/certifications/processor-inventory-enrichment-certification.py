#!/usr/bin/env python3
"""Real API, PostgreSQL and Gateway/Kafka acceptance; no frontend mutation."""
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
API = 'http://127.0.0.1:8082/api/v1'


def main():
    tenant = 'inventory-cert-' + uuid.uuid4().hex[:16]
    output = ROOT/'evidences/inventory-enrichment'/tenant
    output.mkdir(parents=True)
    report = {'status': 'RUNNING', 'tenant': tenant, 'scope': 'versioned local inventory/enrichment backend', 'checks': []}
    exchanges, owned = [], []
    node = tenant + '-node'

    def http(method, url, expected=200, body=None, headers=None):
        req = urllib.request.Request(url, method=method, headers={'Content-Type':'application/json', **(headers or {})},
                                     data=None if body is None else json.dumps(body).encode())
        try: response = urllib.request.urlopen(req, timeout=15)
        except urllib.error.HTTPError as error: response = error
        with response:
            raw = response.read(); data = json.loads(raw) if raw else None
            exchanges.append({'method':method,'url':url,'status':response.status,'request':body,'response':data})
            assert response.status == expected, (url,expected,response.status,data)
            return data

    def call(method, path, expected=200, body=None, revision=None, key=None, customer=tenant):
        headers={'X-Tenant-Id':customer,'X-Actor-Id':'inventory-certification'}
        if revision is not None: headers['If-Match']='"'+str(revision)+'"'
        if key is not None: headers['Idempotency-Key']=key
        return http(method,API+path,expected,body,headers)

    def save(rule, revision=0):
        key=uuid.uuid4().hex; body={'rule':rule,'reason':'Inventory/enrichment synthetic acceptance'}
        result=call('POST','/rules',201,body,revision,key)
        assert call('POST','/rules',201,body,revision,key)==result
        if rule['id'] not in owned: owned.append(rule['id'])
        return result

    def change(id, action, version, revision, expected=200):
        return call('POST','/rules/'+id+'/'+action,expected,{'version':version,'reason':'Synthetic acceptance'},revision,uuid.uuid4().hex)

    def inventory(id, group, priority=10, version=1):
        return {'id':id,'version':version,'type':'INVENTORY','enabled':True,'priority':priority,
                'scope':{'customerCode':tenant,'node':node},'facts':{'resource.ciId':'ci-synthetic','assignment.group':group,
                'resource.managed':True,'service.criticality':3},'metadata':{'owner':'testing'}}

    def plan(required, version=1):
        return {'id':'lookup','version':version,'type':'ENRICHMENT','enabled':True,'priority':10,
                'condition':{'field':'resource.node','operator':'EXISTS'},
                'actions':[{'type':'LOOKUP_INVENTORY','parameters':{'required':required}}],'metadata':{'owner':'testing'}}

    def simulate(target=node, customer=tenant):
        event={'schemaVersion':'1.1','eventId':uuid.uuid4().hex,'eventKey':target,'tenant':{'code':customer},
               'resource':{'name':target},'summary':'Synthetic inventory test','lifecycleAction':'OPEN','effectiveSeverity':3,
               'timestamps':{'receivedAt':dt.datetime.now(dt.timezone.utc).isoformat()}}
        return call('POST','/simulations',body={'event':event},customer=customer)

    def sql(query):
        result=subprocess.run(['docker','exec','-i','event-postgres','sh','-c',
             'psql -X -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
             input=query,capture_output=True,text=True,check=True,timeout=15)
        return result.stdout.strip()

    def wait(fetch):
        for _ in range(90):
            value=fetch()
            if value:return value
            time.sleep(.5)
        raise AssertionError('PROCESSING_TIMEOUT')

    def deliver(label, recovery=False, missing=False):
        fixture=ROOT/'testing/fixtures/events/sdc'/('zabbix-messagebus-recovery.json' if recovery else 'zabbix-messagebus-problem.json')
        # Older feature checkouts keep the same Gateway fixture beside its tests.
        if not fixture.exists(): fixture=ROOT/'test/events'/('zabbix-ok.json' if recovery else 'zabbix-problem.json')
        event=json.loads(fixture.read_text());event.update(CustomerCode=tenant,Node=node,hostname=node)
        accepted=http('POST','http://127.0.0.1:8081/api/v1/events',202,event)
        event_id=accepted['eventId'];assert re.fullmatch(r'[A-Za-z0-9_-]+',event_id)
        row=wait(lambda:sql("SELECT evidence::text FROM event_processor.processing_record WHERE tenant='"+tenant+"' AND event_id='"+event_id+"'"))
        record=json.loads(row);pid=record['processingId'];assert re.fullmatch('[a-f0-9]{64}',pid)
        assert call('GET','/explain/'+pid)==record
        topic='events.dlq' if missing else 'events.normalized'
        data=json.loads(wait(lambda:sql("SELECT payload::text FROM event_processor.output_outbox WHERE processing_id='"+pid+"' AND topic='"+topic+"' AND published_at IS NOT NULL")))
        if missing:
            assert record['enrichment']['status']=='FAILED' and data['stage']=='ContextEnrichment'
            assert data['originalEvent']['redacted'] is True
        else:
            assert record['enrichment']['facts']['assignment.group']=='network-b'
            assert data['processing']['enrichment']['result']==record['enrichment']
            assert data['resource']['name']==node
        assert len(record['stages'])==12 and record['stages'][-1]['status']=='SUCCESS'
        (output/(label+'.json')).write_text(json.dumps({'accepted':accepted,'evidence':record,'output':data},indent=2)+'\n')

    try:
        save(inventory('ci-primary','network-a'));save(plan(True))
        assert simulate()['enrichment']['status']=='NOT_FOUND'
        change('lookup','enable',1,1)
        assert simulate()['directive']=='DEAD_LETTER'
        change('ci-primary','enable',1,1)
        result=simulate();assert result['enrichment']['status']=='SUCCESS'
        assert result['enrichment']['facts']['resource.managed'] is True
        assert len(result['enrichment']['provenance'])==4
        assert simulate(node+'-other')['directive']=='DEAD_LETTER'
        assert simulate(customer=tenant+'-other')['enrichment']['status']=='NOT_FOUND'
        call('GET','/rules/ci-primary',404,customer=tenant+'-other')
        report['checks'].append('idempotent registration, independent activation, typed facts/provenance and resource/tenant isolation')

        save(inventory('ci-primary','network-b',version=2),2)
        assert simulate()['enrichment']['facts']['assignment.group']=='network-a'
        current=call('GET','/rules/ci-primary');assert current['latestVersion']==2 and current['activeVersion']==1
        change('ci-primary','enable',2,2,409);change('ci-primary','enable',2,3)
        assert simulate()['enrichment']['facts']['assignment.group']=='network-b'
        save(inventory('ci-secondary','conflicting',priority=1));change('ci-secondary','enable',1,1)
        result=simulate();assert result['enrichment']['facts']['assignment.group']=='network-b'
        conflict=result['enrichment']['conflicts'][0]
        assert conflict['selectedSource']=='ci-primary:2' and conflict['rejectedSource']=='ci-secondary:1'
        policy={'id':'policy-proof','version':1,'type':'POLICY','enabled':True,'priority':10,
                'condition':{'field':'enrichment.assignment.group','operator':'EQ','value':'network-b'},
                'actions':[{'type':'STATE_ONLY'}],'metadata':{'owner':'testing'}}
        save(policy);change('policy-proof','enable',1,1)
        assert simulate()['directive']=='STATE_ONLY'
        report['checks'].append('new version preserves old active until enable; stale revision rejected; deterministic conflict and policy consumption')

        for view, ids in [('inventory-records',{'ci-primary','ci-secondary'}),('enrichment-plans',{'lookup'})]:
            page=http('GET','http://127.0.0.1:8090/api/catalog/views/'+view)
            rows=[r for r in page['items'] if r['tenant']==tenant]
            assert {r['id'] for r in rows}==ids
            assert all(r['source']=='Event Processor' for r in rows)
        report['checks'].append('deployed catalog reads actual INVENTORY records and ENRICHMENT plans separately')
        deliver('enriched-problem');deliver('enriched-recovery',recovery=True)
        change('ci-primary','disable',2,4);change('ci-secondary','disable',1,2)
        deliver('required-missing-dlq',missing=True)
        save(plan(False,2),2);change('lookup','enable',2,3)
        result=simulate();assert result['directive']=='CONTINUE' and result['enrichment']['status']=='NOT_FOUND'
        change('ci-primary','retire',2,5);change('ci-primary','enable',2,6,409)
        assert len(call('GET','/rules/ci-primary/history')['items'])==6
        for label, invalid in [('type',inventory('invalid-type','x')),('scope',inventory('invalid-scope','x')),('cycle',plan(True,3))]:
            if label=='type':invalid['facts']['resource.managed']='true'
            if label=='scope':invalid['scope']={'customerCode':tenant}
            if label=='cycle':invalid['id']='invalid-cycle';invalid['version']=1;invalid['condition']['field']='enrichment.resource.ciId'
            call('POST','/rules',422,{'rule':invalid,'reason':'Invalid fixture'},0,uuid.uuid4().hex)
            call('GET','/rules/'+invalid['id'],404)
        assert sql("SELECT count(*) FROM event_processor.integration_command WHERE tenant='"+tenant+"'")=='0'
        report['checks'].append('real Gateway/Kafka delivery with identical normalized facts; recovery, required DLQ, optional miss, retire/history and invalid input rejection; no provider commands')
        report['status']='PASS'
    except Exception as error:
        report['status']='FAIL';report['error']=str(error);raise
    finally:
        errors=[]
        for id in reversed(owned):
            try:
                current=call('GET','/rules/'+id)
                if current['status']=='ENABLED':change(id,'disable',current['activeVersion'],current['revision'])
            except Exception as error:errors.append({'id':id,'error':str(error)})
        report['cleanup']={'ownedRules':owned,'errors':errors,'retention':'synthetic events, versions and audit retained; no owned active rules'}
        if errors:report['status']='FAIL'
        (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
        (output/'http-exchanges.json').write_text(json.dumps(exchanges,indent=2)+'\n')
        (output/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(output.iterdir()) if p.name!='SHA256SUMS'))
        print(output)
        if report['status']!='PASS':raise RuntimeError('INVENTORY_ENRICHMENT_CERTIFICATION_FAILED')


if __name__=='__main__':
    main()
