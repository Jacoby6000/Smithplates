package com.jacoby6000.smithy.stache.sql

import com.jacoby6000.smithy.stache.sql.shared.DDLStatement
import com.jacoby6000.smithy.stache.sql.shared.SqlBindPlaceholder
import com.jacoby6000.smithy.stache.sql.shared.SqlRenderOutput
import com.jacoby6000.smithy.stache.sql.shared.SqlRenderUnit

trait DialectRenderer {
  def dialect: SqlDialect

  def renderUnits(schema: SqlSchema, serviceIr: SqlServiceIr): List[SqlRenderUnit]

  def renderSchemaDdlStatements(schema: SqlSchema): List[DDLStatement] =
    SqlRenderOutput.ddlStatements(renderUnits(schema, SqlServiceIr()))

  def render(schema: SqlSchema, serviceIr: SqlServiceIr): String =
    SqlRenderOutput.format(
      renderUnits(schema, serviceIr),
      SqlBindPlaceholder.forDialect(dialect)
    )
}
