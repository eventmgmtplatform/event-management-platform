# Política de versionamiento de módulos Terraform

Los módulos Terraform utilizan Semantic Versioning:

```text
MAJOR.MINOR.PATCH
MAJOR

Se incrementa cuando existe un cambio incompatible.

Ejemplos:

Eliminar una variable.
Renombrar una variable obligatoria.
Eliminar un output.
Cambiar la estructura de un output.
Cambiar el significado de una entrada.
Cambiar el comportamiento esperado del módulo.

Ejemplo:

1.4.2 -> 2.0.0
MINOR

Se incrementa al agregar funcionalidad compatible.

Ejemplos:

Agregar una variable opcional.
Agregar un nuevo output.
Agregar soporte para otro proveedor.
Agregar una convención compatible.
Agregar validaciones que no invaliden configuraciones válidas.

Ejemplo:

1.4.2 -> 1.5.0
PATCH

Se incrementa para correcciones compatibles.

Ejemplos:

Corregir documentación.
Corregir una validación defectuosa.
Corregir normalización sin cambiar el contrato público.
Corregir un nombre calculado.

Ejemplo:

1.4.2 -> 1.4.3
Versiones de desarrollo

Las versiones 0.x.y representan módulos en construcción.

La versión 1.0.0 se publicará después de que el módulo haya sido consumido y
validado por los módulos de Networking e IAM.

Convención de tags Git
terraform-project-common-v0.1.0
terraform-project-common-v1.0.0
terraform-gcp-network-v1.0.0
terraform-gcp-iam-v1.0.0

Los tags se crearán inicialmente de manera local. No se publicarán hasta que el
repositorio remoto sea configurado formalmente.
