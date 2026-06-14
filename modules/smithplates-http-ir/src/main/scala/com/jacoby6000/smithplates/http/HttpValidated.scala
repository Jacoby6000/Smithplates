package com.jacoby6000.smithplates.http

import cats.data.NonEmptyList
import cats.data.ValidatedNel
import cats.syntax.all.*
import com.jacoby6000.smithplates.http.model.HttpSchemaError

type HttpValidated[+A] = ValidatedNel[HttpSchemaError, A]

object HttpValidated {
  def valid[A](value: A): HttpValidated[A] =
    value.validNel

  def invalid(error: HttpSchemaError): HttpValidated[Nothing] =
    error.invalidNel

  def toPluginExceptionMessage(errors: NonEmptyList[HttpSchemaError]): String =
    errors.map(_.message).toList.mkString("; ")
}
