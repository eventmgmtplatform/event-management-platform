# Event Management Platform — Changelog

[Índice por componente y política](docs/changelogs/README.md). Historia del repositorio disponible desde 2026-07-31; corte documental 2026-09-10. Se conservan SHA/mensajes de Git, sin atribuir releases ni resultados de ejecución.

## Unreleased — 2026-09-10

- `06f046f` consolidó la entrega publicada de la WebGUI de administración en
  `feature/os-13-01-webgui-mgmt`: navegación colapsable, i18n ES/EN, temas
  Actual/Kyndryl/IBM Carbon/LIVERPOOL, dashboards de plataforma y sistema,
  plugins de Ticketing/Notifications/CACF, MockSecrets, GLPI, Middleware/Kafka,
  Inventory/Enrichment, Blackouts (incluidos SCHEDULED y RECURRENT), Policies,
  Correlación, Routing, AutoSuppression, AIOps Extensions, ESS y Cliente.
  Incluye proxies same-origin, persistencia PostgreSQL, validaciones, fixtures y
  pruebas de integración; la publicación quedó sincronizada con `origin`.

- [Kafka administrable y reproducible](docs/kafka/README.md): CLI, documentación
  de Kafbat y paquete independiente con imágenes fijadas; certificación de producto pendiente.

- OS_11_01.IMP: coordinación durable optativa fatal → ticket → GNM → CACF → recuperación y cierres confirmados. [Contrato y runbook](docs/event-processor/lifecycle-orchestration.md); migraciones aditivas 020/021. Alcances por componente en el índice.

- Centralización de pruebas en testing, catálogo de casos y separación de evidencias.
- ESS/lifecycle, emisión de solicitudes de estado desde Processor y frontend administrativo quedaron incorporados en el commit consolidado; consultar los changelogs de los componentes para el alcance.
- Changelogs por componente reconstruidos y política permanente de mantenimiento.
- [OS_09_01.IMP](docs/workstreams/OS_09_01_IMP-orquestacion-happy-path.md): prompt para implementar el encadenamiento fatal → ticket → GNM → CACF → clear. Es trabajo especificado para otra tarea, no funcionalidad implementada.

## Cronología confirmada

