package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.service.renderer.HttpClientCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenArtifactConfig
import com.jacoby6000.smithplates.http.service.renderer.PythonTemplateNamespaces
import com.jacoby6000.smithplates.http.service.renderer.ScalateSspTemplateEngine
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig

object HttpLanguageTargetTemplateValidator {
  val bundledLanguageIds: Set[String] = Set("python")

  def defaultServerTemplateDirectory(languageId: String): String =
    languageId match {
      case "python" => PythonTemplateNamespaces.bundledHttpServerTemplateDirectory
      case other    => s"classpath:templates/$other/src/http/server"
    }

  def defaultClientTemplateDirectory(languageId: String): String =
    languageId match {
      case "python" => PythonTemplateNamespaces.bundledHttpClientTemplateDirectory
      case other    => s"classpath:templates/$other/src/http/client"
    }

  def resolveServerTemplateDirectory(target: HttpServerTarget, languageId: String): String =
    target.templateDirectory.getOrElse(defaultServerTemplateDirectory(languageId))

  def resolveClientTemplateDirectory(target: HttpClientTarget, languageId: String): String =
    target.templateDirectory.getOrElse(defaultClientTemplateDirectory(languageId))

  def validateServer(
      languageId: String,
      target: HttpServerTarget
  ): SqlValidated[Unit] = {
    val normalizedLanguageId = languageId.toLowerCase
    if (!bundledLanguageIds.contains(normalizedLanguageId) && target.templateDirectory.isEmpty) {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithplates.$languageId.http.server requires `templateDirectory` " +
            s"because bundled templates are not available for '$languageId'; " +
            s"supported bundled languages: ${bundledLanguageIds.toList.sorted.mkString(", ")}"
        )
      )
    } else {
      validateRequiredArtifactsExist(
        languageId = languageId,
        defaultTemplateDirectory = resolveServerTemplateDirectory(target, languageId),
        artifacts = HttpServiceCodegenApiArtifacts
          .forEnabledFrameworks(List(target.webFramework), List("placeholder"))
      )
    }
  }

  def validateClient(
      languageId: String,
      target: HttpClientTarget
  ): SqlValidated[Unit] = {
    val normalizedLanguageId = languageId.toLowerCase
    if (!bundledLanguageIds.contains(normalizedLanguageId) && target.templateDirectory.isEmpty) {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithplates.$languageId.http.client requires `templateDirectory` " +
            s"because bundled templates are not available for '$languageId'; " +
            s"supported bundled languages: ${bundledLanguageIds.toList.sorted.mkString(", ")}"
        )
      )
    } else {
      validateRequiredArtifactsExist(
        languageId = languageId,
        defaultTemplateDirectory = resolveClientTemplateDirectory(target, languageId),
        artifacts = HttpClientCodegenApiArtifacts
          .forEnabledLibraries(List(target.httpLibrary), List("placeholder"))
      )
    }
  }

  private def validateRequiredArtifactsExist(
      languageId: String,
      defaultTemplateDirectory: String,
      artifacts: List[HttpServiceCodegenArtifactConfig]
  ): SqlValidated[Unit] = {
    val missingTemplates =
      artifacts
        .map { artifact =>
          val templateDirectory = artifact.templateDirectoryOverride.getOrElse(defaultTemplateDirectory)
          (templateDirectory, artifact.template)
        }
        .distinct
        .filterNot { case (templateDirectory, template) =>
          ScalateSspTemplateEngine.classpathResourceExists(
            classpathResourcePath(templateDirectory, template)
          )
        }

    if (missingTemplates.isEmpty) {
      ().validNel
    } else {
      SqlValidated.invalid(
        InvalidPluginConfig(
          s"smithplates.$languageId.http is missing required templates: " +
            missingTemplates.map { case (directory, template) => s"$directory/$template" }.sorted.mkString(", ")
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
