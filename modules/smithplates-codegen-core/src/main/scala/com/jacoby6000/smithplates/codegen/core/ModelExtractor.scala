package com.jacoby6000.smithplates.codegen.core

import cats.syntax.all.*

/** Lowers a Smithy model into language-neutral [[ModelSet]] and [[ServiceModel]] IR. */
trait ModelExtractor[A, S, O] {
  type SmithyModel

  def extract(model: SmithyModel): CodegenValidated[(ModelSet[A], List[ServiceModel[S, O]])]
}

object ModelExtractor {
  def validateServices[A, S, O](
      modelSet: ModelSet[A],
      services: List[ServiceModel[S, O]]
  )(using validator: SystemValidator[A, S, O]): CodegenValidated[Unit] =
    services
      .traverse(service => validator.validate(modelSet, service))
      .map(_ => ())
}
