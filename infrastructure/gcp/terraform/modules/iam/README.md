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
