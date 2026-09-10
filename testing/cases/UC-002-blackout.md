# UC-002 — Blackout registrado y evento coincidente

Ejecutar `python3 testing/run.py blackout` contra el runtime local. Se prueba la
frontera REST de administración + Gateway + Kafka + Processor + PostgreSQL/outbox.
La validación de ESS y proveedores pertenece a UC-001/UC-014.

1. Crear un tenant/nodo y registrar blackout SCHEDULED con ventana relativa al reloj
   actual, versión 1, scope exacto y metadata de testing; activar usando revisión/ETag.
2. Activar correlación y ruta de ticket para el nodo. Una simulación sin blackout
   debe producir un candidato de ticket; así se evita una validación vacía sin routing.
   Ingresar un fatal real por Gateway y esperar su processing_record y publicación
   normalizada. Comparar `/explain` con la evidencia persistida.
3. Comprobar etapa Blackout MATCH con ID de la regla y SUPPRESS_INTEGRATIONS,
   12 etapas, cero comandos y cero outbox de integración del tenant.
4. Ingresar clear durante blackout; conservar evaluación/correlación y auditoría.
5. Probar nodo fuera del scope y regla desactivada: NO_MATCH / CONTINUE. Desactivar
   primero la ruta de control para no crear tickets al quitar el blackout.
6. Desactivar reglas propias en `finally`, incluso ante fallos; conservar eventos
   y auditoría sintéticos con tenant único. Un fallo de limpieza falla la ejecución.

La ventana es `[validFrom, validTo)`. Límites exactos, tenant distinto, regla
inválida, concurrencia y repetición de registro están cubiertos por `BlackoutTest`
y `AdminApiIT`; no se espera al reloj real para probar los límites.

Criterios futuros de ampliación: blackouts superpuestos, clear después de terminar
la ventana, mantenimiento abierto hasta desactivar, cambio de versión durante
procesamiento y recepción concurrente. RECURRING no está implementado: validar
rechazo, sin simular soporte inexistente.

Con rutas lifecycle e integraciones ya abiertas, el blackout conserva recuperación
y deja cierres en SUPPRESSED_PENDING. No cancela comandos ya emitidos. Una nueva
recuperación elegible después del blackout continúa el cierre; el vencimiento de
la ventana por sí solo no reevalúa. LifecycleTransactionTest cubre esa política
transaccional; UC-002 conserva su alcance E2E de Processor, no certifica por sí solo
ese cierre diferido con proveedores. [Contrato](../../docs/event-processor/lifecycle-orchestration.md).
