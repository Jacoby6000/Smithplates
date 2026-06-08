package com.jacoby6000.smithy.stache.sql.codegen

object ScalateTemplateHelpers {
  private val generatorName = "sql-service-codegen"

  def generatedFileHeader(serviceShapeId: String, language: String): String =
    language match {
      case "python" =>
        s"# Generated from $serviceShapeId by $generatorName. Do not edit by hand."
      case other    =>
        throw new IllegalArgumentException(s"unsupported codegen language for generated file header: $other")
    }

  def languageFromTemplateRoot(templateRoot: String): String = {
    val normalized =
      templateRoot.stripPrefix("classpath:").stripPrefix("/").stripSuffix("/")
    if (normalized.isEmpty) {
      "python"
    } else {
      normalized.split("/").toList match {
        case "language-templates" :: language :: _ =>
          language
        case "custom-templates" :: language :: _   =>
          language
        case _                                     =>
          throw new IllegalArgumentException(s"cannot infer codegen language from template root: $templateRoot")
      }
    }
  }

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
