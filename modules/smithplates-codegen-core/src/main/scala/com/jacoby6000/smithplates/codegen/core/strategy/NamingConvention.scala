package com.jacoby6000.smithplates.codegen.core.strategy

enum NamingConventionStyle {
  case SnakeCase, PascalCase, CamelCase, ScreamingSnakeCase, Unchanged
}

/** Declarative casing (and optional suffix) for one identifier role in a [[NamingStrategy]]. */
final case class NamingConvention(style: NamingConventionStyle, suffix: String = "")

object NamingConvention {
  val SnakeCase: NamingConvention          = NamingConvention(NamingConventionStyle.SnakeCase)
  val PascalCase: NamingConvention         = NamingConvention(NamingConventionStyle.PascalCase)
  val CamelCase: NamingConvention          = NamingConvention(NamingConventionStyle.CamelCase)
  val ScreamingSnakeCase: NamingConvention = NamingConvention(NamingConventionStyle.ScreamingSnakeCase)
  val Unchanged: NamingConvention          = NamingConvention(NamingConventionStyle.Unchanged)

  extension (convention: NamingConvention) {
    def withSuffix(suffix: String): NamingConvention =
      convention.copy(suffix = suffix)
  }
}
