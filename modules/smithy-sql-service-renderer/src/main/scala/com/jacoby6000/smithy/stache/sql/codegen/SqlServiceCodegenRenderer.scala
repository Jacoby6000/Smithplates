package com.jacoby6000.smithy.stache.sql.codegen

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.SqlSchema
import com.jacoby6000.smithy.stache.sql.SqlValidated
import com.jacoby6000.smithy.stache.sql.query.SqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.service.SqlServiceIr
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
    schemaDdlRenderers: Map[String, com.jacoby6000.smithy.stache.sql.shared.SqlSchemaDdlRenderer],
    sourceOutputDirectory: Option[String] = None,
    testOutputDirectory: Option[String] = None,
    artifacts: List[SqlServiceCodegenArtifactConfig]
)

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
            val bindPlaceholderStyle = queryRenderer.codegenBindPlaceholder
            SqlServiceCodegenContextBuilder
              .build(model, schema, serviceIr.queries, service, queryRenderer, bindPlaceholderStyle, settings)
              .map { context =>
                if (SqlServiceCodegenDbArtifacts.isIntegrationTestTemplate(artifactConfig.template) &&
                  context.integrationTest.isEmpty) {
                  Nil
                } else {
                  val templatePath = resolveTemplatePath(settings, artifactConfig)
                  val templateRoot = settings.templateDirectory.stripPrefix("classpath:")
                  val content      =
                    if (artifactConfig.bundledResource) {
                      ScalateSspTemplateEngine.readClasspathResource(templatePath)
                    } else {
                      val attributes = SqlCodegenTemplateAttributes.forService(context, templateRoot)
                      ScalateSspTemplateEngine.renderClasspathTemplate(templatePath, attributes, Some(templateRoot))
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
      SqlCodegenTemplateAttributes.renderOutputPath(artifactConfig.outputFile, context)
    val prefixedOutputFile =
      artifactConfig.kind match {
        case SqlServiceCodegenArtifactKind.Src  =>
          settings.sourceOutputDirectory match {
            case Some(sourceOutputDirectory) =>
              s"${normalizeDirectory(sourceOutputDirectory)}/$renderedOutputFile"
            case None                        =>
              renderedOutputFile
          }
        case SqlServiceCodegenArtifactKind.Test =>
          settings.testOutputDirectory match {
            case Some(testOutputDirectory) =>
              s"${normalizeDirectory(testOutputDirectory)}/$renderedOutputFile"
            case None                      =>
              renderedOutputFile
          }
      }
    prefixedOutputFile
  }

  private def queryRendererForArtifact(
      artifactConfig: SqlServiceCodegenArtifactConfig,
      settings: SqlServiceCodegenSettings
  ): SqlQueryRenderer =
    if (artifactConfig.template.contains("/sqlite/")) {
      settings.queryRenderers.getOrElse(
        "sqlite",
        throw new IllegalStateException("sqlite query renderer is required for sqlite codegen artifacts")
      )
    } else if (artifactConfig.template.contains("/postgres/")) {
      settings.queryRenderers.getOrElse(
        "postgres",
        throw new IllegalStateException("postgres query renderer is required for postgres codegen artifacts")
      )
    } else {
      settings.queryRenderers.getOrElse(
        settings.defaultDialectKey,
        throw new IllegalStateException(
          s"query renderer for default dialect '${settings.defaultDialectKey}' is required"
        )
      )
    }

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
