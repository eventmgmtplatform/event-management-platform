"""Bounded asynchronous bridge to the existing local service lifecycle CLI."""
import datetime
import json
import os
import re
import signal
import sqlite3
import subprocess
import threading
from pathlib import Path

ROOT = '/opt/event-management-platform'
SCRIPT = ROOT + '/scripts/eventmanagement-services.sh'
DATABASE = os.environ.get('CONTROL_DATABASE', '/data/operations.sqlite')
ACTIONS = {'start', 'stop', 'restart'}
# Deliberately excludes the API executing this process, non-Compose legacy services,
# and one-shot initialization jobs. Never accepts all or certification runtimes.
SERVICES = {'kafka', 'postgres', 'opensearch', 'event-gateway', 'event-processor',
            'integration-worker', 'event-state-service', 'event-management-console',
            'itsm-ticketing-dashboard', 'kafka-ui', 'opensearch-dashboards',
            'servicenow-mock', 'gnm-mock', 'aiops-mock', 'servicenow-console-mock', 'console-catalog-api'}

def now(): return datetime.datetime.now(datetime.timezone.utc).isoformat()
def connect():
    db = sqlite3.connect(DATABASE, timeout=5)
    db.row_factory = sqlite3.Row
    return db

def initialize():
    with connect() as db:
        db.execute('CREATE TABLE IF NOT EXISTS operations (id TEXT PRIMARY KEY, service TEXT, action TEXT, status TEXT, createdAt TEXT, finishedAt TEXT, exitCode INTEGER)')
        # Never replay a potentially completed action after a crash.
        db.execute("UPDATE operations SET status='interrupted', finishedAt=? WHERE status='running'", (now(),))

def get_operation(operation_id):
    with connect() as db:
        row = db.execute('SELECT * FROM operations WHERE id=?', (operation_id,)).fetchone()
        return dict(row) if row else None

def execute(service, action):
    env = dict(os.environ, EVENTMANAGEMENT_RUNTIME='local', EVENTMANAGEMENT_WAIT_TIMEOUT='240', EVENTMANAGEMENT_STOP_TIMEOUT='60', COMPOSE_PROJECT_NAME='event-management')
    # Argument vector, fixed script, no shell interpolation; output may contain
    # deployment details and is intentionally not exposed in the web API.
    process = subprocess.Popen(['bash', SCRIPT, service, action], cwd=ROOT, env=env,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                               start_new_session=True)
    try:
        return process.wait(timeout=900)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGTERM)
        try: process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
        return 124

def run(operation_id, service, action):
    try: code = execute(service, action)
    except OSError: code = 127
    with connect() as db:
        db.execute('UPDATE operations SET status=?, finishedAt=?, exitCode=? WHERE id=?',
                   ('succeeded' if code == 0 else 'failed', now(), code, operation_id))

def submit(payload, dispatch=True):
    if not isinstance(payload, dict) or set(payload) != {'id', 'service', 'action'}:
        raise ValueError('invalid_request')
    op_id, service, action = payload['id'], payload['service'], payload['action']
    if not all(isinstance(v, str) for v in (op_id, service, action)) or not re.fullmatch(r'[a-f0-9-]{36}', op_id) or service not in SERVICES or action not in ACTIONS:
        raise ValueError('unsupported_action')
    with connect() as db:
        db.execute('BEGIN IMMEDIATE')
        existing = db.execute('SELECT * FROM operations WHERE id=?', (op_id,)).fetchone()
        if existing:
            if existing['service'] != service or existing['action'] != action: raise ValueError('id_conflict')
            return dict(existing)
        if db.execute("SELECT 1 FROM operations WHERE status='running'").fetchone():
            raise RuntimeError('operation_in_progress')
        db.execute('INSERT INTO operations VALUES (?,?,?,?,?,?,?)', (op_id, service, action, 'running', now(), None, None))
    if dispatch:
        threading.Thread(target=run, args=(op_id, service, action), daemon=True).start()
    return get_operation(op_id)
