export const domains = {
  events: { title: "Event Management", short: "Eventos", unit: "Agregados de eventos", description: "Estado operativo y ciclo de vida de los eventos.", checklist: ["Revisar eventos abiertos y severidad efectiva", "Identificar repeticiones con el contador tally", "Consultar cierres en el estado consolidado"] },
  ticketing: { title: "Ticketing", short: "Ticketing", unit: "Eventos con integración ITSM", description: "Seguimiento de solicitudes y resultados conocidos por la plataforma. No representa tickets únicos del proveedor.", checklist: ["Revisar estados pendientes y fallidos", "Verificar referencias de ticket disponibles", "Consultar reconciliaciones con el equipo de integración"] },
  gnm: { title: "GNM", short: "GNM", unit: "Eventos con integración GNM", description: "Notificaciones y confirmaciones conocidas por la plataforma. No representa incidentes únicos de Everbridge.", checklist: ["Revisar estados de apertura y confirmación", "Identificar notificaciones sin referencia", "Revisar fallos y cierres confirmados"] },
  cacf: { title: "CACF", short: "CACF", unit: "Ejecuciones de automatización", description: "Ejecuciones, callbacks y resultados de automatización. COMPLETED indica término; el resultado determina su éxito.", checklist: ["Revisar ejecuciones en curso y callbacks pendientes", "Identificar vencimientos y fallos de envío", "Revisar el resultado de cada ejecución terminada"] },
} as const;
export type Domain = keyof typeof domains;
export type Row = {id: string; tenant: string; status: string; eventId: string; reference: string | null; updatedAt: string; severity: number | null; tally: number | null; outcome: string | null};
export type Snapshot = {schemaVersion: "1.0"; domain: Domain; source: "postgresql" | "internal-api"; observedAt: string; lastUpdatedAt: string | null; total: number; page: number; limit: number; counts: Record<string, number>; rows: Row[]};
export type Query = {tenant: string; status: string; q: string; page: number; limit: number};
const date = (v: unknown): v is string => typeof v === "string" && /(?:Z|[+-]\d\d:\d\d)$/.test(v) && Number.isFinite(Date.parse(v));
const integer = (v: unknown): v is number => typeof v === "number" && Number.isSafeInteger(v) && v >= 0;
const text = (v: unknown): v is string => typeof v === "string" && v.length > 0 && v.length <= 512;

export function parseSnapshot(value: unknown, domain: Domain, query: Query): Snapshot {
  const invalid = () => { throw new Error("Respuesta inválida: el contrato del dashboard no coincide."); };
  if (!value || typeof value !== "object") return invalid();
  const d = value as Snapshot;
  if (d.schemaVersion !== "1.0" || d.domain !== domain || !["postgresql", "internal-api"].includes(d.source) || !date(d.observedAt) || !(d.lastUpdatedAt === null || date(d.lastUpdatedAt)) || !integer(d.total) || d.page !== query.page || d.limit !== query.limit) return invalid();
  if (!d.counts || Array.isArray(d.counts) || typeof d.counts !== "object" || !Object.entries(d.counts).every(([k,v]) => text(k) && integer(v)) || Object.values(d.counts).reduce((a,b) => a+b, 0) !== d.total) return invalid();
  if (!Array.isArray(d.rows) || d.rows.length !== Math.min(query.limit, Math.max(0, d.total - (query.page - 1) * query.limit))) return invalid();
  for (const r of d.rows) {
    if (!r || ![r.id,r.tenant,r.status,r.eventId].every(text) || !date(r.updatedAt) || !(r.reference === null || text(r.reference)) || !(r.outcome === null || text(r.outcome)) || !(r.severity === null || (integer(r.severity) && r.severity <= 5)) || !(r.tally === null || integer(r.tally)) || (query.tenant && r.tenant !== query.tenant) || (query.status && r.status !== query.status)) return invalid();
  }
  if (new Set(d.rows.map(r => r.id)).size !== d.rows.length) return invalid();
  return d;
}

export async function getSnapshot(domain: Domain, query: Query, signal: AbortSignal): Promise<Snapshot> {
  const params = new URLSearchParams(Object.entries(query).map(([k,v]) => [k,String(v)]));
  const response = await fetch(`/api/dashboards/${domain}?${params}`, {signal, headers: {Accept: "application/json"}});
  if (!response.headers.get("content-type")?.includes("application/json")) throw new Error("API de dashboards no conectada.");
  if (!response.ok) throw new Error(response.status === 400 ? "Revisa los filtros de la consulta." : "Fuente no disponible. Verifica la conexión con el administrador.");
  return parseSnapshot(await response.json(), domain, query);
}
