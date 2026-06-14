package com.jacoby6000.smithplates.http

import com.jacoby6000.smithplates.http.model.*
import com.jacoby6000.smithplates.http.traits.HttpProblemTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

import scala.jdk.OptionConverters.*

private[http] object HttpProblemBindingExtractor {
  val ProblemContentTypeHeader: (String, String) =
    ("Content-Type", "application/problem+json")

  def hasProblemTrait(model: Model, shapeId: ShapeId): Boolean =
    extract(model, shapeId).isDefined

  def extract(model: Model, errorShapeId: ShapeId): Option[HttpProblemBinding] =
    model.getShape(errorShapeId).toScala.flatMap(_.getTrait(classOf[HttpProblemTrait]).toScala).map { traitValue =>
      HttpProblemBinding(
        problemType = traitValue.getType,
        title = traitValue.getTitle,
        defaultDetail = Option(traitValue.getDetail).filter(_.nonEmpty)
      )
    }

  def lintProblemType(
      serviceShape: ShapeId,
      errorShapeId: ShapeId,
      problemType: String
  ): Option[HttpProblemTypeWarning] =
    Option.unless(problemType.startsWith("https://")) {
      HttpProblemTypeWarning(
        serviceShape = serviceShape,
        errorShape = errorShapeId,
        problemType = problemType
      )
    }
}
