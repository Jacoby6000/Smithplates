package com.jacoby6000.smithplates.http.service.renderer

import com.jacoby6000.smithplates.http.model.HttpService

object HttpCodegenTemplateAttributes {
  def renderOutputPath(pattern: String, service: HttpService, packageName: String): String =
    pattern
      .replace("{{serviceName}}", service.shapeId.getName)
      .replace("{{serviceClassName}}", service.shapeId.getName)
      .replace("{{serviceFileName}}", toSnakeCase(service.shapeId.getName))
      .replace("{{serviceNamespace}}", service.shapeId.getNamespace)
      .replace("{{serviceShapeId}}", service.shapeId.toString)
      .replace("{{serviceVersion}}", service.version)
      .replace("{{packageName}}", packageName)

  def toSnakeCase(value: String): String =
    value
      .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .toLowerCase
}
