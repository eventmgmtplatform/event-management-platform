export type NavigationIcon="dashboard"|"events"|"correlation"|"automation"|"ticket"|"settings";
export type NavigationItem={label:string;path:string;icon:NavigationIcon;status:"active"|"preview";description:string};
export type NavigationGroup={label:string;items:NavigationItem[]};

export const navigation:NavigationGroup[]=[
  {label:"Plataforma",items:[
    {label:"Dashboard general",path:"/dashboard",icon:"dashboard",status:"active",description:"Resumen operativo de la plataforma"},
    {label:"Eventos",path:"/events",icon:"events",status:"preview",description:"Flujo y ciclo de vida de eventos"},
    {label:"Correlación",path:"/correlation",icon:"correlation",status:"preview",description:"Relaciones, deduplicación y causa raíz"},
    {label:"Automatizaciones",path:"/automations",icon:"automation",status:"preview",description:"Acciones y ejecuciones automatizadas"}
  ]},
  {label:"Plugins",items:[
    {label:"Tickets",path:"/ticketing/tickets",icon:"ticket",status:"active",description:"Administración de tickets ITSM"}
  ]},
  {label:"Sistema",items:[
    {label:"Administración",path:"/administration",icon:"settings",status:"preview",description:"Configuración general del Console"}
  ]}
];
