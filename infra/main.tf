terraform {
  required_version = ">= 1.5.0"

  backend "azurerm" {
    resource_group_name  = "rg-fiap-feedback-tfstate"
    storage_account_name = "stfiaptfstatefm02"
    container_name       = "tfstate"
    key                  = "feedback.tfstate"
    use_azuread_auth     = true
  }

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }
}

provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}

resource "azurerm_resource_group" "feedback" {
  name     = "rg-fiap-feedback-tf"
  location = var.location
}

resource "azurerm_storage_account" "feedback" {
  name                            = "stfiapfeedbacktf${var.suffix}"
  resource_group_name             = azurerm_resource_group.feedback.name
  location                        = azurerm_resource_group.feedback.location
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  min_tls_version                 = "TLS1_2"
  allow_nested_items_to_be_public = false
}

resource "azurerm_log_analytics_workspace" "feedback" {
  name                = "log-fiap-feedback-tf"
  location            = azurerm_resource_group.feedback.location
  resource_group_name = azurerm_resource_group.feedback.name
  sku                 = "PerGB2018"
  retention_in_days   = 30
}

resource "azurerm_application_insights" "feedback" {
  name                = "appi-fiap-feedback-tf"
  location            = azurerm_resource_group.feedback.location
  resource_group_name = azurerm_resource_group.feedback.name
  workspace_id        = azurerm_log_analytics_workspace.feedback.id
  application_type    = "web"
}

resource "azurerm_cosmosdb_account" "feedback" {
  name                = "cosmos-fiap-feedback-tf-${var.suffix}"
  location            = azurerm_resource_group.feedback.location
  resource_group_name = azurerm_resource_group.feedback.name
  offer_type          = "Standard"
  kind                = "GlobalDocumentDB"
  free_tier_enabled   = true

  consistency_policy {
    consistency_level = "Session"
  }

  geo_location {
    location          = azurerm_resource_group.feedback.location
    failover_priority = 0
  }
}

resource "azurerm_cosmosdb_sql_database" "feedback" {
  name                = "feedback"
  resource_group_name = azurerm_resource_group.feedback.name
  account_name        = azurerm_cosmosdb_account.feedback.name
  throughput          = 400
}

resource "azurerm_cosmosdb_sql_container" "avaliacoes" {
  name                = "avaliacoes"
  resource_group_name = azurerm_resource_group.feedback.name
  account_name        = azurerm_cosmosdb_account.feedback.name
  database_name       = azurerm_cosmosdb_sql_database.feedback.name
  partition_key_paths = ["/dia"]
}

resource "azurerm_email_communication_service" "feedback" {
  name                = "acse-fiap-feedback-tf-${var.suffix}"
  resource_group_name = azurerm_resource_group.feedback.name
  data_location       = "Brazil"
}

resource "azurerm_email_communication_service_domain" "feedback" {
  name              = "AzureManagedDomain"
  email_service_id  = azurerm_email_communication_service.feedback.id
  domain_management = "AzureManaged"
}

resource "azurerm_communication_service" "feedback" {
  name                = "acs-fiap-feedback-tf-${var.suffix}"
  resource_group_name = azurerm_resource_group.feedback.name
  data_location       = "Brazil"
}

resource "azurerm_communication_service_email_domain_association" "feedback" {
  communication_service_id = azurerm_communication_service.feedback.id
  email_service_domain_id  = azurerm_email_communication_service_domain.feedback.id
}

resource "azurerm_service_plan" "feedback" {
  name                = "plan-fiap-feedback-tf"
  resource_group_name = azurerm_resource_group.feedback.name
  location            = azurerm_resource_group.feedback.location
  os_type             = "Linux"
  sku_name            = "Y1"
}

resource "azurerm_linux_function_app" "feedback" {
  name                       = var.function_app_name
  resource_group_name        = azurerm_resource_group.feedback.name
  location                   = azurerm_resource_group.feedback.location
  service_plan_id            = azurerm_service_plan.feedback.id
  storage_account_name       = azurerm_storage_account.feedback.name
  storage_account_access_key = azurerm_storage_account.feedback.primary_access_key
  https_only                 = true

  identity {
    type = "SystemAssigned"
  }

  site_config {
    application_insights_connection_string = azurerm_application_insights.feedback.connection_string
    ftps_state                             = "Disabled"
    minimum_tls_version                    = "1.2"

    application_stack {
      java_version = "21"
    }
  }

  app_settings = {
    COSMOS_ENDPOINT       = azurerm_cosmosdb_account.feedback.endpoint
    COSMOS_DATABASE       = azurerm_cosmosdb_sql_database.feedback.name
    COSMOS_CONTAINER      = azurerm_cosmosdb_sql_container.avaliacoes.name
    ACS_CONNECTION_STRING = azurerm_communication_service.feedback.primary_connection_string
    EMAIL_SENDER          = "DoNotReply@${azurerm_email_communication_service_domain.feedback.mail_from_sender_domain}"
    EMAIL_ADMIN           = var.admin_email
  }
}

resource "azurerm_cosmosdb_sql_role_assignment" "feedback" {
  resource_group_name = azurerm_resource_group.feedback.name
  account_name        = azurerm_cosmosdb_account.feedback.name
  principal_id        = azurerm_linux_function_app.feedback.identity[0].principal_id
  scope               = azurerm_cosmosdb_account.feedback.id
  role_definition_id  = "${azurerm_cosmosdb_account.feedback.id}/sqlRoleDefinitions/00000000-0000-0000-0000-000000000002"
}

resource "azurerm_monitor_action_group" "admin" {
  name                = "ag-admin-feedback-tf"
  resource_group_name = azurerm_resource_group.feedback.name
  short_name          = "admfeed"

  email_receiver {
    name          = "admin"
    email_address = var.admin_email
  }
}

resource "azurerm_monitor_metric_alert" "excecoes" {
  name                = "alerta-excecoes"
  resource_group_name = azurerm_resource_group.feedback.name
  scopes              = [azurerm_application_insights.feedback.id]
  severity            = 1
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.Insights/components"
    metric_name      = "exceptions/count"
    aggregation      = "Count"
    operator         = "GreaterThan"
    threshold        = 0
  }

  action {
    action_group_id = azurerm_monitor_action_group.admin.id
  }
}

resource "azurerm_monitor_metric_alert" "falhas" {
  name                = "alerta-falhas-requisicao"
  resource_group_name = azurerm_resource_group.feedback.name
  scopes              = [azurerm_application_insights.feedback.id]
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.Insights/components"
    metric_name      = "requests/failed"
    aggregation      = "Count"
    operator         = "GreaterThan"
    threshold        = 0
  }

  action {
    action_group_id = azurerm_monitor_action_group.admin.id
  }
}
