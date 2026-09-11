# Ciclo de vida — estado de trabajo

Actualizado: 2026-09-10. Checkout: `feature/os-05-core-event-state-service`,
HEAD de la revisión inicial `887baeaf51f1c9274786388dc36b175dce30b3bb`, con cambios locales de varios frentes.
OS_11 dispone del worktree `/tmp/os11-publication`, rama
`remediation/os-11-01-lifecycle-orchestration`, checkpoint `d05b878`.
No confundir ese checkpoint con el HEAD del checkout compartido.

## Implementación y evidencia

- Processor emite `events.state.requested` mediante su outbox transaccional.
- ESS materializa OPEN/CLOSE/reapertura, severidad dual, tally, ledger de
  solicitudes y transiciones; descarta solicitudes duplicadas o antiguas.
- OS_11 coordina ticket → GNM → CACF/NEXT → recuperación → cierre GNM →
  ServiceNow RESOLVED confirmado → ESS. Fuente y situación son agregados separados.
- La reorganización está incorporada: pruebas en `testing/`, nuevas evidencias
  en `evidences/`; `evidence/` conserva el historial.
- El fixture inválido del Processor fue corregido. El resumen OS_11 registra
  Processor 100, Worker 178 y ESS 29 pruebas, sin fallos, errores ni omitidas.
  Evidencia: `evidences/os11/summary.json`.
- UC-001 y reinicio tienen PASS en el laboratorio aislado `os11-lifecycle`, con
  proveedores simulados. Véase [validación](validation.md) y
  [orquestación](../event-processor/lifecycle-orchestration.md).

## Resultado actual

El despliegue compartido de los tres servicios terminó PASS, con 29 pruebas ESS y
9 checks de servicio incluido reinicio. Evidencia e imágenes en [validación](validation.md).
El bloqueo por grupo lifecycle ausente corresponde al estado anterior.

## Repetición y siguiente paso

La plantilla ESS 1.1.0 exige también OPEN/CLOSE/reapertura y replay/stale.
Su runner usa el runtime compartido; UC-001 usa el laboratorio OS_11.
La CLI comprueba prerrequisitos antes de crear fixtures o ejecutar Maven:
requiere tablas 016/017 y grupo consumidor de ciclo de vida. `--verify` rechaza
recompilar un target montado en un contenedor activo.

El despliegue coordinado de Processor, Worker y ESS ya se certificó. Para
activar toda la orquestación OS_11 faltan la configuración de proveedores y
su certificación en el runtime compartido. La salud HTTP no certifica
que el incremento esté desplegado ni que Kafka esté al día.

Pendientes V1: outbox propio de ESS, publicación `events.lifecycle`, reparación
independiente de proyección, API por tenant y ordenamiento genérico de resultados.
No presentar el PASS de UC-001 como cobertura de todos los fallos ni como
certificación contra proveedores reales. No se ha promovido este checkout.

## Despliegue coordinado

`python3 scripts/event-state-lifecycle-deploy.py --offline-build` construye
Processor/Worker/ESS desde snapshots temporales con la caché Maven local; verifica
29 pruebas ESS contra PostgreSQL aislado. Genera imágenes de runtime usando la
sección runtime de cada Dockerfile, sin recompilar los targets montados.
Respalda event_management y event_processor, aplica 016/017/020/021 y despliega
imágenes fijadas por ID. Certifica ESS con reinicio y restaura imágenes previas
si falla; las migraciones aditivas y registros se conservan.

Esto no activa NEXT ni configura tenants GNM en el runtime principal. La
orquestación completa con esos proveedores sigue teniendo alcance OS_11 aislado.
