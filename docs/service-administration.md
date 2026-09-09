# Administración local de EventManagementOpenSource

Rama: `codex/eventmanagement-service-administration`. Base CACF disponible:
`3d45006`. Se amplía el controlador existente `scripts/eventmanagement-services.sh`;
`scripts/emctl` continúa siendo su enlace simbólico, sin duplicar implementación.

## Alcance

Las acciones globales administran los dos proyectos Compose existentes:

- `local`: plataforma principal, GNM, ServiceNow mock, PostgreSQL, Kafka,
  OpenSearch, gateway, event-processor, worker, state service y consolas.
- `cacf-certification`: worker CACF, NEXT mock, ServiceNow mock, PostgreSQL y Kafka
  del laboratorio definido por OS-05. Conserva sus puertos, datos y aislamiento.

CACF está implementado dentro de integration-worker; no existe un microservicio
CACF separado. La administración conjunta no activa el overlay CACF en la base
principal ni suministra credenciales NEXT productivas. Para esa activación siguen
aplicando `docs/cacf/operational-runbook.md` y su migración explícita.

```bash
bash scripts/emctl validate
bash scripts/emctl services
bash scripts/emctl status
bash scripts/emctl stop
bash scripts/emctl start
bash scripts/emctl health
python3 scripts/eventmanagement-test.py
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
6. Reutiliza `scripts/cacf-local-certification.py`: CREATE, ACK, TKTUPDATE,
   reinicio, duplicados, timeout, callback tardío, UNKNOWN, Kafka y ServiceNow.
7. Revalida salud y escribe `artifacts/service-administration/<UTC>/report.json`.

Un fallo termina con código distinto de cero y reporte FAIL; no continúa enviando
simulaciones después de un fallo de arranque. Los datos sintéticos se conservan.
No se eliminan volúmenes ni se administran contenedores de otros proyectos.

## Límites del diseño existente

DP-13: aún no existe orquestador de `events.normalized` a `integration.commands`.
Por eso se certifican los recorridos gateway/event-processor y CACF separadamente.
DP-22: la suite CACF existente no certifica proyección en event-state-service.
Las consolas, PostgreSQL y OpenSearch reciben verificación de salud; el resultado
no equivale a certificar cada función de negocio ni integraciones productivas.

## Evolución Event Processor — 2026-09-09

Event Processor reemplaza enrichment-engine en 8082. El controlador administra
`event-processor`; el grupo Kafka sigue siendo enrichment-engine para conservar
offsets. Aplicar primero la migración aditiva 009 a bases existentes. Véase
[runbook del processor](../services/event-processor/README.md). El contenedor
anterior detenido puede aparecer como orphan mientras se conserva para rollback.
