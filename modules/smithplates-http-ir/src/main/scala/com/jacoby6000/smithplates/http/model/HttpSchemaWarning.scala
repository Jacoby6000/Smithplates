package com.jacoby6000.smithplates.http.model

import software.amazon.smithy.model.shapes.ShapeId

sealed trait HttpSchemaWarning {
  def message: String
}

final case class HttpInputMemberOrderWarning(
    serviceShape: ShapeId,
    operationName: String,
    inputShape: ShapeId,
    declaredOrder: List[String],
    expectedOrder: List[String]
) extends HttpSchemaWarning {
  override def message: String =
    s"HTTP operation '$operationName' on service '${serviceShape.toString}' " +
      s"(input ${inputShape.toString}) declares route input members out of order: " +
      s"[${declaredOrder.mkString(", ")}]; expected headers, then URI path labels in URI order, then queries: " +
      s"[${expectedOrder.mkString(", ")}]"
}

final case class HttpProblemTypeWarning(
    serviceShape: ShapeId,
    errorShape: ShapeId,
    problemType: String
) extends HttpSchemaWarning {
  override def message: String =
    s"@httpProblem on error structure '${errorShape.toString}' in service '${serviceShape.toString}' " +
      s"uses type '$problemType'; prefer an HTTPS URL to human-readable documentation " +
      s"(RFC 9457 recommends a URI reference identifying the problem type)"
}
