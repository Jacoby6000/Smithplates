package com.jacoby6000.smithplates.sql.service.renderer

object SqlCodegenSnakeCase {
  def toSnakeCase(value: String): String =
    value
      .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .toLowerCase
}
