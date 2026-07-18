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

  /** A single ``@httpPayload`` member carries ``@nestedProperties``, so its target structure's fields are serialized as
    * top-level properties of the request body (flattened, not wrapped under the member name). The server route should
    * accept the payload target as the body and reconstruct the input shape before dispatching to the service.
    * `inputShape` is the wrapping operation input structure (e.g. ``CreateProjectInput``); `payloadMember` is the
    * ``@nestedProperties`` member whose target (e.g. ``ProjectCreateRequest``) is the flattened body type.
    */
  final case class NestedDocument(
      inputShape: ShapeId,
      payloadMember: HttpOperationInputMember
  ) extends HttpOperationBodyBinding
}
