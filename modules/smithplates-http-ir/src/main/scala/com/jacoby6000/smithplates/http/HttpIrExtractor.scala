package com.jacoby6000.smithplates.http

import cats.data.Validated
import com.jacoby6000.smithplates.http.model.HttpServiceIr
import software.amazon.smithy.model.Model

object HttpIrExtractor {
  def extract(model: Model): HttpValidated[HttpServiceIr] =
    HttpServiceExtractor.extract(model).map(services => HttpServiceIr(services = services))

  def extractOrThrow(model: Model): HttpServiceIr =
    extract(model) match {
      case Validated.Valid(ir)       => ir
      case Validated.Invalid(errors) =>
        throw new IllegalArgumentException(
          s"HTTP IR extraction failed: ${HttpValidated.toPluginExceptionMessage(errors)}"
        )
    }
}
