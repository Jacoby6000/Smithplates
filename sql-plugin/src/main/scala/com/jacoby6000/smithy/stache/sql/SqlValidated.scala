package com.jacoby6000.smithy.stache.sql

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all.*

type SqlValidated[+A] = ValidatedNel[SqlSchemaError, A]

object SqlValidated {
  def valid[A](value: A): SqlValidated[A] =
    value.validNel

  def invalid(error: SqlSchemaError): SqlValidated[Nothing] =
    error.invalidNel

  def fromEither[A](value: Either[SqlSchemaError, A]): SqlValidated[A] =
    value.toValidatedNel

  def requireNonEmpty[A](values: List[A], error: SqlSchemaError): SqlValidated[Unit] =
    Validated.condNel(values.nonEmpty, (), error)

  def toPluginExceptionMessage(errors: NonEmptyList[SqlSchemaError]): String =
    errors.map(_.message).toList.mkString("; ")

  def hasInvalidMemberKind(errors: NonEmptyList[SqlSchemaError], kind: InvalidMemberColumnType.Kind): Boolean =
    errors.exists {
      case InvalidMemberColumnType(_, _, _, errorKind) => errorKind == kind
      case _                                           => false
    }
}
