package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.TemplateOutputPrefix
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
      modelsPackageName: String,
      emitModels: Boolean,
      sourceOutputDir: String,
      testOutputDir: String
  ): HttpServiceCodegenSettings = {
    val templateDirectory      = HttpLanguageTargetTemplateValidator.resolveServerTemplateDirectory(this, languageId)
    val modelTemplateDirectory = HttpLanguageTargetTemplateValidator.defaultModelsTemplateDirectory(languageId)
    val outputPrefix           = TemplateOutputPrefix.fromTemplateDirectory(templateDirectory)
    val modelsOutputPrefix     = TemplateOutputPrefix.fromTemplateDirectory(modelTemplateDirectory)
    HttpServiceCodegenSettings(
      templateDirectory = templateDirectory,
      defaultFrameworkKey = webFramework,
      enabledFrameworkKeys = List(webFramework),
      packageName = packageName.getOrElse(TemplateOutputPrefix.toPackageName(outputPrefix, rootNamespace)),
      sourceOutputDirectory = Some(sourceOutputDir),
      testOutputDirectory = Some(testOutputDir),
      artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(
        List(webFramework),
        routeGroupTags,
        emitModels
      ),
      outputPrefix = outputPrefix,
      modelsPackageName = modelsPackageName,
      modelsOutputPrefix = modelsOutputPrefix,
      emitModels = emitModels,
      modelTemplateDirectory = Some(modelTemplateDirectory)
    )
  }
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
      modelsPackageName: String,
      emitModels: Boolean,
      sourceOutputDir: String,
      testOutputDir: String
  ): HttpServiceCodegenSettings = {
    val templateDirectory      = HttpLanguageTargetTemplateValidator.resolveClientTemplateDirectory(this, languageId)
    val modelTemplateDirectory = HttpLanguageTargetTemplateValidator.defaultModelsTemplateDirectory(languageId)
    val outputPrefix           = TemplateOutputPrefix.fromTemplateDirectory(templateDirectory)
    val modelsOutputPrefix     = TemplateOutputPrefix.fromTemplateDirectory(modelTemplateDirectory)
    val clientArtifacts        = HttpClientCodegenApiArtifacts.forEnabledLibraries(List(httpLibrary), routeGroupTags)
    val modelArtifacts         = if (emitModels) HttpServiceCodegenApiArtifacts.sharedModels else Nil
    HttpServiceCodegenSettings(
      templateDirectory = templateDirectory,
      defaultFrameworkKey = httpLibrary,
      enabledFrameworkKeys = List(httpLibrary),
      packageName = packageName.getOrElse(TemplateOutputPrefix.toPackageName(outputPrefix, rootNamespace)),
      sourceOutputDirectory = Some(sourceOutputDir),
      testOutputDirectory = Some(testOutputDir),
      artifacts = clientArtifacts ++ modelArtifacts,
      outputPrefix = outputPrefix,
      modelsPackageName = modelsPackageName,
      modelsOutputPrefix = modelsOutputPrefix,
      emitModels = emitModels,
      modelTemplateDirectory = Some(modelTemplateDirectory)
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
  val DefaultRootNamespace: String = "generated"

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

  def resolvedRootNamespace(languageId: String, target: HttpLanguageTarget): Option[String] =
    target.rootNamespace.orElse(
      if (HttpLanguageTargetTemplateValidator.bundledLanguageIds.contains(languageId.toLowerCase)) {
        Some(DefaultRootNamespace)
      } else {
        None
      }
    )

  def resolvedModelsPackageName(languageId: String, target: HttpLanguageTarget): String = {
    val rootNamespace = resolvedRootNamespace(languageId, target)
    target.modelsPackageName.getOrElse(
      TemplateOutputPrefix.toPackageName(
        TemplateOutputPrefix.fromTemplateDirectory(
          HttpLanguageTargetTemplateValidator.defaultModelsTemplateDirectory(languageId)
        ),
        rootNamespace
      )
    )
  }

  private def parseServer(
      languageId: String,
      node: ObjectNode
  ): SqlValidated[HttpServerTarget] =
    PluginConfigMembers.rejectNestedOutputDirectories("http.server", languageId, node).map { _ =>
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
    PluginConfigMembers.rejectNestedOutputDirectories("http.client", languageId, node).map { _ =>
      HttpClientTarget(
        httpLibrary = PluginConfigMembers.optionalStringMember(node, "httpLibrary").getOrElse("httpx"),
        templateDirectory = PluginConfigMembers.optionalStringMember(node, "templateDirectory"),
        packageName = PluginConfigMembers.optionalStringMember(node, "packageName")
      )
    }
}
