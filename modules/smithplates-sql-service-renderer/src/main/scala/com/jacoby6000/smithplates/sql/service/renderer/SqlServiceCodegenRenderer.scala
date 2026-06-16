package com.jacoby6000.smithplates.sql.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.SqlSchema
import com.jacoby6000.smithplates.sql.service.SqlServiceIr
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import software.amazon.smithy.model.Model

final case class SqlServiceCodegenArtifactConfig(
    kind: SqlServiceCodegenArtifactKind,
    template: String,
    outputFile: String,
    bundledResource: Boolean = false
)

final case class SqlServiceCodegenSettings(
    templateDirectory: String,
    defaultDialectKey: String,
    enabledDialectKeys: List[String],
    queryRenderers: Map[String, SqlQueryRenderer],
    schemaDdlRenderers: Map[String, com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer],
    migrationDirectories: Map[String, String] = Map.empty,
    sourceOutputDirectory: Option[String] = None,
    testOutputDirectory: Option[String] = None,
    artifacts: List[SqlServiceCodegenArtifactConfig],
    outputPrefix: String,
    packageName: String
)

object SqlServiceCodegenSettings {
  val SharedDialectKey: String = "shared"
}

object SqlServiceCodegenRenderer {
  def render(
      model: Model,
      schema: SqlSchema,
      serviceIr: SqlServiceIr,
      settings: SqlServiceCodegenSettings
  ): SqlValidated[List[SqlCodegenArtifact]] =
    serviceIr.services
      .traverse { service =>
        settings.artifacts
          .traverse { artifactConfig =>
            val queryRenderer        = queryRendererForArtifact(artifactConfig, settings)
            val bindPlaceholderStyle = queryRenderer
              .map(_.codegenBindPlaceholder)
              .getOrElse(SqlBindPlaceholder("?"))
            SqlServiceCodegenContextBuilder
              .build(model, schema, serviceIr.queries, service, queryRenderer, bindPlaceholderStyle, settings)
              .map { context =>
                if ((SqlServiceCodegenDbArtifacts.isIntegrationTestTemplate(artifactConfig.template) ||
                    SqlServiceCodegenDbArtifacts.isPostgresTestcontainersStub(artifactConfig.template)) &&
                  context.integrationTest.isEmpty) {
                  Nil
                } else if (SqlServiceCodegenDbArtifacts.isMigrationServiceTemplate(artifactConfig.template) &&
                  context.migration.isEmpty) {
                  Nil
                } else {
                  val templatePath = resolveTemplatePath(settings, artifactConfig)
                  val templateRoot = settings.templateDirectory.stripPrefix("classpath:")
                  val content      =
                    if (artifactConfig.bundledResource) {
                      ScalateSspTemplateEngine.readClasspathResource(templatePath)
                    } else {
                      val view = SqlCodegenTemplateAttributes.forService(context)
                      ScalateSspTemplateEngine.renderClasspathTemplate(templatePath, view, Some(templateRoot))
                    }
                  val relativePath = resolveOutputPath(settings, artifactConfig, context)
                  List(
                    SqlCodegenArtifact(
                      relativePath = relativePath,
                      content = content,
                      kind = artifactConfig.kind
                    )
                  )
                }
              }
          }
          .map(_.flatten)
      }
      .map(_.flatten)

  def resolveOutputPath(
      settings: SqlServiceCodegenSettings,
      artifactConfig: SqlServiceCodegenArtifactConfig,
      context: SqlCodegenServiceContext
  ): String = {
    val renderedOutputFile =
      SqlCodegenTemplateAttributes.renderOutputPath(
        artifactConfig.outputFile,
        context,
        settings.templateDirectory.stripPrefix("classpath:")
      )
    val relativeOutputFile = s"${settings.outputPrefix}/$renderedOutputFile"
    val prefixedOutputFile =
      artifactConfig.kind match {
        case SqlServiceCodegenArtifactKind.Src  =>
          settings.sourceOutputDirectory match {
            case Some(sourceOutputDirectory) =>
              s"${normalizeDirectory(sourceOutputDirectory)}/$relativeOutputFile"
            case None                        =>
              relativeOutputFile
          }
        case SqlServiceCodegenArtifactKind.Test =>
          settings.testOutputDirectory match {
            case Some(testOutputDirectory) =>
              s"${normalizeDirectory(testOutputDirectory)}/$relativeOutputFile"
            case None                      =>
              relativeOutputFile
          }
      }
    prefixedOutputFile
  }

  private def queryRendererForArtifact(
      artifactConfig: SqlServiceCodegenArtifactConfig,
      settings: SqlServiceCodegenSettings
  ): Option[SqlQueryRenderer] = {
    val dialectKey = dialectKeyForArtifact(artifactConfig, settings)
    settings.queryRenderers.get(dialectKey) match {
      case some @ Some(_)                                                   => some
      case None if dialectKey == SqlServiceCodegenSettings.SharedDialectKey =>
        None
      case None                                                             =>
        throw new IllegalStateException(s"query renderer for dialect '$dialectKey' is required")
    }
  }

  private def dialectKeyForArtifact(
      artifactConfig: SqlServiceCodegenArtifactConfig,
      settings: SqlServiceCodegenSettings
  ): String = {
    val paths = List(artifactConfig.template, artifactConfig.outputFile)
    if (paths.exists(artifactPathRefersToDialect(_, "sqlite"))) {
      "sqlite"
    } else if (paths.exists(artifactPathRefersToDialect(_, "postgres"))) {
      "postgres"
    } else {
      settings.defaultDialectKey
    }
  }

  private def artifactPathRefersToDialect(path: String, dialectKey: String): Boolean =
    path.startsWith(s"$dialectKey/") || path.contains(s"/$dialectKey/")

  private def normalizeDirectory(directory: String): String =
    directory.stripSuffix("/").stripSuffix("\\")

  def resolveTemplatePath(
      settings: SqlServiceCodegenSettings,
      artifactConfig: SqlServiceCodegenArtifactConfig
  ): String = {
    val baseDirectory      = settings.templateDirectory.stripPrefix("classpath:").stripSuffix("/")
    val normalizedTemplate =
      if (artifactConfig.template.startsWith("/")) {
        artifactConfig.template.stripPrefix("/")
      } else {
        artifactConfig.template
      }
    s"classpath:$baseDirectory/$normalizedTemplate"
  }
}
