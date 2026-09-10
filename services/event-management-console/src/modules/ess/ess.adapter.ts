export type EssConfig={tenants:string[];operatorQuarantine:boolean;authorizationScope:'local-console'};
export type EventState={event_key:string;event_id:string|null;tenant:string;lifecycle_status:string|null;ticket_number:string|null;notification_id:string|null;automation_id:string|null;servicenow_status:string|null;gnm_status:string|null;cacf_status:string|null;source_severity:number|null;effective_severity:number|null;tally:number|null;version:number|null;first_seen_at:string|null;last_updated_at:string|null;last_state_at:string|null};
export type Transition={message_id:string|null;event_key:string;from_status:string|null;to_status:string|null;transition_type:string|null;aggregate_version:number|null;occurred_at:string|null;recorded_at:string|null};
export type EventPage={items:EventState[];nextCursor:string};
export type HistoryPage={items:Transition[];nextVersion:number};
export type Quarantine={scope:'operator-global';items:{reason:string;count:number;last_seen_at:string|null}[]};
export class EssError extends Error{constructor(public status:number,public code:string){super(code);}}
const object=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const text=(v:unknown)=>v===null||typeof v==='string';
const number=(v:unknown)=>v===null||(typeof v==='number'&&Number.isSafeInteger(v));
function invalid():never{throw new EssError(503,'INVALID_UPSTREAM_RESPONSE');}
export function parseEvent(v:unknown):EventState{
 if(!object(v)||typeof v.event_key!=='string'||typeof v.tenant!=='string'||!v.event_key||!v.tenant)invalid();
 for(const field of ['event_id','lifecycle_status','ticket_number','notification_id','automation_id','servicenow_status','gnm_status','cacf_status','first_seen_at','last_updated_at','last_state_at'])if(!text(v[field]))invalid();
 for(const field of ['source_severity','effective_severity','tally','version'])if(!number(v[field]))invalid();
 return v as unknown as EventState;
}
export function parseConfig(v:unknown):EssConfig{if(!object(v)||!Array.isArray(v.tenants)||!v.tenants.length||!v.tenants.every(x=>typeof x==='string'&&x.length>0)||typeof v.operatorQuarantine!=='boolean'||v.authorizationScope!=='local-console')invalid();return v as EssConfig;}
export function parseEvents(v:unknown):EventPage{if(!object(v)||!Array.isArray(v.items)||typeof v.nextCursor!=='string')invalid();return {items:v.items.map(parseEvent),nextCursor:v.nextCursor};}
export function parseHistory(v:unknown):HistoryPage{
 if(!object(v)||!Array.isArray(v.items)||typeof v.nextVersion!=='number'||!Number.isSafeInteger(v.nextVersion)||v.nextVersion<0)invalid();
 for(const row of v.items){if(!object(row)||typeof row.event_key!=='string')invalid();for(const field of ['message_id','from_status','to_status','transition_type','occurred_at','recorded_at'])if(!text(row[field]))invalid();if(!number(row.aggregate_version))invalid();}
 return v as HistoryPage;
}
export function parseQuarantine(v:unknown):Quarantine{if(!object(v)||v.scope!=='operator-global'||!Array.isArray(v.items)||!v.items.every(r=>object(r)&&typeof r.reason==='string'&&typeof r.count==='number'&&Number.isSafeInteger(r.count)&&r.count>=0&&text(r.last_seen_at)))invalid();return v as Quarantine;}
export const essPath=(action:'events'|'event'|'history',params:Record<string,string|number>)=>'/api/ess/'+action+'?'+new URLSearchParams(Object.entries(params).map(([k,v])=>[k,String(v)])).toString();
export async function readEss<T>(path:string,parse:(value:unknown)=>T,signal:AbortSignal):Promise<T>{
 const response=await fetch(path,{signal,cache:'no-store',headers:{Accept:'application/json'}});
 const body:unknown=await response.json().catch(()=>null);
 if(!response.ok)throw new EssError(response.status,object(body)&&typeof body.errorCode==='string'?body.errorCode:'ESS_UNAVAILABLE');
 return parse(body);
}
export const errorMessage=(error:EssError)=>({400:'La consulta no es válida. Revisa la clave, el límite o el cursor.',401:'ESS rechazó la autorización del servidor. Revisa su configuración.',403:'Esta consola no tiene permiso para consultar ese alcance.',404:'El evento no existe o no está disponible para este tenant.',503:'ESS no está disponible o su respuesta no es válida. Vuelve a intentar.'}[error.status]||'No fue posible consultar ESS. Vuelve a intentar.');
export const eventType=(key:string)=>key.startsWith('correlation:')?'Situación correlacionada':'Evento de origen';
export const displayDate=(value:string|null,locale:string)=>value&&Number.isFinite(Date.parse(value))?new Date(value).toLocaleString(locale):'—';
