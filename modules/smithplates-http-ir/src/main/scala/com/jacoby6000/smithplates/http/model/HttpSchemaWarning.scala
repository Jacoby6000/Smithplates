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
