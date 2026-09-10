import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import english from "./en.json";
export type Language = "es" | "en";
const dictionary: Record<string, string> = english;
const I18nContext = createContext({ language: "es" as Language, locale: "es-MX", setLanguage: (_: Language) => {}, t: (text: string) => text });
export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguage] = useState<Language>(() => {
    try { return localStorage.getItem("console.language") === "en" ? "en" : "es"; } catch { return "es"; }
  });
  useEffect(() => {
    document.documentElement.lang = language;
    try { localStorage.setItem("console.language", language); } catch { /* Storage may be disabled. */ }
  }, [language]);
  const t = (text: string) => language === "en" ? dictionary[text] ?? text : text;
  return <I18nContext.Provider value={{ language, locale: language === "en" ? "en-US" : "es-MX", setLanguage, t }}>{children}</I18nContext.Provider>;
}
export const useI18n = () => useContext(I18nContext);
