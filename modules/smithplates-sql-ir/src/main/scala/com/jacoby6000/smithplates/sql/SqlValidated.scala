package com.jacoby6000.smithplates.sql

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.model.SqlSchemaError

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
}
