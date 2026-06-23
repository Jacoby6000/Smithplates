package com.jacoby6000.smithplates.codegen.core

trait ModelMetaValidator[A] {
  def validate(model: Model[A]): CodegenValidated[Unit]
}

object ModelMetaValidator {
  def noop[A]: ModelMetaValidator[A] =
    new ModelMetaValidator[A] {
      def validate(model: Model[A]): CodegenValidated[Unit] =
        CodegenValidated.unit
    }

  def apply[A](f: Model[A] => CodegenValidated[Unit]): ModelMetaValidator[A] =
    new ModelMetaValidator[A] {
      def validate(model: Model[A]): CodegenValidated[Unit] =
        f(model)
    }
}

trait OperationMetaValidator[A] {
  def validate(operation: OperationModel[A]): CodegenValidated[Unit]
}

object OperationMetaValidator {
  def noop[A]: OperationMetaValidator[A] =
    new OperationMetaValidator[A] {
      def validate(operation: OperationModel[A]): CodegenValidated[Unit] =
        CodegenValidated.unit
    }

  def apply[A](f: OperationModel[A] => CodegenValidated[Unit]): OperationMetaValidator[A] =
    new OperationMetaValidator[A] {
      def validate(operation: OperationModel[A]): CodegenValidated[Unit] =
        f(operation)
    }
}

trait ServiceMetaValidator[A] {
  def validate[B](service: ServiceModel[A, B]): CodegenValidated[Unit]
}

object ServiceMetaValidator {
  def noop[A]: ServiceMetaValidator[A] =
    new ServiceMetaValidator[A] {
      def validate[B](service: ServiceModel[A, B]): CodegenValidated[Unit] =
        CodegenValidated.unit
    }

  def apply[A](f: [B] => ServiceModel[A, B] => CodegenValidated[Unit]): ServiceMetaValidator[A] =
    new ServiceMetaValidator[A] {
      def validate[B](service: ServiceModel[A, B]): CodegenValidated[Unit] =
        f[B](service)
    }
}
