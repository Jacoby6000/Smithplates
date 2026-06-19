package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig

object PluginConstants {
  val BundledLanguageIds: Set[String] = Set("python")

  val DefaultRootNamespace: String = "generated"

  def resolvedRootNamespace(languageId: String, explicitRootNamespace: Option[String]): Option[String] =
    explicitRootNamespace.orElse(
      if (BundledLanguageIds.contains(languageId.toLowerCase)) {
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
    if (!BundledLanguageIds.contains(languageId.toLowerCase) && templateDirectory.isEmpty) {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithplates.$languageId.$configPath requires `templateDirectory` " +
            s"because bundled templates are not available for '$languageId'; " +
            s"supported bundled languages: ${BundledLanguageIds.toList.sorted.mkString(", ")}"
        )
      )
    } else {
      ().validNel
    }

  def validateSupportedValue(
      languageId: String,
      configPath: String,
      value: String,
      supported: Set[String]
  ): SqlValidated[Unit] =
    if (supported.contains(value)) {
      ().validNel
    } else {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithplates.$languageId.$configPath '$value' is not supported; " +
            s"supported values: ${supported.toList.sorted.mkString(", ")}"
        )
      )
    }
}
