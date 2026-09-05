import { useState } from "react";
import { Outlet } from "react-router-dom";
import { Breadcrumbs } from "./Breadcrumbs";
import { Header } from "./Header";
import { Sidebar } from "./Sidebar";

export function ConsoleLayout(){const [menuOpen,setMenuOpen]=useState(false);return <div className="console-shell"><Sidebar open={menuOpen} onClose={()=>setMenuOpen(false)}/>{menuOpen&&<button className="backdrop mobile-only" onClick={()=>setMenuOpen(false)} aria-label="Cerrar navegación"/>}<div className="workspace"><Header onMenu={()=>setMenuOpen(true)}/><div className="content"><Breadcrumbs/><main><Outlet /></main></div></div></div>}
