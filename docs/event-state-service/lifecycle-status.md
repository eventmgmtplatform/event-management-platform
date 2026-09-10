# Ciclo de vida — estado de trabajo

Fecha: 2026-09-10. Rama: `feature/os-05-core-event-state-service`.
**Implementación en desarrollo, sin despliegue ni certificación E2E de este incremento.**

## Avance

- Processor: encoder de `events.state.requested`, en la transacción que registra
  decisiones aceptadas y su outbox. Las entradas DLQ no generan solicitud.
- ESS: consumer explícito, ledger de solicitudes, historial de transiciones,
  OPEN/CLOSE/reapertura, severidad dual, tally e identidad inicial estable.
- Requests repetidas: DUPLICATE; timestamps antiguos o empatados: STALE.
- Migración aditiva 017 preparada; no aplicada al runtime.
- Proyección serializada por clave y lectura del último estado confirmado.
- Script de despliegue coordinado Processor/ESS preparado, sin ejecutar.
- Contrato y límites: `docs/event-state-service/lifecycle-contract.md`.

## Validación realizada antes de la pausa

- ESS: 13 unitarias y 15 de integración JTA; 28 en total, sin fallos ni omitidas.
  Evidencia: `evidence/os-05-ess/lifecycle-unit.log` y `lifecycle-verify.log`.
- Processor: 78 pruebas, 1 fallo. La nueva prueba
  stateRequestSharesTheDecisionTransactionAndReplayBoundary recibe un fixture
  cuyo originalJson no es JSON válido; el encoder falla antes del punto de fallo
  transaccional que se pretendía probar. No se declara certificado el incremento.
  Evidencia: `evidence/os-05-ess/processor-lifecycle-tests.log`.

## Pausa solicitada

Durante la ejecución se observó una reorganización concurrente hacia `testing/`
y cambios de Compose y consola. El usuario indicó **esperar antes de adaptar
las pruebas**. Se detuvo esa adaptación y el despliegue dependiente. El runner
contiene parte de la ampliación de ciclo de vida; falta completar y verificar
la plantilla y rutas tras la reorganización. No revertir cambios ajenos.

El runtime permanece en la versión previamente certificada descrita en
`validation.md`; su PASS no corresponde todavía a este incremento.
No se realizaron commits, pushes ni cambios de rama.

## Continuación cuando el usuario autorice

1. Revisar la estructura definitiva de testing/ y los cambios concurrentes.
2. Corregir el fixture de la nueva prueba del Processor y completar la plantilla.
3. Ejecutar las pruebas del incremento en la estructura acordada.
4. Desplegar con respaldo, migración aditiva y rollback; certificar ciclo de vida,
   integración actual y reinicio desde CLI.
5. Actualizar evidencia y límites; mantener outbox ESS/lifecycle publication,
   reconciliación, API tenant-scoped y otros estados como trabajo posterior.
