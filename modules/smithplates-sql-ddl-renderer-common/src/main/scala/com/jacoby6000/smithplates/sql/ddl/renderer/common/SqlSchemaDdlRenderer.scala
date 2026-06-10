package com.jacoby6000.smithplates.sql.ddl.renderer.common

import com.jacoby6000.smithplates.sql.model.DDLStatement
import com.jacoby6000.smithplates.sql.model.SqlSchema

/** Renders dialect-specific DDL from validated schema IR. */
trait SqlSchemaDdlRenderer {
  def renderSchemaDdlStatements(schema: SqlSchema): List[DDLStatement]
}
