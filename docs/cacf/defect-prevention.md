# Defect prevention — pendientes fuera de la liberación CACF local

Base de descubrimiento: `006543a7932d5e05d4694a280ed1eec23f8786d5`.
Estado inicial de todos los registros: **PENDIENTE**.

Esta lista conserva el trabajo diferido; no autoriza correcciones adicionales.
Un hallazgo solo pasa al alcance CACF si bloquea un requisito de esta liberación.

| ID | Hallazgo / trabajo diferido | Evidencia en repositorio | Condición para retomarlo |
|---|---|---|---|
| DP-01 | Cloud Build omite pruebas y carece de gates de contratos/seguridad | `cloudbuild.yaml`, Dockerfile del worker | Liberación cloud |
| DP-02 | Inventario CD desactualizado respecto a Compose; faltan dependencias y componentes | `infrastructure/gcp/terraform/environments/dev/continuous-delivery.tf` | Retomar Terraform/CD |
| DP-03 | Promoción, PR, protecciones, CODEOWNERS y versionado global pendientes | `docs/git/README.md` | Gobernanza global |
| DP-04 | GNM publica éxitos sin campos exigidos por el consumidor de resultados | `GnmLaunchResultProcessor`, `IntegrationResultStateProcessor` | Certificación GNM; escalar si bloquea una prueba CACF compartida |
| DP-05 | Mock ServiceNow permisivo; CREATE no construye el mapeo esperado por lookup | `ServiceNowCommandProcessor`, `CamelServiceNowLookupClient`, mock CREATE | Certificación ServiceNow; corregir solo lo requerido por acciones CACF |
| DP-06 | Publicación de proveedores existentes sin outbox; documentación promete más garantías | `integration-worker.xml`, paquete SN-02.9E | Durabilidad de proveedores existentes; CACF tendrá su propia garantía |
| DP-07 | Renovación de lease descrita pero no implementada periódicamente | `JdbcIntegrationCommandLedger`, paquete SN-02.9E | Recuperación ServiceNow/GNM |
| DP-08 | Variables Compose de tópicos no coinciden con las propiedades del worker | `infrastructure/docker-compose.yml`, `application.properties` | Corregir si se necesita configurar tópicos CACF |
| DP-09 | GNM mock ausente del orden operativo y dependencias | `scripts/eventmanagement-services.sh`, Compose | Operación GNM |
| DP-10 | README de worker/gateway y documentos de laboratorio desactualizados | READMEs de servicios, `docs/labs/os-01-01` | Mantenimiento documental de esos componentes |
| DP-11 | Manejo común de errores acoplado a ServiceNow; correlationId no propagado | `IntegrationFailureProcessor`, `IntegrationCommandProcessor` | Contrato común; aislar CACF de esas suposiciones |
| DP-12 | Diferencia de tamaño de event_key: 128 en proyección y 256 en ledger | SQL 002 y 004 | Evolución de contratos; validar límite compatible en CACF |
| DP-13 | Sin consumidor de events.normalized ni productor automático de comandos | `EnrichmentEngineRoute`, tópicos | Orquestación de eventos; pruebas locales CACF usarán comandos controlados |
| DP-14 | Sin runner global de migraciones para volúmenes existentes | `infrastructure/postgres/init` | Migraciones globales; CACF requiere un mecanismo local acotado |
| DP-15 | Sin métricas/tracing/logging estructurado transversal | POMs y configuración | Observabilidad de plataforma; instrumentar solo CACF en este alcance |
| DP-16 | Sin destino Kubernetes/Helm ni despliegue cloud implementado | `deploy/README.md` | Trabajo de runtime cloud |
| DP-17 | Frontera event-mgmt-docs/Pages/ADRs globales no documentada; README raíz vacío | `README.md`, `docs/` | Gobernanza documental |
| DP-18 | Política Terraform contiene referencia histórica a remoto sin configurar | `infrastructure/terraform/policies/module-versioning.md` | Mantenimiento Terraform |
| DP-19 | Autenticación productiva, rotación de defaults y escaneo global pendientes | Configuraciones locales y ADRs del laboratorio | Endurecimiento productivo; credenciales NEXT deben externalizarse |

## Pendientes para una siguiente certificación

- DP-23: interpretar y certificar StatusCode/Acknowledge de la respuesta síncrona
  NEXT; actualmente se comprueba HTTP 2xx y XML seguro, sin aceptación funcional.
- DP-24: revisar semántica del status del resultado: UNKNOWN/SUBMISSION_FAILED
  publican SUCCESS; consumir outcome/state/requiresReview hasta evolucionar el contrato.
- DP-25: completar validación de ticket.number en admisión, cuerpo nulo de PUT,
  normalización uniforme, correlación estricta de TransactionNumber y propagación
  de correlationId/metadatos estructurados; preservar contratos al retomarlo.
- DP-26: certificar recreación/pérdida de broker y política de retención de evidencia;
  el Compose aislado no declara volumen Kafka y la prueba actual reinicia el worker.

- DP-20: contrastar fixtures sintéticos y catálogo de respuestas con evidencia
  real de NEXT/ServiceNow antes de conectar proveedores reales.
- DP-21: confirmar política de cierre ITSM y necesidad de TKTUPDATE_CLOSE;
  REMEDIATED genera una nota en esta liberación local.
- DP-22: ampliar la certificación hasta la proyección de event-state-service;
  la prueba aislada actual cubre admisión Kafka y acciones ServiceNow.

## Dependencias CACF implementadas y verificadas localmente

- Adaptador XML y callbacks/TKTUPDATE derivados del contrato recuperado;
  certificación externa registrada en DP-20.
- Persistencia, correlación, duplicados, callbacks tardíos y vencimiento durable.
- Publicación durable de resultados CACF.
- Acciones necesarias mediante ServiceNow Core Foundation, sin segunda integración.
- Pruebas locales que demuestren esas garantías.
