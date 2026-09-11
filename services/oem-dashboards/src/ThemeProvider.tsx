import {createContext,useContext,useLayoutEffect,useState,type ReactNode} from 'react';
import {useI18n} from './i18n';
type Theme='current'|'kyndryl'|'carbon'|'liverpool';
const ThemeContext=createContext({theme:'current' as Theme,setTheme:(_:Theme)=>{}});
export function ThemeProvider({children}:{children:ReactNode}){
 const [theme,setTheme]=useState<Theme>(()=>{try{const saved=localStorage.getItem('console.theme');return ['current','kyndryl','carbon','liverpool'].includes(saved??'')?saved as Theme:'current';}catch{return 'current';}});
 useLayoutEffect(()=>{document.documentElement.dataset.theme=theme;try{localStorage.setItem('console.theme',theme);}catch{/* Optional preference storage. */}},[theme]);
 return <ThemeContext.Provider value={{theme,setTheme}}>{children}</ThemeContext.Provider>;
}
export function ThemeSelector(){const {theme,setTheme}=useContext(ThemeContext);const {t}=useI18n();return <label className="theme-control"><span>{t('Tema')}</span><select aria-label={t('Tema')} value={theme} onChange={e=>setTheme(e.target.value as Theme)}><option value="current">{t('Actual')}</option><option value="kyndryl">Kyndryl</option><option value="carbon">IBM Carbon</option><option value="liverpool">LIVERPOOL</option></select></label>;}
