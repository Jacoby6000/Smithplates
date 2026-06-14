package com.jacoby6000.smithplates.http.model

import software.amazon.smithy.model.shapes.ShapeId

sealed trait HttpInputMemberBinding

object HttpInputMemberBinding {
  final case class PathLabel() extends HttpInputMemberBinding

  final case class Query(queryName: String) extends HttpInputMemberBinding

  final case class Header(headerName: String) extends HttpInputMemberBinding

  final case class Payload() extends HttpInputMemberBinding
}

final case class HttpOperationInputMember(
    name: String,
    targetShape: ShapeId,
    typeName: String,
    timestampFormat: Option[HttpTimestampFormat],
    required: Boolean,
    binding: HttpInputMemberBinding,
    resourceIdentifierName: Option[String]
)
