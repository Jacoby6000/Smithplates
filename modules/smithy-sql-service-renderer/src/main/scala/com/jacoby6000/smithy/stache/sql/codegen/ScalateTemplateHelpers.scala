package com.jacoby6000.smithy.stache.sql.codegen

object ScalateTemplateHelpers {

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
}
