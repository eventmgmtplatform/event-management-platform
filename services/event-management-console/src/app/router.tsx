import { Navigate, createBrowserRouter } from "react-router-dom";
import { ConsoleLayout } from "../layout/ConsoleLayout";
import { HomePage } from "../modules/home/HomePage";
import { ticketingRoutes } from "../modules/ticketing/routes";

const Placeholder=({title,description}:{title:string;description:string})=><section className="page empty-state"><div className="empty-icon">◇</div><span className="eyebrow">Módulo preparado</span><h1>{title}</h1><p>{description}</p><span className="preview-chip">Próximamente</span></section>;

export const router=createBrowserRouter([{path:"/",element:<ConsoleLayout/>,children:[
  {index:true,element:<Navigate to="dashboard" replace/>},
  {path:"dashboard",element:<HomePage/>},
  {path:"events",element:<Placeholder title="Eventos" description="Gestión del flujo y ciclo de vida de eventos."/>},
  {path:"correlation",element:<Placeholder title="Correlación" description="Deduplicación, agrupación, relaciones y causa raíz."/>},
  {path:"automations",element:<Placeholder title="Automatizaciones" description="Ejecuciones, reglas y seguimiento de acciones automatizadas."/>},
  ...ticketingRoutes,
  {path:"administration",element:<Placeholder title="Administración" description="Configuración transversal del Event Management Console."/>},
  {path:"*",element:<Placeholder title="Página no encontrada" description="La ruta solicitada no pertenece al Console actual."/>}
]}]);
