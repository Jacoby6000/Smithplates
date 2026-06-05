package com.jacoby6000.smithy.stache.sql.query.sqlite

import com.jacoby6000.smithy.stache.sql.SqlColumnType
import com.jacoby6000.smithy.stache.sql.SqlTimestampFormat
import com.jacoby6000.smithy.stache.sql.query.*
import com.jacoby6000.smithy.stache.sql.service.SqlQueries

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
