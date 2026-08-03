package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.CodegenTemplatePaths
import com.jacoby6000.smithplates.http.service.renderer.HttpClientCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpCodegenTemplatePaths
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.ScalateSspTemplateEngine
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig

import java.nio.file.Files
import java.nio.file.Paths

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
  ): SqlValidated[Unit] = {
    val serverTemplateDirectory = resolveServerTemplateDirectory(target, languageId)
    PluginConstants
      .requireTemplateDirectoryForLanguage(languageId, "http.server", target.templateDirectory)
      .andThen(_ =>
        HttpLanguageTarget.internal
          .resolveFrameworkKey(languageId, "http.server.webFramework", serverTemplateDirectory, target.webFramework)
          .andThen(frameworkKey =>
            internal.loadedArtifacts(HttpServiceCodegenApiArtifacts.frameworkArtifacts(
              serverTemplateDirectory = serverTemplateDirectory,
              modelsTemplateDirectory = defaultModelsTemplateDirectory(languageId),
              frameworkKeys = if (frameworkKey.isEmpty) Nil else List(frameworkKey),
              emitModels = true
            ))))
      .andThen(artifacts =>
        internal.validateRequiredArtifactsExist(
          languageId = languageId,
          defaultTemplateDirectory = serverTemplateDirectory,
          artifacts = artifacts
        ))
  }

  def validateClient(
      languageId: String,
      target: HttpClientTarget,
      emitModels: Boolean
  ): SqlValidated[Unit] = {
    val clientTemplateDirectory = resolveClientTemplateDirectory(target, languageId)
    PluginConstants
      .requireTemplateDirectoryForLanguage(languageId, "http.client", target.templateDirectory)
      .andThen(_ =>
        HttpLanguageTarget.internal
          .resolveClientLibraryKey(languageId, clientTemplateDirectory, target.httpLibrary)
          .andThen { libraryKey =>
            val variantKeys = if (libraryKey.isEmpty) Nil else target.mode.variantKeys(libraryKey)
            internal.loadedArtifacts(
              (
                HttpClientCodegenApiArtifacts.libraryArtifacts(
                  clientTemplateDirectory,
                  variantKeys
                ),
                if (emitModels) {
                  HttpServiceCodegenApiArtifacts.modelArtifacts(defaultModelsTemplateDirectory(languageId))
                } else {
                  List.empty[CodegenOutput].validNel
                }
              ).mapN(_ ++ _))
          })
      .andThen(artifacts =>
        internal.validateRequiredArtifactsExist(
          languageId = languageId,
          defaultTemplateDirectory = clientTemplateDirectory,
          artifacts = artifacts
        ))
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def loadedArtifacts(artifacts: CodegenValidated[List[CodegenOutput]]): SqlValidated[List[CodegenOutput]] =
      artifacts.leftMap(_.map(error => InvalidPluginConfig(error.message)))

    def validateRequiredArtifactsExist(
        languageId: String,
        defaultTemplateDirectory: String,
        artifacts: List[CodegenOutput]
    ): SqlValidated[Unit] = {
      val missingTemplates =
        artifacts
          .flatMap(HttpServiceCodegenApiArtifacts.templatePath)
          .map { template =>
            if (CodegenTemplatePaths.isFileQualified(template)) {
              (CodegenTemplatePaths.filePath(template), template)
            } else if (ConsumerCodegenOutputs.isAdditionalTemplatePath(template)) {
              (template.stripPrefix("classpath:"), template)
            } else {
              val templateDirectory = resolvedArtifactTemplateDirectory(languageId, defaultTemplateDirectory, template)
              (
                PluginTemplatePaths
                  .classpathResourcePath(templateDirectory, stripTemplateDirectoryPrefix(template))
                  .stripPrefix("/"),
                template
              )
            }
          }
          .distinct
          .filterNot { case (resourcePath, template) =>
            if (CodegenTemplatePaths.isFileQualified(template)) {
              Files.isRegularFile(Paths.get(resourcePath))
            } else {
              ScalateSspTemplateEngine.classpathResourceExists(resourcePath)
            }
          }

      if (missingTemplates.isEmpty) {
        ().validNel
      } else {
        SqlValidated.invalid(
          InvalidPluginConfig(
            s"smithplates.$languageId.http is missing required templates: " +
              missingTemplates
                .map { case (resourcePath, template) => s"$resourcePath ($template)" }
                .sorted
                .mkString(", ")
          )
        )
      }
    }

    def resolvedArtifactTemplateDirectory(
        languageId: String,
        defaultTemplateDirectory: String,
        templatePath: String
    ): String =
      HttpCodegenTemplatePaths.resolvedTemplateDirectory(
        defaultTemplateDirectory,
        defaultModelsTemplateDirectory(languageId),
        templatePath
      )

    def stripTemplateDirectoryPrefix(templatePath: String): String =
      HttpCodegenTemplatePaths.stripTemplateDirectoryPrefix(templatePath)
  }
}
