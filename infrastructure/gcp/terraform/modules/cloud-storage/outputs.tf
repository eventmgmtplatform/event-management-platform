output "bucket_ids" {
  description = "IDs de los buckets Cloud Storage indexados por clave lógica."

  value = {
    for key, bucket in google_storage_bucket.this :
    key => bucket.id
  }
}

output "bucket_names" {
  description = "Nombres globalmente únicos de los buckets indexados por clave lógica."

  value = {
    for key, bucket in google_storage_bucket.this :
    key => bucket.name
  }
}

output "bucket_urls" {
  description = "URLs gs:// de los buckets indexadas por clave lógica."

  value = {
    for key, bucket in google_storage_bucket.this :
    key => bucket.url
  }
}

output "bucket_self_links" {
  description = "Self-links de los buckets indexados por clave lógica."

  value = {
    for key, bucket in google_storage_bucket.this :
    key => bucket.self_link
  }
}

output "bucket_locations" {
  description = "Ubicaciones efectivas de los buckets indexadas por clave lógica."

  value = {
    for key, bucket in google_storage_bucket.this :
    key => bucket.location
  }
}

output "bucket_storage_classes" {
  description = "Clases de almacenamiento predeterminadas de los buckets indexadas por clave lógica."

  value = {
    for key, bucket in google_storage_bucket.this :
    key => bucket.storage_class
  }
}

output "bucket_labels" {
  description = "Etiquetas efectivas de los buckets indexadas por clave lógica."

  value = {
    for key, bucket in google_storage_bucket.this :
    key => bucket.labels
  }
}
