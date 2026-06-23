package com.jacoby6000.smithplates.codegen.core

trait ModelSetValidator[A] {
  def validate(modelSet: ModelSet[A]): CodegenValidated[Unit]
}

object ModelSetValidator {
  def apply[A](using modelValidator: ModelValidator[A]): ModelSetValidator[A] =
    DefaultModelSetValidator(modelValidator)

  def default[A](using modelValidator: ModelValidator[A]): ModelSetValidator[A] =
    apply

  final private class DefaultModelSetValidator[A](modelValidator: ModelValidator[A]) extends ModelSetValidator[A] {
    def validate(modelSet: ModelSet[A]): CodegenValidated[Unit] = {
      val structuralErrors =
        Validation.internal.duplicateIdsFromModels(modelSet.all) ++
          Validation.internal.cyclicAliasDefinitions(modelSet)
      val modelErrors      =
        modelSet.all.flatMap(model => modelValidator.validate(model).fold(_.toList, _ => Nil))
      CodegenValidated.fromErrors(structuralErrors ++ modelErrors)
    }
  }
}
