package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.config.CodegenOutputDeckLoader
import com.jacoby6000.smithplates.http.service.renderer.HttpClientCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenSettings
import com.jacoby6000.smithplates.plugin.config.PluginConfigDecoders
import com.jacoby6000.smithplates.plugin.config.PluginConfigDecoding
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import io.circe.Json

final case class HttpServerTarget(
    webFramework: Option[String],
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
      .resolveFrameworkKey(languageId, "http.server.webFramework", serverTemplateDirectory, webFramework)
      .andThen { frameworkKey =>
        HttpLanguageTarget.internal
          .composedServerArtifacts(
            serverTemplateDirectory = serverTemplateDirectory,
            modelsTemplateDirectory = modelsTemplateDirectory,
            frameworkKeys = if (frameworkKey.isEmpty) Nil else List(frameworkKey),
            emitModels = emitModels,
            additionalTemplatesDirectory = additionalTemplatesDirectory
          )
          .map { artifacts =>
            HttpLanguageTarget.buildCodegenSettings(
              languageId = languageId,
              frameworkKey = frameworkKey,
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
}

final case class HttpClientTarget(
    httpLibrary: Option[String],
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
      .resolveFrameworkKey(languageId, "http.client.httpLibrary", clientTemplateDirectory, httpLibrary)
      .andThen { libraryKey =>
        HttpLanguageTarget.internal
          .composedClientArtifacts(
            clientTemplateDirectory = clientTemplateDirectory,
            modelsTemplateDirectory = modelsTemplateDirectory,
            libraryKeys = if (libraryKey.isEmpty) Nil else List(libraryKey),
            emitModels = emitModels,
            additionalTemplatesDirectory = additionalTemplatesDirectory
          )
          .map { artifacts =>
            HttpLanguageTarget.buildCodegenSettings(
              languageId = languageId,
              frameworkKey = libraryKey,
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
    def resolveFrameworkKey(
        languageId: String,
        configPath: String,
        templateDirectory: String,
        explicit: Option[String]
    ): SqlValidated[String] =
      explicit match {
        case Some(key) => key.validNel
        case None      =>
          CodegenOutputDeckLoader
            .load(templateDirectory, getClass.getClassLoader)
            .leftMap(_.map(error => InvalidPluginConfig(error.message)))
            .andThen { deck =>
              deck.variants.keys.toList match {
                case Nil           => "".validNel
                case single :: Nil => single.validNel
                case multiple      =>
                  SqlValidated.invalid(
                    InvalidPluginConfig(
                      s"smithplates.$languageId.$configPath requires an explicit selection but multiple variants " +
                        s"are available: ${multiple.sorted.mkString(", ")}"
                    )
                  )
              }
            }
      }

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
