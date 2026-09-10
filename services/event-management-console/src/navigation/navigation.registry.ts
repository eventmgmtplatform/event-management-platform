export type NavigationIcon="dashboard"|"events"|"correlation"|"automation"|"ticket"|"settings"|"kafka";
export type NavigationItem={label:string;path:string;icon:NavigationIcon;status:"active"|"preview";description:string};
export type NavigationGroup={label:string;items:NavigationItem[]};

export const navigation:NavigationGroup[]=[
  {label:"Plataforma",items:[
    {label:"Dashboard general",path:"/dashboard",icon:"dashboard",status:"active",description:"Resumen operativo de la plataforma"},
    {label:"Eventos",path:"/events",icon:"events",status:"preview",description:"Flujo y ciclo de vida de eventos"},
    {label:"Correlación",path:"/correlation",icon:"correlation",status:"preview",description:"Relaciones, deduplicación y causa raíz"},
    {label:"Automatizaciones",path:"/automations",icon:"automation",status:"preview",description:"Acciones y ejecuciones automatizadas"},
    {label:"Criterios y Filtros",path:"/criteria-filters",icon:"correlation",status:"active",description:"Criterios y destinos por cliente"},
    {label:"Middleware",path:"/middleware",icon:"kafka",status:"active",description:"Kafka y flujos del producto"},
    {label:"Blackouts",path:"/blackouts",icon:"events",status:"active",description:"Ventanas de mantenimiento"},
    {label:"Inventory Services",path:"/inventory-services",icon:"dashboard",status:"active",description:"Inventario de recursos de clientes"},
    {label:"Policies",path:"/policies",icon:"settings",status:"active",description:"Políticas del producto"},
    {label:"AIOps Extensions",path:"/aiops-extensions",icon:"automation",status:"active",description:"Extensiones AIOps"},
    {label:"AutoSuppression",path:"/auto-suppression",icon:"correlation",status:"active",description:"Supresión automática"},
    {label:"Cliente",path:"/customers",icon:"settings",status:"active",description:"Configuración de clientes"}
  ]},
  {label:"Plugins",items:[
    {label:"Tickets",path:"/ticketing/tickets",icon:"ticket",status:"active",description:"Administración de tickets ITSM"}
  ]},
  {label:"Sistema",items:[
    {label:"Administración",path:"/administration",icon:"settings",status:"active",description:"Configuración general del Console"}
  ]}
];
