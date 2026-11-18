output "resource_group_name" {
  value = azurerm_resource_group.feedback.name
}

output "function_app_name" {
  value = azurerm_linux_function_app.feedback.name
}

output "function_app_url" {
  value = "https://${azurerm_linux_function_app.feedback.default_hostname}"
}
