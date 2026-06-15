package com.jacoby6000.smithplates.http.transform

import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model

/** Smithy build projection transform registered as `applyHttpServiceRestJson1`. */
final class ApplyHttpServiceRestJson1Transformer extends ProjectionTransformer {
  override def getName: String = ApplyHttpServiceRestJson1Transformer.Name

  override def transform(context: TransformContext): Model =
    ApplyHttpServiceRestJson1ModelTransformer.transform(context.getModel)
}

object ApplyHttpServiceRestJson1Transformer {
  val Name: String = "applyHttpServiceRestJson1"
}
