import { RouterProvider } from "react-router-dom";
import { router } from "./router";

export const CONSOLE_ROLE = "GLOBAL_EVENT_MANAGEMENT_CONSOLE";
export function App() { return <RouterProvider router={router} />; }
