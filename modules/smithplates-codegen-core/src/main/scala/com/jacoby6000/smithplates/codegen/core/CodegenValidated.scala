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

  def fromEither[A](either: Either[CodegenValidationError, A]): CodegenValidated[A] =
    either match {
      case Right(value) => Validated.validNel(value)
      case Left(error)  => Validated.invalidNel(error)
    }

  extension [A](validated: CodegenValidated[A]) {
    def toCodegenEither: Either[CodegenValidationError, A] =
      validated match {
        case Validated.Valid(value)    => Right(value)
        case Validated.Invalid(errors) => Left(errors.head)
      }
  }

  extension (error: CodegenValidationError) {
    def invalidNel[A]: CodegenValidated[A] =
      Validated.invalidNel(error)
  }
}
