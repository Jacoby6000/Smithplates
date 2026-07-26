package com.jacoby6000.smithplates.sql.service.renderer

import cats.data.Validated
import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidated
import com.jacoby6000.smithplates.codegen.core.CodegenValidated.*
import com.jacoby6000.smithplates.codegen.core.CodegenValidationError
import com.jacoby6000.smithplates.codegen.core.Field
import com.jacoby6000.smithplates.codegen.core.Model
import com.jacoby6000.smithplates.codegen.core.ModelId
import com.jacoby6000.smithplates.codegen.core.ModelMeta
import com.jacoby6000.smithplates.codegen.core.NeutralType
import com.jacoby6000.smithplates.codegen.core.ServiceModel
import com.jacoby6000.smithplates.codegen.core.TemplateRenderFailed
import com.jacoby6000.smithplates.codegen.core.TimestampFormat
import com.jacoby6000.smithplates.codegen.core.Variant
import com.jacoby6000.smithplates.codegen.core.planning.ArtifactKind
import com.jacoby6000.smithplates.codegen.core.planning.CodegenOutput
import com.jacoby6000.smithplates.codegen.core.planning.CodegenPlanner
import com.jacoby6000.smithplates.codegen.core.planning.CodegenSettings
import com.jacoby6000.smithplates.codegen.core.planning.CodegenTemplatePaths
import com.jacoby6000.smithplates.codegen.core.planning.ResolvedArtifact
import com.jacoby6000.smithplates.codegen.core.planning.TemplateRenderer
import com.jacoby6000.smithplates.codegen.core.planning.TemplateView
import com.jacoby6000.smithplates.sql.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.SqlValidated
import com.jacoby6000.smithplates.sql.model.InvalidPluginConfig
import com.jacoby6000.smithplates.sql.model.SqlIntEnum
import com.jacoby6000.smithplates.sql.model.SqlSchema
import com.jacoby6000.smithplates.sql.model.SqlStringEnum
import com.jacoby6000.smithplates.sql.model.SqlStructure
import com.jacoby6000.smithplates.sql.model.SqlStructureMember
import com.jacoby6000.smithplates.sql.model.SqlUnion
import com.jacoby6000.smithplates.sql.service.SqlServiceIr
import com.jacoby6000.smithplates.sql.service.core.SqlCoreModelExtractor
import com.jacoby6000.smithplates.sql.service.core.SqlMeta
import com.jacoby6000.smithplates.sql.service.core.SqlOperationMeta
import com.jacoby6000.smithplates.sql.service.core.SqlServiceMeta
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import software.amazon.smithy.model.Model as SmithyModel
import software.amazon.smithy.model.shapes.ShapeId

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

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
    packageNameOverride: Option[String] = None,
    serviceFilter: Option[Set[String]] = None
)

object SqlServiceCodegenSettings {
  val SharedDialectKey: String = "shared"
}

object SqlServiceCodegenRenderer {
  def render(
      model: SmithyModel,
      schema: SqlSchema,
      serviceIr: SqlServiceIr,
      settings: SqlServiceCodegenSettings
  ): SqlValidated[List[SqlCodegenArtifact]] =
    (
      internal.toSqlValidated(SqlCoreModelExtractor.extract(model)),
      internal.sqlCodegenSettings(settings)
    ).mapN((_, _)).andThen { case ((modelSet, services), codegenSettings) =>
      val filteredServices = internal.filterServices(services, settings.serviceFilter)
      val templateRenderer =
        internal.SqlPlannerTemplateRenderer(model, schema, serviceIr, settings)
      internal
        .toSqlValidated(
          CodegenPlanner.plan(
            settings.artifacts,
            modelSet,
            filteredServices,
            codegenSettings,
            templateRenderer
          )
        )
        .andThen { artifacts =>
          filteredServices
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
  ): String =
    if (CodegenTemplatePaths.isQualified(templatePath)) {
      templatePath
    } else {
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

    def filterServices[S, O](
        services: List[ServiceModel[S, O]],
        serviceFilter: Option[Set[String]]
    ): List[ServiceModel[S, O]] =
      serviceFilter match {
        case None               => services
        case Some(allowedNames) =>
          services.filter { service =>
            val fullName = s"${service.id.namespace}#${service.id.name}"
            allowedNames.contains(service.id.name) || allowedNames.contains(fullName)
          }
      }

    final case class SqlPlannerTemplateRenderer(
        model: SmithyModel,
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
                val enrichedView = internal.enrichView(
                  view.asInstanceOf[SqlNeutralServiceTemplateAttributes.ServiceView],
                  context
                )
                renderSqlTemplate(settings, templatePath, enrichedView)
            }
          case unsupported                 =>
            TemplateRenderFailed(
              templatePath,
              s"SQL templates require a service subject, got ${unsupported.getClass.getName}"
            ).invalidNel
        }
    }

    def enrichView(
        view: SqlNeutralServiceTemplateAttributes.ServiceView,
        context: SqlCodegenServiceContext
    ): SqlNeutralServiceTemplateAttributes.ServiceView =
      SqlNeutralServiceTemplateAttributes.enrichment(context) match {
        case (serviceMeta, opMetas) =>
          val enrichedOperations = view.subject.operations.map { op =>
            op.copy(meta = op.meta.copy(feature = opMetas.getOrElse(op.id.name, op.meta.feature)))
          }
          val existingIds        = view.usedTypes.map(_.id).toSet
          val extraStructures    = context.models
            .filter(s => !existingIds.contains(ModelId(s.namespace, s.name)))
            .map(internal.toNeutralStructure)
          val extraUnions        = context.unions
            .filter(u => !existingIds.contains(ModelId(u.namespace, u.name)))
            .map(internal.toNeutralUnion)
          val enrichedUsedTypes  = view.usedTypes ++ extraStructures ++ extraUnions
          val enrichedService    = view.subject.copy(
            meta = view.subject.meta.copy(feature = serviceMeta),
            operations = enrichedOperations
          )
          view.copy(subject = enrichedService, usedTypes = enrichedUsedTypes)
      }

