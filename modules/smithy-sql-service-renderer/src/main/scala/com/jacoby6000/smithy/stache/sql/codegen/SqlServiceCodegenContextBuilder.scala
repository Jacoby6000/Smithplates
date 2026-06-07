package com.jacoby6000.smithy.stache.sql.codegen

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.SqlSchema
import com.jacoby6000.smithy.stache.sql.SqlValidated
import com.jacoby6000.smithy.stache.sql.codegen.language.LanguageTypeResolver
import com.jacoby6000.smithy.stache.sql.query.SqlBindPlaceholder
import com.jacoby6000.smithy.stache.sql.query.SqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.service.SqlOperation
import com.jacoby6000.smithy.stache.sql.service.SqlQueries
import com.jacoby6000.smithy.stache.sql.service.SqlQueryExtractor
import com.jacoby6000.smithy.stache.sql.service.SqlService
import com.jacoby6000.smithy.stache.sql.service.codegen.ResolvedSqlOperationQuery
import com.jacoby6000.smithy.stache.sql.service.codegen.SqlOperationQueryResolver
import software.amazon.smithy.model.Model

object SqlServiceCodegenContextBuilder {
  def build(
      model: Model,
      schema: SqlSchema,
      queries: SqlQueries,
      service: SqlService,
      queryRenderer: SqlQueryRenderer,
      bindPlaceholderStyle: SqlBindPlaceholder,
      settings: SqlServiceCodegenSettings
  ): SqlValidated[SqlCodegenServiceContext] =
    service.operations
      .traverse(buildOperation(model, queries, _, queryRenderer))
      .andThen { operations =>
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
            .filterNot(_ == SqlCodegenShapeGraph.UnitShapeId)
            .filterNot(_ == SqlQueryExtractor.DerivedStructShapeId)
            .distinct

        val tableShapeIds = schema.tables.map(_.shapeId)

        (
          SqlShapeCodegenExtractor.collectStructures(model, rootShapeIds ++ tableShapeIds),
          SqlShapeCodegenExtractor.collectUnions(model, rootShapeIds ++ tableShapeIds)
        ).mapN { (models, unions) =>
          val baseContext =
            SqlCodegenServiceContext(
              shapeId = service.shapeId,
              name = service.shapeId.getName,
              namespace = service.shapeId.getNamespace,
              fileName = SqlCodegenNaming.serviceFileName(service.shapeId.getName),
              version = service.version,
              dialectKey = queryRenderer.key,
              queryRenderer = queryRenderer,
              bindPlaceholderStyle = bindPlaceholderStyle,
              implementationClassName =
                SqlCodegenDialectConfig.implementationClassName(service.shapeId.getName, queryRenderer.key),
              implementationModuleName = SqlCodegenDialectConfig.implementationModuleName(
                SqlCodegenNaming.serviceFileName(service.shapeId.getName),
                queryRenderer.key
              ),
              hasSqlOperations = operations.exists(_.sql.isDefined),
              models = models.sortBy(_.shapeId.toString),
              unions = unions.sortBy(_.shapeId.toString),
              operations = operations
            )

          baseContext.copy(
            integrationTest = SqlCodegenIntegrationTestBuilder.build(baseContext, schema, settings.schemaDdlRenderers)
          )
        }
      }

  private def buildOperation(
      model: Model,
      queries: SqlQueries,
      operation: SqlOperation,
      queryRenderer: SqlQueryRenderer
  ): SqlValidated[SqlCodegenOperation] = {
    val resolvedQuery    = SqlOperationQueryResolver.resolve(queries, operation.shapeId)
    val usesDerivedInput = operation.inputShape == SqlQueryExtractor.DerivedStructShapeId

    val parameters =
      resolvedQuery match {
        case Some(query) if usesDerivedInput =>
          SqlOperationBindingBuilder.parametersFromQuery(model, operation, query)
        case _                               =>
          buildInputParameters(model, operation)
      }

    val sqlBinding =
      resolvedQuery match {
        case Some(query) =>
          SqlOperationBindingBuilder.build(model, operation, query, queryRenderer).map(Some(_))
        case None        =>
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
              shapeId = errorShapeId,
              name = errorShapeId.getName,
              className = SqlCodegenNaming.className(errorShapeId)
            )
          }
        }

      val outputShapeId =
        operation.outputShape.filter(_ != SqlCodegenShapeGraph.UnitShapeId)

      val outputTypeName =
        outputShapeId.map(shapeId => LanguageTypeResolver.resolveShapeTypeName(model, shapeId))

      val outputClassName =
        outputShapeId
          .filterNot(SqlCodegenShapeGraph.isPreludeShape)
          .map(SqlCodegenNaming.className)

      SqlCodegenOperation(
        shapeId = operation.shapeId,
        name = operation.name,
        methodName = SqlCodegenNaming.methodName(operation.name),
        parameters = resolvedParameters,
        outputShapeId = outputShapeId,
        outputTypeName = outputTypeName,
        outputClassName = outputClassName,
        errors = errors,
        sql = resolvedSql
      )
    }
  }

  private def buildInputParameters(
      model: Model,
      operation: SqlOperation
  ): SqlValidated[List[SqlCodegenParameter]] =
    if (operation.inputShape == SqlCodegenShapeGraph.UnitShapeId) {
      Nil.validNel
    } else {
      SqlShapeCodegenExtractor.extractStructure(model, operation.inputShape).map { inputStructure =>
        inputStructure.members.map { member =>
          SqlCodegenParameter(
            name = member.name,
            typeName = member.typeName,
            optional = member.optional,
            isStructure = member.isStructure,
            structureShapeId = member.structureShapeId
          )
        }
      }
    }
}
