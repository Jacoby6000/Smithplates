package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
object PluginConstants {
  val DefaultRootNamespace: String = "generated"

  def bundledLanguageIds: Set[String] = {
    val classLoader = getClass.getClassLoader
    Option(classLoader.getResourceAsStream("META-INF/smithplates/bundled_languages.json")) match {
      case Some(stream) =>
        try
          io.circe.parser
            .parse(scala.io.Source.fromInputStream(stream, "UTF-8").mkString)
            .flatMap(_.as[List[String]])
            .getOrElse(Set.empty[String])
            .map(_.toLowerCase)
            .toSet
        finally stream.close()
      case None         =>
        Set.empty[String]
    }
  }

  def isBundledLanguage(languageId: String): Boolean =
    bundledLanguageIds.contains(languageId.toLowerCase)

  def resolvedRootNamespace(languageId: String, explicitRootNamespace: Option[String]): Option[String] =
    explicitRootNamespace.orElse(
      if (bundledLanguageIds.contains(languageId.toLowerCase)) {
        Some(DefaultRootNamespace)
      } else {
        None
      }
    )

  def requireTemplateDirectoryForLanguage(
      languageId: String,
      configPath: String,
      templateDirectory: Option[String]
  ): SqlValidated[Unit] =
    if (!bundledLanguageIds.contains(languageId.toLowerCase) && templateDirectory.isEmpty) {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithplates.$languageId.$configPath requires `templateDirectory` " +
            s"because bundled templates are not available for '$languageId'; " +
            s"supported bundled languages: ${bundledLanguageIds.toList.sorted.mkString(", ")}"
        )
      )
    } else {
      ().validNel
    }
}
