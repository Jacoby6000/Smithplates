package com.jacoby6000.smithplates.sql.service

import cats.syntax.all.*
import com.jacoby6000.smithplates.sql.*
import software.amazon.smithy.model.Model

object SqlServiceIrExtractor {
  def extractOrThrow(model: Model, schema: SqlSchema): SqlServiceIr =
    extract(model, schema) match {
      case cats.data.Validated.Valid(serviceIr) => serviceIr
      case cats.data.Validated.Invalid(errors)  =>
        throw new IllegalStateException(SqlValidated.toPluginExceptionMessage(errors))
    }

  def extract(model: Model, schema: SqlSchema): SqlValidated[SqlServiceIr] =
    (
      SqlQueryExtractor.extract(model, schema),
      SqlServiceExtractor.extract(model)
    ).mapN { (queries, services) =>
      SqlServiceIr(queries = queries, services = services)
    }
}
