import {createUuid} from '../../shared/utils/uuid';
export const matchFields=['resource.node','resource.component','enrichment.resource.ciId','enrichment.service.name','enrichment.assignment.group','enrichment.location.site','enrichment.resource.class'];
export type Rule={id:string;version:number;enabled:boolean;priority:number;strategy:string;scope:{field:string;operator:string;value?:string};candidateSelection:{windowSeconds:number;maxCandidates:number;activeOnly:boolean};match:{fields:string[]};relationship:{type:string};metadata:{owner:string;description?:string}};
export type Summary={id:string;latestVersion:number;activeVersion:number|null;status:string;revision:number};
export type Registry=Summary&{rule:Rule;checksum:string};
export const newRule=():Rule=>({id:'',version:1,enabled:true,priority:10,strategy:'ATTRIBUTE',scope:{field:'resource.node',operator:'EXISTS'},candidateSelection:{windowSeconds:300,maxCandidates:16,activeOnly:true},match:{fields:['resource.node']},relationship:{type:'GROUP'},metadata:{owner:'operations'}});
export function supported(r:Rule){return r.strategy==='ATTRIBUTE'&&r.relationship?.type==='GROUP'&&r.candidateSelection?.activeOnly===true&&r.scope?.field==='resource.node'&&['EXISTS','EQ'].includes(r.scope.operator)&&Object.keys(r.scope).every(k=>['field','operator','value'].includes(k))&&Array.isArray(r.match?.fields)&&r.match.fields.every(f=>matchFields.includes(f));}
export function validate(r:Rule){
 if(!supported(r))throw new Error('Definición avanzada: solo consulta en este formulario.');
 if(!/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(r.id))throw new Error('ID inválido: máximo 128 caracteres, letras, números, punto, guion o guion bajo.');
 const {windowSeconds:w,maxCandidates:m}=r.candidateSelection;
 if(!Number.isInteger(w)||w<1||w>86400||!Number.isInteger(m)||m<1||m>32)throw new Error('Ventana: 1..86400 segundos; capacidad: 1..32, enteros.');
 if(r.match.fields.length<1||r.match.fields.length>4||new Set(r.match.fields).size!==r.match.fields.length)throw new Error('Selecciona entre 1 y 4 campos únicos.');
 if(r.scope.operator==='EQ'&&!r.scope.value?.trim())throw new Error('Valor de condición obligatorio.');
 if(!r.metadata.owner.trim()||!Number.isSafeInteger(r.priority))throw new Error('Completa responsable y prioridad entera.');
 return r;
}
export type SequenceRow={key:string;node:string;action:'OPEN'|'CLOSE';at:string};
export function exampleSequence():SequenceRow[]{return ['OPEN','OPEN','CLOSE','CLOSE','OPEN'].map((action,i)=>({key:i===1||i===3?'member-b':'member-a',node:'router-1',action:action as 'OPEN'|'CLOSE',at:`2026-09-10T18:0${i}:00Z`}));}
export function eventsFor(rows:SequenceRow[],tenant:string){
 if(!tenant||rows.length<1||rows.length>64)throw new Error('Selecciona tenant y entre 1 y 64 eventos.');
 return rows.map(r=>{if(!r.key.trim()||!r.node.trim()||!['OPEN','CLOSE'].includes(r.action)||!/^\d{4}-\d\d-\d\dT.*(Z|[+-]\d\d:\d\d)$/.test(r.at)||!Number.isFinite(Date.parse(r.at)))throw new Error('Completa miembro, recurso y fecha ISO con offset de cada evento.');return {schemaVersion:'1.1',eventId:createUuid(),eventKey:r.key,tenant:{code:tenant},resource:{name:r.node},summary:'Console correlation simulation',lifecycleAction:r.action,effectiveSeverity:r.action==='CLOSE'?0:3,timestamps:{receivedAt:new Date(r.at).toISOString()}};});
}
