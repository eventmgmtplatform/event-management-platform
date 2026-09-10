"""Isolated, stateful ServiceNow Table API subset for the Console plugin."""
import datetime
import json
import os
import re
import sqlite3
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

DATABASE = os.environ.get('MOCK_DATABASE', '/data/incidents.sqlite')
SEED = Path(__file__).with_name('seed.json')
TABLE = '/api/now/table/incident'
CLOSE_CODES = {'Solved (Permanently)', 'Solved (Work Around)', 'Not Solved'}
STATES = {'1', '2', '3', '6', '7', '-1'}

def connect():
    connection = sqlite3.connect(DATABASE, timeout=5)
    connection.row_factory = sqlite3.Row
    return connection

def initialize():
    with connect() as db:
        db.execute('CREATE TABLE IF NOT EXISTS incidents (sys_id TEXT PRIMARY KEY, document TEXT NOT NULL)')
        db.execute('CREATE TABLE IF NOT EXISTS audit (id INTEGER PRIMARY KEY, sys_id TEXT, at TEXT, code TEXT, note TEXT)')
        # INSERT OR IGNORE seeds once and preserves edits across container restarts.
        for item in json.loads(SEED.read_text()):
            db.execute('INSERT OR IGNORE INTO incidents VALUES (?, ?)', (item['sys_id'], json.dumps(item)))

def search(query):
    terms = query.split('^') if query else []
    predicates = []
    for term in terms:
        match = re.fullmatch(r'(number|state|short_description|cmdb_ci)(LIKE|=)([^\^]*)', term)
        if not match:
            raise ValueError('unsupported_query')
        field, operator, value = match.groups()
        predicates.append((field, operator, value))
    with connect() as db:
        items = [json.loads(row['document']) for row in db.execute('SELECT document FROM incidents ORDER BY sys_id')]
    return [item for item in items if all((value.casefold() in item[field].casefold()) if op == 'LIKE' else item[field] == value for field, op, value in predicates)]

def close_incident(sys_id, payload):
    if set(payload) != {'state', 'close_code', 'close_notes'} or payload.get('state') != '7':
        raise ValueError('only_close_supported')
    code, note = payload.get('close_code'), payload.get('close_notes')
    if code not in CLOSE_CODES or not isinstance(note, str) or not 10 <= len(note.strip()) <= 4000:
        raise ValueError('invalid_close_details')
    with connect() as db:
        db.execute('BEGIN IMMEDIATE')
        row = db.execute('SELECT document FROM incidents WHERE sys_id=?', (sys_id,)).fetchone()
        if row is None:
            raise LookupError('incident_not_found')
        item = json.loads(row['document'])
        if item['state'] == '7':
            return item  # Repeated close is idempotent; original audit stays intact.
        at = datetime.datetime.now(datetime.timezone.utc).isoformat()
        item.update(state='7', close_code=code, close_notes=note.strip(), sys_updated_on=at)
        db.execute('UPDATE incidents SET document=? WHERE sys_id=?', (json.dumps(item), sys_id))
        db.execute('INSERT INTO audit(sys_id,at,code,note) VALUES(?,?,?,?)', (sys_id, at, code, note.strip()))
        return item

class Handler(BaseHTTPRequestHandler):
    def respond(self, status, data):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlsplit(self.path)
        if parsed.path in ('/health', '/__admin/health'):
            try:
                with connect() as db: db.execute('SELECT count(*) FROM incidents').fetchone()
                return self.respond(200, {'status': 'UP', 'source': 'servicenow-console-mock'})
            except sqlite3.Error:
                return self.respond(503, {'error': 'storage_unavailable'})
        if parsed.path == TABLE:
            try:
                params = parse_qs(parsed.query)
                items = search(params.get('sysparm_query', [''])[0])
                return self.respond(200, {'result': items})
            except ValueError:
                return self.respond(400, {'error': 'unsupported_query'})
        if parsed.path.startswith(TABLE + '/'):
            with connect() as db:
                row = db.execute('SELECT document FROM incidents WHERE sys_id=?', (parsed.path[len(TABLE)+1:],)).fetchone()
            return self.respond(200, {'result': json.loads(row['document'])}) if row else self.respond(404, {'error': 'incident_not_found'})
        self.respond(404, {'error': 'not_found'})

    def do_PATCH(self):
        path = urlsplit(self.path).path
        if not re.fullmatch(re.escape(TABLE) + r'/console-INC\d+', path):
            return self.respond(404, {'error': 'not_found'})
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if not 0 < size <= 16384:
                return self.respond(413, {'error': 'invalid_body_size'})
            payload = json.loads(self.rfile.read(size))
            if not isinstance(payload, dict): raise ValueError()
            return self.respond(200, {'result': close_incident(path.split('/')[-1], payload)})
        except (ValueError, TypeError):
            self.respond(400, {'error': 'invalid_close_request'})
        except LookupError:
            self.respond(404, {'error': 'incident_not_found'})
        except sqlite3.Error:
            self.respond(503, {'error': 'storage_unavailable'})

if __name__ == '__main__':
    initialize()
    ThreadingHTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
