package com.jacoby6000.smithplates.http.transform

import com.jacoby6000.smithplates.http.traits.HttpProblemTrait
import com.jacoby6000.smithplates.http.traits.HttpServiceTrait
import com.jacoby6000.smithplates.http.traits.HttpStaticHeaderTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.model.transform.ModelTransformer

/** Removes smithplates HTTP codegen traits after implied Smithy traits are materialized. */
object StripSmithplatesHttpCodegenTraitsModelTransformer {
  def transform(model: Model): Model =
    ModelTransformer
      .create()
      .removeTraitsIf(
        model,
        (_: Shape, traitShape: Trait) => internal.StripTraitIds.contains(traitShape.toShapeId)
      )

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val StripTraitIds =
      Set(
        HttpServiceTrait.ID,
        HttpProblemTrait.ID,
        HttpStaticHeaderTrait.ID
      )
  }
}
