import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
const require = createRequire(new URL('../../../services/oem-dashboards/package.json', import.meta.url));
const ts = require('typescript');
const source = readFileSync(new URL('../../../services/oem-dashboards/src/data.ts', import.meta.url), 'utf8');
const {outputText} = ts.transpileModule(source, {compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}});
const {parseSnapshot,getSnapshot} = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
const query = {tenant:'',status:'',q:'',page:1,limit:25};
const empty = {schemaVersion:'1.0',domain:'events',source:'postgresql',observedAt:'2026-09-10T00:00:00Z',lastUpdatedAt:null,total:0,page:1,limit:25,counts:{},rows:[]};
test('empty is valid; errors and malformed results are not empty datasets',()=>{
  assert.equal(parseSnapshot(empty,'events',query).total,0);
  for (const change of [{total:1},{counts:{OPEN:1}},{rows:[{}]},{domain:'gnm'},{page:2},{source:'mock'},{observedAt:'not-a-date'}]) assert.throws(()=>parseSnapshot({...empty,...change},'events',query));
});
test('rejects duplicate identities, wrong tenants and invalid severity',()=>{
  const row = {id:'a',tenant:'A',status:'OPEN',eventId:'e',reference:null,updatedAt:empty.observedAt,severity:4,tally:1,outcome:null};
  const data = {...empty,total:1,counts:{OPEN:1},rows:[row]};
  assert.equal(parseSnapshot(data,'events',query).rows.length,1);
  assert.throws(()=>parseSnapshot(data,'events',{...query,tenant:'B'}));
  assert.throws(()=>parseSnapshot({...data,total:2,counts:{OPEN:2},rows:[row,row]},'events',query));
  assert.throws(()=>parseSnapshot({...data,rows:[{...row,severity:8}]},'events',query));
});
test('HTTP failure and SPA responses never fall back to mock',async t=>{
  for (const response of [new Response('offline',{status:503}),new Response('<html/>',{headers:{'content-type':'text/html'}}),Response.json({bad:true})]) {
    t.mock.method(globalThis,'fetch',async()=>response);
    await assert.rejects(()=>getSnapshot('events',query,new AbortController().signal));
    t.mock.restoreAll();
  }
  t.mock.method(globalThis,'fetch',async()=>Response.json(empty));
  assert.equal((await getSnapshot('events',query,new AbortController().signal)).total,0);
});
