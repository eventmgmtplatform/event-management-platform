# Convenciones de nombres

## Formato lógico principal

```text
<platform-short>-<customer>-<environment>-<cloud>-<component>

Ejemplo:

em-shared-dev-gcp-network
Componentes
Campo	Ejemplo
Plataforma corta	em
Cliente	shared
Ambiente	dev
Cloud	gcp
Componente	network
Formatos publicados

El módulo project-common publica tres formatos:

Formato	Ejemplo	Uso
Estándar	em-shared-dev-gcp	Nombre general
Corto	em-shared-dev-gcp	Recursos con límites restrictivos
Compacto	emshareddevgcp	Recursos que no aceptan guiones

Los módulos específicos de cada proveedor seleccionarán el formato apropiado
según las restricciones del recurso.

Reglas generales
Usar letras minúsculas.
No usar espacios.
No usar guion bajo en nombres de recursos.
Separar componentes mediante guiones.
No incluir secretos.
No incluir credenciales.
No incluir datos personales.
Mantener visibles el cliente y el ambiente cuando el recurso lo permita.
Utilizar nombres deterministas para permitir destrucción y recreación.
Mantener la misma semántica entre GCP, AWS y Azure.
Ejemplos
em-shared-dev-gcp-network
em-customer01-prod-aws-workloads
em-customer02-test-azure-data
Excepciones del proveedor

Las restricciones físicas de cada proveedor deben resolverse en sus módulos
específicos. El módulo común solamente publica nombres lógicos y normalizados.
