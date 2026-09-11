# Correlación — aceptación backend local

El motor ATTRIBUTE/GROUP ya tiene reglas versionadas por `/api/v1/rules` y estado
durable en `event_processor.correlation_group`. No necesitó cambios de producción
para este corte. La identidad del evento se conserva; pertenencia y ciclo del grupo
son relaciones calculadas. Event State Service sigue siendo autoridad del evento.

La certificación reproducible está en:

```bash
python3 testing/certifications/processor-correlation-certification.py
```

Requiere runtime local, Gateway 8081, Processor 8082 y Docker/PostgreSQL. Crea tenant
único y reglas/eventos sintéticos; conserva auditoría y desactiva reglas propias ante
fallos. No registra rutas de integración. Evidencia y hashes: `evidences/correlation/`.

Comprueba API de alta/reintento, separación última/activa, revisión optimista,
validación semántica, aislamiento de lectura por tenant, retiro e historial.
La simulación sobre reglas activas verifica pertenencia, recuperación parcial/total,
reapertura, eventos tardíos/empatados, huérfanos, scope, ventana inclusiva y capacidad.
Verifica que simular no escriba grupos durables. En flujo real Gateway/Kafka verifica
dos miembros, cambio equivalente v1/v2 conservando groupId, recuperación, nuevo ciclo,
igualdad entre estado PostgreSQL y decisión, explain, publicación normalizada y
que desactivar detenga mutaciones sin borrar el grupo.

Además, CorrelationTest y CorrelationTransactionTest prueban semántica, concurrencia,
replay, colisiones, rollback conjunto de relaciones/auditoría/outbox/comandos y
retención del primer envelope. Las pruebas SQL requieren el laboratorio aislado
`jdbc:postgresql://127.0.0.1:15439/cacf_test`; no ejecutar sin ese parámetro y contar
omisiones como certificación. Usar copia de compilación si el runtime monta target.

Límites vigentes: ATTRIBUTE/GROUP, 8 reglas activas/tenant, ventana 1..86400 segundos,
1..4 campos String y hasta 32 miembros retenidos por ciclo. Expiración por llegada de
eventos; no barrido autónomo. Desactivar no resuelve grupos. No hay API de edición de
grupos ni endpoint especializado `/correlations`; administración mediante `/rules`.
DP-EP-04/07/08 mantienen extensiones/escala pendientes, DP-EP-01 seguridad diferida.

[Contrato del motor](correlation-suppression-commands.md) ·
[Prompt para frontend](frontend-handoffs/correlation.md).
La aceptación desde navegador sigue pendiente; esta certificación no la sustituye.
