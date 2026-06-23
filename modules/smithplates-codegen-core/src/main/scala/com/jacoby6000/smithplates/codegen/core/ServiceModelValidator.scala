package com.jacoby6000.smithplates.codegen.core

trait ServiceModelValidator[A, B] {
  def validate(service: ServiceModel[A, B]): CodegenValidated[Unit]
}

object ServiceModelValidator {
  def apply[A, B](using
      operationValidator: OperationValidator[B],
      metaValidator: ServiceMetaValidator[A]
  ): ServiceModelValidator[A, B] =
    DefaultServiceModelValidator(operationValidator, metaValidator)

  def default[A, B](using
      operationValidator: OperationValidator[B],
      metaValidator: ServiceMetaValidator[A]
  ): ServiceModelValidator[A, B] =
    apply

  final private class DefaultServiceModelValidator[A, B](
      operationValidator: OperationValidator[B],
      metaValidator: ServiceMetaValidator[A]
  ) extends ServiceModelValidator[A, B] {
    def validate(service: ServiceModel[A, B]): CodegenValidated[Unit] = {
      val metaErrors       = metaValidator.validate(service).fold(_.toList, _ => Nil)
      val structuralErrors = Validation.internal.duplicateIdsFromService(service)
      val operationErrors  =
        service.operations.flatMap(op => operationValidator.validate(op).fold(_.toList, _ => Nil))
      CodegenValidated.fromErrors(metaErrors ++ structuralErrors ++ operationErrors)
    }
  }
}
