package com.jacoby6000.smithy.stache

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.InvalidPluginConfig
import com.jacoby6000.smithy.stache.sql.SqlValidated
import com.jacoby6000.smithy.stache.sql.codegen.ScalateSspTemplateEngine
import com.jacoby6000.smithy.stache.sql.codegen.SqlServiceCodegenDbArtifacts

object LanguageTargetTemplateValidator {
  val bundledLanguageIds: Set[String] = Set("python")

  def defaultTemplateDirectory(languageId: String): String =
    s"classpath:sql-service-codegen/$languageId"

  def resolveTemplateDirectory(target: LanguageTarget, languageId: String): String =
    target.templateDirectory.getOrElse(defaultTemplateDirectory(languageId))

  def validate(
      languageId: String,
      target: LanguageTarget,
      enabledDialectKeys: List[String]
  ): SqlValidated[Unit] = {
    val normalizedLanguageId = languageId.toLowerCase
    if (!bundledLanguageIds.contains(normalizedLanguageId) && target.templateDirectory.isEmpty) {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithy-stache sql.languageTargets.$languageId requires `templateDirectory` " +
            s"because bundled templates are not available for '$languageId'; " +
            s"supported bundled languages: ${bundledLanguageIds.toList.sorted.mkString(", ")}"
        )
      )
    } else {
      validateRequiredTemplatesExist(
        languageId = languageId,
        templateDirectory = resolveTemplateDirectory(target, languageId),
        enabledDialectKeys = enabledDialectKeys
      )
    }
  }

  private def validateRequiredTemplatesExist(
      languageId: String,
      templateDirectory: String,
      enabledDialectKeys: List[String]
  ): SqlValidated[Unit] = {
    val requiredTemplates =
      SqlServiceCodegenDbArtifacts
        .forEnabledDialects(enabledDialectKeys)
        .map(_.template)
        .distinct

    val missingTemplates =
      requiredTemplates.filterNot { template =>
        ScalateSspTemplateEngine.classpathResourceExists(
          classpathResourcePath(templateDirectory, template)
        )
      }

    if (missingTemplates.isEmpty) {
      ().validNel
    } else {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithy-stache sql.languageTargets.$languageId templateDirectory '$templateDirectory' " +
            s"is missing required templates: ${missingTemplates.sorted.mkString(", ")}"
        )
      )
    }
  }

  private def classpathResourcePath(templateDirectory: String, template: String): String = {
    val baseDirectory      = templateDirectory.stripPrefix("classpath:").stripSuffix("/")
    val normalizedTemplate =
      if (template.startsWith("/")) {
        template.stripPrefix("/")
      } else {
        template
      }
    s"/$baseDirectory/$normalizedTemplate"
  }
}
