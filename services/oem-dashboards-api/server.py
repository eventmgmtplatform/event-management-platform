import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

from dashboard import CONFIG_PATH, DOMAINS, DashboardError, load_dashboard, parse_query, read_config, source_for


from delivery import parse_delivery_query
from collection import parse_collection_query


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Do not log query identifiers or upstream credentials.

    def reply(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlsplit(self.path)
        if url.path == "/health":
            return self.reply(200, {"status": "UP", "component": "oem-dashboards-api", "version": "0.1.0"})
        try:
            config = read_config()
            if url.path == "/api/dashboards/config":
                return self.reply(200, {"sources": {d: source_for(config, d) for d in DOMAINS}})
            if url.path == "/ready":
                for domain in DOMAINS:
                    load_dashboard(config, domain, (parse_collection_query if domain == "data-collection" else parse_delivery_query if domain == "delivery" else parse_query)({"limit": ["1"]}))
                return self.reply(200, {"status": "UP"})
            prefix = "/api/dashboards/"
            if not url.path.startswith(prefix) or url.path[len(prefix):] not in DOMAINS:
                return self.reply(404, {"error": "Ruta no encontrada."})
            domain = url.path[len(prefix):]
            try:
                query = (parse_collection_query if domain == "data-collection" else parse_delivery_query if domain == "delivery" else parse_query)(parse_qs(url.query, keep_blank_values=True, max_num_fields=12))
            except (DashboardError, ValueError) as exc:
                return self.reply(400, {"error": str(exc) if isinstance(exc, DashboardError) else "Filtros inválidos."})
            return self.reply(200, load_dashboard(config, domain, query))
        except DashboardError as exc:
            return self.reply(503, {"error": str(exc)})
        except Exception:
            return self.reply(503, {"error": "Consulta no disponible."})

    def do_POST(self):
        self.reply(405, {"error": "Dashboards sólo admite consultas."})

    do_PUT = do_PATCH = do_DELETE = do_POST


if __name__ == "__main__":
    server = ThreadingHTTPServer((os.getenv("OEM_BIND", "127.0.0.1"), int(os.getenv("OEM_PORT", "8092"))), Handler)
    server.daemon_threads = True
    print(f"OEM Dashboards API on {server.server_address}; config={CONFIG_PATH}", flush=True)
    server.serve_forever()
