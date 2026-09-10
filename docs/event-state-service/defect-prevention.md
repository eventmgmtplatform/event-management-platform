# Defect prevention — ESS

| Riesgo observado | Prevención | Prueba |
|---|---|---|
| Build verde sin tests | Unit tests y Failsafe IT explícitos; registrar skipped | Maven verify con servidor aislado |
| Altas paralelas pierden versión/proveedor | Advisory lock transaccional antes del claim | concurrentFirstResultsMergeWithoutLosingProviderOrVersion |
| SQLException deja claim sin estado | rollbackOn Exception en CDI/JTA | sqlFailureRollsBackClaimThroughRealJtaInterceptor |
| resultId repetido altera estado | Ledger con comparación de identidad y JSON | identicalReplay / changedPayloadCollision |
| eventKey ajeno transfiere tenant | Rechazo y rollback sin migración implícita | tenantCollisionRollsBackNewClaim |
| Mensaje inválido bloquea partición aunque health=UP | Cuarentena durable antes de ack | invalidMessageAcknowledgesOnlyAfterDurableQuarantine + runtime invalid-message-quarantine |
| Error de cuarentena descarta entrada | Propagar fallo de DB; no ack | failedQuarantineNeverAcknowledges |
| Proyección falla pero Kafka avanza | No ack hasta proyección correcta | searchFailureNeverAcknowledges |
| Éxito de nuevo evento oculta backlog bloqueado | Esperar lag=0 antes de validar replay | Runtime drained() |
| Reglas sintéticas afectan ejecuciones siguientes | Tenant único; disable + retire en finally | Cleanup obligatorio o FAIL |
| Simulación se confunde con proveedor real | Gate de URL interna y mapeos mock | Certificación ServiceNow mock |

Pendiente: health explícito de progreso del consumer, outbox/reconciliación,
protección de orden, clave por tenant y contratos completos de lifecycle.
El test de fallo SQL/HTTP usa inyección controlada; no declara haber apagado
PostgreSQL/OpenSearch compartidos durante la certificación.
