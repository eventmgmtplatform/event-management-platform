# Matriz de aceptación para `TECHNICAL_DEBT_SN_006=RESOLVED`

| LAB | Caso | Resultado obligatorio |
|---|---|---|
| 9E | Claim nuevo | Owner no vacío, lease futura, `EXECUTE` |
| 9E | Duplicado con lease vigente | `IN_PROGRESS`, sin HTTP ni resultado |
| 9E | Dos workers toman lease vencida | Un solo `RECONCILE` |
| 9E | Owner obsoleto completa | Cero filas actualizadas y error explícito |
| 9F | Lookup encuentra ticket | Resultado `SUCCESS` reconstruido; cero `POST` |
| 9F | Lookup temporal falla | Sin `POST`, sin resultado terminal falso |
| 9G | Lookup devuelve `NOT_FOUND` certificado | Creación permitida una sola vez bajo owner vigente |
| 9H | Inicio en `standby` | Cero pull de backlog y cero side effects |
| 9H | Cambio a `pull_restart` | Barrido inicial antes de readiness `UP` |
| 9I | Caída después de POST y antes de completion | Reinicio encuentra ticket, completa ledger, un resultado |
| 9I | Reinicio con backlog mixto | COMPLETED replayable, vigentes suprimidos, vencidos reconciliados |
| 9I | Apagar/levantar libremente | Sin pérdida, doble ticket ni ownership huérfano permanente |

Marcador final permitido:

```text
TECHNICAL_DEBT_SN_006=RESOLVED
LAB_SN_02_9I_CRASH_RECOVERY_CERTIFIED=TRUE
```

