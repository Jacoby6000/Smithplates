package com.jacoby6000.smithy.stache.sql.shared

import com.jacoby6000.smithy.stache.sql.SqlDialect
import com.jacoby6000.smithy.stache.sql.SqlSchema

/** Renders dialect-specific DDL from validated schema IR. */
trait SqlSchemaDdlRenderer {
  def dialect: SqlDialect

  def renderSchemaDdlStatements(schema: SqlSchema): List[DDLStatement]
}
