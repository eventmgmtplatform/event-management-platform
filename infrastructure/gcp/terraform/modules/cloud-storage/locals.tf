locals {
  buckets = {
    for key, bucket in var.buckets :
    key => {
      name                          = bucket.name
      purpose                       = bucket.purpose
      location                      = coalesce(bucket.location, var.default_location)
      storage_class                 = bucket.storage_class
      force_destroy                 = bucket.force_destroy
      uniform_bucket_level_access   = bucket.uniform_bucket_level_access
      public_access_prevention      = bucket.public_access_prevention
      versioning_enabled            = bucket.versioning_enabled
      soft_delete_retention_seconds = bucket.soft_delete_retention_seconds

      labels = merge(
        var.default_labels,
        bucket.labels,
        {
          purpose = bucket.purpose
        }
      )
    }
  }
}
