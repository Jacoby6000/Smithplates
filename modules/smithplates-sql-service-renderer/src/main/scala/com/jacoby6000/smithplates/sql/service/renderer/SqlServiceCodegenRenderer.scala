package com.jacoby6000.smithplates.sql.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.CodegenPackageNames
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.SqlIntEnum
import com.jacoby6000.smithplates.sql.model.SqlSchema
import com.jacoby6000.smithplates.sql.model.SqlStringEnum
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
    rootNamespace: Option[String],
    packageNameOverride: Option[String] = None
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
            val queryRenderer        = internal.queryRendererForArtifact(artifactConfig, settings)
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
                  val artifact     =
                    SqlCodegenArtifact(
                      relativePath = relativePath,
                      content = content,
                      kind = artifactConfig.kind
                    )
                  if (artifactConfig.template == "models/models.ssp") {
                    artifact :: internal.renderEnumArtifacts(settings, context)
                  } else {
                    List(artifact)
                  }
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
    val renderedOutputFile  =
      SqlCodegenTemplateAttributes.renderOutputPath(
        artifactConfig.outputFile,
        context,
        settings.templateDirectory.stripPrefix("classpath:")
      )
    val namespacePathPrefix =
      com.jacoby6000.smithplates.codegen.CodegenPackageNames.outputPathPrefix(context.namespace)
    val relativeOutputFile  = s"$namespacePathPrefix/$renderedOutputFile"
    val prefixedOutputFile  =
      artifactConfig.kind match {
        case SqlServiceCodegenArtifactKind.Src  =>
          settings.sourceOutputDirectory match {
            case Some(sourceOutputDirectory) =>
              s"${internal.normalizeDirectory(sourceOutputDirectory)}/$relativeOutputFile"
            case None                        =>
              relativeOutputFile
          }
        case SqlServiceCodegenArtifactKind.Test =>
          settings.testOutputDirectory match {
            case Some(testOutputDirectory) =>
              s"${internal.normalizeDirectory(testOutputDirectory)}/$relativeOutputFile"
            case None                      =>
              relativeOutputFile
          }
      }
    prefixedOutputFile
  }

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

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def renderEnumArtifacts(
        settings: SqlServiceCodegenSettings,
        context: SqlCodegenServiceContext
    ): List[SqlCodegenArtifact] = {
      val templateRoot        = settings.templateDirectory.stripPrefix("classpath:")
      val stringEnumArtifacts =
        context.stringEnums.map(stringEnum => renderStringEnumArtifact(settings, templateRoot, context, stringEnum))
      val intEnumArtifacts    =
        context.intEnums.map(intEnum => renderIntEnumArtifact(settings, templateRoot, context, intEnum))
      stringEnumArtifacts ++ intEnumArtifacts
    }

    def renderStringEnumArtifact(
        settings: SqlServiceCodegenSettings,
        templateRoot: String,
        context: SqlCodegenServiceContext,
        stringEnum: SqlStringEnum
    ): SqlCodegenArtifact = {
      val content =
        ScalateSspTemplateEngine.renderClasspathPartial(
          templateRoot,
          "string_enum",
          Map("stringEnum" -> stringEnum)
        )
      SqlCodegenArtifact(
        relativePath = enumArtifactRelativePath(settings, context, stringEnum.name),
        content = content,
        kind = SqlServiceCodegenArtifactKind.Src
      )
    }

    def renderIntEnumArtifact(
        settings: SqlServiceCodegenSettings,
        templateRoot: String,
        context: SqlCodegenServiceContext,
        intEnum: SqlIntEnum
    ): SqlCodegenArtifact = {
      val content =
        ScalateSspTemplateEngine.renderClasspathPartial(
          templateRoot,
          "int_enum",
          Map("intEnum" -> intEnum)
        )
      SqlCodegenArtifact(
        relativePath = enumArtifactRelativePath(settings, context, intEnum.name),
        content = content,
        kind = SqlServiceCodegenArtifactKind.Src
      )
    }

    def enumArtifactRelativePath(
        settings: SqlServiceCodegenSettings,
        context: SqlCodegenServiceContext,
        enumName: String
    ): String = {
      val namespacePathPrefix = CodegenPackageNames.outputPathPrefix(context.namespace)
      val relativeOutputFile  = s"$namespacePathPrefix/${SqlCodegenSnakeCase.toSnakeCase(enumName)}.py"
      settings.sourceOutputDirectory match {
        case Some(sourceOutputDirectory) =>
          s"${normalizeDirectory(sourceOutputDirectory)}/$relativeOutputFile"
        case None                        =>
          relativeOutputFile
      }
    }

    def queryRendererForArtifact(
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

    def dialectKeyForArtifact(
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

    def artifactPathRefersToDialect(path: String, dialectKey: String): Boolean =
      path.startsWith(s"$dialectKey/") || path.contains(s"/$dialectKey/")

    def normalizeDirectory(directory: String): String =
      directory.stripSuffix("/").stripSuffix("\\")
  }
}
