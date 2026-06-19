package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.service.renderer.HttpClientCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenSettings
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import software.amazon.smithy.model.node.ObjectNode

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
      node: ObjectNode
  ): SqlValidated[HttpLanguageTarget] = {
    val serverNode = Option(node.getMember("server").orElse(null))
    val clientNode = Option(node.getMember("client").orElse(null))

    if (serverNode.isEmpty && clientNode.isEmpty) {
      SqlValidated.invalid(
        InvalidPluginConfig(s"smithplates.$languageId.http requires `server` and/or `client`")
      )
    } else {
      (
        serverNode match {
          case None                              => None.validNel
          case Some(value) if value.isObjectNode =>
            parseServer(languageId, value.expectObjectNode()).map(Some(_))
          case Some(_)                           =>
            SqlValidated.invalid(
              InvalidPluginConfig(s"smithplates.$languageId.http.server must be an object")
            )
        },
        clientNode match {
          case None                              => None.validNel
          case Some(value) if value.isObjectNode =>
            parseClient(languageId, value.expectObjectNode()).map(Some(_))
          case Some(_)                           =>
            SqlValidated.invalid(
              InvalidPluginConfig(s"smithplates.$languageId.http.client must be an object")
            )
        }
      ).mapN { (server, client) =>
        HttpLanguageTarget(
          server = server,
          client = client,
          rootNamespace = PluginConfigMembers.optionalStringMember(node, "rootNamespace"),
          modelsPackageName = PluginConfigMembers.optionalStringMember(node, "modelsPackageName")
        )
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

  private val ServerAllowedMembers: Set[String] =
    Set("webFramework", "templateDirectory", "packageName")

  private val ClientAllowedMembers: Set[String] =
    Set("httpLibrary", "templateDirectory", "packageName")

  private def parseServer(
      languageId: String,
      node: ObjectNode
  ): SqlValidated[HttpServerTarget] =
    (
      PluginConfigMembers.rejectNestedOutputDirectories("http.server", languageId, node),
      PluginConfigMembers.rejectUnknownMembers("http.server", languageId, node, ServerAllowedMembers)
    ).mapN { (_, _) =>
      HttpServerTarget(
        webFramework = PluginConfigMembers.optionalStringMember(node, "webFramework").getOrElse("fastapi"),
        templateDirectory = PluginConfigMembers.optionalStringMember(node, "templateDirectory"),
        packageName = PluginConfigMembers.optionalStringMember(node, "packageName")
      )
    }

  private def parseClient(
      languageId: String,
      node: ObjectNode
  ): SqlValidated[HttpClientTarget] =
    (
      PluginConfigMembers.rejectNestedOutputDirectories("http.client", languageId, node),
      PluginConfigMembers.rejectUnknownMembers("http.client", languageId, node, ClientAllowedMembers)
    ).mapN { (_, _) =>
      HttpClientTarget(
        httpLibrary = PluginConfigMembers.optionalStringMember(node, "httpLibrary").getOrElse("httpx"),
        templateDirectory = PluginConfigMembers.optionalStringMember(node, "templateDirectory"),
        packageName = PluginConfigMembers.optionalStringMember(node, "packageName")
      )
    }
}
