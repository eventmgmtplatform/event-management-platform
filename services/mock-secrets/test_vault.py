import os,tempfile,unittest,threading,json,urllib.request,urllib.error
import server
class VaultTest(unittest.TestCase):
 def test_write_only_persistence_and_revision(self):
  with tempfile.TemporaryDirectory() as tmp:
   server.DB=tmp+'/vault.db';server.KEY=server.Path(tmp+'/key');server.initialize()
   http=server.ThreadingHTTPServer(('127.0.0.1',0),server.Handler);thread=threading.Thread(target=http.serve_forever,daemon=True);thread.start()
   def call(path,body=None,method=None,revision=None):
    headers={'Content-Type':'application/json','X-Console-Action':'mock-secrets'}
    if revision:headers['If-Match']=revision
    req=urllib.request.Request('http://127.0.0.1:'+str(http.server_port)+path,data=json.dumps(body).encode() if body else None,method=method,headers=headers)
    try:
     with urllib.request.urlopen(req) as r:return r.status,json.load(r)
    except urllib.error.HTTPError as e:return e.code,json.load(e)
   try:
    body=dict(tenant='test',environment='local',name='sample',kind='password',value='synthetic-only')
    status,item=call('/api/mock-secrets',body);self.assertEqual(status,201);self.assertNotIn('value',item)
    self.assertEqual(call('/api/mock-secrets',body)[0],409)
    server.initialize();status,listed=call('/api/mock-secrets?tenant=test');self.assertEqual(listed['items'][0]['id'],item['id']);self.assertNotIn('synthetic-only',json.dumps(listed))
    with server.connect() as c:row=c.execute('SELECT value FROM secrets').fetchone();self.assertNotIn(b'synthetic-only',row['value']);self.assertEqual(server.Fernet(server.KEY.read_bytes()).decrypt(row['value']),b'synthetic-only')
    self.assertEqual(call('/api/mock-secrets/'+item['id'],body,'PUT','"1"')[0],200)
    self.assertEqual(call('/api/mock-secrets/'+item['id'],body,'PUT','"1"')[0],409)
    self.assertEqual(call('/api/mock-secrets?tenant=other')[1]['items'],[])
   finally:http.shutdown();http.server_close()
if __name__=='__main__':unittest.main()
