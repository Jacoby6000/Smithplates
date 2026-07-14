package com.jacoby6000.smithplates.sql.service.renderer

object ScalateTemplateHelpers {
  def generatedFileHeader(serviceShapeId: String, commentPrefix: String): String =
    s"$commentPrefix Generated from $serviceShapeId by ${internal.generatorName}. Do not edit by hand."

  /** Mustache-style truthiness for SSP conditionals over template attribute values. */
  def isTruthy(value: Any): Boolean =
    value match {
      case null                    => false
      case b: Boolean              => b
      case s: String               => s.nonEmpty
      case m: Map[?, ?] @unchecked =>
        m.nonEmpty
      case l: List[?] @unchecked   =>
        l.nonEmpty
      case other                   =>
        other != None
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val generatorName: String = "sql-service-codegen"
  }
}
