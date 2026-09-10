"""Local console catalog API. Parameterized SQL, atomic writes and optimistic locking."""
import datetime
import json
import os
import uuid
import views
import ess_proxy
from pathlib import Path
from urllib.parse import urlsplit, parse_qs, unquote
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb

CUSTOMER_FIELDS = ('customer_code','customer','bamid','gnmorgid','snow_company_id','snow_assignment_group','gnm_assignment_group','cacf_assignment_group','chatops_team','aiops_extension','timezone','enabled')
FILTER_FIELDS = ('name','description','customer_code','applid','filter_state','filter_weight','severities','criteria')
TARGETS = ('gnm','snow','cacf','chatops','extensions')
CRITERIA = ('IBMManaged','ResourceId','Service','SubAccount','Subsystem','Application','InstanceId','SubComponent','Component','ComponentType','ResourceUsage','OSType','MsgId','AlertKey','AlertGroup','ResourceType','EventType','MonitoringSolution','Location','SourceType','OutsideServiceHours')
TABLES = {'customers': ('customer_configuration','customer_code'), 'filters': ('delivery_filter','filter_id')}

class Invalid(Exception): pass
class Conflict(Exception): pass
class Missing(Exception): pass

def connect():
    return psycopg.connect(host=os.getenv('PGHOST','postgres'), dbname=os.environ['PGDATABASE'], user='console_catalog_login', password=Path('/run/secrets/catalog_password').read_text().strip(), connect_timeout=3, options='-c statement_timeout=5000', row_factory=dict_row)

def validate(kind, data):
    if not isinstance(data, dict): raise Invalid('invalid_payload')
    if kind == 'customers':
        for field in CUSTOMER_FIELDS:
            value = data.get(field)
            if field == 'enabled':
                if type(value) is not bool: raise Invalid('invalid_customer')
            elif not isinstance(value,str) or len(value)> (100 if field=='customer_code' else 200): raise Invalid('invalid_customer')
        if not data['customer'].strip() or not data['customer_code'].strip(): raise Invalid('required_fields')
        from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
        try: ZoneInfo(data['timezone'])
        except (ZoneInfoNotFoundError, ValueError): raise Invalid('invalid_timezone')
        return
    for field in ('name','description','customer_code'):
        if not isinstance(data.get(field),str): raise Invalid('required_fields')
    if not 1<=len(data['name'].strip())<=200 or not 1<=len(data['customer_code'])<=100 or len(data['description'])>4000: raise Invalid('required_fields')
    if data.get('applid') is not None and (not isinstance(data['applid'],str) or not 1<=len(data['applid'])<=128): raise Invalid('invalid_filter')
    if type(data.get('filter_state')) is not int or data['filter_state'] not in (0,1,2): raise Invalid('invalid_filter')
    if type(data.get('filter_weight')) is not int or abs(data['filter_weight'])>2147483647: raise Invalid('invalid_filter')
    sev=data.get('severities')
    if sev is not None and (not isinstance(sev,list) or not 1<=len(sev)<=6 or any(type(x)is not int or x not in range(6) for x in sev)): raise Invalid('invalid_filter')
    criteria=data.get('criteria')
    if not isinstance(criteria,dict) or len(json.dumps(criteria))>16000: raise Invalid('invalid_criteria')
    for field, condition in criteria.items():
        if field not in CRITERIA or not isinstance(condition,dict) or set(condition)!={'operator','value'}: raise Invalid('invalid_criteria')
        op,value=condition['operator'],condition['value']
        if op not in ('eq','eq_ci','regex_ci') or type(value) not in (str,int,float,bool) or len(str(value))>512 or (op!='eq' and not isinstance(value,str)): raise Invalid('invalid_criteria')
    targets=data.get('targets')
    if not isinstance(targets,list) or len(targets)>5: raise Invalid('invalid_targets')
    seen=set()
    for target in targets:
        if not isinstance(target,dict): raise Invalid('invalid_targets')
        name=target.get('target')
        if name not in TARGETS or name in seen: raise Invalid('invalid_targets')
        seen.add(name)
        if target.get('behavior') not in ('enable','force_off','overlay') or type(target.get('dependsOnTicketing')) is not bool: raise Invalid('invalid_targets')
        for key in ('assignmentGroup','actionReference'):
            if target.get(key) is not None and (not isinstance(target[key],str) or len(target[key])>200): raise Invalid('invalid_targets')
        if target['behavior']!='force_off' and not (target.get('actionReference') if name=='extensions' else target.get('assignmentGroup')): raise Invalid('required_destination')
        delay=target.get('delaySeconds')
        if delay is not None and (type(delay)is not int or not 0<=delay<=2147483647): raise Invalid('invalid_targets')

