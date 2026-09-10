# Policy Engine — aceptación backend local

Las definiciones POLICY se administran mediante `/api/v1/rules` y se persisten en
rule_definition/rule_version/rule_change. PolicyEvaluation utiliza el snapshot del
evento, después de Correlación y antes de Routing. No requirió cambios productivos
para esta certificación; configuración y evaluación ya están implementadas.

```bash
python3 testing/certifications/processor-policy-certification.py
```

Requiere runtime local, Gateway 8081, Processor 8082, catálogo 8090 y Docker/PostgreSQL.
Crea tenant único, reglas/eventos sintéticos; desactiva reglas propias en finally.
Registra una ruta elegible sólo mientras una política restrictiva está activa; la
desactiva antes de probar CONTINUE. Conserva auditoría y hashes en `evidences/policy/`.

La certificación cubre CRUD versionado, idempotencia, revisión optimista, separación
última/activa, retiro/historial, aislamiento por tenant/scope, catálogo y rechazo de
campos/tipos/regex inválidos. Prueba precedencia independiente de prioridad y el
recorrido Gateway/Kafka con SUPPRESS_INTEGRATIONS, STATE_ONLY, CORRELATE_ONLY y CONTINUE.
Verifica recuperación, 12 etapas, ID/versión/checksum, explain, publicación normalizada
y cero comandos durables. RuleCompilerTest, RuleRegistryTest y PipelineTest cubren
operadores tipados, determinismo, límites, concurrencia, rollback e inmutabilidad;
pruebas SQL requieren PostgreSQL aislado en localhost:15439/cacf_test.

Todas las reglas aplicables aportan propuestas. Orden: prioridad descendente, ID y
versión ascendentes. No es first-match ni last-write-wins. Precedencia global:
DEAD_LETTER > STATE_ONLY > SUPPRESS_INTEGRATIONS > CORRELATE_ONLY > GENERATE_COMMANDS > CONTINUE.
POLICY sólo admite CONTINUE, STATE_ONLY, SUPPRESS_INTEGRATIONS y CORRELATE_ONLY;
no admite emitir comandos ni solicitar DEAD_LETTER como acción configurable.

En este pipeline STATE_ONLY bloquea Routing, pero no deshace la correlación ya evaluada
ni omite la auditoría/publicación del evento. Ninguna política dispara una llamada
al proveedor. El [prompt de frontend](frontend-handoffs/policy.md) refleja esos límites.
El catálogo policies también contiene registros heredados de event_management.event_policy:
sólo los de fuente Event Processor corresponden al registro ejecutado por este motor.

DP-EP-01 mantiene seguridad diferida. Esta aceptación no certifica frontend, rendimiento
productivo ni paridad histórica completa. Las descripciones iniciales de contratos
se complementan con las capacidades actuales de cada motor y Lifecycle.
