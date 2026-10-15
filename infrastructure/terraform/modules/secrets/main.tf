variable "project_name" { type = string }
variable "environment" { type = string }

resource "aws_secretsmanager_secret" "db_password" {
  name        = "${var.project_name}/${var.environment}/db-password"
  description = "RDS MySQL root password"
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name        = "${var.project_name}/${var.environment}/jwt-secret"
  description = "JWT signing secret"
}

resource "aws_secretsmanager_secret" "admin_credentials" {
  name        = "${var.project_name}/${var.environment}/admin-credentials"
  description = "Backend admin username and password"
}

resource "aws_secretsmanager_secret" "grafana_password" {
  name        = "${var.project_name}/${var.environment}/grafana-password"
  description = "Grafana admin password"
}

output "db_password_arn" { value = aws_secretsmanager_secret.db_password.arn }
output "secret_arns" {
  value = [
    aws_secretsmanager_secret.db_password.arn,
    aws_secretsmanager_secret.jwt_secret.arn,
    aws_secretsmanager_secret.admin_credentials.arn,
    aws_secretsmanager_secret.grafana_password.arn,
  ]
}
