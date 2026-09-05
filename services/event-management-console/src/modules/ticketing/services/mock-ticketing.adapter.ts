import type { TicketingRepository } from "./ticketing.port";
export const mockTicketingRepository:TicketingRepository={search:()=>({items:[{id:"INC-MOCK-0001",status:"Open",priority:"High",summary:"Evento simulado para validar el scaffold"}],total:1})};
