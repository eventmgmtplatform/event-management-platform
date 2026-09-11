# Event Management OpenSource

Documentación técnica de la plataforma `event-mgmt-opensource`.

## Objetivo

Construir una plataforma open source para recibir, normalizar, procesar, integrar, consolidar y consultar eventos operativos.

## Arquitectura principal

```mermaid
flowchart LR
    A[Fuentes de eventos] --> B[event-gateway]
    B --> C[(events.raw)]
    C --> D[event-processor]
    D --> E[(integration.commands)]
    E --> F[integration-worker]
    F --> G[Integraciones externas]
    F --> H[(integration.results)]
    H --> I[event-state-service]
    I --> J[(PostgreSQL)]
    I --> K[(OpenSearch)]
Comenzar
Introducción
Desarrollo local
Primer evento
Arquitectura
Visión general
Componentes
Arquitectura de datos
Seguridad
Operación
Runbook local
Observabilidad
Gestión de incidentes
Proyecto
Estado actual
Roadmap
Deuda técnica
