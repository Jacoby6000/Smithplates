package com.jacoby6000.smithplates.http.model

/** Resolved Smithy `@timestampFormat` semantics for HTTP members. */
sealed trait HttpTimestampFormat

object HttpTimestampFormat {
  case object DateTime     extends HttpTimestampFormat
  case object EpochSeconds extends HttpTimestampFormat
  case object HttpDate     extends HttpTimestampFormat

  val Default: HttpTimestampFormat = DateTime
}
