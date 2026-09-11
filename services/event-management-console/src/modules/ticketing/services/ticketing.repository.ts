import {snowTicketingRepository} from "./snow-ticketing.adapter";
import {glpiTicketingRepository} from "./glpi-ticketing.adapter";
import type {Ticket, TicketingRepository} from "./ticketing.port";
// Providers are combined only at the presentation boundary. IDs and writes remain provider scoped.
export class PartialTicketingError extends Error {
  constructor(public tickets:Ticket[],message:string){super(message);}
}
export const ticketingRepository: TicketingRepository = {
  async search(query,signal) {
    const results=await Promise.allSettled([snowTicketingRepository.search(query,signal),glpiTicketingRepository.search(query,signal)]);
    if(signal.aborted)throw new DOMException("Aborted","AbortError");
    const tickets=results.flatMap(r=>r.status==="fulfilled"?r.value:[]).sort((a,b)=>b.updated.localeCompare(a.updated));
    const failed=results.flatMap((r,i)=>r.status==="rejected"?[i===0?"ServiceNow":"GLPI"]:[]);
    if(failed.length)throw new PartialTicketingError(tickets,`Datos incompletos: ${failed.join(", ")} no disponible. Los conteos sólo incluyen proveedores disponibles.`);
    return tickets;
  },
  close(ticket,code,note) {
    return (ticket.provider==="GLPI"?glpiTicketingRepository:snowTicketingRepository).close(ticket,code,note);
  }
};
