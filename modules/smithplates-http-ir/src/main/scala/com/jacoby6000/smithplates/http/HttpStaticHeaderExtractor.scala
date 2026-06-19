package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.*
import com.jacoby6000.smithplates.http.traits.HttpStaticHeaderTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.OptionConverters.*

private[http] object HttpStaticHeaderExtractor {
  def extract(
      model: Model,
      serviceShape: ShapeId,
      structureShapeId: ShapeId,
      relatedShapeIds: List[ShapeId] = Nil
  ): HttpValidated[List[(String, String)]] =
    model.getShape(structureShapeId).toScala match {
      case None        =>
        InvalidHttpService(
          serviceShape,
          s"structure shape '${structureShapeId.toString}' is not defined in the model"
        ).invalidNel
      case Some(shape) =>
        val explicitHeaders =
          Option(shape.getTrait(classOf[HttpStaticHeaderTrait]).orElse(null)) match {
            case None             => Nil
            case Some(traitValue) => List((traitValue.getName, traitValue.getValue))
          }
        val impliedHeaders  = internal.impliedProblemContentType(model, structureShapeId :: relatedShapeIds)
        internal.mergeStaticHeaders(impliedHeaders, explicitHeaders).validNel
    }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def impliedProblemContentType(model: Model, shapeIds: List[ShapeId]): List[(String, String)] =
      if (shapeIds.exists(HttpProblemBindingExtractor.hasProblemTrait(model, _))) {
        List(HttpProblemBindingExtractor.ProblemContentTypeHeader)
      } else {
        Nil
      }

    def mergeStaticHeaders(
        implied: List[(String, String)],
        explicit: List[(String, String)]
    ): List[(String, String)] = {
      val explicitNames = explicit.map(_._1).toSet
      (implied.filterNot { case (name, _) => explicitNames.contains(name) } ++ explicit).sortBy(_._1)
    }
  }
}
