import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { domains } from "./data";
import { ThemeSelector } from "./ThemeProvider";
import { ProfileMenu } from "./ProfileMenu";
import { useI18n } from "./i18n";
import "./navigation-panel.css";

const minWidth=240,maxWidth=400,defaultWidth=260;
const clamp=(width:number)=>Math.max(minWidth,Math.min(maxWidth,width));
function savedWidth(){try{const n=Number(localStorage.getItem('console.sidebar.width'));return Number.isFinite(n)&&n>=minWidth?clamp(n):defaultWidth;}catch{return defaultWidth;}}
function savedClosed(){try{return localStorage.getItem('console.sidebar.closed')==='true';}catch{return false;}}
export function NavigationFrame({children}:{children:ReactNode}){
 const {t}=useI18n();
 const [mobile,setMobile]=useState(()=>window.matchMedia('(max-width:900px)').matches);
 const [mobileOpen,setMobileOpen]=useState(false),[closed,setClosed]=useState(savedClosed),[width,setWidth]=useState(savedWidth),[dragging,setDragging]=useState(false);
 const menuButton=useRef<HTMLButtonElement>(null),drag=useRef<{x:number;width:number}|null>(null);
 const open=mobile?mobileOpen:!closed;
 useEffect(()=>{const media=window.matchMedia('(max-width:900px)');const change=()=>{setMobile(media.matches);setMobileOpen(false);drag.current=null;setDragging(false);};media.addEventListener('change',change);return()=>media.removeEventListener('change',change);},[]);
 useEffect(()=>{try{localStorage.setItem('console.sidebar.width',String(width));}catch{}},[width]);
 useEffect(()=>{try{localStorage.setItem('console.sidebar.closed',String(closed));}catch{}},[closed]);
 function close(){if(mobile)setMobileOpen(false);else setClosed(true);menuButton.current?.focus();}
 useEffect(()=>{if(!open)return;const escape=(event:KeyboardEvent)=>{if(event.key==='Escape'&&!(event.target as HTMLElement)?.closest('[role="dialog"],.modal-layer')){if(mobile)setMobileOpen(false);else setClosed(true);menuButton.current?.focus();}};window.addEventListener('keydown',escape);return()=>window.removeEventListener('keydown',escape);},[open,mobile]);
 return <div className={`shell navigation-adjustable${!open?' navigation-closed':''}${dragging?' navigation-resizing':''}`} style={{'--sidebar-width':`${width}px`} as CSSProperties}>
 {open&&<DashboardSidebar open={open} onClose={close} onNavigate={()=>{if(mobile)close();}} resizeHandle={!mobile&&<div className="sidebar-resize" role="separator" tabIndex={0} aria-label={t('Ajustar ancho del menú')} aria-orientation="vertical" aria-valuemin={minWidth} aria-valuemax={maxWidth} aria-valuenow={width} aria-controls="dashboards-navigation" title={t('Arrastra para ajustar. Flechas para cambiar ancho; doble clic para restablecer.')} onPointerDown={e=>{if(e.button!==0)return;drag.current={x:e.clientX,width};e.currentTarget.setPointerCapture(e.pointerId);setDragging(true);e.preventDefault();}} onPointerMove={e=>{if(drag.current)setWidth(clamp(drag.current.width+e.clientX-drag.current.x));}} onPointerUp={e=>{drag.current=null;setDragging(false);if(e.currentTarget.hasPointerCapture(e.pointerId))e.currentTarget.releasePointerCapture(e.pointerId);}} onPointerCancel={()=>{drag.current=null;setDragging(false);}} onLostPointerCapture={()=>{drag.current=null;setDragging(false);}} onDoubleClick={()=>setWidth(defaultWidth)} onKeyDown={e=>{if(['ArrowLeft','ArrowRight','Home','End'].includes(e.key)){e.preventDefault();setWidth(w=>e.key==='Home'?minWidth:e.key==='End'?maxWidth:clamp(w+(e.key==='ArrowRight'?16:-16)));}}}/>}/>}
 {mobile&&open&&<button className="backdrop navigation-backdrop" onClick={close} aria-label={t('Cerrar menú')}/>}
 <div className="workspace"><header><button ref={menuButton} className="icon-button navigation-toggle" aria-label={t(open?'Cerrar menú':'Abrir menú')} title={t(open?'Cerrar menú':'Abrir menú')} aria-expanded={open} aria-controls="dashboards-navigation" onClick={()=>mobile?setMobileOpen(v=>!v):setClosed(v=>!v)}>☰</button><div className="page-context"><span className="eyebrow">OPEN EVENT MANAGEMENT</span><strong>{t('Dashboards operativos')}</strong></div><div className="topbar-actions"><span className="environment">LOCAL</span><ProfileMenu/></div></header>{children}</div>
 </div>;
}

function DashboardSidebar({onClose,onNavigate,resizeHandle}:{open:boolean;onClose:()=>void;onNavigate:()=>void;resizeHandle?:ReactNode}){const {t}=useI18n();const [collapsed,setCollapsed]=useState(()=>{try{return localStorage.getItem('console.navigation.operations')==='true';}catch{return false;}});useEffect(()=>{try{localStorage.setItem('console.navigation.operations',String(collapsed));}catch{}},[collapsed]);return <aside id="dashboards-navigation" className="sidebar open"><div className="brand"><span>EM</span><div><strong>Event Management</strong><small>Dashboards</small></div><button className="icon-button sidebar-close" aria-label={t('Cerrar menú')} onClick={onClose}>×</button></div><button className="nav-label nav-group-toggle" aria-expanded={!collapsed} aria-controls="dashboard-navigation-items" onClick={()=>setCollapsed(v=>!v)}><span>{t('Centro de operaciones')}</span><span aria-hidden="true">{collapsed?'›':'⌄'}</span></button><nav id="dashboard-navigation-items" hidden={collapsed} aria-label="Dashboards">{Object.entries(domains).map(([id,info],index)=><NavLink onClick={onNavigate} key={id} to={`/dashboards/${id}`}><span className="nav-number">0{index+1}</span>{info.title}</NavLink>)}{[['delivery','06','Delivery'],['data-collection','07','Data Collection'],['api-management','08','API Management']].map(([id,num,label])=><NavLink onClick={onNavigate} key={id} to={`/dashboards/${id}`}><span className="nav-number">{num}</span>{label}</NavLink>)}</nav><footer className="sidebar-footer"><span>{t('Console local')}</span><div className="sidebar-preferences"><ThemeSelector/></div><small>{t('Consulta operativa')}</small></footer>{resizeHandle}</aside>;}
