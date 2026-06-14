package com.jacoby6000.smithplates.http.transform

import com.jacoby6000.smithplates.http.traits.HttpProblemTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.HttpErrorTrait
import software.amazon.smithy.model.transform.ModelTransformer

import scala.jdk.OptionConverters.*

/** Materializes implied Smithy traits from smithplates HTTP traits for downstream tooling. */
object HttpProblemHttpErrorModelTransformer {
  def transform(model: Model): Model =
    ModelTransformer.create().mapShapes(model, shape => transformShape(shape))

  private def transformShape(shape: Shape): Shape =
    if (!shape.isStructureShape) {
      shape
    } else {
      val structure = shape.asStructureShape.get()
      if (structure.getTrait(classOf[HttpErrorTrait]).isPresent) {
        structure
      } else {
        structure.getTrait(classOf[HttpProblemTrait]).toScala.flatMap { httpProblem =>
          Option(httpProblem.getCode)
        } match {
          case Some(code) =>
            structure
              .toBuilder()
              .addTrait(new HttpErrorTrait(code.intValue(), structure.getSourceLocation))
              .build()
          case None       =>
            structure
        }
      }
    }
}
