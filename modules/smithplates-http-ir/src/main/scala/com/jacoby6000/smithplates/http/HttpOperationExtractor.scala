package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.SmithyHttpTraitAccess.*
import com.jacoby6000.smithplates.http.model.*
import com.jacoby6000.smithplates.http.traits.WebsocketTrait
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
      serviceResources: List[HttpResource],
      serialization: HttpSerialization
  ): HttpValidated[(HttpOperation, List[HttpSchemaWarning])] =
    model.getShape(operationId).toScala.flatMap(_.asOperationShape.toScala) match {
      case None            =>
        InvalidHttpOperation(
          serviceShape,
          operationId.getName,
          s"operation '${operationId.toString}' is not defined in the model"
        ).invalidNel
      case Some(operation) =>
        internal.extractOperation(model, serviceShape, operation, serviceResources, serialization)
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def extractOperation(
        model: Model,
        serviceShape: ShapeId,
        operation: OperationShape,
        serviceResources: List[HttpResource],
        serialization: HttpSerialization
    ): HttpValidated[(HttpOperation, List[HttpSchemaWarning])] = {
      val operationName = operation.getId.getName
      val isWebsocket   = operation.getTrait(classOf[WebsocketTrait]).toScala.isDefined
      (
        requireHttpBinding(serviceShape, operationName, operation),
        requireRouteTag(serviceShape, operationName, operation),
        requireInputShape(serviceShape, operationName, operation),
        validateOutputShape(operation),
        validateErrorShapes(model, serviceShape, operation)
      ).mapN { (http, tag, inputShape, outputShape, errorShapeIds) =>
        HttpOperationInputMemberExtractor
          .extract(model, serviceShape, operation, inputShape, serviceResources, isWebsocket)
          .andThen { inputMembers =>
            val uri            = http.getUri.toString
            val warnings       =
              HttpInputMemberOrdering
                .lintInputMemberOrder(serviceShape, operationName, inputShape, uri, inputMembers)
                .toList
            val orderedMembers = HttpInputMemberOrdering.orderInputMembers(uri, inputMembers)
            val bodyBinding    =
              if (isWebsocket && orderedMembers.isEmpty && inputShape != ShapeId.from("smithy.api#Unit")) {
                // Websocket operations with union-typed inputs have no extracted input members
                // (unions aren't structures). The entire input shape is the message body.
                HttpOperationBodyBinding.Document(inputShape)
              } else {
                HttpInputBodyBindingResolver.resolve(inputShape, orderedMembers)
              }
            (
              HttpOperationOutputMemberExtractor
                .extract(model, serviceShape, operationName, outputShape, isWebsocket),
              HttpOperationErrorExtractor.extract(model, serviceShape, operationName, errorShapeIds)
            ).mapN { (outputMembers, operationErrors) =>
              HttpResponseVariantResolver
                .resolveOperationBinding(
                  model = model,
                  serviceShape = serviceShape,
                  operationName = operationName,
                  successStatusCode = http.getCode,
                  outputShape = outputShape,
                  outputMembers = outputMembers,
                  operationErrors = operationErrors,
                  serialization = serialization
                )
                .map { responseBinding =>
                  (
                    HttpOperation(
                      shapeId = operation.getId,
                      name = operationName,
                      method = http.getMethod,
                      uri = uri,
                      successStatusCode = http.getCode,
                      readonly = operation.readonlyOperation,
                      documentation = operation.documentationText,
                      inputShape = inputShape,
                      inputBoundResource = HttpOperationInputMemberExtractor
                        .inputBoundResource(model, inputShape, operation.getId, serviceResources),
                      inputMembers = orderedMembers,
                      bodyBinding = bodyBinding,
                      outputShape = outputShape,
                      outputMembers = outputMembers,
                      operationErrors = operationErrors,
                      responseBinding = responseBinding,
                      tags = List(tag),
                      websocket = isWebsocket
                    ),
                    warnings
                  )
                }
            }
          }
      }.andThen(identity)
        .andThen(identity)
    }

    def requireHttpBinding(
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

    def requireRouteTag(
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

    def requireInputShape(
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

    def validateOutputShape(operation: OperationShape): HttpValidated[Option[ShapeId]] =
      operation.getOutput.toScala.orElse(Some(operation.getOutputShape)).validNel

    def validateErrorShapes(
        model: Model,
        serviceShape: ShapeId,
        operation: OperationShape
    ): HttpValidated[List[ShapeId]] =
      operation.getErrorsSet.asScala.toList
        .traverse(errorShapeId => validateErrorShape(model, serviceShape, operation, errorShapeId))

    def validateErrorShape(
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
}
