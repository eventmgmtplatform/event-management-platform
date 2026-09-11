export type NavigationIcon="dashboard"|"events"|"correlation"|"automation"|"ticket"|"settings"|"kafka";
export type NavigationItem={label:string;path:string;icon:NavigationIcon;status:"active"|"preview";description:string};
export type NavigationGroup={label:string;items:NavigationItem[]};

export const navigation:NavigationGroup[]=[
  {label:"Plataforma",items:[
    {label:"Dashboard general",path:"/dashboard",icon:"dashboard",status:"active",description:"Resumen operativo de la plataforma"},
    {label:"Event Management",path:"/events",icon:"events",status:"active",description:"Estado, ciclo de vida e historial de eventos"},
    {label:"Correlación",path:"/correlation",icon:"correlation",status:"active",description:"Relaciones, deduplicación y causa raíz"},
    {label:"Criterios y Filtros",path:"/criteria-filters",icon:"correlation",status:"active",description:"Criterios y destinos por cliente"},
    {label:"Middleware",path:"/middleware",icon:"kafka",status:"active",description:"Kafka y flujos del producto"},
    {label:"Blackouts",path:"/blackouts",icon:"events",status:"active",description:"Ventanas de mantenimiento"},
    {label:"Inventory Services",path:"/inventory-services",icon:"dashboard",status:"active",description:"Inventario de recursos de clientes"},
    {label:"Routing y comandos",path:"/routing",icon:"automation",status:"active",description:"Rutas y comandos del motor"},
    {label:"Policies",path:"/policies",icon:"settings",status:"active",description:"Políticas del producto"},
    {label:"AutoSuppression",path:"/auto-suppression",icon:"correlation",status:"active",description:"Supresión automática"},
  ]},
  {label:"Plugins",items:[
    {label:"Tickets",path:"/ticketing/tickets",icon:"ticket",status:"active",description:"Administración de tickets ITSM"},
    {label:"Notifications",path:"/notifications",icon:"events",status:"active",description:"GNM · Notifications"},
    {label:"CACF",path:"/cacf",icon:"automation",status:"active",description:"CACF · Automations"}
  ]},
  {label:"Sistema",items:[
    {label:"Administración",path:"/administration",icon:"settings",status:"active",description:"Configuración general del Console"},
    {label:"Ticketing",path:"/system/ticketing",icon:"ticket",status:"preview",description:"Instancias ServiceNow y GLPI"},
    {label:"Notifications",path:"/system/notifications",icon:"events",status:"preview",description:"Instancias GNM"},
    {label:"Automations",path:"/automations",icon:"automation",status:"preview",description:"Acciones y ejecuciones automatizadas"},
    {label:"AIOps Extensions",path:"/aiops-extensions",icon:"automation",status:"active",description:"Extensiones AIOps"},
    {label:"Cliente",path:"/customers",icon:"settings",status:"active",description:"Configuración de clientes"},
    {label:"Secrets",path:"/system/secrets",icon:"settings",status:"active",description:"MockSecrets local"}
  ]}
];
