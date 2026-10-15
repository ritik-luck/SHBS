terraform {
  backend "s3" {
    bucket         = "shbs-terraform-state"
    key            = "infrastructure/terraform.tfstate"
    region         = "ap-south-1"
    encrypt        = true
    dynamodb_table = "shbs-terraform-lock"
  }
}
