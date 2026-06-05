package com.jacoby6000.smithy.stache

import cats.syntax.all.*
import com.jacoby6000.smithy.sql.{InvalidPluginConfig, SqlDialect, SqlValidated, SqliteDialect}
import com.jacoby6000.smithy.sql.codegen.{
  SqlServiceCodegenDbArtifacts,
  SqlServiceCodegenSettings
}
import com.jacoby6000.smithy.sql.shared.{SqlBindPlaceholder, SqlShared}

final case class LanguageTarget(
    templateDirectory: Option[String],
    sourceOutputDir: String,
    testOutputDir: String
) {
  def toCodegenSettings(
      languageId: String,
      enabledDialects: List[SqlDialect]
  ): SqlServiceCodegenSettings = {
    val defaultDialect = enabledDialects.headOption.getOrElse(SqliteDialect)
    SqlServiceCodegenSettings(
      templateDirectory =
        LanguageTargetTemplateValidator.resolveTemplateDirectory(this, languageId),
      dialect = defaultDialect,
      bindPlaceholderStyle = SqlBindPlaceholder.inferForCodegen(defaultDialect),
      sourceOutputDirectory = Some(sourceOutputDir),
      testOutputDirectory = Some(testOutputDir),
      artifacts = SqlServiceCodegenDbArtifacts.forEnabledDialects(enabledDialects)
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
      case Some(_) =>
        SqlValidated.invalid(InvalidPluginConfig(s"$memberName must be a string"))
      case None =>
        SqlValidated.invalid(InvalidPluginConfig(message))
    }
}
