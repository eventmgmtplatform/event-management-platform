# Decisiones de esta generación

Estas decisiones describen el código implementado; no sustituyen una aprobación
arquitectónica global ni presentan contratos de proveedores como certificados.

| ID | Decisión | Motivo / consecuencia |
|---|---|---|
| CACF-LOCAL-01 | Alcance CACF + dependencias, Docker Compose | Límite explícito del usuario; sin Cloud Build/Terraform |
| CACF-LOCAL-02 | Implementar dentro de integration-worker | Reutilizar foundation y transporte existentes |
| CACF-LOCAL-03 | Grupo Kafka propio con commit manual | Persistir admisión antes de confirmar offset |
| CACF-LOCAL-04 | Transacciones JDBC, locks y outbox | Consistencia SQL y recuperación de publicación; entrega al menos una vez |
| CACF-LOCAL-05 | No reintentar HTTP mutante incierto | Evitar doble automatización y doble nota; requiere revisión |
| CACF-LOCAL-06 | Deadline desde ACK y EWT informativo | Repetir ACK no extiende ejecución |
| CACF-LOCAL-07 | UNKNOWN terminal con revisión | Catálogo incompleto no dispara acción destructiva |
| CACF-LOCAL-08 | ServiceNow vía foundation existente | Lookup/PATCH compartidos; no segundo cliente ITSM |
| CACF-LOCAL-09 | REMEDIATED solo nota | Cierre requiere política confirmada |
| CACF-LOCAL-10 | Fixtures sintéticos y entorno aislado | Reproducibilidad local sin afirmar certificación externa |

Fuente de especificación recuperada: tarea OS_05_CACF.IMP — CACF Core Foundation —
Contract Discovery & Runtime Implementation, id 6a9f90c3-729c-83e8-9669-4e0b6a15996f.
Las decisiones técnicas se rastrean al código incluido y a las pruebas, con los
límites documentados en contracts.md y defect-prevention.md.
