import { useEffect, useRef, useState, type CSSProperties } from "react";
import { Outlet } from "react-router-dom";
import { useI18n } from "../shared/i18n/I18nProvider";
import { Breadcrumbs } from "./Breadcrumbs";
import { Header } from "./Header";
import { Sidebar } from "./Sidebar";
import "./navigation-panel.css";

const minWidth=240,maxWidth=400,defaultWidth=260;
const clamp=(width:number)=>Math.max(minWidth,Math.min(maxWidth,width));
function savedWidth(){try{const n=Number(localStorage.getItem('console.sidebar.width'));return Number.isFinite(n)&&n>=minWidth?clamp(n):defaultWidth;}catch{return defaultWidth;}}
function savedClosed(){try{return localStorage.getItem('console.sidebar.closed')==='true';}catch{return false;}}
export function ConsoleLayout(){
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
 return <div className={`console-shell navigation-adjustable${!open?' navigation-closed':''}${dragging?' navigation-resizing':''}`} style={{'--sidebar-width':`${width}px`} as CSSProperties}>
 {open&&<Sidebar open={open} onClose={close} onNavigate={()=>{if(mobile)close();}} resizeHandle={!mobile&&<div className="sidebar-resize" role="separator" tabIndex={0} aria-label={t('Ajustar ancho del menú')} aria-orientation="vertical" aria-valuemin={minWidth} aria-valuemax={maxWidth} aria-valuenow={width} aria-controls="console-navigation" title={t('Arrastra para ajustar. Flechas para cambiar ancho; doble clic para restablecer.')} onPointerDown={e=>{if(e.button!==0)return;drag.current={x:e.clientX,width};e.currentTarget.setPointerCapture(e.pointerId);setDragging(true);e.preventDefault();}} onPointerMove={e=>{if(drag.current)setWidth(clamp(drag.current.width+e.clientX-drag.current.x));}} onPointerUp={e=>{drag.current=null;setDragging(false);if(e.currentTarget.hasPointerCapture(e.pointerId))e.currentTarget.releasePointerCapture(e.pointerId);}} onPointerCancel={()=>{drag.current=null;setDragging(false);}} onLostPointerCapture={()=>{drag.current=null;setDragging(false);}} onDoubleClick={()=>setWidth(defaultWidth)} onKeyDown={e=>{if(['ArrowLeft','ArrowRight','Home','End'].includes(e.key)){e.preventDefault();setWidth(w=>e.key==='Home'?minWidth:e.key==='End'?maxWidth:clamp(w+(e.key==='ArrowRight'?16:-16)));}}}/>}/>}
 {mobile&&open&&<button className="backdrop navigation-backdrop" onClick={close} aria-label={t('Cerrar menú')}/>}
 <div className="workspace"><Header menuOpen={open} menuButtonRef={menuButton} onMenu={()=>mobile?setMobileOpen(v=>!v):setClosed(v=>!v)}/><div className="content"><Breadcrumbs/><main><Outlet/></main></div></div>
 </div>;
}
