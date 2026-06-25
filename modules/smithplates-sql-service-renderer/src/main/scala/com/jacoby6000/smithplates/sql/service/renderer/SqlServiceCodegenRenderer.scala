package com.jacoby6000.smithplates.sql.service.renderer

import cats.data.Validated
import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidationError
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.TemplateRenderFailed
import com.jacoby6000.smithplates.codegen.core.planning.ArtifactKind
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner
import com.jacoby6000.smithplates.codegen.core.planning.CodegenSettings
import com.jacoby6000.smithplates.codegen.core.planning.ResolvedArtifact
import com.jacoby6000.smithplates.codegen.core.planning.TemplateRenderer
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.codegen.core.strategy.config.LanguageBaseConfigLoader
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import com.jacoby6000.smithplates.sql.model.SqlIntEnum
import com.jacoby6000.smithplates.sql.model.SqlSchema
import com.jacoby6000.smithplates.sql.model.SqlStringEnum
import com.jacoby6000.smithplates.sql.service.SqlServiceIr
import com.jacoby6000.smithplates.sql.service.core.SqlCoreModelExtractor
import com.jacoby6000.smithplates.sql.service.core.SqlMeta
import com.jacoby6000.smithplates.sql.service.core.SqlOperationMeta
import com.jacoby6000.smithplates.sql.service.core.SqlServiceMeta
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import software.amazon.smithy.model.Model

