package com.jacoby6000.smithy.stache.sql

import cats.syntax.all.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[sql] object SqlServiceExtractor {
  def extract(model: Model): SqlValidated[List[SqlService]] =
    model.getServiceShapes.asScala.toList
      .filter(service => SmithySqlTraitAccess.sqlService(service).isDefined)
      .traverse(extractService(model, _))

  private def extractService(model: Model, service: ServiceShape): SqlValidated[SqlService] = {
    val serviceShape = service.getId

    (
      requireServiceVersion(service),
      validateNoResources(service),
      extractOperations(model, service)
    ).mapN { (version, _, operations) =>
      SqlService(shapeId = serviceShape, version = version, operations = operations)
    }
  }

  private def requireServiceVersion(service: ServiceShape): SqlValidated[String] =
    Option(service.getVersion).filter(_.nonEmpty) match {
      case Some(version) => version.validNel
      case None          => MissingSqlServiceVersion(service.getId).invalidNel
    }

  private def validateNoResources(service: ServiceShape): SqlValidated[Unit] =
    if (service.getResources.isEmpty) {
      ().validNel
    } else {
      InvalidSqlService(
        service.getId,
        "resources are not supported on @sqlService; use @sqlTable structures with member traits instead (resource properties cannot carry @sqlPrimaryKey, @sqlForeignKey, @sqlColumn, etc.)"
      ).invalidNel
    }

  private def extractOperations(model: Model, service: ServiceShape): SqlValidated[List[SqlOperation]] =
    service.getOperations.asScala.toList
      .traverse(operationId => resolveOperation(model, service.getId, operationId))
      .andThen { operations =>
        if (operations.isEmpty) {
          EmptySqlService(service.getId).invalidNel
        } else {
          operations.validNel
        }
      }

  private def resolveOperation(
      model: Model,
      serviceShape: ShapeId,
      operationId: ShapeId
  ): SqlValidated[SqlOperation] =
    model.getShape(operationId).toScala.flatMap(_.asOperationShape.toScala) match {
      case None            =>
        InvalidSqlOperation(
          serviceShape,
          operationId.getName,
          s"operation '${operationId.toString}' is not defined in the model"
        ).invalidNel
      case Some(operation) =>
        extractOperation(model, serviceShape, operation)
    }

  private def extractOperation(
      model: Model,
      serviceShape: ShapeId,
      operation: OperationShape
  ): SqlValidated[SqlOperation] =
    (
      requireInputShape(serviceShape, operation),
      validateOutputShape(operation),
      validateErrorShapes(model, serviceShape, operation)
    ).mapN { (inputShape, outputShape, errorShapes) =>
      SqlOperation(
        shapeId = operation.getId,
        name = operation.getId.getName,
        inputShape = inputShape,
        outputShape = outputShape,
        errorShapes = errorShapes
      )
    }

  private def requireInputShape(serviceShape: ShapeId, operation: OperationShape): SqlValidated[ShapeId] =
    Option(operation.getInputShape) match {
      case Some(inputShape) => inputShape.validNel
      case None             =>
        InvalidSqlOperation(
          serviceShape,
          operation.getId.getName,
          "operation must declare an input shape"
        ).invalidNel
    }

  private def validateOutputShape(operation: OperationShape): SqlValidated[Option[ShapeId]] =
    operation.getOutput.toScala.orElse(Some(operation.getOutputShape)).validNel

  private def validateErrorShapes(
      model: Model,
      serviceShape: ShapeId,
      operation: OperationShape
  ): SqlValidated[List[ShapeId]] =
    operation.getErrorsSet.asScala.toList
      .traverse(errorShapeId => validateErrorShape(model, serviceShape, operation, errorShapeId))

  private def validateErrorShape(
      model: Model,
      serviceShape: ShapeId,
      operation: OperationShape,
      errorShapeId: ShapeId
  ): SqlValidated[ShapeId] =
    model.getShape(errorShapeId).toScala match {
      case None                                  =>
        InvalidSqlOperation(
          serviceShape,
          operation.getId.getName,
          s"error shape '${errorShapeId.toString}' is not defined in the model"
        ).invalidNel
      case Some(shape) if shape.isStructureShape =>
        errorShapeId.validNel
      case Some(_)                               =>
        InvalidSqlOperation(
          serviceShape,
          operation.getId.getName,
          s"error shape '${errorShapeId.toString}' must be a structure"
        ).invalidNel
    }
}
