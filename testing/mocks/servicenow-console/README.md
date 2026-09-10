# ServiceNow mock exclusivo del Console

Copia de las fixtures de `../servicenow`, al mismo nivel del mock original. Los archivos `mappings/` se conservan como referencia sin modificaciones. El runtime de esta copia implementa en Python un subconjunto stateful de Table API (GET/PATCH), en lugar de las respuestas estáticas WireMock del original. No pretende implementar toda la API de ServiceNow.

- Contenedor: `event-servicenow-console-mock`.
- Red y Compose: los existentes de Event Management.
- Sin puerto público; Nginx de la consola publica `/api/now/table/incident` en localhost:8090.
- Datos: volumen independiente `event-management-console-snow-data`, SQLite con transacciones y auditoría.
- Seed: los 12 tickets previos del frontend, con los mismos IDs y estados iniciales. Se inserta una sola vez; reiniciar no revierte los cierres.
- Ningún endpoint, volumen o configuración del mock original se modifica.

## Contrato implementado

GET `/api/now/table/incident?sysparm_query=numberLIKEINC0019284^state=1` devuelve `{ "result": [...] }`. GET por `sys_id` devuelve un único incidente. Búsquedas sin query devuelven los 12 registros. Filtros soportados: `number`, `state`, `short_description`, `cmdb_ci`, con `=` o `LIKE`, unidos por `^`. No admite consultas arbitrarias ni OR.

PATCH `/api/now/table/incident/console-INC0019284` recibe:

```json
{"state":"7","close_code":"Solved (Permanently)","close_notes":"Cierre validado desde la consola"}
```

Valida código, estado objetivo y nota de 10–4000 caracteres. Guarda estado, fecha, código y nota en una transacción. Un cierre repetido no duplica la auditoría. No permite crear, borrar o reabrir tickets.

Estados: Open=1, In Progress=2, Pending=3, Resolved=6, Closed=7. Failed=-1 es una extensión del mock para conservar la fila de fallo de integración de la demostración original; no es un estado estándar de incidentes ServiceNow.

## Pruebas

```bash
python3 -m unittest discover -s testing/mocks/servicenow-console/tests -v
```

Las pruebas usan bases temporales: verifican la semilla, los filtros, el ciclo buscar/cerrar/buscar, persistencia, idempotencia y rechazo de cierres inválidos. No acceden al mock de las otras pruebas.
