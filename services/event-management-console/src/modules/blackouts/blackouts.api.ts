import {createUuid} from '../../shared/utils/uuid';
export type Rule={id:string;version:number;type:'SCHEDULED'|'IMMEDIATE'|'RECURRING'|'SUPPRESSION';source?:'MANUAL'|'MAINTENANCE'|'CHANGE';externalStatus?:'ACTIVE'|'APPROVED'|'CANCELLED'|'COMPLETED';enabled:boolean;scope:Record<string,string>;schedule:{timezone:string;validFrom:string;validTo?:string|null;recurrence?:string|null};priority:number;reason:string;metadata:{owner:string;externalReference?:string|null}};
export type RecordState={id:string;latestVersion:number;activeVersion:number|null;status:string;revision:number;rule:Rule;checksum:string};
export type Operation={tenant:string;actor:string;path:string;body:string;etag:string;key:string;ruleId:string};
export class ApiError extends Error {constructor(public status:number,public code:string){super(code);}}
export async function api<T>(tenant:string,path:string,body?:unknown,operation?:Operation,signal?:AbortSignal):Promise<{data:T;etag:string|null}>{
 const controller=new AbortController();const abort=()=>controller.abort();signal?.addEventListener('abort',abort);if(signal?.aborted)abort();const timer=setTimeout(abort,15000);
 try{const response=await fetch('/api/processor/v1'+path,{method:body===undefined?'GET':'POST',cache:'no-store',signal:controller.signal,headers:{'Content-Type':'application/json','X-Tenant-Id':tenant,'X-Actor-Id':operation?.actor??'local-console',...(operation?{'If-Match':operation.etag,'Idempotency-Key':operation.key}:{})},body:body===undefined?undefined:operation?.body??JSON.stringify(body)});
 let data;try{data=await response.json();}catch{throw new ApiError(response.ok?503:response.status,'INVALID_RESPONSE');}
 if(!response.ok)throw new ApiError(response.status,typeof data.errorCode==='string'?data.errorCode:typeof data.code==='string'?data.code:'REQUEST_FAILED');
 return {data,etag:response.headers.get('ETag')};
 }catch(e){if(e instanceof ApiError)throw e;throw new ApiError(503,'CONNECTION_UNCERTAIN');}finally{clearTimeout(timer);signal?.removeEventListener('abort',abort);}
}
export function instant(value:string){if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:\d{2})$/.test(value)||!Number.isFinite(Date.parse(value)))throw new Error('Usa fecha ISO 8601 con Z u offset explícito.');return new Date(value).toISOString();}
export function validateRule(rule:Rule,tenant:string){
 if(!/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(rule.id))throw new Error('ID inválido: máximo 128 caracteres, letras, números, punto, guion o guion bajo.');
 if(!['SCHEDULED','IMMEDIATE','RECURRING','SUPPRESSION'].includes(rule.type)||rule.scope.customerCode!==tenant)throw new Error('La regla debe pertenecer al tenant seleccionado.');
 try{new Intl.DateTimeFormat('en',{timeZone:rule.schedule.timezone}).format();}catch{throw new Error('Zona IANA inválida.');}
 if(rule.type==='SUPPRESSION'&&(!['MANUAL','MAINTENANCE','CHANGE'].includes(rule.source??'')||!['ACTIVE','APPROVED','CANCELLED','COMPLETED'].includes(rule.externalStatus??'')))throw new Error('Origen o estado declarado inválido.');
 const start=instant(rule.schedule.validFrom);const end=rule.schedule.validTo?instant(rule.schedule.validTo):undefined;
 if(rule.type==='RECURRING'&&!/^(FREQ=(DAILY|WEEKLY))(;INTERVAL=[1-9][0-9]*)?(;BYDAY=(MO|TU|WE|TH|FR|SA|SU)(,(MO|TU|WE|TH|FR|SA|SU))*)?$/.test(rule.schedule.recurrence??''))throw new Error('La recurrencia debe usar FREQ=DAILY o WEEKLY, INTERVAL opcional y BYDAY opcional.');
 if(rule.type!=='IMMEDIATE'&&!end||end&&Date.parse(start)>=Date.parse(end))throw new Error('La ventana requiere inicio anterior al fin.');
 if(rule.type!=='RECURRING'&&rule.schedule.recurrence)throw new Error('Solo los blackouts recurrentes admiten recurrencia.');
 if(!rule.reason.trim()||!rule.metadata.owner.trim()||!Number.isSafeInteger(rule.priority))throw new Error('Completa motivo, responsable y prioridad entera.');
 return {...rule,schedule:{...rule.schedule,validFrom:start,...(end?{validTo:end}:{validTo:undefined})}};
}
export function operation(tenant:string,path:string,body:unknown,etag:string,ruleId:string):Operation{return {tenant,actor:'local-console',path,body:JSON.stringify(body),etag,key:createUuid(),ruleId};}
export function errorText(error:unknown){if(!(error instanceof ApiError))return error instanceof Error?error.message:'No se pudo completar la consulta.';return ({400:'Formato de solicitud inválido.',404:'Registro no encontrado.',409:'Conflicto: carga la revisión actual y reconcilia los cambios.',413:'La solicitud es demasiado grande.',422:'La definición no pasó la validación.',428:'Falta la revisión requerida.',503:'API no disponible. La operación puede no haberse confirmado.'} as Record<number,string>)[error.status]??'No se pudo completar la consulta.';}
