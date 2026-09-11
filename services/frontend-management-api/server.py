"""Local inventory BFF. Only fixed Docker GET inspection; never returns raw inspection."""
import concurrent.futures
import datetime
import http.client
import json
import socket
import urllib.request
import control
import middleware
import apis
from urllib.parse import urlsplit
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# Explicit main-runtime names prevent accidental inclusion of certification stacks.
CATALOG = [
    ('console-catalog-api', 'Console Catalog API', 'Core', 'console-catalog-api'),
    ('kafka', 'Kafka', 'Infraestructura', 'event-kafka'),
    ('postgres', 'PostgreSQL', 'Infraestructura', 'event-postgres'),
    ('opensearch', 'OpenSearch', 'Infraestructura', 'event-opensearch'),
    ('event-gateway', 'Event Gateway', 'Core', 'event-gateway'),
    ('event-processor', 'Event Processor', 'Core', 'event-event-processor'),
    ('integration-worker', 'Integration Worker', 'Core', 'event-integration-worker'),
    ('event-state-service', 'Event State Service', 'Core', 'event-state-service'),
    ('enrichment-engine', 'Enrichment Engine', 'Core', 'event-enrichment-engine'),
    ('event-management-console', 'Management Console', 'Interfaces', 'event-management-console'),
    ('oem-dashboards', 'ITSM Dashboard', 'Interfaces', 'event-management-console'),
    ('itsm-ticketing-dashboard', 'ITSM Dashboard legacy', 'Interfaces', 'event-itsm-ticketing-dashboard'),
    ('kafka-ui', 'Kafka UI', 'Interfaces', 'event-kafka-ui'),
    ('opensearch-dashboards', 'OpenSearch Dashboards', 'Interfaces', 'event-opensearch-dashboards'),
    ('open-webui', 'Open WebUI', 'Interfaces', 'open-webui'),
    ('frontend-management-api', 'Frontend Management API', 'Core', 'frontend-management-api'),
    ('servicenow-console-mock', 'ServiceNow Console Mock', 'Integraciones', 'event-servicenow-console-mock'),
    ('glpi-mock', 'GLPI Mock', 'Integraciones', 'event-glpi-mock'),
    ('glpi-ticketing-api', 'GLPI Ticketing API', 'Integraciones', 'event-glpi-ticketing-api'),
    ('servicenow-mock', 'ServiceNow Mock', 'Integraciones', 'event-servicenow-mock'),
    ('gnm-mock', 'GNM Mock', 'Integraciones', 'event-gnm-mock'),
    ('aiops-mock', 'AIOps Mock', 'Integraciones', 'event-aiops-mock'),
    ('next-mock', 'NEXT Mock · CACF', 'Integraciones', 'event-next-mock'),
    ('oem-dashboards-api', 'Dashboards API', 'Core', 'event-management-oem-dashboards-api-1'),
    ('product-observability', 'Product Observability', 'Interfaces', 'event-product-observability'),
    ('kafka-init', 'Kafka Init', 'Tareas', 'event-kafka-init'),
]

class DockerConnection(http.client.HTTPConnection):
    def __init__(self):
        super().__init__('localhost', timeout=2)
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect('/var/run/docker.sock')


def inspect_container(name):
    connection = DockerConnection()
    try:
        connection.request('GET', f'/containers/{name}/json')
        response = connection.getresponse()
        if response.status == 404:
            return None
        if response.status != 200:
            raise RuntimeError('docker_unavailable')
        return json.loads(response.read())
    finally:
        connection.close()


def classify(state, one_shot=False):
    runtime = state.get('Status', 'unknown')
    health = state.get('Health', {}).get('Status')
    if one_shot and runtime == 'exited' and state.get('ExitCode') == 0:
        return 'completed', 'completed'
    if runtime in ('exited', 'created'):
        return ('error', 'exit_error') if state.get('ExitCode', 0) != 0 and state.get('ExitCode') not in (137, 143) else ('stopped', 'stopped')
    if runtime in ('dead', 'restarting', 'removing') or health == 'unhealthy':
        return 'error', 'unhealthy' if health == 'unhealthy' else runtime
    if runtime == 'paused':
        return 'degraded', 'paused'
    if runtime != 'running':
        return 'unknown', 'unverified'
    if health == 'healthy':
        return 'healthy', 'healthy'
    if health == 'starting':
        return 'degraded', 'starting'
    return 'unknown', 'no_healthcheck'


