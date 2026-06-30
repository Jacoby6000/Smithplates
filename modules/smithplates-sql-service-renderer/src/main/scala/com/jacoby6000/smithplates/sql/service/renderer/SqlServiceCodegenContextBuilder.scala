package com.jacoby6000.smithplates.sql.service.renderer

import cats.syntax.all.*
import com.jacoby6000.smithplates.codegen.CodegenPackageNames
import com.jacoby6000.smithplates.sql.*
import com.jacoby6000.smithplates.sql.model.*
import com.jacoby6000.smithplates.sql.service.SqlOperation
import com.jacoby6000.smithplates.sql.service.SqlQueries
import com.jacoby6000.smithplates.sql.service.SqlQueryExtractor
import com.jacoby6000.smithplates.sql.service.SqlService
import com.jacoby6000.smithplates.sql.service.codegen.ResolvedSqlOperationQuery
import com.jacoby6000.smithplates.sql.service.codegen.SqlOperationQueryResolver
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import software.amazon.smithy.model.Model

object SqlServiceCodegenContextBuilder {
  def build(
      model: Model,
      schema: SqlSchema,
      queries: SqlQueries,
      service: SqlService,
      queryRenderer: Option[SqlQueryRenderer],
      bindPlaceholderStyle: SqlBindPlaceholder,
      settings: SqlServiceCodegenSettings
  ): SqlValidated[SqlCodegenServiceContext] =
    SqlCodegenLanguageConventions
      .serviceModuleName(settings, service.shapeId.getName)
      .leftMap(_.map(error => InvalidPluginConfig(error.message)))
      .andThen { moduleName =>
        val rootShapeIds =
          service.operations
            .flatMap { operation =>
              val errorShapes =
                SqlOperationQueryResolver.resolve(queries, operation.shapeId) match {
                  case Some(_: ResolvedSqlOperationQuery.SelectOne) =>
                    Nil
                  case _                                            => operation.errorShapes
                }
              List(operation.inputShape) ++ operation.outputShape.toList ++ errorShapes
            }
            .filterNot(_ == SqlShapeGraph.UnitShapeId)
            .filterNot(_ == SqlQueryExtractor.DerivedStructShapeId)
            .distinct

        val tableShapeIds = schema.tables.map(_.shapeId)

        SqlShapeIrExtractor.extract(model, rootShapeIds ++ tableShapeIds).andThen { shapeIr =>
          service.operations
            .traverse(internal.buildOperation(model, shapeIr, queries, _, queryRenderer))
            .map { operations =>
              val (resolvedOperations, derivedModels) = internal.applySelectOneDerivedOutputs(operations, queries)
              val uuidTypeNames                       = SqlCodegenUuidTypeNames.fromSchema(schema, shapeIr)
              val (stringEnums, intEnums)             =
                SqlEnumExtractor.extractReferenced(
                  model = model,
                  namespace = service.shapeId.getNamespace,
                  structures = shapeIr.structures ++ derivedModels,
                  unions = shapeIr.unions,
                  extraTypeNames = resolvedOperations.flatMap(_.parameters.map(_.typeName)) ++
                    resolvedOperations.flatMap(_.outputTypeName)
                )
              val baseContext                         =
                SqlCodegenServiceContext(
                  shapeId = service.shapeId,
                  name = service.shapeId.getName,
                  moduleName = moduleName,
                  namespace = service.shapeId.getNamespace,
                  version = service.version,
                  dialectKey = queryRenderer.map(_.key).getOrElse(SqlServiceCodegenSettings.SharedDialectKey),
                  packageName = CodegenPackageNames.resolvePackageName(
                    settings.rootNamespace,
                    service.shapeId.getNamespace,
                    settings.packageNameOverride
                  ),
                  bindPlaceholderStyle = bindPlaceholderStyle,
                  hasSqlOperations = resolvedOperations.exists(_.sql.isDefined),
                  models = (shapeIr.structures ++ derivedModels).sortBy(_.shapeId.toString),
                  unions = shapeIr.unions.sortBy(_.shapeId.toString),
                  stringEnums = stringEnums,
                  intEnums = intEnums,
                  operations = resolvedOperations,
                  uuidTypeNames = uuidTypeNames
                )

              val migrationDirectory =
                queryRenderer.flatMap(renderer => settings.migrationDirectories.get(renderer.key))
              baseContext.copy(
                integrationTest = queryRenderer.flatMap(_ =>
                  SqlCodegenIntegrationTestBuilder.build(
                    baseContext,
                    schema,
                    queries,
                    settings.schemaDdlRenderers
                  )),
                migration = migrationDirectory.flatMap(directory =>
                  queryRenderer.flatMap(renderer =>
                    SqlCodegenMigrationBuilder.build(schema, renderer.key, settings.schemaDdlRenderers, directory)))
              )
            }
        }
      }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def applySelectOneDerivedOutputs(
        operations: List[SqlCodegenOperation],
        queries: SqlQueries
    ): (List[SqlCodegenOperation], List[SqlStructure]) = {
      val derivedByOperation =
        operations.flatMap { operation =>
          SqlOperationQueryResolver.resolve(queries, operation.shapeId).flatMap {
            case ResolvedSqlOperationQuery.SelectOne(selectOneQuery) if selectOneQuery.hasNestedResults =>
              val derived = SqlSelectOneDerivedOutputBuilder.build(operation.name, selectOneQuery)
              Some(operation.shapeId -> derived)
            case _                                                                                      =>
              None
          }
        }.toMap

      val resolvedOperations =
        operations.map { operation =>
          derivedByOperation.get(operation.shapeId) match {
            case Some(derived) =>
              operation.copy(
                outputShapeId = Some(derived.structure.shapeId),
                outputTypeName = Some(derived.typeName)
              )
            case None          =>
              operation
          }
        }

      (resolvedOperations, derivedByOperation.values.map(_.structure).toList)
    }

