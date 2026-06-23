package com.jacoby6000.smithplates.codegen.core

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel

type CodegenValidated[+A] = ValidatedNel[CodegenValidationError, A]

object CodegenValidated {
  def valid[A](value: A): CodegenValidated[A] =
    Validated.validNel(value)

  def unit: CodegenValidated[Unit] =
    valid(())

  def fromErrors(errors: List[CodegenValidationError]): CodegenValidated[Unit] =
    NonEmptyList.fromList(errors) match {
      case None        => unit
      case Some(error) => Validated.invalid(error)
    }
}