def service_record(entry, inspect=inspect_container):
    service_id, name, category, container = entry
    result = dict(id=service_id, name=name, category=category, description=service_id,
                  actions=sorted(control.ACTIONS) if service_id in control.SERVICES else [], status='unknown', reason='docker_unavailable', version=None, port=None,
                  container=container, runtime='unknown', health='unknown', restartCount=None)
    try:
        data = inspect(container)
        if data is None:
            result.update(status='error', reason='missing', runtime='missing')
            return result
        state = data.get('State', {})
        status, reason = classify(state, service_id == 'kafka-init')
        config = data.get('Config', {})
        image = config.get('Image', '')
        # Container labels may be inherited from Ubuntu or another base image.
        # Use the deployed image tag, never a base-image OS version.
        version = None
        if ':' in image.rsplit('/', 1)[-1] and '@' not in image and not image.startswith('sha256:'):
            version = image.rsplit(':', 1)[-1]
        ports = data.get('NetworkSettings', {}).get('Ports', {}) or {}
        published = sorted({int(binding['HostPort']) for bindings in ports.values() for binding in (bindings or [])})
        result.update(status=status, reason=reason, version=version, port=(8091 if 8091 in published else None) if service_id == 'oem-dashboards' else published[0] if published else None,
                      runtime=state.get('Status', 'unknown'), health=state.get('Health', {}).get('Status', 'none'),
                      restartCount=data.get('RestartCount', 0))
    except (OSError, ValueError, RuntimeError, http.client.HTTPException):
        pass
    return result


def snapshot():
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(CATALOG)) as pool:
        services = list(pool.map(service_record, CATALOG))
    return {'observedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'services': services}


def source_connections():
    try:
        inspect_container('event-management-console')
        docker_health = 'healthy'
    except (OSError, ValueError, RuntimeError, http.client.HTTPException):
        docker_health = 'error'
    try:
        with urllib.request.urlopen('http://servicenow-console-mock:8080/health', timeout=2) as response:
            snow_health = 'healthy' if response.status == 200 else 'error'
    except (OSError, ValueError):
        snow_health = 'error'
    try:
        with urllib.request.urlopen('http://glpi-ticketing-api:8095/ready', timeout=6) as response:
            glpi_health = 'healthy' if response.status == 200 else 'error'
    except (OSError, ValueError):
        glpi_health = 'error'
    return {'sources': [
        {'id': 'runtime', 'health': docker_health, 'endpoint': '/api/administration/platform', 'target': 'Docker local'},
        {'id': 'glpi', 'health': glpi_health, 'endpoint': '/api/glpi/tickets', 'target': 'glpi-ticketing-api'},
        {'id': 'tickets', 'health': snow_health, 'endpoint': '/api/now/table/incident', 'target': 'servicenow-console-mock'},
    ]}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/api/administration/apis':
            return self.respond(200, apis.snapshot())
        if self.path == '/api/administration/middleware':
            try: return self.respond(200, middleware.snapshot())
            except (OSError, ValueError, KeyError): return self.respond(503, {'error': 'kafka_unavailable'})
        if self.path.startswith('/api/administration/operations/'):
            operation = control.get_operation(self.path.rsplit('/', 1)[-1])
            return self.respond(200, operation) if operation else self.respond(404, {'error': 'not_found'})
        if self.path == '/health':
            self.respond(200, {'status': 'UP'})
        elif self.path == '/api/administration/sources':
            self.respond(200, source_connections())
        elif self.path == '/api/administration/platform':
            data = snapshot()
            unavailable = all(s['reason'] == 'docker_unavailable' for s in data['services'])
            self.respond(503 if unavailable else 200, data)
        else:
            self.respond(404, {'error': 'not_found'})

    def do_POST(self):
        if self.path not in ('/api/administration/operations', '/api/administration/apis/actions'):
            return self.respond(404, {'error': 'not_found'})
        origin = self.headers.get('Origin')
        if self.headers.get('X-Console-Action') != '1' or self.headers.get('Content-Type') != 'application/json' or (origin and urlsplit(origin).netloc != self.headers.get('Host')):
            return self.respond(403, {'error': 'forbidden'})
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if not 0 < size <= 1024: raise ValueError()
            payload = json.loads(self.rfile.read(size))
            if self.path == '/api/administration/apis/actions':
                status, result = apis.action(payload)
                return self.respond(status, result)
            operation = control.submit(payload)
            self.respond(202, operation)
        except ValueError:
            self.respond(400, {'error': 'unsupported_action'})
        except RuntimeError:
            self.respond(409, {'error': 'operation_in_progress'})

    def respond(self, status, data):
        payload = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(payload)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(payload)

if __name__ == '__main__':
    control.initialize()
    ThreadingHTTPServer(('0.0.0.0', 8093), Handler).serve_forever()
