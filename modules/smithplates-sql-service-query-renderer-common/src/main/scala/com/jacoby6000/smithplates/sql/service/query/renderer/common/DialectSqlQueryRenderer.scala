package com.jacoby6000.smithplates.sql.service.query.renderer.common

import com.jacoby6000.smithplates.sql.SqlBindPlaceholder
import com.jacoby6000.smithplates.sql.model.SqlColumnType
import com.jacoby6000.smithplates.sql.service.SqlQueries
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlQueryRenderer
import com.jacoby6000.smithplates.sql.service.query.renderer.SqlRenderedQuery

final class DialectSqlQueryRenderer(
    val key: String,
    val migrationBindPlaceholder: SqlBindPlaceholder,
    val codegenBindPlaceholder: SqlBindPlaceholder,
    autoUpdatedTimestampAssignment: (String, SqlColumnType) => String
) extends SqlQueryRenderer {
  override def renderQueryUnits(queries: SqlQueries): List[SqlRenderedQuery] =
    SqlQueryRendering.renderQueryUnits(queries, autoUpdatedTimestampAssignment)
}
