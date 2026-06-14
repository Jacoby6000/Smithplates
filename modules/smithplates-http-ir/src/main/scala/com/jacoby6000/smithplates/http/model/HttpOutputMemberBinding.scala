package com.jacoby6000.smithplates.http.model

import software.amazon.smithy.model.shapes.ShapeId

sealed trait HttpOutputMemberBinding

object HttpOutputMemberBinding {
  final case class Header(headerName: String) extends HttpOutputMemberBinding

  final case class Payload(explicitBinding: Boolean) extends HttpOutputMemberBinding
}

final case class HttpOperationOutputMember(
    name: String,
    targetShape: ShapeId,
    typeName: String,
    timestampFormat: Option[HttpTimestampFormat],
    required: Boolean,
    binding: HttpOutputMemberBinding
)

final case class HttpOperationError(
    shapeId: ShapeId,
    name: String,
    statusCode: Int
)

final case class HttpResponseVariant(
    variantTypeName: String,
    statusCode: Int,
    mediaType: Option[String],
    headerBindings: List[(String, String)],
    staticHeaders: List[(String, String)],
    modelShapeId: ShapeId
)

final case class HttpOperationResponseBinding(
    successVariant: Option[HttpResponseVariant],
    errorVariants: List[HttpResponseVariant]
) {
  def allVariants: List[HttpResponseVariant] =
    successVariant.toList ++ errorVariants
}
