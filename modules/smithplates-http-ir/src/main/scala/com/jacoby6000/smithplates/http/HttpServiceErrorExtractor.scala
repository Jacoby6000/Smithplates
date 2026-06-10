package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.ErrorTrait
import software.amazon.smithy.model.traits.HttpErrorTrait

import scala.jdk.OptionConverters.*

private[http] object HttpServiceErrorExtractor {
  def extract(
      model: Model,
      serviceShape: ShapeId,
      errorShapeIds: List[ShapeId]): HttpValidated[List[HttpServiceError]] =
    errorShapeIds.distinct.traverse(errorShapeId => extractError(model, serviceShape, errorShapeId))

  private def extractError(
      model: Model,
      serviceShape: ShapeId,
      errorShapeId: ShapeId
  ): HttpValidated[HttpServiceError] =
    model.getShape(errorShapeId).toScala match {
      case None                                   =>
        InvalidHttpService(
          serviceShape,
          s"service error shape '${errorShapeId.toString}' is not defined in the model"
        ).invalidNel
      case Some(shape) if !shape.isStructureShape =>
        InvalidHttpService(
          serviceShape,
          s"service error shape '${errorShapeId.toString}' must be a structure"
        ).invalidNel
      case Some(_)                                =>
        (
          requireErrorTrait(model, serviceShape, errorShapeId),
          requireHttpErrorTrait(model, serviceShape, errorShapeId)
        ).mapN { (_, httpError) =>
          HttpServiceError(
            shapeId = errorShapeId,
            name = errorShapeId.getName,
            statusCode = httpError.getCode
          )
        }
    }

  private def requireErrorTrait(
      model: Model,
      serviceShape: ShapeId,
      errorShapeId: ShapeId
  ): HttpValidated[ErrorTrait] =
    model.getShape(errorShapeId).toScala.flatMap(_.getTrait(classOf[ErrorTrait]).toScala) match {
      case Some(errorTrait) => errorTrait.validNel
      case None             =>
        InvalidHttpService(
          serviceShape,
          s"service error shape '${errorShapeId.toString}' must declare @error"
        ).invalidNel
    }

  private def requireHttpErrorTrait(
      model: Model,
      serviceShape: ShapeId,
      errorShapeId: ShapeId
  ): HttpValidated[HttpErrorTrait] =
    model.getShape(errorShapeId).toScala.flatMap(_.getTrait(classOf[HttpErrorTrait]).toScala) match {
      case Some(httpErrorTrait) => httpErrorTrait.validNel
      case None                 =>
        InvalidHttpService(
          serviceShape,
          s"service error shape '${errorShapeId.toString}' must declare @httpError"
        ).invalidNel
    }
}
