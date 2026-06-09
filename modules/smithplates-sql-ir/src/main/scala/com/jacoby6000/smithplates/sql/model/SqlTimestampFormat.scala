package com.jacoby6000.smithplates.sql

/** Resolved Smithy `@timestampFormat` storage semantics for SQL columns. */
sealed trait SqlTimestampFormat

object SqlTimestampFormat {
  case object DateTime     extends SqlTimestampFormat
  case object EpochSeconds extends SqlTimestampFormat

  val Default: SqlTimestampFormat = DateTime
}
