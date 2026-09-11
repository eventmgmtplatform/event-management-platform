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

## Cierre del alcance acordado

Decisión del usuario, 2026-09-10: mover la revisión final de DA-06 a DP-EP-04 y
backup/restore con replay a DP-EP-08. Ambos quedan DIFERIDOS y no bloquean el corte
mínimo local; no se consideran pruebas aprobadas ni se ejecutan en este incremento.

Con ese alcance, no quedan brechas funcionales de backend identificadas para este
corte: las capacidades de la matriz y su recorrido integrado están en PASS. Esto
no certifica producción, restauración, RPO/RTO ni paridad completa de DA-06.

Para la entrega del producto permanece la aceptación de frontend por su chat, usando
los prompts por motor y sus evidencias de navegador. Los estados de los handoffs
son una entrega histórica, no sustituyen las validaciones posteriores de la consola.
La publicación formal de release (merge a main/tag/despliegue de versión) es un paso
separado de las certificaciones; no se realiza por mover estos pendientes a DP.

## Defect Prevention y límites

Seguridad/OIDC/RBAC, proveedores reales (incluido Bridge), estrategias adicionales,
recurrencias, APIs especializadas, escala y recuperación ampliada permanecen acotados
en [Defect Prevention](defect-prevention.md). [Lifecycle](lifecycle-orchestration.md)
implementa un perfil optativo de orquestación con mocks; no habilita comandos libres
ni certifica parámetros de proveedores reales. Ningún pendiente justifica ampliación
innecesaria del alcance mínimo.
