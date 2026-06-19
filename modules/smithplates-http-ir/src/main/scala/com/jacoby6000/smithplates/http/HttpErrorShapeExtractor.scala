package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.*
import com.jacoby6000.smithplates.http.traits.HttpProblemTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.ErrorTrait
import software.amazon.smithy.model.traits.HttpErrorTrait

import scala.jdk.OptionConverters.*

private[http] object HttpErrorShapeExtractor {
  sealed trait Context {
    def errorPrefix: String
    def invalid(reason: String): HttpSchemaError
  }

  final case class ServiceContext(serviceShape: ShapeId) extends Context {
    override def errorPrefix: String =
      s"service error shape"

    override def invalid(reason: String): HttpSchemaError =
      InvalidHttpService(serviceShape, reason)
  }

  final case class OperationContext(serviceShape: ShapeId, operationName: String) extends Context {
    override def errorPrefix: String =
      s"operation error shape"

    override def invalid(reason: String): HttpSchemaError =
      InvalidHttpOperation(serviceShape, operationName, reason)
  }

  final case class ExtractedError(
      shapeId: ShapeId,
      name: String,
      statusCode: Int
  )

  def extract(
      model: Model,
      context: Context,
      errorShapeId: ShapeId
  ): HttpValidated[ExtractedError] =
    model.getShape(errorShapeId).toScala match {
      case None                                   =>
        context.invalid(s"${context.errorPrefix} '${errorShapeId.toString}' is not defined in the model").invalidNel
      case Some(shape) if !shape.isStructureShape =>
        context
          .invalid(s"${context.errorPrefix} '${errorShapeId.toString}' must be a structure")
          .invalidNel
      case Some(_)                                =>
        (
          internal.requireErrorTrait(model, context, errorShapeId),
          internal.resolveStatusCode(model, context, errorShapeId)
        ).mapN { (_, statusCode) =>
          ExtractedError(
            shapeId = errorShapeId,
            name = errorShapeId.getName,
            statusCode = statusCode
          )
        }
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def requireErrorTrait(
        model: Model,
        context: Context,
        errorShapeId: ShapeId
    ): HttpValidated[ErrorTrait] =
      model.getShape(errorShapeId).toScala.flatMap(_.getTrait(classOf[ErrorTrait]).toScala) match {
        case Some(errorTrait) => errorTrait.validNel
        case None             =>
          context.invalid(s"${context.errorPrefix} '${errorShapeId.toString}' must declare @error").invalidNel
      }

    def resolveStatusCode(
        model: Model,
        context: Context,
        errorShapeId: ShapeId
    ): HttpValidated[Int] = {
      val shape           = model.getShape(errorShapeId).toScala
      val httpErrorCode   = shape.flatMap(_.getTrait(classOf[HttpErrorTrait]).toScala).map(_.getCode)
      val httpProblemCode =
        shape.flatMap(_.getTrait(classOf[HttpProblemTrait]).toScala).flatMap(traitValue => Option(traitValue.getCode))

      (httpErrorCode, httpProblemCode) match {
        case (Some(httpError), Some(problemCode)) if httpError != problemCode =>
          context
            .invalid(
              s"${context.errorPrefix} '${errorShapeId.toString}' declares @httpError($httpError) " +
                s"and @httpProblem(code: $problemCode) with different status codes"
            )
            .invalidNel
        case (Some(httpError), _)                                             =>
          httpError.validNel
        case (None, Some(problemCode))                                        =>
          problemCode.intValue().validNel
        case (None, None)                                                     =>
          context
            .invalid(
              s"${context.errorPrefix} '${errorShapeId.toString}' must declare @httpError " +
                s"or @httpProblem(code: ...)"
            )
            .invalidNel
      }
    }
  }
}