    def buildOperation(
        model: Model,
        shapeIr: SqlShapeIr,
        queries: SqlQueries,
        operation: SqlOperation,
        queryRenderer: Option[SqlQueryRenderer]
    ): SqlValidated[SqlCodegenOperation] = {
      val resolvedQuery    = SqlOperationQueryResolver.resolve(queries, operation.shapeId)
      val usesDerivedInput = operation.inputShape == SqlQueryExtractor.DerivedStructShapeId

      val parameters =
        resolvedQuery match {
          case Some(query) if usesDerivedInput =>
            SqlOperationBindingBuilder.parametersFromQuery(operation, query)
          case _                               =>
            buildInputParameters(shapeIr, operation)
        }

      val sqlBinding =
        (resolvedQuery, queryRenderer) match {
          case (Some(query), Some(renderer)) =>
            SqlOperationBindingBuilder.build(operation, query, renderer).map(Some(_))
          case _                             =>
            None.validNel
        }

      (parameters, sqlBinding).mapN { (resolvedParameters, resolvedSql) =>
        val isSelectOne =
          resolvedQuery.exists(_.isInstanceOf[ResolvedSqlOperationQuery.SelectOne])

        val errors =
          if (isSelectOne) {
            Nil
          } else {
            operation.errorShapes.map { errorShapeId =>
              SqlCodegenErrorType(
                name = errorShapeId.getName
              )
            }
          }

        val outputShapeId =
          operation.outputShape.filter(_ != SqlShapeGraph.UnitShapeId)

        val outputTypeName =
          outputShapeId.map(shapeId => SqlIrTypeNameResolver.resolveShapeTypeName(model, shapeId))

        SqlCodegenOperation(
          shapeId = operation.shapeId,
          name = operation.name,
          parameters = resolvedParameters,
          outputShapeId = outputShapeId,
          outputTypeName = outputTypeName,
          errors = errors,
          isSelectOne = isSelectOne,
          sql = resolvedSql
        )
      }
    }

    def buildInputParameters(
        shapeIr: SqlShapeIr,
        operation: SqlOperation
    ): SqlValidated[List[SqlCodegenParameter]] =
      if (operation.inputShape == SqlShapeGraph.UnitShapeId) {
        Nil.validNel
      } else {
        shapeIr.structure(operation.inputShape) match {
          case None                 =>
            InvalidCodegenShape(operation.inputShape, "expected a structure shape").invalidNel
          case Some(inputStructure) =>
            inputStructure.members.map { member =>
              SqlCodegenParameter(
                name = member.name,
                typeName = member.typeName,
                optional = member.optional,
                isStructure = member.isStructure,
                structureShapeId = member.structureShapeId
              )
            }.validNel
        }
      }
  }
}
