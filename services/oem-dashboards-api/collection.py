"""Gateway ingress journal: original bytes, day/history windows, no reconstructed payloads."""
import base64
import hashlib
import os
from datetime import date, datetime, timedelta, timezone
from uuid import UUID
from zoneinfo import ZoneInfo
from dashboard import DashboardError

STATES=('RECEIVED','VALIDATED','PUBLISHED','REJECTED','DELIVERY_FAILED','PUBLISH_UNCONFIRMED')
DAY_ZONE='America/Mexico_City'

def parse_collection_query(params):
    fields={'scope','from','to','status','customer','source','q','receipt','page','limit'}
    if set(params)-fields or any(len(v)!=1 for v in params.values()):raise DashboardError('Invalid collection filters')
    q={k:params.get(k,[''])[0].strip() for k in fields-{'page','limit'}}
    q['scope']=q['scope'] or 'today'
    if any(len(v)>128 for v in q.values()) or q['scope'] not in ('today','history','all') or q['status'] not in ('',*STATES):raise DashboardError('Invalid collection filters')
    try:
        for key in ('from','to'):
            if q[key]:
                if len(q[key])!=10:raise ValueError()
                date.fromisoformat(q[key])
        if q['from'] and q['to'] and q['from']>q['to']:raise ValueError()
        if q['receipt']:q['receipt']=str(UUID(q['receipt']))
        q.update(page=int(params.get('page',['1'])[0]),limit=int(params.get('limit',['25'])[0]))
        if not 1<=q['page']<=100000 or not 1<=q['limit']<=100:raise ValueError()
    except ValueError as exc:raise DashboardError('Invalid dates, receipt or pagination') from exc
    return q

def validate_collection(data,q):
    from dashboard import timestamp
    def require(ok):
        if not ok:raise DashboardError('Invalid Data Collection contract')
    try:
        require(data['schemaVersion']=='1.0' and data['domain']=='data-collection' and timestamp(data['observedAt']))
        require(type(data['total']) is int and data['total']>=0 and data['page']==q['page'] and data['limit']==q['limit'])
        require(data['scope']==q['scope'] and data['timezone']==DAY_ZONE)
        require(all(k in STATES and type(v) is int and v>=0 for k,v in data['counts'].items()) and sum(data['counts'].values())==data['total'])
        require(isinstance(data['rows'],list) and len(data['rows'])==min(q['limit'],max(0,data['total']-(q['page']-1)*q['limit'])))
        for r in data['rows']:
            UUID(r['id']);require(timestamp(r['receivedAt']) and r['status'] in STATES and type(r['byteCount']) is int and 0<=r['byteCount']<=1048576)
        if data.get('original') is not None:
            original=data['original'];raw=base64.b64decode(original['base64'],validate=True)
            require(q['receipt']==original['id'] and len(data['rows'])==1 and data['rows'][0]['id']==original['id'])
            require(len(raw)==data['rows'][0]['byteCount'] and hashlib.sha256(raw).hexdigest()==data['rows'][0]['sha256'])
        return {k:data[k] for k in ('schemaVersion','domain','observedAt','scope','timezone','total','page','limit','counts','rows','original')}
    except (ValueError,KeyError,TypeError) as exc:raise DashboardError('Invalid Data Collection contract') from exc

def load_collection(q):
    try:
        import psycopg
        from psycopg.rows import dict_row
        dsn=os.getenv('OEM_POSTGRES_DSN')
        if not dsn:raise DashboardError('PostgreSQL unavailable')
        relation={'today':'data_collection_today','history':'data_collection_history','all':'data_collection'}[q['scope']]
        terms,args=[],[]
        for key,column in [('status','status'),('customer','customer_code'),('source','source_system'),('receipt','id')]:
            if q[key]:terms.append(column+'=%s');args.append(q[key])
        for key,operator in [('from','>='),('to','<')]:
            if q[key]:
                day=date.fromisoformat(q[key])+(timedelta(days=1) if key=='to' else timedelta())
                terms.append('received_at '+operator+' %s');args.append(datetime.combine(day,datetime.min.time(),ZoneInfo(DAY_ZONE)))
        if q['q']:
            terms.append("strpos(lower(id || ' ' || coalesce(event_id,'') || ' ' || coalesce(event_key,'')),lower(%s))>0");args.append(q['q'])
        where=' WHERE '+' AND '.join(terms) if terms else ''
        base=' FROM dashboard_read.'+relation+where
        with psycopg.connect(dsn,connect_timeout=3,row_factory=dict_row) as conn:
            with conn.cursor() as cur:
                cur.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY')
                cur.execute("SET LOCAL statement_timeout='4000ms'")
                cur.execute('SELECT status,count(*) AS count'+base+' GROUP BY status',args);groups=cur.fetchall()
                cur.execute('SELECT *'+base+' ORDER BY received_at DESC,id LIMIT %s OFFSET %s',args+[q['limit'],(q['page']-1)*q['limit']]);records=cur.fetchall()
                original=None
                if q['receipt'] and records:
                    cur.execute('SELECT original_body FROM dashboard_read.data_collection_original WHERE id=%s',[q['receipt']]);raw=bytes(cur.fetchone()['original_body'])
                    if hashlib.sha256(raw).hexdigest()!=records[0]['body_sha256']:raise DashboardError('Original integrity check failed')
                    original={'id':q['receipt'],'base64':base64.b64encode(raw).decode()}
        mapping={'id':'id','receivedAt':'received_at','status':'status','eventId':'event_id','eventKey':'event_key','customerCode':'customer_code','sourceSystem':'source_system','severity':'severity','errorCode':'error_code','updatedAt':'updated_at','contentType':'content_type','sha256':'body_sha256','byteCount':'byte_count'}
        rows=[{key:(r[col].isoformat() if isinstance(r[col],datetime) else r[col]) for key,col in mapping.items()} for r in records]
        return {'schemaVersion':'1.0','domain':'data-collection','observedAt':datetime.now(timezone.utc).isoformat(),'scope':q['scope'],'timezone':DAY_ZONE,'total':sum(g['count'] for g in groups),'counts':{g['status']:g['count'] for g in groups},'page':q['page'],'limit':q['limit'],'rows':rows,'original':original}
    except DashboardError:raise
    except Exception as exc:raise DashboardError('Data Collection unavailable; verify migration 025') from exc
