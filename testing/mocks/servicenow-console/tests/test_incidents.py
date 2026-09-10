import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
spec = importlib.util.spec_from_file_location('console_snow', Path(__file__).resolve().parents[1] / 'server.py')
mock = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mock)

class IncidentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        mock.DATABASE = str(Path(self.temp.name) / 'test.sqlite')
        mock.initialize()
    def tearDown(self): self.temp.cleanup()
    def test_seed_and_filters(self):
        self.assertEqual(len(mock.search('')), 12)
        self.assertEqual(len(mock.search('state=1')), 4)
        self.assertEqual(len(mock.search('state=2')), 3)
        self.assertEqual(mock.search('numberLIKEINC0019284')[0]['state'], '1')
        self.assertEqual(mock.search('numberLIKEINC0019284^state=7'), [])
        with self.assertRaises(ValueError): mock.search('state=1^ORstate=7')
    def test_close_search_and_persistence(self):
        payload = dict(state='7', close_code='Solved (Permanently)', close_notes='Closed through API test')
        closed = mock.close_incident('console-INC0019284', payload)
        self.assertEqual(closed['state'], '7')
        mock.initialize() # Simulate a container restart; must not reset data.
        self.assertEqual(mock.search('number=INC0019284')[0]['state'], '7')
        self.assertEqual(mock.close_incident('console-INC0019284', payload), closed)
        with mock.connect() as db: self.assertEqual(db.execute('SELECT count(*) FROM audit').fetchone()[0], 1)
    def test_invalid_close_does_not_mutate(self):
        with self.assertRaises(ValueError): mock.close_incident('console-INC0019284', dict(state='7', close_code='bad', close_notes='tiny'))
        self.assertEqual(mock.search('number=INC0019284')[0]['state'], '1')
        with self.assertRaises(LookupError): mock.close_incident('unknown', dict(state='7', close_code='Not Solved', close_notes='Long enough note'))
if __name__ == '__main__': unittest.main()
