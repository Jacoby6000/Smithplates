package com.jacoby6000.smithplates.codegen.core

import cats.syntax.all.*

/** Holistic validation of a [[ModelSet]] together with a [[ServiceModel]]. */
final class SystemValidator[A, B, C](
    modelSetValidator: ModelSetValidator[A],
    serviceModelValidator: ServiceModelValidator[B, C]
) {
  def validate(modelSet: ModelSet[A], service: ServiceModel[B, C]): CodegenValidated[Unit] = {
    val crossCuttingErrors =
      Validation.internal.crossEntityDuplicateIds(modelSet, service) ++
        Validation.internal.unresolvedOperationRefs(modelSet, service)
    (
      modelSetValidator.validate(modelSet),
      serviceModelValidator.validate(service),
      CodegenValidated.fromErrors(crossCuttingErrors)
    ).mapN((_, _, _) => ())
  }
}

object SystemValidator {
  def apply[A, B, C](using
      modelSetValidator: ModelSetValidator[A],
      serviceModelValidator: ServiceModelValidator[B, C]
  ): SystemValidator[A, B, C] =
    new SystemValidator(modelSetValidator, serviceModelValidator)

  def default[A, B, C](using
      modelSetValidator: ModelSetValidator[A],
      serviceModelValidator: ServiceModelValidator[B, C]
  ): SystemValidator[A, B, C] =
    apply
}
