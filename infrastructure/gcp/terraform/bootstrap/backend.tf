terraform {
  backend "gcs" {
    bucket = "corded-key-504121-v7-terraform-state"
    prefix = "bootstrap"
  }
}
