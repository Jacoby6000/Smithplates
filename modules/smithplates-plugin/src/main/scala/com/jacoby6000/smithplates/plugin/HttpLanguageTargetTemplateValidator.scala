package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.PythonTemplateNamespaces
import com.jacoby6000.smithplates.http.service.renderer.ScalateSspTemplateEngine
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig

object HttpLanguageTargetTemplateValidator {
  val bundledLanguageIds: Set[String] = Set("python")

  def defaultTemplateDirectory(languageId: String): String =
    languageId match {
      case "python" => PythonTemplateNamespaces.bundledHttpTemplateDirectory
      case other    => s"classpath:templates/$other/src/http"
    }

  def resolveTemplateDirectory(target: HttpLanguageTarget, languageId: String): String =
    target.templateDirectory.getOrElse(defaultTemplateDirectory(languageId))

  def validate(
      languageId: String,
      target: HttpLanguageTarget
  ): SqlValidated[Unit] = {
    val normalizedLanguageId = languageId.toLowerCase
    if (!bundledLanguageIds.contains(normalizedLanguageId) && target.templateDirectory.isEmpty) {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithplates http.$languageId requires `templateDirectory` " +
            s"because bundled templates are not available for '$languageId'; " +
            s"supported bundled languages: ${bundledLanguageIds.toList.sorted.mkString(", ")}"
        )
      )
    } else {
      validateRequiredTemplatesExist(
        languageId = languageId,
        templateDirectory = resolveTemplateDirectory(target, languageId),
        webFramework = target.webFramework
      )
    }
  }

  private def validateRequiredTemplatesExist(
      languageId: String,
      templateDirectory: String,
      webFramework: String
  ): SqlValidated[Unit] = {
    val requiredTemplates =
      HttpServiceCodegenApiArtifacts
        .forEnabledFrameworks(List(webFramework), List("placeholder"))
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
          s"smithplates http.$languageId templateDirectory '$templateDirectory' " +
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
    if (baseDirectory.isEmpty) {
      s"/$normalizedTemplate"
    } else {
      s"/$baseDirectory/$normalizedTemplate"
    }
  }
}
