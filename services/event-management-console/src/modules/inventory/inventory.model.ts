export type Kind='INVENTORY'|'ENRICHMENT';
export type BaseRule={id:string;version:number;type:Kind;enabled:boolean;priority:number;metadata:{owner:string}};
export type InventoryRule=BaseRule&{type:'INVENTORY';scope:Record<string,string>;facts:Record<string,string|number|boolean>};
export type PlanRule=BaseRule&{type:'ENRICHMENT';condition:{field:string;operator:'EXISTS'|'EQ';value?:string};actions:[{type:'LOOKUP_INVENTORY';parameters:{required:boolean}}]};
export type Definition=InventoryRule|PlanRule;
export type Registry={id:string;latestVersion:number;activeVersion:number|null;revision:number;status:string;rule:Definition;checksum:string};
export type CatalogRow={id:string;tenant:string;name:string;source:string;status:string;active_version:number|null;latest_version:number;revision:number;configuration:Definition};
export const factTypes={'resource.ciId':'string','service.name':'string','assignment.group':'string','location.site':'string','resource.class':'string','resource.managed':'boolean','service.criticality':'number'} as const;
export const selectors=['node','nodeAlias','component','instanceId','monitoringSolution'] as const;
export function emptyRule(kind:Kind,tenant:string):Definition {
 const base={id:'',version:1,enabled:true,priority:10,metadata:{owner:'operations'}};
 return kind==='INVENTORY'?{...base,type:kind,scope:{customerCode:tenant},facts:{'resource.ciId':''}}:{...base,type:kind,condition:{field:'resource.node',operator:'EXISTS'},actions:[{type:'LOOKUP_INVENTORY',parameters:{required:true}}]};
}
export function supported(rule:Definition):boolean {
 if(rule.type==='INVENTORY')return Object.keys(rule.scope).every(k=>k==='customerCode'||(selectors as readonly string[]).includes(k))&&Object.keys(rule.facts).every(k=>k in factTypes);
 const condition=rule.condition;
 return !!condition&&Object.keys(condition).every(k=>['field','operator','value'].includes(k))&&['resource.node','resource.component'].includes(condition.field)&&['EXISTS','EQ'].includes(condition.operator)&&rule.actions?.length===1&&rule.actions[0].type==='LOOKUP_INVENTORY'&&typeof rule.actions[0].parameters.required==='boolean';
}
export function validateDefinition(rule:Definition,tenant:string):Definition {
 if(!/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(rule.id))throw new Error('ID inválido: máximo 128 caracteres, letras, números, punto, guion o guion bajo.');
 if(!Number.isSafeInteger(rule.priority)||!rule.metadata.owner.trim())throw new Error('Completa responsable y prioridad entera.');
 if(!supported(rule))throw new Error('Definición avanzada: solo consulta en este formulario.');
 if(rule.type==='INVENTORY'){
  if(rule.scope.customerCode&&rule.scope.customerCode!==tenant)throw new Error('La regla debe pertenecer al tenant seleccionado.');
  if(!selectors.some(k=>rule.scope[k]?.trim()))throw new Error('Selecciona al menos un recurso; el inventario global no está permitido.');
  const entries=Object.entries(rule.facts);
  if(!entries.length||entries.length>7)throw new Error('Incluye entre uno y siete hechos permitidos.');
  for(const [key,value] of entries){const type=factTypes[key as keyof typeof factTypes];if(typeof value!==type||type==='number'&&!Number.isFinite(value)||type==='string'&&(!(value as string).trim()||(value as string).length>4096))throw new Error('Hecho inválido: verifica tipo y valor.');}
 }else if(rule.condition.operator==='EQ'&&!rule.condition.value?.trim())throw new Error('La condición de igualdad requiere un valor.');
 return rule;
}
export function candidateSet(rows:CatalogRow[],ids:string[],draft:Definition|null,tenant:string):Definition[]{
 const selected=ids.map(id=>{const row=rows.find(r=>r.id===id&&r.tenant===tenant);if(!row)throw new Error('El conjunto candidato cambió. Vuelve a seleccionar sus registros.');return row.configuration;}).filter(r=>r.id!==draft?.id);
 if(draft)selected.push(validateDefinition(draft,tenant));
 if(!selected.length||selected.length>256)throw new Error('Selecciona entre una y 256 definiciones candidatas.');
 return selected;
}
