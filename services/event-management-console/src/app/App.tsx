import { ThemeProvider } from "../shared/theme/ThemeProvider";
import { I18nProvider } from "../shared/i18n/I18nProvider";
import { RouterProvider } from "react-router-dom";
import { router } from "./router";

export const CONSOLE_ROLE = "GLOBAL_EVENT_MANAGEMENT_CONSOLE";
export function App() { return <ThemeProvider><I18nProvider><RouterProvider router={router} /></I18nProvider></ThemeProvider>; }
