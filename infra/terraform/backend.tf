terraform {
  backend "s3" {
    bucket  = "jobai-terraform-state-minjoo-2376034"
    key     = "jobai/prod/terraform.tfstate"
    location  = "ap-northeast-2"
    encrypt = true
  }
}