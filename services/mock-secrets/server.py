"""Local synthetic vault. Values are write-only to HTTP; no production identity boundary."""
import json, os, sqlite3, uuid, re
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, parse_qs
from cryptography.fernet import Fernet
DB=os.environ.get('MOCK_SECRETS_DB','/data/vault.sqlite')
KEY=Path(os.environ.get('MOCK_SECRETS_KEY','/keys/vault.key'))
def initialize():
    os.umask(0o077);Path(DB).parent.mkdir(parents=True,exist_ok=True);KEY.parent.mkdir(parents=True,exist_ok=True)
    if not KEY.exists():
        if Path(DB).exists():raise RuntimeError('Existing vault requires its original key')
        with KEY.open('xb') as f:f.write(Fernet.generate_key())
    Fernet(KEY.read_bytes())
    with connect() as c:c.execute('CREATE TABLE IF NOT EXISTS secrets(id TEXT PRIMARY KEY,tenant TEXT NOT NULL,environment TEXT NOT NULL,name TEXT NOT NULL,kind TEXT NOT NULL,revision INTEGER NOT NULL,enabled INTEGER NOT NULL,value BLOB NOT NULL,updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(tenant,environment,name))')
def connect():
    c=sqlite3.connect(DB,timeout=5);c.row_factory=sqlite3.Row;return c
def metadata(row):return {k:(bool(row[k]) if k=='enabled' else row[k]) for k in ['id','tenant','environment','name','kind','revision','enabled','updated_at']}
def validate(d):
    if not isinstance(d,dict) or set(d)-{'tenant','environment','name','kind','value'}:raise ValueError()
    for k in ['tenant','environment','name']:
        if not isinstance(d.get(k),str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,99}',d[k]):raise ValueError()
    if d.get('kind') not in ['password','token'] or not isinstance(d.get('value'),str) or not 1<=len(d['value'])<=4096:raise ValueError()
class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def reply(self,status,data):
        raw=json.dumps(data).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
    def do_GET(self):
        p=urlsplit(self.path)
        if p.path=='/health':return self.reply(200,{'status':'UP','mode':'MOCK_ONLY'})
        if p.path!='/api/mock-secrets':return self.reply(404,{'error':'NOT_FOUND'})
        tenant=parse_qs(p.query).get('tenant',[''])[0]
        if not tenant:return self.reply(400,{'error':'TENANT_REQUIRED'})
        try:
            with connect() as c:rows=c.execute('SELECT * FROM secrets WHERE tenant=? ORDER BY environment,name LIMIT 501',(tenant,)).fetchall()
            self.reply(200,{'items':[metadata(r) for r in rows[:500]],'truncated':len(rows)>500,'mode':'MOCK_ONLY'})
        except Exception:self.reply(503,{'error':'VAULT_UNAVAILABLE'})
    def do_POST(self):self.mutate()
    def do_PUT(self):self.mutate()
    def mutate(self):
        if self.headers.get('X-Console-Action')!='mock-secrets':return self.reply(403,{'error':'ACTION_REQUIRED'})
        if self.headers.get('Content-Type','').split(';')[0]!='application/json':return self.reply(415,{'error':'JSON_REQUIRED'})
        try:
            size=int(self.headers.get('Content-Length','0'))
            if size<1 or size>16384:return self.reply(413,{'error':'INVALID_SIZE'})
            d=json.loads(self.rfile.read(size));validate(d)
            p=urlsplit(self.path).path
            if self.command=='POST' and p!='/api/mock-secrets':return self.reply(404,{'error':'NOT_FOUND'})
            if self.command=='PUT' and not re.fullmatch('/api/mock-secrets/[a-f0-9-]{36}',p):return self.reply(404,{'error':'NOT_FOUND'})
            cipher=Fernet(KEY.read_bytes()).encrypt(d.pop('value').encode())
            with connect() as c:
                if self.command=='POST':
                    id=str(uuid.uuid4());c.execute('INSERT INTO secrets(id,tenant,environment,name,kind,revision,enabled,value) VALUES(?,?,?,?,?,1,1,?)',(id,d['tenant'],d['environment'],d['name'],d['kind'],cipher))
                else:
                    id=p.rsplit('/',1)[1];etag=self.headers.get('If-Match')
                    if not etag:return self.reply(428,{'error':'IF_MATCH_REQUIRED'})
                    cur=c.execute('UPDATE secrets SET value=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant=? AND environment=? AND name=? AND kind=? AND revision=?',(cipher,id,d['tenant'],d['environment'],d['name'],d['kind'],etag.strip('"')))
                    if not cur.rowcount:return self.reply(409,{'error':'REVISION_CONFLICT'})
                row=c.execute('SELECT * FROM secrets WHERE id=?',(id,)).fetchone()
            self.reply(201 if self.command=='POST' else 200,metadata(row))
        except (ValueError,TypeError,KeyError):self.reply(400,{'error':'INVALID_FIELDS'})
        except sqlite3.IntegrityError:self.reply(409,{'error':'NAME_EXISTS'})
        except Exception:self.reply(503,{'error':'VAULT_UNAVAILABLE'})
if __name__=='__main__':initialize();ThreadingHTTPServer(('0.0.0.0',8096),Handler).serve_forever()
