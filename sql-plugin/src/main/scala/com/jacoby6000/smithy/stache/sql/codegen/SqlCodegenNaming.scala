package com.jacoby6000.smithy.stache.sql.codegen

import software.amazon.smithy.model.shapes.ShapeId

object SqlCodegenNaming {
  def className(shapeId: ShapeId): String =
    shapeId.getName

  def snakeCase(name: String): String = {
    val withUnderscores =
      name
        .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
    withUnderscores.toLowerCase
  }

  def methodName(operationName: String): String =
    snakeCase(operationName)

  def unionVariantClassName(unionName: String, memberName: String): String =
    s"$unionName${memberName.capitalize}"

  def serviceFileName(serviceName: String): String =
    snakeCase(serviceName)
}
