package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.service.renderer.HttpClientCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpCodegenTemplateSource
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenArtifactConfig
import com.jacoby6000.smithplates.http.service.renderer.ScalateSspTemplateEngine
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig

object HttpLanguageTargetTemplateValidator {
  def defaultServerTemplateDirectory(languageId: String): String =
    PluginTemplatePaths.defaultHttpTemplateDirectory(languageId, "http/server")

  def defaultClientTemplateDirectory(languageId: String): String =
    PluginTemplatePaths.defaultHttpTemplateDirectory(languageId, "http/client")

  def defaultModelsTemplateDirectory(languageId: String): String =
    PluginTemplatePaths.defaultHttpTemplateDirectory(languageId, "http/models")

  def resolveServerTemplateDirectory(target: HttpServerTarget, languageId: String): String =
    target.templateDirectory.getOrElse(defaultServerTemplateDirectory(languageId))

  def resolveClientTemplateDirectory(target: HttpClientTarget, languageId: String): String =
    target.templateDirectory.getOrElse(defaultClientTemplateDirectory(languageId))

  def validateServer(
      languageId: String,
      target: HttpServerTarget
  ): SqlValidated[Unit] =
    PluginConstants
      .requireTemplateDirectoryForLanguage(languageId, "http.server", target.templateDirectory)
      .andThen(_ =>
        validateRequiredArtifactsExist(
          languageId = languageId,
          defaultTemplateDirectory = resolveServerTemplateDirectory(target, languageId),
          artifacts = HttpServiceCodegenApiArtifacts
            .forEnabledFrameworks(List(target.webFramework), List("placeholder"), emitModels = true)
        ))

  def validateClient(
      languageId: String,
      target: HttpClientTarget,
      emitModels: Boolean
  ): SqlValidated[Unit] = {
    val clientArtifacts = HttpClientCodegenApiArtifacts
      .forEnabledLibraries(List(target.httpLibrary), List("placeholder"))
    val modelArtifacts  = if (emitModels) HttpServiceCodegenApiArtifacts.sharedModels else Nil
    PluginConstants
      .requireTemplateDirectoryForLanguage(languageId, "http.client", target.templateDirectory)
      .andThen(_ =>
        validateRequiredArtifactsExist(
          languageId = languageId,
          defaultTemplateDirectory = resolveClientTemplateDirectory(target, languageId),
          artifacts = clientArtifacts ++ modelArtifacts
        ))
  }

  private def validateRequiredArtifactsExist(
      languageId: String,
      defaultTemplateDirectory: String,
      artifacts: List[HttpServiceCodegenArtifactConfig]
  ): SqlValidated[Unit] = {
    val missingTemplates =
      artifacts
        .map { artifact =>
          val templateDirectory = resolvedArtifactTemplateDirectory(languageId, defaultTemplateDirectory, artifact)
          (templateDirectory, artifact.template)
        }
        .distinct
        .filterNot { case (templateDirectory, template) =>
          ScalateSspTemplateEngine.classpathResourceExists(
            PluginTemplatePaths.classpathResourcePath(templateDirectory, template)
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

  private def resolvedArtifactTemplateDirectory(
      languageId: String,
      defaultTemplateDirectory: String,
      artifact: HttpServiceCodegenArtifactConfig
  ): String =
    artifact.templateSource match {
      case HttpCodegenTemplateSource.Service => defaultTemplateDirectory
      case HttpCodegenTemplateSource.Models  => defaultModelsTemplateDirectory(languageId)
    }
}
