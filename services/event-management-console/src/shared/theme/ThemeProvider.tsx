import { createContext, useContext, useLayoutEffect, useState, type ReactNode } from 'react';
export type ConsoleTheme = 'current' | 'kyndryl' | 'carbon' | 'liverpool';
const ThemeContext = createContext({ theme: 'current' as ConsoleTheme, setTheme: (_: ConsoleTheme) => {} });
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<ConsoleTheme>(() => {
    try { const saved=localStorage.getItem('console.theme'); return ['current','kyndryl','carbon','liverpool'].includes(saved??'') ? saved as ConsoleTheme : 'current'; }
    catch { return 'current'; }
  });
  useLayoutEffect(() => {
    document.documentElement.dataset.theme = theme;
    try { localStorage.setItem('console.theme', theme); } catch { /* Optional preference storage. */ }
  }, [theme]);
  return <ThemeContext.Provider value={{ theme, setTheme }}>{children}</ThemeContext.Provider>;
}
export const useTheme = () => useContext(ThemeContext);
