package com.jacoby6000.smithplates.http.transform

import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model

/** Smithy build projection transform registered as `stripSmithplatesHttpCodegenTraits`. */
final class StripSmithplatesHttpCodegenTraitsTransformer extends ProjectionTransformer {
  override def getName: String = StripSmithplatesHttpCodegenTraitsTransformer.Name

  override def transform(context: TransformContext): Model =
    StripSmithplatesHttpCodegenTraitsModelTransformer.transform(context.getModel)
}

object StripSmithplatesHttpCodegenTraitsTransformer {
  val Name: String = "stripSmithplatesHttpCodegenTraits"
}
