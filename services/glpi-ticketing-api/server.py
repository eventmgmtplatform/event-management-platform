import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit
from client import GlpiClient

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def reply(self, status, data):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/health":
            return self.reply(200, {"status": "UP", "component": "glpi-ticketing-api"})
        if path == "/ready":
            try:
                with GlpiClient() as client:
                    client.call("GET", "/Ticket?range=0-0")
                return self.reply(200, {"status": "UP"})
            except Exception:
                return self.reply(503, {"status": "DOWN"})
        if path != "/api/glpi/tickets":
            return self.reply(404, {"error": "Not found"})
        try:
            with GlpiClient() as client:
                return self.reply(200, {"tickets": client.tickets()})
        except Exception:
            return self.reply(502, {"error": "GLPI no disponible; verifica conexión y permisos."})

    def do_POST(self):
        if self.headers.get_content_type() != "application/json":
            return self.reply(415, {"error": "application/json required"})
        match = re.fullmatch(r"/api/glpi/tickets/([1-9][0-9]*)/close", self.path)
        if not match:
            return self.reply(404, {"error": "Not found"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 20000:
                raise ValueError()
            payload = json.loads(self.rfile.read(length))
            note, code = payload.get("note"), payload.get("solutionTypeId", 0)
            if not isinstance(note, str) or not 10 <= len(note.strip()) <= 4000 or type(code) is not int or code < 0:
                raise ValueError()
        except (ValueError, TypeError, AttributeError):
            return self.reply(400, {"error": "Nota o tipo de solución inválido."})
        try:
            with GlpiClient() as client:
                return self.reply(200, {"ticket": client.close(int(match[1]), note.strip(), code)})
        except Exception:
            return self.reply(502, {"error": "Cierre no confirmado. Consulta el ticket antes de reintentar."})

if __name__ == "__main__":
    ThreadingHTTPServer((os.getenv("GLPI_BIND", "0.0.0.0"), int(os.getenv("GLPI_PORT", "8095"))), Handler).serve_forever()
