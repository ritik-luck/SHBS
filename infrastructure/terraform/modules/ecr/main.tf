variable "project_name" { type = string }

resource "aws_ecr_repository" "backend" {
  name                 = "${var.project_name}/backend"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
}

resource "aws_ecr_repository" "frontend" {
  name                 = "${var.project_name}/frontend"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
}

resource "aws_ecr_repository" "inventory" {
  name                 = "${var.project_name}/inventory-service"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
}

# Lifecycle policy: keep only last 10 untagged images
resource "aws_ecr_lifecycle_policy" "cleanup" {
  for_each   = toset(["backend", "frontend", "inventory"])
  repository = each.key == "backend" ? aws_ecr_repository.backend.name : (each.key == "frontend" ? aws_ecr_repository.frontend.name : aws_ecr_repository.inventory.name)

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 20 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}

output "backend_repository_url" { value = aws_ecr_repository.backend.repository_url }
output "frontend_repository_url" { value = aws_ecr_repository.frontend.repository_url }
output "inventory_repository_url" { value = aws_ecr_repository.inventory.repository_url }
output "repository_arns" {
  value = [
    aws_ecr_repository.backend.arn,
    aws_ecr_repository.frontend.arn,
    aws_ecr_repository.inventory.arn,
  ]
}
