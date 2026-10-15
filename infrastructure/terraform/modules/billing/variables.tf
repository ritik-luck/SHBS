variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "monthly_budget_limit" {
  type    = string
  default = "500" # $500 monthly limit
}

variable "alert_email" {
  type    = string
  default = "sre-team@example.com"
}
