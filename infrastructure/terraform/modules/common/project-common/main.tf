check "production_criticality" {
  assert {
    condition = (
      local.normalized.environment != "prod" ||
      contains(["high", "critical"], local.normalized.criticality)
    )

    error_message = "Los ambientes prod deberían utilizar criticality high o critical."
  }
}

check "name_prefix_length" {
  assert {
    condition     = length(local.name_prefix) <= 63
    error_message = "El prefijo estándar generado supera los 63 caracteres."
  }
}

check "short_name_prefix_length" {
  assert {
    condition     = length(local.name_prefix_short) <= 30
    error_message = "El prefijo corto generado supera los 30 caracteres."
  }
}

check "compact_name_prefix_length" {
  assert {
    condition     = length(local.name_prefix_compact) <= 32
    error_message = "El prefijo compacto generado supera los 32 caracteres."
  }
}
