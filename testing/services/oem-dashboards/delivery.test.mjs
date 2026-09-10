import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import vm from 'node:vm';
const require=createRequire(new URL('../../../services/oem-dashboards/package.json',import.meta.url));
const ts=require('typescript');
function load(name) {
  const source=readFileSync(new URL('../../../services/oem-dashboards/src/'+name,import.meta.url),'utf8');
  const {outputText}=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}});
  const module={exports:{}};
  vm.runInNewContext(outputText,{module,exports:module.exports,require,localStorage:{getItem:()=> 'en'},console,Intl});
  return module.exports;
}
const {parseDelivery}=load('delivery-data.ts');
const q={target:'',applid:'',customer:'',state:'',severity:'',q:'',page:1,limit:25};
const empty={schemaVersion:'1.0',domain:'delivery',source:'postgresql',observedAt:'2026-09-10T00:00:00Z',lastUpdatedAt:null,total:0,page:1,limit:25,facets:{targets:[],applids:[],severities:[],states:[]},rows:[]};
test('Delivery validates totals, facet accounting and empty responses',()=>{
  assert.equal(parseDelivery(empty,q).total,0);
  for(const patch of [{total:1},{page:2},{facets:{...empty.facets,states:[{value:'0',count:1}]}},{source:'mock'},{rows:[{}]}]) assert.throws(()=>parseDelivery({...empty,...patch},q));
});
test('English selection translates operational copy, all domain descriptions and navigation labels',()=>{
  const {english,I18nProvider,LanguageSelector,useI18n}=load('i18n.tsx');
  const {domains}=load('data.ts');
  for(const info of Object.values(domains)) for(const key of [info.unit,info.description,...info.checklist]) assert.ok(english[key],key);
  const React=require('react');const {renderToStaticMarkup}=require('react-dom/server');
  function Probe(){const {t,locale}=useI18n();return React.createElement('p',null,`${t('Configuraciones de entrega')} · ${t('Filtros registrados')} · ${locale}`);}
  const html=renderToStaticMarkup(React.createElement(I18nProvider,null,React.createElement(Probe),React.createElement(LanguageSelector)));
  assert.match(html,/Delivery configuration/);assert.match(html,/Registered filters/);assert.match(html,/en-US/);assert.match(html,/Change language/);assert.match(html,/value="en" selected/);
});
