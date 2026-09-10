# Revisión de cierre — v1.0.0 mínima local

Alcance: lógica de negocio local, sin seguridad, con Defect Prevention activo.
Las certificaciones por motor y el recorrido integrado están aprobados en el corte
2026-09-10. Esto no cierra todos los DA ni constituye certificación de producción.
Resultados, intercambios y hashes permanecen en evidences; las pruebas son reproducibles.

## Matriz backend

| Capacidad | Estado del subconjunto local | Reproducción / contrato |
|---|---|---|
| Blackouts | PASS: escritura, ventanas y efecto real | processor-blackout-write-certification.py; testing/run.py blackout |
| Inventory/Enrichment | PASS: registro, planes, hechos/procedencia y efecto real | processor-inventory-enrichment-certification.py |
| Auto-suppression | PASS: vigencia/estados y bloqueo de comandos | processor-auto-suppression-certification.py |
| Correlación | PASS: ATTRIBUTE/GROUP, miembros, ciclos, persistencia | processor-correlation-certification.py |
| Routing/comandos base | PASS: creación única y entrega a mock | processor-routing-certification.py |
| Policy | PASS: DSL tipada, directivas/precedencia y API | processor-policy-certification.py |
| AIOps independiente | PASS: CRUD, mock, reinicio, errores y baja lógica | processor-aiops-certification.py |
| Recorrido integrado | PASS: hechos → mantenimiento → correlación/policy → Worker/mocks → recuperación → ESS/OpenSearch | processor-integrated-certification.py |

Los scripts están en `testing/certifications/`. Las entregas de interfaz se encuentran
en [frontend-handoffs](frontend-handoffs/README.md). Cada chat de frontend conserva su
propia aceptación; esta matriz certifica backend, no navegador.

## Recorrido integrado reproducible

```bash
python3 testing/certifications/processor-integrated-certification.py
```

Usa el runtime compartido con tenant sintético preconfigurado y recurso/reglas únicos;
no crea otro despliegue ni borra datos ajenos. Comprueba que Worker apunta a mocks antes
de comenzar. Añade la variante all_engines al escenario existente, cuyo modo default
se conserva. Mantiene mappings específicos para reconciliación tardía y desactiva
reglas propias. No incluye AIOps en el pipeline: su consulta es independiente.

Inventario aporta la clave CI usada por Correlación y un hecho booleano requerido
por Policy/Routing. Blackout y suppression activos impiden comandos; luego suppression
solo mantiene el bloqueo. Al desactivar ambos, el mismo recurso emite una sola cadena:
ticket → GNM confirmado → NEXT/ACK/TKTUPDATE → remediación y nota. La recuperación
cierra GNM y resuelve ticket, con identidad y estado confirmados en ESS y OpenSearch.
Callback y clear duplicados no duplican efectos. Se verifican outboxes drenados.
El nuevo ciclo tiene certificación por motor; este escenario integrado no repite
una segunda cadena completa de proveedores.

## Gates que siguen abiertos

1. **DA-06: alcance funcional final.** El [contrato ESS](../event-state-service/lifecycle-contract.md)
   define OPEN/CLOSE, tally, identidad, duplicados de transporte y eventos tardíos.
   Tally cuenta OPEN distintos aceptados; no significa deduplicación semántica por
   contenido. El recorrido confirma recuperación/proyección. Falta cerrar explícitamente
   la aceptación de ese subconjunto para v1 y contrastar los casos restantes; no inferir
   paridad completa a partir de la deduplicación de comandos.
2. **Backup/restore ampliado.** Restaurar relaciones, ledger, configuración y auditoría
   AIOps poblados, seguido de replay sin duplicados. Reiniciar y leer configuración no
   sustituye restauración desde backup. Gate PENDING, sin RPO/RTO certificado.
3. **Cierre global.** Vincular esos gates a evidencias y mantener PASS/FAIL/PENDING.
   El backend por motor está aprobado; la release mínima completa sigue PENDING.

## Defect Prevention y límites

Seguridad/OIDC/RBAC, proveedores reales (incluido Bridge), estrategias adicionales,
recurrencias, APIs especializadas, escala y recuperación ampliada permanecen acotados
en [Defect Prevention](defect-prevention.md). [Lifecycle](lifecycle-orchestration.md)
implementa un perfil optativo de orquestación con mocks; no habilita comandos libres
ni certifica parámetros de proveedores reales. Ningún pendiente justifica ampliación
innecesaria del alcance mínimo.
