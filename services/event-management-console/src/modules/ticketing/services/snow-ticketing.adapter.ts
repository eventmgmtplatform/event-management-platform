import type { Ticket, TicketingRepository, Status } from "./ticketing.port";
export const snowStates: Record<Status, string> = { Open: "1", "In Progress": "2", Pending: "3", Resolved: "6", Closed: "7", Failed: "-1" };
const priorities: Record<string, string> = { "1": "Critical", "2": "High", "3": "Moderate", "4": "Low" };
const endpoint = "/api/now/table/incident";
export function parseIncident(value: unknown): Ticket {
  if (!value || typeof value !== "object") throw new Error("Respuesta de ServiceNow inválida.");
  const r = value as Record<string, unknown>;
  const fields = ["sys_id", "number", "state", "priority", "u_customer", "cmdb_ci", "u_tower", "short_description", "description", "sys_updated_on", "close_code", "close_notes"];
  if (!fields.every(key => typeof r[key] === "string")) throw new Error("Respuesta de ServiceNow inválida.");
  const state = Object.entries(snowStates).find(([, code]) => code === r.state)?.[0] as Status | undefined;
  if (!state || !Object.hasOwn(priorities, String(r.priority)) || !/^console-INC\d+$/.test(String(r.sys_id)) || !/^INC\d+$/.test(String(r.number))) throw new Error("Respuesta de ServiceNow inválida.");
  return { provider: "SERVICENOW", id: String(r.number), sysId: String(r.sys_id), status: state, priority: priorities[String(r.priority)], customer: String(r.u_customer), resource: String(r.cmdb_ci), tower: String(r.u_tower), summary: String(r.short_description), description: String(r.description), updated: String(r.sys_updated_on), closeCode: String(r.close_code), closeNotes: String(r.close_notes) };
}
async function request(path: string, init: RequestInit) {
  const response = await fetch(path, { ...init, cache: "no-store", headers: { "Content-Type": "application/json", Accept: "application/json" } });
  if (!response.ok) throw new Error(response.status === 404 ? "Ticket no encontrado." : "No fue posible consultar o actualizar ServiceNow. Intenta nuevamente.");
  if (!response.headers.get("content-type")?.includes("application/json")) throw new Error("Respuesta de ServiceNow inválida.");
  return response.json();
}
export const snowTicketingRepository: TicketingRepository = {
  async search(query, signal) {
    const terms: string[] = [];
    if (query.number.trim()) terms.push(`numberLIKE${query.number.trim().toUpperCase()}`);
    if (query.status !== "All") terms.push(`state=${snowStates[query.status]}`);
    const parameters = new URLSearchParams({ sysparm_query: terms.join("^") });
    const data = await request(`${endpoint}?${parameters}`, { signal });
    if (!Array.isArray(data.result)) throw new Error("Respuesta de ServiceNow inválida.");
    return data.result.map(parseIncident);
  },
  async close(ticket, code, note) {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 8000);
    try {
      const data = await request(`${endpoint}/${encodeURIComponent(ticket.sysId)}`, { method: "PATCH", signal: controller.signal, body: JSON.stringify({ state: "7", close_code: code, close_notes: note.trim() }) });
      const updated = parseIncident(data.result);
      if (updated.sysId !== ticket.sysId || updated.status !== "Closed") throw new Error("Respuesta de ServiceNow inválida.");
      return updated;
    } finally { window.clearTimeout(timeout); }
  },
};
