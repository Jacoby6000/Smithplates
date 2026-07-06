package com.jacoby6000.smithplates.http.codegen

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

/** Service-level HTTP feature metadata. */
final case class HttpServiceMeta()

/** Operation-level HTTP feature metadata. */
final case class HttpOperationMeta(
    method: String,
    uriPattern: String,
    successStatus: Int
)
