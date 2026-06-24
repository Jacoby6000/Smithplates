package com.jacoby6000.smithplates.codegen.core.json

import cats.data.ValidatedNel
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Error
import io.circe.parser.parse

/** Shared Circe JSON parse/decode helpers for configuration files. */
object JsonDecoding {
  def decodeJson[A: Decoder](text: String): Either[Error, A] =
    parse(text).flatMap(_.as[A])

  def decodeJsonValidated[A: Decoder, E](text: String, invalid: String => E): ValidatedNel[E, A] =
    parse(text) match {
      case Left(error) =>
        invalid(s"invalid JSON: ${errorMessage(error)}").invalidNel
      case Right(json) =>
        json.as[A].leftMap(decodeError => invalid(errorMessage(decodeError))).toValidatedNel
    }

  def errorMessage(error: Error): String =
    error match {
      case failure: DecodingFailure => failure.message
      case other                    => other.getMessage
    }
}
