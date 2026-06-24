package com.jacoby6000.smithplates.codegen.core

trait OperationValidator[A] {
  def validate(operation: OperationModel[A]): CodegenValidated[Unit]
}

object OperationValidator {
  def apply[A](using metaValidator: OperationMetaValidator[A]): OperationValidator[A] =
    (operation: OperationModel[A]) => metaValidator.validate(operation)

  def default[A](using metaValidator: OperationMetaValidator[A]): OperationValidator[A] =
    apply
}
