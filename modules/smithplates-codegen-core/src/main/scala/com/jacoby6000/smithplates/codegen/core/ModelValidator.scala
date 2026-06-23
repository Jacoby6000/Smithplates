package com.jacoby6000.smithplates.codegen.core

trait ModelValidator[A] {
  def validate(model: Model[A]): CodegenValidated[Unit]
}

object ModelValidator {
  def apply[A](using metaValidator: ModelMetaValidator[A]): ModelValidator[A] =
    (model: Model[A]) => metaValidator.validate(model)

  def default[A](using metaValidator: ModelMetaValidator[A]): ModelValidator[A] =
    apply
}
