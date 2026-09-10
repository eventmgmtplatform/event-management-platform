"""Fixed, read-only ESS boundary. No caller-supplied credentials or destinations."""
import http.client
import json
import os
import re
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urlsplit

UPSTREAM_HOST = 'event-state-service'
UPSTREAM_PORT = 8084
PREFIX = '/api/ess/'
STATE_FIELDS = ('event_key','event_id','tenant','lifecycle_status','ticket_number','notification_id','automation_id','servicenow_status','gnm_status','cacf_status','source_severity','effective_severity','tally','version','first_seen_at','last_updated_at','last_state_at')
HISTORY_FIELDS = ('message_id','event_key','from_status','to_status','transition_type','aggregate_version','occurred_at','recorded_at')
ERROR_CODES = {'UNAUTHORIZED','EVENT_NOT_FOUND','INVALID_LIMIT','INVALID_CURSOR','INVALID_EVENT_KEY','ADMIN_NOT_CONFIGURED','STATE_STORE_UNAVAILABLE'}
class Failure(Exception):
    def __init__(self,status,code): self.status=status; self.code=code

def settings():
    try:
        raw=json.loads(Path(os.environ.get('ESS_ADMIN_CREDENTIALS_FILE','/run/secrets/ess_admin_tokens')).read_text())
        allowed=[x.strip() for x in os.environ.get('ESS_ALLOWED_TENANTS','').split(',') if x.strip()]
        tokens={t:raw['tenants'][t] for t in dict.fromkeys(allowed) if isinstance(raw.get('tenants',{}).get(t),str) and raw['tenants'][t]}
        operator=raw.get('operatorToken') if os.environ.get('ESS_OPERATOR_QUARANTINE','false').lower()=='true' else None
        if not isinstance(operator,str) or not operator or operator in tokens.values(): operator=None
        if not tokens: raise ValueError()
        return tokens,operator
    except (OSError,ValueError,TypeError,KeyError): raise Failure(503,'ADMIN_NOT_CONFIGURED') from None

def valid_text(value,required=False):
    if (required and not value.strip()) or len(value.encode('utf-16-le'))//2>128 or any(ord(c)<32 or ord(c)==127 for c in value):
        raise Failure(400,'INVALID_EVENT_KEY' if required else 'INVALID_CURSOR')
    return value

def integer(value,maximum,minimum,code):
    if not re.fullmatch(r'[0-9]{1,19}',value): raise Failure(400,code)
    result=int(value)
    if not minimum<=result<=maximum: raise Failure(400,code)
    return result

def upstream(action,params,tenant,token):
    conn=http.client.HTTPConnection(UPSTREAM_HOST,UPSTREAM_PORT,timeout=6)
    try:
        headers={'Accept':'application/json','X-ESS-Admin-Token':token}
        if tenant is not None: headers['X-Tenant-Id']=tenant
        conn.request('GET','/api/v1/state/'+action+('?' + urlencode(params) if params else ''),headers=headers)
        response=conn.getresponse()
        raw=response.read(2_000_001)
        if len(raw)>2_000_000: raise Failure(503,'INVALID_UPSTREAM_RESPONSE')
        try: data=json.loads(raw)
        except (ValueError,UnicodeError): raise Failure(503,'INVALID_UPSTREAM_RESPONSE') from None
        if response.status!=200:
            status=response.status if response.status in (400,401,404,503) else 503
            code=data.get('errorCode') if isinstance(data,dict) else None
            raise Failure(status,code if code in ERROR_CODES else {400:'INVALID_REQUEST',401:'UNAUTHORIZED',404:'EVENT_NOT_FOUND',503:'STATE_STORE_UNAVAILABLE'}[status])
        return data
    except (OSError,http.client.HTTPException): raise Failure(503,'ESS_UNAVAILABLE') from None
    finally: conn.close()

def project(row,fields):
    if not isinstance(row,dict) or any(isinstance(row.get(f),(dict,list)) for f in fields): raise Failure(503,'INVALID_UPSTREAM_RESPONSE')
    return {f:row.get(f) for f in fields}

