# Seguridad

Reporta vulnerabilidades de forma privada al equipo mantenedor antes de abrir
un issue público. No incluyas tokens, contraseñas, datos de clientes ni
capturas con información sensible.

Los secretos deben viajar por referencias a MockSecrets o al gestor aprobado
por el ambiente; nunca deben aparecer en DTOs, logs, changelogs o fixtures.
Las APIs administrativas deben conservar validación de tenant, ETag,
idempotencia y límites de tamaño.

Este documento describe prácticas actuales y queda sujeto a la revisión formal
de gobierno y al canal de contacto que el equipo designe.
