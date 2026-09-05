export type TicketSummary={id:string;status:string;priority:string;summary:string};
export type TicketPage={items:TicketSummary[];total:number};
export interface TicketingRepository{search():TicketPage;}
