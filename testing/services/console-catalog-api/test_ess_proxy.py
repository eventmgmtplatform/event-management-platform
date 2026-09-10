import importlib.util,json,os,tempfile,unittest
from pathlib import Path
from unittest.mock import patch,MagicMock
ROOT=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('ess_proxy',ROOT/'services/console-catalog-api/ess_proxy.py')
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
class EssProxyTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
  path=Path(self.tmp.name)/'tokens.json';path.write_text(json.dumps({'tenants':{'a':'test-secret-a','b':'test-secret-b','not-allowed':'secret-hidden'},'operatorToken':'test-operator'}))
  self.env=patch.dict(os.environ,{'ESS_ADMIN_CREDENTIALS_FILE':str(path),'ESS_ALLOWED_TENANTS':'a,b','ESS_OPERATOR_QUARANTINE':'true'});self.env.start();self.addCleanup(self.env.stop)
 def call(self,path,method='GET',headers=None):return p.handle(method,'/api/ess/'+path,headers or {})
 def fails(self,status,fn):
  with self.assertRaises(p.Failure) as c:fn()
  self.assertEqual(c.exception.status,status)
  self.assertNotIn('secret',c.exception.code)
 def test_config_has_only_allowed_tenants_and_capability(self):
  self.assertEqual(self.call('config'),{'tenants':['a','b'],'operatorQuarantine':True,'authorizationScope':'local-console'})
 def test_scope_is_chosen_server_side(self):
  with patch.object(p,'upstream',return_value={'items':[],'nextCursor':''}) as upstream:
   self.call('events?tenant=b&limit=1')
   upstream.assert_called_once_with('events',{'limit':1,'after':''},'b','test-secret-b')
   self.fails(403,lambda:self.call('events?tenant=not-allowed'))
   self.assertEqual(upstream.call_count,1)
 def test_browser_credentials_are_rejected(self):
  for name in ['X-ESS-Admin-Token','X-Tenant-Id','Authorization']:
   self.fails(401,lambda:self.call('events?tenant=a',headers={name:'caller'}))
 def test_only_fixed_get_routes_and_parameters(self):
  for method in ['POST','PUT','PATCH','DELETE','OPTIONS','HEAD']:self.fails(405,lambda:self.call('events?tenant=a',method))
  for route in ['replay','../events','operator/quarantine/rebuild']:self.fails(404,lambda:self.call(route))
  for suffix in ['&url=http://evil.invalid','&tenant=b','&limit=0','&limit=101','&limit=1.5','&after='+('x'*129)]:self.fails(400,lambda:self.call('events?tenant=a'+suffix))
  for suffix in ['&afterVersion=-1','&afterVersion=9223372036854775808','&eventKey=']:self.fails(400,lambda:self.call('history?tenant=a&eventKey=k'+suffix))
 def test_upstream_cross_tenant_leak_fails_closed(self):
  with patch.object(p,'upstream',return_value={'items':[{'event_key':'x','tenant':'b'}],'nextCursor':''}):self.fails(503,lambda:self.call('events?tenant=a'))
 def test_payload_projection_and_pagination(self):
  row={f:None for f in p.STATE_FIELDS};row.update(event_key='correlation:a&x/+?',tenant='a',payload='NEVER_RETURN',token='test-secret-a')
  with patch.object(p,'upstream',return_value={'items':[row],'nextCursor':row['event_key']}) as upstream:
   result=self.call('events?tenant=a&limit=1')
   self.assertEqual(result['nextCursor'],row['event_key']);self.assertNotIn('payload',result['items'][0]);self.assertNotIn('secret',json.dumps(result))
   upstream.return_value={'items':[],'nextCursor':''}
   self.call('events?tenant=a&after=x%26y%2F%2B%3F')
   self.assertEqual(upstream.call_args.args[1]['after'],'x&y/+?')
 def test_history_pagination_and_event_identity(self):
  row={f:None for f in p.HISTORY_FIELDS};row.update(event_key='x',aggregate_version=2)
  with patch.object(p,'upstream',return_value={'items':[row],'nextVersion':2}):self.assertEqual(self.call('history?tenant=a&eventKey=x&afterVersion=1')['nextVersion'],2)
  with patch.object(p,'upstream',return_value={'items':[dict(row,event_key='other')],'nextVersion':2}):self.fails(503,lambda:self.call('history?tenant=a&eventKey=x'))
 def test_operator_is_separate_and_summary_is_sanitized(self):
  with patch.object(p,'upstream',return_value={'scope':'operator-global','items':[{'reason':'INVALID','count':1,'last_seen_at':None,'payload':'secret'}]}) as upstream:
   result=self.call('operator/quarantine');upstream.assert_called_once_with('quarantine',{},None,'test-operator');self.assertEqual(set(result['items'][0]),{'reason','count','last_seen_at'})
  with patch.dict(os.environ,ESS_OPERATOR_QUARANTINE='false'):
   self.assertFalse(self.call('config')['operatorQuarantine']);self.fails(403,lambda:self.call('operator/quarantine'))
  self.fails(400,lambda:self.call('operator/quarantine?tenant=a'))
 def test_missing_configuration_is_503(self):
  with patch.dict(os.environ,ESS_ADMIN_CREDENTIALS_FILE='/not-present'):self.fails(503,lambda:self.call('config'))
 def test_transport_encodes_query_and_never_follows_redirects(self):
  conn=MagicMock();response=conn.getresponse.return_value;response.status=200;response.read.return_value=b'{}'
  with patch.object(p.http.client,'HTTPConnection',return_value=conn) as cls:
   p.upstream('event',{'eventKey':'a&/?+'},'a','test-secret-a')
   cls.assert_called_once_with('event-state-service',8084,timeout=6)
   self.assertEqual(conn.request.call_args.args,('GET','/api/v1/state/event?eventKey=a%26%2F%3F%2B'))
   response.status=302;response.read.return_value=b'{"errorCode":"test-secret-a"}'
   self.fails(503,lambda:p.upstream('event',{},'a','test-secret-a'))
 def test_known_errors_propagate_without_upstream_content(self):
  for status,code in [(400,'INVALID_LIMIT'),(401,'UNAUTHORIZED'),(404,'EVENT_NOT_FOUND'),(503,'STATE_STORE_UNAVAILABLE')]:
   conn=MagicMock();response=conn.getresponse.return_value;response.status=status;response.read.return_value=json.dumps({'errorCode':code,'detail':'test-secret-a'}).encode()
   with patch.object(p.http.client,'HTTPConnection',return_value=conn):self.fails(status,lambda:p.upstream('events',{},'a','test-secret-a'))
if __name__=='__main__':unittest.main()
