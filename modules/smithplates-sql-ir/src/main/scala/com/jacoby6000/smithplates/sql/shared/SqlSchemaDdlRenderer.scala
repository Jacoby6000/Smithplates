package com.jacoby6000.smithplates.sql.shared

import com.jacoby6000.smithplates.sql.SqlSchema

/** Renders dialect-specific DDL from validated schema IR. */
trait SqlSchemaDdlRenderer {
  def renderSchemaDdlStatements(schema: SqlSchema): List[DDLStatement]
}
