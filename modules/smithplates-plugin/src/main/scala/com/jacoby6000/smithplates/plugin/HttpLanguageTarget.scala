package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.http.service.renderer.HttpClientCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenSettings
import com.jacoby6000.smithplates.plugin.config.PluginConfigDecoders
import com.jacoby6000.smithplates.plugin.config.PluginConfigDecoding
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import io.circe.Json

final case class HttpServerTarget(
    webFramework: String,
    templateDirectory: Option[String],
    additionalTemplatesDirectory: Option[String] = None,
    packageName: Option[String]
) {
  def toCodegenSettings(
      languageId: String,
      routeGroupTags: List[String],
      rootNamespace: Option[String],
      modelsPackageNameOverride: Option[String],
      emitModels: Boolean,
      sourceOutputDir: String,
      testOutputDir: String
  ): SqlValidated[HttpServiceCodegenSettings] = {
    val _                       = routeGroupTags
    val serverTemplateDirectory =
      HttpLanguageTargetTemplateValidator.resolveServerTemplateDirectory(this, languageId)
    val modelsTemplateDirectory =
      HttpLanguageTargetTemplateValidator.defaultModelsTemplateDirectory(languageId)
    HttpLanguageTarget.internal
      .composedServerArtifacts(
        serverTemplateDirectory = serverTemplateDirectory,
        modelsTemplateDirectory = modelsTemplateDirectory,
        frameworkKeys = List(webFramework),
        emitModels = emitModels,
        additionalTemplatesDirectory = additionalTemplatesDirectory
      )
      .map { artifacts =>
        HttpLanguageTarget.buildCodegenSettings(
          languageId = languageId,
          frameworkKey = webFramework,
          templateDirectory = serverTemplateDirectory,
          rootNamespace = rootNamespace,
          packageNameOverride = packageName,
          modelsPackageNameOverride = modelsPackageNameOverride,
          emitModels = emitModels,
          sourceOutputDir = sourceOutputDir,
          testOutputDir = testOutputDir,
          artifacts = artifacts,
          modelTemplateDirectory = Some(modelsTemplateDirectory)
        )
      }
  }
}

final case class HttpClientTarget(
    httpLibrary: String,
    templateDirectory: Option[String],
    additionalTemplatesDirectory: Option[String] = None,
    packageName: Option[String]
) {
  def toCodegenSettings(
      languageId: String,
      routeGroupTags: List[String],
      rootNamespace: Option[String],
      modelsPackageNameOverride: Option[String],
      emitModels: Boolean,
      sourceOutputDir: String,
      testOutputDir: String
  ): SqlValidated[HttpServiceCodegenSettings] = {
    val _                       = routeGroupTags
    val clientTemplateDirectory =
      HttpLanguageTargetTemplateValidator.resolveClientTemplateDirectory(this, languageId)
    val modelsTemplateDirectory =
      HttpLanguageTargetTemplateValidator.defaultModelsTemplateDirectory(languageId)
    HttpLanguageTarget.internal
      .composedClientArtifacts(
        clientTemplateDirectory = clientTemplateDirectory,
        modelsTemplateDirectory = modelsTemplateDirectory,
        libraryKeys = List(httpLibrary),
        emitModels = emitModels,
        additionalTemplatesDirectory = additionalTemplatesDirectory
      )
      .map { artifacts =>
        HttpLanguageTarget.buildCodegenSettings(
          languageId = languageId,
          frameworkKey = httpLibrary,
          templateDirectory = clientTemplateDirectory,
          rootNamespace = rootNamespace,
          packageNameOverride = packageName,
          modelsPackageNameOverride = modelsPackageNameOverride,
          emitModels = emitModels,
          sourceOutputDir = sourceOutputDir,
          testOutputDir = testOutputDir,
          artifacts = artifacts,
          modelTemplateDirectory = Some(modelsTemplateDirectory)
        )
      }
  }
}

