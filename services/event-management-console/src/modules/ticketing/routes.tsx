import type { RouteObject } from "react-router-dom";
import { TicketDashboardPage } from "./pages/TicketDashboardPage";
export const ticketingRoutes:RouteObject[]=[{path: "ticketing/tickets",element:<TicketDashboardPage />}];
