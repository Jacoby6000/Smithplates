package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenApiArtifacts
import com.jacoby6000.smithplates.http.service.renderer.HttpServiceCodegenSettings
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import software.amazon.smithy.model.node.ObjectNode

final case class HttpLanguageTarget(
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
      templateDirectory = HttpLanguageTargetTemplateValidator.resolveTemplateDirectory(this, languageId),
      defaultFrameworkKey = webFramework,
      enabledFrameworkKeys = List(webFramework),
      packageName = packageName.getOrElse("generated.api"),
      sourceOutputDirectory = Some(sourceOutputDir),
      testOutputDirectory = Some(testOutputDir),
      artifacts = HttpServiceCodegenApiArtifacts.forEnabledFrameworks(List(webFramework), routeGroupTags)
    )
}

object HttpLanguageTarget {
  def parse(
      languageId: String,
      node: ObjectNode
  ): SqlValidated[HttpLanguageTarget] =
    Option(node.getMember("server").orElse(null)) match {
      case Some(serverNode) if serverNode.isObjectNode =>
        parseServer(languageId, serverNode.expectObjectNode())
      case Some(_)                                     =>
        SqlValidated.invalid(
          InvalidPluginConfig(s"smithplates http.$languageId.server must be an object")
        )
      case None                                        =>
        SqlValidated.invalid(
          InvalidPluginConfig(s"smithplates http.$languageId requires `server`")
        )
    }

  private def parseServer(
      languageId: String,
      node: ObjectNode
  ): SqlValidated[HttpLanguageTarget] =
    (
      requiredStringMember(
        node,
        "sourceOutputDir",
        s"smithplates http.$languageId.server requires `sourceOutputDir`"
      ),
      requiredStringMember(
        node,
        "testOutputDir",
        s"smithplates http.$languageId.server requires `testOutputDir`"
      )
    ).mapN { (sourceOutputDir, testOutputDir) =>
      HttpLanguageTarget(
        webFramework = optionalStringMember(node, "webFramework").getOrElse("fastapi"),
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
