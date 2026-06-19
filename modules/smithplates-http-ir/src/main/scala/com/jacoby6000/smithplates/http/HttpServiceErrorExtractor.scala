package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

private[http] object HttpServiceErrorExtractor {
  def extract(
      model: Model,
      serviceShape: ShapeId,
      errorShapeIds: List[ShapeId]
  ): HttpValidated[(List[HttpServiceError], List[HttpSchemaWarning])] = {
    val context = HttpErrorShapeExtractor.ServiceContext(serviceShape)
    errorShapeIds.distinct
      .traverse(errorShapeId => internal.extractError(model, context, serviceShape, errorShapeId))
      .map { results =>
        (results.map(_._1), results.flatMap(_._2))
      }
  }

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def extractError(
        model: Model,
        context: HttpErrorShapeExtractor.ServiceContext,
        serviceShape: ShapeId,
        errorShapeId: ShapeId
    ): HttpValidated[(HttpServiceError, List[HttpSchemaWarning])] =
      HttpErrorShapeExtractor.extract(model, context, errorShapeId).map { extracted =>
        val problemBinding = HttpProblemBindingExtractor.extract(model, errorShapeId)
        val warnings       = problemBinding.toList.flatMap { binding =>
          HttpProblemBindingExtractor.lintProblemType(serviceShape, errorShapeId, binding.problemType)
        }
        (
          HttpServiceError(
            shapeId = extracted.shapeId,
            name = extracted.name,
            statusCode = extracted.statusCode,
            problemBinding = problemBinding
          ),
          warnings
        )
      }
  }
}
