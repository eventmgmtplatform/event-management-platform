import { BlackoutsPage } from "../modules/blackouts/BlackoutsPage";
import { EssAdminPage } from "../modules/ess/EssAdminPage";
import { ProductCatalogPage, type ProductKind } from "../modules/product/ProductCatalogPage";
import { MiddlewarePage } from "../modules/product/MiddlewarePage";
import { Navigate, createBrowserRouter } from "react-router-dom";
import { ConsoleLayout } from "../layout/ConsoleLayout";
import { HomePage } from "../modules/home/HomePage";
import { AdministrationPage } from "../modules/administration/AdministrationPage";
import { ticketingRoutes } from "../modules/ticketing/routes";

import { CatalogPage } from "../modules/catalog/CatalogPage";

const Placeholder=({title,description}:{title:string;description:string})=><section className="page empty-state"><div className="empty-icon">◇</div><span className="eyebrow">Módulo preparado</span><h1>{title}</h1><p>{description}</p><span className="preview-chip">Próximamente</span></section>;

export const router=createBrowserRouter([{path:"/",element:<ConsoleLayout/>,children:[
  {index:true,element:<Navigate to="dashboard" replace/>},
  {path:"dashboard",element:<HomePage/>},
  {path:"events",element:<Placeholder title="Eventos" description="Gestión del flujo y ciclo de vida de eventos."/>},
  {path:"correlation",element:<Placeholder title="Correlación" description="Deduplicación, agrupación, relaciones y causa raíz."/>},
  {path:"automations",element:<Placeholder title="Automatizaciones" description="Ejecuciones, reglas y seguimiento de acciones automatizadas."/>},
  {path:"criteria-filters",element:<CatalogPage key="filters" kind="filters"/>},
  {path:"customers",element:<CatalogPage key="customers" kind="customers"/>},
  {path:"middleware",element:<MiddlewarePage/>},
  {path:"blackouts",element:<BlackoutsPage/>},
  ...(["inventory-services","policies","aiops-extensions","auto-suppression"] as ProductKind[]).map(kind=>({path:kind,element:<ProductCatalogPage key={kind} kind={kind}/>})),
  ...ticketingRoutes,
  {path:"administration",element:<AdministrationPage/>},
  {path:"administration/ess",element:<EssAdminPage/>},
  {path:"*",element:<Placeholder title="Página no encontrada" description="La ruta solicitada no pertenece al Console actual."/>}
]}]);
