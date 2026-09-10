# IAM Module

Este módulo administra bindings IAM de proyecto para la cuenta de servicio
utilizada por Terraform.

## Alcance inicial

OS_08_08.1 administra exclusivamente los roles operativos de:

- `terraform-deployer`

Las cuentas de servicio de runtime se incorporarán posteriormente en
OS_08_08.2.

## Recursos

- `google_project_iam_member`

Se utiliza un recurso por combinación de miembro y rol para evitar reemplazar
bindings IAM existentes que no son propiedad de este módulo.

## Roles prohibidos

El módulo rechaza explícitamente:

- `roles/owner`
- `roles/editor`

## Ejemplo

```hcl
module "iam" {
  source = "../../modules/iam"

  project_id               = var.project_id
  terraform_deployer_email = var.terraform_deployer_email

  terraform_deployer_project_roles = [
    "roles/compute.networkAdmin",
    "roles/compute.securityAdmin"
  ]
}
Consideraciones
El módulo no crea llaves de cuenta de servicio.
El módulo no administra la política IAM completa del proyecto.
La cuenta terraform-deployer pertenece al Bootstrap.
Este módulo sólo administra sus permisos operativos.

## Storage remediation

Para permitir que Terraform inventarie y administre los recursos de Cloud
Storage declarados por los ambientes consumidores, la cuenta
`terraform-deployer` puede recibir:

- `roles/storage.admin`

La asignación se administra mediante la variable
`terraform_deployer_project_roles` y recursos individuales
`google_project_iam_member`.

El rol se concede exclusivamente a la identidad de despliegue de Terraform.
No se concede a identidades runtime, operadores ni Cloud Build.

La amplitud del rol se acepta porque el deployer debe administrar el ciclo de
vida completo de buckets y sus configuraciones mediante Terraform. El acceso
se realiza por impersonación y no mediante llaves permanentes.

[Historial de cambios del componente](CHANGELOG.md).
