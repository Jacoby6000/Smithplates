package com.jacoby6000.smithplates.sql.query.sqlite

import com.jacoby6000.smithplates.sql.model.SqlColumnType
import com.jacoby6000.smithplates.sql.model.SqlTimestampFormat
import com.jacoby6000.smithplates.sql.query.*
import com.jacoby6000.smithplates.sql.service.SqlQueries

final class SqliteSqlQueryRenderer(
    val migrationBindPlaceholder: SqlBindPlaceholder,
    val codegenBindPlaceholder: SqlBindPlaceholder
) extends SqlQueryRenderer {
  override def key: String = "sqlite"

  override def renderQueryUnits(queries: SqlQueries): List[SqlRenderedQuery] =
    SqlQueryRendering.renderQueryUnits(queries, autoUpdatedTimestampAssignment)

  private def autoUpdatedTimestampAssignment(columnName: String, columnType: SqlColumnType): String = {
    val expression = columnType match {
      case SqlColumnType.Timestamp(format) =>
        format match {
          case SqlTimestampFormat.DateTime     => "CURRENT_TIMESTAMP"
          case SqlTimestampFormat.EpochSeconds => "unixepoch('subsec')"
        }
      case _                               => "CURRENT_TIMESTAMP"
    }
    s"$columnName = $expression"
  }
}
