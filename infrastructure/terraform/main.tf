# ══════════════════════════════════════════════════════════════════════
# SHBS Infrastructure — Root Module
# ══════════════════════════════════════════════════════════════════════

module "vpc" {
  source             = "./modules/vpc"
  project_name       = var.project_name
  environment        = var.environment
  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones
}

module "ecr" {
  source       = "./modules/ecr"
  project_name = var.project_name
}

module "secrets" {
  source       = "./modules/secrets"
  project_name = var.project_name
  environment  = var.environment
}

module "iam" {
  source       = "./modules/iam"
  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region
  ecr_arns     = module.ecr.repository_arns
  secret_arns  = module.secrets.secret_arns
}

module "rds" {
  source              = "./modules/rds"
  project_name        = var.project_name
  environment         = var.environment
  vpc_id              = module.vpc.vpc_id
  private_subnet_ids  = module.vpc.private_subnet_ids
  instance_class      = var.db_instance_class
  allocated_storage   = var.db_allocated_storage
  multi_az            = var.db_multi_az
  db_password_secret_arn = module.secrets.db_password_arn
  backend_sg_id       = module.ecs.backend_sg_id
}

module "elasticache" {
  source             = "./modules/elasticache"
  project_name       = var.project_name
  environment        = var.environment
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  node_type          = var.redis_node_type
  backend_sg_id      = module.ecs.backend_sg_id
}

module "alb" {
  source            = "./modules/alb"
  project_name      = var.project_name
  environment       = var.environment
  vpc_id            = module.vpc.vpc_id
  public_subnet_ids = module.vpc.public_subnet_ids
  domain_name       = var.domain_name
}

module "ecs" {
  source                = "./modules/ecs"
  project_name          = var.project_name
  environment           = var.environment
  aws_region            = var.aws_region
  vpc_id                = module.vpc.vpc_id
  private_subnet_ids    = module.vpc.private_subnet_ids
  alb_sg_id             = module.alb.alb_sg_id
  backend_target_group_arn  = module.alb.backend_target_group_arn
  frontend_target_group_arn = module.alb.frontend_target_group_arn

  # Images
  backend_image     = "${module.ecr.backend_repository_url}:${var.backend_image_tag}"
  frontend_image    = "${module.ecr.frontend_repository_url}:${var.frontend_image_tag}"
  inventory_image   = "${module.ecr.inventory_repository_url}:${var.inventory_image_tag}"

  # Resources
  backend_cpu       = var.backend_cpu
  backend_memory    = var.backend_memory
  backend_desired   = var.backend_desired_count
  frontend_cpu      = var.frontend_cpu
  frontend_memory   = var.frontend_memory
  inventory_cpu     = var.inventory_cpu
  inventory_memory  = var.inventory_memory

  # Dependencies
  db_endpoint       = module.rds.endpoint
  redis_endpoint    = module.elasticache.endpoint
  secret_arns       = module.secrets.secret_arns
  execution_role_arn = module.iam.execution_role_arn
  task_role_arn      = module.iam.task_role_arn
}

module "cloudwatch" {
  source         = "./modules/cloudwatch"
  project_name   = var.project_name
  environment    = var.environment
  ecs_cluster_name = module.ecs.cluster_name
  alb_arn_suffix   = module.alb.alb_arn_suffix
  rds_identifier   = module.rds.identifier
  redis_replication_group_id = module.elasticache.replication_group_id
}

module "billing" {
  source       = "./modules/billing"
  project_name = var.project_name
  environment  = var.environment
}
