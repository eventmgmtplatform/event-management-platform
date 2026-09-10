import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import unittest
from unittest.mock import patch,Mock
import apis
class ApiTests(unittest.TestCase):
    def test_reboot_resolves_allowlisted_service(self):
        with patch('apis.control.submit',return_value={'status':'running'}) as submit:
            status,_=apis.action({'id':'abc','apiId':'gnm','action':'reboot'})
            self.assertEqual(status,202)
            submit.assert_called_once_with({'id':'abc','service':'gnm-mock','action':'restart'})
    def test_reject_urls_and_self_stop(self):
        for payload in ({'id':'a','apiId':'http://evil','action':'testing'},{'id':'a','apiId':'management','action':'stop'},{'id':'a','apiId':'gnm','action':'rm'}):
            with self.assertRaises(ValueError):apis.action(payload)
    def test_testing_does_not_control(self):
        with patch('apis.probe',return_value={'status':'UP'}) as probe,patch('apis.control.submit') as submit:
            self.assertEqual(apis.action({'id':'a','apiId':'gnm','action':'testing'}),(200,{'status':'UP'}));submit.assert_not_called()
    def test_unreachable_is_not_healthy(self):
        with patch('urllib.request.OpenerDirector.open',side_effect=OSError()):
            result=apis.probe(apis.REGISTRY[0]);self.assertEqual(result['status'],'UNREACHABLE');self.assertIsNone(result['httpStatus'])
