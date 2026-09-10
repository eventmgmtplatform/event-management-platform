"""OpenAPI tool: fixed UC-001 runner, asynchronous execution, durable evidence."""
import fcntl
import hmac
import json
import os
import re
import subprocess
import sys
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
STORE = ROOT / 'evidences/testing/openwebui'
COMMAND = [sys.executable, str(ROOT / 'testing/run.py'), 'happy-path']
SCOPE = 'UC-001: laboratorio os11-lifecycle; proveedores mock; no certifica proveedores reales ni UI de navegador'


def result_from_log(log, code):
    matches = re.findall(r'^(PASS|FAIL|BLOCKED): (.+/report\.json)$', log, re.MULTILINE)
    if not matches:
        return {'status': 'FAIL', 'error': 'El runner terminó sin reporte verificable', 'exitCode': code}
    path = Path(matches[-1][1]).resolve()
    if not path.is_relative_to(ROOT / 'evidences/testing') or path.parent.name != 'happy-path':
        raise ValueError('Ruta de evidencia fuera del runner autorizado')
    report = json.loads(path.read_text())
    passed = code == 0 and report.get('status') == 'PASS' and report.get('caseId') == 'UC-001'
    return {'status': 'PASS' if passed else 'FAIL', 'exitCode': code,
            'reportPath': str(path), 'report': report}


class Runs:
    def __init__(self, directory=STORE):
        self.directory = directory
        directory.mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()
        # Refuse a second server process operating the same evidence store.
        self.lease = (directory / 'server.lock').open('a')
        fcntl.flock(self.lease, fcntl.LOCK_EX | fcntl.LOCK_NB)
        self.active = None
        for path in directory.glob('*.json'):
            data = json.loads(path.read_text())
            if data['status'] == 'RUNNING':
                data.update(status='INTERRUPTED', error='Servidor reiniciado: comprobar proceso y reglas del caso antes de reintentar')
                self.save(data)
        self.interrupted = any(json.loads(p.read_text())['status'] == 'INTERRUPTED' for p in directory.glob('*.json'))

    def save(self, data):
        path = self.directory / (data['runId'] + '.json')
        temporary = path.with_suffix('.tmp')
        temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
        temporary.replace(path)

    def get(self, run_id):
        if not re.fullmatch(r'[0-9a-f]{32}', run_id):
            raise FileNotFoundError
        return json.loads((self.directory / (run_id + '.json')).read_text())

    def start(self):
        with self.lock:
            if self.interrupted:
                return {'status': 'BLOCKED', 'error': 'Hay una ejecución interrumpida. Requiere revisión operativa antes de ejecutar otro caso.'}
            if self.active:
                return self.get(self.active)
            run_id = uuid.uuid4().hex
            self.save({'runId': run_id, 'status': 'RUNNING', 'scope': SCOPE})
            self.active = run_id
            threading.Thread(target=self.execute, args=(run_id,), daemon=False).start()
            return self.get(run_id)

    def execute(self, run_id):
        data = self.get(run_id)
        log_path = self.directory / (run_id + '.log')
        try:
            # The existing scenario has bounded I/O and polling and owns cleanup.
            # Do not expose command, paths, environment or shell arguments to the model.
            with log_path.open('w') as log:
                process = subprocess.run(COMMAND, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            data.update(result_from_log(log_path.read_text(), process.returncode))
        except Exception as error:
            data.update(status='FAIL', error=str(error))
        finally:
            with self.lock:
                self.save(data)
                self.active = None


def schema():
    response = {'description': 'Estado real, alcance y evidencia del runner',
                'content': {'application/json': {'schema': {'type': 'object', 'additionalProperties': True}}}}
    return {'openapi': '3.0.3', 'info': {'title': 'Validación E2E Event Management', 'version': '1.0.0'},
            'security': [{'bearerAuth': []}],
            'components': {'securitySchemes': {'bearerAuth': {'type': 'http', 'scheme': 'bearer'}}},
            'paths': {
                '/runs': {'post': {'operationId': 'ejecutar_validacion_e2e',
                    'summary': 'Ejecuta la prueba de validación E2E UC-001 en laboratorio aislado',
                    'description': 'Ejecuta el caso completo cuando el usuario lo solicite. Devuelve runId. RUNNING no significa aprobado. Consulta consultar_validacion_e2e hasta estado terminal. No reintentes automáticamente un fallo.',
                    'responses': {'200': response}}},
                '/runs/{run_id}': {'get': {'operationId': 'consultar_validacion_e2e',
                    'summary': 'Consulta resultado y evidencias de una ejecución E2E',
                    'parameters': [{'name': 'run_id', 'in': 'path', 'required': True, 'schema': {'type': 'string', 'pattern': '^[0-9a-f]{32}$'}}],
                    'responses': {'200': response, '404': {'description': 'Ejecución inexistente'}}}}}}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Do not log authorization headers or request data.

    def reply(self, status, data):
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        if not hmac.compare_digest(self.headers.get('Authorization', '').encode(),
                                   ('Bearer ' + self.server.token).encode()):
            self.reply(401, {'error': 'Unauthorized'})
            return False
        return True

    def do_GET(self):
        if not self.authorized():
            return
        path = urlsplit(self.path).path
        if path == '/openapi.json':
            return self.reply(200, schema())
        if path.startswith('/runs/'):
            try:
                return self.reply(200, self.server.runs.get(path[6:]))
            except FileNotFoundError:
                pass
        self.reply(404, {'error': 'Not found'})

    def do_POST(self):
        if not self.authorized():
            return
        if self.path != '/runs':
            return self.reply(404, {'error': 'Not found'})
        # No tool arguments: reject extra payloads, including arbitrary commands.
        length = self.headers.get('Content-Length', '0')
        if self.headers.get('Transfer-Encoding') or not length.isdigit() or int(length) > 2:
            return self.reply(400, {'error': 'Esta herramienta no acepta parámetros'})
        if self.rfile.read(int(length)) not in (b'', b'{}'):
            return self.reply(400, {'error': 'Esta herramienta no acepta parámetros'})
        self.reply(200, self.server.runs.start())


def main():
    token = os.environ.get('E2E_TOOL_TOKEN', '')
    if len(token) < 32:
        raise SystemExit('E2E_TOOL_TOKEN debe contener al menos 32 caracteres')
    server = ThreadingHTTPServer((os.environ.get('E2E_TOOL_HOST', '127.0.0.1'),
                                 int(os.environ.get('E2E_TOOL_PORT', '8095'))), Handler)
    server.token = token
    server.runs = Runs()
    server.serve_forever()


if __name__ == '__main__':
    main()
