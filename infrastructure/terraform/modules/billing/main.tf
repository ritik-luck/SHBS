resource "aws_budgets_budget" "shbs_budget" {
  name              = "${var.project_name}-${var.environment}-monthly-budget"
  budget_type       = "COST"
  limit_amount      = var.monthly_budget_limit
  limit_unit        = "USD"
  time_unit         = "MONTHLY"
  time_period_start = "2024-01-01_00:00"

  cost_filter {
    name = "TagKeyValue"
    values = [
      "user:Project$${var.project_name}"
    ]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }
}

resource "aws_ce_anomaly_monitor" "shbs_anomaly_monitor" {
  name              = "${var.project_name}-${var.environment}-anomaly-monitor"
  monitor_type      = "DIMENSIONAL"
  monitor_dimension = "SERVICE"
}

resource "aws_ce_anomaly_subscription" "shbs_anomaly_subscription" {
  name             = "${var.project_name}-${var.environment}-anomaly-subscription"
  threshold_expression {
    dimension {
      key            = "ANOMALY_TOTAL_IMPACT_ABSOLUTE"
      match_options  = ["GREATER_THAN_OR_EQUAL"]
      values         = ["10"]
    }
  }
  frequency        = "DAILY"
  monitor_arn_list = [aws_ce_anomaly_monitor.shbs_anomaly_monitor.arn]

  subscriber {
    type    = "EMAIL"
    address = var.alert_email
  }
}
