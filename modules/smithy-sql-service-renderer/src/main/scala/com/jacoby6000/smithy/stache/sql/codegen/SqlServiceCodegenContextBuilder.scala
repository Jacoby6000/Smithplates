package com.jacoby6000.smithy.stache.sql.codegen

import cats.syntax.all.*
import com.jacoby6000.smithy.stache.sql.SqlSchema
import com.jacoby6000.smithy.stache.sql.SqlValidated
import com.jacoby6000.smithy.stache.sql.codegen.python.SqlCodegenTypeResolver
import com.jacoby6000.smithy.stache.sql.codegen.python.SqlOperationSqlBindingBuilder
import com.jacoby6000.smithy.stache.sql.query.SqlBindPlaceholder
import com.jacoby6000.smithy.stache.sql.query.SqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.service.SqlOperation
import com.jacoby6000.smithy.stache.sql.service.SqlQueries
import com.jacoby6000.smithy.stache.sql.service.SqlQueryExtractor
import com.jacoby6000.smithy.stache.sql.service.SqlService
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
              List(operation.inputShape) ++ operation.outputShape.toList ++ operation.errorShapes
            }
            .filterNot(_ == SqlCodegenTypeResolver.UnitShapeId)
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
          SqlOperationSqlBindingBuilder.parametersFromQuery(model, operation, query)
        case _                               =>
          buildInputParameters(model, operation)
      }

    val sqlBinding =
      resolvedQuery match {
        case Some(query) =>
          SqlOperationSqlBindingBuilder.build(model, operation, query, queryRenderer).map(Some(_))
        case None        =>
          None.validNel
      }

    (parameters, sqlBinding).mapN { (resolvedParameters, resolvedSql) =>
      val errors =
        operation.errorShapes.map { errorShapeId =>
          SqlCodegenErrorType(
            shapeId = errorShapeId,
            name = errorShapeId.getName,
            className = SqlCodegenNaming.className(errorShapeId)
          )
        }

      val outputClassName =
        operation.outputShape
          .filter(_ != SqlCodegenTypeResolver.UnitShapeId)
          .filterNot(SqlCodegenTypeResolver.isPreludeShape)
          .map(SqlCodegenNaming.className)

      val outputPythonTypeName =
        operation.outputShape
          .filter(_ != SqlCodegenTypeResolver.UnitShapeId)
          .map(SqlCodegenTypeResolver.resolveOutputPythonTypeName)

      val responseUnion = buildResponseUnion(outputPythonTypeName, errors)
      SqlCodegenOperation(
        shapeId = operation.shapeId,
        name = operation.name,
        methodName = SqlCodegenNaming.methodName(operation.name),
        parameters = resolvedParameters,
        outputShapeId = operation.outputShape.filter(_ != SqlCodegenTypeResolver.UnitShapeId),
        outputClassName = outputClassName,
        errors = errors,
        responseUnion = responseUnion,
        pythonResponseType = responseUnion,
        sql = resolvedSql
      )
    }
  }

  private def buildInputParameters(
      model: Model,
      operation: SqlOperation
  ): SqlValidated[List[SqlCodegenParameter]] =
    if (operation.inputShape == SqlCodegenTypeResolver.UnitShapeId) {
      Nil.validNel
    } else {
      SqlShapeCodegenExtractor.extractStructure(model, operation.inputShape).map { inputStructure =>
        inputStructure.members.map { member =>
          SqlCodegenParameter(
            name = member.name,
            typeName = member.typeName,
            pythonTypeName = member.pythonTypeName,
            optional = member.optional,
            isStructure = member.isStructure,
            structureShapeId = member.structureShapeId
          )
        }
      }
    }

  private def buildResponseUnion(
      outputPythonTypeName: Option[String],
      errors: List[SqlCodegenErrorType]
  ): String = {
    val responseTypes =
      outputPythonTypeName.toList ++ errors.map(_.className)
    responseTypes match {
      case Nil           => "None"
      case single :: Nil => single
      case many          => many.mkString(" | ")
    }
  }
}