def clean_response(action,data,tenant,params):
    if not isinstance(data,dict): raise Failure(503,'INVALID_UPSTREAM_RESPONSE')
    if action=='event':
        if data.get('tenant')!=tenant or data.get('event_key')!=params['eventKey']: raise Failure(503,'INVALID_UPSTREAM_RESPONSE')
        return project(data,STATE_FIELDS)
    rows=data.get('items')
    if not isinstance(rows,list) or len(rows)> (100 if action=='quarantine' else params['limit']): raise Failure(503,'INVALID_UPSTREAM_RESPONSE')
    if action=='quarantine':
        if data.get('scope')!='operator-global': raise Failure(503,'INVALID_UPSTREAM_RESPONSE')
        return {'scope':'operator-global','items':[project(r,('reason','count','last_seen_at')) for r in rows]}
    if action=='events':
        if any(not isinstance(r,dict) or r.get('tenant')!=tenant or not isinstance(r.get('event_key'),str) for r in rows): raise Failure(503,'INVALID_UPSTREAM_RESPONSE')
        cursor=data.get('nextCursor')
        if not isinstance(cursor,str) or len(cursor)>128 or (cursor and (not rows or cursor!=rows[-1]['event_key'] or cursor==params['after'])): raise Failure(503,'INVALID_UPSTREAM_RESPONSE')
        return {'items':[project(r,STATE_FIELDS) for r in rows],'nextCursor':cursor}
    if any(not isinstance(r,dict) or r.get('event_key')!=params['eventKey'] for r in rows): raise Failure(503,'INVALID_UPSTREAM_RESPONSE')
    version=data.get('nextVersion')
    if type(version)is not int or version<0 or (version and (not rows or version!=rows[-1].get('aggregate_version') or version<=params['afterVersion'])): raise Failure(503,'INVALID_UPSTREAM_RESPONSE')
    return {'items':[project(r,HISTORY_FIELDS) for r in rows],'nextVersion':version}

def handle(method,path,headers):
    if method!='GET': raise Failure(405,'METHOD_NOT_ALLOWED')
    parsed=urlsplit(path)
    routes={'/api/ess/config':'config','/api/ess/events':'events','/api/ess/event':'event','/api/ess/history':'history','/api/ess/operator/quarantine':'quarantine'}
    action=routes.get(parsed.path)
    if not action: raise Failure(404,'NOT_FOUND')
    if any(headers.get(key) for key in ('X-ESS-Admin-Token','X-Tenant-Id','Authorization')): raise Failure(401,'CALLER_CREDENTIALS_NOT_ACCEPTED')
    try:
        query=parse_qs(parsed.query,keep_blank_values=True,strict_parsing=True,max_num_fields=8)
        if any(len(v)!=1 for v in query.values()): raise ValueError()
    except ValueError: raise Failure(400,'INVALID_REQUEST') from None
    allowed={'config':set(),'quarantine':set(),'events':{'tenant','limit','after'},'event':{'tenant','eventKey'},'history':{'tenant','eventKey','limit','afterVersion'}}[action]
    if set(query)-allowed: raise Failure(400,'INVALID_REQUEST')
    tokens,operator=settings()
    if action=='config':return {'tenants':list(tokens),'operatorQuarantine':bool(operator),'authorizationScope':'local-console'}
    if action=='quarantine':
        if not operator: raise Failure(403,'OPERATOR_NOT_ALLOWED')
        return clean_response(action,upstream(action,{},None,operator),None,{})
    tenant=query.get('tenant',[''])[0]
    if tenant not in tokens: raise Failure(403,'TENANT_NOT_ALLOWED')
    params={}
    if action in ('events','history'):params['limit']=integer(query.get('limit',['50'])[0],100,1,'INVALID_LIMIT')
    if action=='events':params['after']=valid_text(query.get('after',[''])[0])
    else:params['eventKey']=valid_text(query.get('eventKey',[''])[0],True)
    if action=='history':params['afterVersion']=integer(query.get('afterVersion',['0'])[0],9223372036854775807,0,'INVALID_CURSOR')
    return clean_response(action,upstream(action,params,tenant,tokens[tenant]),tenant,params)
