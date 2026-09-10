# Changelogs por componente

Corte documental: **2026-09-10**. Historia reconstruida desde el primer commit local disponible (**2026-07-31**, `30817ba`). Se recorrieron todas las referencias Git locales; no se consultaron remotos ni se inventó historia anterior. El repositorio no es shallow.

Las fechas históricas son fechas de autor conservadas por Git, con su zona original; no prueban cuándo se desplegó o comenzó a desarrollarse una función. Un commit que incorpora trabajo acumulado no permite desglosarlo por fechas anteriores. La entrada Unreleased describe trabajo observado sin commit al corte y debe revisarse antes de publicar.

## Índice canónico

| Componente | Changelog |
|---|---|
| event-gateway | [Historial](../../services/event-gateway/CHANGELOG.md) |
| event-processor | [Historial](../../services/event-processor/CHANGELOG.md) |
| event-state-service | [Historial](../../services/event-state-service/CHANGELOG.md) |
| integration-worker | [Historial](../../services/integration-worker/CHANGELOG.md) |
| event-management-console | [Historial](../../services/event-management-console/CHANGELOG.md) |
| itsm-ticketing-dashboard | [Historial](../../services/itsm-ticketing-dashboard/CHANGELOG.md) |
| enrichment-engine | [Historial](../../services/enrichment-engine/CHANGELOG.md) |
| ServiceNow | [Historial](../servicenow/CHANGELOG.md) |
| GNM / Everbridge | [Historial](../gnm/CHANGELOG.md) |
| CACF / NEXT | [Historial](../cacf/CHANGELOG.md) |
| Infraestructura local y cloud | [Historial](../../infrastructure/CHANGELOG.md) |
| PostgreSQL / migraciones | [Historial](../../infrastructure/postgres/CHANGELOG.md) |
| Kafka / topics | [Historial](../../infrastructure/kafka/CHANGELOG.md) |
| Terraform / convenciones | [Historial](../../infrastructure/terraform/CHANGELOG.md) |
| Terraform GCP | [Historial](../../infrastructure/gcp/terraform/CHANGELOG.md) |
| GCP / artifact-registry | [Historial](../../infrastructure/gcp/terraform/modules/artifact-registry/CHANGELOG.md) |
| GCP / cloud-build | [Historial](../../infrastructure/gcp/terraform/modules/cloud-build/CHANGELOG.md) |
| GCP / cloud-storage | [Historial](../../infrastructure/gcp/terraform/modules/cloud-storage/CHANGELOG.md) |
| GCP / continuous-delivery | [Historial](../../infrastructure/gcp/terraform/modules/continuous-delivery/CHANGELOG.md) |
| GCP / iam | [Historial](../../infrastructure/gcp/terraform/modules/iam/CHANGELOG.md) |
| GCP / networking | [Historial](../../infrastructure/gcp/terraform/modules/networking/CHANGELOG.md) |
| GCP / secret-manager | [Historial](../../infrastructure/gcp/terraform/modules/secret-manager/CHANGELOG.md) |
| GCP / bootstrap | [Historial](../../infrastructure/gcp/terraform/bootstrap/CHANGELOG.md) |
| GCP / ambiente dev | [Historial](../../infrastructure/gcp/terraform/environments/dev/CHANGELOG.md) |
| Terraform / project-common | [Historial](../../infrastructure/terraform/modules/common/project-common/CHANGELOG.md) |
| Despliegue / CI-CD | [Historial](../../deploy/CHANGELOG.md) |
| Operación / scripts | [Historial](../../scripts/CHANGELOG.md) |
| Contratos compartidos | [Historial](../../config/CHANGELOG.md) |
| Gobierno Git | [Historial](../git/CHANGELOG.md) |
| Documentación transversal | [Historial](../CHANGELOG.md) |
| Testing | [Historial](../../testing/CHANGELOG.md) |

## Cómo mantenerlo

1. Actualizar el changelog del componente en el mismo cambio que modifica su código, contrato o configuración. Para varios componentes, registrar el impacto específico en cada uno.
2. Usar Unreleased con fecha de edición y categorías cuando ayuden: Agregado, Modificado, Corregido, Retirado, Migraciones/compatibilidad. Explicar qué cambia y por qué, sin afirmar que se publicó.
3. Al confirmar/publicar, enlazar el SHA real y mover la entrada al hito correspondiente. No inventar versiones semánticas ni fechas de release. Mantener sólo versiones documentadas.
4. Mantener este índice y el CHANGELOG raíz para cambios transversales. Las rutas antiguas deben apuntar al historial canónico.
5. Guardar resultados de pruebas, conteos, logs, hashes de ejecución y capturas exclusivamente en evidences/, ignorado por Git. El changelog puede describir una prueba agregada o la ruta para reproducirla, pero no sus resultados.
6. Los changelogs padre (Worker, infraestructura, documentación) agregan el alcance de sus subcomponentes; la repetición de un SHA en varios archivos es intencional. Las tablas/secciones de historia son trazabilidad por rutas modificadas. El mensaje original de un commit transversal se conserva como fuente; no implica que toda su funcionalidad corresponda a cada componente listado. Las rutas históricas pueden estar retiradas o movidas.

## Plantilla de entrada nueva

OEM Dashboards: [UI](../../services/oem-dashboards/CHANGELOG.md) ·
[API y CLI](../../services/oem-dashboards-api/CHANGELOG.md).

```markdown
## Unreleased — AAAA-MM-DD

### Modificado

- Cambio concreto y motivo; API/contrato/configuración afectados.

### Migraciones y compatibilidad

- Migración requerida, orden y efecto; estrategia de compatibilidad o reversión.

### Referencias

- Caso de uso o decisión de arquitectura; SHA al existir.
```

Los respaldos de los changelogs previos y el inventario de reconstrucción se conservan localmente en `evidences/changelog-reconstruction/2026-09-10/`. Su existencia no es una certificación funcional del producto.
