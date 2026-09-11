"""Contract integration test: real HTTP sessions, persistence, pagination, solution and closure."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer

ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/"services/glpi-ticketing-api"))
from client import GlpiClient

def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

class GlpiContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory();os.environ["GLPI_MOCK_DB"]=str(Path(cls.temp.name)/"tickets.sqlite")
        cls.mock=module("glpi_mock",ROOT/"testing/mocks/glpi/server.py")
        cls.server=ThreadingHTTPServer(("127.0.0.1",0),cls.mock.Handler)
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
        os.environ["GLPI_BASE_URL"]=f"http://127.0.0.1:{cls.server.server_port}/apirest.php"
    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown();cls.server.server_close();cls.temp.cleanup()
    def test_create_list_solution_close_and_session_cleanup(self):
        with GlpiClient() as c:
            created=c.call("POST","/Ticket",{"input":{"name":"GLPI contract test","content":"Native ticket","entities_id":4}})
            id=created["id"]
            ticket=c.call("GET",f"/Ticket/{id}")
            self.assertEqual(ticket["entities_id"],4)
            self.assertTrue(any(t["id"]==id for t in c.tickets()))
            closed=c.close(id,"Monitoring recovered successfully",0)
            self.assertEqual(closed["status"],6)
            c.close(id,"Do not duplicate the solution",0)
            with self.mock.connect() as db:
                self.assertEqual(db.execute("SELECT count(*) FROM notes WHERE ticket_id=?",(id,)).fetchone()[0],1)
        self.assertFalse(self.mock.SESSIONS)
    def test_paginated_inventory(self):
        with GlpiClient() as c:
            for n in range(101):c.call("POST","/Ticket",{"input":{"name":f"Pagination {n}"}})
            self.assertGreaterEqual(len(c.tickets()),101)
    def test_wrong_credentials_rejected(self):
        c=GlpiClient();c.user="invalid"
        with self.assertRaises(Exception):c.__enter__()

if __name__=="__main__":unittest.main()
