import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('collector',ROOT/'services/product-observability/collector.py')
c=importlib.util.module_from_spec(spec);spec.loader.exec_module(c)
class CollectorTest(unittest.TestCase):
    def test_allowlist_and_source_timestamp(self):
        docs=c.documents('service',{'observedAt':'2026-09-10T00:00:00Z','services':[{'id':'a','status':'unknown','version':None,'password':'secret','environment':{'TOKEN':'secret'}}]},'later')
        self.assertEqual(docs,[{'id':'a','status':'unknown','version':None,'kind':'service','@timestamp':'2026-09-10T00:00:00Z'}])
    def test_source_failure_does_not_fabricate_healthy_apis(self):
        with patch.object(c,'request') as req,patch.object(c.Path,'write_text'):
            req.side_effect=[OSError(),{'observedAt':'2026-09-10T00:00:00Z','apis':[{'id':'a','status':'DOWN','httpStatus':503}]},{'errors':False}]
            c.collect()
            lines=req.call_args.args[2].splitlines()
            docs=[json.loads(x) for x in lines[1::2]]
            self.assertEqual(docs[0]['status'],'UNREACHABLE')
            self.assertEqual(docs[1]['status'],'DOWN')
            self.assertFalse(any(d['kind']=='service' for d in docs))
    def test_bulk_error_not_marked_successful(self):
        with patch.object(c,'request') as req,patch.object(c.Path,'write_text') as write:
            req.side_effect=[{'observedAt':'x','services':[]},{'observedAt':'x','apis':[]},{'errors':True}]
            with self.assertRaises(RuntimeError):c.collect()
            write.assert_not_called()
    def test_saved_object_references_resolve(self):
        objs=[json.loads(x) for x in (ROOT/'infrastructure/opensearch/product-observability.ndjson').read_text().splitlines()]
        keys={(x['type'],x['id']) for x in objs}
        for obj in objs:
            for r in obj['references']:self.assertIn((r['type'],r['id']),keys)
if __name__=='__main__':unittest.main()