def record(conn,kind,key):
    table,pk=TABLES[kind]
    row=conn.execute(f'SELECT * FROM event_management.{table} WHERE {pk}=%s', (key,)).fetchone()
    if row and kind=='filters':
        row['targets']=conn.execute('SELECT target,behavior,action_reference AS "actionReference",assignment_group AS "assignmentGroup",delay_seconds AS "delaySeconds",depends_on_ticketing AS "dependsOnTicketing" FROM event_management.delivery_filter_target WHERE filter_id=%s ORDER BY target',(key,)).fetchall()
    return row

def serial(value):
    if isinstance(value,(datetime.datetime,datetime.date)): return value.isoformat()
    raise TypeError()

def write(conn,kind,key,data,method):
    table,pk=TABLES[kind]
    before=None
    if method!='POST':
        # Lock parent first so target replacement and concurrency checks are atomic.
        conn.execute(f'SELECT {pk} FROM event_management.{table} WHERE {pk}=%s FOR UPDATE',(key,))
        before=record(conn,kind,key)
        if not before: raise Missing()
        if data.get('updated_at')!=before['updated_at'].isoformat(): raise Conflict('stale_record')
    if method=='DELETE':
        if kind=='customers' and conn.execute('SELECT 1 FROM event_management.delivery_filter WHERE customer_code=%s LIMIT 1',(key,)).fetchone(): raise Conflict('customer_in_use')
        conn.execute(f'DELETE FROM event_management.{table} WHERE {pk}=%s',(key,))
        after=None
    else:
        validate(kind,data)
        fields=CUSTOMER_FIELDS if kind=='customers' else FILTER_FIELDS
        if kind=='filters':
            if not conn.execute('SELECT customer_code FROM event_management.customer_configuration WHERE customer_code=%s FOR SHARE',(data['customer_code'],)).fetchone(): raise Invalid('customer_not_found')
        if method=='PUT' and kind=='customers' and data['customer_code']!=key: raise Invalid('immutable_customer_code')
        values=[Jsonb(data[f]) if f=='criteria' else data.get(f) for f in fields]
        if method=='POST':
            key=data['customer_code'] if kind=='customers' else 'console:'+str(uuid.uuid4())
            if kind=='filters': fields=('filter_id',)+fields; values=[key]+values
            conn.execute(f'INSERT INTO event_management.{table} ({",".join(fields)}) VALUES ({",".join(["%s"]*len(fields))})',values)
        else:
            conn.execute(f'UPDATE event_management.{table} SET '+','.join(f'{f}=%s' for f in fields)+f',updated_at=clock_timestamp() WHERE {pk}=%s',values+[key])
        if kind=='filters':
            conn.execute('DELETE FROM event_management.delivery_filter_target WHERE filter_id=%s',(key,))
            for t in data['targets']:
                conn.execute('INSERT INTO event_management.delivery_filter_target (filter_id,target,behavior,assignment_group,action_reference,delay_seconds,depends_on_ticketing) VALUES (%s,%s,%s,%s,%s,%s,%s)',(key,t['target'],t['behavior'],t.get('assignmentGroup'),t.get('actionReference'),t.get('delaySeconds'),t['dependsOnTicketing']))
        after=record(conn,kind,key)
    conn.execute('INSERT INTO event_management.console_catalog_audit(entity,entity_id,action,before_data,after_data) VALUES (%s,%s,%s,%s,%s)',(kind,key,method,Jsonb(json.loads(json.dumps(before,default=serial))),Jsonb(json.loads(json.dumps(after,default=serial)))))
    return after

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args): pass
    def respond(self,status,data):
        raw=json.dumps(data,default=serial).encode()
        self.send_response(status); self.send_header('Content-Type','application/json'); self.send_header('Cache-Control','no-store'); self.send_header('Content-Length',str(len(raw))); self.end_headers(); self.wfile.write(raw)
    def handle_request(self):
        try:
            if urlsplit(self.path).path.startswith('/api/ess/'):
                try: return self.respond(200,ess_proxy.handle(self.command,self.path,self.headers))
                except ess_proxy.Failure as e: return self.respond(e.status,{'errorCode':e.code})
            if self.command not in ('GET','POST','PUT','DELETE'): return self.respond(405,{'error':'method_not_allowed'})
            parsed=urlsplit(self.path)
            if self.command=='GET' and parsed.path=='/health':
                with connect() as conn: conn.execute('SELECT 1 FROM event_management.customer_configuration LIMIT 1')
                return self.respond(200,{'status':'UP'})
            if self.command=='GET' and parsed.path.startswith('/api/catalog/views/'):
                with connect() as conn: result=views.snapshot(conn,parsed.path.rsplit('/',1)[-1])
                return self.respond(200,result) if result is not None else self.respond(404,{'error':'not_found'})
            parts=parsed.path.strip('/').split('/')
            if len(parts) not in (3,4) or parts[:2]!=['api','catalog'] or parts[2] not in TABLES: return self.respond(404,{'error':'not_found'})
            kind=parts[2]; key=unquote(parts[3]) if len(parts)==4 else None
            if self.command=='GET':
                with connect() as conn:
                    if key:
                        row=record(conn,kind,key)
                        if row is None: raise Missing()
                        return self.respond(200,row)
                    q=parse_qs(parsed.query).get('q',[''])[0][:200]
                    table,pk=TABLES[kind]
                    # Literal substring search, not user-controlled SQL wildcards.
                    target_search=" OR EXISTS (SELECT 1 FROM event_management.delivery_filter_target d WHERE d.filter_id=t.filter_id AND strpos(lower(to_jsonb(d)::text),lower(%s))>0)" if kind=='filters' else ''
                    rows=conn.execute(f'SELECT {pk} FROM event_management.{table} t WHERE strpos(lower(to_jsonb(t)::text),lower(%s))>0'+target_search+f' ORDER BY {pk} LIMIT 2001', (q,q) if kind=='filters' else (q,)).fetchall()
                    if len(rows)>2000: return self.respond(422,{'error':'narrow_search'})
                    return self.respond(200,{'items':[record(conn,kind,r[pk]) for r in rows]})
            if (self.command=='POST' and key) or (self.command!='POST' and not key): raise Invalid('invalid_route')
            if self.headers.get('X-Console-Action')!='1' or self.headers.get('Content-Type','').split(';')[0]!='application/json': return self.respond(403,{'error':'forbidden'})
            origin=self.headers.get('Origin')
            if origin and urlsplit(origin).netloc!=self.headers.get('Host'): return self.respond(403,{'error':'forbidden'})
            size=int(self.headers.get('Content-Length','0'))
            if not 0<size<=65536: raise Invalid('invalid_payload')
            data=json.loads(self.rfile.read(size))
            if not isinstance(data,dict): raise Invalid('invalid_payload')
            with connect() as conn: result=write(conn,kind,key,data,self.command)
            self.respond(201 if self.command=='POST' else 200,{'item':result})
        except Missing: self.respond(404,{'error':'not_found'})
        except Conflict as e: self.respond(409,{'error':str(e)})
        except (Invalid,ValueError,TypeError) as e: self.respond(400,{'error':str(e) if isinstance(e,Invalid) else 'invalid_payload'})
        except psycopg.errors.UniqueViolation: self.respond(409,{'error':'already_exists'})
        except psycopg.IntegrityError: self.respond(400,{'error':'invalid_data'})
        except psycopg.Error: self.respond(503,{'error':'database_unavailable'})
        except Exception: self.respond(500,{'error':'internal_error'})
    do_GET=do_POST=do_PUT=do_DELETE=do_PATCH=do_OPTIONS=handle_request

if __name__=='__main__': ThreadingHTTPServer(('0.0.0.0',8094),Handler).serve_forever()
