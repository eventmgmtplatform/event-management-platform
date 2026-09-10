"""UC-001. Public event/callback boundaries; SQL is observation only, never orchestration."""
import datetime as dt
import importlib.util
import json
import subprocess
import time
import uuid
import urllib.request
import urllib.parse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('blackout_helpers',Path(__file__).with_name('blackout.py'))
b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
http,require,wait=b.http,b.require,b.wait
COMPOSE=['docker','compose','-f',str(ROOT/'testing/environments/lifecycle.compose.yml')]

def sql(query):
    require(query.lstrip().upper().startswith('SELECT '),'E2E SQL must be read only')
    r=subprocess.run(COMPOSE+['exec','-T','postgres','psql','-XAt','-v','ON_ERROR_STOP=1','-U','lifecycle','-d','lifecycle'],input=query,capture_output=True,text=True,check=True,timeout=15)
    return r.stdout.strip()

def shared_sql(query):
    require(query.lstrip().upper().startswith('SELECT '),'E2E SQL must be read only')
    command=['docker','exec','-i','event-postgres','sh','-c',
             'psql -XAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
    return subprocess.run(command,input=query,capture_output=True,text=True,check=True,timeout=15).stdout.strip()


def run(output,report,checkpoint=None,runtime='shared',all_engines=False):
    require(runtime in ('os11','shared'),'Unsupported runtime')
    sql_query = shared_sql if runtime == 'shared' else sql
    ports = {'gateway':8081,'processor':8082,'worker':8083,'sn':8181,'gnm':8182,'next':8184,'search':9200} if runtime == 'shared' else {'gateway':28081,'processor':28082,'worker':28083,'sn':28181,'gnm':28182,'next':28183,'search':28092}
    url=lambda name: 'http://127.0.0.1:'+str(ports[name])
    report['runtime']=runtime
    report['allEngines']=all_engines
    if runtime == 'shared':
        inspected=json.loads(subprocess.run(['docker','inspect','event-integration-worker'],capture_output=True,text=True,check=True).stdout)[0]
        environment=dict(item.split('=',1) for item in inspected['Config']['Env'] if '=' in item)
        for key,value in {'SERVICENOW_BASE_URL':'http://servicenow-mock:8080','GNM_BASE_URL':'http://gnm-mock:8080','NEXT_BASE_URL':'http://next-mock:8080','CACF_ENABLED':'true','CACF_API_TOKEN':'synthetic-lifecycle-only'}.items():
            require(environment.get(key)==value,'Shared synthetic configuration required: '+key)

    runid=uuid.uuid4().hex[:16];tenant='os11-synthetic';node='os11-'+runid
    ticket='INC'+runid.upper();sysid=uuid.uuid4().hex;incident=str(int(runid,16))
    api=url('processor')+'/api/v1';worker=url('worker')
    headers={'X-Tenant-Id':tenant,'X-Actor-Id':'os11-testing'}
    mocks={name:url(name) for name in ('sn','gnm','next')}
    rules=[];mappings=[];report.update(caseId='UC-001',tenant=tenant,node=node,checks=[],identities={})
    def save(name,data): (output/(name+'.json')).write_text(json.dumps(data,indent=2)+'\n')
    def mutate(path,body,rev):return http('POST',api+path,body,{**headers,'Idempotency-Key':uuid.uuid4().hex,'If-Match':f'"{rev}"'})
    def mapping(provider,request,response,scenario=None,before=None,after=None):
        mid=str(uuid.uuid4());m={'id':mid,'priority':1,'request':request,'response':{'status':200,'headers':{'Content-Type':'application/json'},**response}}
        if scenario:m.update(scenarioName=scenario,requiredScenarioState=before)
        if after:m['newScenarioState']=after
        http('POST',mocks[provider]+'/__admin/mappings',m);mappings.append((provider,mid));return m
    def calls(provider):
        return http('GET',mocks[provider]+'/__admin/requests')[1]['requests']
    def callback(execution,name,status=''):
        xml=f'<ServiceIncident xmlns="http://b2b.ibm.com/schema/IS_B2B_CDM/R2_2"><Header><TransactionName>{name}</TransactionName><TransactionNumber>{execution}</TransactionNumber></Header><Body><RequesterID>Zabbix:{report["identities"]["cycleId"]}:{tenant}</RequesterID><ProviderID>NEXT-{runid}</ProviderID><WorkflowStatus>{status}</WorkflowStatus></Body></ServiceIncident>'
        req=urllib.request.Request(worker+'/api/v1/providers/next/callback',data=xml.encode(),headers={'Content-Type':'text/xml','X-CACF-Token':'synthetic-lifecycle-only'},method='POST')
        with urllib.request.urlopen(req,timeout=15) as response:require(response.status==200,'Callback not accepted')
        (output/(name+'-'+(status or 'ACK')+'.xml')).write_text(xml)
    def lifecycle():
        raw=sql_query("SELECT document::text FROM event_processor.lifecycle WHERE tenant='"+tenant+"' AND document->'initialCommand'->'payload'->>'resource'='"+node+"'")
        return json.loads(raw) if raw else None
    def stage(name):
        def fetch():
            d=lifecycle()
            if d and d['state']=='REVIEW':save('review',d);raise AssertionError('Lifecycle requires review: '+d.get('reason','unknown'))
            return d if d and (name in d.get('results',{}) or name==d['state']) else None
        d=wait(fetch,name,120);save(name,d);return d
    def send(recovery=False):
        event={'source':'Zabbix','InstanceSituation':'synthetic-port','AlertKey':'synthetic-port','InstanceValue':'1' if recovery else '0',
            'hostname':node,'ClassName':'Zabbix','Type':'0' if recovery else '1','origin':'192.0.2.10',
            'msg':'Synthetic port recovery' if recovery else 'Synthetic fatal port condition','severity':'5','Component':'SYNTHETIC',
            'InstanceId':'synthetic-check','Node':node,'NodeAlias':'192.0.2.10','ApplId':'SYNTHETIC','CustomerCode':tenant,'SubComponent':'Synthetic test','ExpireTime':900}
        status,accepted,_=http('POST',url('gateway')+'/api/v1/events',event);require(status==202 and accepted['accepted'],'Gateway rejected input')
        save('recovery-'+accepted['eventId'] if recovery else 'fatal',{'input':event,'accepted':accepted})
        return accepted
    try:
        # Provision only owned mock contracts, with explicit synthetic resolution values (no guessed numeric code).
        sn={'number':ticket,'sys_id':sysid,'state':'open-test','u_event_id':''}
        mapping('sn',{'method':'POST','urlPath':'/api/now/table/incident','bodyPatterns':[{'contains':node}]},{'jsonBody':{'result':sn},'status':201})
        lookup={'method':'GET','urlPath':'/api/now/table/incident','queryParameters':{'sysparm_query':{'equalTo':'number='+ticket}}}
        scenario='sn-'+runid
        mapping('sn',lookup,{'jsonBody':{'result':[sn]}},scenario,'Started')
        resolved={**sn,'state':'resolved-test','close_code':'monitor-recovered','close_notes':'Monitoring recovered'}
        mapping('sn',lookup,{'jsonBody':{'result':[resolved]}},scenario,'Resolved')
        patch={'method':'PATCH','urlPath':'/api/now/table/incident/'+sysid}
        mapping('sn',{**patch,'bodyPatterns':[{'matchesJsonPath':'$[?(@.state == "resolved-test")]'}]},{'jsonBody':{'result':resolved}},scenario,'Started','Resolved')
        mapping('sn',{**patch,'bodyPatterns':[{'matchesJsonPath':'$.work_notes'}]},{'jsonBody':{'result':sn}})
        gnmurl='/rest/incidents/11001';sc='gnm-'+runid
        mapping('gnm',{'method':'POST','urlPath':gnmurl,'bodyPatterns':[{'contains':node},{'contains':ticket}]},{'jsonBody':{'status':200,'message':'OK','result':{'id':incident}}})
        for state,before in [('open','Started'),('closed','Closed')]:
            body=json.loads((ROOT/f'testing/mocks/gnm/__files/incident-{state}.json').read_text());body['result'].update(id=incident,organizationId='11001')
            mapping('gnm',{'method':'GET','urlPath':gnmurl+'/'+incident},{'jsonBody':body},sc,before)
        mapping('gnm',{'method':'PUT','urlPath':gnmurl+'/'+incident,'bodyPatterns':[{'contains':'CloseWithNotification'},{'contains':ticket}]},{'jsonBody':{'status':200,'message':'OK','result':{'id':incident}}},sc,'Started','Closed')
        for path in ['tickets','incidents']:
            mapping('next',{'method':'POST','urlPath':'/tupix/api/v1/netcool/'+path,'bodyPatterns':[{'contains':node if path=='tickets' else ticket}]},{'headers':{'Content-Type':'text/xml'},'body':'<response>accepted</response>'})
        correlation={'id':node+'-group','version':1,'enabled':True,'priority':10,'strategy':'ATTRIBUTE','scope':{'field':'resource.node','operator':'EQ','value':node},'candidateSelection':{'windowSeconds':3600,'maxCandidates':8,'activeOnly':True},'match':{'fields':['resource.node']},'relationship':{'type':'GROUP'},'metadata':{'owner':'testing'}}
        route={'id':node+'-route','version':1,'type':'ROUTING','enabled':True,'priority':10,'condition':{'field':'resource.node','operator':'EQ','value':node},'actions':[{'type':'CREATE_TICKET','target':'SERVICENOW','parameters':{'configuration':'default','correlationRuleId':correlation['id'],'lifecycle':{'notificationGroup':'synthetic-operations','originalAssignmentGroup':'synthetic-human','holdingAssignmentGroup':'synthetic-automation','resolvedState':'resolved-test','closeCode':'monitor-recovered'}}}],'metadata':{'owner':'testing'}}
        definitions=[correlation,route]
        if all_engines:
            inventory={'id':node+'-inventory','version':1,'type':'INVENTORY','enabled':True,'priority':10,
                'scope':{'customerCode':tenant,'node':node},'facts':{'resource.ciId':node+'-ci','assignment.group':'synthetic-operations','resource.managed':True},'metadata':{'owner':'testing'}}
            plan={'id':node+'-enrichment','version':1,'type':'ENRICHMENT','enabled':True,'priority':10,
                'condition':correlation['scope'],'actions':[{'type':'LOOKUP_INVENTORY','parameters':{'required':True}}],'metadata':{'owner':'testing'}}
            policy={'id':node+'-policy','version':1,'type':'POLICY','enabled':True,'priority':10,
                'condition':{'all':[correlation['scope'],{'field':'enrichment.resource.managed','operator':'EQ','value':True}]},
                'actions':[{'type':'CONTINUE'}],'metadata':{'owner':'testing'}}
            # Both downstream decisions require actual inventory facts, not just a parallel lookup.
            correlation['match']={'fields':['enrichment.resource.ciId']}
            route['condition']=policy['condition']
            now=dt.datetime.now(dt.timezone.utc)
            blackout={'id':node+'-blackout','version':1,'type':'SCHEDULED','enabled':True,'priority':10,
                'scope':{'customerCode':tenant,'node':node},'schedule':{'timezone':'UTC','validFrom':(now-dt.timedelta(minutes=1)).isoformat(),'validTo':(now+dt.timedelta(minutes=30)).isoformat()},
                'reason':'Integrated synthetic maintenance','metadata':{'owner':'testing'}}
            suppression={**blackout,'id':node+'-suppression','type':'SUPPRESSION','source':'CHANGE','externalStatus':'APPROVED'}
            definitions=[inventory,plan,policy,correlation,route,blackout,suppression]
        for rule in definitions:
            require(mutate('/rules',{'rule':rule,'reason':'UC-001 setup'},0)[0]==201,'Rule create failed');rules.append(rule['id'])
            require(mutate('/rules/'+rule['id']+'/enable',{'version':1,'reason':'UC-001 enable'},1)[0]==200,'Rule enable failed')
        def observe(accepted,label):
            eid=accepted['eventId']
            require(all(c.isalnum() or c in '_-' for c in eid),'Unexpected event ID')
            record=json.loads(wait(lambda:sql_query("SELECT evidence::text FROM event_processor.processing_record WHERE tenant='"+tenant+"' AND event_id='"+eid+"'"),label))
            require(record['enrichment']['facts']['resource.ciId']==node+'-ci','Inventory facts missing')
            require(record['enrichment']['provenance'],'Inventory provenance missing')
            require(len(record['stages'])==12 and record['correlationApplied'],'Incomplete audit/correlation')
            policy_stage=next(x for x in record['stages'] if x['stage']=='PolicyEvaluation')
            require(policy['id'] in json.dumps(policy_stage['evidence']) and policy_stage['match']=='MATCH','Enriched policy did not match')
            require(http('GET',api+'/explain/'+record['processingId'],headers=headers)[1]==record,'Explain differs')
            wait(lambda:sql_query("SELECT count(*) FROM event_processor.output_outbox WHERE processing_id='"+record['processingId']+"' AND topic='events.normalized' AND published_at IS NOT NULL")=='1','normalized output')
            save(label,record);return record
        if all_engines:
            for label,expected_blackout in [('both-maintenance','MATCH'),('suppression-only','NO_MATCH')]:
                record=observe(send(),label)
                require(record['directive']=='SUPPRESS_INTEGRATIONS' and not record['routing']['commands'],'Maintenance emitted command')
                require(next(x for x in record['stages'] if x['stage']=='Blackout')['match']==expected_blackout,'Blackout control mismatch')
                require(next(x for x in record['stages'] if x['stage']=='AutoSuppression')['match']=='MATCH','Suppression control mismatch')
                require(sql_query("SELECT count(*) FROM event_processor.integration_command WHERE tenant='"+tenant+"' AND envelope->'payload'->>'resource'='"+node+"'")=='0','Maintenance created durable command')
                if label=='both-maintenance':mutate('/rules/'+blackout['id']+'/disable',{'version':1,'reason':'Integrated control'},2)
            mutate('/rules/'+suppression['id']+'/disable',{'version':1,'reason':'Integrated control'},2)
            report['checks'].append('Inventory facts drive policy and correlation keys; blackout+suppression and suppression-only block an eligible route, retaining normalized output and audit')
        fatal=send()
        if all_engines:
            accepted_record=observe(fatal,'eligible-enriched-event')
            require(accepted_record['directive']=='GENERATE_COMMANDS','Released route did not generate command')
        d=stage('ticket');require(d['ticketNumber']==ticket and d['ticketId']==sysid,'Ticket response identity mismatch')
        require(d['initialCommand']['payload']['severity']==5,'Fatal severity was not 5')
        report['identities'].update(eventId=d['sourceEventId'],eventKey=d['sourceEventKey'],processingId=d['processingId'],cycleId=d['cycleId'],ticketNumber=ticket,ticketId=sysid)
        require(fatal['eventId']==d['sourceEventId'],'Source event ID replaced by group identity')
        d=stage('open');require(d['incidentId']==incident,'GNM incident mismatch')
        d=wait(lambda: (x if (x:=lifecycle()) and x.get('executionId') else None),'automation intent',120)
        execution=d['executionId'];report['identities'].update(incidentId=incident,executionId=execution)
        def automation():return http('GET',worker+'/api/v1/automations/'+execution,headers={'X-CACF-Token':'synthetic-lifecycle-only'})[1]
        wait(lambda: sql_query("SELECT state FROM event_management.automation_execution WHERE execution_id='"+execution+"'")=='SUBMITTED','NEXT CREATE submitted',120)
        if checkpoint:checkpoint('NEXT_SUBMITTED',dict(report['identities']))
        callback(execution,'Acknowledge_Create')
        wait(lambda: sql_query("SELECT status FROM event_management.automation_provider_dispatch WHERE execution_id='"+execution+"' AND operation='TKTUPDATE'")=='SENT','ticket association',120)
        callback(execution,'result','RESOLVE');stage('automation');stage('note');save('automation',automation())
        before=lifecycle();require(not before['recovered'],'REMEDIATED incorrectly cleared monitoring')
        report['checks'].append('ticket → confirmed GNM with exact ticket → NEXT CREATE/ACK/TKTUPDATE → REMEDIATED and ITSM note')
        recovery=send(True)
        if all_engines:observe(recovery,'enriched-recovery')
        d=stage('COMPLETED')
        require(recovery['eventId']!=fatal['eventId'],'Recovery must have new source eventId')
        callback(execution,'result','RESOLVE');duplicate=send(True)
        wait(lambda: sql_query("SELECT count(*) FROM event_processor.processing_record WHERE tenant='"+tenant+"' AND event_id='"+duplicate['eventId']+"'")=='1','duplicate recovery processed')
        def projected():
            raw=sql_query("SELECT row_to_json(s)::text FROM event_management.event_state s WHERE event_key='correlation:"+d['cycleId']+"'")
            if not raw:return None
            s=json.loads(raw);return s if s['gnm_status']=='CLOSED' and s['servicenow_status']=='RESOLVED' and s['cacf_status']=='REMEDIATED' else None
        save('ess-group',wait(projected,'ESS provider terminal confirmations',120))
        def source_closed():
            raw=sql_query("SELECT row_to_json(s)::text FROM event_management.event_state s WHERE event_key='"+d['sourceEventKey']+"'")
            if not raw:return None
            value=json.loads(raw);return value if value['lifecycle_status']=='CLOSED' else None
        source=wait(source_closed,'ESS source recovery',120);save('ess-source',source)
        def search_confirmed():
            try:
                group=http('GET',url('search')+'/events-current/_doc/'+urllib.parse.quote('correlation:'+d['cycleId'],safe=''))[1]['_source']
                src=http('GET',url('search')+'/events-current/_doc/'+urllib.parse.quote(d['sourceEventKey'],safe=''))[1]['_source']
                return {'group':group,'source':src} if group['integrationStatus']=={'servicenow':'RESOLVED','gnm':'CLOSED','cacf':'REMEDIATED'} and src['lifecycleStatus']=='CLOSED' and group['ticketNumber']==ticket else None
            except urllib.error.HTTPError as e:
                if e.code==404:return None
                raise
        save('opensearch',wait(search_confirmed,'OpenSearch terminal projection',120))
        wait(lambda: sql_query("SELECT count(*) FROM event_processor.output_outbox WHERE processing_id='"+d['processingId']+"' AND published_at IS NULL")=='0','outbox drained')
        journals={k:[r for r in calls(k) if any(x in json.dumps(r['request']) for x in [node,ticket,incident,execution,sysid,d['cycleId']])] for k in mocks};save('provider-journals',journals)
        mutations=lambda provider,method,path:[x for x in journals[provider] if x['request']['method']==method and x['request']['url'].split('?')[0]==path]
        require(len(mutations('sn','POST','/api/now/table/incident'))==1,'Duplicate ticket create')
        require(len(mutations('gnm','POST',gnmurl))==1 and len(mutations('gnm','PUT',gnmurl+'/'+incident))==1,'Duplicate/missing GNM effect')
        require(len(mutations('next','POST','/tupix/api/v1/netcool/tickets'))==1 and len(mutations('next','POST','/tupix/api/v1/netcool/incidents'))==1,'Duplicate/missing NEXT effect')
        patches=mutations('sn','PATCH','/api/now/table/incident/'+sysid)
        require(len(patches)==3,'Expected exactly holding, success note, resolution PATCH')
        ordered=[mutations('sn','POST','/api/now/table/incident')[0],mutations('gnm','POST',gnmurl)[0],mutations('next','POST','/tupix/api/v1/netcool/tickets')[0],mutations('next','POST','/tupix/api/v1/netcool/incidents')[0],mutations('gnm','PUT',gnmurl+'/'+incident)[0],next(x for x in patches if 'state' in json.loads(x['request']['body']))]
        timestamps=[x['request']['loggedDate'] for x in ordered]
        require(timestamps==sorted(timestamps),'Provider effects violated lifecycle order')
        require(all(x['wasMatched'] and 200<=x['response']['status']<300 for x in ordered),'Provider confirmation absent')
        bodies=[json.loads(x['request']['body']) for x in patches]
        require(sum('state' in p for p in bodies)==1 and sum('assignment_group' in p for p in bodies)==1,'Success reassigned or duplicated terminal action')
        require(sql_query("SELECT count(*) FROM event_management.automation_outbox WHERE execution_id='"+execution+"' AND NOT published")=='0','CACF outbox pending')
        require(sql_query("SELECT count(*) FROM event_management.automation_provider_dispatch WHERE execution_id='"+execution+"' AND status<>'SENT'")=='0','Provider dispatch pending')
        wait(lambda: sql_query("SELECT count(*) FROM event_management.integration_command_execution WHERE event_id='"+d['cycleId']+"' AND (execution_status<>'COMPLETED' OR result_published_at IS NULL)")=='0','worker delivery drained',60)
        save('completed',lifecycle());report['checks'].append('monitor clear → GNM CLOSED → ITSM RESOLVED confirmed; ESS projected; duplicate callback/clear cause no extra mutations; outboxes drained')
    finally:
        errors=[]
        for rid in reversed(rules):
            try:
                _,current,h=http('GET',api+'/rules/'+rid,headers=headers)
                if current['status']=='ENABLED':mutate('/rules/'+rid+'/disable',{'version':1,'reason':'UC-001 cleanup'},next(v for k,v in h.items() if k.lower()=='etag').strip('"'))
            except Exception as e:errors.append(str(e))
        # Keep scoped provider mappings for safe late reconciliation; disable rules only.
        report['cleanup']={'rules':rules,'errors':errors,'mappingsRetainedForReconciliation':mappings}
        require(not errors,'Cleanup failed: '+str(errors))
