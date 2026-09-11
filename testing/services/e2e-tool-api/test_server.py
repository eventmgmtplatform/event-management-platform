import importlib.util
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('e2e_tool_server', ROOT / 'services/e2e-tool-api/server.py')
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)


class ToolTests(unittest.TestCase):
    def test_blackout_requires_customer_server_and_exact_sixty_minutes(self):
        for customer, server in [('bad:customer', 'srv-01'), ('CUST-01', 'bad server')]:
            with self.assertRaises(ValueError):
                api.register_blackout(customer, server, 60)
        with self.assertRaises(ValueError):
            api.register_blackout('CUST-01', 'srv-01', 30)

    def test_only_verified_uc001_can_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / 'evidences/testing/run/happy-path/report.json'
            report.parent.mkdir(parents=True)
            with patch.object(api, 'ROOT', root):
                for case, status, code, expected in [('UC-001', 'PASS', 0, 'PASS'),
                        ('UC-003', 'PASS', 0, 'FAIL'), ('UC-001', 'FAIL', 0, 'FAIL'),
                        ('UC-001', 'PASS', 1, 'FAIL')]:
                    report.write_text(json.dumps({'caseId': case, 'status': status}))
                    self.assertEqual(api.result_from_log(f'PASS: {report}\n', code)['status'], expected)
                self.assertEqual(api.result_from_log('PASS', 0)['status'], 'FAIL')
                with self.assertRaises(ValueError):
                    api.result_from_log('PASS: /tmp/report.json', 0)

    def test_concurrent_requests_share_run_and_restart_blocks(self):
        with tempfile.TemporaryDirectory() as directory:
            runs = api.Runs(Path(directory))
            with patch.object(threading.Thread, 'start'):
                first = runs.start()
                self.assertEqual(runs.start()['runId'], first['runId'])
                self.assertEqual(runs.wait(first['runId'], seconds=0)['status'], 'RUNNING')
            runs.lease.close()
            restored = api.Runs(Path(directory))
            try:
                self.assertEqual(restored.get(first['runId'])['status'], 'INTERRUPTED')
                self.assertEqual(restored.wait(first['runId'])['status'], 'INTERRUPTED')
                self.assertEqual(restored.start()['status'], 'BLOCKED')
                with self.assertRaises(FileNotFoundError):
                    restored.get('../../secret')
            finally:
                restored.lease.close()

    def test_http_auth_and_command_rejection(self):
        with tempfile.TemporaryDirectory() as directory:
            server = api.ThreadingHTTPServer(('127.0.0.1', 0), api.Handler)
            server.token = 't' * 32
            server.runs = api.Runs(Path(directory))
            thread = threading.Thread(target=server.serve_forever)
            thread.start()
            base = f'http://127.0.0.1:{server.server_port}'
            try:
                with self.assertRaises(urllib.error.HTTPError) as error:
                    urllib.request.urlopen(base + '/openapi.json')
                self.assertEqual(error.exception.code, 401)
                headers = {'Authorization': 'Bearer ' + server.token}
                request = urllib.request.Request(base + '/openapi.json', headers=headers)
                with urllib.request.urlopen(request) as response:
                    self.assertEqual(json.load(response)['openapi'], '3.0.3')
                request = urllib.request.Request(base + '/runs', data=b'{"cmd":"id"}', headers=headers)
                with self.assertRaises(urllib.error.HTTPError) as error:
                    urllib.request.urlopen(request)
                self.assertEqual(error.exception.code, 400)
                self.assertIsNone(server.runs.active)
            finally:
                server.shutdown()
                thread.join()
                server.server_close()
                server.runs.lease.close()


if __name__ == '__main__':
    unittest.main()
