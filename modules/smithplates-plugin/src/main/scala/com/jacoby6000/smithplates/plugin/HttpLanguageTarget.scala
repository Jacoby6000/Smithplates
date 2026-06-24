package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
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
  ): HttpServiceCodegenSettings =
    HttpLanguageTarget.buildCodegenSettings(
      languageId = languageId,
      frameworkKey = webFramework,
      templateDirectory = HttpLanguageTargetTemplateValidator.resolveServerTemplateDirectory(this, languageId),
      rootNamespace = rootNamespace,
      packageNameOverride = packageName,
      modelsPackageNameOverride = modelsPackageNameOverride,
      emitModels = emitModels,
      sourceOutputDir = sourceOutputDir,
      testOutputDir = testOutputDir,
      artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(
        List(webFramework),
        routeGroupTags,
        emitModels
      )
    )
}

final case class HttpClientTarget(
    httpLibrary: String,
    templateDirectory: Option[String],
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
  ): HttpServiceCodegenSettings = {
    val clientArtifacts = HttpClientCodegenApiArtifacts.forEnabledLibraries(List(httpLibrary), routeGroupTags)
    val modelArtifacts  = if (emitModels) HttpServiceCodegenApiArtifacts.sharedModels else Nil
    HttpLanguageTarget.buildCodegenSettings(
      languageId = languageId,
      frameworkKey = httpLibrary,
      templateDirectory = HttpLanguageTargetTemplateValidator.resolveClientTemplateDirectory(this, languageId),
      rootNamespace = rootNamespace,
      packageNameOverride = packageName,
      modelsPackageNameOverride = modelsPackageNameOverride,
      emitModels = emitModels,
      sourceOutputDir = sourceOutputDir,
      testOutputDir = testOutputDir,
      artifacts = clientArtifacts ++ modelArtifacts
    )
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
      artifacts: List[com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenArtifactConfig]
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
      modelTemplateDirectory = Some(HttpLanguageTargetTemplateValidator.defaultModelsTemplateDirectory(languageId))
    )
}
