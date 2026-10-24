# Частка AWS для BMUZ, якую стварае Terraform. Астатняе пакуль створана рукамі (../README.md).
terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.70"
    }
  }

  # Стан ляжыць у прыватным S3; замок — файл побач з ім.
  backend "s3" {
    bucket       = "bmuz-terraform-state-899012776854"
    key          = "bmuz.tfstate"
    region       = "eu-north-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region              = "eu-north-1"
  allowed_account_ids = [local.account]

  default_tags {
    tags = {
      project = "bmuz"
    }
  }
}

locals {
  account = "899012776854"
  region  = "eu-north-1"
  repo    = "Heorhi-Puhachou/BMUZ"
}
