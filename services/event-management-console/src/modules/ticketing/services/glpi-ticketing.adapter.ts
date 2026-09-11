import type { Ticket, TicketingRepository, Status } from "./ticketing.port";
const statuses: Record<number, Status> = {1:"Open",2:"In Progress",3:"In Progress",4:"Pending",5:"Resolved",6:"Closed"};
const priorities: Record<number,string> = {1:"Very Low",2:"Low",3:"Moderate",4:"High",5:"Critical",6:"Major"};
export function parseGlpiTicket(value: unknown): Ticket {
  if (!value || typeof value !== "object") throw new Error("Respuesta GLPI inválida.");
  const r = value as Record<string, unknown>;
  if (!Number.isSafeInteger(r.id) || Number(r.id) < 1 || !statuses[Number(r.status)] || !priorities[Number(r.priority)] || typeof r.name !== "string" || typeof r.content !== "string" || typeof r.date_mod !== "string") throw new Error("Respuesta GLPI inválida.");
  return {provider:"GLPI", id:`GLPI-${r.id}`,sysId:String(r.id),status:statuses[Number(r.status)],priority:priorities[Number(r.priority)],customer:String(r.entities_id ?? "—"),resource:"—",tower:String(r.itilcategories_id ?? "—"),summary:r.name,description:r.content,updated:r.date_mod,closeCode:"",closeNotes:""};
}
async function request(path:string, init:RequestInit) {
  const r=await fetch(path,{...init,cache:"no-store",headers:{"Content-Type":"application/json",Accept:"application/json"}});
  if(!r.ok)throw new Error("GLPI no disponible o cambio sin confirmar. Consulta el ticket antes de reintentar.");
  return r.json();
}
export const glpiTicketingRepository: TicketingRepository = {
  async search(query,signal) {
    const data=await request("/api/glpi/tickets",{signal});
    if(!Array.isArray(data.tickets))throw new Error("Respuesta GLPI inválida.");
    return data.tickets.map(parseGlpiTicket).filter((ticket:Ticket)=>(query.status==="All" || ticket.status===query.status) && ticket.id.toUpperCase().includes(query.number.trim().toUpperCase()));
  },
  async close(ticket,code,note) {
    const controller=new AbortController();const timeout=window.setTimeout(()=>controller.abort(),30000);
    try {
      const data=await request(`/api/glpi/tickets/${encodeURIComponent(ticket.sysId)}/close`,{method:"POST",signal:controller.signal,body:JSON.stringify({note,solutionTypeId:Number(code)})});
      const updated=parseGlpiTicket(data.ticket);
      if(updated.sysId!==ticket.sysId || updated.status!=="Closed")throw new Error("Cierre GLPI no confirmado.");
      return updated;
    } finally {window.clearTimeout(timeout);}
  }
};
