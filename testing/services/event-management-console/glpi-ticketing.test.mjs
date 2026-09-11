import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
const require=createRequire(new URL('../../../services/event-management-console/package.json',import.meta.url));
const ts=require('typescript');
const source=readFileSync(new URL('../../../services/event-management-console/src/modules/ticketing/services/glpi-ticketing.adapter.ts',import.meta.url),'utf8');
const {outputText}=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}});
const {parseGlpiTicket,glpiTicketingRepository:repo}=await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
const ticket={id:42,name:'CPU high',content:'Native description',status:1,priority:5,entities_id:2,itilcategories_id:3,date_mod:'2026-09-10T10:00:00Z'};
test('GLPI native states differ from ServiceNow and IDs remain provider scoped',()=>{
 assert.equal(parseGlpiTicket(ticket).id,'GLPI-42');
 assert.equal(parseGlpiTicket({...ticket,status:6}).status,'Closed');
 assert.equal(parseGlpiTicket({...ticket,status:5}).status,'Resolved');
 assert.equal(parseGlpiTicket({...ticket,status:3}).status,'In Progress');
 assert.equal(parseGlpiTicket(ticket).provider,'GLPI');
 assert.throws(()=>parseGlpiTicket({...ticket,id:0}));
 assert.throws(()=>parseGlpiTicket({...ticket,status:7}));
});
test('GLPI queries use the dedicated server boundary and filter by native display ID',async t=>{
 t.mock.method(globalThis,'fetch',async url=>{assert.equal(url,'/api/glpi/tickets');return Response.json({tickets:[ticket]});});
 assert.equal((await repo.search({number:'GLPI-42',status:'Open'},new AbortController().signal)).length,1);
 assert.equal((await repo.search({number:'INC42',status:'Open'},new AbortController().signal)).length,0);
});
test('GLPI closure cannot be routed to ServiceNow and verifies returned identity',async t=>{
 globalThis.window=globalThis;
 t.mock.method(globalThis,'fetch',async(url,init)=>{
  assert.equal(url,'/api/glpi/tickets/42/close');assert.equal(init.method,'POST');
  assert.deepEqual(JSON.parse(init.body),{note:'Monitoring recovered',solutionTypeId:0});
  return Response.json({ticket:{...ticket,status:6}});
 });
 assert.equal((await repo.close(parseGlpiTicket(ticket),'0','Monitoring recovered')).status,'Closed');
});
