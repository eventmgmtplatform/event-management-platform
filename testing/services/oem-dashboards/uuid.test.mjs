import {test} from 'node:test';
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
const require=createRequire(new URL('../../../services/oem-dashboards/package.json',import.meta.url));
const ts=require('typescript');
for(const file of ['oem-dashboards/src/uuid.ts','event-management-console/src/shared/utils/uuid.ts']){
 const source=readFileSync(new URL('../../../services/'+file,import.meta.url),'utf8');
 const {outputText}=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}});
 const {createUuid}=await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
 const pattern=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
 test(file+': native UUID and HTTP host without randomUUID',()=>{
  assert.match(createUuid(webcrypto),pattern);
  const httpCrypto={getRandomValues:webcrypto.getRandomValues.bind(webcrypto)};
  const values=Array.from({length:1000},()=>createUuid(httpCrypto));
  assert.equal(new Set(values).size,1000);
  values.forEach(value=>assert.match(value,pattern));
 });
 test(file+': no weak randomness if Web Crypto is unavailable',()=>{
  assert.throws(()=>createUuid({}),/identificadores seguros/);
 });
}