| Fecha de autor | Commit | Cambio registrado (mensaje original) |
|---|---|---|
| 2026-09-09 | `887baeaf51f1` | feat(processor): add independent aiops CRUD and mock provider |
| 2026-09-09 | `068fae1be8ca` | feat(processor): persist correlation and group command decisions atomically |
| 2026-09-09 | `4ed49de5bccc` | feat(processor): integrate typed enrichment and versioned local inventory |
| 2026-09-09 | `d06fd735ff02` | feat(processor): add functional REST administration and versioned blackouts |
| 2026-09-09 | `a8d141c4e806` | build(event-processor): deploy versioned rules and verify configuration recovery |
| 2026-09-09 | `b059aaf8cdbe` | feat(event-processor): add versioned typed policy configuration |
| 2026-09-09 | `54aaa8de1d1e` | feat(event-processor): persist output retries and verify recovery |
| 2026-09-09 | `baf778df7160` | feat(event-processor): replace enrichment with durable processing foundation |
| 2026-09-09 | `425ecd32acc5` | docs(git): record main consolidation and audit boundaries |
| 2026-09-09 | `3e78b3a6ec5e` | chore(git): consolidate pending workstreams for main |
| 2026-09-08 | `1c3ede3e9d9a` | feat: add ecosystem service administration and master tests |
| 2026-09-08 | `3a657ae2332c` | docs(git): record CACF publication and library separation |
| 2026-09-08 | `3d45006cbf23` | feat(os-05-cacf): implement local automation foundation and code documentation |
| 2026-09-07 | `006543a7932d` | feat(os-00-04): establish Git governance baseline |
| 2026-09-07 | `fc51edcaa23a` | chore(integration): capture accumulated project workstreams |
| 2026-09-07 | `909f3b3712ea` | feat(d06): implement GNM notification core and Everbridge lifecycle |
| 2026-09-05 | `85af2ff1d7d3` | feat(console): add event management console foundation |
| 2026-09-05 | `b2475908289a` | feat(servicenow): add pull restart recovery coordinator |
| 2026-09-04 | `a20c7c0a71c1` | feat(servicenow): add persistent operational control |
| 2026-09-04 | `74d75b0f4dec` | feat(servicenow): add safe stale command reconciliation |
| 2026-09-03 | `1d383ffbdb58` | fix(servicenow): classify command id collisions |
| 2026-09-03 | `c3a3da450949` | feat(servicenow): add durable command idempotency |
| 2026-09-02 | `2c1d77275c7a` | feat(servicenow): add controlled retry policy |
| 2026-09-02 | `3f5280cdac10` | feat(servicenow): classify permanent and retryable failures |
| 2026-09-02 | `3bc9cdf2e1b9` | feat(servicenow): add Kafka-aware worker readiness |
| 2026-09-02 | `13bb979928a9` | feat(servicenow): add compatible result contract v1.1 |
| 2026-09-02 | `fb8f050b681d` | fix(servicenow): preserve event key in integration results |
| 2026-08-13 | `741f631080e1` | feat(os-01-01): add events enrichment pass-through |
| 2026-08-13 | `cff1e46e3a28` | feat(os-01-01): add enrichment engine foundation |
| 2026-08-12 | `4e72b25dc54a` | docs(os-01-01): document runtime and Zabbix contract |
| 2026-08-12 | `3927b0adac16` | test(os-01-01): cover native Zabbix normalization |
| 2026-08-12 | `cc23e2c58a55` | feat(os-01-01): add native Zabbix message bus contract |
| 2026-08-12 | `f8b4a43fdd51` | feat(os-01-01): add safe local runtime controls |
| 2026-08-12 | `2b138dbcbd59` | fix(os-08-14): harden runtime persistence and result idempotency |
| 2026-08-08 | `67c190ece54d` | feat(os-08-13): add continuous delivery foundation |
| 2026-08-06 | `1e13b6103547` | feat(cloud-build): add reusable Artifact Registry image pipelines |
| 2026-08-05 | `0814802073d0` | docs(services): document normalized build and runtime |
| 2026-08-05 | `8f3a8e5528cd` | build(services): normalize container build layout |
| 2026-08-04 | `39e6ff6fcafc` | build(services): align Quarkus and Maven versions |
| 2026-08-04 | `ea9530133f6f` | fix(terraform): revoke temporary Secret Manager admin from Cloud Build service agent |
| 2026-08-04 | `367d6696bc5d` | feat(terraform): adopt GitHub connection and link repository |
| 2026-08-04 | `b23b20375d17` | fix(terraform): grant temporary Secret Manager admin to Cloud Build service agent |
| 2026-08-04 | `7186231c67de` | fix(terraform): adopt Cloud Build service agent role |
| 2026-08-04 | `8e508809c165` | feat(terraform): add GCP Cloud Build foundation |
| 2026-08-04 | `c6899b3a4cb0` | fix(terraform): grant Cloud Build administration to deployer |
| 2026-08-04 | `3db90c3f0a99` | fix(terraform): manage Cloud Build APIs in bootstrap |
| 2026-08-04 | `c6e915d4f77a` | feat(terraform): add GCP Secret Manager module |
| 2026-08-04 | `35d6b40baa51` | fix(terraform): grant Secret Manager admin to deployer |
| 2026-08-04 | `de81511421a8` | fix(terraform): manage Secret Manager API in bootstrap |
| 2026-08-04 | `3dcd749baf12` | feat(terraform): add GCP Cloud Storage module |
| 2026-08-04 | `841af71617f9` | feat(terraform): grant Storage admin to deployer |
| 2026-08-04 | `94242ef73f5a` | chore(terraform): add OS_08_10 operational scripts |
| 2026-08-04 | `133af4258aa6` | feat(terraform): add GCP Artifact Registry module |
| 2026-08-04 | `25040e795edb` | feat(terraform): grant Artifact Registry admin to deployer |
| 2026-08-04 | `3aeb90ccbd12` | feat(terraform): add GCP networking module for dev |
| 2026-08-01 | `5a5ca507f1c8` | feat(terraform): add reusable IAM module |
| 2026-08-01 | `49f61570e9e7` | feat(gcp-bootstrap): enable Compute Engine API |
| 2026-08-01 | `7c0e54dce5fc` | feat(terraform): add project-common conventions module |
| 2026-08-01 | `c10a9a7e8c93` | feat(terraform): enable service account impersonation |
| 2026-07-31 | `4ed309ec2bed` | chore: configure dev Terraform GCS backend |
| 2026-07-31 | `7272150ed845` | feat: bootstrap Terraform remote state on GCS |
| 2026-07-31 | `30817ba8cd32` | chore: initialize event management platform repository |