    def toNeutralStructure(structure: SqlStructure): Model.Structure[SqlMeta] =
      Model.Structure(
        id = ModelId(structure.namespace, structure.name),
        meta = ModelMeta(None, Nil, SqlMeta.SqlNestedField),
        fields = structure.members.map(member => Field(member.name, internal.toNeutralType(member)))
      )

    def toNeutralUnion(union: SqlUnion): Model.Union[SqlMeta] =
      Model.Union(
        id = ModelId(union.namespace, union.name),
        meta = ModelMeta(None, Nil, SqlMeta.SqlNestedField),
        members = union.members.map(member =>
          Variant(member.name, internal.toNeutralType(member.typeName, None, optional = false)))
      )

    def toNeutralType(member: SqlStructureMember): NeutralType =
      internal.toNeutralType(member.typeName, member.structureShapeId, member.optional)

    def toNeutralType(typeName: String, structureShapeId: Option[ShapeId], optional: Boolean): NeutralType = {
      val base = structureShapeId match {
        case Some(shapeId) =>
          NeutralType.ModelRef(ModelId(shapeId.getNamespace(), shapeId.getName()))
        case None          =>
          typeName match {
            case "String"                           => NeutralType.StringT
            case "Integer"                          => NeutralType.IntegerT
            case "Long"                             => NeutralType.LongT
            case "BigInteger"                       => NeutralType.BigIntegerT
            case "Float"                            => NeutralType.FloatT
            case "Double"                           => NeutralType.DoubleT
            case "BigDecimal"                       => NeutralType.BigDecimalT
            case "Boolean"                          => NeutralType.BooleanT
            case "Blob"                             => NeutralType.BytesT
            case "Timestamp"                        => NeutralType.TimestampT(TimestampFormat.DateTime)
            case "Document"                         => NeutralType.DocumentT
            case other if other.startsWith("List[") =>
              val inner = other.substring(5, other.length - 1)
              NeutralType.ListT(internal.toNeutralType(inner, None, optional = false))
            case other                              =>
              NeutralType.ModelRef(ModelId("", other))
          }
      }
      if (optional) NeutralType.optional(base) else base
    }

    def renderSqlTemplate(
        settings: SqlServiceCodegenSettings,
        templatePath: String,
        view: SqlNeutralServiceTemplateAttributes.ServiceView
    ): CodegenValidated[String] =
      if (shouldSkip(templatePath, view)) {
        CodegenValidated.valid(SkipArtifactContent)
      } else {
        try {
          val resolvedTemplatePath = resolveTemplatePath(settings, templatePath)
          val bundledTemplateRoot  = settings.templateDirectory.stripPrefix("classpath:")
          val content              =
            if (!SqlServiceCodegenDbArtifacts.isRenderedTemplate(templatePath)) {
              if (CodegenTemplatePaths.isFileQualified(resolvedTemplatePath)) {
                Files.readString(
                  Paths.get(CodegenTemplatePaths.filePath(resolvedTemplatePath)),
                  StandardCharsets.UTF_8
                )
              } else {
                ScalateSspTemplateEngine.readClasspathResource(resolvedTemplatePath)
              }
            } else if (CodegenTemplatePaths.isFileQualified(templatePath)) {
              ScalateSspTemplateEngine.renderFilesystemTemplate(
                CodegenTemplatePaths.filePath(resolvedTemplatePath),
                bundledTemplateRoot,
                view
              )
            } else {
              val templateRoot =
                if (CodegenTemplatePaths.isClasspathQualified(templatePath) &&
                  !resolvedTemplatePath.stripPrefix("classpath:").startsWith(s"$bundledTemplateRoot/")) {
                  bundledTemplateRoot
                } else if (CodegenTemplatePaths.isClasspathQualified(templatePath)) {
                  templatePath.stripPrefix("classpath:").split("/").dropRight(1).mkString("/")
                } else {
                  bundledTemplateRoot
                }
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

    def shouldSkip(templatePath: String, view: SqlNeutralServiceTemplateAttributes.ServiceView): Boolean = {
      val meta = SqlNeutralServiceTemplateAttributes.serviceMeta(view)
      ((templatePath.contains("service_derived_sql_integration_tests") ||
        templatePath.contains("stubs/testcontainers")) && meta.integrationTest.isEmpty) ||
      (templatePath.contains("migrations_service") && meta.migration.isEmpty)
    }

    def sqlCodegenSettings(settings: SqlServiceCodegenSettings): SqlValidated[CodegenSettings] =
      toSqlValidated(SqlCodegenLanguageConventions.codegenSettings(settings))

    def contextForService(
        model: SmithyModel,
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
      val relativeOutputFile =
        s"${context.namespace.replace('.', '/')}/${context.conventions.fileName(ModelId(context.namespace, enumName))}"
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
      SqlCodegenLanguageConventions.normalizeDirectory(directory)
  }
}
