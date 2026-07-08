package com.jacoby6000.smithplates.plugin

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.plugin.config.PluginConfigDecoders
import com.jacoby6000.smithplates.plugin.config.PluginConfigDecoding
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenDbArtifacts
import com.jacoby6000.smithplates.sql.service.renderer.SqlServiceCodegenSettings
import io.circe.Json

final case class LanguageTarget(
    dialects: Map[String, SqlDialectSettings],
    templateDirectory: Option[String],
    additionalTemplatesDirectory: Option[String] = None,
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
  ): SqlValidated[SqlServiceCodegenSettings] = {
    val defaultDialectKey = enabledDialectKeys.headOption.getOrElse(SqlServiceCodegenSettings.SharedDialectKey)
    val templateDirectory = LanguageTargetTemplateValidator.resolveTemplateDirectory(this, languageId)
    val rootNamespace     = PluginConstants.resolvedRootNamespace(languageId, this.rootNamespace)
    LanguageTarget.internal
      .composedArtifacts(templateDirectory, enabledDialectKeys, additionalTemplatesDirectory)
      .map { artifacts =>
        SqlServiceCodegenSettings(
          templateDirectory = templateDirectory,
          defaultDialectKey = defaultDialectKey,
          enabledDialectKeys = enabledDialectKeys,
          queryRenderers = queryRenderers,
          schemaDdlRenderers = schemaDdlRenderers,
          migrationDirectories = migrationDirectories,
          sourceOutputDirectory = Some(sourceOutputDir),
          testOutputDirectory = Some(testOutputDir),
          artifacts = artifacts,
          rootNamespace = rootNamespace,
          packageNameOverride = packageName
        )
      }
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

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def composedArtifacts(
        templateDirectory: String,
        enabledDialectKeys: List[String],
        additionalTemplatesDirectory: Option[String]
    ): SqlValidated[List[CodegenOutput]] =
      SqlServiceCodegenDbArtifacts
        .dialectArtifacts(templateDirectory, enabledDialectKeys)
        .andThen { bundled =>
          additionalTemplatesDirectory match {
            case None                => bundled.validNel
            case Some(additionalDir) =>
              ConsumerCodegenOutputs
                .additionalOutputs(additionalDir, enabledDialectKeys, getClass.getClassLoader)
                .map(ConsumerCodegenOutputs.compose(bundled, _))
          }
        }
        .leftMap(_.map(error => InvalidPluginConfig(error.message)))
  }
}
