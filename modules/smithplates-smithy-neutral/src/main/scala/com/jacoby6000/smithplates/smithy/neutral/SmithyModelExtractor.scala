package com.jacoby6000.smithplates.smithy.neutral

import com.jacoby6000.smithplates.codegen.core.*
import software.amazon.smithy.model.Model

/** Smithy-backed [[ModelExtractor]] with post-extraction [[SystemValidator]] checks. */
trait SmithyModelExtractor[A, S, O] extends ModelExtractor[A, S, O] {
  override type SmithyModel = Model

  def extractValidated(model: Model)(using
      validator: SystemValidator[A, S, O]
  ): CodegenValidated[(ModelSet[A], List[ServiceModel[S, O]])] =
    extract(model).andThen { case (modelSet, services) =>
      ModelExtractor.validateServices(modelSet, services).map(_ => (modelSet, services))
    }
}
