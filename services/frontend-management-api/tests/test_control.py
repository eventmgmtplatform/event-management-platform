import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, Mock
import control

class ControlTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        control.DATABASE = str(Path(self.directory.name) / 'operations.sqlite')
        control.initialize()
        self.payload = dict(id='11111111-1111-4111-8111-111111111111', service='servicenow-console-mock', action='restart')
    def tearDown(self): self.directory.cleanup()
    def test_allowlist(self):
        for service in ('all','frontend-management-api','enrichment-engine','kafka-init','x;id'):
            with self.assertRaises(ValueError): control.submit(dict(self.payload, service=service), dispatch=False)
        with self.assertRaises(ValueError): control.submit(dict(self.payload, action='down'), dispatch=False)
    def test_idempotence_and_serialization(self):
        first = control.submit(self.payload, dispatch=False)
        self.assertEqual(control.submit(self.payload, dispatch=False), first)
        with self.assertRaises(RuntimeError): control.submit(dict(self.payload, id='22222222-2222-4222-8222-222222222222'), dispatch=False)
        with patch.object(control, 'execute', return_value=0): control.run(first['id'],first['service'],first['action'])
        self.assertEqual(control.get_operation(first['id'])['status'],'succeeded')
    def test_failure_and_restart_never_replay(self):
        op = control.submit(self.payload, dispatch=False)
        control.initialize()
        self.assertEqual(control.get_operation(op['id'])['status'],'interrupted')
    def test_calls_existing_script_with_local_scope(self):
        process=Mock();process.wait.return_value=0
        with patch.object(control.subprocess, 'Popen', return_value=process) as popen:
            self.assertEqual(control.execute('servicenow-console-mock','stop'),0)
            args,kwargs=popen.call_args
            self.assertEqual(args[0], ['bash',control.SCRIPT,'servicenow-console-mock','stop'])
            self.assertEqual(kwargs['env']['EVENTMANAGEMENT_RUNTIME'],'local')
            self.assertNotIn('shell',kwargs)

    def test_nonzero_exit_is_not_success(self):
        op = control.submit(self.payload, dispatch=False)
        with patch.object(control, 'execute', return_value=2):
            control.run(op['id'], op['service'], op['action'])
        result = control.get_operation(op['id'])
        self.assertEqual(result['status'], 'failed')
        self.assertEqual(result['exitCode'], 2)
