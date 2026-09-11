"""Server-side GLPI REST V1 adapter; no credentials are forwarded to WebUI."""
import json
import os
from datetime import datetime
from zoneinfo import ZoneInfo
from urllib.request import Request, build_opener, HTTPRedirectHandler

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError("GLPI redirects are disabled")

class GlpiClient:
    def __init__(self):
        self.base = os.getenv("GLPI_BASE_URL", "http://glpi-mock:8080/apirest.php").rstrip("/")
        self.app = os.getenv("GLPI_APP_TOKEN", "local-app")
        self.user = os.getenv("GLPI_USER_TOKEN", "local-user")
        self.session = None
        self.content_range = ""

    def call(self, method, path, payload=None):
        headers = {"Content-Type": "application/json", "App-Token": self.app}
        headers.update({"Session-Token": self.session} if self.session else {"Authorization": "user_token " + self.user})
        request = Request(self.base + path, data=None if payload is None else json.dumps(payload).encode(), headers=headers, method=method)
        with build_opener(NoRedirect()).open(request, timeout=5) as response:
            if response.headers.get_content_type() != "application/json":
                raise ValueError("Invalid GLPI response")
            body = response.read(4_000_001)
            if len(body) > 4_000_000:
                raise ValueError("GLPI response too large")
            self.content_range = response.headers.get("Content-Range", "")
            return json.loads(body)

    def __enter__(self):
        self.session = self.call("GET", "/initSession")["session_token"]
        if not isinstance(self.session, str) or not self.session:
            raise ValueError("Missing GLPI session")
        return self

    def __exit__(self, *_):
        try:
            self.call("GET", "/killSession")
        except Exception:
            pass

    def tickets(self):
        items = []
        for start in range(0, 10000, 100):
            batch = self.call("GET", f"/Ticket?range={start}-{start+99}&sort=id&order=ASC")
            if not isinstance(batch, list):
                raise ValueError("Invalid GLPI ticket list")
            for ticket in batch:
                value = ticket.get("date_mod")
                if isinstance(value, str):
                    date = datetime.fromisoformat(value.replace("Z", "+00:00"))
                    if date.tzinfo is None:
                        date = date.replace(tzinfo=ZoneInfo(os.getenv("GLPI_TIMEZONE", "UTC")))
                    ticket["date_mod"] = date.isoformat()
            items.extend(batch)
            total = self.content_range.rsplit("/", 1)[-1]
            if len(batch) < 100 or (total.isdigit() and start + len(batch) >= int(total)):
                return items
        raise ValueError("GLPI inventory exceeds 10000 tickets; narrow provider scope")

    def close(self, ticket_id, note, solution_type):
        ticket = self.call("GET", f"/Ticket/{ticket_id}")
        if ticket.get("status") == 6:
            return ticket
        if ticket.get("status") != 5:
            solution = self.call("POST", "/ITILSolution", {"input": {"itemtype": "Ticket", "items_id": ticket_id, "content": note, "solutiontypes_id": solution_type}})
            if not isinstance(solution, dict) or not isinstance(solution.get("id"), int):
                raise ValueError("GLPI solution not confirmed")
            ticket = self.call("GET", f"/Ticket/{ticket_id}")
            if ticket.get("status") not in (5, 6):
                raise ValueError("GLPI solution requires review")
        if ticket.get("status") != 6:
            self.call("PUT", f"/Ticket/{ticket_id}", {"input": {"id": ticket_id, "status": 6}})
        ticket = self.call("GET", f"/Ticket/{ticket_id}")
        if ticket.get("id") != ticket_id or ticket.get("status") != 6:
            raise ValueError("GLPI closure not confirmed")
        return ticket
