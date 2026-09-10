# Administración local de EventManagementOpenSource

Actualización de consolidación: sólo el runtime principal queda operativo.
Las instrucciones históricas de laboratorios siguientes requieren recuperación
explícita; véase [consolidación](environment-consolidation.md).
`happy-path` usa shared por defecto y `emctl` ya no arranca CACF aislado.


Kafka dispone de [administración y paquete reproducible](kafka/README.md),
[manual CLI del producto](kafka/cli.md) y [Web UI/configuración](kafka/configuration-and-webui.md).
Las acciones `emctl kafka prepare/install` utilizan un paquete aislado explícito;
`config/inventory/topics/groups/verify/inspect` agregan diagnóstico a las acciones existentes.

Rama: `codex/eventmanagement-service-administration`. Base CACF disponible:
`3d45006`. Se amplía el controlador existente `scripts/eventmanagement-services.sh`;
`scripts/emctl` continúa siendo su enlace simbólico, sin duplicar implementación.

El [inventario de APIs administrativas](administration-api-inventory.md) distingue
control de contenedores, administración funcional y salud por componente.

## Alcance

Las acciones globales administran los dos proyectos Compose existentes:

- `local`: plataforma principal, GNM, ServiceNow mock, PostgreSQL, Kafka,
  OpenSearch, gateway, event-processor, worker, state service y consolas.
- `cacf-certification`: worker CACF, NEXT mock, ServiceNow mock, PostgreSQL y Kafka
  del laboratorio definido por OS-05. Conserva sus puertos, datos y aislamiento.

CACF está implementado dentro de integration-worker; no existe un microservicio
CACF separado. La base principal ahora configura CACF/GNM con mocks locales; véase
[activación compartida](cacf/shared-activation.md). No suministra credenciales NEXT productivas. Para esa activación siguen
aplicando `docs/cacf/operational-runbook.md` y su migración explícita.

```bash
bash scripts/emctl validate
bash scripts/emctl services
bash scripts/emctl status
bash scripts/emctl stop
bash scripts/emctl start
bash scripts/emctl health
python3 testing/certifications/eventmanagement-test.py
```

Las llamadas por servicio conservan el runtime principal como destino:

```bash
bash scripts/emctl integration-worker restart
EVENTMANAGEMENT_RUNTIME=cacf-certification bash scripts/emctl integration-worker restart
EVENTMANAGEMENT_RUNTIME=local bash scripts/emctl status
```

`EVENTMANAGEMENT_RUNTIME=ecosystem` es el valor global predeterminado.
`stop` conserva contenedores y volúmenes. `kafka-init` con salida 0 es correcto.
Los servicios sin healthcheck se reportan como `health=none`: estar running no
certifica su contrato funcional; la prueba CACF verifica las interacciones mock.
El inventario Compose completo debe pertenecer al orden administrado; un nuevo
servicio omitido bloquea las operaciones antes de detener contenedores.

## Prueba maestra

Requisitos: Docker/Compose accesible, imágenes construidas y ambos proyectos
preparados según sus runbooks, Python 3 y jq. El script detiene y enciende ambos
entornos; ejecutarlo durante una ventana local de pruebas. `start` mantiene el
contrato existente `--no-build`; reconstruir con `reload` cuando cambie el código.

1. Valida ambos contratos antes del apagado.
2. Captura status, ejecuta stop y comprueba que los contenedores se detuvieron.
3. Ejecuta start por dependencias y health para todos los servicios.
4. Envía los fixtures Zabbix OPEN/CLOSE existentes, con Node/hostname únicos.
5. Comprueba HTTP 202, ambos eventId en `events.normalized`, lifecycle, eventKey
   compartida y marca `PENDING_RULES` conservada por la base Event Processor. Lee particiones
   desde offsets previos; no modifica grupos consumidores de aplicaciones.
6. Reutiliza `testing/certifications/cacf-local-certification.py`: CREATE, ACK, TKTUPDATE,
   reinicio, duplicados, timeout, callback tardío, UNKNOWN, Kafka y ServiceNow.
7. Revalida salud y escribe `evidences/testing/lifecycle/<UTC>/report.json`.

Un fallo termina con código distinto de cero y reporte FAIL; no continúa enviando
simulaciones después de un fallo de arranque. Los datos sintéticos se conservan.
No se eliminan volúmenes ni se administran contenedores de otros proyectos.

## Límites del diseño existente

OS_11 ya implementa ticket → GNM → CACF → cierres y tiene evidencia en su
laboratorio aislado. Eso no acredita su promoción al runtime compartido. La especificación está en
[UC-001](../testing/cases/UC-001-happy-path.md); esta prueba maestra histórica
certifica los recorridos Gateway/Processor y CACF separadamente.
DP-22: la suite CACF existente no certifica proyección en event-state-service.
Las consolas, PostgreSQL y OpenSearch reciben verificación de salud; el resultado
no equivale a certificar cada función de negocio ni integraciones productivas.

## Evolución Event Processor — 2026-09-09

Event Processor reemplaza enrichment-engine en 8082. El controlador administra
`event-processor`; el grupo Kafka sigue siendo enrichment-engine para conservar
offsets. Aplicar primero la migración aditiva 009 a bases existentes. Véase
[runbook del processor](../services/event-processor/README.md). El contenedor
anterior detenido puede aparecer como orphan mientras se conserva para rollback.

## Certificación recurrente de Event State Service

`bash scripts/emctl event-state-service test` ejecuta el escenario integral con
un tenant sintético y ServiceNow mock. Emite PASS/FAIL, checks y ruta al reporte
JSON. Para añadir reinicio y replay: `python3 testing/certifications/event-state-certification.py --restart`.
Fixture compartida, prerrequisitos y límites en `docs/event-state-service/README.md`.
