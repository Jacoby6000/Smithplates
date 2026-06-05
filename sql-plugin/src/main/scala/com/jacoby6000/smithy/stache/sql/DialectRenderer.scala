package com.jacoby6000.smithy.stache.sql

import com.jacoby6000.smithy.stache.sql.postgres.PostgresRenderer
import com.jacoby6000.smithy.stache.sql.shared.SqlBindPlaceholder
import com.jacoby6000.smithy.stache.sql.sqlite.SqliteRenderer

trait DialectRenderer {
  def dialect: SqlDialect

  def renderUnits(schema: SqlSchema): List[com.jacoby6000.smithy.stache.sql.shared.SqlRenderUnit]

  def render(schema: SqlSchema): String =
    com.jacoby6000.smithy.stache.sql.shared.SqlRenderOutput.format(
      renderUnits(schema),
      SqlBindPlaceholder.forDialect(dialect)
    )
}

object DialectRenderer {
  def forDialect(dialect: SqlDialect): DialectRenderer =
    dialect match {
      case SqliteDialect   => SqliteRenderer
      case PostgresDialect => PostgresRenderer
    }
}
