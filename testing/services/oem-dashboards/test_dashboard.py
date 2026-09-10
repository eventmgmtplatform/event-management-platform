import copy
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "services/oem-dashboards-api"))
from dashboard import (DashboardError, InternalApiSource, PostgresSource, load_dashboard,
                       parse_query, read_config, validate_snapshot)
from cli import set_source
from server import Handler

QUERY = parse_query({})
EMPTY = {"schemaVersion": "1.0", "domain": "events", "observedAt": "2026-09-10T00:00:00Z",
         "lastUpdatedAt": None, "total": 0, "page": 1, "limit": 25, "counts": {}, "rows": []}


class ContractTests(unittest.TestCase):
    def test_filter_validation(self):
        for args in ({"page": ["0"]}, {"limit": ["101"]}, {"tenant": ["a", "b"]}, {"sql": ["x"]}, {"page": ["nan"]}):
            with self.assertRaises(DashboardError): parse_query(args)
        self.assertEqual(parse_query({"q": ["' OR 1=1 --"]})["q"], "' OR 1=1 --")

    def test_contract_filters_private_fields_and_rejects_inconsistency(self):
        self.assertNotIn("secret", validate_snapshot({**EMPTY, "secret": "private"}, "events", QUERY))
        for change in ({"total": 1}, {"domain": "gnm"}, {"rows": [{}]}, {"observedAt": "yesterday"}, {"counts": {"OPEN": True}}, {"counts": []}, {"page": True}):
            with self.assertRaises(DashboardError): validate_snapshot({**EMPTY, **change}, "events", QUERY)

    def test_atomic_source_change_and_rollback(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "config.json"
            set_source(path, "all", "postgresql", lambda *_: None)
            original = path.read_text()
            with self.assertRaises(DashboardError):
                set_source(path, "gnm", "internal-api", lambda *_: (_ for _ in ()).throw(DashboardError("offline")))
            self.assertEqual(path.read_text(), original)
            calls = []
            def fails_after_save(*_):
                calls.append(1)
                if len(calls) == 2: raise DashboardError("verification failed")
            with self.assertRaises(DashboardError): set_source(path, "gnm", "internal-api", fails_after_save)
            self.assertEqual(path.read_text(), original)
            set_source(path, "gnm", "internal-api", lambda *_: None)
            self.assertEqual(read_config(path)["sources"], {"gnm": "internal-api"})
            set_source(path, "all", "postgresql", lambda *_: None)
            self.assertEqual(read_config(path)["sources"], {})

    def test_http_errors_and_no_mutations(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        base = f"http://127.0.0.1:{server.server_port}"
        try:
            with patch.dict(os.environ, {"OEM_POSTGRES_DSN": ""}):
                for path, status in (("/api/dashboards/unknown",404), ("/api/dashboards/events?limit=500",400), ("/api/dashboards/events",503)):
                    with self.assertRaises(HTTPError) as error: urlopen(base+path)
                    self.assertEqual(error.exception.code, status)
                with self.assertRaises(HTTPError) as error: urlopen(base+"/api/dashboards/events", data=b"{}")
                self.assertEqual(error.exception.code, 405)
        finally:
            server.shutdown(); server.server_close()

    def test_internal_api_contract_and_redirect_rejection(self):
        class Upstream(BaseHTTPRequestHandler):
            redirect = False
            def do_GET(self):
                self.send_response(302 if self.redirect else 200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Location", "/other")
                self.end_headers(); self.wfile.write(json.dumps(EMPTY).encode())
            def log_message(self, *_): pass
        server = ThreadingHTTPServer(("127.0.0.1", 0), Upstream)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            with patch.dict(os.environ, {"OEM_INTERNAL_API_URL": f"http://127.0.0.1:{server.server_port}"}):
                self.assertEqual(InternalApiSource().load("events", QUERY), EMPTY)
                Upstream.redirect = True
                with self.assertRaises(DashboardError): InternalApiSource().load("events", QUERY)
        finally:
            server.shutdown(); server.server_close()


@unittest.skipUnless(os.getenv("OEM_TEST_DSN"), "Requires isolated PostgreSQL; run integration.py")
class DatabaseTests(unittest.TestCase):
    def test_collection_original_history_and_immutability(self):
        import psycopg, hashlib, base64, uuid
        from collection import parse_collection_query, load_collection, validate_collection
        raw=b'  {"original": "raw bytes"}\n'
        ids=[str(uuid.uuid4()),str(uuid.uuid4())]
        with psycopg.connect(self.dsn,autocommit=True) as conn:
            for ident,age in zip(ids,("0 days","2 days")):
                conn.execute("INSERT INTO event_management.gateway_receipt(receipt_id,received_at,original_body,body_sha256) VALUES (%s,clock_timestamp()-%s::interval,%s,%s)",(ident,age,raw,hashlib.sha256(raw).hexdigest()))
                conn.execute("INSERT INTO event_management.gateway_receipt_status(receipt_id,status) VALUES (%s,'REJECTED')",(ident,))
            with self.assertRaises(psycopg.Error):conn.execute("UPDATE event_management.gateway_receipt SET original_body='changed' WHERE receipt_id=%s",(ids[0],))
            with self.assertRaises(psycopg.Error):conn.execute("DELETE FROM event_management.gateway_receipt WHERE receipt_id=%s",(ids[0],))
        with patch.dict(os.environ,{'OEM_POSTGRES_DSN':self.dsn}):
            for ident,scope in zip(ids,('today','history')):
                q=parse_collection_query({'scope':[scope],'receipt':[ident]})
                result=validate_collection(load_collection(q),q)
                self.assertEqual(base64.b64decode(result['original']['base64']),raw)
                self.assertEqual(result['total'],1)
                q['scope']='history' if scope=='today' else 'today'
                self.assertEqual(load_collection(q)['total'],0)
            q=parse_collection_query({'scope':['all']})
            self.assertIsNone(load_collection(q)['original'])

    @classmethod
    def setUpClass(cls):
        import psycopg
        cls.dsn = os.environ["OEM_TEST_DSN"]
        with psycopg.connect(cls.dsn, autocommit=True) as conn:
            for name in ("001-initialize-event-management.sql", "002-event-state.sql", "008-cacf-core.sql", "017-ess-lifecycle.sql", "018-oem-dashboard-views.sql", "018-oem-dashboard-views.sql", "022-delivery-filter-catalog.sql", "022-delivery-filter-catalog.sql", "025-gateway-data-collection.sql", "025-gateway-data-collection.sql"):
                sql = (ROOT / "infrastructure/postgres/init" / name).read_text()
                conn.execute("\n".join(line for line in sql.splitlines() if not line.startswith("\\")))
            seed = (ROOT / "scripts/dashboards/seed-delivery-demo.sql").read_text()
            seed = "\n".join(line for line in seed.splitlines() if not line.startswith("\\"))
            conn.execute(seed)
            conn.execute(seed)
            conn.execute("CREATE ROLE dashboard_test LOGIN; GRANT oem_dashboard_reader TO dashboard_test")
            conn.execute("""INSERT INTO event_management.event_state(event_key,event_id,tenant,lifecycle_status,servicenow_status,gnm_status,ticket_number,notification_id,effective_severity,tally)
                VALUES ('a','event-a','tenant-a','OPEN','CREATED','OPEN_CONFIRMED','INC1','GNM1',4,2),
                       ('b','event-b','tenant-b','CLOSED','NOT_REQUIRED','NOT_REQUIRED',NULL,NULL,0,1),
                       ('c','event-c','tenant-a','OPEN','FAILED','FAILED',NULL,NULL,3,1)""")
            conn.execute("""INSERT INTO event_management.automation_execution(execution_id,command_id,event_id,event_key,customer_code,request,requester_id,transaction_number,state,outcome,result_timeout_seconds)
                VALUES ('00000000-0000-0000-0000-000000000001','cmd1','event-a','a','tenant-a','{}','req1','tx1','COMPLETED','UNKNOWN',60)""")
        cls.reader_dsn = cls.dsn.replace("user=postgres", "user=dashboard_test")

    def test_views_filters_and_role(self):
        import psycopg
        with patch.dict(os.environ, {"OEM_POSTGRES_DSN": self.reader_dsn}):
            expected = {"events": 3, "ticketing": 2, "gnm": 2, "cacf": 1}
            for domain, total in expected.items():
                data = PostgresSource().load(domain, QUERY)
                self.assertEqual(data["total"], total)
                validate_snapshot(data, domain, QUERY)
            query = parse_query({"tenant": ["tenant-a"], "status": ["OPEN"], "limit": ["1"], "page": ["2"]})
            data = PostgresSource().load("events", query)
            self.assertEqual(data["total"], 2); self.assertEqual(len(data["rows"]), 1)
            for q in ("' OR 1=1 --", "%", "_"):
                self.assertEqual(PostgresSource().load("events", parse_query({"q": [q]}))["total"], 0)
            self.assertEqual(PostgresSource().load("cacf", QUERY)["rows"][0]["outcome"], "UNKNOWN")
        with psycopg.connect(self.reader_dsn, autocommit=True) as conn:
            for sql in ("SELECT * FROM event_management.event_state", "DELETE FROM dashboard_read.events"):
                with self.assertRaises(psycopg.errors.InsufficientPrivilege): conn.execute(sql)
            self.assertFalse(conn.execute("SELECT has_table_privilege(current_user, 'dashboard_read.events', 'INSERT')").fetchone()[0])

    def test_delivery_facets_distinct_counts_and_queries(self):
        from delivery import load_delivery, parse_delivery_query, validate_delivery
        with patch.dict(os.environ, {"OEM_POSTGRES_DSN": self.reader_dsn}):
            query = parse_delivery_query({})
            data = load_delivery(query)
            self.assertEqual(data["total"],12)
            validate_delivery(data,query)
            self.assertEqual(sum(b["count"] for b in data["facets"]["applids"]),12)
            self.assertEqual({b["value"]:b["count"] for b in data["facets"]["targets"]}, {"gnm":5,"snow":5,"cacf":3,"chatops":3,"extensions":3})
            for params,total in [({"target":["gnm"]},5), ({"target":["gnm"],"applid":["core"]},1), ({"applid":["__ANY__"]},1), ({"target":["gnm"],"severity":["0"]},1), ({"state":["2"]},2), ({"q":["' OR 1=1 --"]},0)]:
                q=parse_delivery_query(params)
                result=load_delivery(q)
                self.assertEqual(result["total"],total)
                validate_delivery(result,q)
            q=parse_delivery_query({"page":["2"],"limit":["5"]})
            self.assertEqual(len(load_delivery(q)["rows"]),5)
            for params in ({"target":["unknown"]},{"severity":["9"]},{"page":["0"]},{"applid":["a","b"]}):
                with self.assertRaises(DashboardError):parse_delivery_query(params)

    def test_delivery_rejects_invalid_configuration_and_preserves_read_only(self):
        import psycopg
        with psycopg.connect(self.dsn,autocommit=True) as conn:
            for criteria in ('{"password":{"operator":"eq","value":"secret"}}','{"Node":{"operator":"shell","value":"run"}}','{"Component":{"operator":"eq_ci","value":null}}'):
                with self.assertRaises(psycopg.errors.CheckViolation):
                    conn.execute("INSERT INTO event_management.delivery_filter(filter_id,name,customer_code,criteria) VALUES ('bad','bad','test',%s::jsonb)",(criteria,))
        with psycopg.connect(self.reader_dsn,autocommit=True) as conn:
            self.assertEqual(conn.execute("SELECT count(*) FROM dashboard_read.delivery").fetchone()[0],12)
            with self.assertRaises(psycopg.errors.InsufficientPrivilege):conn.execute("DELETE FROM event_management.delivery_filter")

    def test_internal_api_matches_postgresql(self):
        config = {"version": 1, "default": "postgresql", "sources": {}}
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            with patch.dict(os.environ, {"OEM_POSTGRES_DSN": self.reader_dsn, "OEM_INTERNAL_API_URL": f"http://127.0.0.1:{server.server_port}"}), patch("server.read_config", return_value=config):
                from delivery import parse_delivery_query
                for domain in ("events", "ticketing", "gnm", "cacf", "delivery"):
                    query = parse_delivery_query({"target":["gnm"]}) if domain == "delivery" else QUERY
                    direct = PostgresSource().load(domain, query)
                    api = InternalApiSource().load(domain, query)
                    direct.pop("observedAt"); api.pop("observedAt")
                    self.assertEqual(direct, api)
        finally:
            server.shutdown(); server.server_close()


if __name__ == "__main__": unittest.main()
