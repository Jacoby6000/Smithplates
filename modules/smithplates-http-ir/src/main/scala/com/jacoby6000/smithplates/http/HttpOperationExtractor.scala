package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.SmithyHttpTraitAccess.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

private[http] object HttpOperationExtractor {
  def extract(
      model: Model,
      serviceShape: ShapeId,
      operationId: ShapeId,
      serviceResources: List[HttpResource]
  ): HttpValidated[HttpOperation] =
    model.getShape(operationId).toScala.flatMap(_.asOperationShape.toScala) match {
      case None            =>
        InvalidHttpOperation(
          serviceShape,
          operationId.getName,
          s"operation '${operationId.toString}' is not defined in the model"
        ).invalidNel
      case Some(operation) =>
        extractOperation(model, serviceShape, operation, serviceResources)
    }

  private def extractOperation(
      model: Model,
      serviceShape: ShapeId,
      operation: OperationShape,
      serviceResources: List[HttpResource]
  ): HttpValidated[HttpOperation] = {
    val operationName = operation.getId.getName
    (
      requireHttpBinding(serviceShape, operationName, operation),
      requireRouteTag(serviceShape, operationName, operation),
      requireInputShape(serviceShape, operationName, operation),
      validateOutputShape(operation),
      validateErrorShapes(model, serviceShape, operation)
    ).mapN { (http, tag, inputShape, outputShape, errorShapes) =>
      HttpOperationInputMemberExtractor
        .extract(model, serviceShape, operation, inputShape, serviceResources)
        .map { inputMembers =>
          HttpOperation(
            shapeId = operation.getId,
            name = operationName,
            method = http.getMethod,
            uri = http.getUri.toString,
            successStatusCode = http.getCode,
            readonly = operation.readonlyOperation,
            documentation = operation.documentationText,
            inputShape = inputShape,
            inputBoundResource = HttpOperationInputMemberExtractor
              .inputBoundResource(model, inputShape, operation.getId, serviceResources),
            inputMembers = inputMembers,
            outputShape = outputShape,
            errorShapes = errorShapes,
            tags = List(tag)
          )
        }
    }.andThen(identity)
  }

  private def requireHttpBinding(
      serviceShape: ShapeId,
      operationName: String,
      operation: OperationShape
  ): HttpValidated[software.amazon.smithy.model.traits.HttpTrait] =
    operation.httpBinding match {
      case Some(http) => http.validNel
      case None       =>
        InvalidHttpOperation(
          serviceShape,
          operationName,
          "operation must declare an @http binding"
        ).invalidNel
    }

  private def requireRouteTag(
      serviceShape: ShapeId,
      operationName: String,
      operation: OperationShape
  ): HttpValidated[String] =
    operation.tags.headOption match {
      case Some(tag) if tag.nonEmpty => tag.validNel
      case _                         =>
        InvalidHttpOperation(
          serviceShape,
          operationName,
          "operation must declare at least one @tags value for route grouping"
        ).invalidNel
    }

  private def requireInputShape(
      serviceShape: ShapeId,
      operationName: String,
      operation: OperationShape
  ): HttpValidated[ShapeId] =
    Option(operation.getInputShape) match {
      case Some(inputShape) => inputShape.validNel
      case None             =>
        InvalidHttpOperation(
          serviceShape,
          operationName,
          "operation must declare an input shape"
        ).invalidNel
    }

  private def validateOutputShape(operation: OperationShape): HttpValidated[Option[ShapeId]] =
    operation.getOutput.toScala.orElse(Some(operation.getOutputShape)).validNel

  private def validateErrorShapes(
      model: Model,
      serviceShape: ShapeId,
      operation: OperationShape
  ): HttpValidated[List[ShapeId]] =
    operation.getErrorsSet.asScala.toList
      .traverse(errorShapeId => validateErrorShape(model, serviceShape, operation, errorShapeId))

  private def validateErrorShape(
      model: Model,
      serviceShape: ShapeId,
      operation: OperationShape,
      errorShapeId: ShapeId
  ): HttpValidated[ShapeId] =
    model.getShape(errorShapeId).toScala match {
      case None                                  =>
        InvalidHttpOperation(
          serviceShape,
          operation.getId.getName,
          s"error shape '${errorShapeId.toString}' is not defined in the model"
        ).invalidNel
      case Some(shape) if shape.isStructureShape =>
        errorShapeId.validNel
      case Some(_)                               =>
        InvalidHttpOperation(
          serviceShape,
          operation.getId.getName,
          s"error shape '${errorShapeId.toString}' must be a structure"
        ).invalidNel
    }
}
