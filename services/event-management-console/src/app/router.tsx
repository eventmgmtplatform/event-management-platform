import { SecretsPage } from "../modules/secrets/SecretsPage";
import { NotificationsConfigPage } from "../modules/integrations/NotificationsConfigPage";
import { IntegrationPluginPage } from "../modules/integration-plugins/IntegrationPluginPage";
import { RoutingPage } from "../modules/routing/RoutingPage";
import { AiopsPage } from "../modules/aiops/AiopsPage";
import { PolicyPage } from "../modules/policy/PolicyPage";
import { CorrelationPage } from "../modules/correlation/CorrelationPage";
import { InventoryPage } from "../modules/inventory/InventoryPage";
import { BlackoutsPage } from "../modules/blackouts/BlackoutsPage";
import { EssAdminPage } from "../modules/ess/EssAdminPage";
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
  {path:"events",element:<EssAdminPage/>},
  {path:"correlation",element:<CorrelationPage/>},
  {path:"automations",element:<Placeholder title="Automatizaciones" description="Ejecuciones, reglas y seguimiento de acciones automatizadas."/>},
  {path:"criteria-filters",element:<CatalogPage key="filters" kind="filters"/>},
  {path:"customers",element:<CatalogPage key="customers" kind="customers"/>},
  {path:"middleware",element:<MiddlewarePage/>},
  {path:"blackouts",element:<BlackoutsPage/>},
  {path:"inventory-services",element:<InventoryPage/>},
  {path:"auto-suppression",element:<BlackoutsPage key="suppression" suppression/>},
  {path:"routing",element:<RoutingPage/>},
  {path:"policies",element:<PolicyPage/>},
  {path:"aiops-extensions",element:<AiopsPage/>},
  {path:"system/secrets",element:<SecretsPage/>},
  {path:"system/ticketing",element:<Placeholder title="Ticketing · Instancias" description="Estructura preparada para ServiceNow y GLPI. El formulario de configuración y sus selectores de MockSecrets se implementan en el siguiente bloque."/>},
  {path:"system/notifications",element:<NotificationsConfigPage/>},
  ...ticketingRoutes,
  {path:"notifications",element:<IntegrationPluginPage key="gnm" domain="gnm"/>},
  {path:"cacf",element:<IntegrationPluginPage key="cacf" domain="cacf"/>},
  {path:"administration",element:<AdministrationPage/>},
  {path:"administration/ess",element:<EssAdminPage/>},
  {path:"*",element:<Placeholder title="Página no encontrada" description="La ruta solicitada no pertenece al Console actual."/>}
]}]);
