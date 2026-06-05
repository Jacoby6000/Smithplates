package com.jacoby6000.smithy.stache

import com.jacoby6000.smithy.stache.sql.*
import com.jacoby6000.smithy.stache.sql.postgres.PostgresRenderer
import com.jacoby6000.smithy.stache.sql.service.SqlServiceIr
import com.jacoby6000.smithy.stache.sql.service.shared.SqlQueryRenderOutput
import com.jacoby6000.smithy.stache.sql.service.shared.SqlQueryRenderer
import com.jacoby6000.smithy.stache.sql.shared.SqlBindPlaceholder
import com.jacoby6000.smithy.stache.sql.shared.SqlSchemaDdlRenderer
import com.jacoby6000.smithy.stache.sql.sqlite.SqliteRenderer

object DialectRenderers {
  def schemaDdlRenderer(dialect: SqlDialect): SqlSchemaDdlRenderer =
    dialect match {
      case SqliteDialect   => SqliteRenderer
      case PostgresDialect => PostgresRenderer
    }

  def render(schema: SqlSchema, serviceIr: SqlServiceIr, dialect: SqlDialect): String =
    SqlQueryRenderOutput.formatWithDdl(
      schemaDdlRenderer(dialect).renderSchemaDdlStatements(schema),
      SqlQueryRenderer.renderQueryUnits(serviceIr.queries),
      SqlBindPlaceholder.forDialect(dialect)
    )
}
