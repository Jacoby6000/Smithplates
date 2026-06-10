package com.jacoby6000.smithplates.http.service.renderer

object HttpCodegenTemplateAttributes {
  def renderOutputPath(pattern: String, context: HttpCodegenServiceContext): String =
    pattern
      .replace("{{serviceName}}", context.name)
      .replace("{{serviceClassName}}", context.name)
      .replace("{{serviceFileName}}", toSnakeCase(context.name))
      .replace("{{serviceNamespace}}", context.namespace)
      .replace("{{serviceShapeId}}", context.shapeId.toString)
      .replace("{{serviceVersion}}", context.version)
      .replace("{{packageName}}", context.packageName)

  def toSnakeCase(value: String): String =
    value
      .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .toLowerCase

  def toOperationSnakeCase(operationName: String): String =
    operationName
      .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .toLowerCase

  def pythonStringLiteral(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  def pythonOptionalStringLiteral(value: Option[String]): String =
    value.map(pythonStringLiteral).getOrElse("None")
}
