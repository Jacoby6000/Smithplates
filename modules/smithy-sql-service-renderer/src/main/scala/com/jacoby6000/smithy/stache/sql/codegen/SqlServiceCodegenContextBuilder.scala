package com.jacoby6000.smithy.stache.sql.codegen

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.*
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
  ): SqlValidated[SqlCodegenServiceContext] = {
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
        .traverse(buildOperation(model, shapeIr, queries, _, queryRenderer))
        .map { operations =>
          val baseContext =
            SqlCodegenServiceContext(
              shapeId = service.shapeId,
              name = service.shapeId.getName,
              namespace = service.shapeId.getNamespace,
              version = service.version,
              dialectKey = queryRenderer.key,
              queryRenderer = queryRenderer,
              bindPlaceholderStyle = bindPlaceholderStyle,
              hasSqlOperations = operations.exists(_.sql.isDefined),
              models = shapeIr.structures.sortBy(_.shapeId.toString),
              unions = shapeIr.unions.sortBy(_.shapeId.toString),
              operations = operations
            )

          baseContext.copy(
            integrationTest = SqlCodegenIntegrationTestBuilder.build(baseContext, schema, settings.schemaDdlRenderers)
          )
        }
    }
  }

  private def buildOperation(
      model: Model,
      shapeIr: SqlShapeIr,
      queries: SqlQueries,
      operation: SqlOperation,
      queryRenderer: SqlQueryRenderer
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
      resolvedQuery match {
        case Some(query) =>
          SqlOperationBindingBuilder.build(operation, query, queryRenderer).map(Some(_))
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
        sql = resolvedSql
      )
    }
  }

  private def buildInputParameters(
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
