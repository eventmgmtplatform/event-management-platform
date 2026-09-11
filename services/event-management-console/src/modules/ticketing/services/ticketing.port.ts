export const ticketStatuses = ["Open", "In Progress", "Pending", "Resolved", "Closed", "Failed"] as const;
export type Status = typeof ticketStatuses[number];
export type Ticket = { id: string; sysId: string; provider?: "SERVICENOW" | "GLPI"; status: Status; priority: string; customer: string; resource: string; tower: string; summary: string; updated: string; description: string; closeCode: string; closeNotes: string };
export type TicketQuery = { number: string; status: Status | "All" };
export interface TicketingRepository {
  search(query: TicketQuery, signal: AbortSignal): Promise<Ticket[]>;
  close(ticket: Ticket, code: string, note: string): Promise<Ticket>;
}
