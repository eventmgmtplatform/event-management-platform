import {useEffect,useRef,useState} from 'react';
import {useI18n} from '../../shared/i18n/I18nProvider';
import {api,ApiError,errorText,operation,type Operation} from '../blackouts/blackouts.api';
import {History} from '../blackouts/BlackoutsPage';
import {ProductCatalogPage} from '../product/ProductCatalogPage';
import {InventorySimulation} from './InventorySimulation';
import {emptyRule,factTypes,selectors,supported,validateDefinition,type CatalogRow,type Definition,type Kind,type Registry} from './inventory.model';
import '../catalog/catalog.css';import '../blackouts/blackouts.css';import './inventory.css';
const pendingKey='console.inventory.pending';
type Pending=Operation&{kind:Kind};
function readPending():Pending|null {try{return JSON.parse(sessionStorage.getItem(pendingKey)||'null');}catch{return null;}}
function savePending(value:Pending|null){if(value)sessionStorage.setItem(pendingKey,JSON.stringify(value));else sessionStorage.removeItem(pendingKey);}
export function InventoryError({error}:{error:unknown}) {const {t}=useI18n();return error?<div role="alert" className="catalog-error">{t(errorText(error))}{error instanceof ApiError&&<small>HTTP {error.status} · {error.code}</small>}</div>:null;}
export function InventoryPage(){
 const {t}=useI18n();
 const [pending,setPending]=useState<Pending|null>(readPending);
 const [tenant,setTenant]=useState(()=>readPending()?.tenant??''),[input,setInput]=useState(()=>readPending()?.tenant??'');
 const [kind,setKind]=useState<Kind|'legacy'>(()=>readPending()?.kind??'INVENTORY');
 const [rows,setRows]=useState<CatalogRow[]>([]),[query,setQuery]=useState(''),[loading,setLoading]=useState(true),[truncated,setTruncated]=useState(false),[catalogError,setCatalogError]=useState<unknown>();
 const [error,setError]=useState<unknown>(),[notice,setNotice]=useState(''),[revision,setRevision]=useState(0),[editorKey,setEditorKey]=useState(0),[selected,setSelected]=useState<string|null>(()=>readPending()?.ruleId??null),[draft,setDraft]=useState<Definition|null>(null),[conflict,setConflict]=useState(false),[busy,setBusy]=useState(false);
 const lock=useRef(false);const disabled=busy||!!pending;
 useEffect(()=>{
  const controller=new AbortController();setLoading(true);setCatalogError(undefined);
  Promise.all(['inventory-records','enrichment-plans'].map(async path=>{
   const response=await fetch('/api/catalog/views/'+path,{signal:controller.signal,cache:'no-store'});
   if(!response.ok)throw new ApiError(response.status,'CATALOG_UNAVAILABLE');const data=await response.json();
   if(!Array.isArray(data.items))throw new ApiError(503,'INVALID_RESPONSE');return data as {items:CatalogRow[];truncated:boolean};
  })).then(data=>{if(!controller.signal.aborted){setRows(data.flatMap(d=>d.items));setTruncated(data.some(d=>d.truncated));}}).catch(e=>{if(!controller.signal.aborted){setCatalogError(e);setRows([]);}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>controller.abort();
 },[revision]);
 async function execute(op:Pending){
  if(lock.current)return;lock.current=true;setBusy(true);setError(undefined);setNotice('');
  try{savePending(op);setPending(op);await api(op.tenant,op.path,JSON.parse(op.body),op);savePending(null);setPending(null);setNotice(op.path==='/rules'?'Guardado, pendiente de activación. La versión activa anterior se conserva.':'Cambio confirmado por el servidor.');setSelected(op.ruleId);setKind(op.kind);setEditorKey(x=>x+1);setRevision(x=>x+1);setConflict(false);}
  catch(e){setError(e);if(op.path.endsWith('/enable'))setNotice('Versión guardada; activación pendiente de confirmar.');if(e instanceof ApiError&&[400,404,409,413,422,428].includes(e.status)){savePending(null);setPending(null);if(e.status===409)setConflict(true);}}
  finally{lock.current=false;setBusy(false);}
 }
 function switchKind(value:Kind|'legacy'){setKind(value);setSelected(null);setDraft(null);setConflict(false);setError(undefined);setNotice('');}
 const filtered=rows.filter(r=>r.configuration.type===kind&&(!tenant||r.tenant===tenant)&&JSON.stringify(r).toLowerCase().includes(query.toLowerCase()));
 return <section className="page catalog-page blackout-page inventory-page">
  <div className="page-heading"><div><span className="eyebrow">Event Processor</span><h1>Inventory Services</h1><p>{t('Inventario ejecutable y planes de enrichment, con versiones independientes.')}</p></div><button disabled={disabled||loading} onClick={()=>setRevision(x=>x+1)}>{t('Actualizar tabla')}</button></div>
  <p className="inventory-note">{t('Un registro INVENTORY no enriquece eventos sin un plan ENRICHMENT aplicable. Guardar nunca activa.')}</p>
  <nav className="inventory-tabs" aria-label={t('Fuentes de inventario')}>
   {([['INVENTORY','Inventario del Processor'],['ENRICHMENT','Planes de enrichment'],['legacy','Catálogo anterior · Solo consulta']] as const).map(([value,label])=><button key={value} aria-pressed={kind===value} disabled={disabled} onClick={()=>switchKind(value)}>{t(label)}</button>)}
  </nav>
  <InventoryError error={error}/>{notice&&<p role="status" className="blackout-notice">{t(notice)}</p>}
  {pending&&<div role="status" className="blackout-notice"><p>{t('Operación pendiente de confirmar. Reintenta exactamente la misma solicitud antes de realizar otro cambio.')}</p><code>{pending.key}</code><button disabled={busy} onClick={()=>execute(pending)}>{t('Reintentar misma operación')}</button></div>}
  {kind==='legacy'?<><p className="inventory-note">{t('Catálogo PostgreSQL anterior: no se sincroniza ni se activa en el Processor desde esta pantalla.')}</p><ProductCatalogPage kind="inventory-services"/></>:<>
   <div className="catalog-toolbar"><label>Tenant<input aria-label="Tenant" list="inventory-tenants" value={input} disabled={disabled} onChange={e=>setInput(e.target.value)} placeholder={t('Todos los tenants')}/></label><datalist id="inventory-tenants">{[...new Set(rows.map(r=>r.tenant))].map(x=><option key={x}>{x}</option>)}</datalist>
    <button disabled={disabled} onClick={()=>{setTenant(input.trim());setSelected(null);setDraft(null);setConflict(false);setError(undefined);setNotice('');}}>{t('Seleccionar tenant')}</button>
    <input aria-label={t('Buscar en la tabla')} placeholder={t('Buscar en la tabla')} value={query} onChange={e=>setQuery(e.target.value)}/>
    <button disabled={!tenant||disabled} onClick={()=>{setSelected('');setEditorKey(x=>x+1);setConflict(false);setNotice('');}}>{t(kind==='INVENTORY'?'Nuevo registro INVENTORY':'Nuevo plan ENRICHMENT')}</button>
   </div>
   <p>{tenant?`${t('Tenant seleccionado')}: ${tenant}`:t('Todos los tenants: selecciona uno para escribir o simular.')}</p>
   <InventoryError error={catalogError}/>{truncated&&<p role="status">{t('Se muestran los primeros 2000 registros por fuente.')}</p>}
   <div className="catalog-table-wrap" aria-busy={loading}><table><thead><tr>{['ID','Tenant','Estado','Versión activa','Última guardada','Detalle'].map(label=><th key={label}>{t(label)}</th>)}</tr></thead><tbody>{filtered.map(row=><tr key={row.tenant+':'+row.id}><td><strong>{row.id}</strong><small>Event Processor · {row.configuration.type}</small></td><td>{row.tenant}</td><td>{t(row.status==='RETIRED'?'Retirado':row.status==='ENABLED'?'Activo':'Desactivado')}</td><td>{row.active_version??'—'}</td><td>{row.latest_version}</td><td><button disabled={!tenant||disabled} aria-label={`${t('Abrir registro')} ${row.id}`} onClick={()=>{setSelected(row.id);setEditorKey(x=>x+1);setConflict(false);setNotice('');}}>{t('Abrir')}</button></td></tr>)}</tbody></table>{loading?<p role="status">{t('Cargando…')}</p>:!filtered.length&&<p>{t('Sin registros')}</p>}</div>
   {tenant&&selected!==null&&<RuleEditor key={tenant+':'+kind+':'+selected+':'+editorKey} tenant={tenant} kind={kind} id={selected} locked={disabled} conflict={conflict} onDraft={setDraft} execute={execute} reconcile={()=>{setConflict(false);setEditorKey(x=>x+1);setRevision(x=>x+1);}}/>}
   {tenant&&<InventorySimulation key={tenant} tenant={tenant} rows={rows.filter(r=>r.tenant===tenant)} draft={draft} registryRevision={revision}/>}
  </>}
 </section>;
}
function RuleEditor({tenant,kind,id,locked,conflict,onDraft,execute,reconcile}:{tenant:string;kind:Kind;id:string;locked:boolean;conflict:boolean;onDraft:(rule:Definition|null)=>void;execute:(op:Pending)=>Promise<void>;reconcile:()=>void}){
 const {t}=useI18n();const [rule,setRule]=useState<Definition>(()=>emptyRule(kind,tenant)),[record,setRecord]=useState<Registry>(),[etag,setEtag]=useState('"0"'),[reason,setReason]=useState(''),[loading,setLoading]=useState(!!id),[error,setError]=useState<unknown>(),[validating,setValidating]=useState(false),[valid,setValid]=useState(false),[confirmation,setConfirmation]=useState<Pending|null>(null),[active,setActive]=useState<Definition>();
 useEffect(()=>{if(!id)return;const abort=new AbortController();api<Registry>(tenant,'/rules/'+encodeURIComponent(id),undefined,undefined,abort.signal).then(({data,etag})=>{if(abort.signal.aborted)return;if(data.rule.type!==kind||!etag)throw new ApiError(422,'WRONG_RULE_TYPE');setRecord(data);setEtag(etag);setRule({...data.rule,version:data.latestVersion+1});}).catch(e=>{if(!abort.signal.aborted)setError(e);}).finally(()=>{if(!abort.signal.aborted)setLoading(false);});return()=>abort.abort();},[id,kind,tenant]);
 useEffect(()=>{onDraft(loading||id&&!record||!supported(rule)?null:rule);return()=>onDraft(null);},[rule,loading,id,record,onDraft]);
 const blocked=locked||loading||!!id&&!record||record?.status==='RETIRED'||conflict||!!confirmation||validating;
 function change(next:Definition){setRule(next);setValid(false);setError(undefined);}
 async function validate(){setValidating(true);setError(undefined);try{await api(tenant,'/rules/validate',{rule:validateDefinition(rule,tenant)});setValid(true);}catch(e){setError(e);}finally{setValidating(false);}}
 function prepare(action:string){setError(undefined);try{if(!reason.trim()||reason.length>2048)throw new Error('Motivo de cambio obligatorio, máximo 2048 caracteres.');const path=action==='save'?'/rules':'/rules/'+encodeURIComponent(id)+'/'+action;const body=action==='save'?{rule:validateDefinition(rule,tenant),reason}:{version:action==='enable'?record!.latestVersion:record!.activeVersion??record!.latestVersion,reason};setConfirmation({...operation(tenant,path,body,etag,rule.id),kind});}catch(e){setError(e);}}
 return <article className="blackout-editor" aria-label={t('Editor de configuración')}><h2>{kind} · {id||t('Nuevo')}</h2><InventoryError error={error}/>{loading&&<p role="status">{t('Cargando…')}</p>}
  {record&&<p>{t('Estado')}: {record.status} · {t('Versión activa')}: {record.activeVersion??'—'} · {t('Última guardada')}: {record.latestVersion} · ETag {etag}</p>}
  {id&&<button disabled={locked||!!confirmation} onClick={reconcile}>{t('Cargar revisión actual y descartar borrador')}</button>}
  {conflict&&<p role="alert">{t('Tu borrador se conserva. Carga la revisión actual antes de preparar otra operación.')}</p>}
  {record?.activeVersion&&<button onClick={async()=>{try{const {data}=await api<Registry>(tenant,'/rules/'+encodeURIComponent(id)+'?version='+record.activeVersion);setActive(data.rule);}catch(e){setError(e);}}}>{t('Consultar definición activa')}</button>}
  {active&&<details open><summary>{t('Definición activa')}</summary><pre>{JSON.stringify(active,null,2)}</pre></details>}
  {!supported(rule)?<><p>{t('Definición avanzada: solo consulta en este formulario.')}</p><pre>{JSON.stringify(rule,null,2)}</pre></>:<fieldset disabled={blocked}><legend>{t('Nueva versión de la definición')}</legend>
   <div className="blackout-fields"><label>ID<input aria-label="Configuration ID" disabled={!!id} maxLength={128} value={rule.id} onChange={e=>change({...rule,id:e.target.value})}/></label><label>{t('Prioridad')}<input type="number" value={rule.priority} onChange={e=>change({...rule,priority:Number(e.target.value)})}/></label><label>{t('Responsable')}<input value={rule.metadata.owner} onChange={e=>change({...rule,metadata:{owner:e.target.value}})}/></label></div>
   {rule.type==='INVENTORY'?<>
    <h3>{t('Alcance exacto')}</h3><p>{t('Selecciona al menos un recurso; todos los selectores se combinan con AND.')}</p><div className="blackout-fields">{selectors.map(field=><label key={field}>{field}<input value={rule.scope[field]??''} onChange={e=>{const scope={...rule.scope};if(e.target.value)scope[field]=e.target.value;else delete scope[field];change({...rule,scope});}}/></label>)}</div>
    <h3>{t('Hechos tipados')}</h3><div className="inventory-facts">{Object.entries(factTypes).map(([field,type])=><div key={field}><label className="inventory-check"><input type="checkbox" aria-label={`${t('Incluir')} ${field}`} checked={Object.hasOwn(rule.facts,field)} onChange={e=>{const facts={...rule.facts};if(e.target.checked)facts[field]=type==='boolean'?true:type==='number'?0:'';else delete facts[field];change({...rule,facts});}}/>{field}<small>{type}</small></label>{Object.hasOwn(rule.facts,field)&&(type==='boolean'?<select aria-label={field} value={String(rule.facts[field])} onChange={e=>change({...rule,facts:{...rule.facts,[field]:e.target.value==='true'}})}><option value="true">true</option><option value="false">false</option></select>:<input aria-label={field} type={type==='number'?'number':'text'} step={type==='number'?'any':undefined} maxLength={type==='string'?4096:undefined} value={String(rule.facts[field])} onChange={e=>change({...rule,facts:{...rule.facts,[field]:type==='number'?(e.target.value===''?'':Number(e.target.value)):e.target.value}})}/>)}</div>)}</div>
   </>:<><h3>{t('Condición del recurso')}</h3><div className="blackout-fields"><label>{t('Campo')}<select value={rule.condition.field} onChange={e=>change({...rule,condition:{...rule.condition,field:e.target.value}})}><option>resource.node</option><option>resource.component</option></select></label><label>{t('Operador')}<select value={rule.condition.operator} onChange={e=>change({...rule,condition:{field:rule.condition.field,operator:e.target.value as 'EXISTS'|'EQ',...(e.target.value==='EQ'?{value:''}:{})}})}><option>EXISTS</option><option>EQ</option></select></label>{rule.condition.operator==='EQ'&&<label>{t('Valor de condición')}<input value={rule.condition.value??''} onChange={e=>change({...rule,condition:{...rule.condition,value:e.target.value}})}/></label>}</div><p>LOOKUP_INVENTORY</p><label className="inventory-check"><input type="checkbox" checked={rule.actions[0].parameters.required} onChange={e=>change({...rule,actions:[{type:'LOOKUP_INVENTORY',parameters:{required:e.target.checked}}]})}/>{t('Inventario requerido')}</label><p>{t('Requerido sin coincidencia: FAILED / DEAD_LETTER. Opcional sin coincidencia: NOT_FOUND y continúa.')}</p></>}
   <label className="inventory-check"><input type="checkbox" checked={rule.enabled} onChange={e=>change({...rule,enabled:e.target.checked})}/>{t('Versión habilitable (no activa al guardar)')}</label>
   <div className="blackout-actions"><button onClick={validate}>{t('Validar definición')}</button></div>
  </fieldset>}
  <fieldset disabled={blocked}><legend>{t('Operaciones de la configuración')}</legend><label>{t('Motivo del cambio')}<textarea maxLength={2048} value={reason} onChange={e=>setReason(e.target.value)}/></label><div className="blackout-actions"><button disabled={!supported(rule)} onClick={()=>prepare('save')}>{t('Guardar nueva versión')}</button>{record&&<><button disabled={!record.rule.enabled} onClick={()=>prepare('enable')}>{t('Activar última guardada')}</button><button onClick={()=>prepare('disable')}>{t('Desactivar')}</button><button onClick={()=>prepare('retire')}>{t('Retirar')}</button></>}</div></fieldset>
  {valid&&<p role="status">{t('Definición válida. Aún no está guardada.')}</p>}{record?.status==='RETIRED'&&<p>{t('Retirado: estado terminal. El historial se conserva.')}</p>}
  {confirmation&&<section className="blackout-confirm" aria-label={t('Confirmar operación')}><h3>{t('Confirmar operación')}</h3><p>Tenant: {tenant} · ID: {confirmation.ruleId} · If-Match: {confirmation.etag}</p><p>{t(confirmation.path==='/rules'?'Guardar no activa. La versión activa anterior seguirá ejecutándose.':confirmation.path.endsWith('/retire')?'Retirar es terminal y bloquea la reactivación.':'Esta operación cambia el estado operativo de la regla guardada.')}</p>{kind==='INVENTORY'&&/\/(disable|retire)$/.test(confirmation.path)&&<p className="inventory-warning">{t('Los planes requeridos pueden quedar sin datos y producir DEAD_LETTER. Simula el impacto antes de confirmar.')}</p>}<pre>{confirmation.body}</pre><button disabled={locked} onClick={()=>{const op=confirmation;setConfirmation(null);void execute(op);}}>{t('Confirmar envío')}</button><button disabled={locked} onClick={()=>setConfirmation(null)}>{t('Cancelar')}</button></section>}
  {record&&<History tenant={tenant} id={id}/>}
 </article>;
}
