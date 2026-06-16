package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlShared
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenDbArtifacts
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenSettings
import software.amazon.smithy.model.node.ObjectNode

import scala.jdk.CollectionConverters.*

final case class LanguageTarget(
    dialects: Map[String, SqlDialectSettings],
    templateDirectory: Option[String],
    sourceOutputDir: String,
    testOutputDir: String
) {
  def enabledDialectKeys: List[String] =
    SmithplatesSqlSettings.OrderedDialectKeys.filter(key => dialects.get(key).exists(_.enabled))

  def toCodegenSettings(
      languageId: String,
      enabledDialectKeys: List[String],
      queryRenderers: Map[String, SqlQueryRenderer],
      schemaDdlRenderers: Map[String, SqlSchemaDdlRenderer],
      migrationDirectories: Map[String, String]
  ): SqlServiceCodegenSettings = {
    val defaultDialectKey = enabledDialectKeys.headOption.getOrElse(SqlServiceCodegenSettings.SharedDialectKey)
    SqlServiceCodegenSettings(
      templateDirectory = LanguageTargetTemplateValidator.resolveTemplateDirectory(this, languageId),
      defaultDialectKey = defaultDialectKey,
      enabledDialectKeys = enabledDialectKeys,
      queryRenderers = queryRenderers,
      schemaDdlRenderers = schemaDdlRenderers,
      migrationDirectories = migrationDirectories,
      sourceOutputDirectory = Some(sourceOutputDir),
      testOutputDirectory = Some(testOutputDir),
      artifacts = SqlServiceCodegenDbArtifacts.forEnabledDialects(enabledDialectKeys)
    )
  }
}

object LanguageTarget {
  def parse(
      languageId: String,
      node: ObjectNode
  ): SqlValidated[LanguageTarget] = {
    val parsedMembers =
      node.getMembers.asScala.toList.traverse { case (keyNode, memberNode) =>
        val key = keyNode.expectStringNode().getValue
        key.toLowerCase match {
          case dialectKey if SmithplatesSqlSettings.DialectKeys.contains(dialectKey) =>
            if (memberNode.isObjectNode) {
              SmithplatesSqlSettings
                .parseDialect(dialectKey, memberNode.expectObjectNode())
                .map(settings => Left(dialectKey -> settings))
            } else {
              SqlValidated.invalid(
                InvalidPluginConfig(s"smithplates.$languageId.sql.$dialectKey must be an object")
              )
            }
          case "sourceoutputdir" | "testoutputdir" | "templatedirectory"             =>
            SqlValidated.valid(Right(()))
          case other                                                                 =>
            SqlValidated.invalid(
              InvalidPluginConfig(
                s"smithplates.$languageId.sql contains unknown key '$other'; expected dialect (sqlite, postgres), " +
                  "`sourceOutputDir`, `testOutputDir`, or `templateDirectory`"
              )
            )
        }
      }

    (
      parsedMembers.map(_.collect { case Left(dialect) => dialect }.toMap),
      requiredStringMember(
        node,
        "sourceOutputDir",
        s"smithplates.$languageId.sql requires `sourceOutputDir`"
      ),
      requiredStringMember(
        node,
        "testOutputDir",
        s"smithplates.$languageId.sql requires `testOutputDir`"
      )
    ).mapN { (dialects, sourceOutputDir, testOutputDir) =>
      LanguageTarget(
        dialects = dialects,
        templateDirectory = optionalStringMember(node, "templateDirectory"),
        sourceOutputDir = sourceOutputDir,
        testOutputDir = testOutputDir
      )
    }
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
