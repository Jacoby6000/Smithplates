package com.jacoby6000.smithplates.http.model

import software.amazon.smithy.model.shapes.ShapeId

sealed trait HttpSchemaError {
  def message: String
}

final case class InvalidPluginConfig(message: String) extends HttpSchemaError

final case class MissingHttpServiceVersion(serviceShape: ShapeId) extends HttpSchemaError {
  override def message: String =
    s"@httpService '${serviceShape.toString}' requires a non-empty service version"
}

final case class EmptyHttpService(serviceShape: ShapeId) extends HttpSchemaError {
  override def message: String =
    s"@httpService '${serviceShape.toString}' must declare at least one HTTP-bound operation"
}

final case class InvalidHttpOperation(
    serviceShape: ShapeId,
    operationName: String,
    reason: String
) extends HttpSchemaError {
  override def message: String =
    s"Invalid HTTP operation '$operationName' on service '${serviceShape.toString}': $reason"
}

final case class InvalidHttpService(
    serviceShape: ShapeId,
    reason: String
) extends HttpSchemaError {
  override def message: String =
    s"Invalid @httpService '${serviceShape.toString}': $reason"
}
