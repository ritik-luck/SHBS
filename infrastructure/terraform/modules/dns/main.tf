variable "project_name" { type = string }
variable "environment" { type = string }
variable "domain_name" { type = string }
variable "alb_dns_name" { type = string }
variable "alb_zone_id" { type = string }

# Only create DNS resources if domain is provided
resource "aws_route53_zone" "main" {
  count = var.domain_name != "" ? 1 : 0
  name  = var.domain_name
}

resource "aws_route53_record" "app" {
  count   = var.domain_name != "" ? 1 : 0
  zone_id = aws_route53_zone.main[0].zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = var.alb_zone_id
    evaluate_target_health = true
  }
}
