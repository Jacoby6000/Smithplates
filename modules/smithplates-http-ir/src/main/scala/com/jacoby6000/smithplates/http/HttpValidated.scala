package com.jacoby6000.smithplates.http

import cats.data.NonEmptyList
import cats.data.ValidatedNel
import com.jacoby6000.smithplates.http.model.HttpSchemaError

type HttpValidated[+A] = ValidatedNel[HttpSchemaError, A]

object HttpValidated {
  def toPluginExceptionMessage(errors: NonEmptyList[HttpSchemaError]): String =
    errors.map(_.message).toList.mkString("; ")
}
