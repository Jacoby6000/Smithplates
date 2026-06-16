package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.service.renderer.HttpClientCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenSettings
import com.jacoby6000.smithplates.http.service.renderer.PythonTemplateNamespaces
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import software.amazon.smithy.model.node.ObjectNode

final case class HttpServerTarget(
    webFramework: String,
    templateDirectory: Option[String],
    sourceOutputDir: String,
    testOutputDir: String,
    packageName: Option[String]
) {
  def toCodegenSettings(
      languageId: String,
      routeGroupTags: List[String]
  ): HttpServiceCodegenSettings =
    HttpServiceCodegenSettings(
      templateDirectory = HttpLanguageTargetTemplateValidator.resolveServerTemplateDirectory(this, languageId),
      defaultFrameworkKey = webFramework,
      enabledFrameworkKeys = List(webFramework),
      packageName = packageName.getOrElse("generated.api"),
      sourceOutputDirectory = Some(sourceOutputDir),
      testOutputDirectory = Some(testOutputDir),
      artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(List(webFramework), routeGroupTags),
      modelTemplateDirectory = Some(PythonTemplateNamespaces.bundledHttpModelsTemplateDirectory)
    )
}

final case class HttpClientTarget(
    httpLibrary: String,
    templateDirectory: Option[String],
    sourceOutputDir: String,
    testOutputDir: String,
    packageName: Option[String]
) {
  def toCodegenSettings(
      languageId: String,
      routeGroupTags: List[String]
  ): HttpServiceCodegenSettings =
    HttpServiceCodegenSettings(
      templateDirectory = HttpLanguageTargetTemplateValidator.resolveClientTemplateDirectory(this, languageId),
      defaultFrameworkKey = httpLibrary,
      enabledFrameworkKeys = List(httpLibrary),
      packageName = packageName.getOrElse("generated.api_client"),
      sourceOutputDirectory = Some(sourceOutputDir),
      testOutputDirectory = Some(testOutputDir),
      artifacts = HttpClientCodegenApiArtifacts.forEnabledLibraries(List(httpLibrary), routeGroupTags),
      serviceTypePrefix = "api_client",
      modelTemplateDirectory = Some(PythonTemplateNamespaces.bundledHttpModelsTemplateDirectory)
    )
}

final case class HttpLanguageTarget(
    server: Option[HttpServerTarget],
    client: Option[HttpClientTarget]
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
      ).mapN(HttpLanguageTarget.apply)
    }
  }

  private def parseServer(
      languageId: String,
      node: ObjectNode
  ): SqlValidated[HttpServerTarget] =
    (
      requiredStringMember(
        node,
        "sourceOutputDir",
        s"smithplates.$languageId.http.server requires `sourceOutputDir`"
      ),
      requiredStringMember(
        node,
        "testOutputDir",
        s"smithplates.$languageId.http.server requires `testOutputDir`"
      )
    ).mapN { (sourceOutputDir, testOutputDir) =>
      HttpServerTarget(
        webFramework = optionalStringMember(node, "webFramework").getOrElse("fastapi"),
        templateDirectory = optionalStringMember(node, "templateDirectory"),
        sourceOutputDir = sourceOutputDir,
        testOutputDir = testOutputDir,
        packageName = optionalStringMember(node, "packageName")
      )
    }

  private def parseClient(
      languageId: String,
      node: ObjectNode
  ): SqlValidated[HttpClientTarget] =
    (
      requiredStringMember(
        node,
        "sourceOutputDir",
        s"smithplates.$languageId.http.client requires `sourceOutputDir`"
      ),
      requiredStringMember(
        node,
        "testOutputDir",
        s"smithplates.$languageId.http.client requires `testOutputDir`"
      )
    ).mapN { (sourceOutputDir, testOutputDir) =>
      HttpClientTarget(
        httpLibrary = optionalStringMember(node, "httpLibrary").getOrElse("httpx"),
        templateDirectory = optionalStringMember(node, "templateDirectory"),
        sourceOutputDir = sourceOutputDir,
        testOutputDir = testOutputDir,
        packageName = optionalStringMember(node, "packageName")
      )
    }

  private def optionalStringMember(
      node: ObjectNode,
      memberName: String
  ): Option[String] =
    Option(node.getMember(memberName).orElse(null)).flatMap {
      case value if value.isStringNode => SqlShared.trimmedNonEmpty(value.expectStringNode().getValue)
      case _                           => None
    }

  private def requiredStringMember(
      node: ObjectNode,
      memberName: String,
      message: String
  ): SqlValidated[String] =
    Option(node.getMember(memberName).orElse(null)) match {
      case Some(value) if value.isStringNode =>
        SqlShared
          .trimmedNonEmpty(value.expectStringNode().getValue)
          .map(SqlValidated.valid)
          .getOrElse(SqlValidated.invalid(InvalidPluginConfig(s"$memberName must be a non-empty string")))
      case Some(_)                           =>
        SqlValidated.invalid(InvalidPluginConfig(s"$memberName must be a string"))
      case None                              =>
        SqlValidated.invalid(InvalidPluginConfig(message))
    }
}