final case class SqlServiceCodegenSettings(
    templateDirectory: String,
    defaultDialectKey: String,
    enabledDialectKeys: List[String],
    queryRenderers: Map[String, SqlQueryRenderer],
    schemaDdlRenderers: Map[String, com.jacoby6000.smithplates.sql.ddl.renderer.common.SqlSchemaDdlRenderer],
    migrationDirectories: Map[String, String] = Map.empty,
    sourceOutputDirectory: Option[String] = None,
    testOutputDirectory: Option[String] = None,
    artifacts: List[CodegenOutput],
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
    (
      internal.toSqlValidated(SqlCoreModelExtractor.extract(model)),
      internal.sqlCodegenSettings(settings)
    ).mapN((_, _)).andThen { case ((modelSet, services), codegenSettings) =>
      val templateRenderer =
        internal.SqlPlannerTemplateRenderer(model, schema, serviceIr, settings)
      internal
        .toSqlValidated(
          CodegenPlanner.plan(
            settings.artifacts,
            modelSet,
            services,
            codegenSettings,
            templateRenderer
          )
        )
        .andThen { artifacts =>
          services
            .traverse(service =>
              internal.contextForService(
                model,
                schema,
                serviceIr,
                service,
                SqlServiceCodegenSettings.SharedDialectKey,
                settings
              ))
            .map { sharedContexts =>
              artifacts
                .filterNot(_.content == internal.SkipArtifactContent)
                .map(internal.sqlArtifact) ++ sharedContexts.flatMap(internal.renderEnumArtifacts(settings, _))
            }
        }
    }

  def resolveTemplatePath(
      settings: SqlServiceCodegenSettings,
      templatePath: String
  ): String = {
    val baseDirectory      = settings.templateDirectory.stripPrefix("classpath:").stripSuffix("/")
    val normalizedTemplate =
      if (templatePath.startsWith("/")) {
        templatePath.stripPrefix("/")
      } else {
        templatePath
      }
    s"classpath:$baseDirectory/$normalizedTemplate"
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val SkipArtifactContent: String = "__SMITHPLATES_SKIP_SQL_ARTIFACT__"

    final case class SqlPlannerTemplateRenderer(
        model: Model,
        schema: SqlSchema,
        serviceIr: SqlServiceIr,
        settings: SqlServiceCodegenSettings
    ) extends TemplateRenderer {
      def render[S, M](templatePath: String, view: TemplateView[S, M]): CodegenValidated[String] =
        view.subject match {
          case service: ServiceModel[?, ?] =>
            val serviceModel = service.asInstanceOf[ServiceModel[SqlServiceMeta, SqlOperationMeta]]
            val dialectKey   = dialectKeyForTemplatePath(templatePath, settings)
            contextForService(model, schema, serviceIr, serviceModel, dialectKey, settings) match {
              case Validated.Invalid(errors) =>
                TemplateRenderFailed(templatePath, errors.map(_.message).toList.mkString("; ")).invalidNel
              case Validated.Valid(context)  =>
                renderSqlTemplate(settings, templatePath, context)
            }
          case unsupported                 =>
            TemplateRenderFailed(
              templatePath,
              s"SQL templates require a service subject, got ${unsupported.getClass.getName}"
            ).invalidNel
        }
    }

    def renderSqlTemplate(
        settings: SqlServiceCodegenSettings,
        templatePath: String,
        context: SqlCodegenServiceContext
    ): CodegenValidated[String] =
      if (shouldSkip(templatePath, context)) {
        CodegenValidated.valid(SkipArtifactContent)
      } else {
        try {
          val resolvedTemplatePath = resolveTemplatePath(settings, templatePath)
          val content              =
            if (SqlServiceCodegenDbArtifacts.bundledTemplatePaths.contains(templatePath)) {
              ScalateSspTemplateEngine.readClasspathResource(resolvedTemplatePath)
            } else {
              val templateRoot = settings.templateDirectory.stripPrefix("classpath:")
              val view         = SqlCodegenTemplateAttributes.forService(context)
              ScalateSspTemplateEngine.renderClasspathTemplate(resolvedTemplatePath, view, Some(templateRoot))
            }
          CodegenValidated.valid(content)
        } catch {
          case error: Exception =>
            TemplateRenderFailed(
              templatePath,
              Option(error.getMessage).getOrElse(error.getClass.getSimpleName)).invalidNel
        }
      }

    def shouldSkip(templatePath: String, context: SqlCodegenServiceContext): Boolean =
      ((templatePath.contains("service_derived_sql_integration_tests") ||
        templatePath.contains("stubs/testcontainers")) && context.integrationTest.isEmpty) ||
        (templatePath.contains("migrations_service") && context.migration.isEmpty)

    def sqlCodegenSettings(settings: SqlServiceCodegenSettings): SqlValidated[CodegenSettings] =
      toSqlValidated(loadBaseConfig(settings).map { baseConfig =>
        CodegenSettings(
          sourceOutputDirectory = settings.sourceOutputDirectory.map(normalizeDirectory).getOrElse(""),
          testOutputDirectory = settings.testOutputDirectory.map(normalizeDirectory).getOrElse(""),
          conventions = baseConfig.conventions(settings.rootNamespace)
        )
      })

    def loadBaseConfig(settings: SqlServiceCodegenSettings): CodegenValidated[
      com.jacoby6000.smithplates.codegen.core.strategy.config.LanguageBaseConfig
    ] = {
      val languageId = settings.templateDirectory.stripPrefix("classpath:").split('/').headOption.getOrElse("python")
      Option(getClass.getClassLoader.getResourceAsStream(s"$languageId/base_config.json")) match {
        case Some(stream) =>
          try {
            val text = scala.io.Source.fromInputStream(stream, "UTF-8").mkString
            LanguageBaseConfigLoader.loadJson(text)
          } finally stream.close()
        case None         =>
          com.jacoby6000.smithplates.codegen.core
            .InvalidLanguageBaseConfig(
              s"missing language base config resource: $languageId/base_config.json"
            )
            .invalidNel
      }
    }

    def contextForService(
        model: Model,
        schema: SqlSchema,
        serviceIr: SqlServiceIr,
        service: ServiceModel[SqlServiceMeta, SqlOperationMeta],
        dialectKey: String,
        settings: SqlServiceCodegenSettings
    ): SqlValidated[SqlCodegenServiceContext] = {
      val serviceShapeId =
        software.amazon.smithy.model.shapes.ShapeId.from(s"${service.id.namespace}#${service.id.name}")
      serviceIr.services.find(_.shapeId == serviceShapeId) match {
        case Some(sqlService) =>
          val queryRenderer        = queryRendererForDialect(dialectKey, settings)
          val bindPlaceholderStyle = queryRenderer
            .map(_.codegenBindPlaceholder)
            .getOrElse(SqlBindPlaceholder("?"))
          SqlServiceCodegenContextBuilder
            .build(model, schema, serviceIr.queries, sqlService, queryRenderer, bindPlaceholderStyle, settings)
        case None             =>
          SqlValidated.invalid(
            InvalidPluginConfig(s"SQL service ${service.id.namespace}#${service.id.name} not found in SQL service IR")
          )
      }
    }

    def sqlArtifact(artifact: ResolvedArtifact): SqlCodegenArtifact =
      SqlCodegenArtifact(
        relativePath = artifact.relativePath,
        content = artifact.content,
        kind = artifactKind(artifact.kind)
      )

    def artifactKind(kind: ArtifactKind): SqlServiceCodegenArtifactKind =
      kind match {
        case ArtifactKind.Src  => SqlServiceCodegenArtifactKind.Src
        case ArtifactKind.Test => SqlServiceCodegenArtifactKind.Test
      }

    def toSqlValidated[A](value: CodegenValidated[A]): SqlValidated[A] =
      value.leftMap(errors => errors.map(codegenError))

    def codegenError(error: CodegenValidationError): InvalidPluginConfig =
      InvalidPluginConfig(error.message)

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
      val relativeOutputFile = s"${context.namespace.replace('.', '/')}/${SqlCodegenSnakeCase.toSnakeCase(enumName)}.py"
      settings.sourceOutputDirectory match {
        case Some(sourceOutputDirectory) =>
          s"${normalizeDirectory(sourceOutputDirectory)}/$relativeOutputFile"
        case None                        =>
          relativeOutputFile
      }
    }

    def queryRendererForDialect(
        dialectKey: String,
        settings: SqlServiceCodegenSettings
    ): Option[SqlQueryRenderer] =
      settings.queryRenderers.get(dialectKey) match {
        case some @ Some(_)                                                   => some
        case None if dialectKey == SqlServiceCodegenSettings.SharedDialectKey =>
          None
        case None                                                             =>
          throw new IllegalStateException(s"query renderer for dialect '$dialectKey' is required")
      }

    def dialectKeyForTemplatePath(
        templatePath: String,
        settings: SqlServiceCodegenSettings
    ): String =
      if (artifactPathRefersToDialect(templatePath, "sqlite")) {
        "sqlite"
      } else if (artifactPathRefersToDialect(templatePath, "postgres")) {
        "postgres"
      } else {
        settings.defaultDialectKey
      }

    def artifactPathRefersToDialect(path: String, dialectKey: String): Boolean =
      path.startsWith(s"$dialectKey/") || path.contains(s"/$dialectKey/")

    def normalizeDirectory(directory: String): String =
      directory.stripSuffix("/").stripSuffix("\\")
  }
}
