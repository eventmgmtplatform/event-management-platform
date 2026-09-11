export const targets = ["gnm","snow","glpi","cacf","chatops","extensions"] as const;
export const targetNames: Record<string,string> = {gnm:"GNM",snow:"SNOW · Ticketing",glpi:"GLPI · Ticketing",cacf:"CACF",chatops:"ChatOps",extensions:"Extensions"};
export type DeliveryQuery = {target:string; applid:string; customer:string; state:string; severity:string; q:string; page:number; limit:number};
export type Target = {target:typeof targets[number]; behavior:"enable"|"force_off"|"overlay"; actionReference:string|null; assignmentGroup:string|null; delaySeconds:number|null; dependsOnTicketing:boolean};
export type DeliveryRow = {id:string;name:string;description:string;customerCode:string;applid:string|null;state:number;weight:number;severities:number[]|null;criteria:Record<string,{operator:string;value:string|number|boolean}>;origin:string;legacyFilterId:string|null;updatedAt:string;targets:Target[]};
export type Bucket = {value:string;count:number};
export type DeliverySnapshot = {schemaVersion:"1.0";domain:"delivery";source:"postgresql"|"internal-api";observedAt:string;lastUpdatedAt:string|null;total:number;page:number;limit:number;facets:Record<"targets"|"applids"|"severities"|"states",Bucket[]>;rows:DeliveryRow[]};

export function parseDelivery(d: DeliverySnapshot, q: DeliveryQuery): DeliverySnapshot {
  const require = (ok:unknown) => {if (!ok) throw new Error("Error de contrato Delivery.");};
  const integer = (n:unknown) => typeof n === "number" && Number.isSafeInteger(n) && n>=0;
  const timestamp = (s:unknown) => typeof s === "string" && /(?:Z|[+-]\d\d:\d\d)$/.test(s) && Number.isFinite(Date.parse(s));
  require(d && d.schemaVersion==="1.0" && d.domain==="delivery" && ["postgresql","internal-api"].includes(d.source));
  require(integer(d.total) && d.page===q.page && d.limit===q.limit && timestamp(d.observedAt) && (d.lastUpdatedAt===null || timestamp(d.lastUpdatedAt)));
  require(d.facets && ["targets","applids","severities","states"].every(k=>Array.isArray(d.facets[k as keyof typeof d.facets])));
  for (const buckets of Object.values(d.facets)) require(buckets.every(b=>typeof b.value==="string" && integer(b.count) && b.count<=d.total));
  require(d.facets.states.reduce((n,b)=>n+b.count,0)===d.total && d.facets.applids.reduce((n,b)=>n+b.count,0)===d.total);
  require(Array.isArray(d.rows) && d.rows.length===Math.min(q.limit,Math.max(0,d.total-(q.page-1)*q.limit)));
  for (const r of d.rows) {
    require(r && [r.id,r.name,r.description,r.customerCode,r.origin].every(v=>typeof v==="string") && r.id && r.name && r.customerCode && [0,1,2].includes(r.state) && Number.isSafeInteger(r.weight));
    require((r.applid===null || typeof r.applid==="string") && (r.severities===null || (Array.isArray(r.severities) && r.severities.every(n=>integer(n)&&n<=5))) && timestamp(r.updatedAt));
    require(r.criteria && !Array.isArray(r.criteria) && typeof r.criteria==="object" && Object.values(r.criteria).every(c=>c && typeof c.operator==="string" && ["string","number","boolean"].includes(typeof c.value)));
    require(Array.isArray(r.targets) && r.targets.every(t=>targets.includes(t.target) && ["enable","force_off","overlay"].includes(t.behavior) && (t.actionReference===null || typeof t.actionReference==="string") && (t.assignmentGroup===null || typeof t.assignmentGroup==="string") && (t.delaySeconds===null || integer(t.delaySeconds)) && typeof t.dependsOnTicketing==="boolean"));
  }
  require(new Set(d.rows.map(r=>r.id)).size===d.rows.length);
  return d;
}
export async function getDelivery(q: DeliveryQuery, signal:AbortSignal): Promise<DeliverySnapshot> {
  const params=new URLSearchParams(Object.entries(q).map(([k,v])=>[k,String(v)]));
  const response=await fetch(`/api/dashboards/delivery?${params}`,{signal,headers:{Accept:"application/json"}});
  if (!response.headers.get("content-type")?.includes("application/json")) throw new Error("API de dashboards no conectada.");
  if (!response.ok) throw new Error(response.status===400 ? "Revisa los filtros de la consulta." : "Fuente no disponible. Verifica la conexión con el administrador.");
  return parseDelivery(await response.json(),q);
}
