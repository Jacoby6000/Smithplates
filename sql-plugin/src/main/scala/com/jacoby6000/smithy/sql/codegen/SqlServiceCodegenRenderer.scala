package com.jacoby6000.smithy.sql.codegen

import cats.syntax.all.*
import com.jacoby6000.smithy.sql.{PostgresDialect, SqlDialect, SqlSchema, SqliteDialect, SqlValidated}
import com.jacoby6000.smithy.sql.shared.SqlBindPlaceholder
import software.amazon.smithy.model.Model

final case class SqlServiceCodegenArtifactConfig(
    kind: SqlServiceCodegenArtifactKind,
    template: String,
    outputFile: String
)

final case class SqlServiceCodegenSettings(
    templateDirectory: String,
    dialect: SqlDialect,
    bindPlaceholderStyle: SqlBindPlaceholder,
    sourceOutputDirectory: Option[String] = None,
    testOutputDirectory: Option[String] = None,
    artifacts: List[SqlServiceCodegenArtifactConfig]
)

object SqlServiceCodegenRenderer {
  def render(
      model: Model,
      schema: SqlSchema,
      settings: SqlServiceCodegenSettings
  ): SqlValidated[List[SqlCodegenArtifact]] =
    schema.services.traverse { service =>
      settings.artifacts.traverse { artifactConfig =>
        val artifactDialect = dialectForArtifact(artifactConfig, settings.dialect)
        val bindPlaceholderStyle = SqlBindPlaceholder.inferForCodegen(artifactDialect)
        SqlServiceCodegenContextBuilder
          .build(model, schema, service, artifactDialect, bindPlaceholderStyle)
          .map { context =>
            if (
              SqlServiceCodegenDbArtifacts.isIntegrationTestTemplate(artifactConfig.template) &&
              context.integrationTest.isEmpty
            ) {
              Nil
            } else {
              val templatePath = resolveTemplatePath(settings, artifactConfig)
              val attributes = SqlCodegenTemplateAttributes.forService(context)
              val content = MustacheTemplateEngine.renderClasspathTemplate(templatePath, attributes)
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
      }.map(_.flatten)
    }.map(_.flatten)

  def resolveOutputPath(
      settings: SqlServiceCodegenSettings,
      artifactConfig: SqlServiceCodegenArtifactConfig,
      context: SqlCodegenServiceContext
  ): String = {
    val renderedOutputFile =
      SqlCodegenTemplateAttributes.renderOutputPath(artifactConfig.outputFile, context)
    val prefixedOutputFile =
      artifactConfig.kind match {
        case SqlServiceCodegenArtifactKind.Src =>
          settings.sourceOutputDirectory match {
            case Some(sourceOutputDirectory) =>
              s"${normalizeDirectory(sourceOutputDirectory)}/$renderedOutputFile"
            case None =>
              renderedOutputFile
          }
        case SqlServiceCodegenArtifactKind.Test =>
          settings.testOutputDirectory match {
            case Some(testOutputDirectory) =>
              s"${normalizeDirectory(testOutputDirectory)}/$renderedOutputFile"
            case None =>
              renderedOutputFile
          }
      }
    prefixedOutputFile
  }

  private def dialectForArtifact(
      artifactConfig: SqlServiceCodegenArtifactConfig,
      defaultDialect: SqlDialect
  ): SqlDialect =
    if (artifactConfig.template.contains("/sqlite/")) {
      SqliteDialect
    } else if (artifactConfig.template.contains("/postgres/")) {
      PostgresDialect
    } else {
      defaultDialect
    }

  private def normalizeDirectory(directory: String): String =
    directory.stripSuffix("/").stripSuffix("\\")

  def resolveTemplatePath(
      settings: SqlServiceCodegenSettings,
      artifactConfig: SqlServiceCodegenArtifactConfig
  ): String = {
    val baseDirectory = settings.templateDirectory.stripPrefix("classpath:").stripSuffix("/")
    val normalizedTemplate =
      if (artifactConfig.template.startsWith("/")) {
        artifactConfig.template.stripPrefix("/")
      } else {
        artifactConfig.template
      }
    s"classpath:$baseDirectory/$normalizedTemplate"
  }
}
