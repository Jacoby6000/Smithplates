package com.jacoby6000.smithplates.http.transform

import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model

/** Smithy build projection transform registered as `applyHttpProblemHttpError`. */
final class ApplyHttpProblemHttpErrorTransformer extends ProjectionTransformer {
  override def getName: String = ApplyHttpProblemHttpErrorTransformer.Name

  override def transform(context: TransformContext): Model =
    HttpProblemHttpErrorModelTransformer.transform(context.getModel)
}

object ApplyHttpProblemHttpErrorTransformer {
  val Name: String = "applyHttpProblemHttpError"
}
