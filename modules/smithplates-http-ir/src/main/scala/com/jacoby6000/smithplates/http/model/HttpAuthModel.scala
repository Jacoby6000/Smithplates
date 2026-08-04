package com.jacoby6000.smithplates.http.model

import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.synthetic.NoAuthTrait

sealed trait HttpAuthScheme {
  def id: ShapeId
}

object HttpAuthScheme {
  final case class Bearer(id: ShapeId) extends HttpAuthScheme

  final case class ApiKey(
      id: ShapeId,
      name: String,
      location: HttpApiKeyLocation,
      scheme: Option[String]
  ) extends HttpAuthScheme

  final case class Cookie(id: ShapeId, name: String) extends HttpAuthScheme
}

enum HttpApiKeyLocation {
  case Header
  case Query
}

final case class HttpAuthAlternative(schemeId: ShapeId)

object HttpAuthAlternative {
  val NoAuth: HttpAuthAlternative = HttpAuthAlternative(NoAuthTrait.ID)
}
