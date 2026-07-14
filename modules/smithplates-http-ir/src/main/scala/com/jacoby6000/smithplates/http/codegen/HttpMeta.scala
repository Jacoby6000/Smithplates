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
}

/** Operation-level HTTP feature metadata. */
final case class HttpResponseVariantMeta(
    variantTypeName: String,
    statusCode: Int,
    mediaType: Option[String] = None,
    headerBindings: List[(String, String)] = Nil,
    staticHeaders: List[(String, String)] = Nil
)

/** Operation-level HTTP feature metadata. */
final case class HttpOperationMeta(
    method: String,
    uriPattern: String,
    successStatus: Int,
    documentation: Option[String] = None,
    inputMembers: List[HttpOperationInputMemberMeta] = Nil,
    bodyBinding: HttpOperationBodyBindingMeta = HttpOperationBodyBindingMeta.None,
    responseVariants: List[HttpResponseVariantMeta] = Nil
)
