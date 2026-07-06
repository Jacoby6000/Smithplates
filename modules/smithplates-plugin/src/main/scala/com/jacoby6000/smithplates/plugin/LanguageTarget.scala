package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.plugin.config.PluginConfigDecoders
import com.jacoby6000.smithplates.plugin.config.PluginConfigDecoding
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenDbArtifacts
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenSettings
import io.circe.Json

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
    val defaultDialectKey = enabledDialectKeys.headOption.getOrElse(SqlServiceCodegenSettings.SharedDialectKey)
    val templateDirectory = LanguageTargetTemplateValidator.resolveTemplateDirectory(this, languageId)
    val rootNamespace     = PluginConstants.resolvedRootNamespace(languageId, this.rootNamespace)
    SqlServiceCodegenSettings(
      templateDirectory = templateDirectory,
      defaultDialectKey = defaultDialectKey,
      enabledDialectKeys = enabledDialectKeys,
      queryRenderers = queryRenderers,
      schemaDdlRenderers = schemaDdlRenderers,
      migrationDirectories = migrationDirectories,
      sourceOutputDirectory = Some(sourceOutputDir),
      testOutputDirectory = Some(testOutputDir),
      artifacts = SqlServiceCodegenDbArtifacts.forEnabledDialects(templateDirectory, enabledDialectKeys),
      rootNamespace = rootNamespace,
      packageNameOverride = packageName
    )
  }
}

object LanguageTarget {
  def parse(
      languageId: String,
      json: Json
  ): SqlValidated[LanguageTarget] = {
    import PluginConfigDecoders.given
    PluginConfigDecoding
      .decode[PluginConfigDecoders.internal.LanguageTargetJson](languageId, "sql", json)
      .andThen(_.toDomain)
  }
}
