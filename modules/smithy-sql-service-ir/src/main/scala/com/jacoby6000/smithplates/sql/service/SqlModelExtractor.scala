package com.jacoby6000.smithplates.sql.service

import cats.data.Validated
import com.jacoby6000.smithplates.sql.*
import software.amazon.smithy.model.Model

/** Combined SQL and service IR extraction result for plugin orchestration and tests. */
final case class SqlExtractionResult(
    schema: SqlSchema,
    serviceIr: SqlServiceIr
) {
  def tables: List[SqlTable]               = schema.tables
  def relationships: List[SqlRelationship] = schema.relationships
  def queries: SqlQueries                  = serviceIr.queries
  def services: List[SqlService]           = serviceIr.services
}

object SqlModelExtractor {
  def extractOrThrow(model: Model): SqlExtractionResult =
    extract(model) match {
      case Validated.Valid(result)   => result
      case Validated.Invalid(errors) =>
        throw new IllegalStateException(SqlValidated.toPluginExceptionMessage(errors))
    }

  def extract(model: Model): SqlValidated[SqlExtractionResult] =
    SqlIrExtractor.extract(model).andThen { schema =>
      SqlServiceIrExtractor.extract(model, schema).map { serviceIr =>
        SqlExtractionResult(schema = schema, serviceIr = serviceIr)
      }
    }
}
