package com.jacoby6000.smithy.stache

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.InvalidPluginConfig
import com.jacoby6000.smithy.stache.sql.SqlValidated
import com.jacoby6000.smithy.stache.sql.codegen.SqlServiceCodegenDbArtifacts
import com.jacoby6000.smithy.stache.sql.codegen.SqlServiceCodegenSettings
import com.jacoby6000.smithy.stache.sql.query.SqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.shared.SqlSchemaDdlRenderer
import com.jacoby6000.smithy.stache.sql.shared.SqlShared

final case class LanguageTarget(
    templateDirectory: Option[String],
    sourceOutputDir: String,
    testOutputDir: String
) {
  def toCodegenSettings(
      languageId: String,
      enabledDialectKeys: List[String],
      queryRenderers: Map[String, SqlQueryRenderer],
      schemaDdlRenderers: Map[String, SqlSchemaDdlRenderer]
  ): SqlServiceCodegenSettings = {
    val defaultDialectKey = enabledDialectKeys.headOption.getOrElse("sqlite")
    SqlServiceCodegenSettings(
      templateDirectory = LanguageTargetTemplateValidator.resolveTemplateDirectory(this, languageId),
      defaultDialectKey = defaultDialectKey,
      enabledDialectKeys = enabledDialectKeys,
      queryRenderers = queryRenderers,
      schemaDdlRenderers = schemaDdlRenderers,
      sourceOutputDirectory = Some(sourceOutputDir),
      testOutputDirectory = Some(testOutputDir),
      artifacts = SqlServiceCodegenDbArtifacts.forEnabledDialects(enabledDialectKeys)
    )
  }
}

object LanguageTarget {
  def parse(
      languageId: String,
      node: software.amazon.smithy.model.node.ObjectNode
  ): SqlValidated[LanguageTarget] =
    (
      requiredStringMember(
        node,
        "sourceOutputDir",
        s"smithy-stache sql.languageTargets.$languageId requires `sourceOutputDir`"
      ),
      requiredStringMember(
        node,
        "testOutputDir",
        s"smithy-stache sql.languageTargets.$languageId requires `testOutputDir`"
      )
    ).mapN { (sourceOutputDir, testOutputDir) =>
      LanguageTarget(
        templateDirectory = optionalStringMember(node, "templateDirectory"),
        sourceOutputDir = sourceOutputDir,
        testOutputDir = testOutputDir
      )
    }

  private def optionalStringMember(
      node: software.amazon.smithy.model.node.ObjectNode,
      memberName: String
  ): Option[String] =
    Option(node.getMember(memberName).orElse(null)).flatMap {
      case value if value.isStringNode => SqlShared.trimmedNonEmpty(value.expectStringNode().getValue)
      case _                           => None
    }

  private def requiredStringMember(
      node: software.amazon.smithy.model.node.ObjectNode,
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
