variable "subscription_id" {
  type = string
}

variable "admin_email" {
  type = string
}

variable "location" {
  type    = string
  default = "brazilsouth"
}

variable "function_app_name" {
  type    = string
  default = "fiap-feedback-fm-tc4"
}

variable "suffix" {
  type = string

  validation {
    condition     = can(regex("^[a-z0-9]{3,6}$", var.suffix))
    error_message = "Use de 3 a 6 letras minusculas ou numeros no sufixo."
  }
}
