package com.jacoby6000.smithplates.http.codegen

import com.jacoby6000.smithplates.codegen.core.ModelId

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
    serviceErrors: List[HttpServiceErrorMeta] = Nil
)

/** Operation-level HTTP feature metadata. */
final case class HttpOperationMeta(
    method: String,
    uriPattern: String,
    successStatus: Int
)
