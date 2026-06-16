package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.TemplateOutputPrefix
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenDbArtifacts
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenSettings
import software.amazon.smithy.model.node.ObjectNode

import scala.jdk.CollectionConverters.*

final case class LanguageTarget(
    dialects: Map[String, SqlDialectSettings],
    templateDirectory: Option[String],
    rootNamespace: Option[String],
    packageName: Option[String]
) {
  def enabledDialectKeys: List[String] =
    SmithplatesSqlSettings.OrderedDialectKeys.filter(key => dialects.get(key).exists(_.enabled))

  def toCodegenSettings(
      languageId: String,
      enabledDialectKeys: List[String],
      queryRenderers: Map[String, SqlQueryRenderer],
      schemaDdlRenderers: Map[String, SqlSchemaDdlRenderer],
      migrationDirectories: Map[String, String],
      sourceOutputDir: String,
      testOutputDir: String
  ): SqlServiceCodegenSettings = {
    val defaultDialectKey   = enabledDialectKeys.headOption.getOrElse(SqlServiceCodegenSettings.SharedDialectKey)
    val templateDirectory   = LanguageTargetTemplateValidator.resolveTemplateDirectory(this, languageId)
    val outputPrefix        = TemplateOutputPrefix.fromTemplateDirectory(templateDirectory)
    val rootNamespace       = LanguageTarget.resolvedRootNamespace(languageId, this)
    val resolvedPackageName =
      packageName.getOrElse(TemplateOutputPrefix.toPackageName(outputPrefix, rootNamespace))
    SqlServiceCodegenSettings(
      templateDirectory = templateDirectory,
      defaultDialectKey = defaultDialectKey,
      enabledDialectKeys = enabledDialectKeys,
      queryRenderers = queryRenderers,
      schemaDdlRenderers = schemaDdlRenderers,
      migrationDirectories = migrationDirectories,
      sourceOutputDirectory = Some(sourceOutputDir),
      testOutputDirectory = Some(testOutputDir),
      artifacts = SqlServiceCodegenDbArtifacts.forEnabledDialects(enabledDialectKeys),
      outputPrefix = outputPrefix,
      packageName = resolvedPackageName
    )
  }
}

object LanguageTarget {
  val DefaultRootNamespace: String = "generated"

  def resolvedRootNamespace(languageId: String, target: LanguageTarget): Option[String] =
    target.rootNamespace.orElse(
      if (LanguageTargetTemplateValidator.bundledLanguageIds.contains(languageId.toLowerCase)) {
        Some(DefaultRootNamespace)
      } else {
        None
      }
    )

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
          case "sourceoutputdir" | "testoutputdir"                                   =>
            SqlValidated.invalid(
              InvalidPluginConfig(
                s"smithplates.$languageId.sql.$key must not be set; " +
                  s"use smithplates.$languageId.sourceOutputDir and smithplates.$languageId.testOutputDir instead"
              )
            )
          case "templatedirectory" | "rootnamespace" | "packagename"                 =>
            SqlValidated.valid(Right(()))
          case other                                                                 =>
            SqlValidated.invalid(
              InvalidPluginConfig(
                s"smithplates.$languageId.sql contains unknown key '$other'; expected dialect (sqlite, postgres), " +
                  "`templateDirectory`, `rootNamespace`, or `packageName`"
              )
            )
        }
      }

    parsedMembers.map { members =>
      LanguageTarget(
        dialects = members.collect { case Left(dialect) => dialect }.toMap,
        templateDirectory = PluginConfigMembers.optionalStringMember(node, "templateDirectory"),
        rootNamespace = PluginConfigMembers.optionalStringMember(node, "rootNamespace"),
        packageName = PluginConfigMembers.optionalStringMember(node, "packageName")
      )
    }
  }
}
