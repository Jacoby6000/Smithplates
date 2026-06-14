package com.jacoby6000.smithplates.http

import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId

private[http] object HttpOperationErrorExtractor {
  def extract(
      model: Model,
      serviceShape: ShapeId,
      operationName: String,
      errorShapeIds: List[ShapeId]
  ): HttpValidated[List[HttpOperationError]] = {
    val context = HttpErrorShapeExtractor.OperationContext(serviceShape, operationName)
    errorShapeIds.distinct.traverse { errorShapeId =>
      HttpErrorShapeExtractor.extract(model, context, errorShapeId).map { extracted =>
        HttpOperationError(
          shapeId = extracted.shapeId,
          name = extracted.name,
          statusCode = extracted.statusCode
        )
      }
    }
  }
}
