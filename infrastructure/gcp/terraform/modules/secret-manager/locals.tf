locals {
  normalized_secrets = {
    for key, secret in var.secrets :
    key => {
      secret_id             = secret.secret_id
      purpose               = secret.purpose
      replication_type      = upper(secret.replication_type)
      replication_locations = sort(tolist(secret.replication_locations))
      deletion_protection   = secret.deletion_protection

      labels = merge(
        var.default_labels,
        secret.labels,
        {
          module  = "secret-manager"
          purpose = secret.purpose
        }
      )

      iam = secret.iam
    }
  }

  iam_member_items = flatten([
    for secret_key, secret in local.normalized_secrets : [
      for role, members in secret.iam : [
        for member in members : {
          key        = "${secret_key}|${role}|${member}"
          secret_key = secret_key
          role       = role
          member     = member
        }
      ]
    ]
  ])

  iam_members = {
    for item in local.iam_member_items :
    item.key => item
  }
}
