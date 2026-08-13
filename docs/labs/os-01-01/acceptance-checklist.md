# OS_01_01 — Checklist de aceptación

## Runtime e idempotencia

- [x] Script global e individual `start|stop|status|reload`.
- [x] `.env` resuelto desde la raíz del proyecto.
- [x] `event-state-service` saludable en `/health/ready`.
- [x] PostgreSQL `UP`.
- [x] Reentrega idéntica mantiene versión y claim.
- [x] Idempotencia persiste tras recreación.
- [x] OpenSearch permanece sincronizado.
- [x] Colisión de `resultId` detectada en tópico aislado.
- [x] Mensaje conflictivo sin commit y con lag esperado.
- [x] Recursos temporales eliminados.
- [x] Consumidor principal con lag total cero.

## Contrato Zabbix

- [x] Schema JSON con 18 campos.
- [x] Fixtures de apertura y recuperación.
- [x] `OPEN` para `Type=1/InstanceValue=0`.
- [x] `CLOSE` para `Type=0/InstanceValue=1`.
- [x] Severidades de origen y efectiva.
- [x] Identidades canónica y heredada.
- [x] Kafka key canónica compartida.
- [x] `SubComponent` convertido a arreglo.
- [x] Payload original conservado.
- [x] SDC y SD2 separados.
- [x] Lifecycle inválido rechazado con HTTP 400.
- [x] Compatibilidad v1.0.
- [x] 8 pruebas JUnit exitosas.

## Commits

- [x] `2b138db` — runtime persistence and result idempotency.
- [x] `f8b4a43` — safe local runtime controls.
- [x] `cc23e2c` — native Zabbix Message Bus contract.
- [x] `3927b0a` — automated normalization tests.

## Pendiente

- [ ] Procesador `events.raw -> events.normalized`.
- [ ] Persistencia del evento Zabbix.
- [ ] Enriquecimiento e inventario SDC.
- [ ] Deduplicación y correlación.
- [ ] Blackout y maintenance.
- [ ] DLQ/reintentos para poison pills.
- [ ] Migraciones repetibles.
- [ ] Autenticación productiva.
- [ ] Rotación de credencial histórica.
