# Carbon Design Preview

Esta página permite validar los estilos principales.

## Estados

<span class="status status--validated">VALIDATED</span>

<span class="status status--pending">PENDING</span>

<span class="status status--blocked">BLOCKED</span>

## Botones

[Documentación](index.md){ .md-button .md-button--primary }

[Arquitectura](architecture/index.md){ .md-button }

## Tabla

| Componente | Estado | Responsabilidad |
|---|---|---|
| Kafka | Validado | Backbone de eventos |
| PostgreSQL | Validado | Estado transaccional |
| OpenSearch | Validado | Proyección de consulta |
| GCP Networking | Pendiente | Red cloud |

## Avisos

!!! info "Información"
    Este es un aviso informativo.

!!! warning "Pendiente"
    Esta funcionalidad requiere validación.

!!! danger "Riesgo"
    No publique secretos dentro de la documentación.

## Código

```yaml
site_name: Event Management OpenSource
theme:
  name: material
cat > docs/carbon-preview.md <<'EOF'
# Carbon Design Preview

Esta página permite validar los estilos principales.

## Estados

<span class="status status--validated">VALIDATED</span>

<span class="status status--pending">PENDING</span>

<span class="status status--blocked">BLOCKED</span>

## Botones

[Documentación](index.md){ .md-button .md-button--primary }

[Arquitectura](architecture/index.md){ .md-button }

## Tabla

| Componente | Estado | Responsabilidad |
|---|---|---|
| Kafka | Validado | Backbone de eventos |
| PostgreSQL | Validado | Estado transaccional |
| OpenSearch | Validado | Proyección de consulta |
| GCP Networking | Pendiente | Red cloud |

## Avisos

!!! info "Información"
    Este es un aviso informativo.

!!! warning "Pendiente"
    Esta funcionalidad requiere validación.

!!! danger "Riesgo"
    No publique secretos dentro de la documentación.

## Código

```yaml
site_name: Event Management OpenSource
theme:
  name: material