final case class HttpLanguageTarget(
    server: Option[HttpServerTarget],
    client: Option[HttpClientTarget],
    rootNamespace: Option[String],
    modelsPackageName: Option[String]
)

object HttpLanguageTarget {
  def parse(
      languageId: String,
      json: Json
  ): SqlValidated[HttpLanguageTarget] = {
    import PluginConfigDecoders.given
    PluginConfigDecoding
      .decode[PluginConfigDecoders.internal.HttpLanguageTargetJson](languageId, "http", json)
      .andThen { config =>
        if (config.server.isEmpty && config.client.isEmpty) {
          InvalidPluginConfig(
            s"smithplates.$languageId.http requires `server` and/or `client`"
          ).invalidNel
        } else {
          config.toDomain.validNel
        }
      }
  }

  private[plugin] def buildCodegenSettings(
      languageId: String,
      frameworkKey: String,
      templateDirectory: String,
      rootNamespace: Option[String],
      packageNameOverride: Option[String],
      modelsPackageNameOverride: Option[String],
      emitModels: Boolean,
      sourceOutputDir: String,
      testOutputDir: String,
      artifacts: List[CodegenOutput],
      modelTemplateDirectory: Option[String]
  ): HttpServiceCodegenSettings =
    HttpServiceCodegenSettings(
      templateDirectory = templateDirectory,
      defaultFrameworkKey = frameworkKey,
      enabledFrameworkKeys = List(frameworkKey),
      sourceOutputDirectory = Some(sourceOutputDir),
      testOutputDirectory = Some(testOutputDir),
      artifacts = artifacts,
      rootNamespace = rootNamespace,
      packageNameOverride = packageNameOverride,
      modelsPackageNameOverride = modelsPackageNameOverride,
      emitModels = emitModels,
      modelTemplateDirectory = modelTemplateDirectory
    )

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def composedServerArtifacts(
        serverTemplateDirectory: String,
        modelsTemplateDirectory: String,
        frameworkKeys: List[String],
        emitModels: Boolean,
        additionalTemplatesDirectory: Option[String]
    ): SqlValidated[List[CodegenOutput]] =
      HttpServiceCodegenApiArtifacts
        .frameworkArtifacts(
          serverTemplateDirectory = serverTemplateDirectory,
          modelsTemplateDirectory = modelsTemplateDirectory,
          frameworkKeys = frameworkKeys,
          emitModels = emitModels
        )
        .andThen { bundled =>
          additionalTemplatesDirectory match {
            case None                => bundled.validNel
            case Some(additionalDir) =>
              ConsumerCodegenOutputs
                .additionalOutputs(additionalDir, frameworkKeys, getClass.getClassLoader)
                .map(ConsumerCodegenOutputs.compose(bundled, _))
          }
        }
        .leftMap(_.map(error => InvalidPluginConfig(error.message)))

    def composedClientArtifacts(
        clientTemplateDirectory: String,
        modelsTemplateDirectory: String,
        libraryKeys: List[String],
        emitModels: Boolean,
        additionalTemplatesDirectory: Option[String]
    ): SqlValidated[List[CodegenOutput]] =
      (
        HttpClientCodegenApiArtifacts.libraryArtifacts(clientTemplateDirectory, libraryKeys),
        if (emitModels) {
          HttpServiceCodegenApiArtifacts.modelArtifacts(modelsTemplateDirectory)
        } else {
          List.empty[CodegenOutput].validNel
        }
      ).mapN(_ ++ _)
        .andThen { bundled =>
          additionalTemplatesDirectory match {
            case None                => bundled.validNel
            case Some(additionalDir) =>
              ConsumerCodegenOutputs
                .additionalOutputs(additionalDir, libraryKeys, getClass.getClassLoader)
                .map(ConsumerCodegenOutputs.compose(bundled, _))
          }
        }
        .leftMap(_.map(error => InvalidPluginConfig(error.message)))
  }
}
