package com.jacoby6000.smithplates.codegen.core

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel

type CodegenValidated[+A] = ValidatedNel[CodegenValidationError, A]
type CodegenEither[+A]    = Either[NonEmptyList[CodegenValidationError], A]

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

  def fromEither[A](either: CodegenEither[A]): CodegenValidated[A] =
    either match {
      case Right(value) => Validated.validNel(value)
      case Left(errors) => Validated.invalid(errors)
    }

  extension [A](validated: CodegenValidated[A]) {
    def toCodegenEither: CodegenEither[A] =
      validated.toEither
  }

  extension [A](either: CodegenEither[A]) {
    def toCodegenValidated: CodegenValidated[A] =
      CodegenValidated.fromEither(either)
  }

  extension (error: CodegenValidationError) {
    def invalidNel[A]: CodegenValidated[A] =
      Validated.invalidNel(error)
  }
}
