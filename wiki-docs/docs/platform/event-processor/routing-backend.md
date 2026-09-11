# Routing y comandos — aceptación base

El registro versionado `/api/v1/rules` administra definiciones ROUTING. Processor
calcula la intención, conserva el primer envelope en `event_processor.integration_command`
y publica mediante outbox. Integration Worker ejecuta al proveedor y conserva/publica
el resultado. No fue necesario modificar lógica productiva en este corte.

## Reproducir

```bash
python3 testing/certifications/processor-routing-certification.py
```

Requiere runtime local y ServiceNow configurado en el Worker hacia `servicenow-mock`.
El script comprueba ese destino antes de generar eventos, crea tenant/nodo únicos y
un mapping específico, sin borrar journals ajenos. Conserva evidencia y hashes en
`evidences/routing/`. Desactiva reglas propias y elimina el mapping propio al completar;
si falla conserva el mapping para permitir terminar comandos en tránsito.

La aceptación comprueba validación, alta/reintento, separación última/activa, revisión
optimista, retiro/historial y aislamiento de lectura por tenant. En simulación prueba
rutas duplicadas, referencia sin grupo, recuperación y rechazo de operaciones/perfiles
no soportados; no genera comandos durables. El recorrido Gateway/Kafka/Processor/Worker
crea un ticket en el mock, verifica SUCCESS y publicación del resultado, exactamente
una solicitud al proveedor y un único envelope inmutable al cambiar de miembro y de
versión de ruta. Recuperación no crea comandos en el perfil base; ruta desactivada
no emite para un nuevo ciclo.

RoutingTest + CorrelationTransactionTest cubren grupos múltiples, deduplicación,
supresión, payload ausente, rollback conjunto, replay y retención del envelope.
Pruebas del Worker cubren contrato Processor, validación, claim y completion.
Usar copia de compilación si el runtime monta target; pruebas SQL requieren el
laboratorio aislado `jdbc:postgresql://127.0.0.1:15439/cacf_test`.

## Alcance y pendientes

Este corte certifica SERVICENOW/CREATE_TICKET, configuración default, por grupo/ciclo.
No certifica ServiceNow real ni rendimiento productivo. Routing base no expone CRUD
para editar/borrar envelopes o ejecutar comandos arbitrarios. Habilitar una ruta con
referencia sintácticamente válida no garantiza grupo aplicable: puede producir
NO_CORRELATION_CYCLE. Guardar/activar no procesa retroactivamente eventos anteriores.

El perfil optativo [Lifecycle](lifecycle-orchestration.md) ya existe y se certifica por
su recorrido propio; no confundir sus operaciones de cierre con acciones libres de
ROUTING. DP-EP-01/06/08 mantienen seguridad, extensiones y escalamiento pendientes.
[Prompt de frontend](frontend-handoffs/routing.md): aceptación visual pendiente.
