package com.jacoby6000.smithplates.http.codegen

import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.http.model.HttpTimestampFormat

/** Model-level HTTP feature metadata carried on [[com.jacoby6000.smithplates.codegen.core.ModelMeta]]. */
sealed trait HttpMeta

object HttpMeta {
  final case class HttpResponseMeta(
      statusCode: Int,
      staticHeaders: Map[String, String] = Map.empty,
      dynamicHeaderFields: Map[String, String] = Map.empty,
      error: Option[HttpErrorMeta] = None
  ) extends HttpMeta

  final case class HttpRequestMeta(
      staticHeaders: Map[String, String] = Map.empty,
      dynamicHeaderFields: Map[String, String] = Map.empty
  ) extends HttpMeta

  case object HttpNestedField extends HttpMeta
}

final case class HttpErrorMeta(
    problemType: Option[String],
    title: Option[String],
    defaultDetail: Option[String] = None
)

/** Smithy service error metadata carried on [[HttpServiceMeta]]. */
final case class HttpServiceErrorMeta(
    id: ModelId,
    statusCode: Int,
    error: Option[HttpErrorMeta] = None
) {
  def name: String = id.name
}

/** Service-level HTTP feature metadata. */
final case class HttpServiceMeta(
    title: Option[String] = None,
    version: String = "",
    serviceErrors: List[HttpServiceErrorMeta] = Nil,
    modelNamespaces: Map[String, String] = Map.empty,
    emittedModelIds: Set[ModelId] = Set.empty
)

sealed trait HttpInputMemberBindingMeta

object HttpInputMemberBindingMeta {
  case object PathLabel extends HttpInputMemberBindingMeta

  final case class Query(queryName: String) extends HttpInputMemberBindingMeta

  final case class Header(headerName: String) extends HttpInputMemberBindingMeta

  case object Payload extends HttpInputMemberBindingMeta
}

final case class HttpOperationInputMemberMeta(
    name: String,
    typeName: String,
    timestampFormat: Option[HttpTimestampFormat] = None,
    required: Boolean,
    binding: HttpInputMemberBindingMeta
)

sealed trait HttpOperationBodyBindingMeta

object HttpOperationBodyBindingMeta {
  case object None extends HttpOperationBodyBindingMeta

  final case class Document(inputShapeName: String) extends HttpOperationBodyBindingMeta

  final case class Members(members: List[HttpOperationInputMemberMeta]) extends HttpOperationBodyBindingMeta

  /** A single ``@httpPayload`` member with ``@nestedProperties``: the wire body is the payload member's target
    * structure (flattened), and the route reconstructs the input shape before dispatching. `inputShapeName` is the
    * wrapping input (e.g. ``CreateProjectInput``); `payloadMemberName` is the member name (e.g. ``body``);
    * `payloadTargetShapeName` is the flattened body type (e.g. ``ProjectCreateRequest``).
    */
  final case class NestedDocument(
      inputShapeName: String,
      payloadMemberName: String,
      payloadTargetShapeName: String
  ) extends HttpOperationBodyBindingMeta
}

/** Operation-level HTTP feature metadata. */
final case class HttpResponseVariantMeta(
    variantTypeName: String,
    statusCode: Int,
    mediaType: Option[String] = None,
    headerBindings: List[(String, String)] = Nil,
    staticHeaders: List[(String, String)] = Nil,
    modelShapeId: Option[ModelId] = None
)

/** Websocket operation metadata. A websocket operation is a bidirectional endpoint whose input shape is the
  * union/structure of client-to-server messages and whose output shape is the union/structure of server-to-client
  * messages. `path` is the WebSocket route path (taken from the `@http` uri).
  */
final case class HttpWebsocketMeta(
    path: String
)

/** Operation-level HTTP feature metadata. */
final case class HttpOperationMeta(
    method: String,
    uriPattern: String,
    successStatus: Int,
    documentation: Option[String] = None,
    inputMembers: List[HttpOperationInputMemberMeta] = Nil,
    bodyBinding: HttpOperationBodyBindingMeta = HttpOperationBodyBindingMeta.None,
    responseVariants: List[HttpResponseVariantMeta] = Nil,
    websocket: Option[HttpWebsocketMeta] = None
)
