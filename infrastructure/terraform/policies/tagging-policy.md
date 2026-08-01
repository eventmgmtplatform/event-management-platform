# Política de labels y tags

## Metadatos obligatorios

- Platform
- Environment
- Customer
- Cloud
- Region
- Owner
- ManagedBy
- Repository
- CostCenter
- DataClassification
- Criticality

## Equivalencias

| Concepto | GCP | AWS | Azure |
|---|---|---|---|
| Metadatos | Labels | Tags | Tags |
| Administración | `managed_by` | `ManagedBy` | `ManagedBy` |
| Ambiente | `environment` | `Environment` | `Environment` |
| Cliente | `customer` | `Customer` | `Customer` |
| Criticidad | `criticality` | `Criticality` | `Criticality` |

## Prioridad

Los metadatos corporativos obligatorios tienen prioridad sobre las etiquetas
adicionales proporcionadas por los módulos consumidores.

## Restricciones

- No almacenar secretos.
- No almacenar contraseñas.
- No almacenar tokens.
- No almacenar correos personales salvo que exista una política explícita.
- Los labels GCP deben cumplir sus restricciones de caracteres y longitud.
