package com.jacoby6000.smithplates.http.model

import software.amazon.smithy.model.shapes.ShapeId

/** How an operation's HTTP request body is bound per Smithy HTTP bindings. */
sealed trait HttpOperationBodyBinding

object HttpOperationBodyBinding {
  case object None extends HttpOperationBodyBinding

  /** All unbound input members serialize as one JSON document (the input structure). */
  final case class Document(inputShape: ShapeId) extends HttpOperationBodyBinding

  /** Remaining payload members alongside URI/query/header bindings. */
  final case class Members(members: List[HttpOperationInputMember]) extends HttpOperationBodyBinding
}
