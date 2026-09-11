# Documentación de Event Management Platform

Este índice reúne la documentación operativa, técnica y de desarrollo del
proyecto. La documentación específica de cada servicio permanece junto a su
código; aquí se describe cómo encontrarla y cómo relacionarla.

## Recorridos principales

- [Arquitectura](architecture/README.md): componentes, flujos, integraciones y despliegue.
- [API y OpenAPI](api/README.md): catálogo de APIs, proxy same-origin y contrato OpenAPI.
- [Desarrollo](development/README.md): preparación local, estándares, pruebas y depuración.
- [Operaciones](operations/README.md): instalación, configuración, despliegue, observabilidad y recuperación.
- [ADRs](adr/README.md): decisiones técnicas y enlaces a decisiones por componente.
- [Releases](releases/README.md): versionado, cambios y proceso de publicación.
- [Defect prevention documental](defect-prevention-documentation.md): pendientes de gobierno y baseline formal.
- [Changelogs](changelogs/README.md): historial por componente y política de mantenimiento.

## Documentación por dominio

- [Event Processor](event-processor/implementation-status.md)
- [Event State Service](event-state-service/README.md)
- [Dashboards](dashboards/architecture.md)
- [Kafka](kafka/README.md)
- [CACF](cacf/README.md)
- [ESS](service-administration.md)
- [Integraciones](architecture/system-integration-instances.md)
- [Testing](../testing/README.md)

La documentación no afirma que un servicio esté desplegado en un ambiente:
para ello se deben consultar los manifiestos, health checks y evidencias del
ambiente correspondiente.
